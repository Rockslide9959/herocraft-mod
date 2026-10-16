package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R -- Water Shot: a pressurised bolt of water (the client draws a wobbling translucent water drop stretched along its
 * flight, with a spray trail). It flies {@link #SPEED} blocks a tick for up to {@link #LIFE} ticks; the first creature it
 * meets takes {@link WaterHandlers#SHOT_DAMAGE} and is knocked back, and fire is put out -- on the creature, and in the
 * block it splashes against.
 */
public class WaterBoltEntity extends WaterFxEntity {
	public static final double SPEED = 2.2;
	public static final int LIFE = 16;

	public WaterBoltEntity(EntityType<? extends WaterBoltEntity> type, Level level) {
		super(type, level);
	}

	public static WaterBoltEntity fire(ServerPlayer owner) {
		ServerLevel level = AbilityHelpers.level(owner);
		WaterBoltEntity e = new WaterBoltEntity(WaterEntities.WATER_BOLT, level);
		e.setup(owner, null, 1.0f);
		Vec3 look = owner.getLookAngle();
		Vec3 from = owner.getEyePosition().add(look.scale(0.6)).add(0, -0.2, 0);
		e.setPos(from.x, from.y, from.z);
		e.setDeltaMovement(look.scale(SPEED));
		e.setYRot(owner.getYRot());
		e.setXRot(owner.getXRot());
		level.addFreshEntity(e);
		return e;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level().isClientSide()) {
			return; // the server moves it (updated every tick)
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || tickCount > LIFE) {
			discard();
			return;
		}
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		LivingEntity struck = null;
		double best = Double.MAX_VALUE;
		AABB sweep = new AABB(from, end).inflate(0.45);
		for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, sweep, le -> le != owner && le.isAlive()
				&& com.projecthero.mod.combat.HeroTargets.canHarm(owner, le))) {
			var hit = le.getBoundingBox().inflate(0.35).clip(from, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(from);
				if (d < best) {
					best = d;
					struck = le;
				}
			}
		}
		if (struck != null) {
			hitEntity(owner, struck);
			splash(level, mid(struck));
			discard();
			return;
		}
		if (block.getType() == HitResult.Type.BLOCK) {
			WaterHandlers.douse(level, block.getBlockPos().relative(block.getDirection()), 1);
			splash(level, end);
			discard();
			return;
		}
		setPos(to.x, to.y, to.z);
		setDeltaMovement(v.add(0, -0.012, 0));
		if (tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** The bolt strikes {@code t}: 12, knockback along the flight, any fire put out. */
	public void hitEntity(ServerPlayer owner, LivingEntity t) {
		if (AbilityHelpers.hurtLands(owner, t, WaterHandlers.SHOT_DAMAGE)) {
			Vec3 dir = getDeltaMovement().lengthSqr() < 1.0e-6 ? owner.getLookAngle() : getDeltaMovement().normalize();
			AbilityHelpers.knockbackFrom(t, t.position().subtract(dir), 0.7);
		}
		t.clearFire();
	}

	private void splash(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.2);
		level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.05);
		level.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.8f, 1.4f);
	}
}
