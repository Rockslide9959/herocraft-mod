package com.projecthero.mod.ironman;

import net.minecraft.util.Mth;

/**
 * v0.15.9, explicit user request ("the pose looks weird especially while moving, just give it a newer and better
 * animation"): the pure numbers behind the Repulsor Boots flight pose (a bare {@code RepulsorItem} worn in the boots
 * slot -- see {@link RepulsorBoots}). Common code with no client classes so the gametests can pin it down; the client
 * ({@code client.ironman.IronManFlightPose}, {@code client.FlightPoseHelper}, {@code client.mixin.PlayerRendererMixin})
 * runs on it.
 *
 * <p>The pose is driven by the flier's <b>velocity in the body's own frame</b> -- forward / sideways / vertical, each
 * normalised against the boots' cruise speed -- rather than by discrete speed tiers, so the lean grows smoothly with
 * speed instead of snapping between poses:
 * <ul>
 *   <li><b>hover</b>: upright, arms angled down and a little back with the palms (the hand ends) to the ground, legs
 *   together and slightly back, a slow bob;</li>
 *   <li><b>forward</b>: the body leans into the flight up to {@link #MAX_LEAN_DEGREES}, arms sweep back along the body,
 *   legs trail;</li>
 *   <li><b>backward</b>: a slight lean back (up to {@link #MAX_BACK_LEAN_DEGREES}), arms forward braking;</li>
 *   <li><b>strafing</b>: the body banks into the slide (up to {@link #MAX_ROLL_DEGREES}), legs trail the other way;</li>
 *   <li><b>climbing / sinking</b>: arms tuck straight down to push / flare out to brake.</li>
 * </ul>
 * Every value is eased once per tick toward its target and interpolated across the partial tick by the client, so it
 * is frame-rate independent and never snaps.
 */
public final class RepulsorFlightLook {
	private RepulsorFlightLook() {
	}

	/** Forward lean at full cruise (degrees). */
	public static final float MAX_LEAN_DEGREES = 35.0f;
	/** Lean back when flying backward at full speed (degrees). */
	public static final float MAX_BACK_LEAN_DEGREES = 12.0f;
	/** Bank when strafing at full speed (degrees, positive = toward the flier's right). */
	public static final float MAX_ROLL_DEGREES = 18.0f;
	/**
	 * Horizontal speed (blocks/tick) that counts as "full speed" for the pose -- just under the boots' 0.55 b/t cruise
	 * ({@code DirectionalFlightModel#repulsorBoots}), so cruising flight reaches the full lean.
	 */
	public static final double FULL_SPEED = 0.5;
	/** Vertical speed (blocks/tick) that counts as a full climb / sink (the boots climb at 0.375 b/t). */
	public static final double FULL_VERTICAL = 0.35;
	/** Per-tick ease of the measured body-frame velocity (smooths remote-player position jitter). */
	public static final float VELOCITY_EASE = 0.35f;
	/** Per-tick ease of the lean / roll toward their targets (on top of the velocity ease: an S-curve, ~0.5 s). */
	public static final float BODY_EASE = 0.2f;
	/** Vertical bob amplitude while hovering (blocks) and its angular speed (radians per tick). */
	public static final float BOB_AMPLITUDE = 0.035f;
	public static final float BOB_SPEED = 0.14f;

	/** Signed speed along the body's facing ({@code yawDegrees}), blocks/tick; negative = flying backward. */
	public static double forward(double dx, double dz, float yawDegrees) {
		float yaw = yawDegrees * Mth.DEG_TO_RAD;
		return -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
	}

	/** Signed speed toward the body's right ({@code yawDegrees}), blocks/tick. */
	public static double right(double dx, double dz, float yawDegrees) {
		float yaw = yawDegrees * Mth.DEG_TO_RAD;
		return -dx * Mth.cos(yaw) - dz * Mth.sin(yaw);
	}

	/** A body-frame speed normalised to -1..1 against {@code full}. */
	public static float normalise(double speed, double full) {
		return (float) Mth.clamp(speed / full, -1.0, 1.0);
	}

	/**
	 * Ease-out response so a gentle drift already leans visibly and the last stretch to cruise tapers in, but still
	 * strictly proportional in direction: 0 -> 0, +-1 -> +-1, monotonic.
	 */
	public static float response(float n) {
		float a = Math.min(1.0f, Math.abs(n));
		float r = 1.0f - (1.0f - a) * (1.0f - a);
		return Math.copySign(r, n);
	}

	/** Target body lean (degrees, positive = forward) for a normalised forward speed. */
	public static float targetLean(float forwardN) {
		float r = response(forwardN);
		return r >= 0.0f ? r * MAX_LEAN_DEGREES : r * MAX_BACK_LEAN_DEGREES;
	}

	/** Target body roll (degrees, positive = banked toward the right) for a normalised rightward speed. */
	public static float targetRoll(float rightN) {
		return response(rightN) * MAX_ROLL_DEGREES;
	}

	/** One tick of exponential easing from {@code current} toward {@code target}. */
	public static float ease(float current, float target, float rate) {
		float next = current + (target - current) * rate;
		return Math.abs(target - next) < 1.0e-4f ? target : next;
	}

	/** How much of the hover pose applies (1 = dead still, 0 = moving at full speed in any direction). */
	public static float hoverWeight(float forwardN, float rightN) {
		return 1.0f - Math.min(1.0f, (float) Math.sqrt(forwardN * forwardN + rightN * rightN));
	}
}
