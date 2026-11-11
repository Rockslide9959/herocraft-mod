package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Diamondhead's show
 * transformation, shot for shot. Compilation video second {@code v} is tick {@code (v - 61.95) * 20}; the beats:
 * <pre>
 *   0-16    WHITE-GREEN FLASH (the slap is already under it)
 *   16-36   Ben from low in front, the watch arm up: green crystal sprouting from the dial, everything washed green
 *   36-50   the arm flung out to his left: the crystal racing along it to the hand (olive field, heavy black dots)
 *   50-68   the hand close up, crystal swallowing the fingers
 *   68-78   the fist whips out of frame, only the dots
 *   78-112  Ben's face, pushing in: crystal spreading over his left cheek and hair
 *   112-134 the eye close-up: Ben's eye, the yellow flooding up, Diamondhead's hexagonal eye        (the body changes at 126)
 *   132-138 smeared out
 *   138-150 Diamondhead's head in profile
 *   150-190 behind his shoulder, low: the crystal spikes thrusting up off his shoulders and back
 *   190-196 a dark green whip pan
 *   196-208 the crossed fists in front of the chest, the Omnitrix on it
 *   208-240 pulling back: the arms sweep out wide, then one fist up high, the other across the chest
 *   240-254 WHITE FLASH, fading off the final pose
 *   252-278 the crouched stance on the green burst, one fist thrust down at the camera
 *   277-287 FULL GREEN FLASH, then a hard cut back to the world
 * </pre>
 * The crystal and the poses are on the body, so everyone sees them; the eye is the holder's own screen.
 */
public final class DiamondheadShow extends ShowSequenceClient {
	static final float FLASH_END = 16f, OUT_AT = 36f, HAND_AT = 50f, WHIP_AT = 68f, FACE_AT = 78f, EYE_AT = 112f, PROFILE_AT = 137.5f, BACK_AT = 150f,
			WHIP2_AT = 190f, FISTS_AT = 196f, PULL_AT = 208f, HIGH_AT = 221f, WHITE_AT = 240f, POSE_AT = 248f, GREEN_AT = 277f, WORLD_AT = 287f;
	static final int TRANSFORM_AT = 126;

	private static final ResourceLocation CRYSTAL = ProjectHeroMod.id("textures/entity/omnitrix/sequence/diamondhead/crystal.png");

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show().density(1.25f).size(1.35f).width(1.3f).rim(1.15f).clear(0.34f),
			GREEN_DOTS = DOTS.copy().alpha(170),
			BURST_DOTS = KirbyDots.Style.show().bands(2).density(1.0f).size(1.35f).width(1.2f).rim(1.1f).clear(0.48f).flow(1.3f);

	private static final CameraTrack TRACK = new CameraTrack(new float[][] {
			// under the flash: the usual slap framing
			{ 0f, 1.6f, 10f, 1.3f, 1.15f, 0f, 0.15f },
			// Ben from low in front, the watch arm up
			{ 16f, 1.25f, -8f, 0.95f, 1.32f, -0.05f, 0.1f },
			{ 36f, 1.0f, -18f, 1.0f, 1.36f, -0.08f, 0.1f },
			// side on from his left: the arm thrust out forward across the frame, his head at the right
			{ 36.01f, 0.95f, -92f, 1.34f, 1.24f, -0.33f, 0.36f },
			{ 50f, 0.8f, -95f, 1.32f, 1.24f, -0.33f, 0.46f },
			// the hand close
			{ 50.01f, 0.48f, -98f, 1.28f, 1.22f, -0.33f, 0.66f },
			{ 68f, 0.42f, -104f, 1.28f, 1.22f, -0.33f, 0.74f },
			// the whip: off the hand into empty dots
			{ 76f, 0.6f, -40f, 1.5f, 1.45f, -0.3f, 1.4f },
			// the face, pushing in
			{ 76.01f, 0.95f, -4f, 1.62f, 1.6f, 0f, 0f },
			{ 104f, 0.62f, -6f, 1.62f, 1.62f, 0.04f, 0f },
			{ 112f, 0.45f, -6f, 1.62f, 1.64f, 0.06f, 0f },
			// (under the eye)
			{ 112.01f, 0.3f, -6f, 1.62f, 1.64f, 0.06f, 0f },
			{ 137.5f, 0.3f, -6f, 1.62f, 1.64f, 0.06f, 0f },
			// the head in profile, from his left
			{ 137.51f, 0.62f, -78f, 1.6f, 1.66f, 0f, 0.02f },
			{ 150f, 0.56f, -82f, 1.6f, 1.66f, 0f, 0.02f },
			// low behind his left shoulder, looking up at the spikes
			{ 150.01f, 0.75f, -150f, 1.2f, 1.62f, 0.15f, 0f },
			{ 190f, 0.62f, -160f, 1.22f, 1.6f, 0.15f, 0f },
			// the whip round to the front
			{ 196f, 0.55f, -20f, 1.3f, 1.35f, 0f, 0f },
			// the crossed fists, then pulling back
			{ 196.01f, 0.7f, 6f, 1.15f, 1.18f, 0f, 0f },
			{ 208f, 1.1f, 8f, 0.95f, 1.28f, 0f, 0f },
			{ 221f, 1.95f, -10f, 0.7f, 1.05f, 0f, 0f },
			{ 240f, 2.05f, -14f, 0.7f, 1.02f, 0f, 0f },
			// the stance on the burst, from his right front, low
			{ 240.01f, 2.15f, 30f, 0.55f, 0.92f, 0f, 0f },
			{ 287f, 2.0f, 28f, 0.55f, 0.92f, 0f, 0f } }).linear(68f, 76f).linear(190f, 196f);

