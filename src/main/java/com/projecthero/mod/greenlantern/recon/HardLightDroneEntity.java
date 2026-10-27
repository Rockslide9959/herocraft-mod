package com.projecthero.mod.greenlantern.recon;

import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Drone -- a small hard-light recon drone that hovers at its Lantern's shoulder. Every bit of
 * behaviour runs server-side in {@link GreenLanternDrone#tickDrone}; the client draws it ({@code HardLightDroneRenderer})
 * from the synced state: the owner, its HP, its mode (follow / fetching / scouting out / hovering / coming back), the
 * last zap (target and tick) and what it is carrying. It can be shot down ({@link GreenLanternReconConfig#DRONE_HP}) and
 * is never saved.
 */
public class HardLightDroneEntity extends Entity {
	public static final int FOLLOW = 0;
	public static final int FETCH = 1;
	public static final int SCOUT_OUT = 2;
	public static final int SCOUT_HOVER = 3;
	public static final int SCOUT_BACK = 4;

	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ZAP_TARGET = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ZAP_TICK = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CARRY_ID = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> SCOUT_SPOT = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Boolean> GOLDEN = SynchedEntityData.defineId(HardLightDroneEntity.class, EntityDataSerializers.BOOLEAN);

	// ---- server-only, run by GreenLanternDrone
	public UUID owner;
	public int modeTicks;
	public int nextZap;
	public int nextScan;
	public int fetchTargetId = -1;
	public Vec3 scoutSpot = Vec3.ZERO;
	public int hurtFlash;

	// ---- client: smooth position interpolation (a plain Entity snaps to each update)
	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYRot;
	private float lerpXRot;

	public HardLightDroneEntity(EntityType<? extends HardLightDroneEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.noCulling = true;
	}

	public static HardLightDroneEntity create(ServerLevel level, ServerPlayer owner, Vec3 at) {
		HardLightDroneEntity d = new HardLightDroneEntity(GreenLanternRecon.DRONE_TYPE, level);
		d.owner = owner.getUUID();
		d.entityData.set(OWNER_ID, owner.getId());
		d.entityData.set(HP, GreenLanternReconConfig.DRONE_HP);
		d.entityData.set(GOLDEN, com.projecthero.mod.greenlantern.GreenLanternRanks.golden(owner));
		d.setPos(at.x, at.y, at.z);
		d.setYRot(owner.getYRot());
		d.xo = at.x;
		d.yo = at.y;
		d.zo = at.z;
		return d;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
		builder.define(HP, GreenLanternReconConfig.DRONE_HP);
		builder.define(MODE, FOLLOW);
		builder.define(ZAP_TARGET, -1);
		builder.define(ZAP_TICK, -100);
		builder.define(CARRY_ID, -1);
		builder.define(SCOUT_SPOT, new Vector3f());
		builder.define(GOLDEN, false);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	public float hp() {
		return entityData.get(HP);
	}

	public void setHp(float hp) {
		entityData.set(HP, Math.max(0f, hp));
	}

	public int mode() {
		return entityData.get(MODE);
	}

	public void setMode(int mode) {
		if (mode() != mode) {
			entityData.set(MODE, mode);
			modeTicks = 0;
		}
	}

	public int zapTarget() {
		return entityData.get(ZAP_TARGET);
	}

	public int zapTick() {
		return entityData.get(ZAP_TICK);
	}

	public void markZap(int targetId) {
		entityData.set(ZAP_TARGET, targetId);
		entityData.set(ZAP_TICK, tickCount);
	}

	public int carryId() {
		return entityData.get(CARRY_ID);
	}

	public void setCarry(int id) {
		entityData.set(CARRY_ID, id);
	}

	public Vec3 scoutSpotSynced() {
		Vector3f v = entityData.get(SCOUT_SPOT);
		return new Vec3(v.x(), v.y(), v.z());
	}

	public void setScoutSpot(Vec3 spot) {
		scoutSpot = spot;
		entityData.set(SCOUT_SPOT, new Vector3f((float) spot.x, (float) spot.y, (float) spot.z));
	}

	public boolean golden() {
		return entityData.get(GOLDEN);
	}

	public boolean scouting() {
		int m = mode();
		return m == SCOUT_OUT || m == SCOUT_HOVER || m == SCOUT_BACK;
	}

	@Override
	public void tick() {
		if (level().isClientSide()) {
			if (lerpSteps > 0) {
				double k = 1.0 / lerpSteps;
				setPos(getX() + (lerpX - getX()) * k, getY() + (lerpY - getY()) * k, getZ() + (lerpZ - getZ()) * k);
				setYRot(getYRot() + net.minecraft.util.Mth.wrapDegrees(lerpYRot - getYRot()) * (float) k);
				setXRot(getXRot() + (lerpXRot - getXRot()) * (float) k);
				lerpSteps--;
			}
		}
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		modeTicks++;
		if (hurtFlash > 0) {
			hurtFlash--;
		}
		GreenLanternDrone.tickDrone(this);
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		this.lerpX = x;
		this.lerpY = y;
		this.lerpZ = z;
		this.lerpYRot = yRot;
		this.lerpXRot = xRot;
		this.lerpSteps = Math.max(1, steps);
	}

	@Override
	public double lerpTargetX() {
		return lerpSteps > 0 ? lerpX : getX();
	}

	@Override
	public double lerpTargetY() {
		return lerpSteps > 0 ? lerpY : getY();
	}

	@Override
	public double lerpTargetZ() {
		return lerpSteps > 0 ? lerpZ : getZ();
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeHitByProjectile() {
		return !isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	/** Hits from anyone but its own Lantern and their squad wear its light down; at 0 it is shot down. */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || isRemoved() || isInvulnerableTo(source) || amount <= 0f) {
			return false;
		}
		return GreenLanternDrone.onHurt(this, source, amount);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96.0 * 96.0;
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
}
