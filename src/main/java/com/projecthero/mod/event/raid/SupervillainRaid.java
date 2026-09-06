package com.projecthero.mod.event.raid;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventSpawns;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.WaveDefinition;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.SupervillainVariant;

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
import net.minecraft.world.phys.Vec3;

/**
 * The Supervillain Village Raid (spec sections 1-57). A rare, superhero-themed village event that is
 * <em>not</em> a vanilla raid: a Pillager Spy marks a village, a 10-minute preparation timer runs,
 * then five escalating raider waves are followed -- after an ominous pause -- by one of three
 * Supervillain bosses carrying a random Experimental Power.
 *
 * <h2>What is reused</h2>
 * Everything structural: {@link EventInstance} (presence, abandon, persistence), {@link EventSpawns}
 * (legal spawn positions), the {@link EmpoweredZombie} boss with its full AI, and the
 * {@link BossPowers} registry of AI-capable powers. This class only adds the countdown, the six-wave
 * table, the boss reveal, and the reward hand-off.
 *
 * <h2>Persistence</h2>
 * Phase, phase timer, wave number, owed counts, whether the boss is still owed, the boss's UUID,
 * the rolled appearance and power, and whether victory rewards have been handed out. A restart
 * mid-raid resumes exactly where it was and can never duplicate a boss or a reward.
 */
public class SupervillainRaid extends EventInstance {
	public static final String TYPE_ID = "supervillain_raid";
	private static final int SPAWNS_PER_TICK = 3;
	/** Players within this many blocks of the marked village see the raid bar. */
	private static final double RAID_BAR_RADIUS = 96.0;
	/** Raiders that stray past this from the village centre are pulled back ("defend the village"). */
	private static final double TETHER_RADIUS = 60.0;

	private enum Phase {
		/** The 10-minute preparation timer after the village is marked. */
		COUNTDOWN,
		/** Counting down to the next raider wave. */
		PREPARING,
		/** A raider wave is spawning / being fought. */
		ACTIVE,
		/** Wave 5 is down; the ominous quiet before the Supervillain arrives. */
		BOSS_INCOMING,
		/** The Supervillain is in the world. */
		BOSS_ACTIVE
	}

	private Phase phase = Phase.COUNTDOWN;
	private int phaseTicks;
	private int wave;
	private int[] owed = new int[0];
	/** Escort still owed on the boss wave. */
	private int[] escortOwed = new int[0];
	private boolean bossOwed;
	private UUID bossId;
	private boolean bossDefeated;
	private BlockPos bossDeathPos;
	private boolean rewardsGranted;
	private boolean cooldownStarted;
	private int lastWarnSecond = Integer.MAX_VALUE;
	private int approachAnnounceAt = -1;
	/** Ticks the combat phases have had nobody present -- a fast "the village has fallen" fail. */
	private int deadWaveTicks;

	/** Rolled once, at creation, and persisted -- so a restart shows the same villain / power. */
	private String variantId = "";
	private String powerKey = "";

	/** Total ticks the preparation countdown runs for -- persisted so the bar's fill is right after a
	 *  restart mid-countdown. */
	private int countdownTotalTicks;
	/**
	 * The vanilla-style raid bar at the top of the screen: the preparation countdown while the village
	 * is marked, then "Wave X/Y -- N enemies" through the raider waves. Not persisted -- rebuilt lazily
	 * and re-populated from the level's player list every tick, so a restart just re-creates it. The
	 * boss phases hand the screen over to the Supervillain's own boss bar, so this one hides then.
	 */
	private transient EventBossBar raidBar = new EventBossBar(id(),
			net.minecraft.world.BossEvent.BossBarColor.RED,
			net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10, false);

