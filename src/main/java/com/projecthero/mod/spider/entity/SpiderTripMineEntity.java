package com.projecthero.mod.spider.entity;

import java.util.UUID;

import com.projecthero.mod.spider.SpiderTripMines;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a Spider-Man Suit v2 Trip Mine (Shift+C) -- a small web-shooter disc stuck to a block face with a blinking
 * light, projecting a thin web laser straight out from the surface (up to {@link SpiderTripMines#BEAM_RANGE} blocks, or
 * to the first block). The first creature or enemy player to cross the laser is yanked onto the mine and cocooned there;
 * the mine is spent. All the rules live in {@link SpiderTripMines}; this entity holds the mine's state.
 *
 * <p>Synced: the face it is stuck to, the laser's length and the creature being reeled in (so the renderer draws the
 * line to it). Saved: the owner, the face and its age, so a mine outlives a relog until its ten minutes are up.
 */
public class SpiderTripMineEntity extends Entity {
	public static final float SIZE = 0.5f;

	private static final EntityDataAccessor<Direction> FACE =
			SynchedEntityData.defineId(SpiderTripMineEntity.class, EntityDataSerializers.DIRECTION);
	private static final EntityDataAccessor<Float> BEAM =
			SynchedEntityData.defineId(SpiderTripMineEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> REEL_TARGET =
			SynchedEntityData.defineId(SpiderTripMineEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	/** Ticks since it was placed (saved, so the 10-minute life is real time loaded, not since the last load). */
	public int age;
	/** While a caught creature is reeled in: ticks since the catch. */
	public int reelTicks;

	public SpiderTripMineEntity(EntityType<? extends SpiderTripMineEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FACE, Direction.UP);
		builder.define(BEAM, 0.0f);
		builder.define(REEL_TARGET, -1);
	}

	public Direction face() {
		return entityData.get(FACE);
	}

	public void setFace(Direction face) {
		entityData.set(FACE, face);
	}

	public float beamLength() {
		return entityData.get(BEAM);
	}

	public void setBeamLength(float length) {
		entityData.set(BEAM, length);
	}

	/** The entity id of the creature being reeled in, or -1. */
	public int reelTarget() {
		return entityData.get(REEL_TARGET);
	}

	public void setReelTarget(int id) {
		entityData.set(REEL_TARGET, id);
	}

	public UUID owner() {
		return owner;
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	/** Place the mine's disc on the face point {@code at} (on the block surface), facing out along {@code face}. */
	public void place(Vec3 at, Direction face) {
		setFace(face);
		Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
		// the (upright) box is centred on the disc for a wall, sits on it for a floor, hangs from it for a ceiling
		Vec3 pos = switch (face) {
			case UP -> at;
			case DOWN -> at.subtract(0, SIZE, 0);
			default -> at.add(n.scale(SIZE * 0.5)).subtract(0, SIZE * 0.5, 0);
		};
		setPos(pos);
	}

	/** The centre of the disc, on the block surface. */
	public Vec3 anchor() {
		Direction face = face();
		Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
		Vec3 p = position();
		return switch (face) {
			case UP -> p;
			case DOWN -> p.add(0, SIZE, 0);
			default -> p.add(0, SIZE * 0.5, 0).subtract(n.scale(SIZE * 0.5));
		};
	}

	/** Where the laser leaves the disc (its lens, a little out from the surface). */
	public Vec3 beamStart() {
		return anchor().add(Vec3.atLowerCornerOf(face().getNormal()).scale(0.12));
	}

	public Vec3 beamEnd() {
		return beamStart().add(Vec3.atLowerCornerOf(face().getNormal()).scale(beamLength()));
	}

	/** Re-measure the laser: straight out to {@link SpiderTripMines#BEAM_RANGE} blocks or the first block in the way. */
	public float measureBeam() {
		Vec3 start = beamStart();
		Vec3 far = start.add(Vec3.atLowerCornerOf(face().getNormal()).scale(SpiderTripMines.BEAM_RANGE));
		var hit = level().clip(new ClipContext(start, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return (float) hit.getLocation().distanceTo(start);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level) {
			SpiderTripMines.tick(level, this);
		}
	}

	@Override
	public boolean isPickable() {
		return true; // sneak + right-click picks it up, a hit knocks it off
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			return SpiderTripMines.pickUp(sp, this) ? InteractionResult.CONSUME : InteractionResult.PASS;
		}
		return owner != null && owner.equals(player.getUUID()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isInvulnerableTo(source) || level().isClientSide() || isRemoved()) {
			return false;
		}
		// knocked off its surface by any hit from a player (or an explosion)
		if (source.getEntity() instanceof Player || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			SpiderTripMines.breakMine((ServerLevel) level(), this);
			return true;
		}
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && reason.shouldDestroy()) {
			SpiderTripMines.forget(this);
		}
		super.remove(reason);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		// the laser too, so it is drawn while the disc itself is off screen
		return getBoundingBox().minmax(new AABB(beamStart(), beamEnd())).inflate(0.2);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			owner = tag.getUUID("Owner");
		}
		setFace(Direction.from3DDataValue(tag.getInt("Face")));
		age = tag.getInt("Age");
		setBeamLength(tag.getFloat("Beam"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		tag.putInt("Face", face().get3DDataValue());
		tag.putInt("Age", age);
		tag.putFloat("Beam", beamLength());
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}
}
