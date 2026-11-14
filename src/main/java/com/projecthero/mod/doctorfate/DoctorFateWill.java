package com.projecthero.mod.doctorfate;

/**
 * v0.17.4 round 2 (user: "the doctor fate space bar minigame to leave doesn't work very well, please improve it"): the willpower
 * test's physics, shared by the server ({@link DoctorFateNabuTrial}, authoritative) and the client (prediction, so the Gauge moves
 * smoothly between packets). Hold Space and the will accelerates up; let go and Nabu pushes it down (harder when angry). Velocity is
 * damped every tick (so it has weight but settles), and the floor and the ceiling bounce it softly.
 */
public final class DoctorFateWill {
	private DoctorFateWill() {
	}

	/** Nabu's downward push this tick at this anger (0-100). */
	public static float sink(float anger) {
		return DoctorFateConfig.WILL_SINK + DoctorFateConfig.WILL_SINK_PER_ANGER * Math.max(0f, anger);
	}

	/** Half the band's width at this anger: wider when he's calm, narrower when he's angry. */
	public static float halfBand(float anger) {
		float a = Math.max(0f, Math.min(1f, anger / DoctorFateConfig.TRIAL_ANGER_MAX));
		return DoctorFateConfig.WILL_BAND + (DoctorFateConfig.WILL_BAND_ANGRY - DoctorFateConfig.WILL_BAND) * a;
	}

	/** One tick: {@code wv} = {will, velocity}, changed in place. */
	public static void step(float[] wv, boolean held, float anger) {
		float v = (wv[1] + (held ? DoctorFateConfig.WILL_RISE : 0f) - sink(anger)) * DoctorFateConfig.WILL_DAMP;
		float w = wv[0] + v;
		if (w < 0f) {
			w = 0f;
			v = -v * DoctorFateConfig.WILL_BOUNCE;
		} else if (w > 1f) {
			w = 1f;
			v = -v * DoctorFateConfig.WILL_BOUNCE;
		}
		wv[0] = w;
		wv[1] = v;
	}

	/** Smoothstep (eased in and out). */
	public static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}
}
