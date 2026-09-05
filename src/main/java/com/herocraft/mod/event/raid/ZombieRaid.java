package com.herocraft.mod.event.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.event.EventBossBar;
import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.EventInstance;
import com.herocraft.mod.event.EventSpawns;
import com.herocraft.mod.event.EventState;
import com.herocraft.mod.event.EventTypes;
import com.herocraft.mod.event.WaveDefinition;
import com.herocraft.mod.event.boss.BossPowers;
import com.herocraft.mod.event.entity.EmpoweredZombie;
import com.herocraft.mod.event.entity.RaidEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

/**
 * The Zombie Raid: twelve waves, Powered Zombie Bosses on 4, 8 and 12, and a Cursed Grave Chest at
 * the end.
 *
 * <h2>How a wave actually runs</h2>
 * A wave is not spawned in one burst. {@link #startWave} converts the wave table entry into an
 * <em>owed</em> count per spawn entry, and each event tick drips at most
 * {@value #SPAWNS_PER_TICK} mobs into legal positions. That does three things at once: it spreads the
 * spawn cost over time instead of spiking one tick, it means a wave whose mobs cannot currently be
 * placed (a walled arena, half the ring unloaded) simply keeps trying rather than silently losing
 * mobs, and -- because the owed counts are plain integers -- it all survives a save/load intact.
 *
 * <h2>Softlock protection</h2>
 * The counter only advances when the last owed mob is spawned and every owned mob is dead. If the
 * living count stops changing for {@link EventConfig.ZombieRaid#waveStallTicks} -- a mob wedged in
 * terrain, stranded on a roof, or pathing at nothing -- the raid pulls the survivors back to fresh
 * spawn positions near the centre rather than waiting forever. If that still cannot be done, the
 * remaining mobs are released from the wave so the raid can move on. A raid can therefore be lost or
 * abandoned, but it cannot hang.
 *
 * <h2>What is persisted</h2>
 * Wave number, phase, phase timer, owed counts, which bosses have already been defeated, whether the
 * completion chest has been generated, and the base class's owned-mob list and participant records.
 * That is deliberately everything needed to make a restart mid-raid a no-op: it cannot re-run a wave,
 * cannot spawn a second boss, and cannot hand out the final chest twice.
 */
public class ZombieRaid extends EventInstance {
	/** Cap on mobs entering the world per event tick, to spread the cost of a big wave. */
	private static final int SPAWNS_PER_TICK = 3;
	/** Players within this many blocks of the raid centre see the raid bar and the purple sky. */
	private static final double RAID_BAR_RADIUS = 96.0;
	/** Undead that stray past this from the raid centre are pulled back toward it. */
	private static final double TETHER_RADIUS = 60.0;

	/**
	 * The vanilla-style raid bar at the top of the screen -- "Wave X/Y -- N enemies", or the
	 * between-wave countdown -- shared with the Supervillain Raid via {@link EventBossBar}, styled like
	 * a vanilla Pillager raid (red, notched, no screen darkening). The dark-purple sky is a separate
	 * client tint driven by {@link ZombieRaidNetworking}. Not persisted; rebuilt every tick.
	 */
	private transient EventBossBar raidBar = new EventBossBar(id(),
			net.minecraft.world.BossEvent.BossBarColor.RED,
			net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10, false);

	private enum Phase {
		/** Counting down to the next wave. */
		PREPARING,
		/** Wave is spawning and/or being fought. */
		ACTIVE
	}

	private final SurviveWavesObjective objective = new SurviveWavesObjective(ZombieRaidWaves.count());

	private int wave;
	private Phase phase = Phase.PREPARING;
	private int phaseTicks;
	/** Full length of the current PREPARING countdown, so the raid bar can refill smoothly across it. */
	private int phaseTotalTicks = 1;
	private int[] owed = new int[0];
	private boolean bossOwed;
	private UUID bossId;
	private int bossesDefeated;
	private boolean chestGenerated;
	/** The wave-12 boss's power, so the completion chest can stamp its Corrupted Power Core. */
	private String finalBossPower = "";
	/** Stall detection: last observed live count and how long it has been unchanged. */
	private int lastAliveCount = -1;
	private int unchangedTicks;

	public ZombieRaid(UUID id) {
		super(id);
	}

	@Override
	public String typeId() {
		return EventTypes.ZOMBIE_RAID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.herocraft.zombie_raid");
	}

	public int wave() {
		return wave;
	}

	public int totalWaves() {
		return ZombieRaidWaves.count();
	}

	public int bossesDefeated() {
		return bossesDefeated;
	}

	public String finalBossPower() {
		return finalBossPower;
	}

	public SurviveWavesObjective objective() {
		return objective;
	}

	// ---------------- lifecycle ----------------

