package com.projecthero.mod.ironman;

import net.minecraft.util.Mth;

/**
 * v0.14.21 flight revamp: the pure numbers behind how Iron Man (and Repulsor Boots) flight <em>looks and sounds</em> --
 * the thruster jet profile per flight state, the hard-vs-soft landing rule, the take-off / landing timings and the
 * thruster-loop volume / pitch curves. Common code (no client classes) so the gametests can pin it down; the client
 * ({@code client.ironman.IronManFlightPose}, {@code client.IronManFlightFxClient}) runs on it.
 *
 * <p>Nothing here touches flight <b>speed or energy</b>: those stay in {@code flight.DirectionalFlightModel#ironMan} and
 * {@link IronManFlight#tick}. Every effect is client-side, driven by state every client already has for every player
 * it can see -- the synced {@code IRON_MAN_FLYING} / {@code REPULSOR_BOOTS_FLYING} flags, the synced sprint flag, the
 * synced {@code TonyStarkState.supersonicUntil}, worn boots, and the interpolated position.
 */
public final class IronManFlightLook {
	private IronManFlightLook() {
	}

	/** What the thrusters are doing -- picks the jet length / density. */
	public enum JetState { HOVER, FORWARD, BACK, SPRINT, SUPERSONIC }

	public static JetState jetState(boolean moving, boolean backward, boolean sprinting, boolean supersonic) {
		if (supersonic) {
			return JetState.SUPERSONIC;
		}
		if (!moving) {
			return JetState.HOVER;
		}
		if (backward) {
			return JetState.BACK;
		}
		return sprinting ? JetState.SPRINT : JetState.FORWARD;
	}

	/** Visible jet length (blocks, before the wearer's scale): short stabilising jets hovering, long ones sprinting. */
	public static float jetLength(JetState s) {
		return switch (s) {
			case HOVER -> 0.32f;
			case FORWARD -> 0.55f;
			case BACK -> 0.45f;
			case SPRINT -> 1.05f;
			case SUPERSONIC -> 1.6f;
		};
	}

	/** Thrust particles per thruster per tick. */
	public static int jetParticles(JetState s) {
		return switch (s) {
			case HOVER -> 1;
			case FORWARD, BACK -> 2;
			case SPRINT, SUPERSONIC -> 3;
		};
	}

	/** Speed (blocks/tick) the thrust particles leave the nozzle at, along the jet. */
	public static double jetSpeed(JetState s) {
		return switch (s) {
			case HOVER -> 0.10;
			case FORWARD -> 0.18;
			case BACK -> 0.15;
			case SPRINT -> 0.32;
			case SUPERSONIC -> 0.45;
		};
	}

	// ---------------------------------------------------------------- landing

	/** Falling at least this fast (blocks/tick, ~9 m/s) when flight ends on the ground = a superhero landing. */
	public static final double HARD_LANDING_DESCENT = 0.45;
	/** ...or moving at least this fast overall (~14 m/s)... */
	public static final double HARD_LANDING_SPEED = 0.7;
	/** ...while coming down at least this fast (a fast flier skimming in, not one sliding along the floor). */
	public static final double HARD_LANDING_MIN_DESCENT = 0.12;
	/**
	 * How many ticks of motion before the flight flag dropped count toward the landing speed: the flag arrives from the
	 * server a tick or more after the touch-down (more with lag), by which time the body has already stopped.
	 */
	public static final int LANDING_LOOKBACK_TICKS = 8;

	/**
	 * Hard landing (three-point superhero landing + ground impact) or soft one (just settle)? A plain Sneak descent
	 * (0.375 b/t straight down) is soft; diving in along the look, or coming in fast and low, is hard. Never hard unless
	 * the flight actually ended on the ground (switching flight off in mid-air is not a landing at all).
	 */
	public static boolean isHardLanding(double peakSpeed, double peakDescent, boolean grounded) {
		if (!grounded) {
			return false;
		}
		return peakDescent >= HARD_LANDING_DESCENT
				|| (peakSpeed >= HARD_LANDING_SPEED && peakDescent >= HARD_LANDING_MIN_DESCENT);
	}

	/** Landing pose timings (ticks): slam down by IN, hold to HOLD_END (~0.6 s down), back up by END. */
	public static final float LANDING_IN = 2.0f;
	public static final float LANDING_HOLD_END = 13.0f;
	public static final float LANDING_END = 19.0f;

	/** 0..1 weight of the landing pose {@code t} ticks after touch-down. */
	public static float landingWeight(float t) {
		if (t < 0f || t >= LANDING_END) {
			return 0f;
		}
		if (t < LANDING_IN) {
			return smooth(t / LANDING_IN);
		}
		if (t < LANDING_HOLD_END) {
			return 1f;
		}
		return 1f - smooth((t - LANDING_HOLD_END) / (LANDING_END - LANDING_HOLD_END));
	}

	// ---------------------------------------------------------------- take-off

	/** Take-off: a brief crouch (knees tucked, arms pulled back) peaking at CROUCH_PEAK, released by TAKEOFF_END. */
	public static final float TAKEOFF_CROUCH_PEAK = 2.5f;
	public static final float TAKEOFF_END = 8.0f;

	/** 0..1 weight of the take-off crouch {@code t} ticks after flight engaged. */
	public static float takeoffCrouch(float t) {
		if (t < 0f || t >= TAKEOFF_END) {
			return 0f;
		}
		if (t < TAKEOFF_CROUCH_PEAK) {
			return smooth(t / TAKEOFF_CROUCH_PEAK);
		}
		return 1f - smooth((t - TAKEOFF_CROUCH_PEAK) / (TAKEOFF_END - TAKEOFF_CROUCH_PEAK));
	}

	// ---------------------------------------------------------------- sound

	/** Thruster roar loop volume for a flier moving at {@code speed} blocks/tick. Hover is audible, sprint is loud. */
	public static float thrusterVolume(double speed, boolean supersonic) {
		float v = 0.35f + (float) Math.min(1.0, speed / 1.5) * 0.4f + (supersonic ? 0.15f : 0f);
		return Mth.clamp(v, 0f, 0.9f);
	}

	/** Thruster roar loop pitch: a low idle rumble rising into a jet whine with speed. */
	public static float thrusterPitch(double speed, boolean supersonic) {
		float p = 0.7f + (float) Math.min(1.0, speed / 2.0) * 0.5f + (supersonic ? 0.15f : 0f);
		return Mth.clamp(p, 0.5f, 1.4f);
	}

	/** Wind rush layer: silent hovering, swelling in with speed. */
	public static float windVolume(double speed) {
		return Mth.clamp((float) ((speed - 0.3) / 1.4), 0f, 1f) * 0.75f;
	}

	/** Repulsor whine layer: strongest at a hover (the stabilisers working), backing off as the roar takes over. */
	public static float whineVolume(double speed) {
		return 0.32f - Mth.clamp((float) (speed / 1.2), 0f, 1f) * 0.18f;
	}

	/** Ticks the loop takes to fade in on take-off and out on landing. */
	public static final int SOUND_FADE_IN_TICKS = 8;
	public static final int SOUND_FADE_OUT_TICKS = 6;

	public static float smooth(float f) {
		f = Mth.clamp(f, 0f, 1f);
		return f * f * (3f - 2f * f);
	}
}
