package com.projecthero.mod.flight;

import com.projecthero.mod.flight.DirectionalFlightModel.Tune;

import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the maths behind <b>Physics Flight</b>, the optional second flight mode picked on the {@code ,} Flight Mode
 * menu (explicit user request -- "add physics to the flying like the Do a Barrel Roll mod ... choosing the regular flight
 * or physics flight"). Pure functions, no client classes, so the gametests can check them; the client
 * ({@code client.flight.DirectionalFlight} for the velocity, {@code client.flight.PhysicsFlight} for the attitude) feeds
 * them the local player's keys, mouse and look.
 *
 * <p>Where Regular flight eases straight to the velocity the keys ask for, Physics flight flies like an aircraft:
 * <ul>
 *   <li><b>momentum</b> -- the velocity's direction swings toward the look over several ticks (it never snaps), and a
 *   hard turn bleeds a little speed;</li>
 *   <li><b>W</b> is throttle: it accelerates toward the flight's top speed instead of jumping to it; <b>S</b> is an air
 *   brake (it never flies backward);</li>
 *   <li><b>diving gains speed</b> (a steep dive up to {@link #DIVE_OVERSPEED} times the top speed), <b>climbing loses it</b>, and with no throttle a light drag slowly glides you down to a
 *   hover;</li>
 *   <li>each flight keeps its own top speed, sprint speed, boosts and ceilings ({@link Tune}).</li>
 * </ul>
 * Attitude (yaw / pitch / <b>roll</b>) is handled by {@link #rotate}: mouse and key inputs turn the flier about its
 * <em>own</em> axes, so a banked flier pulling up turns, and loops and full barrel rolls work.
 */
public final class PhysicsFlightModel {
	/** Fraction of the gap between the velocity's direction and the look closed a tick (the inertia when turning). */
	public static final double GRIP = 0.12;
	/** A tick of hard turning (velocity at right angles to the look) bleeds this fraction of {@link #GRIP} worth of speed. */
	public static final double TURN_BLEED = 0.35;
	/** Speed (blocks/tick) gained a tick diving straight down, lost a tick climbing straight up. */
	public static final double DIVE_GAIN = 0.02;
	/** A dive may carry you up to this multiple of the flight's top speed; level out and the excess bleeds away. */
	public static final double DIVE_OVERSPEED = 1.25;
	/** Throttle pick-up: this fraction of the flight's own acceleration, so it builds speed instead of snapping to it. */
	public static final double THRUST_FRACTION = 0.4;
	/** Light air drag kept a tick with no throttle (a long glide). */
	public static final double GLIDE_DRAG = 0.985;
	/** Speed kept a tick on the air brake (S). */
	public static final double BRAKE_DRAG = 0.85;
	/** Faster than the top speed (sprint let go, a push carried): this fraction of the excess bleeds off a tick. */
	public static final double OVERSPEED_BLEED = 0.06;
	/** Below this speed (blocks/tick) with no throttle, the flight settles into a dead hover. */
	public static final double HOVER_SPEED = 0.02;
	/** Space / Sneak: fraction of the gap to the flight's climb / sink speed closed a tick. */
	public static final double VERTICAL_EASE = 0.25;

	private PhysicsFlightModel() {
	}

	/**
	 * One tick of Physics flight.
	 *
	 * @param velocity     last tick's velocity (blocks/tick)
	 * @param look         the 3D look vector (unit length)
	 * @param throttle     W / S impulse: &gt; 0 throttle, &lt; 0 air brake, 0 glide
	 * @param vertical     Space +1, Sneak -1, neither 0
	 * @param tune         the engaged flight's numbers (top speed = {@code tune.speed()}, or its forced drive)
	 * @param carryingPush an outside push (a dash, a knockback) was adopted in the last few ticks
	 */
	public static Vec3 step(Vec3 velocity, Vec3 look, float throttle, int vertical, Tune tune, boolean carryingPush) {
		boolean forced = tune.forcedForward() > 0.0;
		double top = forced ? tune.forcedForward() : tune.speed();
		double speed = velocity.length();
		Vec3 dir = speed > 1.0e-6 ? velocity.scale(1.0 / speed) : look;

		// inertia: the motion swings toward the look, a hard turn bleeding a little speed
		double grip = carryingPush ? GRIP * 0.3 : GRIP;
		double cos = dir.dot(look);
		Vec3 swung = dir.add(look.subtract(dir).scale(grip));
		dir = swung.lengthSqr() > 1.0e-9 ? swung.normalize() : look;
		speed *= 1.0 - TURN_BLEED * (1.0 - cos) * 0.5 * grip;

		// gravity along the flight path: diving gains (a steep dive can overspeed a little), climbing loses
		double diveCap = top * (1.0 + (DIVE_OVERSPEED - 1.0) * Math.max(0.0, -dir.y));
		if (speed > HOVER_SPEED || throttle > 0f || forced) {
			double gain = -dir.y * DIVE_GAIN;
			if (gain < 0.0 || speed < diveCap) {
				speed = gain > 0.0 ? Math.min(diveCap, speed + gain) : Math.max(0.0, speed + gain);
			}
		}

		// throttle / brake / glide
		if (forced || throttle > 0f) {
			double want = top * (forced ? 1.0 : Math.min(1.0, throttle));
			if (speed < want) {
				speed += (want - speed) * tune.acceleration() * THRUST_FRACTION;
				// from a dead hover the first push points the way you look
				if (velocity.lengthSqr() < 1.0e-6) {
					dir = look;
				}
			}
		} else if (throttle < 0f) {
			speed *= BRAKE_DRAG;
		} else {
			speed *= GLIDE_DRAG;
		}
		if (speed > diveCap) {
			speed -= (speed - diveCap) * OVERSPEED_BLEED;
		}

		Vec3 next = dir.scale(speed);
		if (vertical != 0) {
			double wantY = vertical * tune.verticalSpeed();
			next = new Vec3(next.x, next.y + (wantY - next.y) * VERTICAL_EASE, next.z);
		}
		if (!forced && throttle <= 0f && vertical == 0 && !carryingPush && next.lengthSqr() < HOVER_SPEED * HOVER_SPEED) {
			return Vec3.ZERO;
		}
		if (tune.maxHorizontal() > 0.0 && !carryingPush) {
			next = DirectionalFlightModel.clampHorizontal(next, tune.maxHorizontal());
		}
		return next;
	}

	// ---------------------------------------------------------------- attitude

	/**
	 * Turns a flier about its <em>own</em> axes and returns the new {@code {yaw, pitch, roll}} in Minecraft's convention
	 * (degrees; yaw 0 = south, increasing to the right; pitch + = nose down, always within -90..90; roll + = right wing
	 * down). Pitching through the vertical flips yaw and roll by 180 degrees exactly as the orientation demands, so loops
	 * and barrel rolls are continuous.
	 *
	 * @param dPitch nose down (+) / up (-) about the flier's right axis, degrees
	 * @param dYaw   nose right (+) / left (-) about the flier's up axis, degrees
	 * @param dRoll  right wing down (+) / up (-) about the flier's forward axis, degrees
	 */
	public static float[] rotate(float yaw, float pitch, float roll, double dPitch, double dYaw, double dRoll) {
		double[][] basis = basis(yaw, pitch, roll);
		double[] f = basis[0];
		double[] r = basis[1];
		double[] u = basis[2];
		if (dPitch != 0.0) {
			double a = Math.toRadians(dPitch);
			double c = Math.cos(a);
			double s = Math.sin(a);
			double[] nf = combine(f, c, u, -s);
			double[] nu = combine(u, c, f, s);
			f = nf;
			u = nu;
		}
		if (dYaw != 0.0) {
			double a = Math.toRadians(dYaw);
			double c = Math.cos(a);
			double s = Math.sin(a);
			double[] nf = combine(f, c, r, s);
			double[] nr = combine(r, c, f, -s);
			f = nf;
			r = nr;
		}
		if (dRoll != 0.0) {
			double a = Math.toRadians(dRoll);
			double c = Math.cos(a);
			double s = Math.sin(a);
			double[] nr = combine(r, c, u, -s);
			double[] nu = combine(u, c, r, s);
			r = nr;
			u = nu;
		}
		return angles(normalize(f), normalize(r), yaw);
	}

	/** {forward, right, up} unit vectors of a flier at this yaw / pitch / roll. */
	public static double[][] basis(float yaw, float pitch, float roll) {
		double y = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		double q = Math.toRadians(roll);
		double[] f = { -Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p) };
		double[] r0 = { -Math.cos(y), 0.0, -Math.sin(y) };
		double[] u0 = cross(r0, f);
		double[] r = combine(r0, Math.cos(q), u0, -Math.sin(q));
		double[] u = combine(u0, Math.cos(q), r0, Math.sin(q));
		return new double[][] { f, r, u };
	}

	/** Yaw / pitch / roll back out of a forward and right vector; {@code fallbackYaw} only matters looking straight up / down. */
	static float[] angles(double[] f, double[] r, float fallbackYaw) {
		double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, f[1]))));
		double horizontal = Math.sqrt(f[0] * f[0] + f[2] * f[2]);
		double yaw;
		double roll;
		if (horizontal < 1.0e-6) {
			// straight up or down: the yaw is whatever puts the right vector level, with no roll
			yaw = Math.toDegrees(Math.atan2(-r[2], -r[0]));
			roll = 0.0;
		} else {
			yaw = Math.toDegrees(Math.atan2(-f[0], f[2]));
			double y = Math.toRadians(yaw);
			double[] r0 = { -Math.cos(y), 0.0, -Math.sin(y) };
			double[] u0 = cross(r0, f);
			roll = Math.toDegrees(Math.atan2(-dot(r, u0), dot(r, r0)));
		}
		// keep the yaw continuous with the old one (no 360-degree jumps for the interpolation)
		double delta = wrap(yaw - fallbackYaw);
		return new float[] { (float) (fallbackYaw + delta), (float) pitch, (float) wrap(roll) };
	}

	/** Wraps degrees into -180..180. */
	public static double wrap(double degrees) {
		double d = degrees % 360.0;
		if (d >= 180.0) {
			d -= 360.0;
		}
		if (d < -180.0) {
			d += 360.0;
		}
		return d;
	}

	private static double[] combine(double[] a, double ka, double[] b, double kb) {
		return new double[] { a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb };
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] normalize(double[] a) {
		double l = Math.sqrt(dot(a, a));
		return l < 1.0e-12 ? a : new double[] { a[0] / l, a[1] / l, a[2] / l };
	}
}
