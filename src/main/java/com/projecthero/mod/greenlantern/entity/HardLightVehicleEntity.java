package com.projecthero.mod.greenlantern.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternFlight;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: a rideable hard-light vehicle the Carry Platform turns into -- the {@link HardLightCarEntity Car} or the
 * {@link HardLightJetEntity Jet} (Carry Platform key: Platform -> Car -> Jet -> Platform, see
 * {@code GreenLanternVehicles}). Shared rules:
 * <ul>
 *   <li><b>Client-simulated</b>, like a vanilla boat or horse: the controlling passenger's client runs {@link #simulate} in
 *   {@link #travel} and sends the result (ServerboundMoveVehiclePacket); the server accepts it and only simulates the
 *   vehicle itself while nobody drives (parked: it rolls / sinks to a stop). The velocity lives in {@link #vel}, not the
 *   delta movement, because the client damps a non-AI mob's delta movement every tick.</li>
 *   <li>Anyone may get in (right-click) while a seat is free; the first player in drives. <b>Shift alone does not get you
 *   out</b> -- it is the Shift+R modifier -- hold it {@link GreenLanternConfig#VEHICLE_DISMOUNT_HOLD_TICKS} ticks
 *   (without pressing R) to climb out ({@code PlayerMixin#wantsToStopRiding}); v0.17.1 round 2: counted near the ground,
 *   since a Jet's Shift descends -- in the air the hold is {@link #airExitHoldTicks} ({@link #passengerWantsOut}).</li>
 *   <li>v0.17.1 round 2: unridden and told to follow ({@link #setFollowTarget}), it makes its own way after its owner.</li>
 *   <li>It has hit points and takes damage from anything but its own riders and owner; at 0 the construct dissolves.</li>
 *   <li>Never saved ({@code noSave} type) and never changes dimension; a vehicle no live construct owns removes itself.</li>
 * </ul>
 */
public abstract class HardLightVehicleEntity extends Mob {
	private static final EntityDataAccessor<Long> BOOST_UNTIL = SynchedEntityData.defineId(HardLightVehicleEntity.class,
			EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Boolean> GOLDEN = SynchedEntityData.defineId(HardLightVehicleEntity.class,
			EntityDataSerializers.BOOLEAN);

	/** Set by {@code GreenLanternVehicles}: is a live construct behind this entity? (A stray copy removes itself.) */
	public static java.util.function.Predicate<HardLightVehicleEntity> tracked = e -> true;

	private UUID owner;
	/** The simulated velocity (blocks/tick) -- see the class javadoc. */
	protected Vec3 vel = Vec3.ZERO;
	/** Controlling client: how far the vehicle turned this tick, handed on to its riders' view (boat-style). */
	protected float yawDelta;
	/** Which passenger the simulation last ran for (a new driver starts from rest). */
	private int lastController = -1;
	// server: Shift-hold bookkeeping per rider
	private final Map<UUID, Integer> shiftHeld = new HashMap<>();
	/** The part of each rider's Shift hold spent near the ground (a Jet's Shift descends first, then gets you out). */
	private final Map<UUID, Integer> shiftHeldGround = new HashMap<>();
	/** Set while the construct reshapes it: riders leaving then are re-seated at once, no Slow Falling cushion. */
	public boolean reshaping;
	private final Set<UUID> shiftUsed = new HashSet<>();
	private final Map<UUID, Integer> boardedAt = new HashMap<>();
	/** Client: the smoothed steering / bank value the renderer reads. */
	public float visualTurn;
	public float visualTurnO;
	/** Client: wheel spin (car) / exhaust pulse (jet). */
	public float spin;
	public float spinO;

	protected HardLightVehicleEntity(EntityType<? extends HardLightVehicleEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		noCulling = true;
	}

	protected static AttributeSupplier.Builder vehicleAttributes(double hp, double step) {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, hp)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.STEP_HEIGHT, step)
				.add(Attributes.SAFE_FALL_DISTANCE, 1000.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BOOST_UNTIL, 0L);
		builder.define(GOLDEN, false);
	}

	/** Seats, the driver included. */
	public abstract int seats();

	/** Vehicle-local seat point (x left, y up, z forward) for passenger {@code index}. */
	protected abstract Vec3 seat(int index);

	/**
	 * v0.17.1 round 2: the driver's controls for one tick -- A/D ({@code strafe}, + = left), W/S ({@code forward}), Space
	 * ({@code up}), Sneak ({@code down}) and where they look ({@code yaw} / {@code pitch}).
	 */
	public record Controls(float strafe, float forward, boolean up, boolean down, float yaw, float pitch) {
	}

	/**
	 * Runs the vehicle's physics for one tick. {@code in} is null while nobody drives: it is parked (rolls / settles to a
	 * stop) or, with {@link #followTarget} set, makes its own way after its owner.
	 */
	protected abstract void simulate(Controls in);

	/**
	 * v0.17.1 round 2: the owner it is following (the Carry Platform key, not looking at it), or null to stay parked. Set
	 * every tick by {@code GreenLanternVehicles#tick}; only used while nobody rides it (the server simulates it then).
	 */
	protected Entity followTarget;

	public void setFollowTarget(Entity target) {
		followTarget = target;
	}

	public boolean following() {
		return followTarget != null;
	}

	/** Gametests: one tick of the physics with these controls, as the driver's client would run it. */
	public void stepForTest(Controls in) {
		yawDelta = 0f;
		simulate(in);
	}

	/** Whether there is ground (or anything solid) within a block under it -- the server's landed check. */
	public boolean nearGround() {
		return onGround() || !level().noCollision(this, getBoundingBox().move(0, -1.0, 0));
	}

	/** Getting out while it is airborne needs Shift held this long (a Jet's Shift descends first). */
	protected int airExitHoldTicks() {
		return GreenLanternConfig.VEHICLE_DISMOUNT_HOLD_TICKS;
	}

	/** The action-bar help line a passenger gets on boarding (driver / passenger). */
	protected abstract String controlsKey(boolean driver);

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	public UUID owner() {
		return owner;
	}

	public void setGolden(boolean golden) {
		entityData.set(GOLDEN, golden);
	}

	public boolean golden() {
		return entityData.get(GOLDEN);
	}

	/** Shift+R: horn / afterburner -- {@code ticks} of extra thrust, read by the driver's client. */
	public void boost(int ticks) {
		entityData.set(BOOST_UNTIL, level().getGameTime() + ticks);
	}

	public boolean boosting() {
		return level().getGameTime() < entityData.get(BOOST_UNTIL);
	}

	/** Blocks/second it is really moving (the simulated velocity, or the last move on a remote client / the server). */
	public double speedBps() {
		Vec3 d = isControlledByLocalInstance() ? vel : position().subtract(xo, yo, zo);
		return d.length() * 20.0;
	}

	// ---------------------------------------------------------------- mob plumbing

	@Override
	protected void registerGoals() {
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
	protected void pushEntities() {
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	protected boolean shouldDropLoot() {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.CRAMMING)) {
			return false;
		}
		Entity by = source.getEntity();
		if (by != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& (hasPassenger(by) || by.getUUID().equals(owner))) {
			return false; // its own riders' and owner's fire never chips it
		}
		return super.hurt(source, amount);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive() || player.isPassenger() || getPassengers().size() >= seats()) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide()) {
			player.startRiding(this, true);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && getPassengers().size() < seats();
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!level().isClientSide() && passenger instanceof ServerPlayer sp) {
			GreenLanternFlight.forceStop(sp, false); // sat down: Ring Flight would otherwise keep draining
			boardedAt.put(sp.getUUID(), tickCount);
		}
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player p ? p : null;
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		return new Vec3(player.xxa, 0, player.zza);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return 0.3f;
	}

	@Override
	public void travel(Vec3 input) {
		if (!isControlledByLocalInstance()) {
			calculateEntityAnimation(false);
			return;
		}
		LivingEntity c = getControllingPassenger();
		Player pilot = c instanceof Player p ? p : null;
		int id = pilot == null ? -1 : pilot.getId();
		if (id != lastController) {
			lastController = id;
			vel = onGround() || pilot == null ? Vec3.ZERO : vel;
			onNewController(pilot);
		}
		yawDelta = 0f;
		simulate(pilot == null ? null : new Controls((float) input.x, (float) input.z,
				((com.projecthero.mod.mixin.LivingEntityAccessor) pilot).projecthero$isJumping(), pilot.isShiftKeyDown(),
				pilot.getYRot(), pilot.getXRot()));
		calculateEntityAnimation(false);
	}

	/** A new driver took over (or the last one left) on this side. */
	protected void onNewController(Player pilot) {
	}

	/** Moves by {@link #vel}, keeping what the collisions leave of it. */
	protected void moveByVel() {
		setDeltaMovement(vel);
		move(net.minecraft.world.entity.MoverType.SELF, vel);
		vel = getDeltaMovement();
		resetFallDistance();
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
		super.positionRider(passenger, callback);
		if (yawDelta != 0f && level().isClientSide()) {
			// boat-style: the driver's view turns with the vehicle
			passenger.setYRot(passenger.getYRot() + yawDelta);
			passenger.setYHeadRot(passenger.getYHeadRot() + yawDelta);
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int i = Math.max(0, getPassengers().indexOf(passenger));
		// vehicle-local, but an override is not rotated for us (the v0.15.21 Hulk-seat lesson)
		return seat(i).xRot(-getXRot() * Mth.DEG_TO_RAD).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	/** A vehicle-local point (x left, y up, z forward) in the world, at the current yaw / pitch. */
	public Vec3 local(double x, double y, double z) {
		return position().add(new Vec3(x, y, z).xRot(-getXRot() * Mth.DEG_TO_RAD).yRot(-getYRot() * Mth.DEG_TO_RAD));
	}

	// ---------------------------------------------------------------- getting out

	/**
	 * {@code PlayerMixin#wantsToStopRiding}: Shift held long enough, and not used for Shift+R in the meantime. Near the
	 * ground that is {@link GreenLanternConfig#VEHICLE_DISMOUNT_HOLD_TICKS} of it there; in the air it is
	 * {@link #airExitHoldTicks} (a Jet's Shift descends: hold it to land, keep holding to climb out).
	 */
	public boolean passengerWantsOut(Player p) {
		if (!p.isShiftKeyDown() || shiftUsed.contains(p.getUUID())) {
			return false;
		}
		if (nearGround()) {
			return shiftHeldGround.getOrDefault(p.getUUID(), 0) >= GreenLanternConfig.VEHICLE_DISMOUNT_HOLD_TICKS;
		}
		return shiftHeld.getOrDefault(p.getUUID(), 0) >= airExitHoldTicks();
	}

	/** Shift+R was used while Shift is down: this Shift hold no longer counts toward getting out. */
	public void noteShiftUsed(Player p) {
		shiftUsed.add(p.getUUID());
	}

	@Override
	public void tick() {
		visualTurnO = visualTurn;
		spinO = spin;
		super.tick();
		if (level().isClientSide()) {
			clientTick();
			return;
		}
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (tickCount % 20 == 0 && !tracked.test(this)) {
			discard(); // nothing owns it any more (its construct ended without it)
			return;
		}
		List<Entity> riders = getPassengers();
		for (Entity r : riders) {
			r.resetFallDistance();
			if (!(r instanceof ServerPlayer sp)) {
				continue;
			}
			UUID id = sp.getUUID();
			if (sp.isShiftKeyDown()) {
				shiftHeld.merge(id, 1, Integer::sum);
				if (nearGround()) {
					shiftHeldGround.merge(id, 1, Integer::sum);
				} else {
					shiftHeldGround.remove(id);
				}
			} else {
				shiftHeld.remove(id);
				shiftHeldGround.remove(id);
				shiftUsed.remove(id);
			}
			Integer at = boardedAt.get(id);
			if (at != null && tickCount - at >= 4) {
				// after vanilla's "press Shift to dismount" line, which would be wrong here
				boardedAt.remove(id);
				// v0.17.1 (user): the driver's controls are a permanent panel now (client HardLightVehicleHud); passengers
				// still get the one-line note
				if (getControllingPassenger() != sp) {
					sp.displayClientMessage(Component.translatable(controlsKey(false)), true);
				}
			}
		}
		if (riders.isEmpty() && !shiftHeld.isEmpty()) {
			shiftHeld.clear();
			shiftHeldGround.clear();
			shiftUsed.clear();
		}
		serverTick(server);
	}

	protected void serverTick(ServerLevel server) {
	}

	protected void clientTick() {
	}

	/**
	 * The construct ended (N, out of charge, destroyed, cycled to another form): out go the riders (a mid-air drop is
	 * cushioned for anyone without a ring), and it is gone. The green-spark dissolve is drawn by the caller.
	 */
	public void dissolve() {
		for (Entity r : List.copyOf(getPassengers())) {
			r.stopRiding(); // removePassenger cushions a mid-air drop (unless it is being reshaped)
		}
		discard();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!level().isClientSide() && !reshaping && passenger instanceof LivingEntity le && !nearGround()) {
			// v0.17.1 round 2: out in mid-air (a held-Shift bail-out, N, the ring running dry): never a fatal drop
			le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false, true));
		}
		shiftHeld.remove(passenger.getUUID());
		shiftHeldGround.remove(passenger.getUUID());
		shiftUsed.remove(passenger.getUUID());
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
	}
}
