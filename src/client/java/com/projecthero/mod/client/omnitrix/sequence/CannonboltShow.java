package com.projecthero.mod.client.omnitrix.sequence;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixLongTransform;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Cannonbolt's
 * transformation, shot for shot from the compilation video (tick = (v - 252.15) * 20). The beats (ticks after the right-click):
 * <pre>
 *   0-7     the slap: front medium shot, the right hand raised high and brought down on the dial
 *   6-20    FULL GREEN FLASH
 *   8-28    the dial close-up, the green thinning into rays
 *   28-35   low under the forearm: it starts to swell (green ray field, black Kirby dots)
 *   35-68   low front: the hand balloons, pink and smooth, Ben staring down at it
 *   68-92   the camera sinks: his shirt balloons into a huge round belly, his head peeking over it
 *   92-95   the face from below
 *   95-108  an extreme close-up of his eye; it snaps open YELLOW (104)
 *   106-126 ORANGE DISSOLVE -- the body changes under it (112); the shell plates from behind
 *   126-156 the profile, then the face on his chest and the badge, pushing in
 *   154-161 the plates close round him
 *   160-192 the ball spinning in place (olive field, heavy diagonal streams of dots)
 *   192-200 it rocks to a stop, face front, and opens out
 *   200-209 the arms-wide stance
 *   209-216 WHITE FLASH (fading off to 226)
 *   213-243 the stance from low in front, on the bright green ray burst
 *   243-257 FULL GREEN FLASH, then the world, the green fading off by 273
 * </pre>
 * The ball is drawn by {@code CannonboltClient} from {@link #ball}, so everyone nearby sees him curl, spin and open out.
 */
public final class CannonboltShow extends ShowSequenceClient {
	static final float SLAP_END = 7f, DIAL_END = 28f, FOREARM_AT = 28f, HAND_AT = 35f, BELLY_AT = 68f, FACE_AT = 92f, EYE_AT = 95f, OPEN_AT = 104f,
			ORANGE_AT = 106f, PROFILE_AT = 126f, FACE2_AT = 130f, PLATES_AT = 154f, BALL_AT = 160f, SETTLE_AT = 192f, UNCURL_AT = 195f, OPEN_END = 200f,
			WHITE_AT = 209f, STANCE_AT = 213f, GREEN2_AT = 243f, WORLD_AT = 257f;
	static final int TRANSFORM_AT = 112;

	private static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/omnitrix/sequence/cannonbolt/flesh.png");
	private static final ResourceLocation SHIRT = ProjectHeroMod.id("textures/entity/omnitrix/sequence/cannonbolt/shirt.png");

	/** Every Kirby-dot shot is on the shared show green ({@link Stage#showBackground}); the swelling shots add light rays. */
	private static final int RAY_LIGHT = 0xF4FFC8;
	private static final int BURST_IN = 0xB4FF8E, BURST_MID = 0x46DA3A, BURST_OUT = 0x1A7A16, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;

	private static final KirbyDots.Style SWELL_DOTS = KirbyDots.Style.show().density(1.35f).rim(1.6f).size(1.2f),
			BALL_DOTS = KirbyDots.Style.show().bands(3).angle(58f).spread(8f).density(1.7f).width(1.5f).size(1.15f).rim(1.3f).clear(0.38f),
			BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).clear(0.55f).flow(1.6f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(new float[][] { { 0f, 1.75f, 8f, 1.4f, 1.22f, 0f, 0.15f }, { SLAP_END, 1.6f, 8f, 1.4f, 1.22f, 0f, 0.15f } }, ShowShots.dialCloseUp(SLAP_END + 0.01f, DIAL_END),
			new float[][] {
					// under the forearm, looking up along it
					{ 28.01f, 0.34f, -62f, 0.78f, 1.02f, -0.12f, 0.42f },
					{ 35f, 0.3f, -55f, 0.8f, 1.02f, -0.12f, 0.42f },
					// low in front: the hand coming at the camera, his head up in the corner (v0.17.4: in close, the ballooning hand big in
					// the foreground with his head small behind it, like the show's; it was a full-length shot)
					{ 35.01f, 0.72f, 12f, 0.55f, 1.2f, -0.14f, 0.26f },
					{ 66f, 0.62f, 8f, 0.55f, 1.22f, -0.12f, 0.26f },
					// sinking under the belly (v0.17.4: the belly filling the frame)
					{ 76f, 0.7f, 4f, 0.5f, 1.22f, 0f, 0.1f },
					{ 92f, 0.64f, 0f, 0.5f, 1.26f, 0f, 0.1f },
					// the face from below
					{ 92.01f, 0.95f, 8f, 1.45f, 1.7f, 0f, 0.05f },
					{ 95f, 0.9f, 8f, 1.45f, 1.7f, 0f, 0.05f },
					// right on his eye
					{ 95.01f, 0.22f, -8f, 1.7f, 1.69f, 0.13f, 0.2f },
					{ 112f, 0.17f, -8f, 1.7f, 1.69f, 0.13f, 0.2f },
					// the shell plates from behind, under the orange
					{ 112.01f, 0.95f, 160f, 1.62f, 1.3f, 0f, 0f },
					{ 126f, 0.82f, 140f, 1.55f, 1.32f, 0f, 0f },
					// the profile
					{ 126.01f, 0.95f, 78f, 1.3f, 1.45f, 0f, 0.1f },
					{ 130f, 0.9f, 72f, 1.3f, 1.45f, 0f, 0.1f },
					// the face on his chest and the badge, pushing in
					{ 130.01f, 0.55f, 24f, 1.25f, 1.5f, 0.02f, 0.15f },
					{ 154f, 0.34f, 20f, 1.36f, 1.6f, 0.05f, 0.2f },
					// the plates closing
					{ 154.01f, 1.4f, 30f, 1.3f, 1.0f, 0f, 0f },
					{ 160f, 1.3f, 30f, 1.3f, 1.0f, 0f, 0f },
					// the ball, and it opening out
					{ 160.01f, 3.3f, 0f, 1.0f, 0.92f, 0f, 0f },
					// (v0.17.4: pushing in as he opens out, so the uncurling body stays as big as the ball, like the show's)
					{ SETTLE_AT, 3.1f, 0f, 1.0f, 0.94f, 0f, 0f },
					{ OPEN_END + 3f, 1.9f, 0f, 0.95f, 0.98f, 0f, 0f },
					{ 213f, 1.8f, 0f, 0.95f, 1.0f, 0f, 0f },
					// the stance, from low in front (final-pose rework: lower and looking up more steeply, so he fills the frame from the
					// clawed feet to the shell domes over his raised arms)
					{ 213.01f, 1.75f, 0f, 0.28f, 1.16f, 0f, 0f },
					{ 257f, 1.6f, 0f, 0.28f, 1.2f, 0f, 0f } }));

	public CannonboltShow() {
		super(Aliens.CANNONBOLT.id());
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
		if (t >= SLAP_END && t < DIAL_END) {
			fov -= 8 * smooth((t - SLAP_END) / 20f);
		} else if (t >= EYE_AT && t < TRANSFORM_AT) {
			fov -= 10 * smooth((t - EYE_AT) / 14f);
		}
		return fov;
	}

	// ================================================================ the poses

	/**
	 * v0.17.3 touch-up (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"): the
	 * torso turns through {@link ShowBody} (about the hips, the shoulders following, so nothing floats off), and the elbows and knees
	 * bend ({@link BentLimbs}): the raised slap hand, the swelling hand lifted up at his face, the arms forced round the belly, the arms
	 * tucked under the shell, and the show's squat stance with the arms flung up and out.
	 */
	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t < TRANSFORM_AT) {
			human(m, t);
		} else {
			cannonbolt(m, t);
		}
		ShowBody.layers(m);
	}

	/** Ben: the slap, the swelling hand lifted to his face, the belly pushing his arms out round it, the head tipped back over it. */
	private static void human(HumanoidModel<?> m, float t) {
		if (t < HAND_AT) {
			// the right hand thrown up by his head, open, the elbow bent; then brought down onto the dial
			float up = smooth(t / 2f) * (1f - smooth((t - 4f) / 2.5f));
			float down = smooth((t - 4f) / 2.5f) * (1f - smooth((t - 16f) / 8f));
			m.rightArm.xRot = Mth.lerp(up, m.rightArm.xRot, -0.4f);
			m.rightArm.yRot = Mth.lerp(up, m.rightArm.yRot, 0f);
			m.rightArm.zRot = Mth.lerp(up, m.rightArm.zRot, 2.5f);
			rot(m.rightArm, down, -1.25f, -0.55f, 0.1f);
			BentLimbs.bend(BentLimbs.RIGHT_ARM, 0.6f * up + 0.4f * down, 1.4f * up);
			m.head.xRot = Mth.lerp(smooth(t / 3f), m.head.xRot, 0.35f);
			if (t >= FOREARM_AT) {
				float shake = smooth((t - FOREARM_AT) / 6f);
				m.leftArm.xRot += 0.04f * shake * Mth.sin(t * 3.1f);
				m.leftArm.zRot += 0.04f * shake * Mth.cos(t * 3.7f);
			}
			return;
		}
		float tremble = Mth.sin(t * 3.3f) * 0.04f;
		float h = smooth((t - HAND_AT) / 6f), b = smooth((t - BELLY_AT) / 10f);
		// the swelling hand lifted up toward the camera, Ben staring at it; the other arm flung back
		rot(m.leftArm, h, -0.75f + tremble, 0f, -0.45f);
		rot(m.rightArm, h, 0.45f, 0f, 0.6f + tremble);
		rot(m.head, h, 0.5f, -0.3f, 0.06f);
		// the belly: the arms forced out round it, the forearms hugging its sides; the head thrown back over the top
		rot(m.leftArm, b, -0.5f + tremble, 0.35f, -1.0f);
		rot(m.rightArm, b, -0.5f - tremble, -0.35f, 1.0f);
		rot(m.head, b, t < FACE_AT ? -0.3f : -0.18f, 0f, 0f);
		rot(m.rightLeg, b, -0.1f, 0f, 0.25f);
		rot(m.leftLeg, b, -0.1f, 0f, -0.25f);
		ShowBody.twist(m, Mth.lerp(b, -0.12f * h, 0f));
		ShowBody.lean(m, Mth.lerp(b, -0.08f * h, -0.12f));
		BentLimbs.bend(BentLimbs.LEFT_ARM, Mth.lerp(b, 0.9f * h, 1.4f), Mth.lerp(b, 0.15f * h, 0f));
		BentLimbs.bend(BentLimbs.RIGHT_ARM, Mth.lerp(b, 0.5f * h, 1.4f), 0f);
		BentLimbs.bend(BentLimbs.RIGHT_LEG, 0.35f * b, 0.5f * b);
		BentLimbs.bend(BentLimbs.LEFT_LEG, 0.35f * b, -0.5f * b);
	}

	/** Right arm {x, y, z, elbow, twist}, right leg {x, y, z, knee, twist}, head x, lean, drop (px): tucked under the shell, and the stance. */
	private static final float[] TUCK = { -0.35f, 0.1f, 0.35f, 1.9f, -0.45f, -0.2f, 0f, 0.3f, 0.35f, 1.57f, -0.12f, 0.3f, 0.6f },
			// final-pose rework (the show's t 237): squat -- the thighs forward toward the camera and the shins bent back to near
			// vertical (short, bent legs from the low camera; they were splayed straight out to the corners), knees a little out; the
			// arms up a touch higher and wider in the V; leaning in over the knees
			// v0.17.4: the arms out near level along the shoulder line (the show's row of domes across the top of the frame), not a V
			STANCE = { -0.35f, 0.1f, 1.45f, 0.45f, 1.57f, -0.75f, 0f, 0.28f, 1.0f, 0.35f, -0.3f, 0.25f, 2.2f };
	/** Final-pose rework: the right knee comes up a little higher than the left (thigh, knee), the show's uneven squat. */
	private static final float KNEE_UP_THIGH = -0.15f, KNEE_UP_BEND = 0.15f;

	/** Cannonbolt: hunched with his arms tucked in under the new shell (the face shots), then the squat stance, arms flung up and out. */
	private static void cannonbolt(HumanoidModel<?> m, float t) {
		float breathe = Mth.sin(t * 0.25f) * 0.04f;
		float s = t < OPEN_END ? 0f : smooth((t - OPEN_END) / 4f);
		float[] k = new float[TUCK.length];
		for (int i = 0; i < k.length; i++) {
			k[i] = Mth.lerp(s, TUCK[i], STANCE[i]);
		}
		ShowBody.reset(m, k[12]);
		ShowBody.lean(m, k[11]);
		m.head.xRot = k[10] + breathe * 0.5f;
		rot(m.rightArm, 1f, k[0], k[1], k[2] + breathe);
		rot(m.leftArm, 1f, k[0], -k[1], -k[2] - breathe);
		rot(m.rightLeg, 1f, k[5] + KNEE_UP_THIGH * s, k[6], k[7]);
		rot(m.leftLeg, 1f, k[5], -k[6], -k[7]);
		ShowBody.bend(k[3] + breathe, k[4], k[3] - breathe, -k[4], k[8] + KNEE_UP_BEND * s, k[9], k[8], -k[9]);
	}

	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		if (t >= TRANSFORM_AT) {
			return;
		}
		float throb = 1f + 0.04f * Mth.sin(t * 1.7f);
		float arm = 1f + 0.55f * smooth((t - FOREARM_AT - 1f) / 30f) * throb;
		float belly = smooth((t - BELLY_AT) / 16f) * throb;
		float rArm = 1f + 0.35f * smooth((t - BELLY_AT - 4f) / 16f) * throb;
		float head = 1f + 0.18f * smooth((t - 84f) / 10f) * throb;
		m.leftArm.xScale = m.leftArm.zScale = arm;
		m.rightArm.xScale = m.rightArm.zScale = rArm;
		m.body.xScale = 1f + 0.8f * belly;
		m.body.zScale = 1f + 1.4f * belly;
		m.head.xScale = m.head.yScale = m.head.zScale = head;
		m.hat.copyFrom(m.head);
		m.leftArm.x += (arm - 1f) * 1.5f + belly * 3.2f;
		m.rightArm.x -= (rArm - 1f) * 1.5f + belly * 3.2f;
	}

	/** {bone (0 left arm, 1 body, 2 right arm), x, y, z (px), half-size (px), erupts at}: the hand ballooning, then the belly. */
	private static final float[][] HAND = { { 0, 1f, 9.2f, 0f, 2.4f, 33 }, { 0, 1.4f, 8.6f, -1.2f, 2.6f, 37 }, { 0, 0.6f, 9.8f, 0.8f, 2.8f, 41 },
			{ 0, 1f, 9.5f, 0f, 3.4f, 46 }, { 0, 1f, 7f, 0f, 2.8f, 50 }, { 0, 1f, 9.6f, -0.4f, 3.9f, 55 } };
	/** A bone with no shape and no transform: lumps drawn in a pose already moved onto the forearm. */
	private static final ModelPart[] HERE = { new ModelPart(List.of(), Map.of()) };
	private static final float[][] BELLY = { { 1, 0f, 7.5f, -1.2f, 3.6f, 70 }, { 1, 0f, 6.5f, -1.4f, 4.4f, 75 }, { 1, 0f, 7f, -0.8f, 5f, 80 } };

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HAND_AT - 4f || t >= TRANSFORM_AT) {
			return;
		}
		ModelPart[] bones = { model.leftArm, model.body, model.rightArm };
		// the hand's lumps ride the bent forearm (they sit below the elbow)
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		AlienParts.on(pose, model.leftArm, () -> {
			BentLimbs.toForearm(pose, BentLimbs.LEFT_ARM, slim);
			SwellLumps.render(pose, buffers, HERE, HAND, t, 7f, FLESH, null);
		});
		if (t >= BELLY_AT) {
			SwellLumps.render(pose, buffers, bones, BELLY, t, 9f, SHIRT, null);
		}
	}

	// ================================================================ the shell domes in the stance (CannonboltClient)

	/** The show's shell plates: a yellow dome in a black rim, a lighter cap, a highlight. */
	private static final int DOME_RIM = 0xFF1C1A14, DOME_GOLD = 0xFFE09A1A, DOME_CAP = 0xFFF0B83A, DOME_SHINE = 0xFFFFE7A0;

	/**
	 * Final-pose rework (the show fans big yellow shell domes across the top of the frame over his raised arms; his skin only paints
	 * the plates, which read as grey arm blocks from the low camera): from {@code CannonboltClient.renderExtras}, while his sequence
	 * has him open out of the ball and through the stance, a big dome on each upper arm by the shoulder and a smaller one on each
	 * forearm, facing the front and straddling the arm's upper edge (bone -x on the raised right arm, +x on the left) so they fan out
	 * over the V. The forearm ones ride the bent forearm ({@link BentLimbs#toForearm}; its twisted frame's +x faces the front on the
	 * right arm, -x on the left). Everyone nearby sees them.
	 */
	public static void renderAlien(PoseStack pose, MultiBufferSource buffers, int light, Player p, PlayerModel<?> model, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.CANNONBOLT.id());
		if (t < OPEN_END) {
			return;
		}
		float g = ShowMath.pop(t - OPEN_END, 4f);
		if (g <= 0.02f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(Shards.WHITE));
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			float out = right ? -1f : 1f, xc = right ? -1f : 1f;
			int limb = right ? BentLimbs.RIGHT_ARM : BentLimbs.LEFT_ARM;
			ModelPart arm = right ? model.rightArm : model.leftArm;
			// the big dome by the shoulder, in the arm's bone space
			AlienParts.on(pose, arm, () -> {
				pose.pushPose();
				pose.translate((xc + out * 1.5f) / 16f, 1.2f / 16f, -2f / 16f);
				dome(pose, vc, 3.3f * g, light);
				pose.popPose();
			});
			// the smaller one on the forearm, toward the hand
			AlienParts.on(pose, arm, () -> {
				BentLimbs.toForearm(pose, limb, false);
				pose.pushPose();
				pose.translate((xc - out * 2f) / 16f, 7.4f / 16f, 0f);
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(right ? -90f : 90f));
				dome(pose, vc, 2.5f * g, light);
				pose.popPose();
			});
		}
	}

	/** One dome facing -z at the origin, {@code r} px across its rim; it sinks 0.9 px into whatever it sits on. */
	private static void dome(PoseStack pose, VertexConsumer vc, float r, int light) {
		disc(pose, vc, r, -0.35f, 0.9f, DOME_RIM, light);
		disc(pose, vc, r * 0.82f, -0.62f, 0f, DOME_GOLD, light);
		disc(pose, vc, r * 0.55f, -0.88f, -0.5f, DOME_CAP, light);
		float s = r * 0.16f;
		AlienParts.box(pose, vc, -r * 0.42f - s, -r * 0.42f - s, -0.94f, -r * 0.42f + s, -r * 0.42f + s, -0.8f, 0f, 0f, 1f, 1f, DOME_SHINE, light);
	}

	/** A round slab facing -z (a stack of boxes), radius {@code r}, from {@code z0} to {@code z1}. */
	private static void disc(PoseStack pose, VertexConsumer vc, float r, float z0, float z1, int argb, int light) {
		int n = 5;
		for (int i = 0; i < n; i++) {
			float a = (i + 0.5f) / n * Mth.HALF_PI;
			float hx = Mth.cos(a) * r, hy = Mth.sin(a) * r;
			AlienParts.box(pose, vc, -hx, -hy, z0, hx, hy, z1, 0f, 0f, 1f, 1f, argb, light);
		}
	}

	// ================================================================ the ball (CannonboltClient)

	/**
	 * While his sequence curls him up: {curl (0 standing .. 1 the closed ball), the roll (radians), the heading (degrees, local +Z to it),
	 * the tilt of the spin (radians)}; null otherwise. {@code CannonboltClient} draws his ball / curl from it.
	 */
	public static float[] ball(Player p, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.CANNONBOLT.id());
		if (t < PLATES_AT || t >= OPEN_END) {
			return null;
		}
		// the plates shot opens halfway through the tuck (the plates already sweeping round); the uncurl hands over to the standing body
		// at 0.7, as he springs open (deeper, the curl's figure is drawn smaller than his body)
		float curl = t < UNCURL_AT ? 0.55f + 0.45f * smooth((t - PLATES_AT) / (BALL_AT + 1f - PLATES_AT))
				: 1f - 0.3f * smooth((t - UNCURL_AT) / (OPEN_END - UNCURL_AT));
		// spinning hard, then slowing to rock back face-front: five whole turns, so it settles where the face is
		float u = Mth.clamp((t - BALL_AT - 1f) / (SETTLE_AT + 2f - BALL_AT - 1f), 0f, 1f);
		float roll = Mth.TWO_PI * 5f * (1f - (1f - u) * (1f - u) * (1f - u));
		float tilt = -0.55f * (1f - smooth((t - SETTLE_AT + 4f) / 6f));
		return new float[] { Math.max(curl, 0.0001f), roll, -OmnitrixLongTransform.yaw(Omnitrix.state(p)), tilt };
	}

	// ================================================================ the world: the dial's light

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 5f, 26f);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		int shot = TRACK.shot(t);
		if (t < SLAP_END) {
			// the misty white canyon behind the slap
			st.gradient(0xF2F2EE, 0xD8D8D2, 0xA9AAA2);
		} else if (t < FOREARM_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
			st.rays(st.seed, t - SLAP_END, 0x2E9A1E, 18);
		} else if (t < TRANSFORM_AT) {
			st.showBackground();
			st.rays(st.seed + shot, t - FOREARM_AT, RAY_LIGHT, 20);
			KirbyDots.draw(st, st.shotSeed(shot), t - FOREARM_AT, SWELL_DOTS);
		} else if (t < BALL_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), t - TRANSFORM_AT, SWELL_DOTS);
		} else if (t < STANCE_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), t - BALL_AT, BALL_DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed + 9, t - STANCE_AT, BURST_RAY, 16);
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - STANCE_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 6f);
		ShowShots.dialBloom(st, t, 14f, DIAL_END);
		// the eye snapping open, yellow
		float open = smooth((t - OPEN_AT) / 3f) * (1f - smooth((t - TRANSFORM_AT - 1f) / 3f));
		if (open > 0f) {
			lens(st, 0.05f, 0.02f, 0.62f, 0.34f * open + 0.02f, -0.5f, 0x0A0604, 1f);
			lens(st, 0.05f, 0.02f, 0.56f, 0.29f * open, -0.5f, 0xE59C12, 1f);
		}
		// the orange dissolve the body changes under, thinning over the shell plates
		ShowShots.flash(st, t, 0xD88A14, ORANGE_AT, 5f, TRANSFORM_AT + 2f, 12f, 0.92f);
		ShowShots.flash(st, t, 0xD88A14, TRANSFORM_AT + 2f, 0.01f, PROFILE_AT - 2f, 3f, 0.35f);
		spinStreaks(st, t);
		// the white flash into the stance
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 1.5f, 215f, 11f, 1f);
		ShowShots.greenOut(st, t, GREEN2_AT, WORLD_AT, total());
	}

	/**
	 * v0.17.3 touch-up: the show's spinning ball is a blur of dark and gold streaks running round it with the spin. Drawn over the ball
	 * (centred on screen, ~0.44 screen heights across at the ball shot's camera), thick while it spins hard, thinning out as it slows.
	 */
	private static void spinStreaks(Stage st, float t) {
		if (t < BALL_AT + 1f || t >= SETTLE_AT + 2f) {
			return;
		}
		float u = Mth.clamp((t - BALL_AT - 1f) / (SETTLE_AT + 2f - BALL_AT - 1f), 0f, 1f);
		float speed = (1f - u) * (1f - u) * smooth((t - BALL_AT - 1f) / 3f);
		if (speed < 0.03f) {
			return;
		}
		float tilt = -0.55f * (1f - smooth((t - SETTLE_AT + 4f) / 6f));
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float k = st.k(), r = 0.35f, cy = 0.01f;
		// the surface runs top to bottom with the forward roll, tipped by the spin's tilt
		float dx = -Mth.sin(tilt), dy = Mth.cos(tilt), px = dy, py = -dx;
		for (int i = 0; i < 26; i++) {
			long sd = 7700L + i * 13L;
			float o = (ShowMath.hash(sd, 0) * 2f - 1f) * 0.85f * r;
			float half = 0.9f * Mth.sqrt(Math.max(r * r - o * o, 0f));
			// each streak is a lit run along its chord, sliding round with the spin
			float len = 0.3f + 0.4f * ShowMath.hash(sd, 1);
			float ph = (ShowMath.hash(sd, 2) + t * (0.08f + 0.05f * ShowMath.hash(sd, 3))) % 1f;
			float s0 = -1f + 2f * ph - len, s1 = s0 + 2f * len;
			s0 = Math.max(s0, -0.97f);
			s1 = Math.min(s1, 0.97f);
			if (s1 <= s0) {
				continue;
			}
			boolean dark = i % 3 != 0;
			int rgb = dark ? 0x3A2408 : 0xF6C850;
			float th = (dark ? 0.007f : 0.005f) * (1f + ShowMath.hash(sd, 4));
			float a = (dark ? 0.5f : 0.4f) * Math.min(1f, speed * 1.6f);
			int n = 8;
			for (int j = 0; j < n; j++) {
				float sa = Mth.lerp(j / (float) n, s0, s1), sb = Mth.lerp((j + 1) / (float) n, s0, s1);
				// bowed like a line of longitude on the ball
				float ba = o * (1f - 0.18f * (1f - sa * sa)), bb = o * (1f - 0.18f * (1f - sb * sb));
				float xa = px * ba + dx * sa * half, ya = cy + py * ba + dy * sa * half;
				float xb = px * bb + dx * sb * half, yb = cy + py * bb + dy * sb * half;
				st.put(vc, p, (xa - px * th) * k, ya - py * th, rgb, a);
				st.put(vc, p, (xb - px * th) * k, yb - py * th, rgb, a);
				st.put(vc, p, (xb + px * th) * k, yb + py * th, rgb, a);
				st.put(vc, p, (xa + px * th) * k, ya + py * th, rgb, a);
			}
		}
	}

	/** A pointed almond (a cartoon eye) at {@code cx cy}, {@code w} half-width and {@code h} half-height in screen heights, turned. */
	static void lens(Stage st, float cx, float cy, float w, float h, float ang, int rgb, float a) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float k = st.k(), c = Mth.cos(ang), s = Mth.sin(ang);
		int n = 24;
		for (int i = 0; i < n; i++) {
			float x0 = -w + 2f * w * i / n, x1 = -w + 2f * w * (i + 1) / n;
			float h0 = h * (1f - (x0 / w) * (x0 / w)), h1 = h * (1f - (x1 / w) * (x1 / w));
			float[][] q = { { x0, h0 }, { x1, h1 }, { x1, -h1 }, { x0, -h0 } };
			for (float[] v : q) {
				float rx = v[0] * c - v[1] * s, ry = v[0] * s + v[1] * c;
				st.put(vc, p, cx + rx * k, cy + ry, rgb, a);
			}
		}
	}
}
