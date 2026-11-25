package com.projecthero.mod.shazam.journey;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: <b>the subway train</b> -- two coupled stainless-steel cars (34 blocks), drawn by the client's
 * {@code SubwayTrainRenderer} (cab ends with headlights and a lit destination sign, windows, sliding doors, benches, poles
 * and ceiling lights inside, bogies underneath). Its motion is <b>scripted</b>: both sides work out where it is from the synced
 * mode, its start time and the slot's origin ({@link JourneyGeometry#arriveOffset}, {@link JourneyGeometry#rideOffset}), so
 * a passenger rides perfectly smoothly at 30 blocks a second and no server position update ever fights it.
 *
 * <ul>
 * <li>{@link #ARRIVING}: from deep in the west tunnel, braking to a stop at the platform; the doors open.</li>
 * <li>{@link #STATION}: waiting at the platform, doors open (walk in, or right-click it).</li>
 * <li>{@link #RIDE}: doors close, away down the east tunnel to the cave, doors open.</li>
 * <li>{@link #CAVE}: standing at the end of the line, doors open.</li>
 * </ul>
 * Never saved (a restart ends the journey and the saved return point sends the player home), never hurt, never pushed, no
 * collision -- the long bounding box is only for picking and for render culling.
 */
public class SubwayTrainEntity extends Entity {
	public static final int ARRIVING = 0;
	public static final int STATION = 1;
	public static final int RIDE = 2;
	public static final int CAVE = 3;

	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SubwayTrainEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(SubwayTrainEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<BlockPos> BASE = SynchedEntityData.defineId(SubwayTrainEntity.class, EntityDataSerializers.BLOCK_POS);

	public SubwayTrainEntity(EntityType<? extends SubwayTrainEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(MODE, STATION);
		builder.define(START, 0L);
		builder.define(BASE, BlockPos.ZERO);
	}

	/** Sets the mode, starting now (server). */
	public void setMode(int mode, long start) {
		entityData.set(MODE, mode);
		entityData.set(START, start);
		snap();
	}

	public void setBase(BlockPos origin) {
		entityData.set(BASE, origin);
		snap();
	}

	public int mode() {
		return entityData.get(MODE);
	}

	/** The game time the mode started (may lie in the future: the arrival waits in the tunnel until then). */
	public long start() {
		return entityData.get(START);
	}

	public BlockPos base() {
		return entityData.get(BASE);
	}

	/** Ticks since the mode started (with the partial tick), never negative. */
	public double elapsed(float partialTick) {
		return Math.max(0.0, level().getGameTime() - entityData.get(START) + partialTick);
	}

	/** The centre's x offset from the station stop at mode time {@code t}. */
	public double offsetAt(double t) {
		return switch (mode()) {
			case ARRIVING -> JourneyGeometry.arriveOffset(t);
			case RIDE -> JourneyGeometry.rideOffset(t);
			case CAVE -> JourneyGeometry.rideEnd();
			default -> 0.0;
		};
	}

	/** How open the doors are (0..1) at mode time {@code t}. */
	public double doorsAt(double t) {
		return switch (mode()) {
			case ARRIVING -> JourneyGeometry.arriveDoors(t);
			case RIDE -> JourneyGeometry.rideDoors(t);
			default -> 1.0;
		};
	}

	/** Blocks per tick right now (sounds, the headlight beams). */
	public double speed(float partialTick) {
		double t = elapsed(partialTick);
		return Math.abs(offsetAt(t + 0.5) - offsetAt(t - 0.5));
	}

	public boolean doorsOpen() {
		return doorsAt(elapsed(0f)) >= 0.95;
	}

	private void snap() {
		Vec3 p = JourneyGeometry.track(base(), offsetAt(elapsed(0f)));
		setPos(p.x, p.y, p.z);
	}

	@Override
	public void tick() {
		super.tick();
		if (!base().equals(BlockPos.ZERO)) {
			Vec3 p = JourneyGeometry.track(base(), offsetAt(elapsed(0f)));
			setPos(p.x, p.y, p.z);
		}
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		// scripted: the client works its own position out, so the server's updates are ignored
	}

	@Override
	protected AABB makeBoundingBox() {
		return new AABB(getX() - JourneyGeometry.TRAIN_HALF, getY(), getZ() - JourneyGeometry.CAR_HALF_W, getX() + JourneyGeometry.TRAIN_HALF,
				getY() + JourneyGeometry.CAR_ROOF + 0.2, getZ() + JourneyGeometry.CAR_HALF_W);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
		callback.accept(passenger, getX() + JourneyGeometry.SEAT_X, getY() + JourneyGeometry.SEAT_Y, getZ() + JourneyGeometry.SEAT_Z);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && getPassengers().isEmpty();
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return null;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return passenger.position();
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
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
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && player instanceof ServerPlayer sp) {
			ShazamJourney.boardRequest(sp, this);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
