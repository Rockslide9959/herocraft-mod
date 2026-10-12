package com.projecthero.mod.ultron.contagion.entity;

import com.projecthero.mod.ultron.contagion.Contagion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: the <b>Vibranium Core</b> -- the machine at the sky-citadel's centre, the Sokovia Protocol's anchor. It cannot
 * be hurt; it only shows its charge (synced, the client brightens the crystal with it). The finale owns the charge and the
 * boss bar ({@code SokoviaFinale}).
 */
public class VibraniumCoreEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Float> DATA_CHARGE = SynchedEntityData.defineId(VibraniumCoreEntity.class, EntityDataSerializers.FLOAT);
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public VibraniumCoreEntity(EntityType<? extends VibraniumCoreEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
		addTag(Contagion.UNIT_TAG);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_CHARGE, 0f);
	}

	public float charge() {
		return entityData.get(DATA_CHARGE);
	}

	public void setCharge(float c) {
		if (Math.abs(entityData.get(DATA_CHARGE) - c) > 0.002f) {
			entityData.set(DATA_CHARGE, c);
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
	protected void readAdditionalSaveData(CompoundTag tag) {
		entityData.set(DATA_CHARGE, tag.getFloat("Charge"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putFloat("Charge", charge());
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "idle", 0,
				state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation.vibranium_core.idle"))));
	}
}
