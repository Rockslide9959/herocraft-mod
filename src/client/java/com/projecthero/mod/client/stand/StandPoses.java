package com.projecthero.mod.client.stand;

import org.joml.Quaternionf;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.data.StandPoseState;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
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
 * Negative shoulder / hip {@code x} swings a limb forward and up; a positive elbow or knee folds it. Set straight on the Stand's own
 * model (never through {@code setupAnim}, so no player pose mixin leaks onto it).
 */
public final class StandPoses {
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

	/** At rest: arms folded on the chest, one knee drawn up, chin down, floating. */
	static final float[] REST = pose(p -> {
		p.ra(-0.42f, -0.98f, 0.14f, 1.8f).la(-0.3f, 0.98f, -0.14f, 1.9f);
		p.rl(-0.5f, 0.06f, 0.05f, 0.95f).ll(0.08f, -0.04f, -0.05f, 0.3f);
		p.body(0.04f, 0f, 0f).head(0.12f, 0f);
	});

	/** In front, between moves: a boxer's guard, fists at the chin, weight forward. */
	static final float[] STANCE = pose(p -> {
		p.ra(-1.2f, -0.45f, 0.1f, 2.0f).la(-1.05f, 0.42f, -0.1f, 1.85f);
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
			.at(1.5f).ra(-0.75f, 0.2f, 0.2f, 2.3f).la(-1.2f, 0.35f, -0.1f, 1.7f).body(0.1f, 0.4f, 0f)
			.at(3f).snap().ra(-H, -0.08f, 0f, 0.04f).la(-0.75f, 0.55f, -0.15f, 2.2f).body(0.24f, -0.5f, 0f)
			.rl(-0.25f, 0.08f, 0.07f, 0.5f).ll(0.5f, -0.06f, -0.06f, 0.9f).lean(0.26f, 0f, 0f).go(0.4f, 0f, 0f).aim(1f, 0f)
			.at(5f)
			.at(8f, STANCE);

	/** The other hand: {@link #PUNCH_R} in the mirror. */
	private static final Clip PUNCH_L = mirror(PUNCH_R);

	/** The heavy third blow and the ORAAA finisher: a long wind-up, the whole body behind it. */
	private static final Clip HEAVY = new Clip(STANCE)
			.at(3f).ra(0.55f, 0.3f, 0.45f, 2.4f).la(-1.45f, 0.1f, 0f, 0.5f).body(-0.04f, 0.75f, 0f).head(0f, -0.4f)
			.rl(-0.75f, 0.1f, 0.1f, 1.3f).ll(0.3f, -0.1f, -0.1f, 0.6f).lean(-0.12f, 0f, 0.2f).go(-0.15f, 0f, 0f)
			.at(5f).snap().ra(-H, -0.04f, 0f, 0f).la(0.55f, 0f, -0.3f, 1.9f).body(0.28f, -0.7f, 0f).head(0.05f, 0.3f)
			.rl(0.55f, 0.05f, 0.1f, 0.6f).ll(-0.95f, -0.05f, -0.1f, 1.2f).lean(0.42f, 0f, -0.25f).go(0.75f, 0f, 0f).aim(1f, 0f)
			.at(8f)
			.at(12f, STANCE);

	/** Star Breaker's release: {@link #charging} snaps into a full-stretch punch that drives far forward. */
	private static final Clip BREAKER = new Clip(STANCE)
			.ra(0.85f, 0.35f, 0.55f, 2.5f).la(-1.5f, -0.1f, 0f, 0.3f).body(-0.05f, 0.95f, 0f).lean(-0.15f, 0f, 0.3f)
			.at(2.5f).snap().ra(-H, 0f, 0f, 0f).la(0.7f, 0f, -0.35f, 2.0f).body(0.32f, -0.85f, 0f).head(0.05f, 0.35f)
			.rl(0.75f, 0.05f, 0.12f, 0.4f).ll(-1.1f, -0.05f, -0.12f, 1.4f).lean(0.5f, 0f, -0.3f).go(1.1f, 0.05f, 0f).aim(1f, 0f)
			.at(8f)
			.at(16f, STANCE);

	/** Star Finger: the arm whips out and locks, two fingers extended, the other hand back at the hip. */
	private static final Clip FINGER = new Clip(STANCE)
			.ra(-1.45f, -0.05f, 0f, 0.9f).body(0.1f, 0.3f, 0f).aim(1f, 0f)
			.at(2f).snap().ra(-H, 0f, 0f, 0f).la(0.45f, 0.2f, -0.28f, 1.6f).body(0.16f, -0.42f, 0f)
			.rl(-0.3f, 0.06f, 0.06f, 0.55f).ll(0.45f, -0.05f, -0.06f, 0.75f).lean(0.2f, 0f, 0f).go(0.2f, 0f, 0f)
			.at(10f)
			.at(14f, STANCE);

	/** Power Grab: a long reach, the fingers closing on it, then the catch drawn in. */
	private static final Clip GRAB = new Clip(STANCE)
			.ra(-1.0f, 0.2f, 0.35f, 1.5f)
			.at(3f).snap().ra(-1.6f, -0.02f, 0f, 0.08f).la(-1.1f, 0.3f, -0.05f, 1.2f).body(0.2f, -0.3f, 0f).lean(0.24f, 0f, 0f)
			.go(0.4f, 0f, 0f).aim(1f, 0f)
			.at(6f)
			.at(10f).ra(-1.5f, -0.1f, 0f, 0.4f).la(-1.3f, 0.3f, 0f, 0.95f).body(0.1f, 0f, 0f).lean(0.12f, 0f, 0f).go(0f, 0f, 0f).aim(1f, 1f);

	/** Holding something up in front, the other hand steadying it. */
	static final float[] HOLD = pose(p -> {
		p.ra(-1.5f, -0.1f, 0f, 0.4f).la(-1.3f, 0.3f, 0f, 0.95f).rl(-0.45f, 0.06f, 0.06f, 0.8f).ll(0.15f, -0.05f, -0.06f, 0.35f);
		p.body(0.1f, 0f, 0f).lean(0.1f, 0f, 0f).aim(1f, 1f);
	});

	/** The hurl: wound over the shoulder, the whole body whipping through. */
	private static final Clip THROW = new Clip(HOLD)
			.ra(-2.9f, 0.25f, 0.15f, 2.0f).la(-1.5f, 0.1f, 0f, 0.3f).body(-0.3f, 0.5f, 0f).lean(-0.2f, 0f, 0f).aim(0f, 0f)
			.at(3f).snap().ra(-1.0f, -0.12f, 0f, 0.05f).la(0.35f, 0f, -0.3f, 1.3f).body(0.42f, -0.55f, 0f)
			.rl(0.4f, 0.06f, 0.08f, 0.5f).ll(-0.8f, -0.05f, -0.08f, 1.1f).lean(0.45f, 0f, 0f).go(0.5f, 0f, 0f)
			.at(6f)
			.at(10f, STANCE);

	/** Ball Bearing Flick: the hand before the chest, the forearm snapping out, a thumb's flick. */
	private static final Clip FLICK = new Clip(STANCE)
			.ra(-1.35f, -0.4f, 0.05f, 2.1f).la(-0.95f, 0.35f, 0f, 1.5f).body(0.08f, 0.35f, 0f)
			.at(2f).snap().ra(-H, 0f, 0f, 0.05f).la(-0.85f, 0.45f, 0f, 2.1f).body(0.14f, -0.3f, 0f).go(0.12f, 0f, 0f).aim(1f, 0f)
			.at(6f)
			.at(10f, STANCE);

	/** Stand Leap: crouched under you, palms up, then the legs and arms drive straight out. */
	private static final Clip LEAP = new Clip(STANCE)
			.ra(-2.9f, -0.12f, 0.3f, 1.9f).la(-2.9f, 0.12f, -0.3f, 1.9f).rl(-1.75f, 0.1f, 0.12f, 2.2f).ll(-1.75f, -0.1f, -0.12f, 2.2f)
			.body(0.42f, 0f, 0f).head(-0.3f, 0f).lean(0f, 0f, 0f).go(0f, -0.3f, 0f)
			.at(3f).snap().ra(-3.05f, -0.05f, 0.14f, 0f).la(-3.05f, 0.05f, -0.14f, 0f).rl(0.12f, 0.04f, 0.06f, 0.05f)
			.ll(0.2f, -0.04f, -0.06f, 0.2f).body(-0.1f, 0f, 0f).head(-0.5f, 0f).go(0f, 0.5f, 0f)
			.at(7f)
			.at(10f, REST);

	/** Stand Dash: flat out, one fist leading, legs streaming behind. */
	private static final Clip DASH = new Clip(STANCE)
			.at(2f).snap().ra(-3.0f, 0f, 0.05f, 0f).la(0.55f, 0f, -0.2f, 0.9f).rl(0.35f, 0.04f, 0.05f, 0.55f).ll(0.1f, -0.04f, -0.05f, 1.3f)
			.body(0.1f, -0.1f, 0f).head(-0.95f, 0f).lean(1.2f, 0f, 0f).go(0.6f, 0.15f, 0f)
			.at(7f)
			.at(10f, STANCE);

	/** ORAAA Uppercut: dropped low, fist at the knee, then the whole body rises through it. */
	private static final Clip UPPERCUT = new Clip(STANCE)
			.at(2f).ra(0.4f, 0.3f, 0.35f, 2.1f).la(-1.2f, 0.45f, -0.1f, 1.9f).rl(-1.3f, 0.12f, 0.1f, 1.9f).ll(-0.2f, -0.1f, -0.1f, 1.5f)
			.body(0.42f, 0.5f, 0f).lean(0.15f, 0f, 0f).go(0.1f, -0.5f, 0f)
			.at(4.5f).snap().ra(-3.0f, -0.05f, 0.12f, 0.1f).la(0.3f, 0f, -0.32f, 1.7f).rl(-0.25f, 0.05f, 0.06f, 0.5f)
			.ll(0.35f, -0.05f, -0.06f, 0.9f).body(-0.24f, -0.45f, 0f).head(-0.5f, 0f).lean(-0.08f, 0f, 0f).go(0.4f, 0.6f, 0f)
			.at(8f)
			.at(12f, STANCE);

	/**
	 * The World: arms locked across the chest through the voice line, tensing, head bowed; on the burst (39 ticks in, as the sound
	 * and the stop land) they are flung wide, the head thrown back.
	 */
	private static final Clip THE_WORLD = new Clip(STANCE)
			.at(6f).ra(-1.1f, -0.85f, 0.05f, 1.75f).la(-1.1f, 0.85f, -0.05f, 1.75f).rl(-0.55f, 0.08f, 0.1f, 0.9f).ll(-0.1f, -0.08f, -0.1f, 0.5f)
			.body(0.2f, 0f, 0f).head(0.35f, 0f).lean(0.05f, 0f, 0f)
			.at(34f).ra(-1.2f, -0.95f, 0.05f, 2.0f).la(-1.2f, 0.95f, -0.05f, 2.0f).rl(-0.85f, 0.1f, 0.12f, 1.4f).ll(-0.3f, -0.1f, -0.12f, 0.9f)
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
			.ra(-1.7f, -0.7f, 0f, 2.3f).la(-1.7f, 0.7f, 0f, 2.3f).rl(-1.7f, 0.08f, 0.08f, 2.2f).ll(-1.7f, -0.08f, -0.08f, 2.2f)
			.body(0.65f, 0f, 0f).head(0.55f, 0f).go(0f, -0.4f, 0f)
			.at(9f).snap().ra(-0.35f, 0f, 0.85f, 0.35f).la(-0.35f, 0f, -0.85f, 0.35f).rl(0.05f, 0.05f, 0.12f, 0.2f).ll(0.05f, -0.05f, -0.12f, 0.2f)
			.body(-0.12f, 0f, 0f).head(-0.25f, 0f).go(0f, 0.12f, 0f)
			.at(16f, REST);

	/** A dismissal: it folds back up as it fades. */
	private static final Clip DISMISS = new Clip(REST)
			.at(10f).ra(-1.7f, -0.7f, 0f, 2.3f).la(-1.7f, 0.7f, 0f, 2.3f).rl(-1.7f, 0.08f, 0.08f, 2.2f).ll(-1.7f, -0.08f, -0.08f, 2.2f)
			.body(0.65f, 0f, 0f).head(0.55f, 0f).go(0f, -0.4f, 0f);

	/** A catch: the hand shoots out and closes, pulled back to the chest. */
	private static final Clip CATCH = new Clip(STANCE)
			.at(2f).snap().ra(-1.75f, -0.1f, 0f, 0.15f).body(0.12f, -0.3f, 0f).aim(1f, 0f)
			.at(4f)
			.at(8f, STANCE);

	/** Guard: forearms crossed in an X before the face, braced, head down behind them. */
	static final float[] GUARD = pose(p -> {
		p.ra(-1.3f, -0.8f, 0.08f, 1.55f).la(-1.3f, 0.8f, -0.08f, 1.55f).rl(-0.8f, 0.1f, 0.08f, 1.3f).ll(-0.05f, -0.08f, -0.08f, 0.7f);
		p.body(0.26f, 0f, 0f).head(0.3f, 0f).lean(0.12f, 0f, 0f).go(0f, -0.08f, 0f);
	});

	/** Precision Sight: leaning over the shoulder, a hand shading its eyes. */
	static final float[] SIGHT = pose(p -> {
		p.ra(-2.3f, -0.55f, 0.2f, 2.05f).la(-0.4f, 0.98f, -0.14f, 1.9f).rl(-0.5f, 0.06f, 0.05f, 0.95f).ll(0.08f, -0.04f, -0.05f, 0.3f);
		p.body(0.2f, -0.1f, 0f).lean(0.15f, 0f, 0f).go(0.25f, 0.1f, 0f);
	});

	/**
	 * The N pose, struck along with its user (docs/reference/jotaro_pose): looming behind the left shoulder, leaning in over them, the
	 * right arm raised high in a fist and the left fist drawn back at the chest, glaring down.
	 */
	private static final float[] POSE_POINT = pose(p -> {
		p.ra(-0.5f, 0f, 2.4f, 0.3f).la(-1.2f, 0.25f, -0.15f, 2.0f).rl(-0.45f, 0.08f, 0.1f, 0.7f).ll(0.15f, -0.06f, -0.08f, 0.25f);
		p.body(0.3f, 0.32f, 0f).head(0.34f, -0.18f).lean(0.25f, -0.06f, 0f).go(0.05f, 0.1f, -1.5f);
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

	/** Length of a one-shot animation (ticks; 0 = none). */
	static int length(int animId) {
		Clip c = clip(animId);
		return c == null ? 0 : (int) Math.ceil(c.length());
	}

	// ---------------------------------------------------------------- what the figure should be doing right now

	/** The wanted pose this frame into {@code out}. */
	static void target(StandState s, StandPoseState userPose, float age, float t, long now, boolean fight, float[] out) {
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
		} else if (userPose != null && userPose.pose() != 0) {
			System.arraycopy(POSE_POINT, 0, out, 0, N);
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
		out[RA] = -H + 0.2f * Mth.cos(ph * 0.7f);
		out[RA + 1] = Mth.lerp(r, -0.05f, -0.5f);
		out[RA + 2] = 0.05f;
		out[RA + 3] = 2.1f * (1f - r);
		out[LA] = -H + 0.2f * Mth.cos(ph * 0.7f + 2f);
		out[LA + 1] = Mth.lerp(l, 0.05f, 0.5f);
		out[LA + 2] = -0.05f;
		out[LA + 3] = 2.1f * (1f - l);
		out[BODY] = 0.2f;
		out[BODY + 1] = 0.42f * (l - r);
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
		lerpTo(out, RA, e, 0.85f + shake, 0.35f, 0.55f, 2.5f);
		lerpTo(out, LA, e, -1.5f, -0.1f + shake, 0f, 0.3f);
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

	// ---------------------------------------------------------------- onto the model

	/** The pose {@code p} on the Stand's model, with the look's {@code lookPitch} / {@code lookYaw} (radians) and its joints. */
	static void apply(PlayerModel<?> m, float[] p, float lookPitch, float lookYaw) {
		part(m.rightArm, p, RA);
		part(m.leftArm, p, LA);
		m.rightArm.xRot += p[AIM_R] * lookPitch;
		m.leftArm.xRot += p[AIM_L] * lookPitch;
		part(m.rightLeg, p, RL);
		part(m.leftLeg, p, LL);
		m.head.xRot = lookPitch + p[HEAD];
		m.head.yRot = lookYaw + p[HEAD + 1];
		// the shoulders go round with the torso (as vanilla's attack swing moves them)
		float yaw = p[BODY + 1];
		m.body.yRot = yaw;
		m.body.zRot = p[BODY + 2];
		m.rightArm.z = Mth.sin(yaw) * 5f;
		m.rightArm.x = -Mth.cos(yaw) * 5f;
		m.leftArm.z = -Mth.sin(yaw) * 5f;
		m.leftArm.x = Mth.cos(yaw) * 5f;
		m.rightArm.yRot += yaw;
		m.leftArm.yRot += yaw;
		m.head.yRot += yaw * 0.5f;
		HipBend.pitch(m, 1f, p[BODY]);
		joint(JointedLimbs.RIGHT_ARM, -p[RA + 3]);
		joint(JointedLimbs.LEFT_ARM, -p[LA + 3]);
		joint(JointedLimbs.RIGHT_LEG, p[RL + 3]);
		joint(JointedLimbs.LEFT_LEG, p[LL + 3]);
	}

	/** The elbow / knee of {@code limb} folded by {@code angle} (x radians in the limb's own frame). */
	static void joint(int limb, float angle) {
		JointedLimbs.setJoint(limb, Math.abs(angle) > 0.002f ? new Quaternionf().rotationX(angle) : null);
	}

	private static void part(ModelPart part, float[] p, int slot) {
		part.xRot = p[slot];
		part.yRot = p[slot + 1];
		part.zRot = p[slot + 2];
	}

	static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
