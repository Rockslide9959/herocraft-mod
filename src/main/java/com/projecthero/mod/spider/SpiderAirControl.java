package com.projecthero.mod.spider;

import com.projecthero.mod.hero.power.ElytraPhysics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user: "when spider-man is web swinging dont make him slow down in the air, similar to how the hulks jumps
 * work ... give spider-man increased aerial control aswell please").
 *
 * <p><b>Air momentum.</b> Once he lets go of a swing in the air (or fires a Zip Boost, or lets go of a Web Wings glide),
 * the owning client takes vanilla's horizontal air drag back off every tick ({@link #cancelDrag}) -- the same trick as
 * the Hulk's Super Leap ({@code HulkLeap.cancelDrag}) -- so he carries the release speed all the way to the next line or
 * the ground instead of bleeding 9% of it a tick. Gravity and the vertical drag are vanilla's. It stops the moment he
 * lands, hits a wall, enters water / lava, grabs a wall, hangs, glides, rides or flies
 * ({@code client.spider.SpiderAirMomentumClient}).
 *
 * <p><b>Aerial control.</b> While it lasts, {@link #steer} turns his airborne heading toward the held direction (W = where
 * he is looking, A / D = his left / right, combinations in between) at {@code airTurnDegPerTick}, pushes him along it at
 * {@code airAccel} up to {@code airAccelCap}, and S sheds {@code airBrake} of his speed a tick. Horizontal speed never
 * exceeds the swing's own top speed ({@code swingMaxSpeed}).
 *
 * <p>Multiplayer: movement is the client's, as with the swing; the server's checks are untouched -- he is always falling
 * (gravity is vanilla's, so the floating check never trips) and never faster than a swing already moves him.
 */
public final class SpiderAirControl {
	private SpiderAirControl() {
	}

	/** After vanilla's air step: put back the horizontal drag (0.91) it just took off. Vertical is left alone. */
	public static Vec3 cancelDrag(Vec3 afterVanilla) {
		return new Vec3(afterVanilla.x / ElytraPhysics.AIR_DRAG_XZ, afterVanilla.y, afterVanilla.z / ElytraPhysics.AIR_DRAG_XZ);
	}

	/** {@link #steer(Vec3, float, int, double, double, double, double, double)} with the config's numbers. */
	public static Vec3 steer(Vec3 v, float yawDeg, int input) {
		SpiderManConfig.Moves c = SpiderManConfig.moves();
		return steer(v, yawDeg, input, c.airTurnDegPerTick, c.airAccel, c.airAccelCap, c.airBrake, c.swingMaxSpeed);
	}

	/**
	 * One tick of aerial control on velocity {@code v} (only its horizontal part changes).
	 *
	 * @param yawDeg   his look yaw (vanilla degrees, 0 = +Z)
	 * @param input    packed keys ({@link SpiderSwingInput})
	 * @param turnDeg  the most the heading turns toward the held direction this tick
	 * @param accel    push along the held direction (only while slower than {@code accelCap})
	 * @param brake    fraction of horizontal speed S sheds
	 * @param maxSpeed horizontal speed cap
	 */
	public static Vec3 steer(Vec3 v, float yawDeg, int input, double turnDeg, double accel, double accelCap, double brake,
			double maxSpeed) {
		Vec3 want = wantDirection(yawDeg, input);
		double hx = v.x;
		double hz = v.z;
		double speed = Math.sqrt(hx * hx + hz * hz);
		if (want != null) {
			float wantHeading = heading(want.x, want.z);
			if (speed < 0.05) {
				// barely moving: no heading to turn -- just push off the way he wants to go
				double s = Math.min(accelCap, speed + accel);
				hx = want.x * Math.max(s, speed);
				hz = want.z * Math.max(s, speed);
			} else {
				float now = heading(hx, hz);
				float turn = Mth.clamp(Mth.wrapDegrees(wantHeading - now), (float) -turnDeg, (float) turnDeg);
				float next = now + turn;
				double s = speed;
				// push only roughly along the way he is going (after the turn) and only below the cap
				if (s < accelCap && Math.abs(Mth.wrapDegrees(wantHeading - next)) < 60f) {
					s = Math.min(accelCap, s + accel);
				}
				Vec3 dir = Vec3.directionFromRotation(0f, next);
				hx = dir.x * s;
				hz = dir.z * s;
			}
		}
		if (SpiderSwingInput.back(input)) {
			hx *= 1.0 - brake;
			hz *= 1.0 - brake;
		}
		double h = Math.sqrt(hx * hx + hz * hz);
		if (h > maxSpeed) {
			hx *= maxSpeed / h;
			hz *= maxSpeed / h;
		}
		return new Vec3(hx, v.y, hz);
	}

	/**
	 * What vanilla's own air control ({@code moveRelative} in {@code LivingEntity.travel}) added to the move this tick:
	 * {@code xxa} / {@code zza} are the strafe / forward impulses it used, {@code yawDeg} his yaw, and the push 0.02 a
	 * tick (0.026 sprinting -- {@code Player.getFlyingSpeed}). Taken back out before {@link #steer}, so with the drag gone
	 * it can't pile up past {@code airAccelCap}.
	 */
	public static Vec3 vanillaAirInput(float xxa, float zza, float yawDeg, boolean sprinting) {
		Vec3 rel = new Vec3(xxa, 0.0, zza);
		double len2 = rel.lengthSqr();
		if (len2 < 1.0e-7) {
			return Vec3.ZERO;
		}
		float speed = sprinting ? 0.025999999f : 0.02f;
		Vec3 v = (len2 > 1.0 ? rel.normalize() : rel).scale(speed);
		float sin = Mth.sin(yawDeg * Mth.DEG_TO_RAD);
		float cos = Mth.cos(yawDeg * Mth.DEG_TO_RAD);
		return new Vec3(v.x * cos - v.z * sin, 0.0, v.z * cos + v.x * sin);
	}

	/** The held direction, flat and unit length -- W forward, A / D left / right -- or null for none (S alone brakes). */
	public static Vec3 wantDirection(float yawDeg, int input) {
		Vec3 fwd = Vec3.directionFromRotation(0f, yawDeg);
		Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
		double f = SpiderSwingInput.forward(input) ? 1.0 : 0.0;
		double r = (SpiderSwingInput.right(input) ? 1.0 : 0.0) - (SpiderSwingInput.left(input) ? 1.0 : 0.0);
		Vec3 w = fwd.scale(f).add(right.scale(r));
		return w.lengthSqr() < 1.0e-6 ? null : w.normalize();
	}

	/** Vanilla yaw (degrees) of the flat direction (x, z). */
	public static float heading(double x, double z) {
		return (float) (Mth.atan2(-x, z) * Mth.RAD_TO_DEG);
	}
}
