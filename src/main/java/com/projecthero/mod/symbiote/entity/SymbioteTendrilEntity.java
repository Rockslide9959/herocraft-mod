package com.projecthero.mod.symbiote.entity;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: a living tendril -- the visual for every Symbiote tendril move (Tendril Strike, Sweep, Barrage,
 * Grab, Grapple and the Onslaught ultimate), drawn by {@code SymbioteTendrilRenderer} as a tapering, writhing
 * black tube that lashes out, holds, and snaps back. Purely cosmetic: every hit is resolved by the ability code
 * the moment it fires; this only has to look right, for everyone (it is a tracked entity, so multiplayer
 * viewers see it too).
 *
 * <p>It starts either at a hand of its owner (re-read every frame on the client, so it stays attached while
 * the host moves) or at its own position ({@link #ANCHOR_FIXED} -- ground tendrils), and ends on its target
 * entity if that is still around, otherwise on its fixed end point. Never saved.
 */
public class SymbioteTendrilEntity extends Entity {
	public static final int ANCHOR_RIGHT_HAND = 0;
	public static final int ANCHOR_LEFT_HAND = 1;
	public static final int ANCHOR_FIXED = 2;

	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANCHOR = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> DATA_END = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_EXTEND = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(SymbioteTendrilEntity.class, EntityDataSerializers.FLOAT);

	public SymbioteTendrilEntity(EntityType<? extends SymbioteTendrilEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/**
	 * Lash a tendril out of a host's hand.
	 *
	 * @param target entity the tip should stay on (may be null); {@code end} is used once it is gone
	 * @param life total ticks on screen, {@code extend} of them spent reaching out
	 */
	public static SymbioteTendrilEntity fromHand(Player owner, boolean rightHand, Vec3 end, Entity target,
			int life, int extend, float width) {
		Vec3 start = com.projecthero.mod.symbiote.SymbioteHands.hand(owner, rightHand);
		SymbioteTendrilEntity t = create(owner.level(), start, end, target, life, extend, width);
		if (t != null) {
			t.entityData.set(DATA_OWNER, owner.getId());
			t.entityData.set(DATA_ANCHOR, rightHand ? ANCHOR_RIGHT_HAND : ANCHOR_LEFT_HAND);
			owner.level().addFreshEntity(t);
		}
		return t;
	}

	/** A tendril rooted at a fixed point (bursting out of the ground). */
	public static SymbioteTendrilEntity fromPoint(Level level, Vec3 start, Vec3 end, Entity target, int life, int extend,
			float width) {
		SymbioteTendrilEntity t = create(level, start, end, target, life, extend, width);
		if (t != null) {
			t.entityData.set(DATA_ANCHOR, ANCHOR_FIXED);
			level.addFreshEntity(t);
		}
		return t;
	}

	private static SymbioteTendrilEntity create(Level level, Vec3 start, Vec3 end, Entity target, int life, int extend,
			float width) {
		if (!(level instanceof ServerLevel)) {
			return null;
		}
		SymbioteTendrilEntity t = SymbioteEntityTypes.TENDRIL.create(level);
		if (t == null) {
			return null;
		}
		t.moveTo(start.x, start.y, start.z, 0.0f, 0.0f);
		t.entityData.set(DATA_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
		t.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
		t.entityData.set(DATA_LIFE, Math.max(2, life));
		t.entityData.set(DATA_EXTEND, Math.max(1, Math.min(extend, life - 1)));
		t.entityData.set(DATA_WIDTH, width);
		return t;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER, -1);
		builder.define(DATA_ANCHOR, ANCHOR_FIXED);
		builder.define(DATA_TARGET, -1);
		builder.define(DATA_END, new Vector3f());
		builder.define(DATA_LIFE, 10);
		builder.define(DATA_EXTEND, 3);
		builder.define(DATA_WIDTH, 0.12f);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && tickCount >= life()) {
			discard();
		}
	}

	/** End the tendril early (the grab was released, the pull finished). */
	public void retract() {
		if (!level().isClientSide && isAlive()) {
			discard();
		}
	}

	public int ownerId() {
		return entityData.get(DATA_OWNER);
	}

	public int anchor() {
		return entityData.get(DATA_ANCHOR);
	}

	public int targetId() {
		return entityData.get(DATA_TARGET);
	}

	public Vec3 end() {
		Vector3f v = entityData.get(DATA_END);
		return new Vec3(v.x(), v.y(), v.z());
	}

	public int life() {
		return entityData.get(DATA_LIFE);
	}

	public int extendTicks() {
		return entityData.get(DATA_EXTEND);
	}

	public float width() {
		return entityData.get(DATA_WIDTH);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
