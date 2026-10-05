package com.projecthero.mod.flight;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.kryptonian.KryptonianConfig;

import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: the maths behind the mod-wide <b>directional flight</b> (explicit user request -- "make all flights in the mod
 * the new directional flight system", "player should be able to fly backwards with S ... should work the same as green
 * lantern flight"). Pure functions, no client classes, so the gametests can check them; the client controller
 * ({@code client.flight.DirectionalFlight}) feeds them the local player's keys and look and moves the player.
 *
 * <p>Every flight works the same way, the Green Lantern Ring Flight way (v0.13.21):
 * <ul>
 *   <li><b>W / S</b> fly forward / <b>backward</b> along the full 3D look vector -- look up or down to climb or dive;</li>
 *   <li><b>A / D</b> slide sideways on the horizontal plane;</li>
 *   <li><b>Space / Sneak</b> rise and sink straight up and down on top of that;</li>
 *   <li><b>no input</b> eases to a dead hover -- no gravity, no drift.</li>
 * </ul>
 * Each flight keeps its own numbers through a {@link Tune}: its cruise speed (already including any sprint / boost / speed
 * tier), vertical speed, how quickly it gets up to speed, how hard it turns around, how fast it settles into a hover, an
 * optional horizontal speed ceiling and an optional forced forward drive (supersonic bursts).
 */
public final class DirectionalFlightModel {
	/**
	 * Vanilla creative flight's terminal step per tick is {@code flyingSpeed / (1 - 0.91)}: each tick adds
	 * {@code flyingSpeed} along the input and air drag keeps 91% of the rest. Flights that used to ride vanilla's creative
	 * flight cruise at exactly that speed now, so none of them got faster or slower.
	 */
	public static final double VANILLA_CRUISE_FACTOR = 1.0 / 0.09;
	/**
	 * Vanilla creative flight's terminal vertical step: {@code flyingSpeed x 3} added a tick by Space/Sneak, 60% of the
	 * vertical velocity kept a tick, so the player settles at {@code 7.5 x flyingSpeed} a tick.
	 */
	public static final double VANILLA_VERTICAL_FACTOR = 7.5;
	/** The plain creative flying-speed ability value, for flights that never set their own. */
	public static final float VANILLA_FLYING_SPEED = 0.05f;
	/** Default fraction of the gap to the wanted velocity closed a tick while steering (Green Lantern's). */
	public static final double DEFAULT_ACCELERATION = GreenLanternConfig.FLIGHT_ACCELERATION;
	/** Default fraction closed a tick while steering against the current motion (pressing S while flying forward). */
	public static final double DEFAULT_REVERSE = 0.30;
	/** Default fraction closed a tick with no input, easing into the hover (Green Lantern's). */
	public static final double DEFAULT_IDLE = GreenLanternConfig.FLIGHT_BRAKING;
	/** A velocity change bigger than this (blocks/tick) that the flight did not make itself is an outside push. */
	public static final double PUSH_ADOPT_DISTANCE = 0.6;
	/** While carrying an outside push (a dash, a knockback) the flight eases back at vanilla's air drag instead. */
	public static final double PUSH_EASE = 0.09;
	/** How many ticks an adopted outside push is carried before the flight takes over again. */
	public static final int PUSH_CARRY_TICKS = 10;
	/** An adopted outside push is never taken above this speed (blocks/tick) -- a guard against a stale server velocity. */
	public static final double MAX_ADOPTED_PUSH = 6.0;
	/** Below this squared speed (blocks/tick) with no input, the hover snaps to a dead stop. */
	public static final double HOVER_SNAP_SQR = 1.0e-4;

	// -- Flight power (power_03_flight) caps, previously the LocalPlayerMixin clamp: 15 b/s cruise, 25/32/39/46 b/s
	// sprint tiers, 50 b/s Sonic Flight.
	public static final double FLIGHT_POWER_CRUISE_CAP = 15.0 / 20.0;
	public static final double FLIGHT_POWER_SONIC_SPEED = 50.0 / 20.0;

	/** Iron Man's supersonic burst: the old server drive (0.7 kept + 0.9 along the look) settled at 3 blocks a tick. */
	public static final double IRON_MAN_SUPERSONIC_SPEED = 3.0;
	/** Iron Man's supersonic ceiling, 80 m/s. */
	public static final double IRON_MAN_SUPERSONIC_CAP = 80.0 / 20.0;
	/** Repulsor Boots' horizontal ceiling, 15 m/s (half of the fastest marks'). */
	public static final double REPULSOR_BOOTS_CAP = 15.0 / 20.0;

	private DirectionalFlightModel() {
	}

	/**
	 * One flight's numbers for this tick. Speeds are blocks per tick.
	 *
	 * @param speed          cruise speed along the look for a full W / S press (already sprint / boost / tier adjusted)
	 * @param verticalSpeed  Space / Sneak straight up / down speed
	 * @param strafeScale    A / D speed as a fraction of {@code speed}
	 * @param acceleration   fraction of the gap closed a tick while steering
	 * @param reverse        fraction closed a tick while steering against the current motion (at least {@code acceleration})
	 * @param idle           fraction closed a tick with no input (settling into the hover)
	 * @param maxHorizontal  horizontal speed ceiling, 0 = none
	 * @param forcedForward  &gt; 0: a burst that drives along the look at this speed whatever W / S say (supersonic)
	 * @param sneakBoosts    Sneak is a boost modifier while sprinting (Green Lantern) rather than "descend"
	 * @param pushCarryTicks ticks an adopted outside push is carried at vanilla drag (0 = ease it out normally)
	 * @param adoptPushes    adopt big outside velocity changes (knockback, dashes) instead of ignoring them
	 */
	public record Tune(double speed, double verticalSpeed, double strafeScale, double acceleration, double reverse,
			double idle, double maxHorizontal, double forcedForward, boolean sneakBoosts, int pushCarryTicks,
			boolean adoptPushes) {

		public Tune withMaxHorizontal(double max) {
			return new Tune(speed, verticalSpeed, strafeScale, acceleration, reverse, idle, max, forcedForward,
					sneakBoosts, pushCarryTicks, adoptPushes);
		}

		public Tune withForcedForward(double forced) {
			return new Tune(speed, verticalSpeed, strafeScale, acceleration, reverse, idle, maxHorizontal, forced,
					sneakBoosts, pushCarryTicks, forced <= 0.0 && adoptPushes);
		}
	}

	// ---------------------------------------------------------------- per-flight tunes

	/**
	 * Any flight that used to ride vanilla creative flight (Thor, Max Steel Turbo Flight, the experimental hero flights,
	 * rock / flame flight, Magnetic hover): the same cruise and climb speeds vanilla gave its {@code flyingSpeed},
	 * doubled while sprinting like creative flight, with Green Lantern's handling.
	 */
	public static Tune vanilla(float flyingSpeed, boolean sprint) {
		return new Tune(flyingSpeed * VANILLA_CRUISE_FACTOR * (sprint ? 2.0 : 1.0), flyingSpeed * VANILLA_VERTICAL_FACTOR,
				1.0, DEFAULT_ACCELERATION, DEFAULT_REVERSE, DEFAULT_IDLE, 0.0, 0.0, false, PUSH_CARRY_TICKS, true);
	}

	/** Thor's flight: the vanilla tune at Thor's flying speed ({@code ThorPowers.THOR_FLYING_SPEED}). */
	public static Tune thor(float flyingSpeed, boolean sprint) {
		return vanilla(flyingSpeed, sprint);
	}

	/**
	 * Green Lantern Ring Flight, unchanged from v0.13.21: cruise {@code flyingSpeed x 10} (the server sets the cruise /
	 * Boost value), doubled sprinting; 8 b/s climb, 15 b/s boosting; Sneak is the Boost modifier while sprinting.
	 */
	public static Tune greenLantern(float flyingSpeed, boolean sprint, boolean boosting) {
		double vertical = (boosting ? GreenLanternConfig.BOOST_VERTICAL_SPEED_BPS
				: GreenLanternConfig.FLIGHT_VERTICAL_SPEED_BPS) / 20.0;
		return new Tune(flyingSpeed * 10.0 * (sprint ? 2.0 : 1.0), vertical, 1.0, GreenLanternConfig.FLIGHT_ACCELERATION,
				GreenLanternConfig.FLIGHT_ACCELERATION, GreenLanternConfig.FLIGHT_BRAKING, 0.0, 0.0, true,
				PUSH_CARRY_TICKS, true);
	}

	/**
	 * Kryptonian flight, the v0.14.8 numbers: 0.9 cruise, 1.75 sprinting, 2.75 with Flight Boost, 0.6 climb, strafe at 70%.
	 * S now flies backward; turning around uses the old hard-brake rate ({@link KryptonianConfig#FLIGHT_BRAKE}), so
	 * tapping S still stops him dead fast.
	 */
	public static Tune kryptonian(boolean sprint, boolean boost) {
		double speed = sprint ? (boost ? KryptonianConfig.FLIGHT_BOOST_SPEED : KryptonianConfig.FLIGHT_SPRINT_SPEED)
				: KryptonianConfig.FLIGHT_SPEED;
		return new Tune(speed, KryptonianConfig.FLIGHT_VERTICAL_SPEED, 0.7, KryptonianConfig.FLIGHT_ACCELERATION,
				KryptonianConfig.FLIGHT_BRAKE, KryptonianConfig.FLIGHT_COAST, 0.0, 0.0, false, 0, true);
	}

	/**
	 * The experimental Flight power: the vanilla tune at its tier's flying speed, under its old caps -- 15 b/s cruising,
	 * 25 / 32 / 39 / 46 b/s for sprint tiers 0-3 -- and Sonic Flight's 50 b/s forced drive.
	 */
	public static Tune flightPower(float flyingSpeed, boolean sprint, int tier, boolean sonic) {
		int t = Math.max(0, Math.min(3, tier));
		double cap = sprint ? (25.0 + 7.0 * t) / 20.0 : FLIGHT_POWER_CRUISE_CAP;
		Tune tune = vanilla(flyingSpeed, sprint).withMaxHorizontal(sonic ? FLIGHT_POWER_SONIC_SPEED : cap);
		return sonic ? tune.withForcedForward(FLIGHT_POWER_SONIC_SPEED) : tune;
	}

	/**
	 * Iron Man suit flight: the vanilla creative cruise scaled by the mark's {@code flightSpeed} (Mark 1 0.75 ... Mark 6/7
	 * 1.7), doubled sprinting, under the mark's {@code maxFlightSpeedMps} ceiling; how fast it gets up to speed follows
	 * the mark's {@code flightAcceleration}. Supersonic drives along the look at {@link #IRON_MAN_SUPERSONIC_SPEED}.
	 */
	public static Tune ironMan(float suitSpeed, float suitAcceleration, double maxFlightSpeedMps, boolean sprint,
			boolean supersonic) {
		double accel = Math.max(0.10, Math.min(0.35, suitAcceleration * 2.5));
		// v0.14.16 (merge): never slower than the plain creative-flight speed every mark and the boots really flew at
		// before directional flight -- the faster marks still go faster.
		Tune tune = new Tune(VANILLA_FLYING_SPEED * VANILLA_CRUISE_FACTOR * Math.max(1.0, suitSpeed) * (sprint ? 2.0 : 1.0),
				VANILLA_FLYING_SPEED * VANILLA_VERTICAL_FACTOR, 1.0, accel, Math.max(accel, DEFAULT_REVERSE), DEFAULT_IDLE,
				maxFlightSpeedMps > 0.0 ? maxFlightSpeedMps / 20.0 : 0.0, 0.0, false, PUSH_CARRY_TICKS, true);
		if (supersonic) {
			return tune.withMaxHorizontal(IRON_MAN_SUPERSONIC_CAP).withForcedForward(IRON_MAN_SUPERSONIC_SPEED);
		}
		return tune;
	}

	/**
	 * v0.14.27: an Iron Man suit's flight with the per-suit extras -- {@code cruiseMps > 0} fixes the cruise speed (the
	 * Mark 1 burst flies at 8 b/s), and {@code speedMultiplier} scales cruise, climb and ceiling (the Mark 2 supersonic
	 * boost doubles them).
	 */
	public static Tune ironManSuit(float suitSpeed, float suitAcceleration, double maxFlightSpeedMps, double cruiseMps,
			boolean sprint, boolean supersonic, double speedMultiplier) {
		Tune base = ironMan(suitSpeed, suitAcceleration, maxFlightSpeedMps, sprint, supersonic);
		if (supersonic || (cruiseMps <= 0.0 && speedMultiplier == 1.0)) {
			return base;
		}
		double speed = (cruiseMps > 0.0 ? cruiseMps / 20.0 : base.speed()) * speedMultiplier;
		double max = base.maxHorizontal() > 0.0 ? Math.max(base.maxHorizontal() * speedMultiplier, speed) : 0.0;
		return new Tune(speed, base.verticalSpeed() * speedMultiplier, base.strafeScale(), base.acceleration(), base.reverse(),
				base.idle(), max, 0.0, false, base.pushCarryTicks(), base.adoptPushes());
	}

	/**
	 * v0.14.27: the lowest Y a hover-floored flight may sink to at {@code pos} -- {@code floor} blocks above the first
	 * solid surface within {@code floor + 2} blocks below, or {@code Double.NEGATIVE_INFINITY} with no ground that close.
	 */
	public static double hoverFloorY(net.minecraft.world.level.BlockGetter level, Vec3 pos, double floor) {
		Vec3 from = pos.add(0, 0.01, 0);
		Vec3 to = pos.subtract(0, floor + 2.0, 0);
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
			return Double.NEGATIVE_INFINITY;
		}
		return hit.getLocation().y + floor;
	}

	/**
	 * Repulsor Boots: the Mark 2's cruise speed (never slower than the creative-flight speed they always really had) with
	 * {@code FLIGHT_ACCELERATION} (half its pick-up) -- no extra ceiling, so sprint flight is as quick as it was.
	 */
	public static Tune repulsorBoots(boolean sprint) {
		return ironMan(com.projecthero.mod.ironman.RepulsorBoots.FLIGHT_SPEED,
				com.projecthero.mod.ironman.RepulsorBoots.FLIGHT_ACCELERATION, 0.0, sprint, false);
	}

	// ---------------------------------------------------------------- the model

	/** The vertical input: Space up, Sneak down -- unless Sneak is this flight's boost modifier and the player is boosting. */
	public static int verticalInput(boolean jumping, boolean sneaking, boolean boosting) {
		return (jumping ? 1 : 0) - (sneaking && !boosting ? 1 : 0);
	}

	/**
	 * The velocity the flight wants this tick.
	 *
	 * @param look       the 3D look vector (unit length)
	 * @param yawDegrees the player's yaw, for the horizontal strafe axis
	 * @param forward    W / S impulse, +1 forward .. -1 backward
	 * @param strafe     A / D impulse, +1 left .. -1 right (vanilla's {@code leftImpulse})
	 * @param vertical   +1 up, -1 down, 0 neither
	 */
	public static Vec3 wantedVelocity(Vec3 look, float yawDegrees, float forward, float strafe, int vertical, Tune tune) {
		boolean forced = tune.forcedForward() > 0.0;
		double fwd = forced ? 1.0 : forward;
		double speed = forced ? tune.forcedForward() : tune.speed();
		double yaw = yawDegrees * (Math.PI / 180.0);
		Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
		Vec3 wish = look.scale(fwd).add(left.scale(strafe * tune.strafeScale()));
		if (wish.lengthSqr() > 1.0) {
			wish = wish.normalize();
		}
		return wish.scale(speed).add(0.0, vertical * tune.verticalSpeed(), 0.0);
	}

	/** True if the player is giving the flight any input this tick (or a forced burst is driving it). */
	public static boolean steering(float forward, float strafe, int vertical, Tune tune) {
		return forward != 0f || strafe != 0f || vertical != 0 || tune.forcedForward() > 0.0;
	}

	/** The fraction of the gap to {@code wanted} closed this tick. */
	public static double ease(Vec3 velocity, Vec3 wanted, boolean steering, Tune tune) {
		if (!steering) {
			return tune.idle();
		}
		if (velocity.dot(wanted) < 0.0) {
			return Math.max(tune.acceleration(), tune.reverse());
		}
		return tune.acceleration();
	}

	/**
	 * One tick of the model: ease from last tick's velocity toward the wanted one, snap a near-still hover to a dead stop,
	 * and hold the horizontal speed under the flight's ceiling (not while an outside push is being carried).
	 *
	 * @param carryingPush an outside push (a dash, a knockback) was adopted within the last few ticks
	 */
	public static Vec3 step(Vec3 velocity, Vec3 wanted, boolean steering, Tune tune, boolean carryingPush) {
		double ease = ease(velocity, wanted, steering, tune);
		// a push that outruns the flight bleeds off at vanilla's air drag; one slower than it is simply flown out of
		if (carryingPush && velocity.lengthSqr() > wanted.lengthSqr()) {
			ease = Math.min(ease, PUSH_EASE);
		}
		Vec3 next = velocity.add(wanted.subtract(velocity).scale(ease));
		if (!steering && next.lengthSqr() < HOVER_SNAP_SQR) {
			return Vec3.ZERO;
		}
		if (tune.maxHorizontal() > 0.0 && !carryingPush) {
			next = clampHorizontal(next, tune.maxHorizontal());
		}
		return next;
	}

	public static Vec3 clampHorizontal(Vec3 v, double max) {
		double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
		if (horizontal <= max || horizontal < 1.0e-9) {
			return v;
		}
		double f = max / horizontal;
		return new Vec3(v.x * f, v.y, v.z * f);
	}

	/**
	 * Did something other than the flight change the velocity since last tick (a knockback, a dash, a server launch)?
	 * {@code vanillaNudge} is the Space / Sneak vertical nudge vanilla's own creative flight already added this tick.
	 */
	public static boolean outsidePush(Vec3 recorded, Vec3 current, double vanillaNudge) {
		return current.subtract(recorded.add(0.0, vanillaNudge, 0.0)).lengthSqr() > PUSH_ADOPT_DISTANCE * PUSH_ADOPT_DISTANCE;
	}

	/**
	 * For the flight poses: is a flier moving mostly backward? True when the movement's component along the body's facing
	 * is negative and at least half the horizontal speed (a strafe drifting a little back still reads as a strafe).
	 */
	public static boolean backward(double horizontalSpeed, double forwardSpeed) {
		return forwardSpeed < 0.0 && -forwardSpeed >= horizontalSpeed * 0.5;
	}

	/** An outside push as adopted: kept as it is, but never above {@link #MAX_ADOPTED_PUSH}. */
	public static Vec3 adoptedPush(Vec3 pushed) {
		double length = pushed.length();
		return length > MAX_ADOPTED_PUSH ? pushed.scale(MAX_ADOPTED_PUSH / length) : pushed;
	}
}
