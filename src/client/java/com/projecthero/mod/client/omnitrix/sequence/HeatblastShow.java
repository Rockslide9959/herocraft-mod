package com.projecthero.mod.client.omnitrix.sequence;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... try to be as expressive as you can
 * without bending the limbs"): Heatblast's show transformation, shot for shot.
 *
 * <p>v0.17.3 v2, bent limbs (user: "start remaking Heatblast's one ... make sure their limbs don't go weird or detach from the body and
 * touch up the effects and poses please"), the way Four Arms was remade: elbows and knees bend through {@link BentLimbs}, and the body
 * runs on one keyframed pose track ({@link #POSE}: Catmull-Rom through the keys, held where the show holds, snapped on the cuts),
 * re-matched shot by shot to the compilation video. Video second {@code v} is tick {@code (v - 10.30) * 20}; the beats:
 * <pre>
 *   tick     shot / what the body does
 *   0-7      the slap, front medium: the right arm flung up, then slapping down on the dial; the left forearm thrust across, watch up
 *   7-24     FULL GREEN FLASH
 *   24-38    the dial close-up in its green bloom; molten rock (orange, cooling red) bubbling out round the dial from 28
 *   38-62    in front of the left forearm, held across the chest (elbow bent): the camera runs along it from the hand to the elbow,
 *            up the upper arm and the shoulder, while red rock plates crawl up the arm a wave at a time
 *   62-83    Ben's face, low and close from his left: head tilted over, chin up, the rock creeping up the neck and along the jaw;
 *            pushing in to the cheek
 *   83-92    the eye: the green iris, a light streak, the swirl blurring into black
 *   92-107   black, a hot white streak across it, thinning to a line                     (the body changes at 95)
 *   107-112  dark red rock, one jagged magma crack splitting up across it
 *   112-116  closer: the crack web, branching, white-hot
 *   115-118  a smeared whip pan
 *   118-143  one long pull-back off the chest symbol (black and khaki splotches): arms hanging, elbows soft; the head bursts into flame
 *            (127); the fists flung down and out, elbows wide, flaring white (131); the right fist hooks across (137); both fists
 *            together in front of the chest, elbows out (139.5)
 *   143-145  a cut round to his left: up into the guard
 *   145-152  WHITE FLASH, fading off the stance to 166
 *   150-190  the stance on the green ray burst, low and nearly square on, knees to crown: leaning back and over to his left, the
 *            right fist low, out and toward the lens, the left fist up out to the side at head height (upper arm near level,
 *            forearm up), legs wide; flat white fists, the head blazing (final-pose rework)
 *   190      a hard cut back to the world (no green out in this one); the stance eases to standing for the bystanders
 * </pre>
 * The rock, the poses, the head fire and the flaring fists are on the body, so everyone nearby sees them; the eye, the streak and the
 * cracks are the holder's own screen (flat quads, no particles).
 */
public final class HeatblastShow extends ShowSequenceClient {
	static final float SLAP_END = 7f, DIAL_AT = 24f, ARM_AT = 38f, FACE_AT = 62f, EYE_AT = 83f, BLACK_AT = 92f, CRACK_AT = 107f, WEB_AT = 111.5f,
			SMEAR_AT = 115f, CHEST_AT = 118f, FLAME_AT = 127f, FLARE_AT = 131f, HOOK_AT = 137f, CROSS_AT = 139.5f, GUARD_AT = 143f, WHITE_AT = 144f,
			POSE_AT = 150f, WORLD_AT = 190f;
	static final int TRANSFORM_AT = 95;

	/** v2: a grey plate with a white rim, tinted per plate (molten orange cooling to the show's dark red with pink edges). */
	private static final ResourceLocation ROCK_PLATE = ProjectHeroMod.id("textures/entity/omnitrix/sequence/heatblast/rock_plate.png");

	private static final KirbyDots.Style ARM_DOTS = KirbyDots.Style.show().size(1.2f).clear(0.2f),
			KHAKI_DOTS = KirbyDots.Style.show().rgb(0xA49A6A).bands(3).density(0.6f).size(1.3f).width(0.9f).rim(0.6f).clear(0.42f).flow(0.6f),
			BURST_DOTS = KirbyDots.Style.show().rgb(0x0B2208).bands(2).density(1.2f).size(1.4f).width(1.3f).rim(1.2f).clear(0.5f).flow(1.0f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(
			// the slap, closer than the shared shot: waist up, a little low (the show's framing)
			// v0.17.4: from a little above, head to waist, the hands meeting low in the middle (the show's booth shot)
			new float[][] { { 0f, 1.15f, 0f, 1.68f, 1.3f, -0.1f, 0.2f }, { SLAP_END, 1.05f, 0f, 1.66f, 1.28f, -0.1f, 0.2f } }, ShowShots.dialCloseUp(SLAP_END + 0.01f, ARM_AT),
			new float[][] {
					// in front of the forearm held across the chest: the hand, along to the elbow, up the upper arm to the shoulder
					// v0.17.4: in tight and a little above, so the rock fills the frame and the face stays out of it (the show never
					// shows the face here: only the forearm, then the white sleeve and the shoulder behind the rock)
					{ 38.01f, 0.4f, -8f, 1.4f, 1.24f, 0.06f, 0.3f },
					{ 47f, 0.42f, -14f, 1.42f, 1.24f, -0.06f, 0.28f },
					{ 55f, 0.46f, -26f, 1.5f, 1.3f, -0.2f, 0.16f },
					{ 62f, 0.5f, -34f, 1.56f, 1.36f, -0.24f, 0.06f },
					// the face, low and close from his left front, pushing in to the cheek
					{ 62.01f, 0.62f, -24f, 1.52f, 1.62f, -0.02f, 0f },
					{ 72f, 0.52f, -34f, 1.54f, 1.6f, -0.04f, 0f },
					{ 83f, 0.42f, -48f, 1.56f, 1.58f, -0.06f, 0f },
					// (under the eye, the black and the cracks: never seen)
					{ 83.01f, 0.4f, 0f, 1.6f, 1.62f, 0f, 0f },
					{ 117.99f, 0.4f, 0f, 1.6f, 1.62f, 0f, 0f },
					// the long pull-back off the chest symbol
					// v0.17.4: starts tighter on the symbol and ends wider (the show's pull-back ends on the whole torso and thighs with
					// room round him, the fists low in the frame)
					{ 118.01f, 0.3f, 0f, 1.24f, 1.24f, 0f, 0f },
					{ 124f, 0.7f, 0f, 1.24f, 1.28f, 0f, 0f },
					{ 130f, 1.4f, -2f, 1.2f, 1.32f, 0f, 0f },
					{ 137f, 2.3f, -5f, 1.06f, 1.18f, 0f, 0f },
					{ 143f, 2.6f, -8f, 1.0f, 1.14f, 0f, 0f },
					// round to his left: the guard
					{ 143.01f, 2.45f, -40f, 0.72f, 1.08f, 0f, 0f },
					{ 150f, 2.4f, -34f, 0.72f, 1.06f, 0f, 0f },
					// the stance, low and nearly square on (final-pose rework: pulled back so the raised fist out to the side and the
					// wide legs fit, knees to crown like the show's frame; it was a tight waist-up crop from his left)
					// v0.17.4: closer and lower (the show crops him at the thighs, head near the top), pushing in slowly
					{ 150.01f, 2.15f, -6f, 0.55f, 1.18f, 0f, 0f },
					{ 190f, 1.9f, -4f, 0.58f, 1.2f, 0f, 0f } }))
			.linear(SMEAR_AT, CHEST_AT);

	public HeatblastShow() {
		super(Aliens.HEATBLAST.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= DIAL_AT && t < ARM_AT) {
			fov -= 8 * smooth((t - DIAL_AT) / 14f);
		} else if (t >= ARM_AT && t < FACE_AT) {
			// v0.17.4: a longer lens on the rock arm, so the face stays out of the shot
			fov -= 6;
		} else if (t >= FLAME_AT && t < FLAME_AT + 6f) {
			// a jolt as the head ignites
			fov -= 3 * Mth.sin(Mth.PI * (t - FLAME_AT) / 6f);
		}
		return fov;
	}

	// ================================================================ the pose track

	/** Channels (radians unless noted). Arms / legs: x, y, z rotation, elbow / knee flex, twist. DROP / SHRUG in px. */
	static final int HX = 1, HY = 2, HZ = 3, LEAN = 4, DROP = 5, SHRUG = 6, RA = 7, LA = 12, RL = 17, LL = 22, N = 27;

	/**
	 * Keyframes, one row per key: {tick, channels...} (as {@code FourArmsShow}'s track). A key under 0.05 ticks after the one before it
	 * is a cut (snaps); {@code hold()} keys ease in and out; the rest flow through (Catmull-Rom).
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

		private boolean cutBefore(int i) {
			return i > 0 && i < rows.size() && rows.get(i)[0] - rows.get(i - 1)[0] < 0.05f;
		}

		private float tangent(int i, int c) {
			int last = rows.size() - 1;
			if (i <= 0 || i >= last || holds.get(i) || cutBefore(i) || cutBefore(i + 1)) {
				return 0f;
			}
			float[] a = rows.get(i - 1), b = rows.get(i + 1);
			return (b[c] - a[c]) / (b[0] - a[0]);
		}

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
			// ---- the slap: the right arm flung up high, the left forearm thrust across with the dial up
			// v0.17.4: the left forearm held out low in front of the belly (the show's hands meet low in the middle of the frame, not
			// up at the chest); the right hand up by the shoulder, then slapping down on it
			.at(0f).head(0.22f, 0.12f, 0.1f).legs(0f, 0f, 0.06f, 0.1f, 0f).lean(0.06f)
			.lArm(-0.5f, 0.15f, -0.05f, 1.2f, 0.6f).rArm(-1.9f, -0.3f, 0.3f, 1.2f, 0.3f)
			// slapping down on the dial
			.at(2.5f).hold().rArm(-0.55f, -0.2f, 0.05f, 1.3f, -0.7f).head(0.3f, 0.05f, -0.02f)
			.at(SLAP_END).rArm(-0.6f, -0.2f, 0.05f, 1.25f, -0.7f).head(0.32f, 0f, 0f)
			// ---- the dial close-up: the right hand lifting off the core
			.at(20f).rArm(-0.8f, -0.55f, 0.2f, 1.2f, 0.3f).head(0.35f, 0f, 0f)
			.at(34f).hold().rArm(-0.55f, -0.3f, 0.25f, 1.0f, 0.2f)
			// ---- the forearm across the chest (the camera runs along it); the right arm drops out of the shot
			.at(ARM_AT).lArm(-1.2f, 0.05f, -0.08f, 1.6f, 1.2f).rArm(0.08f, 0f, 0.18f, 0.35f, 0f).head(0.4f, 0.12f, 0.05f).shrug(-0.4f).lean(0.08f)
			.at(50f).lArm(-1.25f, 0.05f, -0.12f, 1.7f, 1.2f).head(0.35f, 0.2f, 0.1f).shrug(-0.9f)
			// the arm lifting toward the face as it hardens, shoulders up round the neck
			.at(FACE_AT - 0.5f).lArm(-1.3f, 0.08f, -0.14f, 1.75f, 1.2f).head(0.1f, 0.12f, -0.2f).shrug(-1.3f).lean(0.04f)
			// ---- the face: tilted hard over, chin up, gritting
			.at(68f).head(-0.22f, 0.22f, -0.42f).shrug(-1.6f).lean(-0.06f).lArm(-0.8f, 0.05f, -0.14f, 1.62f, 1.18f)
			.at(EYE_AT).hold().head(-0.28f, 0.28f, -0.52f).shrug(-1.7f).lean(-0.1f).lArm(-0.78f, 0.05f, -0.16f, 1.65f, 1.18f)
			// ---- (unseen under the eye / streak / cracks) Heatblast at rest
			.at(TRANSFORM_AT).head(0.3f, 0f, 0f).shrug(0f).lean(0.04f).drop(0f)
			.arms(0.04f, 0f, 0.2f, 0.35f, -0.3f).legs(0f, 0f, 0.06f, 0.08f, 0.1f)
			// ---- the pull-back: arms hanging a little out, the head lifting
			.at(CHEST_AT).hold()
			.at(FLAME_AT - 1f).head(0.05f, 0f, 0f).arms(0.0f, 0f, 0.28f, 0.45f, -0.4f)
			// the head ignites: chin up, chest out
			.at(FLAME_AT + 2f).head(-0.2f, 0f, 0f).lean(-0.06f).arms(-0.05f, 0f, 0.4f, 0.55f, -0.6f)
			// the fists flung down and out, elbows wide, a wide stance
			.at(FLARE_AT + 1.5f).hold().head(-0.05f, 0f, 0f).lean(0.08f).drop(1f)
			.arms(-0.12f, 0f, 0.82f, 1.0f, -1.35f).legs(-0.05f, 0f, 0.2f, 0.4f, 0.6f)
			.at(HOOK_AT - 1f).hold().arms(-0.14f, 0f, 0.86f, 1.05f, -1.35f)
			// the right fist hooks across the chest
			.at(HOOK_AT + 1.5f).rArm(-1.25f, -0.55f, 0.35f, 1.55f, -0.9f).head(0.08f, 0.1f, 0f)
			// both fists together in front of the chest, elbows out
			.at(CROSS_AT + 1f).hold().head(0.12f, 0f, 0f).lean(0.12f)
			.arms(-1.05f, -0.38f, 0.58f, 1.8f, -1.0f)
			.at(GUARD_AT - 0.5f).hold().arms(-1.1f, -0.38f, 0.6f, 1.85f, -1.0f)
			// ---- the guard (a cut): the left fist up by the head, the right forward
			.at(GUARD_AT + 0.01f).head(-0.05f, 0.2f, 0f).lean(0.08f).drop(1.5f)
			.lArm(-0.45f, 0f, -1.35f, 1.8f, -1.45f).rArm(-1.0f, -0.2f, 0.3f, 0.8f, 0.2f)
			.legs(-0.1f, 0f, 0.3f, 0.45f, 0.6f)
			// ---- the stance (final-pose rework, matched to the show's t 185): leaning back and over to his left (STANCE_ROLL), chin
			// up; the left upper arm out near level with the forearm up and out, so the fist is out to the side at head height (it was
			// folded in beside the face); the right fist low, out to his right and forward toward the lens; legs wide, the left knee bent
			.at(POSE_AT).hold().head(-0.2f, 0.12f, 0f).lean(-0.1f).drop(1.8f)
			// v0.17.4: the raised fist level with the head (forearm up more), the near fist low at the hip and out toward the lens
			.lArm(-0.25f, 0f, -1.35f, 1.45f, -1.5f).rArm(-0.5f, 0f, 0.75f, 0.45f, 0.25f)
			.rLeg(-0.15f, 0f, 0.5f, 0.12f, 0.2f).lLeg(0.18f, 0f, -0.36f, 0.5f, -0.5f)
			.at(WORLD_AT).hold()
			// eased to standing for the bystanders once the holder is back in the world
			.at(199f).hold().head(0f, 0f, 0f).lean(0f).drop(0f).shrug(0f).arms(0f, 0f, 0.1f, 0.15f, 0f).legs(0f, 0f, 0.03f, 0f, 0f);

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		float[] k = POSE.sample(t);
		boolean human = t < TRANSFORM_AT;
		// the rock taking the arm: a tremble that builds; after the change a slow heave and the stance's fists pumping
		float shake = human ? smooth((t - 30f) / 25f) : 0f;
		float breathe = human ? 0f : Mth.sin(t * 0.3f) * 0.035f;
		float pump = t >= POSE_AT ? 0.05f * Mth.sin(t * 0.5f) : 0f;
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
		// final-pose rework: the stance's sideways lean over to his left, about the hips (ShowBody.tilt: the neck, head and
		// shoulders follow, the arms roll with the chest, so nothing comes away from the body)
		float roll = stanceRoll(t);
		if (roll != 0f) {
			ShowBody.tilt(m, roll);
			m.rightArm.y += k[SHRUG];
			m.leftArm.y += k[SHRUG];
		}
		HipBend.pitch(m, 1f, k[LEAN] + shake * 0.025f * Mth.sin(t * 2.3f));
		m.head.xRot = k[HX] + breathe * 0.5f + shake * 0.04f * Mth.sin(t * 2.6f);
		m.head.yRot = k[HY];
		m.head.zRot = k[HZ] + roll;
		m.rightArm.xRot = k[RA] + 0.03f * shake * Mth.sin(t * 2.7f + 1f) - pump;
		m.rightArm.yRot = k[RA + 1];
		m.rightArm.zRot = k[RA + 2] + breathe + roll;
		m.leftArm.xRot = k[LA] + 0.05f * shake * Mth.sin(t * 3.1f);
		m.leftArm.yRot = k[LA + 1];
		m.leftArm.zRot = k[LA + 2] - breathe + 0.04f * shake * Mth.cos(t * 3.9f) + roll;
		m.rightLeg.xRot = k[RL];
		m.rightLeg.yRot = k[RL + 1];
		m.rightLeg.zRot = k[RL + 2];
		m.leftLeg.xRot = k[LL];
		m.leftLeg.yRot = k[LL + 1];
		m.leftLeg.zRot = k[LL + 2];
		// the elbows and knees (eased straight by the end, so nothing snaps when the sequence stops)
		BentLimbs.bend(BentLimbs.RIGHT_ARM, k[RA + 3] + pump * 0.6f, k[RA + 4]);
		BentLimbs.bend(BentLimbs.LEFT_ARM, k[LA + 3] + 0.03f * shake * Mth.sin(t * 4.1f), k[LA + 4]);
		BentLimbs.bend(BentLimbs.RIGHT_LEG, k[RL + 3], k[RL + 4]);
		BentLimbs.bend(BentLimbs.LEFT_LEG, k[LL + 3], k[LL + 4]);
		copyLayers(m);
	}

	/** Final-pose rework: how far the stance leans over to his left (radians, as {@code body.zRot}; the show's body slants that way). */
	static final float STANCE_ROLL = 0.2f; // v0.17.4: 0.15 -> 0.2, the show's slant

	/** The stance's sideways lean at {@code t}: in under the white flash after the guard, eased out with the stance for the bystanders. */
	static float stanceRoll(float t) {
		if (t < GUARD_AT + 1f || t >= 199f) {
			return 0f;
		}
		return STANCE_ROLL * smooth((t - GUARD_AT - 1f) / 5f) * (1f - smooth((t - WORLD_AT) / 9f));
	}

	static void copyLayers(HumanoidModel<?> m) {
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	// ================================================================ the rock skin (v2)

	/** Bone boxes (px): x0 x1 y0 y1 z0 z1 -- 0 left arm, 1 body, 2 head, 3 right arm (wide arms). */
	private static final float[][] BOX = { { -1, 3, -2, 10, -2, 2 }, { -4, 4, 0, 12, -2, 2 }, { -4, 4, -8, 0, -4, 4 }, { -3, 1, -2, 10, -2, 2 } };

	/**
	 * Where the rock grows: {bone, face (0 +x, 1 -x, 2 +z back, 3 -z front, 4 -y top), a0, a1 (across the face: z on the x faces, x
	 * on the others), b0, b1 (down the face: y; z on the top face), cell (px), first tick, ticks per px from the origin, origin a, origin
	 * b}. Each region is tiled by plates on a jittered grid, overlapping, so the skin disappears under them as the wave passes.
	 */
	private static final float[][] REGIONS = {
			// the left forearm, out of the watch (the wrist at y 8), every side; the hand stays bare
			{ 0, 0, -2, 2, 3.5f, 8.8f, 1.2f, 26, 1.3f, 0, 8 }, { 0, 1, -2, 2, 3.5f, 8.8f, 1.2f, 27, 1.3f, 0, 8 },
			{ 0, 2, -1, 3, 3.5f, 8.8f, 1.2f, 27, 1.3f, 1, 8 }, { 0, 3, -1, 3, 3.5f, 8.8f, 1.2f, 26, 1.3f, 1, 8 },
			// the upper arm and the shoulder
			{ 0, 0, -2, 2, -2f, 4.9f, 1.26f, 38, 2.4f, 0, 4 }, { 0, 1, -2, 2, -2f, 4.9f, 1.26f, 40, 2.4f, 0, 4 },
			{ 0, 2, -1, 3, -2f, 4.9f, 1.26f, 39, 2.4f, 1, 4 }, { 0, 3, -1, 3, -2f, 4.9f, 1.26f, 38, 2.4f, 1, 4 },
			{ 0, 4, -1, 3, -2, 2, 1.26f, 52, 1.2f, 1, 0 },
			// the left of the chest and the back, the neck
			{ 1, 3, 0, 4, 0, 7, 1.32f, 52, 1.5f, 4, 0 }, { 1, 2, 0, 4, 0, 6, 1.32f, 54, 1.5f, 4, 0 }, { 1, 4, -2, 4, -2, 2, 1.2f, 56, 1.2f, 4, 0 },
			{ 1, 3, -4, 0, 0, 6, 1.32f, 62, 1.6f, 0, 0 }, { 1, 2, -4, 0, 0, 6, 1.32f, 64, 1.6f, 0, 0 },
			// up the neck, along the jaw, over the left cheek
			{ 2, 0, -4, 2, -2.6f, 0, 1.02f, 62, 2.2f, 2, 0 }, { 2, 3, 1, 4, -2.4f, 0, 0.96f, 66, 2.4f, 4, 0 },
			{ 2, 3, -3, 1, -1.2f, 0, 0.96f, 74, 2.4f, 1, 0 }, { 2, 2, 0, 4, -2.4f, 0, 1.08f, 64, 1.8f, 4, 0 },
			// the right arm, last
			{ 3, 1, -2, 2, -2, 6, 1.26f, 68, 1.5f, 0, -2 }, { 3, 3, -3, 1, -2, 6, 1.26f, 70, 1.5f, -1, -2 }, { 3, 2, -3, 1, -2, 5, 1.26f, 71, 1.5f, -1, -2 } };

	/** Plates: {bone, forearm (1 = rides the bent forearm), face, x, y, z, half-w, half-h, angle, tilt, erupt, shade}. */
	private static final float[][] ROCK_PLATES = growRock(0x4EA7B1A5L);

	private static float[][] growRock(long seed) {
		List<float[]> out = new ArrayList<>();
		int n = 0;
		for (float[] r : REGIONS) {
			int bone = (int) r[0], face = (int) r[1];
			float[] box = BOX[bone];
			float cell = r[6];
			int na = Math.max(1, Math.round((r[3] - r[2]) / cell)), nb = Math.max(1, Math.round((r[5] - r[4]) / cell));
			float sa = (r[3] - r[2]) / na, sb = (r[5] - r[4]) / nb;
			for (int i = 0; i < na; i++) {
				for (int j = 0; j < nb; j++, n++) {
					float a = r[2] + (i + 0.5f + (ShowMath.hash(seed, n * 9) - 0.5f) * 0.5f) * sa;
					float b = r[4] + (j + 0.5f + (ShowMath.hash(seed, n * 9 + 1) - 0.5f) * 0.5f) * sb;
					float x, y, z;
					switch (face) {
						case 0 -> { x = box[1]; y = b; z = a; }
						case 1 -> { x = box[0]; y = b; z = a; }
						case 2 -> { x = a; y = b; z = box[5]; }
						case 3 -> { x = a; y = b; z = box[4]; }
						default -> { x = a; y = box[2]; z = b; }
					}
					float d = Mth.sqrt((a - r[9]) * (a - r[9]) + (b - r[10]) * (b - r[10]));
					float erupt = r[7] + d * r[8] + (ShowMath.hash(seed, n * 9 + 2) - 0.5f) * 2.4f;
					float hw = Math.max(sa, sb) * (0.6f + 0.2f * ShowMath.hash(seed, n * 9 + 3));
					float hh = hw * (0.75f + 0.35f * ShowMath.hash(seed, n * 9 + 4));
					// (the halves overlap round the elbow, so the stretched outer side of the bend stays covered)
					boolean fore = (bone == 0 || bone == 3) && face != 4 && r[4] > 3f;
					out.add(new float[] { bone, fore ? 1 : 0, face, x, y, z, hw, hh, (ShowMath.hash(seed, n * 9 + 5) - 0.5f) * 60f,
							(ShowMath.hash(seed, n * 9 + 6) - 0.5f) * 26f, erupt, 0.78f + 0.22f * ShowMath.hash(seed, n * 9 + 7) });
				}
			}
		}
		return out.toArray(new float[0][]);
	}

	/** The rock's colours: molten as it bubbles out, cooling to the show's dark red (the texture's rim gives the pink edges). */
	private static final int MOLTEN = 0xFF7A20, ROCK_RED = 0xE43838;

	/** A bone with no shape and no transform: plates drawn in a pose already moved onto a forearm. */
	private static final ModelPart HERE = new ModelPart(List.of(), java.util.Map.of());

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < 27f || t >= TRANSFORM_AT) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ROCK_PLATE));
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		for (int b = 0; b < 4; b++) {
			int bone = b;
			for (int fore = 0; fore <= ((b == 0 || b == 3) ? 1 : 0); fore++) {
				int f = fore;
				AlienParts.on(pose, bones[b], () -> {
					if (f == 1) {
						BentLimbs.toForearm(pose, bone == 0 ? BentLimbs.LEFT_ARM : BentLimbs.RIGHT_ARM, slim);
					}
					drawPlates(pose, vc, bone, f, slim, t);
				});
			}
		}
	}

	private static void drawPlates(PoseStack pose, VertexConsumer vc, int bone, int fore, boolean slim, float t) {
		int light = LightTexture.FULL_BRIGHT;
		for (float[] pl : ROCK_PLATES) {
			if ((int) pl[0] != bone || (int) pl[1] != fore) {
				continue;
			}
			float age = t - pl[10];
			float g = ShowMath.pop(age, 5f);
			if (g <= 0.02f) {
				continue;
			}
			// bubbling: a molten throb for a few ticks, then cooling to red
			float cool = smooth((age - 2f) / 7f);
			int rgb = ShowMath.lerpRgb(cool, MOLTEN, ShowMath.scaleRgb(ROCK_RED, pl[11]) & 0xFFFFFF);
			float throb = 1f + (1f - cool) * 0.12f * Mth.sin(age * 2.2f + pl[3]);
			float x = pl[3];
			int face = (int) pl[2];
			// the slim arm's outer side is a pixel in
			if (slim && face == 0 && (bone == 0)) {
				x -= 1f;
			} else if (slim && face == 1 && bone == 3) {
				x += 1f;
			} else if (slim && (face >= 2) && (bone == 0 || bone == 3)) {
				x = bone == 0 ? Math.min(x, 2f) : Math.max(x, -2f);
			}
			pose.pushPose();
			pose.translate(x / 16f, pl[4] / 16f, pl[5] / 16f);
			switch (face) {
				case 0 -> pose.mulPose(Axis.YP.rotationDegrees(90f));
				case 1 -> pose.mulPose(Axis.YP.rotationDegrees(-90f));
				case 3 -> pose.mulPose(Axis.YP.rotationDegrees(180f));
				case 4 -> pose.mulPose(Axis.XP.rotationDegrees(90f));
				default -> {
				}
			}
			pose.mulPose(Axis.ZP.rotationDegrees(pl[8]));
			pose.mulPose(Axis.XP.rotationDegrees(pl[9]));
			float s = g * throb;
			pose.scale(s, s, s);
			float w = pl[6], h = pl[7];
			// sitting on the skin (and its outer layer), raised a little like a scab
			AlienParts.box(pose, vc, -w, -h, -0.1f, w, h, 0.55f, 0f, 0f, 1f, 1f, 0xFF000000 | rgb, light);
			pose.popPose();
		}
	}

	/**
	 * v0.17.3: angular plates growing over the body's parts (Diamondhead's crystal, Wildmutt's fur; Heatblast's own rock moved to
	 * {@link #growRock} in v2): each a flat box on a face of its bone, turned a random way about the face's normal, popping up springily
	 * from its erupt tick.
	 */
	static final class Plates {
		private Plates() {
		}

		/** Plates {bone, x, y, z, face, half-width, half-height, angle, tilt, erupt, shade} from {@code patches} (see the callers). */
		static float[][] gen(long seed, float[][] patches) {
			java.util.List<float[]> out = new java.util.ArrayList<>();
			int n = 0;
			for (float[] pa : patches) {
				int count = (int) pa[7];
				int faces = (int) pa[14];
				int[] allowed = new int[5];
				int na = 0;
				for (int f = 0; f < 5; f++) {
					if ((faces & (1 << f)) != 0) {
						allowed[na++] = f;
					}
				}
				float maxD = 0f;
				float[][] pts = new float[count][];
				for (int i = 0; i < count; i++, n++) {
					int face = allowed[(int) (ShowMath.hash(seed, n * 11) * na) % na];
					float x = Mth.lerp(ShowMath.hash(seed, n * 11 + 1), pa[1], pa[2]);
					float y = Mth.lerp(ShowMath.hash(seed, n * 11 + 2), pa[3], pa[4]);
					float z = Mth.lerp(ShowMath.hash(seed, n * 11 + 3), pa[5], pa[6]);
					switch (face) {
						case 0 -> x = pa[2] + 0.25f;
						case 1 -> x = pa[1] - 0.25f;
						case 2 -> z = pa[6] + 0.25f;
						case 3 -> z = pa[5] - 0.25f;
						default -> y = pa[3] - 0.25f;
					}
					float d = Mth.sqrt((x - pa[8]) * (x - pa[8]) + (y - pa[9]) * (y - pa[9]) + (z - pa[10]) * (z - pa[10]));
					maxD = Math.max(maxD, d);
					float size = pa[13] * (0.75f + 0.5f * ShowMath.hash(seed, n * 11 + 4));
					pts[i] = new float[] { pa[0], x, y, z, face, size, size * (0.6f + 0.6f * ShowMath.hash(seed, n * 11 + 5)),
							ShowMath.hash(seed, n * 11 + 6) * 360f, (ShowMath.hash(seed, n * 11 + 7) - 0.5f) * 40f, d,
							0.8f + 0.2f * ShowMath.hash(seed, n * 11 + 8) };
				}
				for (float[] pt : pts) {
					pt[9] = Mth.lerp(maxD <= 0f ? 0f : pt[9] / maxD, pa[11], pa[12]) + (ShowMath.hash(seed, n + (int) pt[1] * 7) - 0.5f) * 2f;
					out.add(pt);
				}
			}
			return out.toArray(new float[0][]);
		}

		/** Every plate erupted by {@code t}, each popping up over {@code popLen} ticks; {@code thick} is the half-thickness (px). */
		static void render(PoseStack pose, VertexConsumer vc, ModelPart[] bones, float[][] plates, float t, float popLen, float thick) {
			int light = LightTexture.FULL_BRIGHT;
			for (float[] pl : plates) {
				float g = ShowMath.pop(t - pl[9], popLen);
				if (g <= 0.02f) {
					continue;
				}
				int shade = Math.round(255 * pl[10]);
				int argb = 0xFF000000 | (shade << 16) | (shade << 8) | shade;
				AlienParts.on(pose, bones[(int) pl[0]], () -> {
					pose.pushPose();
					pose.translate(pl[1] / 16f, pl[2] / 16f, pl[3] / 16f);
					switch ((int) pl[4]) {
						case 0 -> pose.mulPose(Axis.YP.rotationDegrees(90f));
						case 1 -> pose.mulPose(Axis.YP.rotationDegrees(-90f));
						case 3 -> pose.mulPose(Axis.YP.rotationDegrees(180f));
						case 4 -> pose.mulPose(Axis.XP.rotationDegrees(90f));
						default -> {
						}
					}
					pose.mulPose(Axis.ZP.rotationDegrees(pl[7]));
					pose.mulPose(Axis.XP.rotationDegrees(pl[8]));
					pose.scale(g, g, g);
					float w = pl[5], h = pl[6];
					AlienParts.box(pose, vc, -w, -h, -thick, w, h, thick, 0f, 0f, 1f, 1f, argb, light);
					pose.popPose();
				});
			}
		}
	}

	// ================================================================ Heatblast's body: the head igniting, the fists flaring (v2)

	/**
	 * From {@code HeatblastClient}: the head fire's size while this sequence plays (0 = not lit yet; it bursts up with an overshoot at
	 * {@link #FLAME_AT} and settles), or -1 when it is not playing (his usual fire).
	 */
	public static float headFire(Player p, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.HEATBLAST.id());
		if (t < 0f) {
			return -1f;
		}
		if (t < FLAME_AT) {
			return 0f;
		}
		// a spark, then the blaze climbing up out of the head and overshooting before it settles
		float burst = smooth((t - FLAME_AT) / 4.5f);
		float flare = 0.4f * env(t, FLAME_AT + 2f, 2.5f, FLAME_AT + 5f, 6f);
		return burst * (1.1f + flare) + (t >= POSE_AT ? 0.1f : 0f);
	}

	/** How hot the fists burn at {@code t} (0 = not at all): flaring at {@link #FLARE_AT}, steady through the guard and the stance. */
	private static float fistHeat(float t) {
		if (t < FLARE_AT - 0.5f) {
			return 0f;
		}
		float k = ShowMath.pop(t - FLARE_AT + 0.5f, 4f) * (1f + 0.3f * Math.max(0f, 1f - (t - FLARE_AT - 2f) / 5f));
		return k * (0.9f + 0.1f * Mth.sin(t * 1.7f));
	}

	/**
	 * From {@code HeatblastClient.renderExtras} (Heatblast's body, everyone sees it): the white-hot flare round each fist (riding the bent
	 * forearm) and the glow round the igniting head.
	 */
	public static void renderAlien(PoseStack pose, MultiBufferSource buffers, Player p, PlayerModel<?> model, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.HEATBLAST.id());
		if (t < TRANSFORM_AT) {
			return;
		}
		float heat = fistHeat(t) * (1f - smooth((t - 194f) / 6f));
		// final-pose rework: from the guard on the fists are the show's flat white fists, a shell just over the hand with a thin haze
		// (the flare's blazing blocks read far bigger than the show's)
		float tight = smooth((t - GUARD_AT) / 6f);
		if (heat > 0.02f) {
			for (int side = 0; side < 2; side++) {
				ModelPart arm = side == 0 ? model.rightArm : model.leftArm;
				int limb = side == 0 ? BentLimbs.RIGHT_ARM : BentLimbs.LEFT_ARM;
				float cx = side == 0 ? -1f : 1f;
				int s = side;
				AlienParts.on(pose, arm, () -> {
					BentLimbs.toForearm(pose, limb, false);
					// the white-hot fist: a blazing block round the hand, swelling and shimmering
					float wob = 0.15f * Mth.sin(t * 2.3f + s * 1.7f);
					// additive (eyes) shells: white-hot just over the hand, a warm haze round it -- light, not a lit block
					VertexConsumer add = PyroDraw.additive(buffers);
					float yc = 8.6f;
					float r = Mth.lerp(tight, 2.2f + 0.35f * heat + wob * 0.5f, 2.08f + 0.12f * heat + wob * 0.2f);
					AlienParts.box(pose, add, cx - r, yc - r * 0.8f, -r, cx + r, yc + r, r, 0f, 0f, 1f, 1f, ShowMath.grey(0.95f * Math.min(1f, heat)),
							LightTexture.FULL_BRIGHT);
					float r2 = r + Mth.lerp(tight, 0.7f + 0.4f * heat + wob, 0.3f + 0.12f * heat + wob * 0.3f);
					AlienParts.box(pose, add, cx - r2, yc - r2 * 0.8f, -r2, cx + r2, yc + r2, r2, 0f, 0f, 1f, 1f,
							ShowMath.scaleRgb(0xFFD890, Mth.lerp(tight, 0.45f, 0.3f) * Math.min(1f, heat)), LightTexture.FULL_BRIGHT);
					Vec3 at = new Vec3(cx / 16f, yc / 16f, 0);
					float g = Mth.lerp(tight, 1f, 0.5f);
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at, (0.34f * heat + 0.08f) * g, PyroDraw.dim(0xFFFFE6A0, 0.95f));
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at, (0.62f * heat + 0.1f) * g, PyroDraw.dim(0xFFFF9030, 0.7f));
				});
			}
		}
		float fire = headFire(p, partial);
		if (fire > 0.02f) {
			AlienParts.on(pose, model.head, () -> {
				Vec3 at = new Vec3(0, -0.3f, 0);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at, 0.42f * Math.min(fire, 1.4f), PyroDraw.dim(0xFFFFE0A0, 0.85f));
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, -0.55f, 0), 0.7f * Math.min(fire, 1.4f), PyroDraw.dim(0xFFFF8A30, 0.6f));
			});
		}
	}

	// ================================================================ in the world

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 36f);
		// the magma heat round the body as the head ignites and the fists flare
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, FLAME_AT - 1f, 136f, 0x5A1A06, 0x7A4A16);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t < SLAP_END) {
			// the RV's brown interior
			st.gradient(0x9A6A44, 0x6B4428, 0x2E1A0C);
		} else if (t < ARM_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < FACE_AT) {
			// amber light behind the arm, black dots
			st.gradient(0xF0C060, 0xC08A34, 0x5C3A12);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - ARM_AT, ARM_DOTS);
		} else if (t < CHEST_AT) {
			// the warm light behind the face
			st.gradient(0xFFE2B0, 0xE9A86A, 0xA0502A);
			st.rays(st.seed + 9, t - FACE_AT, 0xFFF0D0, 10);
		} else if (t < WHITE_AT + 4f) {
			// black, crossed by khaki splotches
			st.fill(0x050505, 1f);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - CHEST_AT, KHAKI_DOTS);
		} else {
			st.gradient(0x9CFA6A, 0x3CD620, 0x14820C);
			st.rays(st.seed + 3, t - WHITE_AT, 0x07300A, 12);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)) + 5, t - WHITE_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 5f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 8f, 1f, 21f, 4f, 1f);
		ShowShots.dialBloom(st, t, DIAL_AT, ARM_AT - 5f);
		if (t >= EYE_AT && t < BLACK_AT + 1f) {
			eye(st, t);
		}
		if (t >= BLACK_AT && t < CRACK_AT + 1f) {
			streak(st, t);
		}
		if (t >= CRACK_AT && t < CHEST_AT + 0.5f) {
			cracks(st, t);
		}
		// the white flash: held, then thinning slowly off the stance (the show keeps it milky to ~166)
		float w = env(t, WHITE_AT, 1f, POSE_AT + 1.5f, 15f);
		if (w > 0f) {
			st.fill(0xFFFFFF, w);
		}
	}

	/** The eye close-up: the white of the eye, Ben's green iris with its dark rim, a green light streak, then the swirl into black. */
	private static void eye(Stage st, float t) {
		float u = t - EYE_AT;
		VertexConsumer flat = st.flat();
		PoseStack.Pose p = st.last();
		// the white of the eye, shading grey toward the lid on the left
		st.radial(flat, p, 0xF2F4EE, 0xC4C8C0, 0x6C706A, 1f, 1f);
		st.rect(flat, p, -1.5f, -1.5f, -0.25f, 1.5f, 0x2A2E1A, 0.85f, 0f);
		VertexConsumer dots = st.dots();
		float k = st.k();
		float cx = (0.12f - 0.02f * u) * k, cy = 0.04f;
		float r = 0.62f + 0.03f * u;
		st.dot(dots, p, cx, cy, r * 1.12f, 0x1C2006, 255);
		st.dot(dots, p, cx, cy, r, 0x9EB41C, 255);
		st.dot(dots, p, cx + 0.05f * k, cy - 0.06f, r * 0.82f, 0xB2C82C, 255);
		float pr = 0.16f + 0.05f * smooth(u / 4f);
		st.dot(dots, p, cx, cy, pr, 0x030303, 255);
		// the light streak coming in from the left
		float sv = env(t, EYE_AT, 1f, EYE_AT + 4f, 2f);
		if (sv > 0f) {
			line(st, st.flat(), p, -1.2f, -0.55f, cx - 0.1f * k, cy - 0.05f, 0.16f, 0xC6E44A, 0.75f * sv);
			line(st, st.flat(), p, -1.2f, -0.55f, cx - 0.1f * k, cy - 0.05f, 0.05f, 0xEFFFB0, 0.6f * sv);
		}
		// the swirl: olive smears wheeling round the pupil, the dark swallowing the frame
		float sw = smooth((u - 4f) / 3f);
		if (sw > 0f) {
			for (int i = 0; i < 7; i++) {
				float a = i * 0.9f + u * 0.35f;
				float x0 = cx + Mth.cos(a) * 0.15f * k, y0 = cy + Mth.sin(a) * 0.15f;
				float x1 = cx + Mth.cos(a + 1.2f) * 1.6f * k, y1 = cy + Mth.sin(a + 1.2f) * 1.6f;
				line(st, st.flat(), p, x0, y0, x1, y1, 0.18f, i % 2 == 0 ? 0x5C6A10 : 0x9AAE30, 0.45f * sw);
			}
			st.dot(st.dots(), p, cx, cy, 0.2f + 1.1f * sw * sw, 0x0A0C02, Math.round(170 * sw));
		}
		ShowShots.flash(st, t, 0x000000, BLACK_AT - 1.5f, 1.5f, BLACK_AT + 1f, 0.1f, 1f);
	}

	/** Black, a hot white streak slashed up across it from the bottom left (the show's angle), thinning to a line and fading. */
	private static void streak(Stage st, float t) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		st.fill(vc, p, 0x000000, 1f);
		float u = t - BLACK_AT;
		float vis = env(t, BLACK_AT + 1.5f, 1.5f, CRACK_AT - 4f, 3f);
		if (vis <= 0f) {
			return;
		}
		float off = 0.08f * smooth(u / 14f);
		float x0 = -0.82f + off, x1 = 0.58f + off;
		float w = Mth.lerp(smooth((u - 2f) / 7f), 0.09f, 0.006f);
		line(st, vc, p, x0, -1.2f, x1, 1.2f, w * 4f, 0xB8A060, 0.3f * vis);
		line(st, vc, p, x0, -1.2f, x1, 1.2f, w * 1.8f, 0xE8DCA8, 0.55f * vis);
		line(st, vc, p, x0, -1.2f, x1, 1.2f, w, 0xFFFAE6, vis);
	}

	/** The first crack (screen units): bottom left, jagged, up past the middle; and the branch off it. */
	private static final float[][] CRACK_A = { { -1.15f, -1.15f, -0.78f, -0.72f, -0.48f, -0.46f, -0.12f, -0.26f, 0.14f, -0.06f, 0.3f, 0.28f, 0.33f, 0.66f,
			0.42f, 1.15f }, { 0.3f, 0.28f, 0.52f, 0.24f, 0.6f, 0.3f } };
	/** The closer web: an arch with branches up off it. */
	private static final float[][] CRACK_B = { { -1.2f, -1.15f, -0.82f, -0.55f, -0.42f, 0.18f, 0.1f, 0.32f, 0.42f, 0.08f, 0.68f, -0.26f, 0.98f, -0.62f },
			{ -0.42f, 0.18f, -0.5f, 0.6f, -0.56f, 1.15f }, { 0.1f, 0.32f, 0.2f, 0.7f, 0.28f, 1.15f }, { -0.46f, 0.36f, -0.66f, 0.58f },
			{ 0.68f, -0.26f, 0.9f, -0.1f } };

	/** Dark red rock, magma cracks splitting across it (one, then the web closer), glowing; then the whip pan's smear. */
	private static void cracks(Stage st, float t) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		boolean web = t >= WEB_AT;
		float u = web ? t - WEB_AT : t - CRACK_AT;
		float heat = smooth((t - CRACK_AT) / 7f);
		st.radial(vc, p, ShowMath.lerpRgb(heat, 0x4A0404, 0xB01010), ShowMath.lerpRgb(heat, 0x2A0202, 0x8A0808), ShowMath.lerpRgb(heat, 0x0C0000, 0x4A0404), 1f, 1f);
		float[][] lines = web ? CRACK_B : CRACK_A;
		// the web zooms in as it glows
		float zoom = web ? 1f + 0.12f * smooth(u / 4f) : 1f;
		float core = web ? 0.045f + 0.02f * smooth(u / 3f) : 0.016f + 0.014f * smooth(u / 4f);
		List<float[]> knots = new ArrayList<>();
		for (int c = 0; c < lines.length; c++) {
			float[] pl = lines[c];
			float g = web ? (c == 0 ? smooth(u / 1.5f + 0.4f) : smooth((u - 0.5f - c * 0.4f) / 2f)) : (c == 0 ? smooth(u / 3.5f) : smooth((u - 2.5f) / 1.5f));
			int segs = pl.length / 2 - 1;
			float shown = g * segs;
			for (int s = 0; s < segs && s < shown; s++) {
				float f = Math.min(1f, shown - s);
				float x0 = pl[s * 2] * zoom, y0 = pl[s * 2 + 1] * zoom;
				float x1 = Mth.lerp(f, x0, pl[s * 2 + 2] * zoom), y1 = Mth.lerp(f, y0, pl[s * 2 + 3] * zoom);
				line(st, vc, p, x0, y0, x1, y1, core * 3.6f, 0xFF5A10, 0.28f);
				line(st, vc, p, x0, y0, x1, y1, core * 1.8f, 0xFFB070, 0.55f);
				line(st, vc, p, x0, y0, x1, y1, core, 0xFFF6E0, 1f);
				knots.add(new float[] { x1, y1 });
			}
		}
		// a round knuckle at each bend, so the joints never notch (the dot buffer last: switching buffers ends the flat one)
		VertexConsumer dots = st.dots();
		for (float[] kn : knots) {
			st.dot(dots, p, kn[0], kn[1], core * 1.05f, 0xFFF6E0, 255);
		}
		vc = st.flat();
		// the smear into the chest shot
		float smear = env(t, SMEAR_AT - 1f, 1.5f, CHEST_AT - 1f, 1f);
		if (smear > 0f) {
			st.fill(vc, p, 0x9A2424, 0.55f * smear);
			ShowShots.whipStreaks(st, t, SMEAR_AT - 1f, CHEST_AT - 0.3f);
		}
	}

	/** A straight band from (x0, y0) to (x1, y1) in screen units, {@code w} screen heights thick. */
	static void line(Stage st, VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float w, int rgb, float a) {
		float asp = st.aspect();
		float dx = (x1 - x0) * asp, dy = y1 - y0;
		float len = Mth.sqrt(dx * dx + dy * dy);
		if (len < 1e-4f) {
			return;
		}
		float nx = -dy / len * w / asp, ny = dx / len * w;
		st.put(vc, p, x0 + nx, y0 + ny, rgb, a);
		st.put(vc, p, x1 + nx, y1 + ny, rgb, a);
		st.put(vc, p, x1 - nx, y1 - ny, rgb, a);
		st.put(vc, p, x0 - nx, y0 - ny, rgb, a);
	}
}
