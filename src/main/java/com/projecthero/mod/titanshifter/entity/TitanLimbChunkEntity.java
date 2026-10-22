package com.projecthero.mod.titanshifter.entity;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.titanshifter.TitanCombat;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.7 (user, Titan fixes): a severed Titan limb. When {@link TitanFormEntity#sever} cuts an arm or leg off, the limb
 * itself is flung from the joint -- drawn by the client with the Titan's own model (only that limb's bones), texture and
 * scale -- tumbles, lands with a thud, lies steaming, then dissolves like the corpse (skin, flesh, bone) with the
 * pixels blowing away on the wind ({@code TitanLimbChunkRenderer}). The Titan meanwhile shows its stump and regrows.
 *
 * <p>Purely cosmetic: not saved, not solid, cannot be hurt. At most {@link TitanShifterConfig.Limbs#maxChunks} lie about
 * at once server-wide (the oldest goes first); the list is cleared by {@code ServerStateReset}.
 */
public class TitanLimbChunkEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LIMB = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_EMERGENCY = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.BOOLEAN);
	/** The Titan's height when the limb came off (the chunk is drawn at that scale). */
	private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.FLOAT);
	/** Ticks since it was cut off, refreshed every {@link #SYNC_EVERY}; clients interpolate in between. */
	private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.INT);
	/** The age it landed at; -1 while it still flies. */
	private static final EntityDataAccessor<Integer> DATA_LANDED = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(TitanLimbChunkEntity.class, EntityDataSerializers.INT);
	private static final int SYNC_EVERY = 5;
	/** It lies steaming at least this long after landing before the skin starts to go. */
	public static final int STEAM_TICKS = 40;
	/** The longest it may fly before it counts as landed anyway (it fell off a cliff, into water...). */
	public static final int MAX_FLIGHT = 60;

	private static final List<TitanLimbChunkEntity> LIVE = new ArrayList<>();

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int age;
	private int clientAge;
	private int clientAgeAt;

	public TitanLimbChunkEntity(EntityType<? extends TitanLimbChunkEntity> type, Level level) {
		super(type, level);
	}

	/** ServerStateReset: forget every chunk (never saved, so they went with the world). */
	public static void clearSessionState() {
		LIVE.clear();
	}

	/** Chunks lying about now. */
	public static int liveCount() {
		LIVE.removeIf(Entity::isRemoved);
		return LIVE.size();
	}

	/**
	 * Flings {@code limb} off {@code form} (server): from the joint, outward from the body and up, tumbling. Returns the chunk,
	 * or null if chunks are off ({@code maxChunks} 0) or it could not be added.
	 */
	public static TitanLimbChunkEntity fling(TitanFormEntity form, int limb, Vec3 at) {
		if (!(form.level() instanceof ServerLevel sl)) {
			return null;
		}
		int max = TitanShifterConfig.limbs().maxChunks;
		if (max <= 0) {
			return null;
		}
		liveCount();
		while (LIVE.size() >= max) {
			TitanLimbChunkEntity old = LIVE.remove(0);
			old.discard();
		}
		TitanLimbChunkEntity c = TitanShifterEntities.TITAN_LIMB_CHUNK.create(sl);
		if (c == null) {
			return null;
		}
		c.entityData.set(DATA_TYPE, form.titanType().ordinal());
		c.entityData.set(DATA_LIMB, limb);
		c.entityData.set(DATA_EMERGENCY, form.isEmergency());
		c.entityData.set(DATA_HEIGHT, form.getBbHeight());
		c.entityData.set(DATA_LIFE, Math.max(STEAM_TICKS + MAX_FLIGHT + 40, TitanShifterConfig.limbs().chunkTicks));
		c.refreshDimensions();
		// the chunk's position is its bottom; the limb is drawn centred on the middle of its box
		c.moveTo(at.x, at.y - c.getBbHeight() * 0.5, at.z, form.getYRot(), 0.0f);
		Vec3 right = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
		boolean arm = limb == TitanFormEntity.LIMB_R_ARM || limb == TitanFormEntity.LIMB_L_ARM;
		boolean onRight = limb == TitanFormEntity.LIMB_R_ARM || limb == TitanFormEntity.LIMB_R_LEG;
		double s = Math.sqrt(form.getBbHeight() / 15.0);
		var r = sl.random;
		Vec3 out = right.scale(onRight ? 1.0 : -1.0).scale((arm ? 0.45 : 0.3) * s);
		Vec3 back = Vec3.directionFromRotation(0, form.getYRot()).scale((r.nextDouble() - 0.6) * 0.25 * s);
		c.setDeltaMovement(out.add(back).add(0, (arm ? 0.35 : 0.5) * s, 0));
		if (!sl.addFreshEntity(c)) {
			return null;
		}
		LIVE.add(c);
		return c;
	}

	public TitanType titanType() {
		return TitanType.byOrdinal(this.entityData.get(DATA_TYPE));
	}

	/** The limb bit ({@link TitanFormEntity#LIMB_R_ARM} ...). */
	public int limb() {
		return this.entityData.get(DATA_LIMB);
	}

	public boolean isArm() {
		return limb() == TitanFormEntity.LIMB_R_ARM || limb() == TitanFormEntity.LIMB_L_ARM;
	}

	public boolean isEmergency() {
		return this.entityData.get(DATA_EMERGENCY);
	}

	/** The height of the Titan it came off. */
	public float titanHeight() {
		return this.entityData.get(DATA_HEIGHT);
	}

	public int life() {
		return this.entityData.get(DATA_LIFE);
	}

	/** The age it landed at, or -1 while it flies. */
	public int landedAt() {
		return this.entityData.get(DATA_LANDED);
	}

	public boolean hasLanded() {
		return landedAt() >= 0;
	}

	/** Ticks since it was cut off, smooth on the client. */
	public float chunkAge(float partialTick) {
		if (level().isClientSide) {
			return clientAge + Math.min(SYNC_EVERY + 2, tickCount - clientAgeAt) + partialTick;
		}
		return age + partialTick;
	}

	/** When the dissolve starts (ticks of age): some steaming time after it landed. */
	public int dissolveStart() {
		int l = landedAt();
		return (l < 0 ? MAX_FLIGHT : l) + STEAM_TICKS;
	}

	/** 0..1 through the dissolve (skin, flesh, bone), 0 before it starts. */
	public float dissolveProgress(float partialTick) {
		float start = dissolveStart();
		float end = Math.max(start + 20, life() - 10);
		return Math.max(0.0f, Math.min(1.0f, (chunkAge(partialTick) - start) / (end - start)));
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		if (this.entityData == null) {
			return super.getDimensions(pose);
		}
		float h = Math.max(1.0f, titanHeight());
		// a limb lying on its side: about an eighth of the Titan thick
		return EntityDimensions.scalable(h * 0.15f, h * 0.13f);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TYPE, 0);
		builder.define(DATA_LIMB, TitanFormEntity.LIMB_R_ARM);
		builder.define(DATA_EMERGENCY, false);
		builder.define(DATA_HEIGHT, 15.0f);
		builder.define(DATA_AGE, 0);
		builder.define(DATA_LANDED, -1);
		builder.define(DATA_LIFE, 360);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_HEIGHT.equals(accessor)) {
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
		if (!(level() instanceof ServerLevel sl)) {
			return;
		}
		age++;
		if (age % SYNC_EVERY == 0) {
			this.entityData.set(DATA_AGE, age);
		}
		Vec3 v = getDeltaMovement().add(0, -0.06, 0);
		setDeltaMovement(v);
		move(MoverType.SELF, v);
		Vec3 m = getDeltaMovement(); // move() zeroes the blocked axes
		if (!hasLanded()) {
			if (onGround() || age >= MAX_FLIGHT || isInWater()) {
				land(sl);
				setDeltaMovement(m.x * 0.15, 0, m.z * 0.15);
			} else {
				setDeltaMovement(m.scale(0.985));
			}
		} else {
			// it slides a little and settles; it still falls if the ground goes
			setDeltaMovement(m.x * 0.6, onGround() ? 0 : m.y * 0.98, m.z * 0.6);
		}
		if (hasLanded()) {
			steam(sl);
		}
		if (age >= life()) {
			sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5, getZ(), 12, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.03);
			discard();
		}
	}

	private void land(ServerLevel sl) {
		this.entityData.set(DATA_LANDED, age);
		float h = titanHeight();
		float power = Math.min(1.5f, h / 12.0f);
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 1.4f * power, 0.4f);
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0f * power, 0.4f);
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SMALL_FALL, SoundSource.HOSTILE, 2.5f * power, 0.4f);
		BlockPos below = BlockPos.containing(getX(), getY() - 0.2, getZ());
		var ground = sl.getBlockState(below);
		double w = getBbWidth() * 1.6;
		if (!ground.isAir()) {
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 40, w * 0.5, 0.1, w * 0.5, 0.15);
		}
		sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 14, w * 0.5, 0.1, w * 0.5, 0.05);
		sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.4, getZ(), 18, w * 0.4, 0.3, w * 0.4, 0.06);
		TitanCombat.shake(sl, position(), 0.35f * power, 8);
	}

	/** Steam boils off it while it lies there, thinning out as it dissolves. */
	private void steam(ServerLevel sl) {
		float d = dissolveProgress(0);
		if (d >= 0.97f) {
			return;
		}
		int every = d <= 0.0f ? 3 : 5;
		if (age % every != 0) {
			return;
		}
		double w = getBbWidth() * 0.9;
		int n = d <= 0.0f ? 4 : Math.max(1, (int) (3 * (1.0f - d)));
		sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.7, getZ(), n, w * 0.5, 0.15, w * 0.5, 0.02);
		if (age % (every * 3) == 0) {
			sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, w * 0.4, 0.1, w * 0.4, 0.01);
		}
		if (age % 40 == 0 && d < 0.5f) {
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 0.5f);
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
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// none: the limb is drawn in its rest pose and turned as a whole by the renderer
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
