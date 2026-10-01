package com.projecthero.mod.horde;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.raid.ZombieRaidNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.12: a Horde -- the raid a Horde block starts when it is right-clicked ({@link HordeKind}: zombie, skeleton or
 * spider). Everything pours out of the block itself:
 * <ol>
 *   <li><b>Countdown</b> (5 s): everyone within {@link #ARENA} blocks is a <em>fighter</em> and is now locked in.</li>
 *   <li><b>Waves</b>: {@link #WAVES} waves, each bigger than the last ({@link #waveSize}), spilling out of the block a
 *       few mobs at a time, with a short breather between them -- several hundred mobs over the raid.</li>
 *   <li><b>Boss</b>: the kind's boss climbs out (a Titan, the Bone Tyrant or the Brood Queen) with a trickle of
 *       escorts; killing it wins.</li>
 * </ol>
 * <b>The arena</b>: a fighter who walks past {@link #ARENA} blocks is pulled back in (a coloured ring marks the edge).
 * Anyone who walks in later joins the fight and is locked in too. <b>Dying fails you</b>: a fighter who dies is out
 * of the raid for good (pushed out if they come back); when every fighter is out, the horde wins and the block
 * crumbles. <b>Victory</b>: the block turns into a chest of loot ({@link HordeRewards}), better for the harder hordes.
 */
public class HordeRaid extends EventInstance {
	public static final double ARENA = 36.0;
	public static final int WAVES = 8;
	static final int COUNTDOWN_TICKS = 5 * 20;
	static final int BREATHER_TICKS = 8 * 20;
	/** Mobs out of the block per framework tick (every 10 game ticks). */
	static final int SPAWNS_PER_TICK = 4;
	/** Live horde mobs at once (the framework's own ceiling still applies on top). */
	static final int MAX_ALIVE = 70;
	/** A wave down to its last few mobs this long glows them; twice this long and they are dropped. */
	static final int STRAGGLER_TICKS = 90 * 20;

	private enum Phase { COUNTDOWN, WAVE, BREATHER, BOSS }

	private final HordeKind kind;
	private Phase phase = Phase.COUNTDOWN;
	private long phaseStart = -1;
	private int wave;
	private int owed;
	private int waveTotal;
	private UUID bossId;
	private int escortTimer;
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> fallen = new HashSet<>();
	private transient EventBossBar bar;

	public HordeRaid(UUID id, HordeKind kind) {
		super(id);
		this.kind = kind;
	}

	public HordeKind kind() {
		return kind;
	}

	@Override
	public String typeId() {
		return kind.typeId();
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero." + kind.typeId());
	}

	@Override
	public double radius() {
		return ARENA + 24.0;
	}

	public Set<UUID> fighters() {
		return fighters;
	}

	public boolean isFighter(UUID player) {
		return fighters.contains(player) && !fallen.contains(player);
	}

	public int wave() {
		return wave;
	}

	public boolean bossPhase() {
		return phase == Phase.BOSS;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void onTick(ServerLevel level) {
		long now = level.getGameTime();
		if (state() == EventState.PENDING) {
			setState(EventState.RUNNING);
			phaseStart = now;
			lockInFighters(level);
			broadcastTitle(level, displayName().copy().withStyle(kind.color()),
					Component.translatable("event.projecthero.horde.locked_in").withStyle(ChatFormatting.GRAY));
			level.playSound(null, center(), kind.startSound(), SoundSource.HOSTILE, 2.0f, 0.6f);
		}
		if (phaseStart < 0) {
			phaseStart = now; // loaded from an old save
		}
		keepTheBlock(level);
		enforceArena(level);
		ZombieRaidNetworking.pushSky(level, center(), ARENA, kind.skyColor());
		drawBorder(level, now);
		tetherOwnedMobs(level, ARENA + 4);
		long inPhase = now - phaseStart;
		if (phase == Phase.WAVE || phase == Phase.BOSS) {
			retarget(level);
		}
		switch (phase) {
			case COUNTDOWN -> {
				if (inPhase >= COUNTDOWN_TICKS) {
					startWave(level, now, 1);
				}
			}
			case WAVE -> tickWave(level, now, inPhase);
			case BREATHER -> {
				if (inPhase >= BREATHER_TICKS) {
					if (wave >= WAVES) {
						startBoss(level, now);
					} else {
						startWave(level, now, wave + 1);
					}
				}
			}
			case BOSS -> tickBoss(level);
		}
		if (!state().finished()) {
			updateBar(level, now);
		}
	}

	private void lockInFighters(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (eligible(p) && horizontalDistance(p) <= ARENA) {
				fighters.add(p.getUUID());
			}
		}
	}

	private static boolean eligible(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	private double horizontalDistance(Entity e) {
		BlockPos c = center();
		double dx = e.getX() - (c.getX() + 0.5);
		double dz = e.getZ() - (c.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Fighters stay in; anyone who wanders in joins; the fallen stay out. */
	private void enforceArena(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!eligible(p)) {
				continue;
			}
			double d = horizontalDistance(p);
			UUID u = p.getUUID();
			if (fallen.contains(u)) {
				if (d < ARENA + 2) {
					teleportTo(level, p, ARENA + 6);
					p.displayClientMessage(Component.translatable("event.projecthero.horde.fallen_keep_out").withStyle(ChatFormatting.RED), true);
				}
				continue;
			}
			if (!fighters.contains(u)) {
				if (d <= ARENA) {
					fighters.add(u);
					p.displayClientMessage(Component.translatable("event.projecthero.horde.joined").withStyle(kind.color()), true);
				}
				continue;
			}
			if (d > ARENA) {
				teleportTo(level, p, ARENA - 4);
				p.displayClientMessage(Component.translatable("event.projecthero.horde.no_escape").withStyle(ChatFormatting.RED), true);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.6f);
			}
		}
	}

	private void teleportTo(ServerLevel level, ServerPlayer p, double distance) {
		BlockPos c = center();
		double dx = p.getX() - (c.getX() + 0.5);
		double dz = p.getZ() - (c.getZ() + 0.5);
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-3) {
			dx = 1;
			len = 1;
		}
		double x = c.getX() + 0.5 + dx / len * distance;
		double z = c.getZ() + 0.5 + dz / len * distance;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		double y = Math.max(surface, Math.min(p.getY(), surface + 16.0));
		p.teleportTo(x, y, z);
		p.fallDistance = 0;
	}

	/** The edge of the arena, drawn every half second as a ring of the horde's colour at the fighters' height. */
	private void drawBorder(ServerLevel level, long now) {
		BlockPos c = center();
		DustParticleOptions dust = new DustParticleOptions(kind.borderColor(), 1.6f);
		int points = 72;
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (horizontalDistance(p) < ARENA + 48) {
				viewers.add(p);
			}
		}
		if (viewers.isEmpty()) {
			return;
		}
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points + (now % 40) * 0.004;
			double x = c.getX() + 0.5 + Math.cos(a) * ARENA;
			double z = c.getZ() + 0.5 + Math.sin(a) * ARENA;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			// long-range (forced) particles, so the ring shows from anywhere in the arena -- normal ones vanish past ~32 blocks
			for (ServerPlayer viewer : viewers) {
				level.sendParticles(viewer, dust, true, x, y + 0.6, z, 1, 0, 0.4, 0, 0);
				level.sendParticles(viewer, dust, true, x, y + 2.0, z, 1, 0, 0.4, 0, 0);
			}
		}
	}

	/** The block is the horde's mouth: if something knocks it out mid-raid it comes straight back. */
	private void keepTheBlock(ServerLevel level) {
		if (!level.getBlockState(center()).is(kind.block())) {
			level.setBlock(center(), kind.block().defaultBlockState().setValue(HordeBlock.ACTIVE, true), 3);
		} else if (!level.getBlockState(center()).getValue(HordeBlock.ACTIVE)) {
			level.setBlock(center(), level.getBlockState(center()).setValue(HordeBlock.ACTIVE, true), 3);
		}
	}

	// ---------------------------------------------------------------- waves

	/** Wave {@code n}'s size for {@code players} fighters: 18, 24, ... 60 alone, half again per extra fighter. */
	public static int waveSize(int n, int players) {
		double base = 12 + 6.0 * n;
		return Math.min(150, (int) Math.round(base * (1.0 + 0.5 * Math.max(0, players - 1))));
	}

	private int fighterCount() {
		return Math.max(1, fighters.size() - fallen.size());
	}

	private void startWave(ServerLevel level, long now, int n) {
		wave = n;
		waveTotal = waveSize(n, fighterCount());
		owed = waveTotal;
		phase = Phase.WAVE;
		phaseStart = now;
		broadcastTitle(level, Component.translatable("event.projecthero.horde.wave", n, WAVES).withStyle(kind.color()),
				Component.translatable("event.projecthero.horde.wave.sub", waveTotal).withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), kind.startSound(), SoundSource.HOSTILE, 1.6f, 0.8f + n * 0.04f);
	}

	private void tickWave(ServerLevel level, long now, long inPhase) {
		int alive = ownedAlive(level);
		if (owed > 0) {
			spawnFromBlock(level, Math.min(SPAWNS_PER_TICK, owed), alive, false);
			return;
		}
		if (alive == 0) {
			phase = Phase.BREATHER;
			phaseStart = now;
			broadcast(level, Component.translatable(wave >= WAVES ? "event.projecthero.horde.boss_soon" : "event.projecthero.horde.wave_cleared",
					wave).withStyle(kind.color()));
			return;
		}
		// stragglers stuck somewhere: light them up, then give up on them
		if (alive <= 5 && inPhase > STRAGGLER_TICKS) {
			for (Mob m : liveOwnedMobs(level)) {
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
				if (inPhase > STRAGGLER_TICKS * 2) {
					disown(m.getUUID());
					m.discard();
				}
			}
		}
	}

	/** Spits {@code count} mobs out of the block (respecting the live caps). */
	private void spawnFromBlock(ServerLevel level, int count, int alive, boolean escort) {
		int room = Math.min(MAX_ALIVE, EventConfig.framework().maxLiveMobs) - alive;
		int n = Math.min(count, room);
		for (int i = 0; i < n; i++) {
			Mob mob = HordeWaves.create(level, kind, wave, escort);
			if (mob == null) {
				continue;
			}
			Vec3 at = spawnSpot(level);
			mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
			HordeWaves.dress(mob, kind, wave);
			mob.restrictTo(center(), (int) ARENA);
			own(mob);
			level.addFreshEntity(mob);
			LivingEntity target = nearestFighter(level, mob.position());
			if (target != null) {
				mob.setTarget(target);
			}
			if (!escort) {
				owed--;
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 4, 0.2, 0.3, 0.2, 0.02);
		}
		if (n > 0) {
			level.sendParticles(kind.burstParticle(), center().getX() + 0.5, center().getY() + 1.1, center().getZ() + 0.5,
					10, 0.3, 0.2, 0.3, 0.05);
			if (level.random.nextInt(3) == 0) {
				level.playSound(null, center(), kind.spawnSound(), SoundSource.HOSTILE, 1.0f, 0.7f + level.random.nextFloat() * 0.4f);
			}
		}
	}

	/** On the ground right round the block. */
	private Vec3 spawnSpot(ServerLevel level) {
		BlockPos c = center();
		double a = level.random.nextDouble() * Math.PI * 2;
		double r = 1.2 + level.random.nextDouble() * 1.8;
		double x = c.getX() + 0.5 + Math.cos(a) * r;
		double z = c.getZ() + 0.5 + Math.sin(a) * r;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		return new Vec3(x, Math.max(y, c.getY()), z);
	}

	private ServerPlayer nearestFighter(ServerLevel level, Vec3 from) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (UUID u : fighters) {
			if (fallen.contains(u) || !(level.getPlayerByUUID(u) instanceof ServerPlayer p) || !eligible(p)) {
				continue;
			}
			double d = p.distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Any horde mob without a target (a cave spider that lost interest in daylight, say) goes for the nearest fighter. */
	private void retarget(ServerLevel level) {
		for (Mob m : liveOwnedMobs(level)) {
			LivingEntity t = m.getTarget();
			if (t == null || !t.isAlive() || t instanceof ServerPlayer p && !isFighter(p.getUUID())) {
				ServerPlayer next = nearestFighter(level, m.position());
				if (next != null) {
					m.setTarget(next);
				}
			}
		}
	}

	// ---------------------------------------------------------------- boss

	private void startBoss(ServerLevel level, long now) {
		phase = Phase.BOSS;
		phaseStart = now;
		escortTimer = 0;
		Vec3 at = spawnSpot(level);
		LivingEntity boss = HordeWaves.createBoss(level, kind, at, fighterCount());
		if (boss == null) {
			complete(level); // nothing to fight: never leave a raid stuck
			return;
		}
		bossId = boss.getUUID();
		if (boss instanceof Mob mob) {
			own(mob);
			LivingEntity target = nearestFighter(level, at);
			if (target != null) {
				mob.setTarget(target);
			}
		}
		broadcastTitle(level, boss.getDisplayName().copy().withStyle(kind.color(), ChatFormatting.BOLD),
				Component.translatable("event.projecthero.horde.boss.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, kind.bossPitch());
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
	}

	private LivingEntity boss(ServerLevel level) {
		return bossId == null ? null : level.getEntity(bossId) instanceof LivingEntity l ? l : null;
	}

	private void tickBoss(ServerLevel level) {
		LivingEntity boss = boss(level);
		if (boss == null || !boss.isAlive() || boss.isDeadOrDying()) {
			victory(level);
			return;
		}
		if (++escortTimer >= 40) { // every 20 s, a handful more from the block
			escortTimer = 0;
			spawnFromBlock(level, 6, ownedAlive(level), true);
		}
	}

	private void victory(ServerLevel level) {
		broadcastTitle(level, Component.translatable("event.projecthero.horde.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("event.projecthero.horde.victory.sub").withStyle(ChatFormatting.GRAY));
		complete(level);
	}

	/** {@code /projecthero raid advancetimer <horde>}: on to the next stage now (a running wave's mobs are dropped). */
	public void debugAdvance(ServerLevel level) {
		long now = level.getGameTime();
		switch (phase) {
			case COUNTDOWN -> startWave(level, now, 1);
			case WAVE -> {
				for (Mob m : liveOwnedMobs(level)) {
					disown(m.getUUID());
					m.discard();
				}
				owed = 0;
				phaseStart = now - BREATHER_TICKS;
				phase = Phase.BREATHER;
			}
			case BREATHER -> phaseStart = now - BREATHER_TICKS;
			case BOSS -> {
				LivingEntity boss = boss(level);
				if (boss != null) {
					boss.kill();
				}
			}
		}
	}

	// ---------------------------------------------------------------- deaths and the end

	/** A fighter died: they are out of the raid. When every fighter is out, the horde wins. */
	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!isFighter(player.getUUID()) || state().finished()) {
			return;
		}
		fallen.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		player.sendSystemMessage(Component.translatable("event.projecthero.horde.you_failed", displayName()).withStyle(ChatFormatting.RED));
		broadcast(level, Component.translatable("event.projecthero.horde.fighter_fell", player.getDisplayName()).withStyle(ChatFormatting.RED));
		boolean anyLeft = false;
		for (UUID u : fighters) {
			if (!fallen.contains(u)) {
				anyLeft = true;
				break;
			}
		}
		if (!anyLeft) {
			fail(level, Component.translatable("event.projecthero.horde.defeat", displayName()).withStyle(ChatFormatting.DARK_RED));
		}
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		tearDown(level);
		BlockPos c = center();
		if (success) {
			List<UUID> winners = new ArrayList<>();
			for (UUID u : fighters) {
				if (!fallen.contains(u)) {
					winners.add(u);
				}
			}
			HordeRewards.placeChest(level, c, kind, winners.size());
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
		} else {
			// the horde won: its block crumbles away
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.02);
			level.playSound(null, c, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0f, 1.5f);
		}
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
		if (level.getBlockState(center()).is(kind.block())) {
			level.setBlock(center(), kind.block().defaultBlockState(), 3);
		}
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		if (bar != null) {
			bar.clear(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
	}

	private void tearDown(ServerLevel level) {
		if (bar != null) {
			bar.clear(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
		LivingEntity boss = boss(level);
		if (boss != null && boss.isAlive()) {
			boss.discard();
		}
	}

	// ---------------------------------------------------------------- the bar

	private void updateBar(ServerLevel level, long now) {
		if (bar == null) {
			bar = new EventBossBar(id(), kind.barColor(), BossEvent.BossBarOverlay.NOTCHED_10, true);
		}
		Component name;
		float progress;
		long inPhase = now - phaseStart;
		switch (phase) {
			case COUNTDOWN -> {
				int secs = (int) Math.ceil((COUNTDOWN_TICKS - inPhase) / 20.0);
				name = Component.translatable("event.projecthero.horde.bar.countdown", displayName(), Math.max(0, secs));
				progress = Mth.clamp(inPhase / (float) COUNTDOWN_TICKS, 0f, 1f);
			}
			case BREATHER -> {
				name = Component.translatable("event.projecthero.horde.bar.breather", displayName(), wave, WAVES);
				progress = Mth.clamp(inPhase / (float) BREATHER_TICKS, 0f, 1f);
			}
			case WAVE -> {
				int left = owed + ownedAlive(level);
				name = Component.translatable("event.projecthero.horde.bar.wave", displayName(), wave, WAVES, left);
				progress = waveTotal <= 0 ? 0f : Mth.clamp(left / (float) waveTotal, 0f, 1f);
			}
			default -> {
				LivingEntity boss = boss(level);
				name = Component.translatable("event.projecthero.horde.bar.boss", displayName(),
						boss == null ? Component.empty() : boss.getDisplayName());
				progress = boss == null ? 0f : Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0f, 1f);
			}
		}
		bar.update(level, center(), ARENA + 24, name.copy().withStyle(kind.color()), progress);
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("HordePhase", phase.name());
		tag.putLong("HordePhaseStart", phaseStart);
		tag.putInt("HordeWave", wave);
		tag.putInt("HordeOwed", owed);
		tag.putInt("HordeWaveTotal", waveTotal);
		tag.putInt("HordeEscortTimer", escortTimer);
		if (bossId != null) {
			tag.putUUID("HordeBoss", bossId);
		}
		tag.put("HordeFighters", uuids(fighters));
		tag.put("HordeFallen", uuids(fallen));
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("HordePhase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.COUNTDOWN;
		}
		phaseStart = tag.getLong("HordePhaseStart");
		wave = tag.getInt("HordeWave");
		owed = tag.getInt("HordeOwed");
		waveTotal = tag.getInt("HordeWaveTotal");
		escortTimer = tag.getInt("HordeEscortTimer");
		bossId = tag.hasUUID("HordeBoss") ? tag.getUUID("HordeBoss") : null;
		readUuids(tag.getList("HordeFighters", Tag.TAG_INT_ARRAY), fighters);
		readUuids(tag.getList("HordeFallen", Tag.TAG_INT_ARRAY), fallen);
	}

	private static ListTag uuids(Set<UUID> set) {
		ListTag list = new ListTag();
		for (UUID u : set) {
			list.add(NbtUtils.createUUID(u));
		}
		return list;
	}

	private static void readUuids(ListTag list, Set<UUID> into) {
		into.clear();
		for (int i = 0; i < list.size(); i++) {
			into.add(NbtUtils.loadUUID(list.get(i)));
		}
	}

	/** Colours for the border ring, as dust particle colours. */
	static Vector3f rgb(int color) {
		return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
	}
}
