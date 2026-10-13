package com.projecthero.mod.galactus.entity;

import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: <b>Taa II, the Worldship</b> -- render-only scenery hanging over the arena from Act 2 on. No hit-box worth the
 * name, can't be hit, never saved (the event summons it again after a reload); the event moves it and picks its clip
 * (idle, leave).
 */
public class WorldshipEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(WorldshipEntity.class, EntityDataSerializers.INT);

	private int animSeq;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private Clip clientClip;
	private int clientClipStart;
	private int mainClipStart = Integer.MIN_VALUE;

	public WorldshipEntity(EntityType<? extends WorldshipEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ANIM, GalactusAnims.indexOf(GalactusAnims.WORLDSHIP_IDLE));
	}

	public void play(Clip clip) {
		animSeq = (animSeq + 1) & 0x3FFFFF;
		entityData.set(DATA_ANIM, GalactusAnims.indexOf(clip) | (animSeq << 10));
	}

	public Clip clip() {
		return GalactusAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ANIM.equals(key) && level() != null && level().isClientSide()) {
			clientClip = GalactusAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
			clientClipStart = tickCount;
		}
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(90.0, 40.0, 90.0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 400 * 400;
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
		controllers.add(new AnimationController<WorldshipEntity>(this, "main", GalactusAnims.TRANSITION, state -> {
			Clip clip = clientClip == null ? GalactusAnims.WORLDSHIP_IDLE : clientClip;
			if (clientClipStart != mainClipStart) {
				mainClipStart = clientClipStart;
				state.getController().forceAnimationReset();
			}
			return state.setAndContinue(GalactusMob.raw(clip));
		}));
	}
}
