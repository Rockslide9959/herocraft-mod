package com.projecthero.mod.hero.power.p08;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis: the R Fireball and the Z Meteor. A straight-flying ball of fire (drawn as layered glowing
 * spheres with a flame tail by {@code PyroBoltRenderer}); the damage is fixed when it is thrown, so the Heat bonus is
 * the one you had at the cast. A Meteor's {@link #size()} (0.6 - 1.4 blocks) grows with the charge. v0.16.6: the hold-R
 * Blue Flame ({@link #blue()}) and the hold-Z Meteor Shower's smaller meteors ({@link #launchFrom}).
 */
public class PyroBoltEntity extends Fireball {
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(PyroBoltEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> METEOR = SynchedEntityData.defineId(PyroBoltEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.16.6: the hold-R Blue Flame (blue-white, not fire damage). */
	private static final EntityDataAccessor<Boolean> BLUE = SynchedEntityData.defineId(PyroBoltEntity.class, EntityDataSerializers.BOOLEAN);

	private float damage = PyrokinesisHandlers.FIREBALL_DAMAGE;
	private int life;
	/** v0.16.6: a meteor's blast radius (a Meteor Shower's are smaller). */
	private double blastRadius = PyrokinesisHandlers.METEOR_RADIUS;

	public PyroBoltEntity(EntityType<? extends PyroBoltEntity> type, Level level) {
		super(type, level);
	}

	/** A bolt from {@code owner}'s eye along {@code dir} at {@code speed} blocks a tick. */
	public static PyroBoltEntity launch(ServerPlayer owner, Vec3 dir, double speed, float damage, boolean meteor, float size) {
		PyroBoltEntity b = new PyroBoltEntity(PyroEntities.BOLT, owner.level());
		b.setOwner(owner);
		Vec3 d = dir.normalize();
		Vec3 from = owner.getEyePosition().add(d.scale(meteor ? 1.6 : 1.0)).add(0, meteor ? 0.1 : -0.15, 0);
		b.moveTo(from.x, from.y - b.getBbHeight() * 0.5, from.z, owner.getYRot(), owner.getXRot());
		b.setDeltaMovement(d.scale(speed));
		// steady speed under AbstractHurtingProjectile's 0.95 inertia: v = accel * 0.95 / 0.05
		b.accelerationPower = speed * 0.05 / 0.95;
		b.damage = damage;
		b.entityData.set(METEOR, meteor);
		b.entityData.set(SIZE, size);
		owner.level().addFreshEntity(b);
		return b;
	}

	/**
	 * v0.16.6: a meteor of a Meteor Shower -- from {@code from} (in the sky) along {@code dir}, a {@code blast}-block
	 * blast of {@code damage} where it lands.
	 */
	public static PyroBoltEntity launchFrom(ServerPlayer owner, Vec3 from, Vec3 dir, double speed, float damage, float size, double blast) {
		PyroBoltEntity b = new PyroBoltEntity(PyroEntities.BOLT, owner.level());
		b.setOwner(owner);
		Vec3 d = dir.normalize();
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
		float pitch = (float) (-Math.asin(Math.max(-1.0, Math.min(1.0, d.y))) * 180.0 / Math.PI);
		b.moveTo(from.x, from.y, from.z, yaw, pitch);
		b.setDeltaMovement(d.scale(speed));
		b.accelerationPower = speed * 0.05 / 0.95;
		b.damage = damage;
		b.blastRadius = blast;
		b.entityData.set(METEOR, true);
		b.entityData.set(SIZE, size);
		owner.level().addFreshEntity(b);
		return b;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SIZE, 0.45f);
		builder.define(METEOR, false);
		builder.define(BLUE, false);
	}

	public boolean blue() {
		return entityData.get(BLUE);
	}

	void setBlue(boolean blue) {
		entityData.set(BLUE, blue);
	}

	public double blastRadius() {
		return blastRadius;
	}

	public float size() {
		return entityData.get(SIZE);
	}

	public boolean meteor() {
		return entityData.get(METEOR);
	}

	public float damage() {
		return damage;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl) {
			if (++life > (meteor() ? 120 : 80)) {
				discard();
				return;
			}
			if (isInWater()) {
				sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.05);
				discard();
				return;
			}
			if (meteor() && life % 2 == 0) {
				sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + size() * 0.5, getZ(), 2, size() * 0.4, size() * 0.4,
						size() * 0.4, 0.01);
			}
		}
	}

	@Override
	protected ParticleOptions getTrailParticle() {
		return blue() ? ParticleTypes.SOUL_FIRE_FLAME : meteor() ? ParticleTypes.LAVA : ParticleTypes.FLAME;
	}

	@Override
	protected boolean shouldBurn() {
		return false; // the model is the fire; vanilla's burning overlay would sit on top
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false; // can't be punched back
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && e != getOwner() && !(e instanceof PyroBoltEntity) && !(e instanceof PyroFxEntity);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		// handled in onHit, so a meteor's blast is not doubled up with the direct hit
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		// no vanilla block side effects
	}

	@Override
	protected void onHit(HitResult result) {
		if (!(level() instanceof ServerLevel sl)) {
			return;
		}
		Vec3 at = result.getLocation();
		LivingEntity direct = result instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le ? le : null;
		if (getOwner() instanceof ServerPlayer p) {
			if (meteor()) {
				PyrokinesisHandlers.meteorImpact(p, sl, at, damage, size(), result instanceof BlockHitResult bhr ? bhr : null, blastRadius);
			} else if (blue()) {
				PyrokinesisHandlers.blueImpact(p, sl, at, direct, damage, result instanceof BlockHitResult bhr ? bhr : null);
			} else {
				PyrokinesisHandlers.fireballImpact(p, sl, at, direct, damage, result instanceof BlockHitResult bhr ? bhr : null);
			}
		}
		discard();
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
