package com.projecthero.mod.hero.revamp.batcha;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Flight rework: one burst of air drawn as a model ({@code client.revamp.batcha.FlightFxRenderer}) -- the Sonic
 * Boom's cone of shock rings, the Dive Bomb's crater with its expanding shock ring and thrown-up debris, the Takeoff
 * Blast's ground ring and column, the Updraft's twisting wind column, a punch's impact ring, the vapour ring a dash leaves
 * behind. Its kind, size and the ground block (for the crater's debris) are synced; its yaw / pitch point the effect.
 * It lives a second or two, never saved, no collision.
 */
public class FlightFxEntity extends Entity {
	public static final int SONIC_CONE = 0;
	public static final int CRATER = 1;
	public static final int TAKEOFF = 2;
	public static final int UPDRAFT = 3;
	public static final int PUNCH = 4;
	public static final int VAPOR_RING = 5;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FlightFxEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(FlightFxEntity.class,
			EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(FlightFxEntity.class,
			EntityDataSerializers.BLOCK_STATE);

	public FlightFxEntity(EntityType<? extends FlightFxEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** How long each kind lives, ticks. */
	public static int life(int kind) {
		return switch (kind) {
			case SONIC_CONE -> 16;
			case CRATER -> 70;
			case TAKEOFF -> 30;
			case UPDRAFT -> 34;
			case PUNCH -> 10;
			default -> 12;
		};
	}

	/** Spawns a burst at {@code at} pointing along {@code dir}. */
	public static FlightFxEntity spawn(ServerLevel level, int kind, Vec3 at, Vec3 dir, float radius, BlockState block) {
		FlightFxEntity e = new FlightFxEntity(BatchAEntities.FLIGHT_FX, level);
		e.entityData.set(KIND, kind);
		e.entityData.set(RADIUS, radius);
		if (block != null && !block.isAir()) {
			e.entityData.set(BLOCK, block);
		}
		Vec3 d = dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : dir.normalize();
		float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
		float pitch = (float) (-Math.asin(Mth.clamp(d.y, -1.0, 1.0)) * Mth.RAD_TO_DEG);
		e.moveTo(at.x, at.y, at.z, yaw, pitch);
		level.addFreshEntity(e);
		return e;
	}

	public int kind() {
		return this.entityData.get(KIND);
	}

	public float radius() {
		return this.entityData.get(RADIUS);
	}

	public BlockState block() {
		return this.entityData.get(BLOCK);
	}

	/** The direction the burst points (from the synced yaw / pitch). */
	public Vec3 direction() {
		return Vec3.directionFromRotation(getXRot(), getYRot());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, VAPOR_RING);
		builder.define(RADIUS, 1.0f);
		builder.define(BLOCK, Blocks.STONE.defaultBlockState());
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.tickCount > life(kind())) {
			this.discard();
		}
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
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
