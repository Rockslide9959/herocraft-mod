package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: per-body animation state the T-Rex, snake, fly and the flyers ease and integrate between frames (how far
 * into flight, the wing-beat phase and strength, glide, bank, a damped head, footfall impacts). Keyed by {@link BeastPose#entityId} and
 * stepped by the pose's own clock ({@code age}, ticks), so it is frame-rate independent, deterministic in previews and inert across the
 * repeated draw passes of one frame (dt = 0). A pose without an id gets a fresh state every call that snaps straight to its targets.
 * Render thread only; cleared with {@link BeastPose#clearSmoothing()} (leaving a world).
 */
public final class MotionState {
	private static final Map<Long, MotionState> STATES = new HashMap<>();

	/** True on the first step (nothing to ease from): {@link #ease} snaps. */
	public boolean fresh = true;
	/** Ticks since the last step (0 for repeated passes of the same frame). */
	public float dt;
	private float lastAge;
	private boolean started;

	// flyers
	public float air, flapPhase, flapAmp, glide, bank, pitch, speed, climb, perch;
	// walkers
	public float stomp, sway, headYaw, headPitch;
	/** Free slots for a body's own eased values. */
	public final float[] f = new float[8];

	private MotionState() {
	}

	public static void clear() {
		STATES.clear();
	}

	/** The state of {@code s}'s body for {@code kind} (one id can carry several bodies over time), stepped to the pose's clock. */
	public static MotionState of(BeastPose s, int kind) {
		MotionState m;
		if (s.entityId == -1) {
			m = new MotionState();
		} else {
			if (STATES.size() > 256) {
				STATES.clear();
			}
			m = STATES.computeIfAbsent(((long) s.entityId << 8) | (kind & 255), k -> new MotionState());
		}
		if (!m.started) {
			m.started = true;
			m.fresh = true;
			m.dt = 0f;
			m.lastAge = s.age;
			return m;
		}
		float d = s.age - m.lastAge;
		// a jump back (a new preview) or a long gap (unloaded): start over rather than easing across it
		m.fresh = d < 0f || d > 40f;
		if (m.fresh) {
			d = 0f;
		}
		m.dt = Math.min(d, 3f);
		m.lastAge = s.age;
		return m;
	}

	/** Eases {@code cur} toward {@code target} at {@code rate} per tick (exponential, frame-rate independent); snaps when fresh. */
	public float ease(float cur, float target, float rate) {
		if (fresh) {
			return target;
		}
		return cur + (target - cur) * (1f - (float) Math.exp(-rate * dt));
	}

	/** Like {@link #ease} but for angles (radians), the short way round. */
	public float easeAngle(float cur, float target, float rate) {
		if (fresh) {
			return target;
		}
		return cur + Mth.wrapDegrees((target - cur) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * (1f - (float) Math.exp(-rate * dt));
	}

	/** Advances a phase by {@code freq} radians per tick; fresh states start at {@code age * freq} so previews still animate. */
	public float advance(float phase, float freq, float age) {
		if (fresh) {
			return age * freq;
		}
		return phase + freq * dt;
	}
}
