package com.projecthero.mod.stand.entity;

import com.projecthero.mod.stand.StandCombat;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.network.StandFxPayload;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "Ball bearing flick, player flicks a ball bearing that deals 20 damage"): a real steel ball, flicked by Star
 * Platinum's thumb at 80 blocks a second. It is solid metal, so everyone sees it (only the Stand that flicked it is hidden).
 * It hits the first thing in its path for {@link StandConfig#BEARING_DAMAGE} through {@link StandCombat#hit} (so it follows the
 * targeting rules, and in stopped time it hangs in the air like any of the caster's projectiles and lands its hit on resume).
 */
public class BallBearingEntity extends ThrowableProjectile {
	public BallBearingEntity(EntityType<? extends BallBearingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && tickCount > StandConfig.BEARING_LIFE) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && !(e instanceof BallBearingEntity) && e != getOwner()
				&& (!(getOwner() instanceof ServerPlayer sp) || StandCombat.isTarget(sp, e));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level().isClientSide()) {
			return;
		}
		if (getOwner() instanceof ServerPlayer sp && hit.getEntity() instanceof LivingEntity le) {
			Vec3 v = getDeltaMovement();
			StandCombat.hit(sp, le, StandConfig.BEARING_DAMAGE, v.lengthSqr() < 1.0e-4 ? Vec3.ZERO : v.normalize().scale(0.35));
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel sl) {
			Vec3 at = hit.getLocation();
			sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.15);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!level().isClientSide()) {
			if (level() instanceof ServerLevel sl && getOwner() instanceof ServerPlayer) {
				Stands.vfx(sl, StandFxPayload.IMPACT, hit.getLocation(), getDeltaMovement(), 0.45f);
			}
			discard();
		}
	}
}
