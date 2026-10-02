package com.projecthero.mod.moonknight.entity;

import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.ability.MoonKnightCombat;
import com.projecthero.mod.moonknight.ability.MoonKnightDarts;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R: a Crescent Dart. It flies flat and fast, spinning ({@code CrescentDartRenderer}); at night it gently bends toward
 * the nearest hostile mob near its path; whatever it hits first takes the dart's damage and the dart is spent. If it
 * hits a wall or flies its full range without hitting anything it turns round and curves back to the thrower like a
 * boomerang, passing through blocks on the way home, and is caught when it reaches them.
 *
 * <p>A Moon Mark dart (SNEAK+R) does not vanish on a hit: it sticks into the target and rides along with it for the
 * length of the mark.
 *
 * <p>Multiplayer: the owner, the homing target, the "returning" flag and the stuck-in target are synced entity data,
 * so every client simulates exactly the same curve instead of snapping to server corrections
 * ({@code Projectile.getOwner()} is null client-side, hence {@link #OWNER_ID}).
 */
public class CrescentDartEntity extends ThrowableProjectile {
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(CrescentDartEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(CrescentDartEntity.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(CrescentDartEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STUCK_ID = SynchedEntityData.defineId(CrescentDartEntity.class,
			EntityDataSerializers.INT);
	/** v0.14.4 Crescent Fan: locked on to one target (TARGET_ID) from the throw, turning hard -- synced so clients bend the same. */
	private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(CrescentDartEntity.class,
			EntityDataSerializers.BOOLEAN);

	// server-only flight parameters
	private float damage = MoonKnightConfig.DART_DAMAGE;
	private boolean homing;
	private int markTicks;
	private double range = MoonKnightConfig.DART_BOOMERANG_RANGE;
	private Vec3 origin;
	private long stuckUntil;

	public CrescentDartEntity(EntityType<? extends CrescentDartEntity> type, Level level) {
		super(type, level);
	}

	/**
	 * Throw one dart.
	 *
	 * @param damage     already lunar-scaled
	 * @param homing     bend toward hostile mobs (night)
	 * @param range      how far it flies before it turns back
	 * @param markTicks  0 for a plain dart; otherwise a Moon Mark of this length that sticks in
	 */
	public static CrescentDartEntity throwDart(ServerPlayer owner, Vec3 from, Vec3 dir, double speed, float damage,
			boolean homing, double range, int markTicks) {
		CrescentDartEntity dart = new CrescentDartEntity(MoonKnightEntities.CRESCENT_DART, owner.level());
		dart.setOwner(owner);
		dart.entityData.set(OWNER_ID, owner.getId());
		dart.setPos(from.x, from.y, from.z);
		dart.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0f);
		dart.damage = damage;
		dart.homing = homing;
		dart.range = range;
		dart.markTicks = markTicks;
		dart.origin = from;
		owner.level().addFreshEntity(dart);
		return dart;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
		builder.define(RETURNING, false);
		builder.define(TARGET_ID, -1);
		builder.define(STUCK_ID, -1);
		builder.define(LOCKED, false);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	public boolean isReturning() {
		return entityData.get(RETURNING);
	}

	public boolean isStuck() {
		return entityData.get(STUCK_ID) >= 0;
	}

	/**
	 * v0.14.4 Crescent Fan: lock this dart on to {@code target} -- it steers hard toward it every tick, never re-picks,
	 * and keeps flying (no boomerang at the usual range) until it hits, the target dies, or it runs out of life.
	 */
	public void lockOn(LivingEntity target) {
		entityData.set(TARGET_ID, target.getId());
		entityData.set(LOCKED, true);
		range = Math.max(range, MoonKnightConfig.DART_FAN_TARGET_RANGE * 2.0);
	}

	public boolean isLocked() {
		return entityData.get(LOCKED);
	}

	/** The entity id this dart is homing on, or -1. */
	public int targetId() {
		return entityData.get(TARGET_ID);
	}

	public boolean isMoonMark() {
		return markTicks > 0;
	}

	/** The thrower, on either side. */
	public Entity thrower() {
		Entity owner = getOwner();
		if (owner != null) {
			return owner;
		}
		int id = entityData.get(OWNER_ID);
		return id < 0 ? null : level().getEntity(id);
	}

	@Override
	public void tick() {
		if (isStuck()) {
			tickStuck();
			return;
		}
		if (isReturning()) {
			tickReturn();
			return;
		}
		if (!level().isClientSide) {
			if (tickCount > MoonKnightConfig.DART_MAX_LIFE_TICKS) {
				discard();
				return;
			}
			if (origin != null && position().distanceTo(origin) >= range) {
				startReturn();
				tickReturn();
				return;
			}
			if (isLocked()) {
				Entity t = level().getEntity(entityData.get(TARGET_ID));
				if (t == null || !t.isAlive()) {
					entityData.set(LOCKED, false); // its target is gone: fly on as a plain dart
					entityData.set(TARGET_ID, -1);
				}
			} else if (homing && tickCount % 2 == 0) {
				pickHomingTarget();
			}
		}
		steerTowardTarget();
		super.tick();
		if (level().isClientSide && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- homing

	private void pickHomingTarget() {
		Vec3 v = getDeltaMovement();
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		Vec3 dir = v.normalize();
		double r = MoonKnightConfig.DART_HOMING_RADIUS;
		Entity owner = getOwner();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(r),
				e -> com.projecthero.mod.combat.HeroTargets.isHostile(owner, e))) { // v0.14.20: homing, rule 2
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(position());
			double d = to.length();
			if (d > r || d < 1.0e-3 || to.normalize().dot(dir) < 0.35) {
				continue; // only mobs roughly ahead: it bends, it doesn't turn round
			}
			if (owner instanceof ServerPlayer p && MoonKnightCombat.friendly(p, e)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		entityData.set(TARGET_ID, best == null ? -1 : best.getId());
	}

	private void steerTowardTarget() {
		int id = entityData.get(TARGET_ID);
		if (id < 0) {
			return;
		}
		Entity t = level().getEntity(id);
		Vec3 v = getDeltaMovement();
		if (t == null || !t.isAlive() || v.lengthSqr() < 1.0e-6) {
			return;
		}
		double speed = v.length();
		Vec3 dir = v.normalize();
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(position()).normalize();
		double angle = Math.acos(Mth.clamp(dir.dot(to), -1.0, 1.0));
		double maxTurn = Math.toRadians(isLocked() ? MoonKnightConfig.DART_LOCKED_TURN : MoonKnightConfig.DART_HOMING_TURN);
		double f = angle <= maxTurn ? 1.0 : maxTurn / angle;
		Vec3 turned = dir.lerp(to, f).normalize();
		setDeltaMovement(turned.scale(speed));
	}

	// ---------------------------------------------------------------- boomerang

	private void startReturn() {
		entityData.set(RETURNING, true);
		entityData.set(TARGET_ID, -1);
		entityData.set(LOCKED, false);
		if (level() instanceof ServerLevel level) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4f, 1.9f);
		}
	}

	private void tickReturn() {
		noPhysics = true;
		Entity owner = thrower();
		if (owner == null || !owner.isAlive() || owner.level() != level()) {
			if (!level().isClientSide) {
				discard();
			}
			return;
		}
		Vec3 home = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
		Vec3 to = home.subtract(position());
		double dist = to.length();
		if (!level().isClientSide) {
			if (dist < MoonKnightConfig.DART_CATCH_DISTANCE) {
				level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN,
						SoundSource.PLAYERS, 0.5f, 1.7f);
				discard();
				return;
			}
			if (tickCount > MoonKnightConfig.DART_MAX_LIFE_TICKS + 100) {
				discard();
				return;
			}
		}
		Vec3 want = to.scale(MoonKnightConfig.DART_RETURN_SPEED / Math.max(1.0e-4, dist));
		Vec3 v = getDeltaMovement().lerp(want, 0.3);
		if (v.length() > dist) {
			v = v.normalize().scale(dist);
		}
		setDeltaMovement(v);
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		updateRotation();
		if (level().isClientSide && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- stuck in (Moon Mark)

	private void tickStuck() {
		noPhysics = true;
		Entity target = level().getEntity(entityData.get(STUCK_ID));
		if (target == null || !target.isAlive()) {
			if (!level().isClientSide) {
				discard();
			}
			return;
		}
		if (!level().isClientSide && level().getGameTime() > stuckUntil) {
			discard();
			return;
		}
		setDeltaMovement(Vec3.ZERO);
		// ride along on the side it went in from (the travel direction is frozen in the dart's own yaw)
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		Vec3 back = new Vec3(-Mth.sin(yaw), 0.0, -Mth.cos(yaw)).scale(target.getBbWidth() * 0.45);
		setPos(target.getX() + back.x, target.getY() + target.getBbHeight() * 0.62, target.getZ() + back.z);
		if (level().isClientSide && tickCount % 6 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.1, getZ(), 0.0, 0.02, 0.0);
		}
	}

	// ---------------------------------------------------------------- hits

	@Override
	protected boolean canHitEntity(Entity target) {
		if (isReturning() || isStuck() || !(target instanceof LivingEntity) || target == thrower()) {
			return false;
		}
		return super.canHitEntity(target);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel level) || !(result.getEntity() instanceof LivingEntity target)) {
			return;
		}
		if (!(getOwner() instanceof ServerPlayer owner)) {
			discard();
			return;
		}
		if (MoonKnightCombat.friendly(owner, target)) {
			return; // flies on past a squadmate
		}
		boolean landed = MoonKnightCombat.hit(owner, target, damageSources().thrown(this, owner), damage);
		Vec3 at = result.getLocation();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.25);
		level.sendParticles(MoonKnightCombat.MOON, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
		if (landed) {
			Vec3 v = getDeltaMovement();
			double len = Math.max(1.0e-4, v.horizontalDistance());
			if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)) {
				target.knockback(0.2, -v.x / len, -v.z / len);
			}
		}
		if (markTicks > 0) {
			MoonKnightDarts.applyMark(owner, target, markTicks);
			entityData.set(STUCK_ID, target.getId());
			entityData.set(TARGET_ID, -1);
			stuckUntil = level.getGameTime() + markTicks;
			setDeltaMovement(Vec3.ZERO);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.7f);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.05);
			return;
		}
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (level() instanceof ServerLevel level && !isReturning()) {
			Vec3 p = result.getLocation();
			level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 5, 0.1, 0.1, 0.1, 0.2);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.5f, 1.8f);
			// bounce back out of the wall and head home
			setDeltaMovement(getDeltaMovement().scale(-0.4));
			startReturn();
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
