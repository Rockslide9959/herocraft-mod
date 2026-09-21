package com.projecthero.mod.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.diagnostics.TickWatchdog;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.hammer.MjolnirStatus;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFeedback;
import com.projecthero.mod.sound.ProjectHeroSounds;
import com.projecthero.mod.worthiness.Worthiness;
import com.projecthero.mod.worthiness.WorthinessEnforcer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown/flying/dropped Mjolnir.
 *
 * <h2>One authoritative state</h2>
 * Everything the hammer does hangs off a single {@link State}, which is server-authoritative and
 * {@linkplain #DATA_STATE synced} to clients rather than being inferred from a pile of booleans.
 * The whole lifecycle is {@code THROWN -> IMPACT -> RETURNING -> (caught)}, with {@link State#RESTING}
 * as the "lying in the world, waiting" branch.
 *
 * <h2>Why the flight is smooth</h2>
 * The client runs the <em>same</em> simulation the server does and the server barely corrects it.
 * Two things make that possible:
 * <ul>
 *   <li>{@link #DATA_OWNER_ID} carries the owner's entity id over the wire. {@code Projectile}'s own
 *   owner field resolves to {@code null} on the client ({@code findOwner} only looks entities up on a
 *   {@code ServerLevel}), which is what used to make the client bail out of the homing branch
 *   entirely: the returning hammer did not move client-side at all and was dragged along purely by
 *   periodic position packets, which is exactly what the stutter was.</li>
 *   <li>{@link #lerpTo} no longer teleports. Small corrections (the normal case, since both sides
 *   simulate identically) are ignored outright; genuinely large ones are folded in over
 *   {@link #CORRECTION_TICKS} ticks as a decaying offset, so the hammer keeps flying while it
 *   converges instead of snapping backwards.</li>
 * </ul>
 * The entity type's tracking interval is correspondingly relaxed (see {@link ModEntityTypes}):
 * fewer, softer corrections rather than a hard snap every other tick.
 */
public class MjolnirEntity extends ThrowableItemProjectile {
	/**
	 * Outbound speed, in blocks per tick. ~35 blocks/second: quick enough to feel hurled rather than
	 * lobbed, still slow enough to watch it cross a field. Held exactly every tick (see
	 * {@link #tick()}) rather than being a one-off impulse, which -- with gravity off while airborne
	 * -- is what makes the throw a dead-straight line instead of a trident-style arc.
	 */
	private static final double THROW_SPEED = 1.75;
	/** Return flight starts here and accelerates, so a long recall arrives with real momentum. */
	private static final double RETURN_SPEED_MIN = 1.1;
	private static final double RETURN_SPEED_MAX = 2.6;
	private static final double RETURN_ACCELERATION = 0.14;
	/**
	 * Final-approach easing: within this distance the hammer is speed-limited to a fraction of the
	 * remaining gap, so the catch decelerates into the hand instead of the hammer vanishing from
	 * five blocks out at full tilt.
	 */
	private static final double RETURN_EASE_DISTANCE = 4.0;
	private static final double RETURN_EASE_FACTOR = 0.5;

	/** ~62 blocks of outbound travel before it turns around on its own. */
	private static final int MAX_OUTBOUND_TICKS = 36;
	/** How long the hammer stays embedded in what it struck before turning for home. */
	private static final int IMPACT_TICKS = 5;
	private static final double RETURN_CATCH_DISTANCE = 1.1;
	private static final float DAMAGE = 10.0f;
	/** The return trip hits hard too, but not as hard as a committed throw. */
	private static final float RETURN_DAMAGE = 6.0f;

	/** How close a player has to be to collect a resting hammer by walking into it. */
	private static final double PICKUP_RADIUS = 1.0;
	/** Matches vanilla {@code ItemEntity}'s post-drop grace period before an item can be re-collected. */
	private static final int PICKUP_DELAY_TICKS = 40;

	/**
	 * Squared position error below which a server correction is simply ignored. Both sides run the
	 * same simulation, so anything under half a block is latency, not desync -- and applying it would
	 * yank the hammer backwards by roughly one tick of travel on every single update, which reads as
	 * a stutter even though nothing is actually wrong.
	 */
	private static final double CORRECTION_TOLERANCE_SQR = 0.5 * 0.5;
	/** A real correction is blended in over this many ticks rather than teleporting. */
	private static final int CORRECTION_TICKS = 4;

	/** How often the flight whoosh is emitted while airborne. */
	private static final int WHOOSH_INTERVAL_TICKS = 8;


	private static final EntityDataAccessor<Byte> DATA_STATE =
			SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.BYTE);
	/** The owner's entity id, or -1. Present so the client can run the homing simulation too. */
	private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
			SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.INT);

	private static final String TAG_STATE = "ProjectHeroState";
	private static final String TAG_TICKS_ALIVE = "ProjectHeroTicksAlive";
	private static final String TAG_PICKUP_DELAY = "ProjectHeroPickupDelay";
	private static final String TAG_PICKUP_ARMED = "ProjectHeroPickupArmed";
	private static final String TAG_IMPACT_TICKS = "ProjectHeroImpactTicks";

	/**
	 * What the hammer is currently doing -- the single switch the rest of the class reads. Mirrored
	 * into the persistent {@link com.projecthero.mod.hammer.HammerRecord} as a {@link MjolnirStatus} so
	 * an unloaded hammer can still be described accurately.
	 */
	public enum State {
		/** Thrown, flying away from the player. Collides with blocks normally. */
		THROWN,
		/** Just struck something; held in place for a beat before turning for home. */
		IMPACT,
		/** Flying home (impact, throw timeout, or the call keybind). Phases through terrain. */
		RETURNING,
		/** Lying in the world: dropped, ejected, or set down. Waits to be picked up or called. */
		RESTING;

		private static final State[] BY_ID = values();

		static State byId(byte id) {
			return id >= 0 && id < BY_ID.length ? BY_ID[id] : RESTING;
		}
	}

	private int ticksAlive = 0;
	private int impactTicks = 0;
	private int pickupDelay = 0;
	private double returnSpeed = RETURN_SPEED_MIN;

	/** Entities already struck during the current return flight -- see {@link #onHitEntity}. */
	private final Set<UUID> hitDuringReturn = new HashSet<>();

	/** Decaying client-side correction toward the last server position. See {@link #lerpTo}. */
	private Vec3 correction = Vec3.ZERO;
	private int correctionTicks = 0;

	/** Set once the server has given this hammer its identity and told the registry where it is. */
	private boolean registered = false;

	/**
	 * Whether the catch should announce itself. Only a return the player actually <em>asked</em> for
	 * says "Mjolnir returned" -- an ordinary throw comes back every single time, and narrating that
	 * on the action bar would be constant noise rather than feedback.
	 */
	private boolean announceArrival = false;

	/**
	 * Walk-over pickup is disarmed until every player has stepped out of {@link #PICKUP_RADIUS} at
	 * least once. Without this, a hammer dropped with the drop key lands on top of the player who
	 * dropped it and is instantly collected again. Dropping and calling stay two separate deliberate
	 * actions: a dropped hammer stays put until you walk back to it or press the call key.
	 *
	 * <p>Only correct for a hammer that starts at a player's own feet, though -- see
	 * {@link #createResting(Level, Entity, ItemStack, Vec3, Vec3, boolean)}'s {@code armedImmediately}
	 * parameter for the natural-crater case, where nobody has touched it yet and this default would
	 * otherwise strand a player who walks straight up to it and stops.
	 */
	private boolean pickupArmed = true;

	public MjolnirEntity(EntityType<? extends MjolnirEntity> type, Level level) {
		super(type, level);
	}

	public MjolnirEntity(Level level, LivingEntity owner) {
		super(ModEntityTypes.MJOLNIR, owner, level);
		this.setItem(new ItemStack(ModItems.MJOLNIR));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) State.THROWN.ordinal());
		builder.define(DATA_OWNER_ID, -1);
	}

	/**
	 * A hammer lying in the world rather than one actively in flight -- a drop, a worthiness
	 * ejection, or a vanilla {@link ItemEntity} converted by {@code ItemEntityMixin}. Carries over
	 * the real item stack (so the bound owner and the hammer's identity survive) instead of a blank
	 * one, and starts with pickup disarmed so it stays on the ground where it landed.
	 */
	public static MjolnirEntity createResting(Level level, Entity owner, ItemStack stack, Vec3 pos, Vec3 velocity) {
		return createResting(level, owner, stack, pos, velocity, false);
	}

	/**
	 * @param armedImmediately Whether walk-over pickup is live from the very first tick, instead of
	 *                         requiring every player to step out of {@link #PICKUP_RADIUS} once first
	 *                         (see {@link #pickupArmed}'s own javadoc). {@code false} (the 5-arg
	 *                         overload) is correct for anything that lands at a player's own feet --
	 *                         a Q-drop or a worthiness ejection -- so they cannot instantly re-collect
	 *                         what they just let go of. It is the WRONG default for a natural crater's
	 *                         hammer, which has never been near any player: found the hard way via
	 *                         this project's gametest suite ({@code restingHammerIsPickedUpByWorthyPlayer}
	 *                         failed) -- a player who walks straight up to a freshly discovered hammer
	 *                         and stops within {@link #PICKUP_RADIUS} never gets it, because arming
	 *                         requires the radius to have been empty at some point, which it never is
	 *                         if they walked in and stayed. {@link com.projecthero.mod.worldgen.CraterAmbience}
	 *                         passes {@code true} for exactly this reason.
	 */
	public static MjolnirEntity createResting(Level level, Entity owner, ItemStack stack, Vec3 pos, Vec3 velocity,
			boolean armedImmediately) {
		MjolnirEntity entity = new MjolnirEntity(ModEntityTypes.MJOLNIR, level);
		entity.setItem(stack.copy());
		entity.setPos(pos.x, pos.y, pos.z);
		if (owner != null) {
			entity.setOwner(owner);
		}
		// Keeps whatever velocity the caller handed in (a Q-drop's normal forward-toss impulse from
		// vanilla's own drop physics, most notably -- see ItemEntityMixin) rather than the zero
		// settleAsResting() normally settles to, so a dropped hammer arcs forward and down like any
		// other dropped item instead of falling straight from wherever it was promoted.
		entity.settleAsResting(velocity);
		if (armedImmediately) {
			entity.pickupArmed = true;
		}
		return entity;
	}

	/**
	 * A hammer that has just been recalled from somewhere it could not be reached (an unloaded
	 * chunk, another dimension) and is flying in from a distance. See
	 * {@link com.projecthero.mod.hammer.MjolnirRecall}.
	 */
	public static MjolnirEntity createReturning(Level level, Player owner, ItemStack stack, Vec3 pos) {
		MjolnirEntity entity = new MjolnirEntity(ModEntityTypes.MJOLNIR, level);
		entity.setItem(stack.copy());
		entity.setPos(pos.x, pos.y, pos.z);
		entity.setOwner(owner);
		entity.beginReturn();
		entity.announceArrival = true;
		return entity;
	}

	public void throwFromPlayer(Player player, Vec3 direction) {
		Vec3 aim = direction.normalize();
		this.setPos(player.getX() + aim.x, player.getEyeY() - 0.1, player.getZ() + aim.z);
		this.setDeltaMovement(aim.scale(THROW_SPEED));
		this.hasImpulse = true;
		this.ticksAlive = 0;
		setState(State.THROWN);
	}

	// ---------------- state ----------------

	public State getState() {
		// Reachable from the superclass constructor (getDefaultGravity) before the synched data is
		// built; the airborne default is the safe answer there.
		SynchedEntityData data = this.getEntityData();
		return data == null ? State.THROWN : State.byId(data.get(DATA_STATE));
	}

	private void setState(State state) {
		if (level().isClientSide()) {
			// Server-authoritative: clients read the synced value and never write it. A client that
			// locally decides "that was a block hit" would otherwise fight the incoming sync.
			return;
		}
		if (getState() != state) {
			this.entityData.set(DATA_STATE, (byte) state.ordinal());
			noteToRegistry();
		}
	}

	/** Maps the entity's state onto the value stored in the persistent record. */
	private MjolnirStatus statusForRecord() {
		return switch (getState()) {
			case THROWN -> MjolnirStatus.THROWN;
			case IMPACT -> MjolnirStatus.IMPACT;
			case RETURNING -> MjolnirStatus.RETURNING;
			case RESTING -> MjolnirStatus.RESTING;
		};
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		if (!level().isClientSide()) {
			this.entityData.set(DATA_OWNER_ID, owner == null ? -1 : owner.getId());
		}
	}

	/**
	 * The owner, resolved on either side. On the server this is {@code Projectile}'s own field; on
	 * the client it comes from {@link #DATA_OWNER_ID}, because {@code Projectile.findOwner} only
	 * looks entities up on a {@code ServerLevel} and therefore always answers {@code null} there.
	 */
	private Player resolveOwner() {
		Entity owner = this.getOwner();
		if (owner instanceof Player player) {
			return player;
		}
		int id = this.entityData.get(DATA_OWNER_ID);
		if (id >= 0 && level().getEntity(id) instanceof Player player) {
			return player;
		}
		return null;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.MJOLNIR;
	}

	/**
	 * Zero while the hammer is airborne: Thor's hammer flies in a straight line, it does not arc like
	 * a thrown trident. Gravity comes back only once it has {@linkplain State#RESTING settled}, so a
	 * dropped/ejected hammer still falls to the floor instead of hanging in mid-air -- a little
	 * stronger than a vanilla dropped item's own gravity (~0.04) so it reads as heavy rather than
	 * floaty on the way down, without needing any bounce (there isn't one: {@link #onHitBlock} stops
	 * it dead on contact) to sell the weight.
	 */
	@Override
	protected double getDefaultGravity() {
		return getState() == State.RESTING ? 0.055 : 0.0;
	}

	// ---------------- client/server position sync ----------------

	/**
	 * Folds a server position update into the local simulation instead of teleporting to it.
	 *
	 * <p>Vanilla's {@code Entity.lerpTo} is a hard {@code setPos}, and the packet it comes from
	 * describes where the hammer was when the packet was <em>sent</em>. For a fast projectile that
	 * means every update yanks it a tick's worth of travel backwards -- the visible stutter. Since
	 * both sides run the same simulation here, a small disagreement is latency and is discarded; a
	 * large one is a real desync and is blended in over a few ticks as an offset applied on top of
	 * ordinary movement, so the hammer never stops or reverses while it converges.
	 *
	 * <p>Rotation is deliberately ignored for a flying hammer: it is derived from the velocity
	 * locally every tick and is far smoother than the byte-quantised angle on the wire.
	 */
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		if (getState() == State.RESTING) {
			// Nothing to simulate -- take the server's word for it, rotation included.
			super.lerpTo(x, y, z, yRot, xRot, steps);
			return;
		}

		double errorSqr = this.distanceToSqr(x, y, z);
		if (errorSqr < CORRECTION_TOLERANCE_SQR) {
			this.correction = Vec3.ZERO;
			this.correctionTicks = 0;
			return;
		}

		this.correction = new Vec3(x, y, z).subtract(this.position());
		this.correctionTicks = CORRECTION_TICKS;
	}

	private void applyPendingCorrection() {
		if (correctionTicks <= 0) {
			return;
		}
		Vec3 step = correction.scale(1.0 / correctionTicks);
		this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
		this.correction = correction.subtract(step);
		this.correctionTicks--;
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
		// Order matters: the staleness check has to run *before* registration. Registering brings the
		// stack's generation up to the record's, which for a ghost hammer -- the original, whose
		// chunk has just loaded after a recall already reconstructed it elsewhere -- would quietly
		// promote it back to valid and leave two Mjolnirs in the world.
		if (!level().isClientSide()) {
			if (discardIfStale()) {
				return;
			}
			if (!registered) {
				registerWithRegistry();
			}
		}

		if (pickupDelay > 0) {
			pickupDelay--;
		}

		applyPendingCorrection();

		State state = getState();

		if (state == State.RESTING) {
			// See TickWatchdog's javadoc -- a permanent, silent-unless-anomalous timing guard around
			// exactly the tick path a "the world sometimes completely freezes" report named (a
			// natural, unclaimed hammer's own per-tick work), not temporary debug spam.
			TickWatchdog.run("MjolnirEntity.tickResting", this::tickResting);
			return;
		}

		switch (state) {
			case IMPACT -> {
				tickImpact();
				return;
			}
			case RETURNING -> {
				if (!tickReturning()) {
					return;
				}
			}
			case THROWN -> tickThrown();
			default -> {
			}
		}

		// super.tick() calls updateRotation() itself, which reads the velocity we just set above and
		// eases the model's heading 20% of the way toward it. Doing it here as well would double that
		// rate and lose the smoothing; the important part is only that the velocity is final before
		// the call. It runs on both sides, so the renderer always has a locally-derived heading
		// rather than the byte-quantised one off the wire.
		super.tick();

		ticksAlive++;
		if (getState() == State.THROWN && ticksAlive > MAX_OUTBOUND_TICKS) {
			beginReturn();
		}

		spawnFlightEffects();
	}

	private void tickResting() {
		// Lying in the world: just fall/settle normally (onHitBlock stops it dead on contact instead
		// of letting it sink through the floor), no homing or timeout logic.
		//
		// The rotation is held across the tick because vanilla's updateRotation() derives facing from
		// velocity, and a stopped hammer's velocity is zero -- which would keep snapping its yaw to a
		// fixed compass direction as it settles. MjolnirEntityRenderer stands a resting hammer
		// upright and uses only that yaw, so holding it steady keeps the hammer pointing the way it
		// was travelling when it landed.
		float restingYRot = this.getYRot();
		float restingXRot = this.getXRot();

		super.tick();

		this.setYRot(restingYRot);
		this.setXRot(restingXRot);
		this.yRotO = restingYRot;
		this.xRotO = restingXRot;

		if (!level().isClientSide()) {
			updatePickupArming();
			// No walk-over pickup any more ("changes 14"): a resting Mjolnir is collected only by
			// right-clicking it (see #interact), so it doesn't silently jump into your inventory as
			// you pass by. tryPickup() still exists for that interaction path.
		}
	}

	private void tickImpact() {
		this.setDeltaMovement(Vec3.ZERO);

		// Projectile.updateRotation() (called inside super.tick(), unconditionally, every tick) eases
		// yaw/pitch 20% of the way toward atan2(0, 0) = (0, 0) whenever velocity is zero -- it has no
		// "don't touch rotation if not moving" guard. Left alone, that would make the hammer visibly
		// drift back toward a level, forward-facing angle during the impact pause instead of staying
		// stuck at the angle it struck, undoing the whole point of a freeze. Same fix as
		// tickResting() below: hold the angle across the super.tick() call.
		float frozenYRot = this.getYRot();
		float frozenXRot = this.getXRot();

		super.tick();

		this.setYRot(frozenYRot);
		this.setXRot(frozenXRot);
		this.yRotO = frozenYRot;
		this.xRotO = frozenXRot;

		if (!level().isClientSide() && ++impactTicks >= IMPACT_TICKS) {
			beginReturn();
		}
	}

	/** @return true if the flight should continue this tick, false if the hammer is done. */
	private boolean tickReturning() {
		Player owner = resolveOwner();
		if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
			// The owner logged out, died, or changed dimension mid-flight. Settle where we are; the
			// persistent record keeps the hammer callable, so nothing is lost.
			if (!level().isClientSide()) {
				settleAsResting();
			}
			return false;
		}

		Vec3 target = catchTarget(owner);
		Vec3 toTarget = target.subtract(this.position());
		double distance = toTarget.length();

		if (distance < RETURN_CATCH_DISTANCE) {
			if (!level().isClientSide()) {
				catchBy(owner);
			}
			return false;
		}

		returnSpeed = Math.min(RETURN_SPEED_MAX, returnSpeed + RETURN_ACCELERATION);
		double speed = returnSpeed;
		if (distance < RETURN_EASE_DISTANCE) {
			// Ease the last few blocks so the catch decelerates into the hand.
			speed = Math.min(speed, Math.max(RETURN_SPEED_MIN * 0.6, distance * RETURN_EASE_FACTOR));
		}

		// Head straight for the target. This used to steer the heading toward the target by a
		// bounded turn per tick instead of re-aiming dead-on every tick -- meant to read as smooth
		// curved flight, but in practice (especially recalling a hammer that was flying off at an
		// angle, or lying somewhere off to the side) it produced a wide, looping arc before the
		// hammer even pointed at the player, which read as wrong, not smooth. Direct homing is what
		// "just fly back to me" actually looks like.
		this.setDeltaMovement(toTarget.scale(speed / distance));
		return true;
	}

	private void tickThrown() {
		// ThrowableProjectile.tick() bleeds 1% of the velocity away every tick as air drag. Left
		// alone that decelerates the throw over its life; re-asserting the speed along the current
		// heading each tick keeps it constant. Direction is read back off the velocity rather than
		// stored, because with gravity off nothing else ever changes it.
		Vec3 velocity = this.getDeltaMovement();
		if (velocity.lengthSqr() > 1.0E-6) {
			this.setDeltaMovement(velocity.scale(THROW_SPEED / velocity.length()));
		}
	}

	/**
	 * Where the hammer aims for on the way home: the owner's <em>hand</em>, not their feet or their
	 * eyes. Computed from position and look angle only, both of which the client also has for a
	 * remote player, so the two sides agree on the target and the flight path stays identical.
	 */
	private Vec3 catchTarget(Player owner) {
		Vec3 look = owner.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		if (forward.lengthSqr() < 1.0E-6) {
			forward = new Vec3(0.0, 0.0, 1.0);
		} else {
			forward = forward.normalize();
		}
		// Perpendicular in the horizontal plane, on the side the main hand is on.
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double sideSign = owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0;
		return owner.position()
				.add(0.0, owner.getBbHeight() * 0.72, 0.0)
				.add(side.scale(0.45 * sideSign))
				.add(forward.scale(0.25));
	}

	// ---------------- transitions ----------------

	/** Turn for home. Idempotent, and safe to call from recall, impact, or the outbound timeout. */
	public void beginReturn() {
		if (getState() == State.RETURNING) {
			return;
		}
		setState(State.RETURNING);
		this.ticksAlive = 0;
		this.impactTicks = 0;
		this.returnSpeed = RETURN_SPEED_MIN;
		this.hitDuringReturn.clear();
		this.hasImpulse = true;
	}

	/**
	 * Called by the "call hammer" keybind (via {@link com.projecthero.mod.hammer.MjolnirRecall}) to
	 * recall an in-flight or resting hammer.
	 *
	 * <p>The summoner becomes the entity's owner, which is what the returning branch homes toward --
	 * without this, a hammer that reached the ground with no owner (a death drop, which vanilla
	 * spawns with no thrower) would answer the call and then immediately settle again, having
	 * nobody to fly to.
	 */
	public void recall(Player summoner) {
		this.setOwner(summoner);
		beginReturn();
		this.announceArrival = true;
		if (level() instanceof ServerLevel serverLevel) {
			// A rising whoosh with electric energy -- see ProjectHeroSounds' javadoc for the placeholder
			// .ogg this will pick up automatically once it exists; the vanilla layer alongside it is
			// what makes the cue audible today. Deliberately not TRIDENT_RETURN (used here and at the
			// catch below before this pass) -- same "don't sound like a trident" ask that covers throw.
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					ProjectHeroSounds.MJOLNIR_RECALL, SoundSource.PLAYERS, 0.8f, 1.0f);
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.5f, 1.3f);
		}
	}

	// ---------------- collisions ----------------

	@Override
	protected boolean canHitEntity(Entity target) {
		if (!super.canHitEntity(target)) {
			return false;
		}
		if (getState() == State.RESTING || getState() == State.IMPACT) {
			return false;
		}
		Player owner = resolveOwner();
		if (owner != null && target == owner) {
			// Mjolnir never strikes the hand that threw it, outbound or on the way back.
			return false;
		}
		// One hit per entity per return trip: without this the hammer, which is travelling toward the
		// player and can pass straight through a mob, re-hits it every tick it overlaps.
		return getState() != State.RETURNING || !hitDuringReturn.contains(target.getUUID());
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);

		State state = getState();
		if (state == State.RESTING || state == State.IMPACT) {
			// A hammer lying on the ground is scenery, not a weapon.
			return;
		}

		Entity target = result.getEntity();
		Player owner = resolveOwner();
		if (target == owner) {
			return;
		}

		if (!level().isClientSide()) {
			boolean returning = state == State.RETURNING;
			float damage = returning ? RETURN_DAMAGE : DAMAGE;
			var damageSource = level().damageSources().trident(this, owner == null ? this : owner);
			if (target.hurt(damageSource, damage)) {
				if (target instanceof LivingEntity living) {
					living.knockback(returning ? 0.4 : 0.8, this.getX() - target.getX(), this.getZ() - target.getZ());
				}
				level().playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0f, 1.0f);
			}
			if (returning) {
				hitDuringReturn.add(target.getUUID());
			}
		}

		if (state == State.THROWN) {
			// A landed hit sends it home rather than letting it sail on.
			beginImpact();
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		State state = getState();

		if (state == State.RETURNING) {
			// The only case that phases through terrain: a hammer flying home to its owner is
			// unstoppable. Doing nothing here means the block hit is ignored and the return flight
			// continues unimpeded.
			//
			// It also has to stay a no-op rather than "stop and re-home": ThrowableProjectile.tick()
			// re-reads deltaMovement *after* onHitBlock, so zeroing the velocity while the homing
			// vector keeps re-aiming into the same wall re-triggers every tick and it never unsticks.
			return;
		}

		if (state == State.IMPACT) {
			return;
		}

		this.setDeltaMovement(Vec3.ZERO);

		if (state == State.THROWN) {
			if (!level().isClientSide()) {
				level().playSound(null, this.blockPosition(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0f, 0.8f);
				if (level() instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
							12, 0.2, 0.2, 0.2, 0.08);
				}
			}
			beginImpact();
		}
	}

	/**
	 * A struck hammer pauses in place for a beat and then flies home by itself -- no second keypress
	 * needed. If it has nobody to fly home to it settles instead, so an ownerless hammer does not
	 * hang in the air forever.
	 */
	private void beginImpact() {
		if (level().isClientSide()) {
			return;
		}
		if (resolveOwner() == null) {
			settleAsResting();
			return;
		}
		this.impactTicks = 0;
		setState(State.IMPACT);
	}

	// ---------------- pickup / catch ----------------

	/**
	 * Re-arms pickup once nobody is standing on the hammer any more. Combined with {@link #pickupDelay}
	 * this is what makes a dropped Mjolnir stay dropped for its grace period. Pickup itself is a
	 * right-click now (see {@link #interact}), not walk-over.
	 */
	private void updatePickupArming() {
		if (pickupArmed) {
			return;
		}
		if (level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(PICKUP_RADIUS)).isEmpty()) {
			pickupArmed = true;
		}
	}

	/** Resting Mjolnir can be crosshair-targeted so it can be right-clicked to pick up ("changes 14"). */
	@Override
	public boolean isPickable() {
		return !isRemoved() && getState() == State.RESTING;
	}

	/**
	 * Right-click to pick a resting Mjolnir up off the ground ("changes 14" -- it no longer collects
	 * itself as a worthy player walks past). Worthiness gates the pickup; ownership does not -- a
	 * worthy player who is not the bound owner can still physically take it and wield its abilities
	 * (see {@link com.projecthero.mod.power.ThorPassives}'s class javadoc). Unworthy players get the
	 * usual throttled "it will not budge" feedback.
	 */
	@Override
	public net.minecraft.world.InteractionResult interact(Player player, InteractionHand hand) {
		if (level().isClientSide()) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		if (getState() != State.RESTING || pickupDelay > 0) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (WorthinessEnforcer.bypassesWorthiness(player) || Worthiness.isWorthy(player)) {
			if (giveTo(player)) {
				level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
				this.discard();
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		WorthinessEnforcer.playRejectionFeedback(player, this.position());
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	private void catchBy(Player player) {
		if (!giveTo(player)) {
			// Inventory full: the hammer still comes home, it just waits at the owner's feet rather
			// than being deleted. It stays a MjolnirEntity, so it is still callable.
			ThorFeedback.recallInventoryFull(player);
			this.setPos(player.getX(), player.getY(), player.getZ());
			settleAsResting();
			return;
		}

		// A solid metallic catch/thud -- distinct from the recall's rising whoosh above, and again not
		// TRIDENT_RETURN. MACE_SMASH_GROUND is a genuinely heavy metal-weapon-impact sample (added
		// alongside vanilla's own Mace), which is a much closer match for "hammer lands in hand" than
		// anything trident-flavoured.
		level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		level().playSound(null, player.blockPosition(), ProjectHeroSounds.MJOLNIR_CATCH, SoundSource.PLAYERS, 0.8f, 1.2f);
		level().playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.6f, 1.3f);
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY(), player.getZ(),
					10, 0.3, 0.3, 0.3, 0.02);
		}
		if (announceArrival) {
			ThorFeedback.recallArrived(player);
		}
		this.discard();
	}

	/**
	 * Puts the hammer where a returning Mjolnir should go, in order of preference: the empty main
	 * hand, then the next free inventory slot -- v0.11.12, explicit user request: the off hand is no
	 * longer a landing spot at all, so a returning Mjolnir never bumps whatever the player deliberately
	 * has equipped there. Carries over the entity's actual held stack (not a fresh plain one) so the
	 * bound owner, the hammer id and the generation all survive the round trip.
	 *
	 * @return false if there was nowhere to put it -- the caller must not delete the hammer.
	 */
	private boolean giveTo(Player player) {
		ItemStack stack = this.getItem().copy();
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			return false;
		}
		if (level() instanceof ServerLevel serverLevel) {
			MjolnirRegistry.get(serverLevel).noteCarried(stack, player, true);
		}
		return true;
	}

	/**
	 * A lost/dropped Mjolnir always remains a tracked {@code MjolnirEntity} in the world rather than
	 * reverting to a plain {@link ItemEntity} -- used when a throw is orphaned, when a return trip
	 * cannot complete (owner gone, or their inventory is full), and by {@link #createResting}. Keeps
	 * this same entity instance alive rather than discarding and respawning a new one.
	 */
	private void settleAsResting() {
		settleAsResting(Vec3.ZERO);
	}

	/**
	 * @param velocity carried into the settle rather than zeroed -- see {@link #createResting}, the
	 *                 only caller that ever passes something other than {@link Vec3#ZERO}. Every
	 *                 internal "just stop where you are" call site (an abandoned return flight, an
	 *                 inventory-full catch, an ownerless throw's impact) still goes through the
	 *                 zero-argument overload above, unchanged.
	 */
	private void settleAsResting(Vec3 velocity) {
		setState(State.RESTING);
		this.ticksAlive = 0;
		this.impactTicks = 0;
		this.pickupDelay = PICKUP_DELAY_TICKS;
		this.pickupArmed = false;
		this.hitDuringReturn.clear();
		this.setDeltaMovement(velocity);
	}

	// ---------------- registry bookkeeping ----------------

	/**
	 * Gives the hammer its identity and tells the persistent registry where it is, once, on the
	 * first server tick. Everything after that is event-driven ({@link #setState}, removal), so the
	 * registry is never written on a per-tick basis.
	 */
	private void registerWithRegistry() {
		registered = true;
		noteToRegistry();
	}

	/**
	 * Both registry calls can write components back onto the stack (its id, its generation), so they
	 * run against a working copy which is then stored through {@link #setItem} -- writing through
	 * {@code getItem()} in place would mutate the synched value without ever marking it dirty, and
	 * the client would never learn the hammer's identity.
	 */
	private void noteToRegistry() {
		if (!registered || !(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		ItemStack working = this.getItem().copy();
		MjolnirRegistry registry = MjolnirRegistry.get(serverLevel);
		registry.identify(working);
		registry.noteEntity(working, this, statusForRecord());
		this.setItem(working);
	}

	/**
	 * Deletes this hammer if a newer generation of it exists -- i.e. if it is the original that a
	 * recall already reconstructed elsewhere while this chunk was unloaded. This is the other half of
	 * the duplication guarantee described on {@link MjolnirRegistry}: without it, loading the old
	 * chunk would put a second, identical Mjolnir back into the world.
	 */
	private boolean discardIfStale() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (!MjolnirRegistry.get(serverLevel).isStale(this.getItem())) {
			return false;
		}
		this.discard();
		return true;
	}

	// Note: there is deliberately no chunk-unload hook. The record is written on the hammer's first
	// tick and again on every state change, and RESTING -- the state a hammer is in for essentially
	// all of the time it spends sitting in a chunk that later unloads -- does not move. What recall
	// actually needs from the record (the owner, the dimension, the entity UUID) is fixed by then;
	// the stored position is informational only.

	// ---------------- presentation ----------------

	private void spawnFlightEffects() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		// Sparse on purpose: a trail, not a smoke screen -- the hammer has to stay easy to follow.
		if (this.tickCount % 3 == 0) {
			Vec3 behind = this.position().subtract(this.getDeltaMovement().scale(0.5));
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, behind.x, behind.y, behind.z, 1, 0.05, 0.05, 0.05, 0.0);
		}

		// The whirl, heard rather than seen. Emitted at the hammer, so vanilla's own distance
		// attenuation does the "getting closer" work for free -- no manual volume ramp needed.
		if (this.tickCount % WHOOSH_INTERVAL_TICKS == 0) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35f, 1.5f);
		}
	}

	// ---------------- persistence ----------------

	// Without these, a hammer resting on the ground came back from a world reload as a freshly
	// thrown projectile: it would time out and fly at whoever last threw it, entirely unprompted.
	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString(TAG_STATE, getState().name());
		tag.putInt(TAG_TICKS_ALIVE, ticksAlive);
		tag.putInt(TAG_IMPACT_TICKS, impactTicks);
		tag.putInt(TAG_PICKUP_DELAY, pickupDelay);
		tag.putBoolean(TAG_PICKUP_ARMED, pickupArmed);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_STATE, (byte) readState(tag).ordinal());
		ticksAlive = tag.getInt(TAG_TICKS_ALIVE);
		impactTicks = tag.getInt(TAG_IMPACT_TICKS);
		pickupDelay = tag.getInt(TAG_PICKUP_DELAY);
		// Default true (rather than false) so a hammer saved mid-flight does not come back
		// permanently uncollectable if nobody ever steps away from it.
		pickupArmed = !tag.contains(TAG_PICKUP_ARMED) || tag.getBoolean(TAG_PICKUP_ARMED);
	}

	/**
	 * A hammer that was mid-flight when the server stopped has no owner to fly to on the way back up
	 * (the {@code Projectile} owner is a UUID that may belong to an offline player), so it is
	 * recovered into the one state that is safe to sit in indefinitely rather than resuming a flight
	 * with nothing to home toward. The persistent record keeps it callable, so nothing is lost.
	 */
	private static State readState(CompoundTag tag) {
		// Every state collapses to RESTING on load, deliberately -- see the javadoc above. The tag is
		// still written by addAdditionalSaveData so a save file can be inspected/debugged, and so a
		// future state that genuinely should survive a restart has somewhere to be read back from.
		return State.RESTING;
	}
}