	public SupervillainRaid(UUID id) {
		super(id);
	}

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.supervillain_raid");
	}

	public int wave() {
		return wave;
	}

	public int totalWaves() {
		return SupervillainRaidWaves.count();
	}

	public SupervillainVariant variant() {
		return SupervillainVariant.byId(variantId);
	}

	public String powerKey() {
		return powerKey;
	}

	public boolean inCountdown() {
		return phase == Phase.COUNTDOWN;
	}

	public int secondsRemaining() {
		return Math.max(0, phaseTicks / 20);
	}

	public String phaseName() {
		return phase.name();
	}

	// ---------------- lifecycle ----------------

	@Override
	protected void onTick(ServerLevel level) {
		int interval = Math.max(1, EventConfig.framework().tickIntervalTicks);

		if (state() == EventState.PENDING) {
			begin(level);
			return;
		}

		// Fast fail (spec section 34): all participating players are down and none are present to
		// defend. The base class's 5-minute abandon still covers "everyone wandered off".
		boolean fighting = phase == Phase.ACTIVE || phase == Phase.BOSS_INCOMING || phase == Phase.BOSS_ACTIVE;
		if (fighting && participants().present().isEmpty() && participants().eligibleCount() > 0) {
			deadWaveTicks += interval;
			if (deadWaveTicks >= 30 * 20) {
				fail(level, Component.translatable("event.projecthero.supervillain_raid.fallen"));
				return;
			}
		} else {
			deadWaveTicks = 0;
		}

		switch (phase) {
			case COUNTDOWN -> tickCountdown(level, interval);
			case PREPARING -> {
				phaseTicks -= interval;
				updateRaidBar(level);
				if (phaseTicks <= 0) {
					startWave(level, wave + 1);
				}
			}
			case ACTIVE -> tickActiveWave(level);
			case BOSS_INCOMING -> tickBossIncoming(level, interval);
			case BOSS_ACTIVE -> tickBossActive(level);
			default -> {
			}
		}
	}

	private void begin(ServerLevel level) {
		setState(EventState.RUNNING);
		participants().refresh(level, center(), EventConfig.framework().abandonRadius, true);
		EventConfig.SupervillainRaid cfg = EventConfig.supervillain();

		if (variantId.isEmpty()) {
			variantId = SupervillainVariant.random(level.random).id();
		}
		if (powerKey.isEmpty()) {
			powerKey = BossPowers.randomSupervillainKey(level.random);
		}

		phase = Phase.COUNTDOWN;
		phaseTicks = Math.max(20, cfg.raidCountdownSeconds * 20);
		countdownTotalTicks = phaseTicks;
		lastWarnSecond = Integer.MAX_VALUE;

		broadcastTitle(level,
				Component.translatable("event.projecthero.supervillain_raid.marked")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("event.projecthero.supervillain_raid.marked.sub").withStyle(ChatFormatting.GRAY));
		broadcast(level, Component.translatable("event.projecthero.supervillain_raid.marked.chat",
				secondsRemaining() / 60).withStyle(ChatFormatting.RED));
		level.playSound(null, center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.7f, 0.5f);
		level.playSound(null, center(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.4f);
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] Village marked at {} — villain {}, power {}",
				center(), variantId, powerKey);
	}

	private void tickCountdown(ServerLevel level, int interval) {
		phaseTicks -= interval;
		int seconds = Math.max(0, phaseTicks / 20);
		updateRaidBar(level);

		if (crossed(seconds, 600)) {
			broadcast(level, Component.translatable("event.projecthero.supervillain_raid.warn_10")
					.withStyle(ChatFormatting.RED));
		} else if (crossed(seconds, 300)) {
			broadcast(level, Component.translatable("event.projecthero.supervillain_raid.warn_5")
					.withStyle(ChatFormatting.RED));
			level.playSound(null, center(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.5f);
		} else if (crossed(seconds, 60)) {
			broadcast(level, Component.translatable("event.projecthero.supervillain_raid.warn_1")
					.withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			level.playSound(null, center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.9f, 0.6f);
		} else if (seconds <= 10 && seconds >= 1 && seconds != lastWarnSecond) {
			broadcast(level, Component.literal(String.valueOf(seconds))
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
			level.playSound(null, center(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
		}
		lastWarnSecond = seconds;

		if (phaseTicks <= 0) {
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] Countdown complete — Wave 1 begins at {}", center());
			startWave(level, 1);
		}
	}

	private boolean crossed(int seconds, int threshold) {
		return seconds <= threshold && lastWarnSecond > threshold;
	}

	// ---------------- raid bar ----------------

	/**
	 * Refresh the top-of-screen raid bar. During the preparation phase it is an {@code M:SS} countdown
	 * to the first wave; once the waves are running it reads "Wave X/Y -- N enemies", filling as the
	 * wave is cleared. Audience and fill are recomputed here every tick from the level's own player
	 * list, so no packets of the mod's own are needed. Modelled on the vanilla raid bar (red, notched).
	 */
	private void updateRaidBar(ServerLevel level) {
		Component name;
		float progress;
		if (phase == Phase.COUNTDOWN) {
			int seconds = secondsRemaining();
			name = Component.translatable("event.projecthero.supervillain_raid.bar",
					String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
			int total = countdownTotalTicks > 0
					? countdownTotalTicks
					: Math.max(20, EventConfig.supervillain().raidCountdownSeconds * 20);
			progress = phaseTicks / (float) total;
		} else if (phase == Phase.PREPARING) {
			int shown = Math.max(1, wave);
			name = Component.translatable("event.projecthero.supervillain_raid.bar.wave",
					shown, SupervillainRaidWaves.count() - 1, 0);
			// Refill across the lull between waves so the bar visibly recharges before the next wave.
			int total = Math.max(1, EventConfig.supervillain().betweenWaveSeconds * 20);
			progress = 1.0f - Math.min(1.0f, Math.max(0, phaseTicks) / (float) total);
		} else {
			int shown = Math.max(1, wave);
			int remaining = enemiesRemaining(level);
			name = Component.translatable("event.projecthero.supervillain_raid.bar.wave",
					shown, SupervillainRaidWaves.count() - 1, remaining);
			int expected = Math.max(1, waveMobBudget());
			// Full while the wave is at strength, draining toward empty as the raiders fall.
			progress = Math.min(1.0f, remaining / (float) expected);
		}
		raidBar.update(level, center(), RAID_BAR_RADIUS, name, progress);
	}

	/** Enemies this wave still has out -- alive plus not yet spawned. Drives the raid-bar readout. */
	public int enemiesRemaining(ServerLevel level) {
		int remaining = ownedAlive(level);
		for (int n : owed) {
			remaining += n;
		}
		return remaining;
	}

	/** Rough count of raiders a wave is worth, for the raid bar's fill. */
	private int waveMobBudget() {
		if (wave <= 0 || wave >= SupervillainRaidWaves.count()) {
			return 1;
		}
		WaveDefinition def = SupervillainRaidWaves.get(wave);
		int players = Math.max(1, participants().presentCount());
		double per = EventConfig.supervillain().waveMobMultiplier;
		int total = 0;
		for (WaveDefinition.Spawn s : def.spawns()) {
			total += s.count(players, per);
		}
		return total;
	}

	/** Drop the raid bar off every client -- including any who are out of range and holding a ghost
	 *  copy. Safe to call when it was never created. */
	private void clearRaidBar(ServerLevel level) {
		raidBar.clear(level);
	}

	private void startWave(ServerLevel level, int number) {
		clearRaidBar(level);
		wave = number;
		if (number >= SupervillainRaidWaves.count()) {
			// Wave 6 = the Supervillain reveal, not a raider wave.
			beginBossReveal(level);
			return;
		}
		phase = Phase.ACTIVE;
		WaveDefinition def = SupervillainRaidWaves.get(number);
		int players = Math.max(1, participants().presentCount());
		double per = EventConfig.supervillain().waveMobMultiplier;
		owed = new int[def.spawns().size()];
		for (int i = 0; i < owed.length; i++) {
			owed[i] = def.spawns().get(i).count(players, per);
		}
		bossOwed = false;

		broadcastTitle(level,
				Component.translatable("event.projecthero.supervillain_raid.title")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("event.projecthero.supervillain_raid.wave", number, SupervillainRaidWaves.count())
						.withStyle(ChatFormatting.GOLD));
		level.playSound(null, center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] Wave {} started at {}", number, center());
	}

	private void tickActiveWave(ServerLevel level) {
		spawnOwed(level);
		updateRaidBar(level);
		tetherOwnedMobs(level, TETHER_RADIUS);
		int alive = ownedAlive(level);
		if (anyOwed() || alive > 0) {
			return;
		}
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] Wave {} cleared", wave);
		if (wave >= SupervillainRaidWaves.count() - 1) {
			beginBossReveal(level);
			return;
		}
		phase = Phase.PREPARING;
		phaseTicks = Math.max(20, EventConfig.supervillain().betweenWaveSeconds * 20);
		broadcast(level, Component.translatable("event.projecthero.supervillain_raid.wave_cleared",
				EventConfig.supervillain().betweenWaveSeconds).withStyle(ChatFormatting.GREEN));
		level.playSound(null, center(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	private void beginBossReveal(ServerLevel level) {
		clearRaidBar(level);
		phase = Phase.BOSS_INCOMING;
		wave = SupervillainRaidWaves.count();
		int seconds = Math.max(4, EventConfig.supervillain().bossArrivalSeconds);
		phaseTicks = seconds * 20;
		approachAnnounceAt = phaseTicks / 2;
		broadcast(level, Component.translatable("event.projecthero.supervillain_raid.wave5_cleared")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		level.playSound(null, center(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 1.0f, 0.5f);
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] Wave 5 cleared — boss incoming ({}s)", seconds);
	}

	private void tickBossIncoming(ServerLevel level, int interval) {
		phaseTicks -= interval;
		BlockPos c = center();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5,
				12, 6.0, 2.0, 6.0, 0.02);
		level.sendParticles(ParticleTypes.SOUL, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5,
				6, 5.0, 1.0, 5.0, 0.02);
		if (approachAnnounceAt > 0 && phaseTicks <= approachAnnounceAt) {
			approachAnnounceAt = -1;
			// Split across the title + subtitle lines: a single long title string does not wrap and
			// runs off both edges of the screen at the 4x title scale.
			broadcastTitle(level,
					Component.translatable("event.projecthero.supervillain_raid.approaching")
							.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
					Component.translatable("event.projecthero.supervillain_raid.approaching.sub")
							.withStyle(ChatFormatting.DARK_PURPLE));
			level.playSound(null, c, SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 1.0f, 0.7f);
		}
		if (phaseTicks <= 0) {
			spawnBoss(level);
		}
	}

	private void tickBossActive(ServerLevel level) {
		spawnEscort(level);
		if (bossDefeated || (bossId != null && !bossAlive(level))) {
			complete(level);
		}
	}

	private boolean bossAlive(ServerLevel level) {
		return bossId != null && level.getEntity(bossId) != null && level.getEntity(bossId).isAlive();
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

	private boolean anyEscortOwed() {
		if (bossOwed) {
			return true;
		}
		for (int n : escortOwed) {
			if (n > 0) {
				return true;
			}
		}
		return false;
	}

	private void spawnOwed(ServerLevel level) {
		if (!anyOwed()) {
			return;
		}
		if (ownedAlive(level) >= EventConfig.framework().maxLiveMobs) {
			return;
		}
		List<ServerPlayer> present = participants().present();
		if (present.isEmpty()) {
			return;
		}
		WaveDefinition def = SupervillainRaidWaves.get(wave);
		int spawned = 0;
		for (int i = 0; i < owed.length && spawned < SPAWNS_PER_TICK; i++) {
			while (owed[i] > 0 && spawned < SPAWNS_PER_TICK) {
				BlockPos pos = EventSpawns.findSpawn(level, center(), present, level.random);
				if (pos == null) {
					return;
				}
				Mob mob = createMob(level, def.spawns().get(i).type().get(), pos);
				if (mob == null) {
					owed[i]--;
					continue;
				}
				own(mob);
				EventSpawns.place(level, mob, pos, center());
				owed[i]--;
				spawned++;
			}
		}
	}

	private void spawnEscort(ServerLevel level) {
		if (!anyEscortOwed()) {
			return;
		}
		if (ownedAlive(level) >= EventConfig.framework().maxLiveMobs) {
			return;
		}
		List<ServerPlayer> present = participants().present();
		if (present.isEmpty()) {
			return;
		}
		EntityType<?>[] types = {EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.EVOKER};
		int spawned = 0;
		for (int i = 0; i < escortOwed.length && spawned < SPAWNS_PER_TICK; i++) {
			while (escortOwed[i] > 0 && spawned < SPAWNS_PER_TICK) {
				BlockPos pos = EventSpawns.findSpawn(level, center(), present, level.random);
				if (pos == null) {
					return;
				}
				@SuppressWarnings("unchecked")
				Mob mob = createMob(level, (EntityType<? extends Mob>) types[i], pos);
				if (mob == null) {
					escortOwed[i]--;
					continue;
				}
				own(mob);
				EventSpawns.place(level, mob, pos, center());
				escortOwed[i]--;
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
		if (mob instanceof net.minecraft.world.entity.raid.Raider raider) {
			raider.setCanJoinRaid(false);
		}
		// Keep the fight at the village: idle wandering stays inside this radius, and the event tick
		// pulls back anything that gets kited further out (see EventInstance#tetherOwnedMobs).
		mob.restrictTo(center(), (int) Math.round(TETHER_RADIUS));
		return mob;
	}

	private void spawnBoss(ServerLevel level) {
		List<ServerPlayer> present = participants().present();
		BlockPos pos = present.isEmpty() ? null : EventSpawns.findSpawn(level, center(), present, level.random);
		if (pos == null) {
			pos = center();
		}
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(level);
		if (boss == null) {
			ProjectHeroMod.LOGGER.error("[SupervillainRaid] boss entity failed to create — completing raid");
			complete(level);
			return;
		}
		SupervillainVariant variant = variant() == null ? SupervillainVariant.random(level.random) : variant();
		variantId = variant.id();
		if (powerKey.isEmpty()) {
			powerKey = BossPowers.randomSupervillainKey(level.random);
		}
		boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		boss.configureAsSupervillain(variant, powerKey, Math.max(1, participants().presentCount()));
		own(boss);
		bossId = boss.getUUID();
		EventSpawns.place(level, boss, pos, center());

		// Small escort (spec section 16).
		int players = Math.max(1, participants().presentCount());
		escortOwed = new int[] {
				3 + level.random.nextInt(3) + (players - 1),
				3 + level.random.nextInt(3) + (players - 1),
				1};

		phase = Phase.BOSS_ACTIVE;
		broadcastTitle(level,
				Component.empty().append(variant.displayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("event.projecthero.supervillain_raid.power", BossPowers.displayName(powerKey))
						.withStyle(ChatFormatting.GOLD));
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.3f, 0.8f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
				1, 0, 0, 0, 0);
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] Supervillain spawned: {} / {}", variantId, powerKey);
	}

	// ---------------- kill / completion hooks ----------------

	/** Called by {@link SupervillainRaidRewards} when this raid's boss dies. */
	public void onBossDied(BlockPos at) {
		bossDefeated = true;
		bossDeathPos = at;
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		clearRaidBar(level);
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		clearRaidBar(level);
		BlockPos rewardAt = bossDeathPos != null ? bossDeathPos : center();
		if (success) {
			broadcastTitle(level,
					Component.translatable("event.projecthero.supervillain_raid.defeated")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("event.projecthero.supervillain_raid.safe").withStyle(ChatFormatting.GREEN));
			level.playSound(null, rewardAt, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			if (!rewardsGranted) {
				rewardsGranted = true;
				SupervillainRaidRewards.grantVictory(level, this, rewardAt);
			}
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] Raid completed — village safe");
		} else {
			broadcastTitle(level,
					Component.translatable("event.projecthero.supervillain_raid.fallen")
							.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					null);
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] Raid failed — the village has fallen");
		}
		if (!cooldownStarted) {
			cooldownStarted = true;
			SupervillainVillages.get(level).startCooldown(level, center());
		}
	}

	public Vec3 bossPosition(ServerLevel level) {
		if (bossId != null && level.getEntity(bossId) != null) {
			return level.getEntity(bossId).position();
		}
		return Vec3.atCenterOf(center());
	}

	// ---------------- debug hooks ----------------

	@Override
	protected void onAborted(ServerLevel level) {
		clearRaidBar(level);
	}

	public void debugSkipToBoss(ServerLevel level) {
		if (state() == EventState.PENDING) {
			begin(level);
		}
		cleanupOwnedMobs(level);
		java.util.Arrays.fill(owed, 0);
		wave = SupervillainRaidWaves.count() - 1;
		beginBossReveal(level);
		phaseTicks = 40;
	}

	public void debugSkipCountdown(ServerLevel level) {
		if (state() == EventState.PENDING) {
			begin(level);
		}
		if (phase == Phase.COUNTDOWN) {
			phaseTicks = 20;
		}
	}

	// ---------------- persistence ----------------

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("Phase", phase.name());
		tag.putInt("PhaseTicks", phaseTicks);
		tag.putInt("CountdownTotalTicks", countdownTotalTicks);
		tag.putInt("Wave", wave);
		tag.putIntArray("Owed", owed);
		tag.putIntArray("EscortOwed", escortOwed);
		tag.putBoolean("BossOwed", bossOwed);
		if (bossId != null) {
			tag.putUUID("BossId", bossId);
		}
		tag.putBoolean("BossDefeated", bossDefeated);
		tag.putBoolean("RewardsGranted", rewardsGranted);
		tag.putBoolean("CooldownStarted", cooldownStarted);
		tag.putString("VariantId", variantId);
		tag.putString("PowerKey", powerKey);
		if (bossDeathPos != null) {
			tag.putLong("BossDeathPos", bossDeathPos.asLong());
		}
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("Phase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.COUNTDOWN;
		}
		phaseTicks = tag.getInt("PhaseTicks");
		countdownTotalTicks = tag.getInt("CountdownTotalTicks");
		wave = tag.getInt("Wave");
		owed = tag.getIntArray("Owed");
		escortOwed = tag.getIntArray("EscortOwed");
		bossOwed = tag.getBoolean("BossOwed");
		bossId = tag.hasUUID("BossId") ? tag.getUUID("BossId") : null;
		bossDefeated = tag.getBoolean("BossDefeated");
		rewardsGranted = tag.getBoolean("RewardsGranted");
		cooldownStarted = tag.getBoolean("CooldownStarted");
		variantId = tag.getString("VariantId");
		powerKey = tag.getString("PowerKey");
		if (tag.contains("BossDeathPos")) {
			bossDeathPos = BlockPos.of(tag.getLong("BossDeathPos"));
		}
	}
}
