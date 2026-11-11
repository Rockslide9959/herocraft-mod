package com.projecthero.mod.client.omnitrix.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.2 (user: "this is the fourarms transformation from the ben 10 show, can you try and recreate this in minecraft please, do your
 * absolute best ... use vfx and try not to use particles too much"), on the v0.17.3 framework: the episode's Four Arms transformation
 * shot for shot, cut to its own soundtrack.
 *
 * <p>v0.17.3 remake (user: "remake the fourarms transformation sequence please, you may bend the limbs, match the minecraft characters
 * posing with the posing in the video please, match it as close as you can, dont break the body or make limbs detatch"): every limb
 * bends ({@link BentLimbs}: Ben's elbows and knees, then all four of Four Arms' elbows and his knees), and the body runs on one keyframed
 * pose track ({@link #POSE}, eased with Catmull-Rom curves through the keys, held where the show holds, snapped on the cuts) matched
 * frame by frame to the compilation video. Video second {@code v} is tick {@code (v - 132.585) * 20} (the user's own clip:
 * {@code (v - 2.55) * 20}); the beats (ticks after the right-click):
 * <pre>
 *   tick     video s   shot / what the body does
 *   0-5      132.59    front medium: left forearm across the belly (watch up), right arm flung up, elbow bent, and slapping down
 *   4-18     132.8     FULL GREEN FLASH
 *   14-29    133.3     the dial close-up, green light bursting off it; the right hand lifting off the dial
 *   29-49    134.0     the hand swells (peach flesh, red veins) -- the forearm still across the belly, trembling; right hand pulls away
 *   49-74    135.0     up the forearm, the shoulder; the arm lifts and the shoulders hunch
 *   74-89    136.3     the face from low on his left: head tilted hard over, chin up, shoulders up round the neck
 *   87-103   136.9     GOLDEN FLASH -- the body changes under it (95)
 *   101-112  137.6     from behind, low: elbows out, forearms up and clawing (a double-biceps from the back), hunched;
 *                      the second pair splits out of the first from 104
 *   112-121  138.2     the camera orbits round his left: the four arms clawing out at all angles, the body twisting
 *   121-128  138.6     round to the front, low: upper arms out level, forearms forward; lower pair under them; head down
 *   128-136  139.0     the arms sweep in past the lens; whip pan to the head
 *   136-150  139.4     the red head close, from above: bowed, glaring up, the shoulders hunched up
 *   150-166  140.1     the head lifts and throws back -- the camera drops below him -- the roar
 *   166-177  140.9     low-angle torso from the right front, still roaring, the arms flexing
 *   177-182  141.4     full body from his right: crouched, all four arms gathered in (the wind-up)
 *   182-191  141.7     the lunge: leaning in to the right of frame, all four fists driven forward, front knee deep
 *   191-200  142.1     rising and turning toward us into the stance
 *   200-249  142.6     the power stance: horse stance, knees out; top fists curled up by his head, lower fists forward; roaring up
 *   207-216  143.0     WHITE FLASH, then the same stance on the green ray burst (211)
 *   236-249  144.4     FULL GREEN FLASH (the stance eases to standing under it)
 *   249-261  145.0     back in the world, the green fading off
 * </pre>
 * The swelling and the poses are on the body, so everyone nearby sees those, plus the dial's light, a golden glow and the usual green
 * burst at the end; the music plays for them too. Everything is flat-colour quads (no particles).
 */
public final class FourArmsShow extends ShowSequenceClient {
	/** The beats (ticks). */
	static final float SLAP_END = 5f, WATCH_AT = 14f, HAND_AT = 29f, ARM_AT = 49f, FACE_AT = 74f, GOLD_AT = 87f, BACK_AT = 101f, WHIP_AT = 128f,
			HEAD_AT = 136f, ROAR_AT = 150f, TORSO_AT = 166f, POSE_AT = 177f, WHITE_AT = 207f, BURST_STAGE_AT = 211f, GREEN2_AT = 236f, WORLD_AT = 249f;
	static final int TRANSFORM_AT = 95;

	/** Colours: the olive stage, the green ray burst. */
	// v0.17.3: the shared show green (Stage.SHOW_*) -- every sequence uses the same background now
	private static final int OLIVE_IN = Stage.SHOW_IN, OLIVE_MID = Stage.SHOW_MID, OLIVE_OUT = Stage.SHOW_OUT;
	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;

	/** The Kirby dots: the olive field's streams; the stance's (a wider clear middle round the whole body); the burst's, dark green. */
	private static final KirbyDots.Style OLIVE_DOTS = KirbyDots.Style.show(),
			STANCE_DOTS = KirbyDots.Style.show().clear(0.42f),
			BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).clear(0.5f).flow(1.6f);

	/** The camera (see {@link CameraTrack} for the key layout). */
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(5.01f, HAND_AT),
			new float[][] {
					// the hand swelling, from their left front, looking down the forearm across the belly, sliding up the arm
					{ 29.01f, 0.66f, -45f, 1.25f, 1.13f, -0.02f, 0.26f },
					{ 49f, 0.8f, -60f, 1.32f, 1.2f, 0.03f, 0.18f },
					{ 74f, 0.95f, -78f, 1.55f, 1.36f, -0.2f, 0f },
					// the face, from low on his left
					{ 74.01f, 0.8f, -32f, 1.66f, 1.6f, -0.03f, 0f },
					{ 101f, 0.7f, -28f, 1.68f, 1.62f, -0.03f, 0f },
					// from behind, low, then orbiting round his left to the front (angles past 180 run on round: one continuous sweep)
					{ 101.01f, 2.2f, 188f, 0.62f, 1.22f, 0f, 0f },
					{ 112f, 2.05f, 205f, 0.64f, 1.24f, 0f, 0f },
					{ 121f, 1.85f, 268f, 0.66f, 1.25f, 0f, 0f },
					{ 125f, 1.65f, 338f, 0.55f, 1.24f, 0f, 0f },
					{ 128f, 1.45f, 345f, 0.55f, 1.26f, 0f, 0f },
					// the arms sweeping past the lens, the whip up to the head (the angle runs straight, so it sweeps)
					{ 132f, 0.6f, 352f, 1.35f, 1.45f, 0f, 0f },
					// the head from above the brow: bowed, glaring up
					{ 136f, 0.85f, 360f, 1.72f, 1.55f, 0f, 0f },
					{ 150f, 0.8f, 360f, 1.7f, 1.56f, 0f, 0f },
					// the head throws back, the camera drops under it: the roar
					{ 156f, 0.9f, 362f, 1.2f, 1.62f, 0f, 0f },
					{ 166f, 0.85f, 361f, 1.18f, 1.64f, 0f, 0f },
					// low-angle torso from his right front
					{ 166.01f, 1.1f, 18f, 0.75f, 1.6f, 0f, 0f },
					{ 177f, 1.0f, 22f, 0.72f, 1.58f, 0f, 0f },
					// the full body from his right (he faces the right of frame): the wind-up and the lunge; turning toward us for the stance
					{ 177.01f, 2.05f, 80f, 0.5f, 0.95f, 0f, 0f },
					{ 191f, 2.1f, 76f, 0.52f, 0.98f, 0f, 0f },
					{ 201f, 2.15f, 42f, 0.5f, 1.0f, 0f, 0f },
					{ 249f, 2.05f, 38f, 0.52f, 1.02f, 0f, 0f } }))
			// the whip pan runs at speed through its middle key; the roar shakes the shot a little
			.linear(WHIP_AT, HEAD_AT).shake(ROAR_AT + 4, TORSO_AT, 0.012f, 18f);

	public FourArmsShow() {
		super(Aliens.FOUR_ARMS.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 238;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		// a slow push on the dial, the roar punching in
		double fov = 62;
		if (t >= WATCH_AT && t < HAND_AT) {
			fov -= 8 * smooth((t - WATCH_AT) / 15f);
		} else if (t >= ROAR_AT + 2 && t < TORSO_AT) {
			fov -= 7 * smooth((t - ROAR_AT - 2) / 5f);
		}
		return fov;
	}

	// ================================================================ the pose track

	/** Channels of the pose track (radians unless noted). Arms: x, y, z rotation, elbow flex, twist; legs the same with the knee. */
	static final int HX = 1, HY = 2, HZ = 3, LEAN = 4, DROP = 5, SHRUG = 6, SPLIT = 7,
			RA = 8, LA = 13, RL = 18, LL = 23, QR = 28, QL = 33, N = 38;

	/**
	 * Keyframes, one row per key: {tick, channels...}. A key under 0.05 ticks after the one before it is a cut (snaps); {@code hold()}
	 * keys ease in and out (the curve stops on them); the rest flow through (Catmull-Rom).
	 */
	static final class Track {
		final List<float[]> rows = new ArrayList<>();
		final List<Boolean> holds = new ArrayList<>();

		Track at(float tick) {
			float[] r = rows.isEmpty() ? new float[N] : rows.get(rows.size() - 1).clone();
			r[0] = tick;
			rows.add(r);
			holds.add(false);
			return this;
		}

		private float[] cur() {
			return rows.get(rows.size() - 1);
		}

		Track hold() {
			holds.set(holds.size() - 1, true);
			return this;
		}

		Track set(int ch, float... v) {
			System.arraycopy(v, 0, cur(), ch, v.length);
			return this;
		}

		Track head(float x, float y, float z) {
			return set(HX, x, y, z);
		}

		Track lean(float v) {
			return set(LEAN, v);
		}

		Track drop(float px) {
			return set(DROP, px);
		}

		Track shrug(float px) {
			return set(SHRUG, px);
		}

		Track split(float v) {
			return set(SPLIT, v);
		}

		Track rArm(float x, float y, float z, float flex, float twist) {
			return set(RA, x, y, z, flex, twist);
		}

		Track lArm(float x, float y, float z, float flex, float twist) {
			return set(LA, x, y, z, flex, twist);
		}

		/** Both arms, the left mirroring the right. */
		Track arms(float x, float y, float z, float flex, float twist) {
			return rArm(x, y, z, flex, twist).lArm(x, -y, -z, flex, -twist);
		}

		Track rLeg(float x, float y, float z, float knee, float twist) {
			return set(RL, x, y, z, knee, twist);
		}

		Track lLeg(float x, float y, float z, float knee, float twist) {
			return set(LL, x, y, z, knee, twist);
		}

		Track legs(float x, float y, float z, float knee, float twist) {
			return rLeg(x, y, z, knee, twist).lLeg(x, -y, -z, knee, -twist);
		}

		/** Four Arms' lower pair (body space; the right one, the left one, both mirrored). */
		Track lowR(float x, float y, float z, float flex, float twist) {
			return set(QR, x, y, z, flex, twist);
		}

		Track lowL(float x, float y, float z, float flex, float twist) {
			return set(QL, x, y, z, flex, twist);
		}

		Track lows(float x, float y, float z, float flex, float twist) {
			return lowR(x, y, z, flex, twist).lowL(x, -y, -z, flex, -twist);
		}

		private boolean cutBefore(int i) {
			return i > 0 && rows.get(i)[0] - rows.get(i - 1)[0] < 0.05f;
		}

		/** The tangent at key {@code i} (per tick) for channel {@code c}. */
		private float tangent(int i, int c) {
			int last = rows.size() - 1;
			if (i <= 0 || i >= last || holds.get(i) || cutBefore(i) || cutBefore(i + 1)) {
				return 0f;
			}
			float[] a = rows.get(i - 1), b = rows.get(i + 1);
			return (b[c] - a[c]) / (b[0] - a[0]);
		}

		/** Every channel at {@code t}. */
		float[] sample(float t) {
			int i = 0;
			while (i < rows.size() - 2 && t >= rows.get(i + 1)[0]) {
				i++;
			}
			float[] a = rows.get(i), b = rows.get(Math.min(i + 1, rows.size() - 1));
			float dt = b[0] - a[0];
			if (dt <= 0.05f || t <= a[0]) {
				return (t >= b[0] ? b : a).clone();
			}
			float s = Mth.clamp((t - a[0]) / dt, 0f, 1f);
			float s2 = s * s, s3 = s2 * s;
			float h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
			float[] out = new float[N];
			out[0] = t;
			for (int c = 1; c < N; c++) {
				out[c] = h00 * a[c] + h10 * dt * tangent(i, c) + h01 * b[c] + h11 * dt * tangent(i + 1, c);
			}
			return out;
		}
	}

	/** The whole sequence's body, matched to the video's frames (see the class beat table). */
	static final Track POSE = new Track()
			// ---- the slap: left forearm across the belly, the dial up; the right arm flung up, then slapping down
			.at(0f).head(0.04f, 0.05f, 0.1f).legs(0f, 0f, 0.05f, 0.08f, 0f)
			.lArm(-0.78f, 0.05f, -0.12f, 1.62f, 1.18f).rArm(-0.35f, 0f, 1.1f, 1.9f, 0.6f)
			.at(2f).rArm(-0.4f, 0f, 2.6f, 0.45f, 1.5f).head(0.02f, 0.05f, 0.08f)
			.at(4f).hold().rArm(-0.5f, 0f, 0.5f, 2.2f, -0.25f).head(0.08f, 0f, -0.12f)
			.at(6.5f).rArm(-1.0f, -0.6f, 0.2f, 0.95f, 0.35f).head(0.3f, 0f, 0f)
			// ---- the dial close-up: the right hand lifting off the core
			.at(14f).rArm(-0.82f, -0.55f, 0.18f, 1.2f, 0.3f).head(0.35f, 0f, 0f)
			// ---- the hand swelling: the forearm trembling where it is, the right hand pulling away from the flesh
			.at(HAND_AT).hold()
			.at(44f).rArm(-0.55f, -0.3f, 0.2f, 1.25f, 0.25f)
			.at(ARM_AT).lArm(-0.88f, 0.05f, -0.18f, 1.55f, 1.12f).rArm(-0.4f, -0.15f, 0.25f, 1.1f, 0.2f).lean(0.06f)
			.at(66f).lArm(-0.98f, 0.05f, -0.26f, 1.45f, 1.05f).rArm(-0.3f, -0.1f, 0.3f, 0.9f, 0.2f).shrug(-0.6f).lean(0.1f)
			// ---- the face: tilted hard over, chin up, shoulders hunched up round the neck
			.at(FACE_AT).head(0.05f, -0.2f, -0.2f)
			.at(80f).head(-0.25f, -0.3f, -0.85f).shrug(-1.4f).lean(-0.08f)
			.at(GOLD_AT + 4f).hold().head(-0.3f, -0.32f, -0.95f).shrug(-1.6f).lean(-0.12f)
			// ---- Four Arms from behind: elbows out, forearms up and clawing; the second pair splitting out
			.at(TRANSFORM_AT).head(0.35f, 0f, 0f).shrug(0f).lean(0.3f).drop(1f).split(0f)
			.arms(-0.3f, 0.05f, 1.5f, 0.95f, 1.2f).lows(-0.3f, 0.05f, 1.5f, 0.95f, 1.2f).legs(-0.12f, 0f, 0.16f, 0.4f, 0.35f)
			.at(104f).hold().split(0f)
			.at(110f).split(0.8f).head(0.22f, -0.2f, 0.08f).lean(0.36f)
			.rArm(-0.55f, 0.1f, 1.0f, 1.25f, 0.6f).lArm(-1.45f, 0.2f, -0.6f, 1.4f, -0.3f)
			.lowR(-0.2f, 0f, 1.2f, 0.55f, 0.9f).lowL(-0.5f, 0f, -1.05f, 0.75f, -0.9f)
			.at(117f).split(1f).head(0.25f, 0.1f, -0.05f)
			.rArm(-0.75f, 0.2f, 1.12f, 1.5f, 0.8f).lArm(-1.3f, 0.3f, -0.78f, 1.55f, -0.5f)
			.lowR(-0.5f, 0f, 1.0f, 0.95f, 0.7f).lowL(-0.75f, 0f, -0.9f, 1.05f, -0.7f)
			// round his left side: all four reaching forward
			.at(121f).head(0.25f, 0.28f, 0f).lean(0.32f)
			.rArm(-1.15f, -0.15f, 0.6f, 1.2f, 0.3f).lArm(-1.3f, 0.15f, -0.55f, 1.25f, -0.3f)
			.lowR(-0.95f, -0.15f, 0.55f, 1.15f, 0.3f).lowL(-1.0f, 0.15f, -0.5f, 1.15f, -0.3f)
			// the front, low: upper arms out level, forearms forward; the lower pair out under them; head down
			.at(125f).hold().head(0.32f, 0f, 0f).lean(0.3f).drop(1.5f)
			.arms(-0.35f, 0.2f, 1.35f, 1.35f, -0.45f).lows(-0.45f, 0.1f, 0.85f, 1.25f, 0.2f).legs(-0.15f, 0f, 0.22f, 0.45f, 0.25f)
			.at(WHIP_AT).arms(-0.45f, 0.1f, 1.3f, 1.45f, -0.4f)
			// the arms sweeping in past the lens
			.at(133f).arms(-1.35f, -0.7f, 0.4f, 1.9f, 0.2f).lows(-0.9f, -0.45f, 0.4f, 1.6f, 0.2f)
			// ---- the head, bowed, glaring up; shoulders hunched up
			.at(HEAD_AT).head(0.35f, 0f, 0f).shrug(-1.5f).lean(0.15f).arms(-0.3f, 0f, 1.05f, 1.5f, 0.3f).lows(-0.3f, 0f, 0.75f, 1.3f, 0.3f)
			.at(145f).head(0.4f, 0f, 0f)
			// ducking right down (the top of the head fills the frame), then
			.at(ROAR_AT + 1f).hold().head(0.85f, 0f, 0f)
			// the roar: thrown back
			.at(156f).head(-0.42f, 0f, 0f).shrug(0f).lean(-0.15f).arms(-0.15f, 0f, 0.8f, 0.7f, 0.25f).lows(-0.1f, 0f, 0.55f, 0.55f, 0.2f)
			.at(TORSO_AT).head(-0.45f, 0.05f, 0f)
			// low torso: still roaring, the arms flexing
			.at(170f).head(-0.55f, -0.25f, 0f).arms(-0.4f, 0f, 0.62f, 1.15f, 0.6f).lows(-0.3f, 0f, 0.5f, 1.0f, 0.4f).lean(-0.05f)
			.at(POSE_AT - 0.5f).hold()
			// ---- the full body: crouched, all four arms gathered in (a cut)
			.at(POSE_AT).lean(0.55f).head(0.12f, 0f, 0f).drop(2f)
			.arms(-0.95f, -0.55f, 0.15f, 1.85f, 0.4f).lows(-0.65f, -0.4f, 0.2f, 1.75f, 0.3f).legs(-0.35f, 0f, 0.5f, 1.1f, 0.8f)
			.at(181f).hold()
			// the lunge: all four fists driven forward, the front knee deep
			.at(185f).lean(0.42f).head(-0.25f, 0f, 0f).drop(2f)
			.rArm(-2.15f, 0f, 0.25f, 0.35f, 0f).lArm(-1.55f, 0f, -0.2f, 0.55f, 0f)
			.lowR(-1.25f, 0.1f, 0.25f, 1.35f, 0.2f).lowL(-1.1f, -0.1f, -0.25f, 1.4f, -0.2f)
			.rLeg(0.6f, 0f, 0.28f, 0.25f, 0.2f).lLeg(-0.8f, 0f, -0.3f, 1.15f, -0.3f)
			.at(191f).hold()
			// rising and turning into the power stance
			.at(199f).lean(0.1f).head(-0.38f, 0.15f, 0f).drop(3f)
			.rArm(-0.25f, 0.1f, 1.35f, 1.95f, 1.57f).lArm(-0.5f, -0.1f, -1.2f, 1.85f, -1.5f)
			.lowR(-0.35f, 0f, 0.6f, 1.7f, 0.5f).lowL(-0.25f, 0f, -0.55f, 1.6f, -0.45f)
			.legs(-0.15f, 0f, 1.0f, 1.05f, 1.5f)
			.at(240f).hold()
			// eased to standing under the green flash
			.at(252f).hold().lean(0f).head(0f, 0f, 0f).drop(0f).arms(0f, 0f, 0.12f, 0.25f, 0f).lows(0f, 0f, 0.3f, 0.25f, 0f).legs(0f, 0f, 0.04f, 0f, 0f);

	// ================================================================ the poses

	/** The body: the pose track, plus the tremble of the swelling before the change and the stance's breathing after it. */
	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		float[] k = POSE.sample(t);
		boolean human = t < TRANSFORM_AT;
		// a tremble that builds with the swelling; after the change a slow heave
		float shake = human ? smooth((t - HAND_AT) / 30f) * (t < GOLD_AT ? 1f : 0.5f) : 0f;
		float breathe = human ? 0f : Mth.sin(t * 0.25f) * 0.04f;
		float flexPulse = !human && t < WHIP_AT ? 0.12f * Mth.sin(t * 0.9f) : 0f;
		// the body's base: everything from its rest place, so nothing is left over from the watch pose
		float drop = k[DROP];
		m.body.xRot = m.body.yRot = m.body.zRot = 0f;
		m.body.x = 0f;
		m.body.y = drop;
		m.body.z = 0f;
		m.head.x = 0f;
		m.head.y = drop;
		m.head.z = 0f;
		m.rightArm.x = -5f;
		m.leftArm.x = 5f;
		m.rightArm.y = m.leftArm.y = 2f + drop + k[SHRUG];
		m.rightArm.z = m.leftArm.z = 0f;
		m.rightLeg.x = -1.9f;
		m.leftLeg.x = 1.9f;
		m.rightLeg.y = m.leftLeg.y = 12f + drop;
		m.rightLeg.z = m.leftLeg.z = 0f;
		HipBend.pitch(m, 1f, k[LEAN] + shake * 0.03f * Mth.sin(t * 2.1f));
		m.head.xRot = k[HX] + (human ? 0f : breathe * 0.5f);
		m.head.yRot = k[HY];
		m.head.zRot = k[HZ];
		m.rightArm.xRot = k[RA] + 0.04f * shake * Mth.sin(t * 3.3f + 1f);
		m.rightArm.yRot = k[RA + 1];
		m.rightArm.zRot = k[RA + 2] + breathe;
		m.leftArm.xRot = k[LA] + 0.05f * shake * Mth.sin(t * 2.9f);
		m.leftArm.yRot = k[LA + 1];
		m.leftArm.zRot = k[LA + 2] - breathe + 0.04f * shake * Mth.cos(t * 3.7f);
		m.rightLeg.xRot = k[RL];
		m.rightLeg.yRot = k[RL + 1];
		m.rightLeg.zRot = k[RL + 2];
		m.leftLeg.xRot = k[LL];
		m.leftLeg.yRot = k[LL + 1];
		m.leftLeg.zRot = k[LL + 2];
		// the elbows and knees
		if (t < WORLD_AT) {
			BentLimbs.bend(BentLimbs.RIGHT_ARM, k[RA + 3] + flexPulse, k[RA + 4]);
			BentLimbs.bend(BentLimbs.LEFT_ARM, k[LA + 3] + 0.03f * shake * Mth.sin(t * 4.1f) - flexPulse, k[LA + 4]);
			BentLimbs.bend(BentLimbs.RIGHT_LEG, k[RL + 3], k[RL + 4]);
			BentLimbs.bend(BentLimbs.LEFT_LEG, k[LL + 3], k[LL + 4]);
		}
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}


	// ================================================================ Four Arms' lower pair

	private static final float LOW_X = 5.4f, LOW_Y = 5.4f, LOW_Z = 1.0f;

	/**
	 * Four Arms' lower pair while it plays (from {@code FourArmsClient}, drawn in the body's space): {right x, y, z (pivot px), xRot,
	 * yRot, zRot, how far they have split out of the upper pair (0 = still inside it), left x, y, z, xRot, yRot, zRot}. Null when not
	 * playing.
	 */
	public static float[] lowerArms(Player p, HumanoidModel<?> m, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.FOUR_ARMS.id());
		if (t < 0f) {
			return null;
		}
		float[] k = POSE.sample(t);
		float split = smooth(k[SPLIT]);
		float breathe = t >= TRANSFORM_AT ? Mth.sin(t * 0.25f + 1f) * 0.04f : 0f;
		float[] r = inBody(m, m.rightArm), l = inBody(m, m.leftArm);
		return new float[] { Mth.lerp(split, r[0], -LOW_X), Mth.lerp(split, r[1], LOW_Y), Mth.lerp(split, r[2], LOW_Z), Mth.lerp(split, r[3], k[QR]),
				Mth.lerp(split, r[4], k[QR + 1]), Mth.lerp(split, r[5], k[QR + 2] + breathe), split,
				Mth.lerp(split, l[0], LOW_X), Mth.lerp(split, l[1], LOW_Y), Mth.lerp(split, l[2], LOW_Z), Mth.lerp(split, l[3], k[QL]),
				Mth.lerp(split, l[4], k[QL + 1]), Mth.lerp(split, l[5], k[QL + 2] - breathe) };
	}

	/** An upper arm's pivot and rotation, seen from the body's space (the body only ever pitches here). */
	private static float[] inBody(HumanoidModel<?> m, ModelPart arm) {
		float a = -m.body.xRot, c = Mth.cos(a), s = Mth.sin(a);
		float dy = arm.y - m.body.y, dz = arm.z - m.body.z;
		return new float[] { arm.x - m.body.x, dy * c - dz * s, dy * s + dz * c, arm.xRot - m.body.xRot, arm.yRot, arm.zRot };
	}

	/** The lower pair's faces: the skin's own arm UVs and sleeve layer, as {@code FourArmsClient} builds them. */
	private static List<ModelPart.Polygon[]> lowR, lowL;

	private static Map.Entry<List<ModelPart.Polygon[]>, List<ModelPart.Polygon[]>> lowerFaces() {
		if (lowR == null) {
			lowR = List.of(BentLimbs.cube(40, 16, -3, -2, -2, 4, 11, 4, -0.25f, 64, 64), BentLimbs.cube(40, 32, -3, -2, -2, 4, 11, 4, 0f, 64, 64));
			lowL = List.of(BentLimbs.cube(32, 48, -1, -2, -2, 4, 11, 4, -0.25f, 64, 64), BentLimbs.cube(48, 48, -1, -2, -2, 4, 11, 4, 0f, 64, 64));
		}
		return Map.entry(lowR, lowL);
	}

	/** The lower pair's elbows {right, left}: the upper arms' while still inside them, their own once split out. */
	private static BentLimbs.Joint[] lowerJoints(float t) {
		float[] k = POSE.sample(t);
		float split = smooth(k[SPLIT]);
		float pulse = t >= TRANSFORM_AT && t < WHIP_AT ? 0.12f * Mth.sin(t * 0.9f + 1.4f) : 0f;
		float rf = Mth.lerp(split, k[RA + 3], k[QR + 3] + pulse), rt = Mth.lerp(split, k[RA + 4], k[QR + 4]);
		float lf = Mth.lerp(split, k[LA + 3], k[QL + 3] - pulse), lt = Mth.lerp(split, k[LA + 4], k[QL + 4]);
		// the box runs -2..9 px: the elbow at 3.5
		return new BentLimbs.Joint[] { new BentLimbs.Joint(rf, rt, false, -1f, 0f, 3.5f, 5.25f), new BentLimbs.Joint(lf, lt, false, 1f, 0f, 3.5f, 5.25f) };
	}

	/** From {@code FourArmsClient} (in the body's space): the lower pair, bent. False = not playing (draw them as usual). */
	public static boolean renderLowerArms(PoseStack pose, VertexConsumer vc, int light, Player p, ModelPart lowerRight, ModelPart lowerLeft, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.FOUR_ARMS.id());
		if (t < 0f || t >= WORLD_AT) {
			return false;
		}
		BentLimbs.Joint[] j = lowerJoints(t);
		var faces = lowerFaces();
		BentLimbs.drawPart(pose, lowerRight, faces.getKey(), j[0], vc, light);
		BentLimbs.drawPart(pose, lowerLeft, faces.getValue(), j[1], vc, light);
		return true;
	}

	/**
	 * The swell through the model's own parts: the left arm thickens as the flesh takes it, then the chest, the right arm and the head;
	 * after the change the upper arms throb as the second pair forces its way out.
	 */
	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		if (t < TRANSFORM_AT) {
			float throb = 1f + 0.05f * Mth.sin(t * 1.7f);
			float arm = 1f + 0.38f * smooth((t - HAND_AT - 2f) / 34f) * throb;
			float chest = 1f + 0.16f * smooth((t - 58f) / 24f) * throb;
			float rArm = 1f + 0.26f * smooth((t - 70f) / 16f) * throb;
			float head = 1f + 0.1f * smooth((t - FACE_AT - 2f) / 12f) * throb;
			m.leftArm.xScale = m.leftArm.zScale = arm;
			m.rightArm.xScale = m.rightArm.zScale = rArm;
			m.body.xScale = chest;
			m.body.zScale = 1f + (chest - 1f) * 1.4f;
			m.head.xScale = m.head.yScale = m.head.zScale = head;
			m.hat.copyFrom(m.head);
			m.leftArm.x += (arm - 1f) * 1.5f + (chest - 1f) * 3f;
			m.rightArm.x -= (rArm - 1f) * 1.5f + (chest - 1f) * 3f;
		} else if (t < WHIP_AT) {
			float k = 0.12f * (1f - smooth((t - BACK_AT) / 27f)) * (0.6f + 0.4f * Mth.sin(t * 1.9f));
			m.leftArm.xScale = m.leftArm.zScale = 1f + k;
			m.rightArm.xScale = m.rightArm.zScale = 1f + k;
		}
	}

	// ================================================================ the swelling flesh

	/** Bulges ({@link SwellLumps} rows): bone (0 left arm, 1 body, 2 head, 3 right arm), centre x y z (px), half-size (px), erupts at. */
	private static final float[][] BULGES = {
			// the hand and wrist, bubbling out of the watch
			{ 0, 1.2f, 9.4f, -2.1f, 1.5f, 29 }, { 0, 2.9f, 9.6f, 0.2f, 1.4f, 30.5f }, { 0, -0.9f, 8.8f, 0.9f, 1.3f, 32 }, { 0, 1.0f, 10.4f, 0.4f, 1.7f, 33 },
			{ 0, 1.6f, 7.2f, 2.1f, 1.5f, 35 }, { 0, 3.0f, 6.6f, -0.9f, 1.6f, 37 }, { 0, -0.2f, 5.8f, -2.2f, 1.5f, 39 },
			// up the forearm and the upper arm to the shoulder
			{ 0, 1.2f, 4.6f, 2.2f, 1.7f, 42 }, { 0, 3.1f, 3.1f, 0.6f, 1.9f, 45 }, { 0, -1.0f, 3.3f, -0.4f, 1.5f, 47 }, { 0, 1.0f, 1.2f, -2.2f, 1.9f, 50 },
			{ 0, 3.2f, 0.1f, 0.1f, 2.0f, 53 }, { 0, 1.1f, -1.4f, 2.0f, 1.9f, 56 }, { 0, 1.0f, -2.2f, 0.0f, 2.2f, 58 },
			// the chest and the back
			{ 1, 3.4f, 1.2f, -2.1f, 2.0f, 60 }, { 1, 3.5f, 1.6f, 2.1f, 2.0f, 62 }, { 1, 2.8f, 4.4f, -2.2f, 2.2f, 63 }, { 1, 1.0f, 1.4f, -2.3f, 1.8f, 66 },
			{ 1, 0.0f, -0.1f, 0.0f, 1.9f, 69 }, { 1, -1.6f, 3.2f, -2.3f, 2.0f, 70 }, { 1, -2.1f, 0.6f, 2.2f, 1.8f, 72 }, { 1, -3.5f, 1.5f, -2.0f, 2.0f, 74 },
			{ 1, 0.2f, 6.4f, -2.3f, 2.0f, 76 }, { 1, 2.6f, 8.6f, 2.1f, 1.7f, 79 },
			// the right arm
			{ 3, -1.0f, -1.6f, 0.0f, 2.0f, 74 }, { 3, -3.1f, 2.2f, 0.2f, 1.8f, 77 }, { 3, -1.0f, 5.2f, -2.2f, 1.7f, 80 }, { 3, -1.2f, 8.2f, 2.1f, 1.6f, 83 },
			// the neck and the face
			{ 2, 3.6f, -2.0f, -2.0f, 1.8f, 76 }, { 2, 2.4f, -0.6f, -4.0f, 1.5f, 79 }, { 2, 3.9f, -5.0f, 1.0f, 1.7f, 80 }, { 2, -3.5f, -2.4f, -1.6f, 1.8f, 82 },
			{ 2, 0.0f, -7.6f, -2.0f, 2.0f, 84 }, { 2, -2.0f, -1.0f, -4.1f, 1.4f, 86 }, { 2, -3.8f, -5.6f, 1.6f, 1.6f, 87 } };

	/** The lumps on the upper arms / body / head, and those on each forearm (below the elbow, which ride the bent forearm). */
	private static final float[][] BULGES_UPPER, BULGES_LEFT_FOREARM, BULGES_RIGHT_FOREARM;

	static {
		List<float[]> up = new ArrayList<>(), lf = new ArrayList<>(), rf = new ArrayList<>();
		for (float[] b : BULGES) {
			if (b[0] == 0 && b[2] > 4f) {
				lf.add(b.clone());
			} else if (b[0] == 3 && b[2] > 4f) {
				rf.add(b.clone());
			} else {
				up.add(b);
			}
		}
		// the forearm lists draw on one bone each (index 0)
		for (float[] b : lf) {
			b[0] = 0;
		}
		for (float[] b : rf) {
			b[0] = 0;
		}
		BULGES_UPPER = up.toArray(new float[0][]);
		BULGES_LEFT_FOREARM = lf.toArray(new float[0][]);
		BULGES_RIGHT_FOREARM = rf.toArray(new float[0][]);
	}

	/** A bone with no shape and no transform: lumps drawn in a pose already moved onto a forearm. */
	private static final ModelPart HERE = new ModelPart(List.of(), Map.of());

	/**
	 * The swelling: lumpy peach flesh with red veins and the show's red rim, erupting out of the watch and spreading over the arm, the
	 * chest, the other arm and the face; each lump pops up springily and keeps throbbing. Gone under the golden flash. Lumps below an
	 * elbow ride the bent forearm.
	 */
	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HAND_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		SwellLumps.render(pose, buffers, bones, BULGES_UPPER, t, 7f, SwellLumps.SWELL, SwellLumps.SWELL_GLOW);
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		ModelPart[] here = { HERE };
		AlienParts.on(pose, model.leftArm, () -> {
			BentLimbs.toForearm(pose, BentLimbs.LEFT_ARM, slim);
			SwellLumps.render(pose, buffers, here, BULGES_LEFT_FOREARM, t, 7f, SwellLumps.SWELL, SwellLumps.SWELL_GLOW);
		});
		AlienParts.on(pose, model.rightArm, () -> {
			BentLimbs.toForearm(pose, BentLimbs.RIGHT_ARM, slim);
			SwellLumps.render(pose, buffers, here, BULGES_RIGHT_FOREARM, t, 7f, SwellLumps.SWELL, SwellLumps.SWELL_GLOW);
		});
	}

	/**
	 * After the change: the new arms' red glow as they split out (from {@code FourArmsClient}, on the lower arms' own bones: the upper
	 * half and the bent forearm), fading by the whip pan.
	 */
	public static void renderSplitGlow(PoseStack pose, MultiBufferSource buffers, Player p, ModelPart[] arms, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.FOUR_ARMS.id());
		if (t < TRANSFORM_AT || t >= WHIP_AT + 6) {
			return;
		}
		float k = (1f - smooth((t - BACK_AT - 10f) / 25f)) * (0.7f + 0.3f * Mth.sin(t * 1.6f));
		int c = ShowMath.grey(k);
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SwellLumps.SWELL_GLOW));
		BentLimbs.Joint[] joints = lowerJoints(t);
		for (int i = 0; i < arms.length; i++) {
			// the right arm's box runs -3..1 px, the left's -1..3
			float x0 = i == 0 ? -3.6f : -1.6f, x1 = i == 0 ? 1.6f : 3.6f;
			BentLimbs.Joint j = joints[i];
			AlienParts.on(pose, arms[i], () -> {
				pose.pushPose();
				j.toTwist(pose);
				AlienParts.box(pose, glow, x0, -2.6f, -2.6f, x1, 4.2f, 2.6f, 0f, 0f, 1f, 1f, c, LightTexture.FULL_BRIGHT);
				pose.popPose();
				j.toLower(pose);
				AlienParts.box(pose, glow, x0, 2.8f, -2.6f, x1, 9.6f, 2.6f, 0f, 0f, 1f, 1f, c, LightTexture.FULL_BRIGHT);
			});
		}
	}

	// ================================================================ in the world: the dial's light, the golden glow

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 24f);
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, GOLD_AT - 2f, 97f, 0xFFC23A, 0xFFF4C8);
	}

	// ================================================================ the holder's screen

	/**
	 * v0.17.2 (user: "recreate the tranformation sequence frame for frame with the background and everything"): every shot has the
	 * episode's own background -- the dim room behind the slap, the green bloom round the dial, the olive field framed by black Kirby
	 * dots through the swelling and the reveal (a warm glow behind the stance), the dark-green ray burst.
	 */
	@Override
	public void drawStage(Stage st, float t) {
		if (t < SLAP_END) {
			st.gradient(0x56682E, 0x2E3C17, 0x0B1005);
		} else if (t < HAND_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < BURST_STAGE_AT) {
			st.gradient(OLIVE_IN, OLIVE_MID, OLIVE_OUT);
			if (t >= POSE_AT - 4f) {
				// the stance: a red-orange glow behind his body
				float w = smooth((t - POSE_AT + 4f) / 6f);
				st.radial(0xE0743A, 0xB8823E, OLIVE_MID, 0.85f * w, 0f);
			}
			// v0.17.3 (user: "in the show there are lines of tons of clumped together kirby dots in every frame and angle, make ours look
			// like that"): streams of clumped dots sweeping across every shot, a new layout at each cut
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - HAND_AT, t >= POSE_AT ? STANCE_DOTS : OLIVE_DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - BURST_STAGE_AT, BURST_RAY, 16);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)) + 5, t - BURST_STAGE_AT, BURST_DOTS);
		}
	}

	/** The full-screen flashes (green in, gold, white, green out and its fade), the dial's green bloom, and the whip pan's streaks. */
	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 3.5f);
		ShowShots.dialBloom(st, t, WATCH_AT, HAND_AT);
		ShowShots.goldFlash(st, t, GOLD_AT, 97f);
		ShowShots.whipStreaks(st, t, WHIP_AT, HEAD_AT);
		ShowShots.flash(st, t, 0xF3F8EC, WHITE_AT, 2.5f, 210f, 6f, 0.92f);
		ShowShots.greenOut(st, t, GREEN2_AT, WORLD_AT, total());
	}
}
