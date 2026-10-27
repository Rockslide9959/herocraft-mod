package com.projecthero.mod.greenlantern.recon;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: one door of a Hard-Light Gateway -- a tall frame of hard light round a swirling green surface (drawn by
 * {@code HardLightGatewayRenderer}). The entity sits at the bottom centre of the opening; its front faces {@link #faceYaw()}.
 *
 * <p>States (synced, with the game time each began so every client animates in step):
 * <ul>
 *   <li>{@link #TRACE}: the bearer is channelling -- the frame's outline draws itself out of light over
 *   {@link GreenLanternReconConfig#GATEWAY_CHANNEL_TICKS}. Nothing can pass yet.</li>
 *   <li>{@link #OPEN}: it unfolds from the centre out over {@link GreenLanternReconConfig#GATEWAY_UNFOLD_TICKS}, then the
 *   Lantern and their squad walk through to its partner ({@link GreenLanternGateway#pass}).</li>
 *   <li>{@link #CLOSING}: it folds back into the centre and is gone.</li>
 * </ul>
 * Both doors keep their chunk loaded while open. Never saved; the live ones are owned by {@link GreenLanternGateway}.
 */
public class HardLightGatewayEntity extends Entity {
	public static final int TRACE = 0;
	public static final int OPEN = 1;
	public static final int CLOSING = 2;

	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HardLightGatewayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> FACE_YAW = SynchedEntityData.defineId(HardLightGatewayEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(HardLightGatewayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> STATE_AT = SynchedEntityData.defineId(HardLightGatewayEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Boolean> GOLDEN = SynchedEntityData.defineId(HardLightGatewayEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FAR_END = SynchedEntityData.defineId(HardLightGatewayEntity.class, EntityDataSerializers.BOOLEAN);

	// ---- server-only
	public UUID owner;
	public HardLightGatewayEntity partner;
	public long closeAt = Long.MAX_VALUE;

	public HardLightGatewayEntity(EntityType<? extends HardLightGatewayEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.noCulling = true;
	}

	public static HardLightGatewayEntity create(ServerLevel level, ServerPlayer owner, Vec3 feet, float faceYaw, int state, boolean farEnd) {
		HardLightGatewayEntity g = new HardLightGatewayEntity(GreenLanternRecon.GATEWAY_TYPE, level);
		g.owner = owner.getUUID();
		g.entityData.set(OWNER_ID, owner.getId());
		g.entityData.set(FACE_YAW, faceYaw);
		g.entityData.set(GOLDEN, com.projecthero.mod.greenlantern.GreenLanternRanks.golden(owner));
		g.entityData.set(FAR_END, farEnd);
		g.setPos(feet.x, feet.y, feet.z);
		g.setYRot(faceYaw);
		g.setState(state, level.getGameTime());
		return g;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
		builder.define(FACE_YAW, 0f);
		builder.define(STATE, TRACE);
		builder.define(STATE_AT, 0L);
		builder.define(GOLDEN, false);
		builder.define(FAR_END, false);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	public float faceYaw() {
		return entityData.get(FACE_YAW);
	}

	public int state() {
		return entityData.get(STATE);
	}

	public long stateAt() {
		return entityData.get(STATE_AT);
	}

	public boolean golden() {
		return entityData.get(GOLDEN);
	}

	/** The door at the waypoint (the one that formed far away). */
	public boolean farEnd() {
		return entityData.get(FAR_END);
	}

	public void setState(int state, long now) {
		entityData.set(STATE, state);
		entityData.set(STATE_AT, now);
	}

	/** Ticks (with {@code partial}) since the current state began, by world time. */
	public float stateAge(float partial) {
		return Math.max(0f, level().getGameTime() - stateAt() + partial);
	}

	/** The way the door faces (unit, horizontal): out of its front. */
	public Vec3 normal() {
		float r = faceYaw() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	/** Along the door's width, to its right as you look at its front. */
	public Vec3 right() {
		float r = faceYaw() * Mth.DEG_TO_RAD;
		return new Vec3(Mth.cos(r), 0, Mth.sin(r));
	}

	/** 0..1: how unfolded the door is (0 while tracing, rising when it opens, falling as it closes). */
	public float openness(float partial) {
		float k = stateAge(partial) / GreenLanternReconConfig.GATEWAY_UNFOLD_TICKS;
		return switch (state()) {
			case OPEN -> Mth.clamp(k, 0f, 1f);
			case CLOSING -> Mth.clamp(1f - k, 0f, 1f);
			default -> 0f;
		};
	}

	/** Open and fully unfolded: travellers may pass. */
	public boolean passable() {
		return state() == OPEN && stateAge(0f) >= GreenLanternReconConfig.GATEWAY_UNFOLD_TICKS;
	}

	/** Starts folding away (once). */
	public void close() {
		if (state() != CLOSING && !level().isClientSide()) {
			if (state() == TRACE) {
				discard();
				return;
			}
			setState(CLOSING, level().getGameTime());
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		ServerLevel sl = (ServerLevel) level();
		long now = sl.getGameTime();
		int st = state();
		if (st == CLOSING) {
			if (now - stateAt() >= GreenLanternReconConfig.GATEWAY_UNFOLD_TICKS) {
				discard();
			}
			return;
		}
		if (st == OPEN) {
			if (tickCount == 1 || (now - stateAt()) % 100 == 0) {
				BlockPos at = blockPosition();
				sl.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(at), 2, at);
			}
			if (now >= closeAt || partner == null || partner.isRemoved()) {
				close();
				if (partner != null && !partner.isRemoved()) {
					partner.close();
				}
				return;
			}
			if ((now - stateAt()) % 40 == 0) {
				GreenLanternDrone.sound(sl, position().add(0, 1.4, 0), net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, 0.6f, 1.3f);
			}
			if (passable()) {
				GreenLanternGateway.passThrough(this, sl);
			}
		}
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return new AABB(position(), position()).inflate(1.8, 0, 1.8).expandTowards(0, 3.6, 0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128.0 * 128.0;
	}

	@Override
	public boolean isPickable() {
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
