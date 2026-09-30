package com.projecthero.mod.kryptonian;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Kryptonian's ten moves (v0.14.8). Every move goes through {@link #begin} (power, kryptonite, burn-out, cooldown and
 * Solar Energy gates). Moves that last longer than a tick keep a small per-player session here and are advanced by
 * {@link #tick}; every session is dropped by {@link #clear} (death, logout, world change, kryptonite) and all of them by
 * {@link #clearSessionState} when the server stops.
 *
 * <pre>
 *   R        Kryptonian Punch          Shift+R  Heat Vision (hold)
 *   G        Freeze Breath             Shift+G  Thunderclap
 *   Z        Ground Slam               Shift+Z  SOLAR FLARE (ultimate)
 *   X        Super Dash                Shift+X  Sky Launch
 *   V        X-Ray Vision              Shift+V  Super Grab / Throw
 * </pre>
 */
public final class KryptonianAbilities {
	public static final String PUNCH = "punch";
	public static final String HEAT_VISION = "heat_vision";
	public static final String FREEZE_BREATH = "freeze_breath";
	public static final String THUNDERCLAP = "thunderclap";
	public static final String GROUND_SLAM = "ground_slam";
	public static final String SOLAR_FLARE = "solar_flare";
	public static final String SUPER_DASH = "super_dash";
	public static final String SKY_LAUNCH = "sky_launch";
	public static final String XRAY = "xray_vision";
	public static final String SUPER_GRAB = "super_grab";

	private static final class Heat {
		final long start;

		Heat(long start) {
			this.start = start;
		}
	}

	private static final class Dash {
		final Vec3 dir;
		final long start;
		final boolean wasFlying;
		final Set<Integer> hit = new HashSet<>();
		double travelled;
		Vec3 last;

		Dash(Vec3 dir, long start, boolean wasFlying, Vec3 from) {
			this.dir = dir;
			this.start = start;
			this.wasFlying = wasFlying;
			this.last = from;
		}
	}

	private record Launch(long start, double startY) {
	}

	private record Grab(int entityId, long until) {
	}

	private record Thrown(UUID owner, long start) {
	}

	private static final Map<UUID, Heat> HEAT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> BREATH = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> SLAM = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> FLARE = new ConcurrentHashMap<>();
	private static final Map<UUID, Dash> DASH = new ConcurrentHashMap<>();
	private static final Map<UUID, Launch> LAUNCH = new ConcurrentHashMap<>();
	private static final Map<UUID, Grab> GRAB = new ConcurrentHashMap<>();
	/** Entity id -> who threw it (the impact is checked every tick from the thrower's tick). */
	private static final Map<Integer, Thrown> THROWN = new ConcurrentHashMap<>();

	private KryptonianAbilities() {
	}

	public static void clearSessionState() {
		HEAT.clear();
		BREATH.clear();
		SLAM.clear();
		FLARE.clear();
		DASH.clear();
		LAUNCH.clear();
		GRAB.clear();
		THROWN.clear();
	}

	/** Ends every running move of this player (a held mob is set down, heat vision stops). */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		BREATH.remove(id);
		SLAM.remove(id);
		FLARE.remove(id);
		DASH.remove(id);
		LAUNCH.remove(id);
		Grab g = GRAB.remove(id);
		if (g != null) {
			Entity e = player.level().getEntity(g.entityId());
			if (e != null) {
				e.setNoGravity(false);
			}
		}
		THROWN.values().removeIf(t -> t.owner().equals(id));
		if (HEAT.remove(id) != null || Kryptonian.heatVisionActive(player)) {
			setHeatFlag(player, false);
		}
	}

	/** Is this move running right now (tests / HUD)? */
	public static boolean running(ServerPlayer player, String id) {
		UUID u = player.getUUID();
		return switch (id) {
			case HEAT_VISION -> HEAT.containsKey(u);
			case FREEZE_BREATH -> BREATH.containsKey(u);
			case GROUND_SLAM -> SLAM.containsKey(u);
			case SOLAR_FLARE -> FLARE.containsKey(u);
			case SUPER_DASH -> DASH.containsKey(u);
			case SKY_LAUNCH -> LAUNCH.containsKey(u);
			case SUPER_GRAB -> GRAB.containsKey(u);
			default -> false;
		};
	}

	public static boolean holding(ServerPlayer player) {
		return GRAB.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------- the gate

	/** Power, kryptonite, burn-out, cooldown and Solar Energy -- then spends the energy. */
	static boolean begin(ServerPlayer player, String id, float cost) {
		if (!Kryptonian.canAct(player)) {
			return false;
		}
		int cd = Kryptonian.cooldownRemaining(player, id);
		if (cd > 0) {
			Kryptonian.say(player, "message.projecthero.kryptonian.cooldown", ChatFormatting.GRAY,
					net.minecraft.network.chat.Component.translatable("projecthero.kryptonian.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!Kryptonian.spendSolar(player, cost)) {
			Kryptonian.say(player, "message.projecthero.kryptonian.low_solar", ChatFormatting.YELLOW, (int) Math.ceil(cost),
					(int) Math.floor(Kryptonian.solar(player)));
			return false;
		}
		return true;
	}

	static void cooldown(ServerPlayer player, String id, int ticks) {
		KryptonianState n = Kryptonian.state(player).copy();
		n.abilityReadyAt.put(id, player.level().getGameTime() + ticks);
		Kryptonian.save(player, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	// ---------------------------------------------------------------- R: Kryptonian Punch

	public static void punch(ServerPlayer p) {
		if (!begin(p, PUNCH, KryptonianConfig.PUNCH_COST)) {
			return;
		}
		cooldown(p, PUNCH, KryptonianConfig.PUNCH_COOLDOWN);
		Kryptonian.setAnim(p, KryptonianState.ANIM_PUNCH);
		ServerLevel level = level(p);
		LivingEntity target = AbilityHelpers.raycastEntity(p, KryptonianConfig.PUNCH_RANGE);
		Vec3 look = p.getLookAngle();
		if (target != null && KryptonianCombat.isTarget(p, target)) {
			Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
			Set<Integer> hit = new HashSet<>();
			hit.add(target.getId());
			KryptonianCombat.strike(p, target, p.position(), KryptonianConfig.PUNCH_DAMAGE, 0.0, 0.0, true);
			if (target.isAlive() && !KryptonianCombat.isBoss(target)) {
				// launched: straight along the punch, not just "away"
				AbilityHelpers.push(target, look.scale(KryptonianConfig.PUNCH_LAUNCH).add(0, KryptonianConfig.PUNCH_LIFT, 0));
			}
			KryptonianCombat.radial(p, at, KryptonianConfig.PUNCH_WAVE_RADIUS, KryptonianConfig.PUNCH_WAVE_DAMAGE, 1.2, 0.3, hit);
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.15);
			KryptonianCombat.ring(level, ParticleTypes.CLOUD, at, 1.6, 16);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4f, 0.5f);
			KryptonianCombat.shake(level, at, 0.35f, 8, 16.0);
		} else {
			// a punch into the air: the pressure wave still flattens whatever is just in front
			Vec3 eye = p.getEyePosition();
			for (LivingEntity e : KryptonianCombat.cone(p, eye, look, 7.0, 40.0)) {
				KryptonianCombat.strike(p, e, p.position(), 14.0f, 2.0, 0.3, true);
			}
			for (int i = 1; i <= 6; i++) {
				Vec3 c = eye.add(look.scale(i));
				level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 2, 0.15 * i, 0.15 * i, 0.15 * i, 0.02);
			}
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- Shift+R: Heat Vision (held)

	public static void startHeatVision(ServerPlayer p) {
		if (HEAT.containsKey(p.getUUID())) {
			return;
		}
		// needs at least a second's worth of energy to open up; after that it is paid as it burns
		if (!Kryptonian.canAct(p)) {
			return;
		}
		if (Kryptonian.solar(p) < KryptonianConfig.HEAT_COST_PER_SECOND) {
			Kryptonian.say(p, "message.projecthero.kryptonian.low_solar", ChatFormatting.YELLOW,
					(int) Math.ceil(KryptonianConfig.HEAT_COST_PER_SECOND), (int) Math.floor(Kryptonian.solar(p)));
			return;
		}
		if (!begin(p, HEAT_VISION, 0f)) {
			return;
		}
		HEAT.put(p.getUUID(), new Heat(p.level().getGameTime()));
		setHeatFlag(p, true);
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 1.6f);
	}

	/** The key came up (or anything else stopped it): the beam ends and the cooldown starts. */
	public static void stopHeatVision(ServerPlayer p) {
		if (HEAT.remove(p.getUUID()) == null) {
			return;
		}
		setHeatFlag(p, false);
		cooldown(p, HEAT_VISION, KryptonianConfig.HEAT_COOLDOWN);
	}

	private static void setHeatFlag(ServerPlayer p, boolean on) {
		KryptonianState s = Kryptonian.state(p);
		if (s.heatVision != on) {
			KryptonianState n = s.copy();
			n.heatVision = on;
			Kryptonian.save(p, n);
		}
	}

	private static void tickHeat(ServerPlayer p, Heat h, long now) {
		long age = now - h.start;
		if (age >= KryptonianConfig.HEAT_MAX_TICKS || !Kryptonian.empowered(p) || !p.isAlive()) {
			stopHeatVision(p);
			return;
		}
		if (age > 0 && age % 10 == 0 && !Kryptonian.spendSolar(p, KryptonianConfig.HEAT_COST_PER_SECOND * 0.5f)) {
			stopHeatVision(p);
			return;
		}
		ServerLevel level = level(p);
		// always from the eyes along the look -- the same in first and third person, whichever way the camera faces
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		Vec3 far = eye.add(dir.scale(KryptonianConfig.HEAT_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && KryptonianCombat.isTarget(p, le) && e.isPickable(), eye.distanceToSqr(end));
		LivingEntity target = ehr != null && ehr.getEntity() instanceof LivingEntity le ? le : null;
		if (target != null) {
			end = ehr.getLocation();
		}
		if (age % KryptonianConfig.HEAT_HIT_INTERVAL == 0) {
			if (target != null) {
				KryptonianCombat.strike(p, target, eye, KryptonianConfig.HEAT_DAMAGE, 0.0, 0.0, true, AbilityHelpers.fire(p));
				target.igniteForSeconds(KryptonianConfig.HEAT_FIRE_SECONDS);
			}
			level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.15, 0.15, 0.15, 0.02);
			level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0.01);
		}
		if (target == null && bhr.getType() == HitResult.Type.BLOCK && age % 20 == 10 && AbilityHelpers.canGrief()) {
			BlockPos firePos = bhr.getBlockPos().relative(bhr.getDirection());
			if (level.getBlockState(firePos).isAir() && level.mayInteract(p, firePos)) {
				BlockState fire = BaseFireBlock.getState(level, firePos);
				if (fire.canSurvive(level, firePos)) {
					level.setBlock(firePos, fire, 11);
				}
			}
		}
		if (age % 12 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.8f);
		}
	}

	// ---------------------------------------------------------------- G: Freeze Breath

	public static void freezeBreath(ServerPlayer p) {
		if (BREATH.containsKey(p.getUUID()) || !begin(p, FREEZE_BREATH, KryptonianConfig.BREATH_COST)) {
			return;
		}
		cooldown(p, FREEZE_BREATH, KryptonianConfig.BREATH_COOLDOWN);
		Kryptonian.setAnim(p, KryptonianState.ANIM_BREATH);
		BREATH.put(p.getUUID(), p.level().getGameTime());
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.2f, 0.6f);
	}

	private static void tickBreath(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (age >= KryptonianConfig.BREATH_TICKS || !Kryptonian.empowered(p)) {
			BREATH.remove(p.getUUID());
			return;
		}
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition().add(0, -0.15, 0);
		Vec3 dir = p.getLookAngle();
		// the breath: a widening stream of snow and frost
		for (int i = 1; i <= 6; i++) {
			double d = i * KryptonianConfig.BREATH_RANGE / 6.0;
			Vec3 c = eye.add(dir.scale(d));
			double spread = d * Math.tan(Math.toRadians(KryptonianConfig.BREATH_CONE_DEGREES * 0.5)) * 0.5;
			level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 3, spread, spread, spread, 0.02);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 1, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
			}
		}
		if (age % KryptonianConfig.BREATH_HIT_INTERVAL == 0) {
			for (LivingEntity e : KryptonianCombat.cone(p, eye, dir, KryptonianConfig.BREATH_RANGE, KryptonianConfig.BREATH_CONE_DEGREES)) {
				if (KryptonianCombat.strike(p, e, eye, KryptonianConfig.BREATH_DAMAGE, 0.25, 0.0, true, AbilityHelpers.freeze(p))) {
					e.clearFire();
					e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 100));
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, KryptonianConfig.BREATH_SLOW_TICKS, 3);
				}
			}
		}
		if (age % 5 == 2) {
			freezeTerrain(p, level, eye, dir);
		}
	}

	/** Water in the breath's path freezes over (frosted ice, which melts back on its own); fires go out. */
	private static void freezeTerrain(ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 dir) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		for (int i = 2; i <= (int) KryptonianConfig.BREATH_RANGE; i++) {
			Vec3 c = eye.add(dir.scale(i));
			int w = Math.max(1, i / 4);
			for (int s = -w; s <= w; s++) {
				Vec3 q = c.add(side.scale(s));
				for (int dy = -2; dy <= 1; dy++) {
					pos.set(q.x, q.y + dy, q.z);
					BlockState st = level.getBlockState(pos);
					if (st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE)) {
						level.removeBlock(pos, false);
					} else if (st.is(Blocks.WATER) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir()
							&& level.mayInteract(p, pos)) {
						level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState().setValue(FrostedIceBlock.AGE, 0));
						level.scheduleTick(pos, Blocks.FROSTED_ICE, 60 + level.random.nextInt(60));
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- Shift+G: Thunderclap

	public static void thunderclap(ServerPlayer p) {
		if (!begin(p, THUNDERCLAP, KryptonianConfig.CLAP_COST)) {
			return;
		}
		cooldown(p, THUNDERCLAP, KryptonianConfig.CLAP_COOLDOWN);
		Kryptonian.setAnim(p, KryptonianState.ANIM_CLAP);
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 hands = eye.add(look.scale(0.8)).add(0, -0.4, 0);
		for (LivingEntity e : KryptonianCombat.cone(p, eye, look, KryptonianConfig.CLAP_RANGE, KryptonianConfig.CLAP_CONE_DEGREES)) {
			double dist = e.position().distanceTo(p.position());
			double falloff = 1.0 - 0.5 * Math.min(1.0, dist / KryptonianConfig.CLAP_RANGE);
			if (KryptonianCombat.strike(p, e, p.position(), (float) (KryptonianConfig.CLAP_DAMAGE * falloff),
					KryptonianConfig.CLAP_KNOCKBACK * falloff, KryptonianConfig.CLAP_LIFT, true)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, KryptonianConfig.CLAP_STUN_TICKS, 6);
				AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, KryptonianConfig.CLAP_STUN_TICKS, 1);
				if (e instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
		// rings of air rolling out down the cone
		for (int i = 1; i <= 8; i++) {
			double d = i * KryptonianConfig.CLAP_RANGE / 8.0;
			Vec3 c = hands.add(look.scale(d));
			double r = d * Math.tan(Math.toRadians(KryptonianConfig.CLAP_CONE_DEGREES * 0.5)) * 0.6;
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 4, r, r * 0.4, r, 0.05);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, r * 0.6, 0.3, r * 0.6, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, hands.x, hands.y, hands.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6f, 1.4f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.2f);
		KryptonianCombat.shake(level, p.position(), 0.5f, 12, 24.0);
	}

	// ---------------------------------------------------------------- Z: Ground Slam

	public static void groundSlam(ServerPlayer p) {
		if (SLAM.containsKey(p.getUUID()) || !begin(p, GROUND_SLAM, KryptonianConfig.SLAM_COST)) {
			return;
		}
		cooldown(p, GROUND_SLAM, KryptonianConfig.SLAM_COOLDOWN);
		Kryptonian.setAnim(p, KryptonianState.ANIM_SLAM);
		if (p.onGround()) {
			slam(p, 0.75f);
			return;
		}
		// in the air: dive straight down (a little along the look) and slam where he lands
		KryptonianFlight.stop(p, false);
		Vec3 f = p.getLookAngle();
		AbilityHelpers.launchSelf(p, new Vec3(f.x * 0.4, -KryptonianConfig.SLAM_DIVE_SPEED, f.z * 0.4));
		SLAM.put(p.getUUID(), p.level().getGameTime());
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6f, 1.8f);
	}

	private static void tickSlam(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (p.onGround() || p.isInWater() || age >= KryptonianConfig.SLAM_MAX_DIVE_TICKS) {
			SLAM.remove(p.getUUID());
			// a longer dive hits harder (up to the full damage after a second)
			slam(p, (float) Math.min(1.0, 0.75 + age / 80.0));
			return;
		}
		if (age % 3 == 0) {
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x, Math.min(v.y, -KryptonianConfig.SLAM_DIVE_SPEED), v.z));
		}
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.8, p.getZ(), 3, 0.3, 0.4, 0.3, 0.01);
	}

	private static void slam(ServerPlayer p, float power) {
		ServerLevel level = level(p);
		Vec3 at = p.position();
		p.resetFallDistance();
		KryptonianCombat.radial(p, at, KryptonianConfig.SLAM_RADIUS, KryptonianConfig.SLAM_DAMAGE * power, KryptonianConfig.SLAM_KNOCKBACK,
				KryptonianConfig.SLAM_LIFT, new HashSet<>());
		KryptonianCombat.crater(p, at.add(0, -0.5, 0), KryptonianConfig.SLAM_CRATER_RADIUS, KryptonianConfig.SLAM_CRATER_MAX_BLOCKS);
		BlockState ground = level.getBlockState(p.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, 40, 2.0, 0.2, 2.0, 0.15);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		KryptonianCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), 3.0, 24);
		KryptonianCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), KryptonianConfig.SLAM_RADIUS * 0.8, 32);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.5f);
		KryptonianCombat.shake(level, at, 0.6f, 14, 28.0);
	}

	// ---------------------------------------------------------------- Shift+Z: SOLAR FLARE

	public static void solarFlare(ServerPlayer p) {
		if (FLARE.containsKey(p.getUUID()) || !Kryptonian.canAct(p)) {
			return;
		}
		if (Kryptonian.solar(p) + 1.0e-3f < KryptonianConfig.FLARE_MIN_SOLAR) {
			Kryptonian.say(p, "message.projecthero.kryptonian.low_solar", ChatFormatting.YELLOW, (int) KryptonianConfig.FLARE_MIN_SOLAR,
					(int) Math.floor(Kryptonian.solar(p)));
			return;
		}
		if (!begin(p, SOLAR_FLARE, 0f)) {
			return;
		}
		long now = p.level().getGameTime();
		FLARE.put(p.getUUID(), now);
		KryptonianState n = Kryptonian.state(p).copy();
		n.flareChargeStart = now;
		n.animId = KryptonianState.ANIM_FLARE;
		n.animStart = now;
		Kryptonian.save(p, n);
		ServerLevel level = level(p);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.5f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.kryptonian.flare_charging")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	private static void tickFlare(ServerPlayer p, long start, long now) {
		if (!Kryptonian.empowered(p) || Kryptonian.state(p).flareChargeStart == 0L) {
			FLARE.remove(p.getUUID());
			return;
		}
		long age = now - start;
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		// the sunlight he has stored pours out of him
		double r = 3.5 * (1.0 - age / (double) KryptonianConfig.FLARE_CHARGE_TICKS) + 0.6;
		for (int i = 0; i < 6; i++) {
			double a = (now * 0.4) + i * Math.PI / 3.0;
			level.sendParticles(Kryptonian.SUN_GOLD, c.x + Math.cos(a) * r, c.y + (i % 3) * 0.5 - 0.5, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 2, 0.4, 0.8, 0.4, 0.05);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, false));
		if (age % 10 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.5f + age / 60.0f);
		}
		if (age >= KryptonianConfig.FLARE_CHARGE_TICKS) {
			FLARE.remove(p.getUUID());
			detonateFlare(p);
		}
	}

	private static void detonateFlare(ServerPlayer p) {
		ServerLevel level = level(p);
		float solar = Kryptonian.solar(p);
		float damage = KryptonianConfig.FLARE_BASE_DAMAGE + solar * KryptonianConfig.FLARE_DAMAGE_PER_SOLAR;
		Vec3 at = p.position().add(0, 1.0, 0);
		KryptonianState n = Kryptonian.state(p).copy();
		n.flareChargeStart = 0L;
		n.abilityReadyAt.put(SOLAR_FLARE, level.getGameTime() + KryptonianConfig.FLARE_COOLDOWN);
		Kryptonian.save(p, n);
		Set<Integer> hit = new HashSet<>();
		KryptonianCombat.radial(p, at, KryptonianConfig.FLARE_RADIUS, damage, KryptonianConfig.FLARE_KNOCKBACK, KryptonianConfig.FLARE_LIFT, hit);
		for (LivingEntity e : KryptonianCombat.targets(p, new AABB(at, at).inflate(KryptonianConfig.FLARE_RADIUS))) {
			if (hit.contains(e.getId())) {
				e.igniteForSeconds(8);
			}
		}
		KryptonianCombat.crater(p, p.position().add(0, -0.5, 0), KryptonianConfig.FLARE_CRATER_RADIUS, KryptonianConfig.FLARE_CRATER_MAX_BLOCKS);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 3, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 4, 3.0, 1.5, 3.0, 0.0);
		level.sendParticles(Kryptonian.SUN_GOLD, at.x, at.y, at.z, 200, 6.0, 3.0, 6.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 120, 0.5, 0.5, 0.5, 0.9);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, 4.0, 2.0, 4.0, 0.1);
		for (double r = 3.0; r <= KryptonianConfig.FLARE_RADIUS; r += 3.0) {
			KryptonianCombat.ring(level, ParticleTypes.CLOUD, at.add(0, -0.6, 0), r, 32);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
		KryptonianCombat.shake(level, at, 1.0f, 30, 48.0);
		Kryptonian.depower(p, KryptonianConfig.FLARE_DEPOWER_TICKS);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.kryptonian.flare_spent")
				.withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- X: Super Dash

	public static void superDash(ServerPlayer p) {
		if (DASH.containsKey(p.getUUID()) || !begin(p, SUPER_DASH, KryptonianConfig.DASH_COST)) {
			return;
		}
		cooldown(p, SUPER_DASH, KryptonianConfig.DASH_COOLDOWN);
		Kryptonian.setAnim(p, KryptonianState.ANIM_DASH);
		boolean flying = Kryptonian.isFlying(p);
		Vec3 dir = p.getLookAngle();
		if (!flying) {
			// on foot it stays low: a flat blur along the ground (a little up so it clears the first step)
			dir = new Vec3(dir.x, Math.max(0.05, Math.min(dir.y, 0.35)), dir.z);
		}
		dir = dir.normalize();
		if (flying) {
			KryptonianFlight.stop(p, false);
		}
		DASH.put(p.getUUID(), new Dash(dir, p.level().getGameTime(), flying, p.position()));
		AbilityHelpers.launchSelf(p, dir.scale(KryptonianConfig.DASH_SPEED));
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.8, p.getZ(), 12, 0.3, 0.4, 0.3, 0.08);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0f, 1.8f);
	}

	private static void tickDash(ServerPlayer p, Dash d, long now) {
		long age = now - d.start;
		Vec3 pos = p.position();
		double step = pos.distanceTo(d.last);
		d.travelled += step;
		d.last = pos;
		ServerLevel level = level(p);
		// ram whatever is in the way
		Vec3 mid = pos.add(0, p.getBbHeight() * 0.5, 0);
		for (LivingEntity e : KryptonianCombat.targets(p, p.getBoundingBox().inflate(KryptonianConfig.DASH_HIT_RADIUS))) {
			if (d.hit.add(e.getId())) {
				KryptonianCombat.strike(p, e, pos.subtract(d.dir), KryptonianConfig.DASH_DAMAGE, KryptonianConfig.DASH_KNOCKBACK, 0.4, true);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 2, 0.2, 0.3, 0.2, 0.01);
		level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 1, 0.1, 0.2, 0.1, 0.0);
		boolean blocked = age >= 2 && step < 0.3;
		if (d.travelled >= KryptonianConfig.DASH_DISTANCE || blocked || age > 20) {
			DASH.remove(p.getUUID());
			AbilityHelpers.launchSelf(p, d.dir.scale(0.3));
			if (d.wasFlying && Kryptonian.empowered(p) && !p.onGround()) {
				KryptonianFlight.start(p);
			}
			return;
		}
		if (age >= 1) {
			AbilityHelpers.launchSelf(p, d.dir.scale(KryptonianConfig.DASH_SPEED));
		}
	}

	// ---------------------------------------------------------------- Shift+X: Sky Launch

	public static void skyLaunch(ServerPlayer p) {
		if (LAUNCH.containsKey(p.getUUID()) || !begin(p, SKY_LAUNCH, KryptonianConfig.LAUNCH_COST)) {
			return;
		}
		cooldown(p, SKY_LAUNCH, KryptonianConfig.LAUNCH_COOLDOWN);
		KryptonianFlight.stop(p, false);
		ServerLevel level = level(p);
		Vec3 at = p.position();
		KryptonianCombat.radial(p, at, KryptonianConfig.LAUNCH_WAVE_RADIUS, KryptonianConfig.LAUNCH_WAVE_DAMAGE, 1.0, 0.8, new HashSet<>());
		KryptonianCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), 2.0, 24);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
		AbilityHelpers.launchSelf(p, new Vec3(0.0, Kryptonian.verticalSpeedForHeight(KryptonianConfig.LAUNCH_HEIGHT), 0.0));
		LAUNCH.put(p.getUUID(), new Launch(level.getGameTime(), p.getY()));
	}

	private static void tickLaunch(ServerPlayer p, Launch l, long now) {
		long age = now - l.start();
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() - 0.2, p.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
		boolean apex = age > 6 && (p.getY() >= l.startY() + KryptonianConfig.LAUNCH_HEIGHT - 1.0 || p.getDeltaMovement().y <= 0.05);
		if (apex || age > 80) {
			LAUNCH.remove(p.getUUID());
			if (Kryptonian.empowered(p)) {
				KryptonianFlight.start(p); // hovers at the top, ready to fly
				AbilityHelpers.launchSelf(p, Vec3.ZERO);
			}
		}
	}

	// ---------------------------------------------------------------- V: X-Ray Vision

	public static void xray(ServerPlayer p) {
		if (!begin(p, XRAY, KryptonianConfig.XRAY_COST)) {
			return;
		}
		cooldown(p, XRAY, KryptonianConfig.XRAY_COOLDOWN);
		KryptonianState n = Kryptonian.state(p).copy();
		n.xrayUntil = p.level().getGameTime() + KryptonianConfig.XRAY_TICKS;
		Kryptonian.save(p, n);
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, KryptonianConfig.XRAY_TICKS + 20, 0, false, false, true));
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.6f);
	}

	// ---------------------------------------------------------------- Shift+V: Super Grab / Throw

	public static void superGrab(ServerPlayer p) {
		if (GRAB.containsKey(p.getUUID())) {
			throwHeld(p);
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, KryptonianConfig.GRAB_RANGE);
		if (target == null || !KryptonianCombat.isTarget(p, target) || KryptonianCombat.isBoss(target)
				|| target.getBbWidth() > KryptonianConfig.GRAB_MAX_WIDTH || target.isPassenger() || target.isVehicle()) {
			if (Kryptonian.canAct(p)) {
				Kryptonian.say(p, "message.projecthero.kryptonian.grab_none", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, SUPER_GRAB, KryptonianConfig.GRAB_COST)) {
			return;
		}
		GRAB.put(p.getUUID(), new Grab(target.getId(), p.level().getGameTime() + KryptonianConfig.GRAB_HOLD_TICKS));
		target.setNoGravity(true);
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	private static void tickGrab(ServerPlayer p, Grab g, long now) {
		Entity e = p.level().getEntity(g.entityId());
		if (!(e instanceof LivingEntity held) || !held.isAlive() || !Kryptonian.empowered(p)) {
			GRAB.remove(p.getUUID());
			if (e != null) {
				e.setNoGravity(false);
			}
			return;
		}
		if (now >= g.until()) {
			throwHeld(p); // held too long: he hurls it anyway
			return;
		}
		Vec3 spot = p.getEyePosition().add(p.getLookAngle().scale(2.2 + held.getBbWidth() * 0.5)).add(0, -held.getBbHeight() * 0.5, 0);
		if (held instanceof ServerPlayer hp) {
			hp.teleportTo(spot.x, spot.y, spot.z);
		} else {
			held.moveTo(spot.x, spot.y, spot.z, held.getYRot(), held.getXRot());
		}
		held.setDeltaMovement(Vec3.ZERO);
		held.resetFallDistance();
		held.hurtMarked = true;
	}

	private static void throwHeld(ServerPlayer p) {
		Grab g = GRAB.remove(p.getUUID());
		if (g == null) {
			return;
		}
		cooldown(p, SUPER_GRAB, KryptonianConfig.GRAB_COOLDOWN);
		Entity e = p.level().getEntity(g.entityId());
		if (!(e instanceof LivingEntity held) || !held.isAlive()) {
			return;
		}
		held.setNoGravity(false);
		Kryptonian.setAnim(p, KryptonianState.ANIM_THROW);
		Vec3 v = p.getLookAngle().scale(KryptonianConfig.THROW_SPEED).add(0, 0.2, 0);
		held.setDeltaMovement(v);
		held.hurtMarked = true;
		held.hasImpulse = true;
		if (held instanceof ServerPlayer hp) {
			hp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(hp));
		}
		THROWN.put(held.getId(), new Thrown(p.getUUID(), p.level().getGameTime()));
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.4f, 0.5f);
	}

	private static void tickThrown(ServerPlayer p, long now) {
		if (THROWN.isEmpty()) {
			return;
		}
		ServerLevel level = level(p);
		for (var it = THROWN.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			Thrown t = en.getValue();
			if (!t.owner().equals(p.getUUID())) {
				continue;
			}
			Entity e = level.getEntity(en.getKey());
			if (!(e instanceof LivingEntity flying) || !flying.isAlive() || now - t.start() > 80) {
				it.remove();
				continue;
			}
			long age = now - t.start();
			level.sendParticles(ParticleTypes.CLOUD, flying.getX(), flying.getY() + flying.getBbHeight() * 0.5, flying.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			boolean hitMob = age > 1 && !level.getEntitiesOfClass(LivingEntity.class, flying.getBoundingBox().inflate(0.4),
					x -> x != flying && x != p && KryptonianCombat.isTarget(p, x)).isEmpty();
			if (age > 2 && (flying.horizontalCollision || flying.verticalCollision || flying.onGround() || hitMob || flying.isInWater())) {
				it.remove();
				Vec3 at = flying.position().add(0, flying.getBbHeight() * 0.5, 0);
				Set<Integer> hit = new HashSet<>();
				KryptonianCombat.strike(p, flying, at, KryptonianConfig.THROW_DAMAGE, 0.0, 0.0, true);
				hit.add(flying.getId());
				KryptonianCombat.radial(p, at, KryptonianConfig.THROW_IMPACT_RADIUS, KryptonianConfig.THROW_DAMAGE * 0.75f, 1.4, 0.4, hit);
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.3f);
				flying.resetFallDistance();
			}
		}
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		Heat h = HEAT.get(id);
		if (h != null) {
			tickHeat(p, h, now);
		} else if (Kryptonian.heatVisionActive(p)) {
			setHeatFlag(p, false); // a stale flag (reload) never leaves the beam drawn
		}
		Long b = BREATH.get(id);
		if (b != null) {
			tickBreath(p, b, now);
		}
		Long s = SLAM.get(id);
		if (s != null) {
			tickSlam(p, s, now);
		}
		Long f = FLARE.get(id);
		if (f != null) {
			tickFlare(p, f, now);
		} else if (Kryptonian.state(p).flareChargeStart != 0L) {
			KryptonianState n = Kryptonian.state(p).copy();
			n.flareChargeStart = 0L;
			Kryptonian.save(p, n);
		}
		Dash d = DASH.get(id);
		if (d != null) {
			tickDash(p, d, now);
		}
		Launch l = LAUNCH.get(id);
		if (l != null) {
			tickLaunch(p, l, now);
		}
		Grab g = GRAB.get(id);
		if (g != null) {
			tickGrab(p, g, now);
		}
		tickThrown(p, now);
	}
}
