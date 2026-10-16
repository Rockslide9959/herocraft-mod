package com.projecthero.mod.hero.power.p25;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Water Manipulation rework: the shared base of the power's water bodies -- the Water Shot bolt, the Water Whip,
 * the Water Prison sphere, the Tidal Wave wall, the Geyser column and the Healing Water swirl. They are all entities
 * (never water blocks), so nothing is ever left behind: each one runs out on its own clock and is never saved.
 *
 * <p>Synced: the caster's entity id (the client draws the whip from his hand), a target's entity id (the prison and the
 * healing swirl wrap it) and one size value; the client draws the translucent animated water model for each kind
 * ({@code WaterFxRenderer}) from these plus its own tick count.
 */
public abstract class WaterFxEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(WaterFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(WaterFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(WaterFxEntity.class, EntityDataSerializers.FLOAT);

	protected UUID ownerUuid;

	protected WaterFxEntity(EntityType<? extends WaterFxEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	protected void setup(ServerPlayer owner, LivingEntity target, float size) {
		this.ownerUuid = owner.getUUID();
		this.entityData.set(OWNER, owner.getId());
		this.entityData.set(TARGET, target == null ? 0 : target.getId());
		this.entityData.set(SIZE, size);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, 0);
		builder.define(TARGET, 0);
		builder.define(SIZE, 1.0f);
	}

	public int ownerId() {
		return this.entityData.get(OWNER);
	}

	public int targetId() {
		return this.entityData.get(TARGET);
	}

	public float size() {
		return this.entityData.get(SIZE);
	}

	protected void setSize(float s) {
		this.entityData.set(SIZE, s);
	}

	/** The caster (server side), or null once he has left. */
	protected ServerPlayer owner() {
		if (ownerUuid == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getPlayerByUUID(ownerUuid) instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
	}

	/** The target (either side), or null. */
	public LivingEntity target() {
		int id = targetId();
		return id != 0 && level().getEntity(id) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	protected static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
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
}
