package com.projecthero.mod.maxsteel.entity;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.2: the Turbo Cannon's discharge beam, as the world sees it. Purely visual -- the entity sits at the muzzle,
 * carries the beam's end point, half-width and charge in synced data, lives {@link #LIFE_TICKS} and removes itself.
 * {@code MaxSteelCannon} has already resolved every hit by the time it spawns, so there is nothing here a client
 * could exploit. Never saved.
 */
public class TurboCannonBeamEntity extends Entity {
	public static final int LIFE_TICKS = 14;

	private static final EntityDataAccessor<Vector3f> END =
			SynchedEntityData.defineId(TurboCannonBeamEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> WIDTH =
			SynchedEntityData.defineId(TurboCannonBeamEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CHARGE =
			SynchedEntityData.defineId(TurboCannonBeamEntity.class, EntityDataSerializers.FLOAT);

	public TurboCannonBeamEntity(EntityType<? extends TurboCannonBeamEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.noCulling = true;
	}

	public static TurboCannonBeamEntity spawn(ServerLevel level, Vec3 start, Vec3 end, double width, float charge) {
		TurboCannonBeamEntity beam = new TurboCannonBeamEntity(MaxSteelEntityTypes.TURBO_CANNON_BEAM, level);
		beam.setPos(start.x, start.y, start.z);
		beam.entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
		beam.entityData.set(WIDTH, (float) width);
		beam.entityData.set(CHARGE, charge);
		level.addFreshEntity(beam);
		return beam;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(END, new Vector3f());
		builder.define(WIDTH, 0.5f);
		builder.define(CHARGE, 0.0f);
	}

	public Vec3 end() {
		Vector3f v = this.entityData.get(END);
		return new Vec3(v.x(), v.y(), v.z());
	}

	public float width() {
		return this.entityData.get(WIDTH);
	}

	public float charge() {
		return this.entityData.get(CHARGE);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && this.tickCount >= LIFE_TICKS) {
			discard();
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