	@Override
	protected void onTick(ServerLevel level) {
		int interval = Math.max(1, EventConfig.framework().tickIntervalTicks);

		if (state() == EventState.PENDING) {
			begin(level);
			return;
		}

		if (phase == Phase.PREPARING) {
			phaseTicks -= interval;
			if (phaseTicks <= 0) {
				startWave(level, wave + 1);
			}
			pushHud(level);
			return;
		}

		// ACTIVE
		spawnOwed(level);
		int alive = ownedAlive(level);
		boolean stillOwed = anyOwed() || bossOwed;

		if (!stillOwed && alive == 0) {
			completeWave(level);
			return;
		}

		trackStall(level, alive, stillOwed, interval);
		tetherOwnedMobs(level, TETHER_RADIUS);
		pushHud(level);
	}

	private void begin(ServerLevel level) {
		setState(EventState.RUNNING);
		// Everyone standing in the area when it starts is a participant from the outset.
		participants().refresh(level, center(), EventConfig.framework().abandonRadius, true);
		wave = 0;
		phase = Phase.PREPARING;
		phaseTicks = 5 * 20;
		phaseTotalTicks = phaseTicks;

		broadcastTitle(level,
				Component.translatable("event.herocraft.zombie_raid.rise")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("event.herocraft.zombie_raid").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.6f);
		level.sendParticles(ParticleTypes.SOUL, center().getX() + 0.5, center().getY() + 1.0, center().getZ() + 0.5,
				80, 3.0, 1.0, 3.0, 0.05);
		HeroCraftMod.LOGGER.info("[HeroCraft] Zombie Raid {} started at {}", id(), center());
	}

	private void startWave(ServerLevel level, int number) {
		wave = number;
		phase = Phase.ACTIVE;
		unchangedTicks = 0;
		lastAliveCount = -1;

		WaveDefinition definition = ZombieRaidWaves.get(number);
		int players = Math.max(1, participants().presentCount());
		double perPlayer = EventConfig.raid().mobCountPerExtraPlayer;

		owed = new int[definition.spawns().size()];
		for (int i = 0; i < owed.length; i++) {
			owed[i] = definition.spawns().get(i).count(players, perPlayer);
		}
		bossOwed = definition.bossWave();

		broadcast(level, Component.translatable("event.herocraft.zombie_raid.wave",
				number, ZombieRaidWaves.count()).withStyle(ChatFormatting.GOLD));
		level.playSound(null, center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0f,
				definition.bossWave() ? 0.6f : 1.0f);
		pushHud(level);
	}

