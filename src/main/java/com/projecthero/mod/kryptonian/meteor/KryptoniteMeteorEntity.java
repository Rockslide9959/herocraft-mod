package com.projecthero.mod.kryptonian.meteor;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.14.8: the Kryptonite Meteor in flight -- a flaming green rock that streaks down a straight line from the sky to its
 * impact point over {@code KryptonianConfig.METEOR_FALL_TICKS}. Purely the show: the impact itself (crater, ore, core)
 * is done by {@link MeteorManager} at the scheduled tick, so it happens even if nobody is near enough for this entity's
 * chunk to be ticking. Never saved.
 */
public class KryptoniteMeteorEntity extends Entity {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 2.5f);

	private Vec3 start;
	private Vec3 target;
	private int fallTicks = 1;

	public KryptoniteMeteorEntity(EntityType<? extends KryptoniteMeteorEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Sets the flight path. Call before adding it to the level. */
	public void setPath(Vec3 start, Vec3 target, int fallTicks) {
		this.start = start;
		this.target = target;
		this.fallTicks = Math.max(1, fallTicks);
		this.moveTo(start.x, start.y, start.z, 0.0f, 0.0f);
		this.setDeltaMovement(target.subtract(start).scale(1.0 / this.fallTicks));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 400.0 * 400.0;
	}

	@Override
	public boolean isOnFire() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			// the client moves it along its velocity between the server's position updates
			this.setPos(this.position().add(this.getDeltaMovement()));
			return;
		}
		if (start == null || target == null || tickCount > fallTicks + 5) {
			discard();
			return;
		}
		float t = Math.min(1.0f, tickCount / (float) fallTicks);
		Vec3 pos = start.lerp(target, t);
		this.setPos(pos.x, pos.y, pos.z);
		this.setDeltaMovement(target.subtract(start).scale(1.0 / fallTicks));
		this.hasImpulse = true;
		ServerLevel level = (ServerLevel) this.level();
		Vec3 back = this.getDeltaMovement().normalize().scale(-1.2);
		Vec3 c = pos.add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.6, 0.6, 0.6, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x + back.x, c.y + back.y, c.z + back.z, 4, 0.5, 0.5, 0.5, 0.02);
		level.sendParticles(GREEN, c.x, c.y, c.z, 6, 0.7, 0.7, 0.7, 0.0);
		if (tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0.0);
		}
		if (tickCount % 20 == 1) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.AMBIENT, 6.0f, 0.4f);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
