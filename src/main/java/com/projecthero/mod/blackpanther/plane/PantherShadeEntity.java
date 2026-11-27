package com.projecthero.mod.blackpanther.plane;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>a panther shade</b> -- the ghostly black panthers of the Trial of the Shades
 * ({@link ShadeTrial}), and (with {@link #spirit} set, many times the size) <b>Bast's spirit</b> for the finale. The vanilla
 * ocelot model in a dark translucent hide with glowing violet eyes (gold for Bast), not a custom body. Moved entirely by the
 * plane's story ({@link AncestralPlane}); {@link #phase} is synced so the client can crouch it for the tell and flare its eyes.
 * One hit from the player it hunts strikes it down; it can't hurt anyone (its pounce only marks a wound in the trial).
 */
public class PantherShadeEntity extends PathfinderMob {
	public static final int APPEAR = 0, STALK = 1, WINDUP = 2, POUNCE = 3, RETREAT = 4;

	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(PantherShadeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> SPIRIT = SynchedEntityData.defineId(PantherShadeEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(PantherShadeEntity.class, EntityDataSerializers.FLOAT);

	/** Server-side trial state (the story drives these). */
	int phaseStart;
	Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
	double orbit;
	boolean struck;

	public PantherShadeEntity(EntityType<? extends PantherShadeEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PHASE, APPEAR);
		builder.define(SPIRIT, false);
		builder.define(SIZE, 1.0f);
	}

	public int phase() {
		return entityData.get(PHASE);
	}

	void setPhase(int phase, int now) {
		entityData.set(PHASE, phase);
		phaseStart = now;
		setPose(phase == WINDUP ? Pose.CROUCHING : Pose.STANDING);
		setSprinting(phase == POUNCE || phase == RETREAT);
	}

	public boolean spirit() {
		return entityData.get(SPIRIT);
	}

	public float size() {
		return entityData.get(SIZE);
	}

	/** Makes this Bast's spirit, {@code size} times a shade. */
	public void makeSpirit(float size) {
		entityData.set(SPIRIT, true);
		entityData.set(SIZE, size);
		refreshDimensions();
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(size());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (SIZE.equals(key)) {
			refreshDimensions();
		}
	}

	/** Faces {@code target} (body and head). */
	void face(Vec3 target) {
		float yaw = PlaneGeometry.lookYaw(position(), target);
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
	}

	@Override
	public void travel(Vec3 input) {
		calculateEntityAnimation(false);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || spirit() || struck || isRemoved()) {
			return false;
		}
		Entity by = source.getEntity();
		if (by instanceof ServerPlayer sp && AncestralPlane.shadeStruck(sp, this)) {
			struck = true;
			return true;
		}
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
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !spirit();
	}
}
