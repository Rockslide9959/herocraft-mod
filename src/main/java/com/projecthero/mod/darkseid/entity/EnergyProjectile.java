package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A weightless bolt of energy that flies under its own steering: the base of Darkseid's {@link OmegaBeamEntity}
 * and the Parademons' {@link ParademonBoltEntity}.
 *
 * <p>Deliberately a plain {@link Entity} rather than a vanilla {@code Projectile}: those hard-wire gravity, drag
 * and an owner-leave rule that fight a steered beam, and {@code Projectile}'s constructor is package-private
 * anyway. The hit test is still vanilla's own ({@link ProjectileUtil#getHitResultOnMoveVector}), so a bolt
 * collides exactly like an arrow does. Never saved (see the entity type) and always short-lived.
 *
 * <p>The glowing trail is drawn client-side from {@link #trail}, a small ring of the positions the entity has
 * actually been at -- that is what lets a curving Omega Beam read as a bent ribbon of light without the server
 * sending a single particle for it.
 */
public abstract class EnergyProjectile extends Entity {
	/** Client-side trail history, newest first. */
	public static final int TRAIL_LENGTH = 14;
	public final Vec3[] trail = new Vec3[TRAIL_LENGTH];
	public int trailCount;

	private UUID ownerId;
	private Entity cachedOwner;
	protected int life;

	protected EnergyProjectile(EntityType<? extends EnergyProjectile> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setOwner(Entity owner) {
		this.cachedOwner = owner;
		this.ownerId = owner == null ? null : owner.getUUID();
	}

	public Entity getOwner() {
		if (cachedOwner != null && !cachedOwner.isRemoved()) {
			return cachedOwner;
		}
		if (ownerId != null && level() instanceof ServerLevel server) {
			cachedOwner = server.getEntity(ownerId);
			return cachedOwner;
		}
		return null;
	}

	/** Maximum ticks alive before it simply fizzles. */
	protected abstract int maxLife();

	/** Server: change {@code getDeltaMovement()} before the move. */
	protected abstract void steer(ServerLevel level);

	/** Whether this bolt may strike {@code target}. */
	protected abstract boolean canHit(Entity target);

	protected abstract void onStrikeEntity(ServerLevel level, Entity target);

	/** Blocks stop every energy bolt -- which is exactly why cover works against the Omega Beams. */
	protected abstract void onStrikeBlock(ServerLevel level, Vec3 at);

	/** Trail colour for the client renderer, 0xRRGGBB. */
	public abstract int trailColor();

	/** Trail core colour (brighter centre), 0xRRGGBB. */
	public abstract int coreColor();

	/** Ribbon half-width, in blocks. */
	public abstract float trailWidth();

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			for (int i = TRAIL_LENGTH - 1; i > 0; i--) {
				trail[i] = trail[i - 1];
			}
			trail[0] = position();
			trailCount = Math.min(TRAIL_LENGTH, trailCount + 1);
			return;
		}
		ServerLevel server = (ServerLevel) level();
		if (++life > maxLife()) {
			discard();
			return;
		}
		steer(server);
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitInternal, ClipContext.Block.COLLIDER);
		if (hit.getType() == HitResult.Type.ENTITY) {
			onStrikeEntity(server, ((EntityHitResult) hit).getEntity());
			discard();
			return;
		}
		if (hit.getType() == HitResult.Type.BLOCK) {
			onStrikeBlock(server, hit.getLocation());
			discard();
			return;
		}
		Vec3 v = getDeltaMovement();
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		faceAlong(v);
	}

	private boolean canHitInternal(Entity target) {
		return target != this && target.isAlive() && !target.isSpectator() && canHit(target);
	}

	protected void faceAlong(Vec3 v) {
		double h = v.horizontalDistance();
		setYRot((float) (Math.atan2(v.x, v.z) * (180.0 / Math.PI)));
		setXRot((float) (Math.atan2(v.y, h) * (180.0 / Math.PI)));
	}

	/** Rotate {@code current} toward {@code wanted} by at most {@code maxDegrees}, keeping its length. */
	protected static Vec3 turnToward(Vec3 current, Vec3 wanted, double maxDegrees) {
		double speed = current.length();
		if (speed < 1.0e-6 || wanted.lengthSqr() < 1.0e-6) {
			return current;
		}
		Vec3 a = current.normalize();
		Vec3 b = wanted.normalize();
		double dot = Math.max(-1.0, Math.min(1.0, a.dot(b)));
		double angle = Math.acos(dot);
		double max = Math.toRadians(maxDegrees);
		if (angle <= max) {
			return b.scale(speed);
		}
		// slerp by max/angle
		double t = max / angle;
		double sin = Math.sin(angle);
		Vec3 out = a.scale(Math.sin((1 - t) * angle) / sin).add(b.scale(Math.sin(t * angle) / sin));
		return out.normalize().scale(speed);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	/** Particles-only impact flash -- no block is ever broken by the raid's energy weapons. */
	protected void burst(ServerLevel level, Vec3 at, net.minecraft.core.particles.ParticleOptions particle, int count) {
		level.sendParticles(particle, at.x, at.y, at.z, count, 0.25, 0.25, 0.25, 0.08);
	}

	protected static boolean isRaidHostile(Entity e) {
		return e instanceof DarkseidEntity || e instanceof ParademonEntity;
	}

	/** Exposed for the renderer: last hit-block normal is not needed, only the travel direction. */
	public Vec3 travelDirection() {
		Vec3 v = getDeltaMovement();
		return v.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(getXRot(), getYRot()) : v.normalize();
	}
}
