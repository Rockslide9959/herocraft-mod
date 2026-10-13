package com.projecthero.mod.galactus.entity;

import java.util.UUID;

import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;
import com.projecthero.mod.galactus.GalactusEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: what every unit of Galactus: The Devourer shares -- Galactus himself, his weak points and grabbing hand, the
 * Silver Surfer, the Element Converters, the Power Pylons and the Punishers.
 * <ul>
 *   <li><b>The event link</b>: a unit made by a {@link GalactusEvent} remembers it (saved) and removes itself within two
 *       seconds if that event no longer exists -- the orphan guard for a crash or a dropped record.</li>
 *   <li><b>Animation</b>: one synced int, the playing {@link Clip}'s index in {@link GalactusAnims#ALL} (low 10 bits) and a
 *       bump counter above it, so the same move twice still restarts. The client's GeckoLib "main" controller blends into
 *       it for {@link GalactusAnims#TRANSITION} ticks; with no clip it plays {@link #idleClip()} / {@link #moveClip()}.</li>
 *   <li>Never despawns, never pushed, immune to fall damage and drowning; nothing ever sets a server glowing flag (the
 *       private-highlight rule).</li>
 * </ul>
 */
public abstract class GalactusMob extends Monster implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(GalactusMob.class, EntityDataSerializers.INT);

	private UUID eventId;
	private int animSeq;
	protected int animTicksLeft;

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private Clip clientClip;
	private int clientClipStart;
	private int mainClipStart = Integer.MIN_VALUE;

	protected GalactusMob(EntityType<? extends GalactusMob> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		this.xpReward = 0;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ANIM, 0x3FF);
	}

	@Override
	protected void registerGoals() {
	}

	// ---------------------------------------------------------------- the event link

	public UUID eventId() {
		return eventId;
	}

	public void bindToEvent(UUID id) {
		this.eventId = id;
	}

	public GalactusEvent event() {
		return eventId != null && level() instanceof ServerLevel server ? GalactusEvent.find(server, eventId) : null;
	}

	// ---------------------------------------------------------------- animation

	/** Asset base name ({@link GalactusAnims#MODELS}), or null for no model. */
	public abstract String model();

	protected Clip idleClip() {
		return null;
	}

	/** The locomotion clip while moving (null: idle). */
	protected Clip moveClip() {
		return null;
	}

	/** Server: play {@code clip} (held {@code clip.length()} ticks, then back to locomotion; null: locomotion now). */
	public void setAnim(Clip clip) {
		animSeq = (animSeq + 1) & 0x3FFFFF;
		int idx = GalactusAnims.indexOf(clip);
		entityData.set(DATA_ANIM, (idx < 0 ? 0x3FF : idx) | (animSeq << 10));
		animTicksLeft = clip == null || clip.loops() ? 0 : clip.length();
	}

	public Clip currentClip() {
		return GalactusAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
	}

	public Clip clientClip() {
		return clientClip;
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
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	private static final java.util.Map<Clip, RawAnimation> RAW = new java.util.HashMap<>();

	public static RawAnimation raw(Clip clip) {
		synchronized (RAW) {
			return RAW.computeIfAbsent(clip, c -> c.loops() ? RawAnimation.begin().thenLoop(c.id()) : RawAnimation.begin().thenPlayAndHold(c.id()));
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		if (model() == null) {
			return;
		}
		controllers.add(new AnimationController<>(this, "main", GalactusAnims.TRANSITION, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<GalactusMob> state) {
		Clip clip = clientClip;
		if (clip != null) {
			if (clientClipStart != mainClipStart) {
				mainClipStart = clientClipStart;
				state.getController().forceAnimationReset();
			}
			return state.setAndContinue(raw(clip));
		}
		mainClipStart = Integer.MIN_VALUE;
		double dx = getX() - xo;
		double dz = getZ() - zo;
		double dy = getY() - yo;
		Clip move = moveClip();
		if (move != null && dx * dx + dz * dz + dy * dy * 0.25 > 4.0e-4) {
			return state.setAndContinue(raw(move));
		}
		Clip idle = idleClip();
		return idle == null ? PlayState.STOP : state.setAndContinue(raw(idle));
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (animTicksLeft > 0 && --animTicksLeft == 0) {
				Clip c = currentClip();
				if (c != null && !c.holds()) {
					setAnim(null);
				}
			}
			if (tickCount % 40 == 0 && eventId != null && GalactusEvent.find(server, eventId) == null) {
				discard(); // the orphan guard
			}
		}
	}

	/** Turns the body and head to face {@code at} (at most {@code maxStep} degrees this call). */
	protected void faceTowards(Vec3 at, float maxStep) {
		double dx = at.x - getX();
		double dz = at.z - getZ();
		float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		float yaw = Mth.approachDegrees(getYRot(), want, maxStep);
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
		yRotO = yaw;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		if (com.projecthero.mod.galactus.GalactusCombat.isCosmic(this) && com.projecthero.mod.galactus.GalactusCombat.isCosmic(other)) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (eventId != null) {
			tag.putUUID("GalactusEvent", eventId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		eventId = tag.hasUUID("GalactusEvent") ? tag.getUUID("GalactusEvent") : null;
	}
}