	public DiamondheadShow() {
		super(Aliens.DIAMONDHEAD.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		m.head.yRot = 0f;
		if (t < TRANSFORM_AT) {
			if (t < OUT_AT) {
				// looking down at the crystal on the raised arm, the other hand at the wrist
				float w = smooth((t - 14f) / 6f);
				rot(m.head, w, 0.3f, 0.35f, 0f);
				rot(m.rightArm, w, -1.25f, 0.45f, 0f);
			} else if (t < FACE_AT - 2f) {
				// the arm flung out to his left, the crystal racing down it
				float w = smooth((t - OUT_AT) / 4f);
				float shake = 0.03f * Mth.sin(t * 3.3f);
				rot(m.leftArm, w, -1.55f + shake, 0.1f, 0f);
				rot(m.rightArm, w, 0.1f, 0f, 0.25f);
				rot(m.head, w, 0.05f, 0.15f, 0f);
			} else {
				// the face: square to the camera, the brow down
				float w = smooth((t - FACE_AT + 2f) / 3f);
				rot(m.head, w, 0.08f + 0.02f * Mth.sin(t * 1.9f), 0.04f, 0.05f);
				rot(m.leftArm, w, 0.1f, 0f, -0.25f);
				rot(m.rightArm, w, 0.1f, 0f, 0.25f);
			}
			HeatblastShow.copyLayers(m);
			return;
		}
		float breathe = Mth.sin(t * 0.3f) * 0.04f;
		if (t < FISTS_AT) {
			// the head and the shoulders: braced, arms down and a little out
			rot(m.rightArm, 1f, 0.05f, 0f, 0.3f + breathe);
			rot(m.leftArm, 1f, 0.05f, 0f, -0.3f - breathe);
			float look = smooth((t - BACK_AT) / 25f);
			rot(m.head, 1f, -0.1f, -0.35f * look, 0f);
		} else if (t < PULL_AT + 4f) {
			// the crossed fists in front of the chest, rising
			float w = smooth((t - FISTS_AT) / 3f);
			float up = smooth((t - FISTS_AT - 4f) / 8f);
			rot(m.rightArm, w, -1.3f - 0.4f * up, -0.62f, 0.05f);
			rot(m.leftArm, w, -1.3f - 0.4f * up, 0.62f, -0.05f);
			rot(m.head, w, 0.1f, 0f, 0f);
		} else if (t < HIGH_AT) {
			// the sweep: the arms thrown out wide, fists up
			float w = smooth((t - PULL_AT - 4f) / 5f);
			rot(m.rightArm, w, -0.35f, 0f, 2.2f + breathe);
			rot(m.leftArm, w, -0.35f, 0f, -2.2f - breathe);
			rot(m.rightLeg, w, 0f, 0f, 0.22f);
			rot(m.leftLeg, w, 0f, 0f, -0.22f);
			rot(m.head, w, -0.12f, 0f, 0f);
		} else if (t < POSE_AT - 2f) {
			// one fist punched up high, the other across the chest, leaning into it
			float w = smooth((t - HIGH_AT) / 4f);
			rot(m.leftArm, w, -2.85f, 0f, -0.28f);
			rot(m.rightArm, w, -1.3f, -0.6f, 0.2f + breathe);
			rot(m.rightLeg, w, 0.15f, 0f, 0.3f);
			rot(m.leftLeg, w, -0.2f, 0f, -0.25f);
			rot(m.head, w, -0.25f, 0.2f, 0f);
			m.body.zRot = Mth.lerp(w, m.body.zRot, 0.08f);
		} else {
			// the crouched stance: legs wide, one fist driven down at the camera, the other flung back
			float w = smooth((t - POSE_AT + 2f) / 4f);
			rot(m.rightArm, w, -0.95f + breathe, 0.1f, 0.55f);
			rot(m.leftArm, w, 0.35f, 0f, -1.15f - breathe);
			rot(m.rightLeg, w, -0.3f, 0f, 0.5f);
			rot(m.leftLeg, w, 0.2f, 0f, -0.48f);
			rot(m.head, w, 0.1f, 0.25f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.22f);
		}
		HeatblastShow.copyLayers(m);
	}

	// ================================================================ the crystal

	/** Crystal patches ({@link HeatblastShow.Plates}): bone (0 left arm, 1 body, 2 head, 3 right arm), box, count, origin, erupt span, size, faces. */
	private static final float[][] PATCHES = {
			// out of the dial, round the forearm
			{ 0, -1, 3, 4, 9, -2, 2, 26, 1, 8, 0, 20, 34, 1.4f, 0b1111 },
			// down the arm to the hand
			{ 0, -1, 3, 8, 12, -2, 2, 24, 1, 8, 0, 38, 56, 1.25f, 0b11111 },
			{ 0, -1, 3, -2, 4, -2, 2, 18, 1, 8, 0, 34, 48, 1.5f, 0b11111 },
			// the left cheek, the brow, the hair (the face is -z)
			{ 2, 0, 4, -8, 0, -4, 4, 22, 4, -1, -4, 78, 100, 1.3f, 0b1001 },
			{ 2, -4, 4, -8, -3, -4, 4, 18, 4, -6, -4, 96, 112, 1.5f, 0b11001 },
			// the shoulder and chest (seen only as the face fills up)
			{ 1, 0, 4, 0, 6, -2, 2, 12, 4, 0, 0, 86, 108, 1.6f, 0b1101 } };
	private static final float[][] PLATES = HeatblastShow.Plates.gen(0x0D1A30EDL, PATCHES);

	/** After the change: the shoulder and back spikes thrusting up (bone 1 body). */
	private static final float[][] SPIKE_PATCHES = { { 1, -4, 4, 0, 4, -2, 2, 14, 0, 0, 2, 152, 182, 2.2f, 0b11000 } };
	private static final float[][] SPIKES = HeatblastShow.Plates.gen(0x5B1CE5L, SPIKE_PATCHES);

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < 18f) {
			return;
		}
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CRYSTAL));
		if (t < TRANSFORM_AT) {
			HeatblastShow.Plates.render(pose, vc, bones, PLATES, t, 6f, 0.7f);
		} else if (t < WHIP2_AT + 2f) {
			HeatblastShow.Plates.render(pose, vc, bones, SPIKES, t, 8f, 1.1f);
		}
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 14f, 30f);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t < FLASH_END) {
			st.gradient(0xF2FFE0, 0x9CF070, 0x5AC830);
		} else if (t < OUT_AT) {
			st.gradient(0x9AF06A, 0x52C82E, 0x1E7A12);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - FLASH_END, GREEN_DOTS);
		} else if (t < WHITE_AT + 6f) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - TRACK.shotStart(t), DOTS);
		} else {
			st.gradient(0x9CFA6A, 0x3EDC22, 0x0E5E08);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)) + 5, t - WHITE_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		// the opening flash: white-green, then the green wash that fades off Ben
		ShowShots.flash(st, t, 0xF0FFE2, -1f, 0.01f, 8f, 6f, 1f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 6f, 3f, 16f, 18f, 0.8f);
		if (t >= EYE_AT && t < PROFILE_AT + 0.5f) {
			eye(st, t);
		}
		ShowShots.whipStreaks(st, t, WHIP_AT, FACE_AT - 1f);
		if (t >= WHIP2_AT && t < FISTS_AT + 0.5f) {
			// the dark green smear
			st.fill(0x0E3A12, 0.85f * env(t, WHIP2_AT, 1f, FISTS_AT - 1f, 1f));
			ShowShots.whipStreaks(st, t, WHIP2_AT, FISTS_AT + 1f);
		}
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 1f, 246f, 10f, 1f);
		ShowShots.greenOut(st, t, GREEN_AT, WORLD_AT, WORLD_AT + 1f);
	}

	/**
	 * The eye close-up, over crystal facets: Ben's eye (white, yellow-green iris, pupil), the yellow flooding up from below, then
	 * Diamondhead's hexagonal yellow eye, smeared away.
	 */
	private static void eye(Stage st, float t) {
		float u = t - EYE_AT;
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float k = st.k();
		st.fill(vc, p, 0x3FBF78, 1f);
		// crystal facets round it
		quad(st, vc, p, -1.5f, 1.5f, -0.3f, 1.5f, -0.55f, 0.55f, -1.5f, 0.2f, 0x8FEFC0);
		quad(st, vc, p, 0.4f, -1.5f, 1.5f, -1.5f, 1.5f, -0.2f, 0.7f, -0.6f, 0x2E9A60);
		quad(st, vc, p, 0.6f, 1.5f, 1.5f, 1.5f, 1.5f, 0.6f, 0.9f, 0.7f, 0xC4F8E0);
		float drift = 0.02f * u;
		float cx = (0.05f - drift) * k, cy = 0.05f;
		float morph = smooth((u - 9f) / 6f);
		float hex = smooth((u - 14f) / 2f);
		if (hex < 1f) {
			// Ben's eye
			ellipse(st, vc, p, cx, cy, 0.62f * k, 0.38f, 24, 0f, 0x0A0A0A, 1f);
			// the white goes yellow as the iris and the pupil melt into it
			ellipse(st, vc, p, cx, cy, 0.58f * k, 0.34f, 24, 0f, ShowMath.lerpRgb(morph, 0xF4F4F0, 0xF4E21E), 1f);
			float iris = 1f - morph;
			ellipse(st, vc, p, cx + 0.06f * k, cy + 0.06f, 0.27f * k, 0.27f, 24, 0f, 0x1A1A08, iris);
			ellipse(st, vc, p, cx + 0.06f * k, cy + 0.06f, 0.24f * k, 0.24f, 24, 0f, 0xC6DA1E, iris);
			ellipse(st, vc, p, cx + 0.08f * k, cy + 0.08f, 0.1f * k, 0.1f, 18, 0f, 0x050505, iris);
		}
		if (hex > 0f) {
			// Diamondhead's eye: a black-rimmed yellow hexagon, a white glint
			ellipse(st, vc, p, cx, cy, 0.66f * k, 0.36f, 6, 0f, 0x050505, hex);
			ellipse(st, vc, p, cx, cy, 0.6f * k, 0.31f, 6, 0f, 0xF2E01E, hex);
			ellipse(st, vc, p, cx - 0.12f * k, cy + 0.12f, 0.16f * k, 0.06f, 12, 0f, 0xFFFBD0, hex);
		}
		ShowShots.whipStreaks(st, t, PROFILE_AT - 5f, PROFILE_AT + 1f);
	}

	/** A filled quad given its four screen corners. */
	private static void quad(Stage st, VertexConsumer vc, PoseStack.Pose p, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
			int rgb) {
		st.put(vc, p, ax, ay, rgb, 1f);
		st.put(vc, p, bx, by, rgb, 1f);
		st.put(vc, p, cx, cy, rgb, 1f);
		st.put(vc, p, dx, dy, rgb, 1f);
	}

	/** A filled ellipse (an {@code n}-gon; 6 = a hexagon) of radii {@code rx} (screen widths) / {@code ry} (screen heights). */
	static void ellipse(Stage st, VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float rx, float ry, int n, float rot, int rgb, float a) {
		for (int i = 0; i < n; i++) {
			float a0 = rot + i * Mth.TWO_PI / n, a1 = rot + (i + 1) * Mth.TWO_PI / n;
			st.put(vc, p, cx, cy, rgb, a);
			st.put(vc, p, cx + Mth.cos(a0) * rx, cy + Mth.sin(a0) * ry, rgb, a);
			st.put(vc, p, cx + Mth.cos(a1) * rx, cy + Mth.sin(a1) * ry, rgb, a);
			st.put(vc, p, cx, cy, rgb, a);
		}
	}
}
