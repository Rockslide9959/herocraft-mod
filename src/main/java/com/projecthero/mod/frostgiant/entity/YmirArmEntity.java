package com.projecthero.mod.frostgiant.entity;

import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: <b>Ymir's arm</b> -- render-only scenery. In phase 3 the first Frost Giant reaches through the rift: an
 * enormous glacier-grey arm (~40 blocks) whose palm sits at this entity's position (the model rises from there up to the
 * rift). The assault moves it and picks its clip (reach in, palm slam, glacial sweep, retract) and deals the damage itself
 * ({@code FrostGiantAssault#tickYmir}); this entity only carries the synced clip. It can't be hit, has no hit-box worth
 * the name and is never saved -- the assault re-summons it after a reload.
 */
public class YmirArmEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(YmirArmEntity.class, EntityDataSerializers.INT);

	private int animSeq;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private Clip clientClip;
	private int clientClipStart;
	private int mainClipStart = Integer.MIN_VALUE;

	public YmirArmEntity(EntityType<? extends YmirArmEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ANIM, FrostGiantAnims.indexOf(FrostGiantAnims.YMIR_REACH_IN));
	}

	public void play(Clip clip) {
		animSeq = (animSeq + 1) & 0x3FFFFF;
		entityData.set(DATA_ANIM, FrostGiantAnims.indexOf(clip) | (animSeq << 10));
	}

	public Clip clip() {
		return FrostGiantAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ANIM.equals(key) && level() != null && level().isClientSide()) {
			clientClip = FrostGiantAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
			clientClipStart = tickCount;
		}
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(40.0, 48.0, 40.0).move(0, 24.0, 0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
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

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<YmirArmEntity>(this, "main", FrostGiantAnims.TRANSITION, state -> {
			Clip clip = clientClip == null ? FrostGiantAnims.YMIR_REACH_IN : clientClip;
			int start = clientClip == null ? Integer.MIN_VALUE + 1 : clientClipStart;
			if (start != mainClipStart) {
				mainClipStart = start;
				state.getController().forceAnimationReset();
			}
			state.setAndContinue(FrostGiantEntity.raw(clip));
			return PlayState.CONTINUE;
		}));
	}
}
