package com.projecthero.mod.ultron;

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
import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ultron.block.UltronBeaconBlock;
import com.projecthero.mod.ultron.block.UltronBlocks;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHeavyEntity;
import com.projecthero.mod.ultron.entity.UltronPrimeEntity;
import com.projecthero.mod.ultron.entity.UltronPylonEntity;
import com.projecthero.mod.ultron.entity.UltronRobot;
import com.projecthero.mod.ultron.entity.UltronSentinelDroneEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;
import com.projecthero.mod.ultron.entity.UltronSniperEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: <b>The Ultron Uprising</b> -- Stark tech turned on its makers. Built on the mod's world-event framework
 * ({@link EventInstance}: persistence, owned-mob tracking and cleanup, pause/abandon). Started by right-clicking an
 * Ultron Beacon (which becomes the <b>uplink</b> at the arena's heart), by {@code /projecthero raid start ultron}, or --
 * if a server turns it on -- by the natural JARVIS trigger.
 *
 * <pre>
 * COUNTDOWN (3 relay pylons rise) -> WAVE 1 .. WAVE 5 (breathers between)
 *   -> PYLONS ("DESTROY THE RELAY PYLONS" -- the gate: no boss while any stands)
 *   -> PRIME (Ultron Prime arrives; he re-raises reserve pylons -- spare bodies -- at the relay sites)
 *        at 0 health with a pylon standing: body jump (pylon consumed, new body at half health)
 *        at 0 health with none: he falls
 *   -> SENTRY_RISE -> SENTRY (the giant; shield phase at half) -> PURGED -> the uplink becomes the reward chest
 * every fighter down at once, at any point -> Ultron wins (the uplink burns out)
 * </pre>
 * <ul>
 *   <li><b>Arena</b>: 40 blocks round the uplink, its edge a red / cyan particle ring. Fighters are kept inside (pulled
 *       back in if they leave); anyone who walks in joins. A fighter who dies is down until they walk back in.</li>
 *   <li><b>Relay pylons</b> ({@link UltronPylonEntity}): while any stands, Ultron's units within 16 blocks of it repair
 *       2 health a second. A destroyed pylon's EMP stuns nearby robots.</li>
 *   <li><b>Waves</b>: 5, scaled x(1 + 0.5 per extra fighter); 1-2 drones only, 3 adds Sentinel Drones, 4 a Heavy and
 *       snipers, 5 everything.</li>
 * </ul>
 */
public class UltronUprising extends EventInstance {
	public static final String TYPE_ID = "ultron";
	public static final int MAX_WAVES = 8;
	static final int ARRIVALS_PER_TICK = 4;
	static final int STRAGGLER_TICKS = 90 * 20;
	static final int SENTRY_RISE_TICKS = 60;
	static final int PURGED_TICKS = UltronSentryEntity.DEATH_TICKS + 20;
	static final int SKY_COLOR = 0x3A0808;

	public enum Phase { COUNTDOWN, WAVE, BREATHER, PYLONS, PRIME, SENTRY_RISE, SENTRY, PURGED }

	/** Unit kinds in a wave. */
	public static final int DRONE = 0, SENTINEL = 1, HEAVY = 2, SNIPER = 3;

	private Phase phase = Phase.COUNTDOWN;
	private long phaseStart = -1;
	private int wave;
	private int waveTotal;
	private final int[] owed = new int[4];
	private final List<UUID> pylons = new ArrayList<>();
	private final List<BlockPos> pylonSites = new ArrayList<>();
	private UUID bossId;
	private boolean reserveRaised;
	private int lastPylonCount = -1;
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> down = new HashSet<>();
	private String activatorName = "";
	private transient EventBossBar bar;
	/** Test hooks: arrivals land this far from the centre (and this high) instead of the configured ring, when >= 0. */
	public transient double arrivalRadiusOverride = -1;
	public transient double arrivalHeightOverride = -1;
	/** Test hook: pylons rise this far from the centre instead of the configured ring, when >= 0. */
	public transient double pylonRingOverride = -1;

	public UltronUprising(UUID id) {
		super(id);
	}

	// ---------------------------------------------------------------- identity

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.ultron");
	}

	public static double arena() {
		return UltronConfig.arena().radius;
	}

	@Override
	public double radius() {
		return arena() + 24.0;
	}

	/** The running uprising with this id, or null. */
	public static UltronUprising find(ServerLevel level, UUID id) {
		if (id == null) {
			return null;
		}
		return EventSavedData.get(level).byId(id) instanceof UltronUprising u && u.state().active() ? u : null;
	}

	/** Every running uprising on the server. */
	public static List<UltronUprising> all(MinecraftServer server) {
		List<UltronUprising> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof UltronUprising u) {
				out.add(u);
			}
		}
		return out;
	}

	public static int waveCount() {
		return Mth.clamp(UltronConfig.waves().waveCount, 1, MAX_WAVES);
	}

	/** Wave {@code n}'s solo composition as {drones, sentinel drones, heavies, snipers}. */
	public static int[] composition(int n) {
		UltronConfig.Waves cfg = UltronConfig.waves();
		int i = Math.max(1, n) - 1;
		return new int[] { at(cfg.drones, i), at(cfg.sentinelDrones, i), at(cfg.heavies, i), at(cfg.snipers, i) };
	}

	private static int at(int[] arr, int i) {
		if (arr == null || arr.length == 0) {
			return 0;
		}
		return Math.max(0, arr[Math.min(i, arr.length - 1)]);
	}

	/** {@code base} scaled for {@code fighters}: x(1 + 0.5 per extra fighter), rounded. */
	public static int scaled(int base, int fighters) {
		if (base <= 0) {
			return 0;
		}
		return (int) Math.round(base * (1.0 + UltronConfig.waves().perExtraFighter * Math.max(0, fighters - 1)));
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

	public int owed() {
		return owed[0] + owed[1] + owed[2] + owed[3];
	}

	public int fighterCount() {
		return Math.max(1, fighters.size() - down.size());
	}

	public List<UUID> pylonIds() {
		return pylons;
	}

	/** The pylons still standing. */
	public List<UltronPylonEntity> livePylons(ServerLevel level) {
		List<UltronPylonEntity> out = new ArrayList<>();
		for (UUID u : pylons) {
			if (level.getEntity(u) instanceof UltronPylonEntity p && p.isAlive() && !p.isDeadOrDying()) {
				out.add(p);
			}
		}
		return out;
	}

	public LivingEntity boss(ServerLevel level) {
		return bossId != null && level.getEntity(bossId) instanceof LivingEntity l ? l : null;
	}

	/** Live units of this uprising, by kind {drones, sentinel drones, heavies, snipers, bosses}. */
	public int[] liveCounts(ServerLevel level) {
		int[] n = new int[5];
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof UltronDroneEntity) n[0]++;
			else if (m instanceof UltronSentinelDroneEntity) n[1]++;
			else if (m instanceof UltronHeavyEntity) n[2]++;
			else if (m instanceof UltronSniperEntity) n[3]++;
			else if (m instanceof UltronPrimeEntity || m instanceof UltronSentryEntity) n[4]++;
		}
		return n;
	}

	public boolean hasRoom(ServerLevel level) {
		return ownedAlive(level) < Math.min(UltronConfig.waves().enemyCap, EventConfig.framework().maxLiveMobs);
	}

	/** Makes {@code mob} one of this uprising's (bound + owned + cleaned up with it). */
	public void adopt(Mob mob) {
		if (mob instanceof UltronRobot r) {
			r.bindToUprising(id());
		}
		own(mob);
	}

	// ---------------------------------------------------------------- activation

	/** Called by {@link Ultron#start}: lock in everyone nearby, light the uplink, raise the relay pylons. */
	void activate(ServerLevel level, ServerPlayer activator) {
		if (activator != null) {
			activatorName = activator.getGameProfile().getName();
		}
		setState(EventState.RUNNING);
		phase = Phase.COUNTDOWN;
		phaseStart = level.getGameTime();
		BlockPos c = center();
		double r2 = arena() * arena();
		for (ServerPlayer p : level.players()) {
			if (UltronCombat.isFighter(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= r2) {
				fighters.add(p.getUUID());
			}
		}
		if (activator != null && UltronCombat.isFighter(activator)) {
			fighters.add(activator.getUUID());
		}
		participants().refresh(level, c, EventConfig.framework().abandonRadius, true);
		keepTheUplink(level);
		raiseRelayPylons(level);
		broadcastTitle(level, Component.translatable("title.projecthero.ultron.start").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.ultron.start.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		level.playSound(null, c, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.5f);
		for (ServerPlayer p : level.players()) {
			if (fighters.contains(p.getUUID())) {
				JarvisDialogue.speak(p, "ultron_pylons");
			}
		}
		ProjectHeroMod.LOGGER.info("[ProjectHero] Ultron Uprising {} started at {} with {} fighter(s)", id(), c, fighters.size());
	}

	/** The three relay pylons rise on the ring round the uplink. */
	public void raiseRelayPylons(ServerLevel level) {
		UltronConfig.Arena cfg = UltronConfig.arena();
		pylonSites.clear();
		int n = Math.max(0, cfg.pylons);
		double ring = pylonRingOverride >= 0 ? pylonRingOverride : cfg.pylonRing;
		double start = level.random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double a = start + i * Math.PI * 2 / n;
			BlockPos site = groundAt(level, center().getX() + 0.5 + Math.cos(a) * ring, center().getZ() + 0.5 + Math.sin(a) * ring);
			pylonSites.add(site);
			spawnPylon(level, site, UltronConfig.pylonHealthFor(fighterCount()), false);
		}
		lastPylonCount = livePylons(level).size();
	}

	/** Ultron Prime's spare bodies: reserve pylons at the first relay sites, at a fraction of a relay pylon's health. */
	public void raiseReservePylons(ServerLevel level) {
		reserveRaised = true;
		UltronConfig.Arena cfg = UltronConfig.arena();
		int n = Math.min(Math.max(0, cfg.reservePylons), pylonSites.size());
		for (int i = 0; i < n; i++) {
			spawnPylon(level, pylonSites.get(i), UltronConfig.pylonHealthFor(fighterCount()) * cfg.reservePylonHealthFraction, true);
		}
		lastPylonCount = livePylons(level).size();
	}

	private UltronPylonEntity spawnPylon(ServerLevel level, BlockPos site, double health, boolean reserve) {
		UltronPylonEntity pylon = UltronEntityTypes.PYLON.create(level);
		if (pylon == null) {
			return null;
		}
		pylon.moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		pylon.bindToUprising(id());
		level.addFreshEntity(pylon);
		pylon.configure(center(), health, reserve);
		pylons.add(pylon.getUUID());
		level.sendParticles(ParticleTypes.EXPLOSION, site.getX() + 0.5, site.getY() + 0.5, site.getZ() + 0.5, 2, 0.4, 0.2, 0.4, 0);
		level.playSound(null, site, SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 2.0f, 0.5f);
		return pylon;
	}

	private BlockPos groundAt(ServerLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, center().getY(), z);
		if (!level.isLoaded(column)) {
			return column;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		return new BlockPos(column.getX(), Math.max(y, level.getMinBuildHeight() + 1), column.getZ());
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
		if (phase != Phase.PURGED) {
			keepTheUplink(level);
		}
		enforceArena(level);
		ZombieRaidNetworking.pushSky(level, center(), arena(), SKY_COLOR);
		drawBorder(level, now);
		tetherOwnedMobs(level, arena());
		trackPylons(level);
		repair(level);
		long inPhase = now - phaseStart;
		switch (phase) {
			case COUNTDOWN -> {
				if (inPhase >= UltronConfig.waves().countdownSeconds * 20L) {
					startWave(level, 1);
				}
			}
			case WAVE -> tickWave(level, now, inPhase);
			case BREATHER -> {
				if (inPhase >= UltronConfig.waves().breatherSeconds * 20L) {
					if (wave >= waveCount()) {
						afterWaves(level);
					} else {
						startWave(level, wave + 1);
					}
				}
			}
			case PYLONS -> tickPylonGate(level, inPhase);
			case PRIME -> tickPrime(level);
			case SENTRY_RISE -> tickSentryRise(level, inPhase);
			case SENTRY -> tickSentry(level);
			case PURGED -> {
				if (inPhase >= PURGED_TICKS) {
					complete(level);
				}
			}
		}
		if (!state().finished()) {
			updateBar(level, now);
		}
	}

	/** The uplink stays: if something knocks it out mid-raid it comes straight back, lit. */
	private void keepTheUplink(ServerLevel level) {
		var state = level.getBlockState(center());
		if (!state.is(UltronBlocks.ULTRON_BEACON)) {
			level.setBlock(center(), UltronBlocks.ULTRON_BEACON.defaultBlockState().setValue(UltronBeaconBlock.ACTIVE, true), 3);
		} else if (!state.getValue(UltronBeaconBlock.ACTIVE)) {
			level.setBlock(center(), state.setValue(UltronBeaconBlock.ACTIVE, true), 3);
		}
	}

	private double horizontalDistance(Entity e) {
		BlockPos c = center();
		double dx = e.getX() - (c.getX() + 0.5);
		double dz = e.getZ() - (c.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Fighters stay in; anyone who walks in joins; a downed fighter back inside is up again. */
	private void enforceArena(ServerLevel level) {
		double r = arena();
		for (ServerPlayer p : level.players()) {
			if (!UltronCombat.isFighter(p)) {
				continue;
			}
			double d = horizontalDistance(p);
			UUID u = p.getUUID();
			if (!fighters.contains(u)) {
				if (d <= r) {
					fighters.add(u);
					p.displayClientMessage(Component.translatable("message.projecthero.ultron.joined").withStyle(ChatFormatting.RED), true);
				}
				continue;
			}
			if (down.contains(u)) {
				if (d <= r) {
					down.remove(u);
				}
				continue;
			}
			if (d > r && d < r + 48) {
				pullBack(level, p, r - 4);
				p.displayClientMessage(Component.translatable("message.projecthero.ultron.no_escape").withStyle(ChatFormatting.RED), true);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.6f);
			}
		}
	}

	private void pullBack(ServerLevel level, ServerPlayer p, double distance) {
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

	/** The arena's edge: a ring of alternating red and cyan dust at the ground, every half second (long-range). */
	private void drawBorder(ServerLevel level, long now) {
		BlockPos c = center();
		double r = arena();
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (horizontalDistance(p) < r + 48) {
				viewers.add(p);
			}
		}
		if (viewers.isEmpty()) {
			return;
		}
		int points = 80;
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points + (now % 80) * 0.002;
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			var dust = i % 2 == 0 ? UltronFx.RED : UltronFx.CYAN;
			for (ServerPlayer viewer : viewers) {
				level.sendParticles(viewer, dust, true, x, y + 0.6, z, 1, 0, 0.4, 0, 0);
				level.sendParticles(viewer, dust, true, x, y + 2.0, z, 1, 0, 0.4, 0, 0);
			}
		}
	}

	/** Notices pylons going down (a JARVIS line and a chat note), and forgets the dead ones. */
	private void trackPylons(ServerLevel level) {
		int live = livePylons(level).size();
		boolean late = phase == Phase.SENTRY_RISE || phase == Phase.SENTRY || phase == Phase.PURGED;
		if (lastPylonCount >= 0 && live < lastPylonCount && !late) {
			broadcast(level, Component.translatable(live == 0 ? "message.projecthero.ultron.pylons_all_down"
					: "message.projecthero.ultron.pylon_down", live).withStyle(ChatFormatting.AQUA));
		}
		lastPylonCount = live;
		pylons.removeIf(u -> !(level.getEntity(u) instanceof UltronPylonEntity p) || !p.isAlive() || p.isDeadOrDying());
	}

	/** Ultron's units near a standing pylon repair themselves ({@link UltronConfig.Arena#repairPerSecond}). */
	private void repair(ServerLevel level) {
		List<UltronPylonEntity> live = livePylons(level);
		if (live.isEmpty()) {
			return;
		}
		UltronConfig.Arena cfg = UltronConfig.arena();
		float heal = (float) (cfg.repairPerSecond * Math.max(1, EventConfig.framework().tickIntervalTicks) / 20.0);
		for (Mob m : liveOwnedMobs(level)) {
			if (!(m instanceof UltronRobot) || m instanceof UltronPrimeEntity || m instanceof UltronSentryEntity || m.getHealth() >= m.getMaxHealth()) {
				continue;
			}
			for (UltronPylonEntity p : live) {
				if (p.repairs(m)) {
					m.heal(heal);
					if (level.random.nextInt(3) == 0) {
						level.sendParticles(UltronFx.RED_SMALL, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
					}
					break;
				}
			}
		}
	}

	/** Repairs everything near a pylon right now, as one framework tick would (gametest hook). */
	public void repairNow(ServerLevel level) {
		repair(level);
	}

	// ---------------------------------------------------------------- waves

	public void startWave(ServerLevel level, int n) {
		wave = Mth.clamp(n, 1, MAX_WAVES);
		int[] mix = composition(wave);
		int f = fighterCount();
		for (int i = 0; i < 4; i++) {
			owed[i] = scaled(mix[i], f);
		}
		waveTotal = owed();
		phase = Phase.WAVE;
		phaseStart = level.getGameTime();
		broadcastTitle(level, Component.translatable("title.projecthero.ultron.wave", wave, waveCount()).withStyle(ChatFormatting.RED),
				Component.translatable("title.projecthero.ultron.wave.sub", waveTotal).withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.6f);
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
			broadcast(level, Component.translatable(wave >= waveCount() ? "message.projecthero.ultron.waves_done"
					: "message.projecthero.ultron.wave_cleared", wave).withStyle(ChatFormatting.RED));
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

	/** Brings in up to {@code max} owed units while the cap has room: heavies and snipers first, then a mix. */
	private void spawnOwed(ServerLevel level, int max) {
		int made = 0;
		int guard = 0;
		while (made < max && owed() > 0 && hasRoom(level) && guard++ < 200) {
			int kind = owed[HEAVY] > 0 ? HEAVY : owed[SNIPER] > 0 ? SNIPER
					: owed[SENTINEL] > 0 && (owed[DRONE] == 0 || level.random.nextBoolean()) ? SENTINEL : DRONE;
			owed[kind]--;
			if (spawnUnit(level, kind) != null) {
				made++;
			}
		}
	}

	/** One unit of {@code kind}, brought in round the arena and pointed at a fighter. Null if it could not be made. */
	public Mob spawnUnit(ServerLevel level, int kind) {
		EntityType<? extends Mob> type = switch (kind) {
			case SENTINEL -> UltronEntityTypes.SENTINEL_DRONE;
			case HEAVY -> UltronEntityTypes.HEAVY;
			case SNIPER -> UltronEntityTypes.SNIPER;
			default -> UltronEntityTypes.DRONE;
		};
		Mob mob = type.create(level);
		if (mob == null) {
			return null;
		}
		Vec3 at = arrivalSpot(level, kind == DRONE ? 10.0 : 0.0);
		mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		adopt(mob);
		level.addFreshEntity(mob);
		if (mob instanceof UltronSniperEntity sniper) {
			UltronPylonEntity perch = freePerch(level);
			if (perch != null) {
				sniper.perchOn(perch);
			}
		}
		Player target = UltronCombat.nearestPlayer(level, mob.position(), arena() * 2);
		if (target != null) {
			mob.setTarget(target);
		}
		level.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(UltronFx.RED, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
		return mob;
	}

	/** A standing pylon with no sniper on it yet. */
	private UltronPylonEntity freePerch(ServerLevel level) {
		List<UltronPylonEntity> live = livePylons(level);
		java.util.Collections.shuffle(live, new java.util.Random(level.random.nextLong()));
		for (UltronPylonEntity p : live) {
			boolean taken = false;
			for (Mob m : liveOwnedMobs(level)) {
				if (m instanceof UltronSniperEntity s && s.isPerched() && s.distanceToSqr(p.getX(), p.getY() + p.getBbHeight(), p.getZ()) < 2.0) {
					taken = true;
					break;
				}
			}
			if (!taken) {
				return p;
			}
		}
		return null;
	}

	/** A point on the arrival ring {@code height} blocks above the ground (the centre if the ground isn't loaded). */
	private Vec3 arrivalSpot(ServerLevel level, double height) {
		BlockPos c = center();
		double min = arrivalRadiusOverride >= 0 ? arrivalRadiusOverride : 12.0;
		double max = arrivalRadiusOverride >= 0 ? arrivalRadiusOverride : arena() - 6.0;
		double h = arrivalHeightOverride >= 0 ? arrivalHeightOverride : height;
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = min + level.random.nextDouble() * Math.max(0.0, max - min);
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			BlockPos column = BlockPos.containing(x, c.getY(), z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			return new Vec3(x, Math.max(ground, c.getY()) + h, z);
		}
		return new Vec3(c.getX() + 0.5, c.getY() + 1 + h, c.getZ() + 0.5);
	}

	// ---------------------------------------------------------------- the pylon gate and the boss

	/** After the last wave: the gate if any pylon stands, else straight to the boss. */
	private void afterWaves(ServerLevel level) {
		if (!livePylons(level).isEmpty()) {
			phase = Phase.PYLONS;
			phaseStart = level.getGameTime();
			broadcastTitle(level, Component.translatable("title.projecthero.ultron.pylons").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.ultron.pylons.sub").withStyle(ChatFormatting.GRAY));
			for (UltronPylonEntity p : livePylons(level)) {
				p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
			}
			return;
		}
		tryStartBoss(level);
	}

	private void tickPylonGate(ServerLevel level, long inPhase) {
		if (livePylons(level).isEmpty()) {
			tryStartBoss(level);
			return;
		}
		// the network keeps sending a trickle while its relays stand
		if (inPhase > 0 && inPhase % 300 < Math.max(1, EventConfig.framework().tickIntervalTicks) && hasRoom(level)) {
			for (int i = 0; i < scaled(2, fighterCount()); i++) {
				spawnUnit(level, DRONE);
			}
		}
	}

	/**
	 * The gate: the boss comes only once every relay pylon is down. Returns whether Ultron Prime was brought in (false
	 * while a pylon stands, or if he could not be made).
	 */
	public boolean tryStartBoss(ServerLevel level) {
		if (!livePylons(level).isEmpty()) {
			return false;
		}
		phase = Phase.PRIME;
		phaseStart = level.getGameTime();
		raiseReservePylons(level);
		UltronPrimeEntity prime = UltronEntityTypes.PRIME.create(level);
		if (prime == null) {
			complete(level); // nothing to fight: never leave an uprising stuck
			return false;
		}
		Vec3 at = arrivalSpot(level, 18.0);
		if (arrivalRadiusOverride < 0) {
			at = new Vec3(center().getX() + 0.5, at.y, center().getZ() + 0.5);
		}
		prime.moveTo(at.x, at.y, at.z, 0f, 0f);
		prime.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		prime.configure(fighterCount());
		adopt(prime);
		level.addFreshEntity(prime);
		bossId = prime.getUUID();
		broadcastTitle(level, Component.translatable("title.projecthero.ultron.prime").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.ultron.prime.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.7f);
		UltronFx.beam(level, Vec3.atCenterOf(center()), at, UltronFx.STREAK, 30);
		return true;
	}

	private void tickPrime(ServerLevel level) {
		LivingEntity b = boss(level);
		if (b instanceof UltronPrimeEntity prime && prime.isAlive() && !prime.isDeadOrDying()) {
			return;
		}
		if (b != null && b.isAlive() && !b.isDeadOrDying()) {
			return;
		}
		// Prime has fallen with no spare body left
		phase = Phase.SENTRY_RISE;
		phaseStart = level.getGameTime();
		for (UltronPylonEntity p : livePylons(level)) {
			p.discard(); // nothing left to jump to: the network collapses into the Sentry
		}
		broadcastTitle(level, Component.translatable("title.projecthero.ultron.sentry").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.ultron.sentry.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0f, 0.6f);
	}

	private void tickSentryRise(ServerLevel level, long inPhase) {
		Vec3 base = Vec3.atCenterOf(center());
		UltronFx.beam(level, base, base.add(0, 30, 0), UltronFx.CANNON, 12);
		UltronFx.forced(level, UltronFx.RED_BIG, base.x, base.y + 1, base.z, 10, 1.5, 0.5, 1.5, 0.05);
		if (inPhase >= SENTRY_RISE_TICKS) {
			startSentry(level);
		}
	}

	/** Brings in the Ultron Sentry now (also the gametests' / command's hook). */
	public UltronSentryEntity startSentry(ServerLevel level) {
		UltronSentryEntity sentry = UltronEntityTypes.SENTRY.create(level);
		if (sentry == null) {
			complete(level);
			return null;
		}
		Vec3 at = arrivalSpot(level, 0.0);
		if (arrivalRadiusOverride < 0) {
			BlockPos g = groundAt(level, center().getX() + 0.5 + 5, center().getZ() + 0.5);
			at = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
		}
		sentry.moveTo(at.x, at.y, at.z, 0f, 0f);
		sentry.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		sentry.configure(fighterCount());
		adopt(sentry);
		level.addFreshEntity(sentry);
		bossId = sentry.getUUID();
		phase = Phase.SENTRY;
		phaseStart = level.getGameTime();
		UltronFx.forced(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2, 1, 0.5, 1, 0);
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.4f);
		return sentry;
	}

	private void tickSentry(ServerLevel level) {
		LivingEntity b = boss(level);
		if (b != null && b.isAlive() && !b.isDeadOrDying()) {
			return;
		}
		if (bossId != null) {
			disown(bossId); // let his death play out instead of vanishing with the cleanup
		}
		phase = Phase.PURGED;
		phaseStart = level.getGameTime();
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof UltronRobot r && !(m instanceof UltronSentryEntity)) {
				r.stun(200);
				m.hurt(level.damageSources().generic(), 1000f);
			}
		}
		broadcastTitle(level, Component.translatable("title.projecthero.ultron.purged").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.ultron.purged.sub").withStyle(ChatFormatting.WHITE));
		for (ServerPlayer p : level.players()) {
			if (fighters.contains(p.getUUID())) {
				JarvisDialogue.speak(p, "ultron_purged");
			}
		}
	}

	/** {@code /projecthero raid advancetimer ultron}: on to the next stage now. */
	public void debugAdvance(ServerLevel level) {
		long now = level.getGameTime();
		switch (phase) {
			case COUNTDOWN -> startWave(level, 1);
			case WAVE -> {
				for (Mob m : liveOwnedMobs(level)) {
					disown(m.getUUID());
					m.discard();
				}
				for (int i = 0; i < 4; i++) {
					owed[i] = 0;
				}
				phase = Phase.BREATHER;
				phaseStart = now - UltronConfig.waves().breatherSeconds * 20L;
			}
			case BREATHER -> phaseStart = now - UltronConfig.waves().breatherSeconds * 20L;
			case PYLONS -> {
				for (UltronPylonEntity p : livePylons(level)) {
					p.kill();
				}
			}
			case PRIME -> {
				for (UltronPylonEntity p : livePylons(level)) {
					p.discard();
				}
				LivingEntity b = boss(level);
				if (b != null) {
					b.kill();
				}
			}
			case SENTRY_RISE -> phaseStart = now - SENTRY_RISE_TICKS;
			case SENTRY -> {
				LivingEntity b = boss(level);
				if (b != null) {
					b.kill();
				}
			}
			case PURGED -> phaseStart = now - PURGED_TICKS;
		}
	}

	// ---------------------------------------------------------------- deaths and the end

	/** A fighter died: down until they walk back in. When every fighter is down at once, Ultron wins. */
	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!fighters.contains(player.getUUID()) || state().finished()) {
			return;
		}
		down.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		broadcast(level, Component.translatable("message.projecthero.ultron.fighter_down", player.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		if (down.containsAll(fighters)) {
			fail(level, Component.translatable("message.projecthero.ultron.defeat").withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** Everyone still standing at the end. */
	public List<ServerPlayer> winners(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if (!down.contains(u) && level.getPlayerByUUID(u) instanceof ServerPlayer p && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		tearDown(level);
		BlockPos c = center();
		if (success) {
			List<ServerPlayer> winners = winners(level);
			UltronRewards.placeChest(level, c, Math.max(1, winners.size()));
			for (ServerPlayer p : winners) {
				UltronRewards.grantMindStone(p);
			}
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			ProjectHeroMod.LOGGER.info("[ProjectHero] Ultron Uprising {} purged; {} fighter(s) rewarded", id(), winners.size());
		} else {
			// Ultron won: the uplink burns out
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.02);
			level.playSound(null, c, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
		var state = level.getBlockState(center());
		if (state.is(UltronBlocks.ULTRON_BEACON)) {
			level.setBlock(center(), state.setValue(UltronBeaconBlock.ACTIVE, false), 3);
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
		for (UUID u : pylons) {
			if (level.getEntity(u) instanceof UltronPylonEntity p) {
				p.discard();
			}
		}
		pylons.clear();
	}

	// ---------------------------------------------------------------- the bar

	private void updateBar(ServerLevel level, long now) {
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10, true);
		}
		long inPhase = now - phaseStart;
		Component name;
		float progress;
		switch (phase) {
			case COUNTDOWN -> {
				int total = UltronConfig.waves().countdownSeconds * 20;
				name = Component.translatable("event.projecthero.ultron.bar.countdown", Math.max(0, (int) Math.ceil((total - inPhase) / 20.0)));
				progress = Mth.clamp(inPhase / (float) Math.max(1, total), 0f, 1f);
			}
			case BREATHER -> {
				int total = UltronConfig.waves().breatherSeconds * 20;
				name = Component.translatable("event.projecthero.ultron.bar.breather", wave, waveCount());
				progress = Mth.clamp(inPhase / (float) Math.max(1, total), 0f, 1f);
			}
			case WAVE -> {
				int left = owed() + ownedAlive(level);
				name = Component.translatable("event.projecthero.ultron.bar.wave", wave, waveCount(), left, livePylons(level).size());
				progress = waveTotal <= 0 ? 0f : Mth.clamp(left / (float) waveTotal, 0f, 1f);
			}
			case PYLONS -> {
				int live = livePylons(level).size();
				name = Component.translatable("event.projecthero.ultron.bar.pylons", live);
				progress = Mth.clamp(live / (float) Math.max(1, UltronConfig.arena().pylons), 0f, 1f);
			}
			case PRIME -> {
				name = Component.translatable("event.projecthero.ultron.bar.prime", livePylons(level).size());
				progress = 1f;
			}
			case SENTRY_RISE, SENTRY -> {
				name = Component.translatable("event.projecthero.ultron.bar.sentry");
				progress = 1f;
			}
			default -> {
				name = Component.translatable("event.projecthero.ultron.bar.purged");
				progress = 0f;
			}
		}
		bar.update(level, center(), radius(), name.copy().withStyle(ChatFormatting.RED), progress);
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("UltronPhase", phase.name());
		tag.putLong("UltronPhaseStart", phaseStart);
		tag.putInt("UltronWave", wave);
		tag.putInt("UltronWaveTotal", waveTotal);
		tag.putIntArray("UltronOwed", owed.clone());
		tag.put("UltronPylons", uuids(pylons));
		ListTag sites = new ListTag();
		for (BlockPos p : pylonSites) {
			sites.add(NbtUtils.writeBlockPos(p));
		}
		tag.put("UltronPylonSites", sites);
		if (bossId != null) {
			tag.putUUID("UltronBoss", bossId);
		}
		tag.putBoolean("UltronReserveRaised", reserveRaised);
		tag.putString("UltronActivatorName", activatorName);
		tag.put("UltronFighters", uuids(fighters));
		tag.put("UltronDown", uuids(down));
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("UltronPhase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.COUNTDOWN;
		}
		phaseStart = tag.getLong("UltronPhaseStart");
		wave = tag.getInt("UltronWave");
		waveTotal = tag.getInt("UltronWaveTotal");
		int[] o = tag.getIntArray("UltronOwed");
		for (int i = 0; i < 4; i++) {
			owed[i] = i < o.length ? o[i] : 0;
		}
		pylons.clear();
		ListTag pl = tag.getList("UltronPylons", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < pl.size(); i++) {
			pylons.add(NbtUtils.loadUUID(pl.get(i)));
		}
		pylonSites.clear();
		ListTag sites = tag.getList("UltronPylonSites", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < sites.size(); i++) {
			int[] a = sites.getIntArray(i);
			if (a.length == 3) {
				pylonSites.add(new BlockPos(a[0], a[1], a[2]));
			}
		}
		bossId = tag.hasUUID("UltronBoss") ? tag.getUUID("UltronBoss") : null;
		reserveRaised = tag.getBoolean("UltronReserveRaised");
		activatorName = tag.getString("UltronActivatorName");
		readUuids(tag.getList("UltronFighters", Tag.TAG_INT_ARRAY), fighters);
		readUuids(tag.getList("UltronDown", Tag.TAG_INT_ARRAY), down);
	}

	private static ListTag uuids(Iterable<UUID> set) {
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

	/** Test hook: a fighter added directly. */
	public void addFighter(UUID id) {
		fighters.add(id);
	}

	/** Test hook: does this uprising own {@code e}? */
	public boolean ownsEntity(LivingEntity e) {
		return owns(e.getUUID());
	}
}
