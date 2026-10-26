package com.projecthero.mod.speedforce.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * v0.17.1 (user, Speed Force Memories): one glowing doorway off the memory road -- only a marker: the server's
 * {@code SpeedForceRealm} knows where it leads and takes a runner who runs through it; clients draw its swirl of lightning
 * ({@code SpeedForceClient}) and its floating label (the custom name, always shown). Never saved (the type is
 * {@code noSave}), never moves, can't be hit.
 */
public class SpeedForceDoorwayEntity extends Entity {
	/** The doorway's kind (home / death / exit / marked) -- the client tints the swirl by it. */
	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(SpeedForceDoorwayEntity.class, EntityDataSerializers.INT);
	/** Which way the doorway faces along z (+1 / -1): the branch it stands at the end of. */
	private static final EntityDataAccessor<Integer> DATA_SIDE = SynchedEntityData.defineId(SpeedForceDoorwayEntity.class, EntityDataSerializers.INT);

	public SpeedForceDoorwayEntity(EntityType<? extends SpeedForceDoorwayEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
		setInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_KIND, 0);
		builder.define(DATA_SIDE, 1);
	}

	public void setup(int kind, int side) {
		entityData.set(DATA_KIND, kind);
		entityData.set(DATA_SIDE, side);
		setCustomNameVisible(true);
	}

	public int kind() {
		return entityData.get(DATA_KIND);
	}

	public int side() {
		return entityData.get(DATA_SIDE);
	}

	@Override
	public void tick() {
		// a marker: nothing moves
		xo = getX();
		yo = getY();
		zo = getZ();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
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
