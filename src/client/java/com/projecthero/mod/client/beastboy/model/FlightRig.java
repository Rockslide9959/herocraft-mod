package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: the flight state the pterodactyl, hawk and bat share, eased in their {@link MotionState}: how far into the
 * air (take-off / landing blend), wing-beat phase and strength tied to climb and speed (hard beats climbing or hovering, lighter when
 * cruising, a glide when sinking), bank into turns and body pitch from the look and the climb.
 */
final class FlightRig {
	private FlightRig() {
	}

	/**
	 * @param baseFreq wing beats in radians per tick at full strength
	 * @param bursts   0..1 how much a fast level cruise turns into flap-and-glide bursts (hawks), 0 = steady
	 */
	static void update(MotionState st, BeastPose s, boolean forcedAir, float baseFreq, float bursts) {
		float airT = s.airborne || s.inWater || forcedAir ? 1f : 0f;
		st.air = st.ease(st.air, airT, airT > st.air ? 0.3f : 0.16f);
		st.climb = st.ease(st.climb, Mth.clamp(s.vy * 6f, -1f, 1f), 0.2f);
		st.speed = st.ease(st.speed, s.walkAmt, 0.15f);
		float amp = Mth.clamp(0.55f + st.climb * 0.6f + (1f - st.speed) * 0.35f, 0.05f, 1f);
		if (st.climb < -0.3f) {
			amp *= Mth.clamp(1f + (st.climb + 0.3f) * 2.6f, 0.06f, 1f); // sinking: glide
		}
		if (bursts > 0f && st.speed > 0.5f && Math.abs(st.climb) < 0.35f) {
			float b = Math.max(0f, Mth.sin(s.age * 0.045f));
			amp *= Mth.lerp(bursts, 1f, 0.2f + 0.8f * b);
		}
		st.flapAmp = st.ease(st.flapAmp, amp * st.air, 0.09f);
		st.glide = 1f - Mth.clamp(st.flapAmp / 0.6f, 0f, 1f);
		st.flapPhase = st.advance(st.flapPhase, baseFreq * (0.75f + 0.45f * st.flapAmp), s.age);
		st.bank = st.ease(st.bank, Mth.clamp(s.tailLag * 2.0f, -0.7f, 0.7f) * st.air, 0.12f);
		st.pitch = st.ease(st.pitch, Mth.clamp(s.headPitch * 0.45f - st.climb * 0.25f, -0.5f, 0.7f) * st.air, 0.1f);
	}

	/**
	 * The stroke at a phase: {lift (+ = wings up), fold 0..1 (partly folded on the upstroke)}. The downstroke is the faster, longer
	 * power half; the amplitude scales both.
	 */
	static float[] stroke(float phase, float amp) {
		float p = phase + 0.28f * Mth.sin(phase);
		float lift = Mth.cos(p) * amp;
		float fold = Math.max(0f, -Mth.sin(p)) * amp;
		return new float[] { lift, fold };
	}
}
