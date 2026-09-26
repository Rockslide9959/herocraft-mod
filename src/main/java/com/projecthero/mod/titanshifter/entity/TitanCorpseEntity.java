package com.projecthero.mod.titanshifter.entity;

import java.util.List;

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
 * v0.12.36 -- what a Titan leaves behind when its shifter changes back: the body slumps where it stood, steams every
 * three seconds and dissolves over a full minute, breaking apart piece by piece (the renderer hides one
 * {@code piece_*} bone of the sliced corpse model after another, see {@link #PIECES}). Purely cosmetic: not saved,
 * not pickable, not solid, cannot be hurt.
 */
public class TitanCorpseEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_TYPE =
			SynchedEntityData.defineId(TitanCorpseEntity.class, EntityDataSerializers.INT);
	/** Ticks since the corpse formed, refreshed every {@link #SYNC_EVERY} ticks; clients interpolate in between. */
	private static final EntityDataAccessor<Integer> DATA_AGE =
			SynchedEntityData.defineId(TitanCorpseEntity.class, EntityDataSerializers.INT);
	private static final int SYNC_EVERY = 5;

	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.titan.death");

	/** The sliced model's pieces in the order they break off: extremities first, the head last. */
	public static final List<String> PIECES = List.of(
			"piece_rarm_2", "piece_larm_2", "piece_rleg_2", "piece_lleg_2",
			"piece_rarm_1", "piece_larm_1", "piece_rleg_1", "piece_lleg_1",
			"piece_body_2", "piece_rarm_0", "piece_larm_0", "piece_rleg_0", "piece_lleg_0",
			"piece_head_1", "piece_body_1", "piece_body_0", "piece_head_0");

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
	}

	public TitanType titanType() {
		return TitanType.byOrdinal(this.entityData.get(DATA_TYPE));
	}

	public static int pieceIndex(String bone) {
		return bone.startsWith("piece_") ? PIECES.indexOf(bone) : -1;
	}

	/** The fraction (0..1) of the dissolve at which piece {@code index} is gone -- staggered through the minute with a little jitter. */
	public static float vanishFraction(int index) {
		float jitter = (((index * 7919) % 13) / 13.0f - 0.5f) * 0.03f;
		return 0.06f + 0.90f * index / (PIECES.size() - 1) + jitter;
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
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
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
			return;
		}
		clientCrumble();
	}

	private void steam(ServerLevel sl) {
		double h = getBbHeight();
		float p = age / (float) dissolveTicks();
		int n = Math.max(6, (int) (26 * (1.0f - 0.6f * p)));
		sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + h * 0.3, getZ(), n, 1.6, h * 0.22, 1.6, 0.05);
		sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + h * 0.4, getZ(), 6, 1.2, h * 0.25, 1.2, 0.02);
		sl.playSound(null, getX(), getY() + h * 0.3, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.6f, 0.5f);
	}

	/** Client: a puff of dust where each piece lets go. */
	private void clientCrumble() {
		float now = dissolveAge(0.0f);
		float before = now - 1.0f;
		float total = dissolveTicks();
		for (int i = 0; i < PIECES.size(); i++) {
			float at = vanishFraction(i) * total;
			if (before < at && now >= at) {
				double h = getBbHeight();
				for (int k = 0; k < 6; k++) {
					level().addParticle(ParticleTypes.POOF, getX() + (random.nextDouble() - 0.5) * 3.0,
							getY() + random.nextDouble() * h * 0.45, getZ() + (random.nextDouble() - 0.5) * 3.0, 0.0, 0.05, 0.0);
				}
				level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5) * 2.0, getY() + h * 0.2, getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.08, 0.0);
			}
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
			state.setAndContinue(DEATH);
			return PlayState.CONTINUE;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
