package com.projecthero.mod.bluebeetle.entity;

import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.BlueBeetleCombat;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 (user: "give plasma blaster a projectile model"): one Plasma Blaster bolt -- a real, synced projectile drawn
 * as a glowing blue orb with a short trail ({@code PlasmaBoltRenderer}). Straight line, no gravity, 15 damage through
 * {@link BlueBeetleCombat#strike} (targeting rules, boss cap, Unleashed bonus). Gone after {@code BLAST_RANGE} blocks.
 */
public class PlasmaBoltEntity extends Projectile {
	private int life;

	public PlasmaBoltEntity(EntityType<? extends PlasmaBoltEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static PlasmaBoltEntity shoot(ServerPlayer owner, Vec3 from, Vec3 velocity) {
		PlasmaBoltEntity bolt = new PlasmaBoltEntity(BlueBeetleEntityTypes.PLASMA_BOLT, owner.level());
		bolt.setOwner(owner);
		bolt.moveTo(from.x, from.y, from.z, 0f, 0f);
		bolt.setDeltaMovement(velocity);
		double h = velocity.horizontalDistance();
		bolt.setYRot((float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
		bolt.setXRot((float) Math.toDegrees(Math.atan2(velocity.y, h)));
		bolt.yRotO = bolt.getYRot();
		bolt.xRotO = bolt.getXRot();
		owner.level().addFreshEntity(bolt);
		return bolt;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = this.getDeltaMovement();
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hit.getType() != HitResult.Type.MISS && !this.level().isClientSide) {
			this.onHit(hit);
			if (this.isRemoved()) {
				return;
			}
		}
		this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
		if (++this.life * v.length() > BlueBeetleConfig.BLAST_RANGE || this.life > 100) {
			if (!this.level().isClientSide) {
				this.discard();
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!super.canHitEntity(e) || !(e instanceof LivingEntity le)) {
			return false;
		}
		return !(this.getOwner() instanceof ServerPlayer p) || BlueBeetleCombat.isTarget(p, le);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (this.getOwner() instanceof ServerPlayer p && result.getEntity() instanceof LivingEntity le) {
			BlueBeetleCombat.strike(p, le, this.position().subtract(this.getDeltaMovement()), BlueBeetleConfig.BLAST_DAMAGE, 0.3, 0.1);
		}
		burst(result.getLocation());
		this.discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		burst(result.getLocation());
		this.discard();
	}

	private void burst(Vec3 at) {
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.15);
			level.sendParticles(BlueBeetle.BLUE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.0);
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}
}
