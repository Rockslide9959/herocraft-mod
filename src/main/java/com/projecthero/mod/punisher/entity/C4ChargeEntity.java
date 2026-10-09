package com.projecthero.mod.punisher.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * v0.15.18: the Explosive Charge (C4) ability is gone. The entity type stays registered (as {@code projecthero:c4_charge})
 * only so a world saved with a charge still loads cleanly -- any such leftover charge quietly removes itself on its first
 * tick, without exploding. Nothing spawns it any more.
 */
public class C4ChargeEntity extends Entity {
	public C4ChargeEntity(EntityType<? extends C4ChargeEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			discard();
		}
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
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
