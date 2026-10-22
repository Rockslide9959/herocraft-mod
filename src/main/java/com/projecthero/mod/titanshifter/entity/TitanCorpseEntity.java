package com.projecthero.mod.titanshifter.entity;

import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;

/**
 * v0.12.36 -- what a Titan leaves behind when its shifter changes back. v0.16.7 (user, Titan revamp): it falls to its
 * knees and bows its head over the ground ({@code animation.titan.corpse_kneel}), the nape torn open where the shifter
 * climbed out, steams, and dissolves over the minute -- skin, then muscle, then skeleton, every pixel blowing away on the
 * wind (all client-side, see {@code TitanCorpseRenderer}). It is sized like the Titan type it was. Purely cosmetic: not
 * saved, not pickable, not solid, cannot be hurt.
 */
public class TitanCorpseEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_TYPE =
			SynchedEntityData.defineId(TitanCorpseEntity.class, EntityDataSerializers.INT);
	/** Ticks since the corpse formed, refreshed every {@link #SYNC_EVERY} ticks; clients interpolate in between. */
	private static final EntityDataAccessor<Integer> DATA_AGE =
			SynchedEntityData.defineId(TitanCorpseEntity.class, EntityDataSerializers.INT);
	private static final int SYNC_EVERY = 5;
	/** v0.12.39: the corpse of an Emergency Titan -- smaller and pale. */
	private static final EntityDataAccessor<Boolean> DATA_EMERGENCY =
			SynchedEntityData.defineId(TitanCorpseEntity.class, EntityDataSerializers.BOOLEAN);

	private static final RawAnimation KNEEL = RawAnimation.begin().thenPlayAndHold("animation.titan.corpse_kneel");
	private static final RawAnimation DEFEAT = RawAnimation.begin().thenPlayAndHold("animation.titan.corpse_defeat");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int age;
	private int clientAge;
	private int clientAgeAt;

	public TitanCorpseEntity(EntityType<? extends TitanCorpseEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public void bind(TitanType type) {
		this.entityData.set(DATA_TYPE, type.ordinal());
		refreshDimensions();
	}

	public void bind(TitanType type, boolean emergency) {
		bind(type);
		this.entityData.set(DATA_EMERGENCY, emergency);
		refreshDimensions();
	}

	public boolean isEmergency() {
		return this.entityData.get(DATA_EMERGENCY);
	}

	/**
	 * v0.16.7 (user, Titan revamp): why the body was left behind (synced, for the visuals) -- {@link #CAUSE_REVERT} the
	 * shifter climbed out after the 3 s REVERTING steam, {@link #CAUSE_CUT_OUT} they were cut out of the nape, or
	 * {@link #CAUSE_DEFEAT} the Titan fell at 0 HP.
	 */
	private static final EntityDataAccessor<Integer> DATA_CAUSE =
			SynchedEntityData.defineId(TitanCorpseEntity.class, EntityDataSerializers.INT);
	public static final int CAUSE_REVERT = 0, CAUSE_CUT_OUT = 1, CAUSE_DEFEAT = 2;

	public int cause() {
		return this.entityData.get(DATA_CAUSE);
	}

	public void setCause(int cause) {
		this.entityData.set(DATA_CAUSE, cause);
	}

	@Override
	public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
		net.minecraft.world.entity.EntityDimensions base = super.getDimensions(pose);
		if (this.entityData == null) {
			return base;
		}
		// v0.16.7: sized to its own Titan type, and ~60% of it for an Emergency Titan
		TitanType t = titanType();
		base = net.minecraft.world.entity.EntityDimensions.scalable(t.dimensionWidth(), t.dimensionHeight());
		return isEmergency() ? base.scale((float) TitanShifterConfig.emergency().heightFraction) : base;
	}

	public TitanType titanType() {
		return TitanType.byOrdinal(this.entityData.get(DATA_TYPE));
	}

	/** Ticks since the corpse formed, smooth on the client. */
	public float dissolveAge(float partialTick) {
		if (level().isClientSide) {
			return clientAge + Math.min(SYNC_EVERY + 2, tickCount - clientAgeAt) + partialTick;
		}
		return age + partialTick;
	}

	public static int dissolveTicks() {
		return Math.max(20, TitanShifterConfig.transformation().corpseTicks);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TYPE, 0);
		builder.define(DATA_AGE, 0);
		builder.define(DATA_EMERGENCY, false);
		builder.define(DATA_CAUSE, CAUSE_REVERT);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_EMERGENCY.equals(accessor) || DATA_TYPE.equals(accessor)) {
			refreshDimensions();
		}
		if (DATA_AGE.equals(accessor)) {
			clientAge = this.entityData.get(DATA_AGE);
			clientAgeAt = tickCount;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl) {
			age++;
			if (age % SYNC_EVERY == 0) {
				this.entityData.set(DATA_AGE, age);
			}
			int steamEvery = Math.max(10, TitanShifterConfig.transformation().corpseSteamTicks);
			if (age % steamEvery == 0) {
				steam(sl);
			}
			if (age >= dissolveTicks()) {
				sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 40, 1.5, 0.6, 1.5, 0.05);
				discard();
			}
		}
	}

	private void steam(ServerLevel sl) {
		double h = getBbHeight();
		float p = age / (float) dissolveTicks();
		int n = Math.max(6, (int) (26 * (1.0f - 0.6f * p)));
		sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + h * 0.3, getZ(), n, 1.6, h * 0.22, 1.6, 0.05);
		sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + h * 0.4, getZ(), 6, 1.2, h * 0.25, 1.2, 0.02);
		sl.playSound(null, getX(), getY() + h * 0.3, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.6f, 0.5f);
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
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "corpse", 0, state -> {
			// a defeated Titan was already down on its hands; any other body falls to its knees
			state.setAndContinue(cause() == CAUSE_DEFEAT ? DEFEAT : KNEEL);
			return PlayState.CONTINUE;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
