package com.projecthero.mod.stand;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.stand.data.StandState;

import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "upgrade the animations for all the abilities ... make everything look really good ... by bending star platinums
 * limbs"): Star Platinum's body language. Every pose is one flat array ({@link #N} numbers): each arm (shoulder x / y / z + elbow),
 * each leg (hip x / y / z + knee), the torso (bent about the hips), the head on top of the look, and the whole figure (lean, roll,
 * twist and where it lunges to). The elbows and knees are real joints ({@link JointedLimbs}: one continuous skin, split and mitred at
 * the joint), the overlay layers and the glow shell bend with them.
 *
 * <p>Each move is a keyframed {@link Clip}: a short anticipation, a strike that snaps in on an ease-out, a held follow-through and a
 * return. Held moves (the rush, Guard, the Star Breaker charge, a held grab, Precision Sight) are computed. {@link StandRender} then
 * cross-fades the figure toward whatever this gives on a fast per-frame filter, so nothing ever pops from one pose to the next.
 * Negative shoulder / hip {@code x} swings a limb forward and up; a positive elbow or knee folds it. Common code (no client classes), so a gametest can
 * walk every frame ({@link #clearanceProblems}); the client's {@code StandPoses} sets the result on the model.
 */
public final class StandClips {
	/** Pose slots. Arms: x, y, z, elbow. Legs: x, y, z, knee. */
	public static final int RA = 0, LA = 4, RL = 8, LL = 12;
	/** Torso pitch (about the hips), yaw (the shoulders follow), roll. */
	public static final int BODY = 16;
	/** Head pitch and yaw, added to the look. */
	public static final int HEAD = 19;
	/** The whole figure: lean forward, roll, twist (radians, about the waist). */
	public static final int LEAN = 21, ROLL = 22, TWIST = 23;
	/** Blocks: lunge forward, up, to the right. */
	public static final int FWD = 24, UP = 25, SIDE = 26;
	/** How much of the look's pitch each arm follows (0..1). */
	public static final int AIM_R = 27, AIM_L = 28;
	public static final int N = 29;

	private static final float H = Mth.HALF_PI;

	/** A keyframed clip: {@link #at} starts a key from the one before, the setters change it, {@link #snap} eases into it fast. */
	private static final class Clip {
		final float[] times = new float[16];
		final float[][] keys = new float[16][];
		final boolean[] snaps = new boolean[16];
		int count;

		Clip(float[] start) {
			times[0] = 0f;
			keys[0] = start.clone();
			count = 1;
		}

		Clip at(float tick) {
			times[count] = tick;
			keys[count] = keys[count - 1].clone();
			count++;
			return this;
		}

		/** A key that starts over from a whole pose. */
		Clip at(float tick, float[] pose) {
			at(tick);
			System.arraycopy(pose, 0, keys[count - 1], 0, N);
			return this;
		}

		Clip snap() {
			snaps[count - 1] = true;
			return this;
		}

		private float[] k() {
			return keys[count - 1];
		}

		Clip set(int slot, float... v) {
			System.arraycopy(v, 0, k(), slot, v.length);
			return this;
		}

		Clip ra(float x, float y, float z, float e) {
			return set(RA, x, y, z, e);
		}

		Clip la(float x, float y, float z, float e) {
			return set(LA, x, y, z, e);
		}

		Clip rl(float x, float y, float z, float kn) {
			return set(RL, x, y, z, kn);
		}

		Clip ll(float x, float y, float z, float kn) {
			return set(LL, x, y, z, kn);
		}

		Clip body(float pitch, float yaw, float roll) {
			return set(BODY, pitch, yaw, roll);
		}

		Clip head(float pitch, float yaw) {
			return set(HEAD, pitch, yaw);
		}

		Clip lean(float lean, float roll, float twist) {
			return set(LEAN, lean, roll, twist);
		}

		Clip go(float fwd, float up, float side) {
			return set(FWD, fwd, up, side);
		}

		Clip aim(float r, float l) {
			return set(AIM_R, r, l);
		}

		float length() {
			return times[count - 1];
		}

		void sample(float t, float[] out) {
			if (t <= times[0]) {
				System.arraycopy(keys[0], 0, out, 0, N);
				return;
			}
			for (int i = 1; i < count; i++) {
				if (t <= times[i]) {
					float u = (t - times[i - 1]) / Math.max(0.001f, times[i] - times[i - 1]);
					// a strike lands on an ease-out; everything else on a smoothstep
					u = snaps[i] ? 1f - (1f - u) * (1f - u) * (1f - u) : u * u * (3f - 2f * u);
					for (int j = 0; j < N; j++) {
						out[j] = Mth.lerp(u, keys[i - 1][j], keys[i][j]);
					}
					return;
				}
			}
			System.arraycopy(keys[count - 1], 0, out, 0, N);
		}
	}

	// ---------------------------------------------------------------- the two home poses

	/**
	 * At rest: arms down and a little out, fists clenched, one knee drawn up, chin down, floating. (Never folded arms: a forearm is 6 px,
	 * too short to cross the chest, so folded hands meet in the middle and ride up under the chin.)
	 */
	public static final float[] REST = pose(p -> {
		p.ra(-0.22f, 0f, 0.26f, 0.55f).la(-0.1f, 0f, -0.26f, 0.35f);
		p.rl(-0.5f, 0.06f, 0.05f, 0.95f).ll(0.08f, -0.04f, -0.05f, 0.3f);
		p.body(0.04f, 0f, 0f).head(0.12f, 0f);
	});

	/** In front, between moves: fists up in front of the chest, shoulder-width apart and well under the chin, weight forward. */
	public static final float[] STANCE = pose(p -> {
		p.ra(-0.7f, -0.05f, 0.14f, 1.3f).la(-0.92f, 0.05f, -0.14f, 1.15f);
		p.rl(-0.6f, 0.08f, 0.07f, 1.0f).ll(0.2f, -0.06f, -0.06f, 0.4f);
		p.body(0.14f, 0.1f, 0f).head(0.05f, 0f).lean(0.1f, 0f, 0f);
	});

	private static float[] pose(java.util.function.Consumer<Clip> fn) {
		Clip c = new Clip(new float[N]);
		fn.accept(c);
		return c.keys[0];
	}

	// ---------------------------------------------------------------- the clips

	/** A straight right: cocked, the hip turns, the elbow locks out at the target. */
	private static final Clip PUNCH_R = new Clip(STANCE)
			.at(1.5f).ra(-0.45f, -0.1f, 0.2f, 1.5f).la(-0.8f, -0.2f, -0.2f, 1.0f).body(0.1f, 0.4f, 0f)
			.at(3f).snap().ra(-1.42f, 0.42f, 0f, 0.04f).la(-0.5f, 0.3f, -0.2f, 1.4f).body(0.24f, -0.5f, 0f)
			.rl(-0.25f, 0.08f, 0.07f, 0.5f).ll(0.5f, -0.06f, -0.06f, 0.9f).lean(0.26f, 0f, 0f).go(0.4f, 0f, 0f).aim(1f, 0f)
			.at(5f)
			.at(8f, STANCE);

	/** The other hand: {@link #PUNCH_R} in the mirror. */
	private static final Clip PUNCH_L = mirror(PUNCH_R);

	/** The heavy third blow and the ORAAA finisher: a long wind-up, the whole body behind it. */
	private static final Clip HEAVY = new Clip(STANCE)
			.at(3f).ra(0.6f, -0.3f, 0.5f, 1.7f).la(-1.3f, -0.55f, -0.1f, 0.5f).body(-0.04f, 0.75f, 0f).head(0f, -0.4f)
			.rl(-0.75f, 0.1f, 0.1f, 1.3f).ll(0.3f, -0.1f, -0.1f, 0.6f).lean(-0.12f, 0f, 0.2f).go(-0.15f, 0f, 0f)
			.at(5f).snap().ra(-1.42f, 0.62f, 0f, 0f).la(0.5f, 0.3f, -0.35f, 1.5f).body(0.28f, -0.7f, 0f).head(0.05f, 0.3f)
			.rl(0.55f, 0.05f, 0.1f, 0.6f).ll(-0.95f, -0.05f, -0.1f, 1.2f).lean(0.42f, 0f, -0.25f).go(0.75f, 0f, 0f).aim(1f, 0f)
			.at(8f)
			.at(12f, STANCE);

	/** Star Breaker's release: {@link #charging} snaps into a full-stretch punch that drives far forward. */
	private static final Clip BREAKER = new Clip(STANCE)
			.ra(0.75f, -0.4f, 0.55f, 1.7f).la(-1.35f, -0.8f, -0.1f, 0.3f).body(-0.05f, 0.95f, 0f).lean(-0.15f, 0f, 0.3f)
			.at(2.5f).snap().ra(-1.42f, 0.75f, 0f, 0f).la(0.6f, 0.4f, -0.4f, 1.5f).body(0.32f, -0.85f, 0f).head(0.05f, 0.35f)
			.rl(0.75f, 0.05f, 0.12f, 0.4f).ll(-1.1f, -0.05f, -0.12f, 1.4f).lean(0.5f, 0f, -0.3f).go(1.1f, 0.05f, 0f).aim(1f, 0f)
			.at(8f)
			.at(16f, STANCE);

	/** Star Finger: the arm whips out and locks, two fingers extended, the other hand back at the hip. */
	private static final Clip FINGER = new Clip(STANCE)
			.ra(-1.3f, -0.2f, 0.1f, 0.9f).body(0.1f, 0.3f, 0f).aim(1f, 0f)
			.at(2f).snap().ra(-1.42f, 0.36f, 0f, 0f).la(0.4f, 0.35f, -0.3f, 1.4f).body(0.16f, -0.42f, 0f)
			.rl(-0.3f, 0.06f, 0.06f, 0.55f).ll(0.45f, -0.05f, -0.06f, 0.75f).lean(0.2f, 0f, 0f).go(0.2f, 0f, 0f)
			.at(10f)
			.at(14f, STANCE);

	/** Power Grab: a long reach, the fingers closing on it, then the catch drawn in. */
	private static final Clip GRAB = new Clip(STANCE)
			.ra(-0.8f, 0.1f, 0.3f, 1.2f)
			.at(3f).snap().ra(-1.42f, 0.28f, 0.05f, 0.08f).la(-0.85f, 0.3f, -0.14f, 1.0f).body(0.2f, -0.3f, 0f).lean(0.24f, 0f, 0f)
			.go(0.4f, 0f, 0f).aim(1f, 0f)
			.at(6f)
			.at(10f).ra(-1.2f, 0f, 0.12f, 0.35f).la(-1.2f, 0f, -0.12f, 0.35f).body(0.1f, 0f, 0f).lean(0.12f, 0f, 0f).go(0f, 0f, 0f).aim(1f, 1f);

	/** Holding something out in front, a hand either side of it. */
	public static final float[] HOLD = pose(p -> {
		p.ra(-1.2f, 0f, 0.12f, 0.35f).la(-1.2f, 0f, -0.12f, 0.35f).rl(-0.45f, 0.06f, 0.06f, 0.8f).ll(0.15f, -0.05f, -0.06f, 0.35f);
		p.body(0.1f, 0f, 0f).lean(0.1f, 0f, 0f).aim(1f, 1f);
	});

	/** The hurl: wound over the shoulder, the whole body whipping through. */
	private static final Clip THROW = new Clip(HOLD)
			.ra(-2.5f, 0f, -0.6f, 0.7f).la(-1.25f, -0.3f, -0.1f, 0.3f).body(-0.3f, 0.5f, 0f).lean(-0.2f, 0f, 0f).aim(0f, 0f)
			.at(3f).snap().ra(-1.05f, 0.4f, 0.05f, 0.05f).la(0.35f, 0.25f, -0.3f, 1.3f).body(0.42f, -0.55f, 0f)
			.rl(0.4f, 0.06f, 0.08f, 0.5f).ll(-0.8f, -0.05f, -0.08f, 1.1f).lean(0.45f, 0f, 0f).go(0.5f, 0f, 0f)
			.at(6f)
			.at(10f, STANCE);

	/** Ball Bearing Flick: the hand before the chest, the forearm snapping out, a thumb's flick. */
	private static final Clip FLICK = new Clip(STANCE)
			.ra(-0.45f, -0.2f, 0.25f, 1.5f).la(-0.8f, -0.15f, -0.14f, 1.2f).body(0.08f, 0.35f, 0f)
			.at(2f).snap().ra(-1.42f, 0.26f, 0f, 0.05f).la(-0.7f, 0.2f, -0.16f, 1.3f).body(0.14f, -0.3f, 0f).go(0.12f, 0f, 0f).aim(1f, 0f)
			.at(6f)
			.at(10f, STANCE);

	/** Stand Leap: crouched under you, palms up, then the legs and arms drive straight out. */
	private static final Clip LEAP = new Clip(STANCE)
			.ra(-2.6f, 0f, -0.7f, 0.5f).la(-2.6f, 0f, 0.7f, 0.5f).rl(-1.75f, 0.1f, 0.12f, 2.2f).ll(-1.75f, -0.1f, -0.12f, 2.2f)
			.body(0.42f, 0f, 0f).head(-0.3f, 0f).lean(0f, 0f, 0f).go(0f, -0.3f, 0f)
			.at(3f).snap().ra(-2.9f, 0f, -0.4f, 0f).la(-2.9f, 0f, 0.4f, 0f).rl(0.12f, 0.04f, 0.06f, 0.05f)
			.ll(0.2f, -0.04f, -0.06f, 0.2f).body(-0.1f, 0f, 0f).head(-0.5f, 0f).go(0f, 0.5f, 0f)
			.at(7f)
			.at(10f, REST);

	/** Stand Dash: flat out, one fist leading, legs streaming behind. */
	private static final Clip DASH = new Clip(STANCE)
			.at(2f).snap().ra(-2.8f, 0.2f, -0.55f, 0f).la(0.55f, 0f, -0.2f, 0.9f).rl(0.35f, 0.04f, 0.05f, 0.55f).ll(0.1f, -0.04f, -0.05f, 1.3f)
			.body(0.1f, -0.1f, 0f).head(-0.95f, 0f).lean(1.2f, 0f, 0f).go(0.6f, 0.15f, 0f)
			.at(7f)
			.at(10f, STANCE);

	/** ORAAA Uppercut: dropped low, fist at the knee, then the whole body rises through it. */
	private static final Clip UPPERCUT = new Clip(STANCE)
			.at(2f).ra(0.4f, -0.3f, 0.4f, 1.5f).la(-0.9f, -0.3f, -0.14f, 1.1f).rl(-1.3f, 0.12f, 0.1f, 1.9f).ll(-0.2f, -0.1f, -0.1f, 1.5f)
			.body(0.42f, 0.5f, 0f).lean(0.15f, 0f, 0f).go(0.1f, -0.5f, 0f)
			.at(4.5f).snap().ra(-2.8f, 0.4f, -0.6f, 0.1f).la(0.3f, 0.2f, -0.32f, 1.4f).rl(-0.25f, 0.05f, 0.06f, 0.5f)
			.ll(0.35f, -0.05f, -0.06f, 0.9f).body(-0.24f, -0.45f, 0f).head(-0.5f, 0f).lean(-0.08f, 0f, 0f).go(0.4f, 0.6f, 0f)
			.at(8f)
			.at(12f, STANCE);

	/**
	 * The World: fists clenched low and wide through the voice line, tensing, head bowed; on the burst (39 ticks in, as the sound
	 * and the stop land) they are flung wide, the head thrown back.
	 */
	private static final Clip THE_WORLD = new Clip(STANCE)
			.at(6f).ra(-0.25f, 0f, 0.5f, 0.9f).la(-0.25f, 0f, -0.5f, 0.9f).rl(-0.55f, 0.08f, 0.1f, 0.9f).ll(-0.1f, -0.08f, -0.1f, 0.5f)
			.body(0.2f, 0f, 0f).head(0.35f, 0f).lean(0.05f, 0f, 0f)
			.at(34f).ra(-0.35f, 0f, 0.7f, 1.2f).la(-0.35f, 0f, -0.7f, 1.2f).rl(-0.85f, 0.1f, 0.12f, 1.4f).ll(-0.3f, -0.1f, -0.12f, 0.9f)
			.body(0.34f, 0f, 0f).head(0.5f, 0f).go(0f, -0.15f, 0f)
			.at(39.5f).snap().ra(-0.2f, 0f, 1.5f, 0.1f).la(-0.2f, 0f, -1.5f, 0.1f).rl(0.1f, 0.1f, 0.22f, 0.15f).ll(0.1f, -0.1f, -0.22f, 0.15f)
			.body(-0.2f, 0f, 0f).head(-0.65f, 0f).lean(-0.18f, 0f, 0f).go(0f, 0.4f, 0f)
			.at(56f)
			.at(62f, STANCE);

	/** Inhale: the chest swells and the head tips back, arms spread and elbows bent, shoulders drawn up. */
	private static final Clip INHALE = new Clip(STANCE)
			.at(8f).ra(-0.55f, 0.1f, 0.95f, 1.1f).la(-0.55f, -0.1f, -0.95f, 1.1f).rl(-0.3f, 0.1f, 0.14f, 0.6f).ll(-0.05f, -0.1f, -0.14f, 0.3f)
			.body(-0.38f, 0f, 0f).head(-0.75f, 0f).lean(-0.25f, 0f, 0f).go(0f, 0.15f, 0f)
			.at(34f).ra(-0.65f, 0.1f, 1.1f, 1.3f).la(-0.65f, -0.1f, -1.1f, 1.3f).body(-0.46f, 0f, 0f).head(-0.85f, 0f)
			.at(40f, STANCE);

	/** A summon: it unfolds out of a tucked ball, flings its arms open and settles with its arms folding. */
	private static final Clip SUMMON = new Clip(REST)
			.ra(-0.75f, 0f, 0.45f, 0.8f).la(-0.75f, 0f, -0.45f, 0.8f).rl(-1.7f, 0.08f, 0.08f, 2.2f).ll(-1.7f, -0.08f, -0.08f, 2.2f)
			.body(0.65f, 0f, 0f).head(0.55f, 0f).go(0f, -0.4f, 0f)
			.at(9f).snap().ra(-0.35f, 0f, 0.85f, 0.35f).la(-0.35f, 0f, -0.85f, 0.35f).rl(0.05f, 0.05f, 0.12f, 0.2f).ll(0.05f, -0.05f, -0.12f, 0.2f)
			.body(-0.12f, 0f, 0f).head(-0.25f, 0f).go(0f, 0.12f, 0f)
			.at(16f, REST);

	/** A dismissal: it folds back up as it fades. */
	private static final Clip DISMISS = new Clip(REST)
			.at(10f).ra(-0.75f, 0f, 0.45f, 0.8f).la(-0.75f, 0f, -0.45f, 0.8f).rl(-1.7f, 0.08f, 0.08f, 2.2f).ll(-1.7f, -0.08f, -0.08f, 2.2f)
			.body(0.65f, 0f, 0f).head(0.55f, 0f).go(0f, -0.4f, 0f);

	/** A catch: the hand shoots out and closes, pulled back to the chest. */
	private static final Clip CATCH = new Clip(STANCE)
			.at(2f).snap().ra(-1.42f, 0.28f, 0f, 0.15f).body(0.12f, -0.3f, 0f).aim(1f, 0f)
			.at(4f)
			.at(8f, STANCE);

	/** Guard: forearms crossed in an X before the chest (the one pose where the fists do meet), braced, head low over them. */
	public static final float[] GUARD = pose(p -> {
		p.ra(-0.95f, -0.75f, 0.08f, 1.0f).la(-1.0f, 0.75f, -0.08f, 0.85f).rl(-0.8f, 0.1f, 0.08f, 1.3f).ll(-0.05f, -0.08f, -0.08f, 0.7f);
		p.body(0.26f, 0f, 0f).head(0.3f, 0f).lean(0.12f, 0f, 0f).go(0f, -0.08f, 0f);
	});

	/** Precision Sight: leaning over the shoulder, a hand raised beside the temple, the other arm down. */
	public static final float[] SIGHT = pose(p -> {
		p.ra(-0.35f, 0f, 1.05f, 1.75f).la(-0.1f, 0f, -0.26f, 0.35f).rl(-0.5f, 0.06f, 0.05f, 0.95f).ll(0.08f, -0.04f, -0.05f, 0.3f);
		p.body(0.2f, -0.1f, 0f).lean(0.15f, 0f, 0f).go(0.25f, 0.1f, 0f);
	});

	private static float[] mirrorPose(float[] p) {
		float[] m = p.clone();
		for (int side = 0; side < 2; side++) {
			int a = side == 0 ? RA : RL, b = side == 0 ? LA : LL;
			m[a] = p[b];
			m[a + 1] = -p[b + 1];
			m[a + 2] = -p[b + 2];
			m[a + 3] = p[b + 3];
			m[b] = p[a];
			m[b + 1] = -p[a + 1];
			m[b + 2] = -p[a + 2];
			m[b + 3] = p[a + 3];
		}
		m[BODY + 1] = -p[BODY + 1];
		m[BODY + 2] = -p[BODY + 2];
		m[HEAD + 1] = -p[HEAD + 1];
		m[ROLL] = -p[ROLL];
		m[TWIST] = -p[TWIST];
		m[SIDE] = -p[SIDE];
		m[AIM_R] = p[AIM_L];
		m[AIM_L] = p[AIM_R];
		return m;
	}

	private static Clip mirror(Clip c) {
		Clip m = new Clip(mirrorPose(c.keys[0]));
		for (int i = 1; i < c.count; i++) {
			m.at(c.times[i], mirrorPose(c.keys[i]));
			m.snaps[i] = c.snaps[i];
		}
		return m;
	}

	private static Clip clip(int animId) {
		return switch (animId) {
			case StandState.ANIM_SUMMON -> SUMMON;
			case StandState.ANIM_DISMISS -> DISMISS;
			case StandState.ANIM_PUNCH_R -> PUNCH_R;
			case StandState.ANIM_PUNCH_L -> PUNCH_L;
			case StandState.ANIM_PUNCH_HEAVY, StandState.ANIM_RUSH_FINISH -> HEAVY;
			case StandState.ANIM_BREAKER -> BREAKER;
			case StandState.ANIM_FINGER -> FINGER;
			case StandState.ANIM_GRAB -> GRAB;
			case StandState.ANIM_THROW -> THROW;
			case StandState.ANIM_FLICK -> FLICK;
			case StandState.ANIM_LEAP -> LEAP;
			case StandState.ANIM_DASH -> DASH;
			case StandState.ANIM_THE_WORLD -> THE_WORLD;
			case StandState.ANIM_INHALE -> INHALE;
			case StandState.ANIM_UPPERCUT -> UPPERCUT;
			case StandState.ANIM_CATCH -> CATCH;
			default -> null;
		};
	}

	/**
	 * v0.17.2 (user: "make star platinum mirror the users pose but do it behind their back"): nothing of its own is running, so it stands
	 * behind its user copying them (the client works their pose out; these clips only cover the moves).
	 */
	public static boolean mirrors(StandState s, float age, long now) {
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0 || s.inhaleUntil != 0L || s.sightUntil > now) {
			return false;
		}
		Clip one = clip(s.animId);
		return one == null || age < 0f || age >= one.length();
	}

	/** Length of a one-shot animation (ticks; 0 = none). */
	public static int length(int animId) {
		Clip c = clip(animId);
		return c == null ? 0 : (int) Math.ceil(c.length());
	}

	// ---------------------------------------------------------------- what the figure should be doing right now

	/** The wanted pose this frame into {@code out}. */
	public static void target(StandState s, float age, float t, long now, boolean fight, float[] out) {
		Clip one = clip(s.animId);
		boolean playing = one != null && age >= 0f && age < one.length();
		if (s.rushing) {
			rushing(t, out);
		} else if (s.guarding) {
			System.arraycopy(GUARD, 0, out, 0, N);
			out[RA + 3] += 0.05f * Mth.sin(t * 0.5f);
		} else if (s.chargeStart != 0L) {
			charging(Mth.clamp((now - s.chargeStart) / (float) StandConfig.BREAKER_FULL_CHARGE, 0f, 1f), t, out);
		} else if (playing) {
			one.sample(age, out);
		} else if (s.grabbedEntity != -1 || s.grabbedBlock != 0) {
			System.arraycopy(HOLD, 0, out, 0, N);
		} else if (s.sightUntil > now) {
			System.arraycopy(SIGHT, 0, out, 0, N);
		} else {
			System.arraycopy(fight ? STANCE : REST, 0, out, 0, N);
		}
		if (!s.rushing && s.chargeStart == 0L) {
			// a slow breath and a drift, on top of everything: it is never quite still
			float b = Mth.sin(t * 0.09f);
			out[RA + 3] += 0.05f * b;
			out[LA + 3] -= 0.05f * b;
			out[BODY] += 0.015f * b;
			out[RL] += 0.04f * b;
			out[LL] += 0.04f * Mth.sin(t * 0.09f + 1.1f);
		}
	}

	/** The ORA Rush: both fists pistoning, each elbow folding and locking, the shoulders rolling behind them, the body driving in. */
	private static void rushing(float t, float[] out) {
		System.arraycopy(STANCE, 0, out, 0, N);
		float ph = t * 2.4f;
		float r = 0.5f + 0.5f * Mth.sin(ph), l = 0.5f + 0.5f * Mth.sin(ph + Mth.PI);
		out[RA] = -1.3f + 0.12f * Mth.cos(ph * 0.7f);
		out[RA + 1] = Mth.lerp(r, 0.1f, 0.3f);
		out[RA + 2] = 0.08f;
		out[RA + 3] = 1.4f * (1f - r);
		out[LA] = -1.3f + 0.12f * Mth.cos(ph * 0.7f + 2f);
		out[LA + 1] = Mth.lerp(l, -0.1f, -0.3f);
		out[LA + 2] = -0.08f;
		out[LA + 3] = 1.4f * (1f - l);
		out[BODY] = 0.2f;
		out[BODY + 1] = 0.25f * (l - r);
		out[HEAD] = 0.12f;
		out[HEAD + 1] = -0.2f * (l - r);
		out[LEAN] = 0.06f; // upright: the barrage goes straight out, not into the ground
		out[FWD] = 0.25f + 0.05f * Mth.sin(ph * 0.5f);
		out[RL] = 0.05f;
		out[RL + 3] = 0.7f;
		out[LL] = 0.45f;
		out[LL + 3] = 1.1f;
		out[AIM_R] = 1f;
		out[AIM_L] = 1f;
	}

	/** The Star Breaker charge: coiling back further and further, the fist at the hip, the front arm out, shaking with the power. */
	private static void charging(float c, float t, float[] out) {
		System.arraycopy(STANCE, 0, out, 0, N);
		float e = 1f - (1f - c) * (1f - c);
		float shake = 0.03f * c * Mth.sin(t * 5.3f);
		lerpTo(out, RA, e, 0.75f + shake, -0.4f, 0.55f, 1.7f);
		lerpTo(out, LA, e, -1.35f, -0.8f + shake, -0.1f, 0.3f);
		lerpTo(out, RL, e, -1.05f, 0.14f, 0.14f, 1.6f);
		lerpTo(out, LL, e, 0.45f, -0.1f, -0.12f, 0.75f);
		lerpTo(out, BODY, e, -0.05f, 0.95f + shake, 0f);
		lerpTo(out, LEAN, e, -0.15f, shake, 0.3f);
		out[HEAD + 1] = -0.5f * e;
		out[UP] = -0.12f * e;
		out[AIM_L] = e;
	}

	private static void lerpTo(float[] out, int slot, float w, float... v) {
		for (int i = 0; i < v.length; i++) {
			out[slot + i] = Mth.lerp(w, out[slot + i], v[i]);
		}
	}

	// ---------------------------------------------------------------- hands clear of the head

	/** Model px (y down, forward is -z). The head's box half-size, a fist's half-size. */
	private static final float HEAD_PX = 4f, FIST = 2f;
	/** Closer than this, centre to centre, the two fists read as clasped together. */
	public static final float HANDS_APART = 7f;

	/**
	 * v0.17.2 (user: "star platinums hands are usually really close together ... in his face or just in his head a lot of the time"):
	 * forward kinematics of one pose, the same sums the model does: where the fist is, relative to the middle of the head (px).
	 */
	public static float[] fist(float[] p, boolean right) {
		int arm = right ? RA : LA;
		float yaw = p[BODY + 1], lean = p[BODY];
		float c = Mth.cos(lean), s = Mth.sin(lean);
		// HipBend: the neck moves so the hips stay put; the shoulders are 2 px down the torso
		float neckY = 12f * (1f - c), neckZ = -12f * s;
		float side = right ? -1f : 1f;
		float px = side * 5f * Mth.cos(yaw), py = 2f + neckY + 2f * (c - 1f), pz = -side * 5f * Mth.sin(yaw) + neckZ + 2f * s;
		float e = p[arm + 3];
		// the forearm folds about the elbow, 4 px down the arm, and ends 10 px down it
		float lx = side, ly = 4f + 6f * Mth.cos(e), lz = -6f * Mth.sin(e);
		// rotationZYX(z, y, x): x first
		float rx = p[arm], ry = p[arm + 1] + yaw, rz = p[arm + 2];
		float y1 = ly * Mth.cos(rx) - lz * Mth.sin(rx), z1 = ly * Mth.sin(rx) + lz * Mth.cos(rx);
		float x2 = lx * Mth.cos(ry) + z1 * Mth.sin(ry), z2 = -lx * Mth.sin(ry) + z1 * Mth.cos(ry);
		float x3 = x2 * Mth.cos(rz) - y1 * Mth.sin(rz), y3 = x2 * Mth.sin(rz) + y1 * Mth.cos(rz);
		return new float[] { px + x3, py + y3 - (neckY - HEAD_PX), pz + z2 - neckZ };
	}

	/** What is wrong with one pose: a fist in the head, across the face from the front, or the fists clasped; null if nothing. */
	public static String problem(float[] p) {
		return problem(p, false);
	}

	private static String problem(float[] p, boolean clasped) {
		float[] r = fist(p, true), l = fist(p, false);
		for (float[] h : new float[][] { r, l }) {
			String which = h == r ? "right" : "left";
			if (Math.abs(h[0]) < HEAD_PX + FIST && Math.abs(h[1]) < HEAD_PX + FIST && Math.abs(h[2]) < HEAD_PX + FIST) {
				return which + " fist in the head at " + xyz(h);
			}
			// seen from the front: over the face, however far out in front of it
			if (Math.abs(h[0]) < HEAD_PX + 1f && Math.abs(h[1]) < HEAD_PX + 1.5f && h[2] < 0f) {
				return which + " fist over the face at " + xyz(h);
			}
		}
		if (clasped) {
			return null;
		}
		float dx = r[0] - l[0], dy = r[1] - l[1], dz = r[2] - l[2];
		float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		return d < HANDS_APART ? "fists together, " + d + " px apart, at " + xyz(r) : null;
	}

	private static String xyz(float[] h) {
		return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", h[0], h[1], h[2]);
	}

	/** Every clip's frames, a quarter tick apart, and every held pose: one line per thing wrong. */
	public static List<String> clearanceProblems() {
		List<String> out = new ArrayList<>();
		String[] names = { "summon", "punch", "punch left", "heavy", "dismiss", "finger", "grab", "throw", "flick", "leap", "dash", "breaker", "the world", "inhale",
				"uppercut", "catch" };
		int[] ids = { StandState.ANIM_SUMMON, StandState.ANIM_PUNCH_R, StandState.ANIM_PUNCH_L, StandState.ANIM_PUNCH_HEAVY, StandState.ANIM_DISMISS,
				StandState.ANIM_FINGER, StandState.ANIM_GRAB, StandState.ANIM_THROW, StandState.ANIM_FLICK, StandState.ANIM_LEAP, StandState.ANIM_DASH,
				StandState.ANIM_BREAKER, StandState.ANIM_THE_WORLD, StandState.ANIM_INHALE, StandState.ANIM_UPPERCUT, StandState.ANIM_CATCH };
		float[] p = new float[N];
		for (int i = 0; i < ids.length; i++) {
			Clip c = clip(ids[i]);
			for (float t = 0f; t <= c.length(); t += 0.25f) {
				c.sample(t, p);
				String bad = problem(p);
				if (bad != null) {
					out.add(names[i] + " @" + t + ": " + bad);
				}
			}
		}
		for (Object[] held : new Object[][] { { "rest", REST }, { "stance", STANCE }, { "hold", HOLD }, { "guard", GUARD }, { "sight", SIGHT } }) {
			String bad = problem((float[]) held[1], held[1] == GUARD);
			if (bad != null) {
				out.add(held[0] + ": " + bad);
			}
		}
		for (float t = 0f; t < 20f; t += 0.13f) {
			rushing(t, p);
			String bad = problem(p);
			if (bad != null) {
				out.add("rush @" + t + ": " + bad);
			}
			charging(Math.min(1f, t / 15f), t, p);
			bad = problem(p);
			if (bad != null) {
				out.add("charge @" + t + ": " + bad);
			}
		}
		return out;
	}
}
