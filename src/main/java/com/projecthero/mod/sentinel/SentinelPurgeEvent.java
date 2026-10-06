package com.projecthero.mod.sentinel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.raid.ZombieRaidNetworking;
import com.projecthero.mod.sentinel.entity.MasterMoldEntity;
import com.projecthero.mod.sentinel.entity.SentinelDroneEntity;
import com.projecthero.mod.sentinel.entity.SentinelEntity;
import com.projecthero.mod.sentinel.entity.SentinelEntityTypes;
import com.projecthero.mod.sentinel.entity.SentinelRobot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.15.1: the <b>Sentinel Purge</b> -- Bolivar Trask's Sentinel Program comes for the world's superhumans. Built on the
 * mod's world-event framework ({@link EventInstance}: persistence, owned-mob tracking and cleanup, pause/abandon).
 *
 * <pre>
 * DETECTION (10 s: "MUTANT DETECTED") -> WAVE 1 -> BREATHER -> ... -> WAVE n -> BREATHER -> MASTER MOLD -> victory
 *                                     (every fighter down at once, at any point) -> the purge succeeds (defeat)
 * </pre>
 * <ul>
 *   <li><b>Fighters</b>: every player (not spectating, not creative) within {@link #REGISTRATION_RADIUS} when the purge
 *       starts, plus anyone who walks into it later.</li>
 *   <li><b>Waves</b> ({@link SentinelConfig.Waves}): units come down out of the sky round the purge -- Sentinels on
 *       their boot thrusters, Drones straight into a hover -- half again as many per extra fighter, never more than the
 *       live cap at once (the rest wait for room). Wave 1 is all Drones; Sentinels join from wave 2.</li>
 *   <li><b>The boss</b>: Master Mold descends after the last wave with an escort of Sentinels. Destroying it wins.</li>
 *   <li><b>Defeat</b>: a fighter who dies is "down" until they walk back in; when every fighter is down at the same
 *       time the purge has done its job and ends.</li>
 *   <li><b>Victory</b>: {@link SentinelRewards} pays every fighter still standing.</li>
 * </ul>
 * Every Sentinel Program unit it sends is owned by the event (cleaned up with it) and carries the purge's id, so one left
 * in an unloaded chunk removes itself (its orphan guard) when it next ticks.
 */
public class SentinelPurgeEvent extends EventInstance {
	public static final String TYPE_ID = "sentinel_purge";
	public static final double REGISTRATION_RADIUS = 48.0;
	public static final double ARENA = 64.0;
	public static final int MAX_WAVES = 6;
	/** Units it brings down per framework tick (every 10 game ticks) while a wave still owes some. */
	static final int ARRIVALS_PER_TICK = 4;
	static final int STRAGGLER_TICKS = 90 * 20;
	static final int SKY_COLOR = 0x2A1040;
	private static final DustParticleOptions SCAN = new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.85f), 1.6f);

	public enum Phase { DETECTION, WAVE, BREATHER, BOSS }

	private Phase phase = Phase.DETECTION;
	private long phaseStart = -1;
	private int wave;
	private int waveTotal;
	private int owedDrones;
	private int owedSentinels;
	private UUID bossId;
	private UUID activatorId;
	private String activatorName = "";
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> down = new HashSet<>();
	private transient EventBossBar bar;
	/** Test hook: arrivals land this far from the centre instead of the configured ring (and this high), when >= 0. */
	public transient double arrivalRadiusOverride = -1;
	public transient double arrivalHeightOverride = -1;

	public SentinelPurgeEvent(UUID id) {
		super(id);
	}

	// ---------------------------------------------------------------- identity

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.sentinel_purge");
	}

	@Override
	public double radius() {
		return ARENA + 24.0;
	}

	/** The running purge with this id, or null. */
	public static SentinelPurgeEvent find(ServerLevel level, UUID id) {
		if (id == null) {
			return null;
		}
		return EventSavedData.get(level).byId(id) instanceof SentinelPurgeEvent p && p.state().active() ? p : null;
	}

	/** Every running purge on the server. */
	public static List<SentinelPurgeEvent> all(MinecraftServer server) {
		List<SentinelPurgeEvent> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof SentinelPurgeEvent p) {
				out.add(p);
			}
		}
		return out;
	}

	/** How many waves come before Master Mold (the config, clamped to 1..{@link #MAX_WAVES}). */
	public static int waveCount() {
		return Mth.clamp(SentinelConfig.waves().waveCount, 1, MAX_WAVES);
	}

	/** Wave {@code n}'s solo composition as {drones, sentinels}. */
	public static int[] composition(int n) {
		SentinelConfig.Waves cfg = SentinelConfig.waves();
		int i = Mth.clamp(n, 1, MAX_WAVES) - 1;
		return new int[] { at(cfg.drones, i, 4), at(cfg.sentinels, i, Math.min(6, i + 1)) };
	}

	private static int at(int[] arr, int i, int fallback) {
		return arr != null && i < arr.length ? Math.max(0, arr[i]) : fallback;
	}

	/** {@code base} scaled for {@code fighters} fighters. */
	public static int scaled(int base, int fighters) {
		if (base <= 0) {
			return 0;
		}
		return (int) Math.round(base * (1.0 + SentinelConfig.waves().perExtraFighter * Math.max(0, fighters - 1)));
	}

	public Phase phase() {
		return phase;
	}

	public int wave() {
		return wave;
	}

	public Set<UUID> fighters() {
		return fighters;
	}

	public boolean isFighter(UUID player) {
		return fighters.contains(player);
	}

	public UUID activatorId() {
		return activatorId;
	}

	public int owed() {
		return owedDrones + owedSentinels;
	}

	public MasterMoldEntity boss(ServerLevel level) {
		return bossId != null && level.getEntity(bossId) instanceof MasterMoldEntity m ? m : null;
	}

	/** Units of this purge alive right now, by class -- {drones, sentinels, master molds}. */
	public int[] liveCounts(ServerLevel level) {
		int[] n = new int[3];
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof SentinelDroneEntity) n[0]++;
			else if (m instanceof SentinelEntity) n[1]++;
			else if (m instanceof MasterMoldEntity) n[2]++;
		}
		return n;
	}

	/** Whether the live cap leaves room for another unit. */
	public boolean hasRoom(ServerLevel level) {
		return ownedAlive(level) < liveCap();
	}

	private static int liveCap() {
		return Math.min(SentinelConfig.waves().enemyCap, EventConfig.framework().maxLiveMobs);
	}

	/** Fighters still standing (at least 1) -- what wave sizes and Master Mold's health scale with. */
	public int fighterCount() {
		return Math.max(1, fighters.size() - down.size());
	}

	// ---------------------------------------------------------------- activation

	/** Called by {@link SentinelPurge#start}: the activator (may be null) and the lock-in of everyone nearby. */
	void activate(ServerLevel level, ServerPlayer activator) {
		if (activator != null) {
			activatorId = activator.getUUID();
			activatorName = activator.getGameProfile().getName();
		}
		setState(EventState.RUNNING);
		phase = Phase.DETECTION;
		phaseStart = level.getGameTime();
		BlockPos c = center();
		for (ServerPlayer p : level.players()) {
			if (eligible(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= REGISTRATION_RADIUS * REGISTRATION_RADIUS) {
				fighters.add(p.getUUID());
			}
		}
		participants().refresh(level, c, EventConfig.framework().abandonRadius, true);
		broadcastTitle(level, Component.translatable("title.projecthero.sentinel_purge.detected").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.sentinel_purge.detected.sub").withStyle(ChatFormatting.DARK_PURPLE));
		for (ServerPlayer p : level.players()) {
			if (fighters.contains(p.getUUID())) {
				SentinelTargets.Threat threat = SentinelTargets.classify(p);
				p.sendSystemMessage(Component.translatable("message.projecthero.sentinel_purge.classified", p.getDisplayName(),
						Component.translatable(threat.langKey())).withStyle(threat == SentinelTargets.Threat.MUTANT ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));
			}
		}
		level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		level.playSound(null, c, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.6f);
		ProjectHeroMod.LOGGER.info("[ProjectHero] Sentinel Purge {} started at {} with {} fighter(s)", id(), c, fighters.size());
	}

	static boolean eligible(Player p) {
		return p.isAlive() && !p.isSpectator() && !p.getAbilities().invulnerable;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void onTick(ServerLevel level) {
		long now = level.getGameTime();
		if (state() == EventState.PENDING) {
			activate(level, null);
		}
		if (phaseStart < 0) {
			phaseStart = now;
		}
		trackFighters(level);
		ZombieRaidNetworking.pushSky(level, center(), ARENA, SKY_COLOR);
		tetherOwnedMobs(level, ARENA);
		long inPhase = now - phaseStart;
		switch (phase) {
			case DETECTION -> {
				scanFx(level, now);
				if (inPhase >= SentinelConfig.waves().detectionSeconds * 20L) {
					startWave(level, 1);
				}
			}
			case WAVE -> tickWave(level, now, inPhase);
			case BREATHER -> {
				if (inPhase >= SentinelConfig.waves().breatherSeconds * 20L) {
					if (wave >= waveCount()) {
						startBoss(level);
					} else {
						startWave(level, wave + 1);
					}
				}
			}
			case BOSS -> tickBoss(level);
		}
		if (!state().finished()) {
			updateBar(level, now);
		}
	}

	/** Anyone who walks in joins; a downed fighter back on their feet inside is up again. */
	private void trackFighters(ServerLevel level) {
		BlockPos c = center();
		double r2 = ARENA * ARENA;
		for (ServerPlayer p : level.players()) {
			if (!eligible(p) || p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) > r2) {
				continue;
			}
			if (fighters.add(p.getUUID())) {
				p.displayClientMessage(Component.translatable("message.projecthero.sentinel_purge.joined").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
			down.remove(p.getUUID());
		}
	}

	/** The detection sweep: magenta scan rings round every fighter and drop beacons round the purge. */
	private void scanFx(ServerLevel level, long now) {
		for (ServerPlayer p : level.players()) {
			if (!fighters.contains(p.getUUID())) {
				continue;
			}
			double r = 1.2 + (now % 20) * 0.12;
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				level.sendParticles(SCAN, p.getX() + Math.cos(a) * r, p.getY() + 0.2, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		if (now % 20 == 0) {
			level.playSound(null, center(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- waves

	/** Begins wave {@code n} and brings down as many of its units as the cap allows. */
	public void startWave(ServerLevel level, int n) {
		wave = Mth.clamp(n, 1, MAX_WAVES);
		int[] mix = composition(wave);
		int f = fighterCount();
		owedDrones = scaled(mix[0], f);
		owedSentinels = scaled(mix[1], f);
		waveTotal = owedDrones + owedSentinels;
		phase = Phase.WAVE;
		phaseStart = level.getGameTime();
		broadcastTitle(level, Component.translatable("title.projecthero.sentinel_purge.wave", wave, waveCount()).withStyle(ChatFormatting.LIGHT_PURPLE),
				Component.translatable("title.projecthero.sentinel_purge.wave.sub", owedSentinels, owedDrones).withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.5f);
		spawnOwed(level, waveTotal);
	}

	private void tickWave(ServerLevel level, long now, long inPhase) {
		int alive = ownedAlive(level);
		if (owed() > 0) {
			spawnOwed(level, ARRIVALS_PER_TICK);
			return;
		}
		if (alive == 0) {
			phase = Phase.BREATHER;
			phaseStart = now;
			broadcast(level, Component.translatable(wave >= waveCount() ? "message.projecthero.sentinel_purge.boss_soon"
					: "message.projecthero.sentinel_purge.wave_cleared", wave).withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (alive <= 3 && inPhase > STRAGGLER_TICKS) {
			for (Mob m : liveOwnedMobs(level)) {
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
				if (inPhase > STRAGGLER_TICKS * 2) {
					disown(m.getUUID());
					m.discard();
				}
			}
		}
	}

	/** Brings down up to {@code max} owed units, Sentinels first, while the cap has room. */
	private void spawnOwed(ServerLevel level, int max) {
		int made = 0;
		while (made < max && owed() > 0 && hasRoom(level)) {
			boolean sentinel = owedSentinels > 0 && (owedDrones == 0 || level.random.nextFloat() < 0.6f);
			Mob m = spawnUnit(level, sentinel ? SentinelEntityTypes.SENTINEL : SentinelEntityTypes.SENTINEL_DRONE);
			if (sentinel) {
				owedSentinels--;
			} else {
				owedDrones--;
			}
			if (m != null) {
				made++;
			}
		}
	}

	/** One unit, dropped in out of the sky round the purge and pointed at a fighter. Null if it could not be made. */
	public Mob spawnUnit(ServerLevel level, EntityType<? extends Mob> type) {
		Mob mob = type.create(level);
		if (mob == null) {
			return null;
		}
		double height = type == SentinelEntityTypes.SENTINEL_DRONE ? 12.0 : type == SentinelEntityTypes.MASTER_MOLD ? 34.0
				: SentinelConfig.waves().arrivalHeight;
		Vec3 at = arrivalSpot(level, height);
		mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		if (mob instanceof SentinelEntity s) {
			s.rollCarrier();
			s.startArrival();
		} else if (mob instanceof MasterMoldEntity mm) {
			mm.configure(fighterCount());
			mm.startArrival();
		}
		if (mob instanceof SentinelRobot r) {
			r.bindToPurge(id());
		}
		own(mob);
		level.addFreshEntity(mob);
		Player target = SentinelTargets.pickTarget(level, mob, ARENA * 2);
		if (target != null) {
			mob.setTarget(target);
		}
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		return mob;
	}

	/** A point on the arrival ring, {@code height} blocks above the ground (the centre if that ground isn't loaded). */
	private Vec3 arrivalSpot(ServerLevel level, double height) {
		BlockPos c = center();
		SentinelConfig.Waves cfg = SentinelConfig.waves();
		double min = arrivalRadiusOverride >= 0 ? arrivalRadiusOverride : cfg.arrivalRadiusMin;
		double max = arrivalRadiusOverride >= 0 ? arrivalRadiusOverride : Math.max(min, cfg.arrivalRadiusMax);
		double h = arrivalHeightOverride >= 0 ? arrivalHeightOverride : height;
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = min + level.random.nextDouble() * (max - min);
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			BlockPos column = BlockPos.containing(x, c.getY(), z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			return new Vec3(x, Math.max(ground, c.getY()) + h, z);
		}
		return new Vec3(c.getX() + 0.5, c.getY() + h, c.getZ() + 0.5);
	}

	// ---------------------------------------------------------------- the boss

	public void startBoss(ServerLevel level) {
		phase = Phase.BOSS;
		phaseStart = level.getGameTime();
		Mob boss = spawnUnit(level, SentinelEntityTypes.MASTER_MOLD);
		if (boss == null) {
			complete(level); // nothing to fight: never leave a purge stuck
			return;
		}
		bossId = boss.getUUID();
		for (int i = 0; i < scaled(SentinelConfig.waves().bossEscorts, fighterCount()) && hasRoom(level); i++) {
			spawnUnit(level, SentinelEntityTypes.SENTINEL);
		}
		broadcastTitle(level, Component.translatable("title.projecthero.sentinel_purge.boss").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.sentinel_purge.boss.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.5f);
	}

	private void tickBoss(ServerLevel level) {
		MasterMoldEntity boss = boss(level);
		if (boss == null || !boss.isAlive() || boss.isDeadOrDying()) {
			victory(level);
		}
	}

	private void victory(ServerLevel level) {
		if (bossId != null) {
			disown(bossId); // let its five-second death play out instead of vanishing with the cleanup
		}
		broadcastTitle(level, Component.translatable("title.projecthero.sentinel_purge.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.sentinel_purge.victory.sub").withStyle(ChatFormatting.GRAY));
		complete(level);
	}

	/** {@code /projecthero raid advancetimer sentinel_purge}: on to the next stage now (a running wave's units are dropped). */
	public void debugAdvance(ServerLevel level) {
		long now = level.getGameTime();
		switch (phase) {
			case DETECTION -> startWave(level, 1);
			case WAVE -> {
				for (Mob m : liveOwnedMobs(level)) {
					disown(m.getUUID());
					m.discard();
				}
				owedDrones = 0;
				owedSentinels = 0;
				phase = Phase.BREATHER;
				phaseStart = now - SentinelConfig.waves().breatherSeconds * 20L;
			}
			case BREATHER -> phaseStart = now - SentinelConfig.waves().breatherSeconds * 20L;
			case BOSS -> {
				MasterMoldEntity boss = boss(level);
				if (boss != null) {
					boss.kill();
				}
			}
		}
	}

	// ---------------------------------------------------------------- deaths and the end

	/** A fighter died: down until they walk back in. When every fighter is down at once, the purge succeeds. */
	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!fighters.contains(player.getUUID()) || state().finished()) {
			return;
		}
		down.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		SentinelTargets.Threat threat = SentinelTargets.classify(player);
		broadcast(level, Component.translatable("message.projecthero.sentinel_purge.terminated", Component.translatable(threat.langKey()),
				player.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
		if (down.containsAll(fighters)) {
			fail(level, Component.translatable("message.projecthero.sentinel_purge.defeat").withStyle(ChatFormatting.DARK_RED));
		}
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		tearDown(level);
		if (success) {
			List<ServerPlayer> winners = new ArrayList<>();
			for (UUID u : fighters) {
				if (down.contains(u)) {
					continue;
				}
				if (level.getPlayerByUUID(u) instanceof ServerPlayer p && p.isAlive()) {
					winners.add(p);
				}
			}
			SentinelRewards.grant(level, winners);
			level.playSound(null, center(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			ProjectHeroMod.LOGGER.info("[ProjectHero] Sentinel Purge {} repelled; {} fighter(s) rewarded", id(), winners.size());
		}
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
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
	}

	// ---------------------------------------------------------------- the bar

	private void updateBar(ServerLevel level, long now) {
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, true);
		}
		long inPhase = now - phaseStart;
		Component name;
		float progress;
		switch (phase) {
			case DETECTION -> {
				int total = SentinelConfig.waves().detectionSeconds * 20;
				name = Component.translatable("event.projecthero.sentinel_purge.bar.detection", Math.max(0, (int) Math.ceil((total - inPhase) / 20.0)));
				progress = Mth.clamp(inPhase / (float) Math.max(1, total), 0f, 1f);
			}
			case BREATHER -> {
				int total = SentinelConfig.waves().breatherSeconds * 20;
				name = Component.translatable("event.projecthero.sentinel_purge.bar.breather", wave, waveCount());
				progress = Mth.clamp(inPhase / (float) Math.max(1, total), 0f, 1f);
			}
			case WAVE -> {
				int left = owed() + ownedAlive(level);
				name = Component.translatable("event.projecthero.sentinel_purge.bar.wave", wave, waveCount(), left);
				progress = waveTotal <= 0 ? 0f : Mth.clamp(left / (float) waveTotal, 0f, 1f);
			}
			default -> {
				MasterMoldEntity boss = boss(level);
				name = Component.translatable("event.projecthero.sentinel_purge.bar.boss", boss == null ? 1 : boss.phase());
				progress = boss == null ? 0f : Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0f, 1f);
			}
		}
		bar.update(level, center(), radius(), name.copy().withStyle(ChatFormatting.LIGHT_PURPLE), progress);
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("PurgePhase", phase.name());
		tag.putLong("PurgePhaseStart", phaseStart);
		tag.putInt("PurgeWave", wave);
		tag.putInt("PurgeWaveTotal", waveTotal);
		tag.putInt("PurgeOwedDrones", owedDrones);
		tag.putInt("PurgeOwedSentinels", owedSentinels);
		if (bossId != null) {
			tag.putUUID("PurgeBoss", bossId);
		}
		if (activatorId != null) {
			tag.putUUID("PurgeActivator", activatorId);
		}
		tag.putString("PurgeActivatorName", activatorName);
		tag.put("PurgeFighters", uuids(fighters));
		tag.put("PurgeDown", uuids(down));
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("PurgePhase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.DETECTION;
		}
		phaseStart = tag.getLong("PurgePhaseStart");
		wave = tag.getInt("PurgeWave");
		waveTotal = tag.getInt("PurgeWaveTotal");
		owedDrones = tag.getInt("PurgeOwedDrones");
		owedSentinels = tag.getInt("PurgeOwedSentinels");
		bossId = tag.hasUUID("PurgeBoss") ? tag.getUUID("PurgeBoss") : null;
		activatorId = tag.hasUUID("PurgeActivator") ? tag.getUUID("PurgeActivator") : null;
		activatorName = tag.getString("PurgeActivatorName");
		readUuids(tag.getList("PurgeFighters", Tag.TAG_INT_ARRAY), fighters);
		readUuids(tag.getList("PurgeDown", Tag.TAG_INT_ARRAY), down);
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

	/** Test hook: a fighter added directly (mock players report creative, so the lock-in skips them). */
	public void addFighter(UUID id) {
		fighters.add(id);
	}

	/** Test hook: has this living entity been sent by this purge? */
	public boolean ownsEntity(LivingEntity e) {
		return owns(e.getUUID());
	}
}