	private void completeWave(ServerLevel level) {
		objective.noteWaveCleared(wave);
		if (objective.isComplete()) {
			complete(level);
			return;
		}
		phase = Phase.PREPARING;
		phaseTicks = EventConfig.raid().betweenWaveTicks;
		phaseTotalTicks = Math.max(1, phaseTicks);
		broadcast(level, Component.translatable("event.herocraft.zombie_raid.wave_cleared", wave)
				.withStyle(ChatFormatting.GREEN));
		level.playSound(null, center(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
		pushHud(level);
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		if (success) {
			broadcastTitle(level,
					Component.translatable("event.herocraft.zombie_raid.cleared")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("event.herocraft.zombie_raid.cleared.sub").withStyle(ChatFormatting.GRAY));
			level.playSound(null, center(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			if (!chestGenerated) {
				chestGenerated = true;
				ZombieRaidRewards.grantCompletion(level, this);
			}
		}
		raidBar.clear(level);
		ZombieRaidNetworking.clearSky(level);
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		raidBar.clear(level);
		ZombieRaidNetworking.clearSky(level);
	}

	@Override
	protected void onAborted(ServerLevel level) {
		raidBar.clear(level);
		ZombieRaidNetworking.clearSky(level);
	}

	// ---------------- spawning ----------------

	private boolean anyOwed() {
		for (int n : owed) {
			if (n > 0) {
				return true;
			}
		}
		return false;
	}

	private void spawnOwed(ServerLevel level) {
		if (!anyOwed() && !bossOwed) {
			return;
		}
		if (ownedAlive(level) >= EventConfig.framework().maxLiveMobs) {
			return; // hard ceiling: never exceed the configured live-mob budget
		}
		List<ServerPlayer> present = participants().present();
		if (present.isEmpty()) {
			return;
		}

		// The boss goes first so a boss wave never ends up with its escort in and the boss still owed.
		if (bossOwed) {
			if (spawnBoss(level, present)) {
				bossOwed = false;
			}
			return;
		}

		WaveDefinition definition = ZombieRaidWaves.get(wave);
		int spawned = 0;
		for (int i = 0; i < owed.length && spawned < SPAWNS_PER_TICK; i++) {
			while (owed[i] > 0 && spawned < SPAWNS_PER_TICK) {
				BlockPos pos = EventSpawns.findSpawn(level, center(), present, level.random);
				if (pos == null) {
					return; // nowhere legal right now -- try again next tick, lose nothing
				}
				WaveDefinition.Spawn entry = definition.spawns().get(i);
				Mob mob = createMob(level, entry.type().get(), pos);
				if (mob == null) {
					owed[i]--;
					continue;
				}
				if (entry.customizer() != null) {
					entry.customizer().accept(mob);
				}
				own(mob);
				EventSpawns.place(level, mob, pos, center());
				owed[i]--;
				spawned++;
			}
		}
	}

	private Mob createMob(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
		Mob mob = type.create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
		mob.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null);
		// Keep the horde on the raid rather than letting it drift off into the dark -- idle wander
		// stays inside this radius and EventInstance#tetherOwnedMobs pulls back anything kited past it.
		mob.restrictTo(center(), (int) Math.round(TETHER_RADIUS));
		return mob;
	}

	private boolean spawnBoss(ServerLevel level, List<ServerPlayer> present) {
		BlockPos pos = EventSpawns.findSpawn(level, center(), present, level.random);
		if (pos == null) {
			return false;
		}
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(level);
		if (boss == null) {
			return true; // registration problem; do not wedge the raid on it
		}
		boolean finalBoss = wave >= ZombieRaidWaves.count();
		String primary = BossPowers.randomKey(level.random);
		String secondary = null;
		if (finalBoss && level.random.nextDouble() < EventConfig.raid().finalBossDualPowerChance) {
			var controller = BossPowers.create(primary, boss);
			if (controller != null) {
				secondary = BossPowers.randomCompatibleKey(level.random, controller);
			}
		}
		boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		boss.configure(primary, secondary, finalBoss, Math.max(1, participants().presentCount()));
		if (finalBoss) {
			finalBossPower = primary;
		}
		own(boss);
		bossId = boss.getUUID();
		EventSpawns.place(level, boss, pos, center());

		broadcast(level, Component.translatable("event.herocraft.zombie_raid.boss",
				BossPowers.displayName(primary)).withStyle(ChatFormatting.LIGHT_PURPLE));
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 0.8f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				60, 1.0, 1.5, 1.0, 0.08);
		return true;
	}

	// ---------------- stall handling ----------------

	private void trackStall(ServerLevel level, int alive, boolean stillOwed, int interval) {
		if (alive != lastAliveCount || stillOwed) {
			lastAliveCount = alive;
			unchangedTicks = 0;
			return;
		}
		unchangedTicks += interval;
		if (unchangedTicks < EventConfig.raid().waveStallTicks) {
			return;
		}
		unchangedTicks = 0;

		List<ServerPlayer> present = participants().present();
		if (present.isEmpty()) {
			return;
		}
		List<Mob> stuck = new ArrayList<>(liveOwnedMobs(level));
		if (stuck.isEmpty()) {
			return;
		}
		int moved = 0;
		for (Mob mob : stuck) {
			BlockPos pos = EventSpawns.findSpawn(level, center(), present, level.random);
			if (pos == null) {
				continue;
			}
			mob.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			mob.getNavigation().stop();
			moved++;
		}
		if (moved == 0) {
			// Could not reposition anything -- release them from the wave so it can end rather than
			// leaving the raid waiting on mobs it cannot reach.
			for (Mob mob : stuck) {
				disown(mob.getUUID());
				mob.discard();
			}
			HeroCraftMod.LOGGER.info("[HeroCraft] Zombie Raid {} released {} unreachable wave mobs",
					id(), stuck.size());
		}
	}

	// ---------------- boss / kill hooks ----------------

	/** Called by {@link ZombieRaidRewards} when an Empowered Zombie belonging to this raid dies. */
	public void noteBossDefeated(UUID entityId) {
		bossesDefeated++;
		if (entityId.equals(bossId)) {
			bossId = null;
		}
	}

	// ---------------- HUD ----------------

	/**
	 * Refresh the top-of-screen raid bar (wave / enemies, or the between-wave countdown) and push the
	 * "you are in a raid area" flag that drives the client's dark-purple sky tint. Both are recomputed
	 * every event tick from data the server already has; the sky push de-duplicates so it is a handful
	 * of packets per raid, not one per tick.
	 */
	private void pushHud(ServerLevel level) {
		Component name;
		float progress;
		if (phase == Phase.PREPARING) {
			int secs = secondsToNextWave();
			name = wave <= 0
					? Component.translatable("event.herocraft.zombie_raid.bar.incoming", secs)
					: Component.translatable("event.herocraft.zombie_raid.bar.next", secs);
			// Refill the bar across the countdown, so it visibly "recharges" back to full before the
			// next wave drops rather than sitting empty.
			progress = 1.0f - Math.min(1.0f, Math.max(0, phaseTicks) / (float) Math.max(1, phaseTotalTicks));
		} else {
			int remaining = enemiesRemaining(level);
			name = Component.translatable("event.herocraft.zombie_raid.bar.wave",
					wave, totalWaves(), remaining);
			int expected = Math.max(1, waveMobBudget());
			// Full when the whole wave is still standing, draining toward empty as they die.
			progress = Math.min(1.0f, remaining / (float) expected);
		}
		raidBar.update(level, center(), RAID_BAR_RADIUS, name, progress);
		ZombieRaidNetworking.pushSky(level, this, RAID_BAR_RADIUS);
	}

	/** Rough count of undead a wave is worth, for the raid bar's fill. */
	private int waveMobBudget() {
		if (wave <= 0 || wave > ZombieRaidWaves.count()) {
			return 1;
		}
		WaveDefinition def = ZombieRaidWaves.get(wave);
		int players = Math.max(1, participants().presentCount());
		double per = EventConfig.raid().mobCountPerExtraPlayer;
		int total = def.bossWave() ? 1 : 0;
		for (WaveDefinition.Spawn s : def.spawns()) {
			total += s.count(players, per);
		}
		return total;
	}

	/** Enemies still to deal with this wave -- alive plus not yet spawned. Used by the HUD. */
	public int enemiesRemaining(ServerLevel level) {
		int remaining = ownedAlive(level);
		for (int n : owed) {
			remaining += n;
		}
		if (bossOwed) {
			remaining++;
		}
		return remaining;
	}

	public boolean preparing() {
		return phase == Phase.PREPARING;
	}

	public int secondsToNextWave() {
		return Math.max(0, phaseTicks / 20);
	}

	// ---------------- debug hooks (op-only commands) ----------------

	/**
	 * Discard whatever the current wave still has out, spawned or owed, and jump to {@code number}.
	 * Used by {@code /heropack zombieraid wave <n>}; deliberately not reachable from gameplay.
	 */
	public void debugJumpToWave(ServerLevel level, int number) {
		if (state() == EventState.PENDING) {
			begin(level);
		}
		cleanupOwnedMobs(level);
		java.util.Arrays.fill(owed, 0);
		bossOwed = false;
		objective.noteWaveCleared(Math.max(0, number - 1));
		startWave(level, number);
	}

	/** Kill every mob this raid still owns, so the wave finishes through the normal path. */
	public int debugClearWave(ServerLevel level) {
		java.util.List<Mob> mobs = liveOwnedMobs(level);
		for (Mob mob : mobs) {
			mob.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
			if (mob.isAlive()) {
				mob.discard();
				disown(mob.getUUID());
			}
		}
		java.util.Arrays.fill(owed, 0);
		bossOwed = false;
		return mobs.size();
	}

	/** Adopt a command-spawned boss into this raid so its rewards and bookkeeping still apply. */
	public void debugAdoptBoss(EmpoweredZombie boss) {
		own(boss);
		bossId = boss.getUUID();
	}

	// ---------------- persistence ----------------

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putInt("Wave", wave);
		tag.putString("Phase", phase.name());
		tag.putInt("PhaseTicks", phaseTicks);
		tag.putInt("PhaseTotalTicks", phaseTotalTicks);
		tag.putIntArray("Owed", owed);
		tag.putBoolean("BossOwed", bossOwed);
		if (bossId != null) {
			tag.putUUID("BossId", bossId);
		}
		tag.putInt("BossesDefeated", bossesDefeated);
		tag.putBoolean("ChestGenerated", chestGenerated);
		tag.putString("FinalBossPower", finalBossPower);
		CompoundTag objectiveTag = new CompoundTag();
		objective.save(objectiveTag);
		tag.put("Objective", objectiveTag);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		wave = tag.getInt("Wave");
		try {
			phase = Phase.valueOf(tag.getString("Phase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.PREPARING;
		}
		phaseTicks = tag.getInt("PhaseTicks");
		phaseTotalTicks = tag.contains("PhaseTotalTicks")
				? Math.max(1, tag.getInt("PhaseTotalTicks"))
				: Math.max(1, Math.max(phaseTicks, EventConfig.raid().betweenWaveTicks));
		owed = tag.getIntArray("Owed");
		bossOwed = tag.getBoolean("BossOwed");
		bossId = tag.hasUUID("BossId") ? tag.getUUID("BossId") : null;
		bossesDefeated = tag.getInt("BossesDefeated");
		chestGenerated = tag.getBoolean("ChestGenerated");
		finalBossPower = tag.getString("FinalBossPower");
		objective.load(tag.getCompound("Objective"));
	}
}
