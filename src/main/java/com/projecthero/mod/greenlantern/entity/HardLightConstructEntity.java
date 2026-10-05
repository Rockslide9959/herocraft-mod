package com.projecthero.mod.greenlantern.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.3: one piece of Green Lantern hard light that is not made of blocks. The {@link Shape} (synced) decides both
 * how it is drawn ({@code HardLightConstructRenderer}) and what it does; every bit of gameplay runs server-side in
 * {@code GreenLanternConstructAttacks#tickEntity}, so a client only ever sees the result. Never saved: a construct is
 * willpower, and does not outlast the session, a chunk unload or its owner leaving.
 *
 * <p>Synced: the shape, a scale, an end point (bolts / beams draw from the entity to it), a target entity id (chains
 * wrap it, the hand holds it), a life in ticks (0 = until dismissed; the model fades over its last few ticks) and an
 * "action" tick (when the warrior last swung, when the pad last fired, when the hand closed) for one-shot animations.
 */
public class HardLightConstructEntity extends Entity {
	public enum Shape {
		BOLT, BEAM, FIST, HAMMER, MISSILE, BUZZSAW, ANVIL, HAND, CHAINS, LAUNCH_PAD, WARRIOR,
		/** v0.14.22: the Rescue Tether's hard-light bubble round whatever is being carried (scale = radius). */
		BUBBLE;

		public static Shape byId(int id) {
			Shape[] all = values();
			return id >= 0 && id < all.length ? all[id] : BOLT;
		}
	}

	private static final EntityDataAccessor<Integer> SHAPE =
			SynchedEntityData.defineId(HardLightConstructEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SCALE =
			SynchedEntityData.defineId(HardLightConstructEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Vector3f> END =
			SynchedEntityData.defineId(HardLightConstructEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> TARGET =
			SynchedEntityData.defineId(HardLightConstructEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LIFE =
			SynchedEntityData.defineId(HardLightConstructEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION =
			SynchedEntityData.defineId(HardLightConstructEntity.class, EntityDataSerializers.INT);

	// ---- server-only state, read and written by GreenLanternConstructAttacks ----
	public UUID owner;
	public float damage;
	public Vec3 velocity = Vec3.ZERO;
	public Vec3 origin = Vec3.ZERO;
	public double travelled;
	public int bounces;
	public int phase;
	public int homingId = -1;
	public int nextHitTick;
	public boolean returning;
	public final Set<Integer> hit = new HashSet<>();

	public HardLightConstructEntity(EntityType<? extends HardLightConstructEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.noCulling = true;
	}

	public static HardLightConstructEntity create(ServerLevel level, Shape shape, UUID owner, Vec3 pos, float scale, int life) {
		HardLightConstructEntity e = new HardLightConstructEntity(GreenLanternEntityTypes.HARD_LIGHT_CONSTRUCT, level);
		e.owner = owner;
		e.origin = pos;
		e.setPos(pos.x, pos.y, pos.z);
		e.entityData.set(SHAPE, shape.ordinal());
		e.entityData.set(SCALE, scale);
		e.entityData.set(LIFE, life);
		e.setEnd(pos);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SHAPE, 0);
		builder.define(SCALE, 1.0f);
		builder.define(END, new Vector3f());
		builder.define(TARGET, -1);
		builder.define(LIFE, 0);
		builder.define(ACTION, -100);
	}

	public Shape shape() {
		return Shape.byId(this.entityData.get(SHAPE));
	}

	public float scale() {
		return this.entityData.get(SCALE);
	}

	public Vec3 end() {
		Vector3f v = this.entityData.get(END);
		return new Vec3(v.x(), v.y(), v.z());
	}

	public void setEnd(Vec3 end) {
		this.entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
	}

	public int targetId() {
		return this.entityData.get(TARGET);
	}

	public void setTargetId(int id) {
		this.entityData.set(TARGET, id);
	}

	/** Total life in ticks, 0 = lives until its owner's code discards it. */
	public int life() {
		return this.entityData.get(LIFE);
	}

	public void setLife(int life) {
		this.entityData.set(LIFE, life);
	}

	/** {@link #tickCount} of the last one-shot action (a swing, a launch, the hand closing). */
	public int actionTick() {
		return this.entityData.get(ACTION);
	}

	public void markAction() {
		this.entityData.set(ACTION, this.tickCount);
	}

	/** 1 while alive, easing to 0 over the last 5 ticks of a timed life (the renderer's fade). */
	public float fade(float partial) {
		int life = life();
		if (life <= 0) {
			return 1.0f;
		}
		float left = life - (this.tickCount + partial);
		return Math.max(0.0f, Math.min(1.0f, left / 5.0f));
	}

	/** Point the model along {@code dir} (yaw / pitch in the vanilla convention the renderer undoes). */
	public void face(Vec3 dir) {
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}
		double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		setYRot((float) (Math.toDegrees(Math.atan2(-dir.x, dir.z))));
		setXRot((float) (-Math.toDegrees(Math.atan2(dir.y, h))));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		int life = life();
		if (life > 0 && this.tickCount >= life) {
			discard();
			return;
		}
		com.projecthero.mod.greenlantern.GreenLanternConstructAttacks.tickEntity(this);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	public boolean isPickable() {
		return false;
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
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
