package com.projecthero.mod.spider.entity;

import com.projecthero.mod.spider.SpiderAbilities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Web Shot (R tap) as a real projectile -- a small spinning glob of webbing with trailing strands
 * ({@code client.spider.WebShotRenderer}), fast and straight. On a body it lands the Web Shot's hit
 * ({@link SpiderAbilities#webShotHit}); on a block it splats (and smothers small fires). Either way: a burst of web and
 * a wet splat.
 */
public class SpiderWebShotEntity extends AbstractHurtingProjectile {
	public static final double SPEED = 2.6;
	private static final int LIFE = 14;
	private int life;

	public SpiderWebShotEntity(EntityType<? extends SpiderWebShotEntity> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0;
	}

	public SpiderWebShotEntity(Level level, LivingEntity owner, Vec3 movement) {
		super(SpiderEntityTypes.WEB_SHOT, owner, movement, level);
		this.accelerationPower = 0.0;
		setDeltaMovement(movement);
	}

	/** A faint trail of web fibres instead of the base class's smoke. */
	@Override
	protected net.minecraft.core.particles.ParticleOptions getTrailParticle() {
		return ParticleTypes.WHITE_ASH;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected float getInertia() {
		return 1.0f;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			// keep a constant speed (accelerationPower is 0; the base class would otherwise leave it at its spawn nudge)
			Vec3 v = getDeltaMovement();
			double len = v.length();
			if (len > 1.0e-4 && Math.abs(len - SPEED) > 0.02) {
				setDeltaMovement(v.scale(SPEED / len));
			}
		}
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		if (++life > LIFE) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity)
				&& (!(entity instanceof LivingEntity le) || com.projecthero.mod.combat.HeroTargets.canHarm(getOwner(), le));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level().isClientSide() || !(hit.getEntity() instanceof LivingEntity target) || !(getOwner() instanceof ServerPlayer shooter)) {
			return;
		}
		SpiderAbilities.webShotHit(shooter, target);
		splat(position());
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (!level().isClientSide() && getOwner() instanceof ServerPlayer shooter) {
			SpiderAbilities.webShotBlock(shooter, hit.getLocation());
			splat(hit.getLocation());
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		if (hit.getType() != HitResult.Type.MISS) {
			super.onHit(hit);
		}
	}

	private void splat(Vec3 at) {
		if (!isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.04);
		level.playSound(null, at.x, at.y, at.z, com.projecthero.mod.spider.SpiderSounds.SPLAT, SoundSource.PLAYERS, 0.6f, 1.4f);
		discard();
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
