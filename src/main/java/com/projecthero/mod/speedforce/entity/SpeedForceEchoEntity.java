package com.projecthero.mod.speedforce.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * v0.17.1 (user, Speed Force Memories): an echo -- a see-through after-image of the runner replaying one of their own
 * recent runs along the memory road. The server moves it a tick at a time ({@code SpeedForceRealm}) and gives back a little
 * Speed Force the first time the runner runs through it; clients draw it in the owner's skin, pale and translucent
 * ({@code SpeedForceEchoRenderer}). Never saved, can't be hit, passes through everything.
 */
public class SpeedForceEchoEntity extends Entity {
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(SpeedForceEchoEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	/** Set once the runner has run through it (it fades out). */
	private static final EntityDataAccessor<Boolean> DATA_SPENT = SynchedEntityData.defineId(SpeedForceEchoEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.17.1 (user, race ghosts): a race ghost is drawn in this lightning tint (0xRRGGBB); -1 = an ordinary pale echo. */
	private static final EntityDataAccessor<Integer> DATA_TINT = SynchedEntityData.defineId(SpeedForceEchoEntity.class, EntityDataSerializers.INT);

	public SpeedForceEchoEntity(EntityType<? extends SpeedForceEchoEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
		setInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_SPENT, false);
		builder.define(DATA_TINT, -1);
	}

	public void setOwner(UUID owner) {
		entityData.set(DATA_OWNER, Optional.ofNullable(owner));
	}

	public Optional<UUID> owner() {
		return entityData.get(DATA_OWNER);
	}

	public boolean spent() {
		return entityData.get(DATA_SPENT);
	}

	public int tint() {
		return entityData.get(DATA_TINT);
	}

	public void setTint(int rgb) {
		entityData.set(DATA_TINT, rgb);
	}

	public void setSpent(boolean spent) {
		entityData.set(DATA_SPENT, spent);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			// the server places it; between updates the client lerps
			return;
		}
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
