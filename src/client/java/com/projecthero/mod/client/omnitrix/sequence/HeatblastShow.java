package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Heatblast's show
 * transformation, shot for shot. Compilation video second {@code v} is tick {@code (v - 10.30) * 20}; the beats:
 * <pre>
 *   0-8     the slap: Ben in the RV seat, the watch thrust at the camera (front medium)
 *   6-24    FULL GREEN FLASH
 *   24-38   the dial close-up in its green bloom; magma rock starts bubbling out round the dial (30)
 *   38-62   along the forearm to the shoulder: red rock plates crawling up the arm (olive field, black Kirby dots)
 *   62-83   Ben's face, low and close: the rock creeps up his neck and cheek, a warm glow behind him
 *   83-92   the eye close-up: the green iris, the pupil swelling until it swallows the frame
 *   92-107  black, a hot white streak slicing across it and thinning out           (the body changes at 95)
 *   107-116 dark red rock, glowing magma cracks splitting across it
 *   115-120 a smeared whip pan
 *   120-144 one long pull-back: the Omnitrix on the cracked chest, the upper body (khaki and black splotches),
 *           the head bursting into flame (130), the fists flaring, crossed, then up in a guard
 *   143-160 WHITE FLASH, fading off the final pose
 *   150-190 the stance on the green ray burst: right fist driven at the camera, the left cocked by his head, legs wide
 *   190     a hard cut back to the world (no green out in this one)
 * </pre>
 * The rock plates and the poses are on the body, so everyone nearby sees them; the eye, the streak and the cracks are the holder's own
 * screen (flat quads, no particles).
 */
public final class HeatblastShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, DIAL_AT = 24f, ARM_AT = 38f, FACE_AT = 62f, EYE_AT = 83f, BLACK_AT = 92f, CRACK_AT = 107f, SMEAR_AT = 115f,
			CHEST_AT = 120f, FLAME_AT = 129f, CROSS_AT = 135f, GUARD_AT = 139.5f, WHITE_AT = 143f, POSE_AT = 150f, WORLD_AT = 190f;
	static final int TRANSFORM_AT = 95;

	private static final ResourceLocation ROCK = ProjectHeroMod.id("textures/entity/omnitrix/sequence/heatblast/rock.png");

	private static final KirbyDots.Style ARM_DOTS = KirbyDots.Style.show().size(1.2f),
			KHAKI_DOTS = KirbyDots.Style.show().bands(3).density(1.4f).size(1.4f).width(1.4f).rim(1.3f).clear(0.36f).flow(0.6f),
			BURST_DOTS = KirbyDots.Style.show().bands(2).density(1.0f).size(1.3f).width(1.2f).rim(1.0f).clear(0.5f).flow(1.2f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(SLAP_END + 0.01f, ARM_AT),
			new float[][] {
					// along the forearm from above, the wrist to the shoulder
					{ 38.01f, 0.42f, -84f, 1.8f, 1.22f, -0.33f, 0.5f },
					{ 50f, 0.42f, -88f, 1.8f, 1.24f, -0.33f, 0.3f },
					{ 62f, 0.45f, -95f, 1.82f, 1.3f, -0.33f, 0.06f },
					// the face, close, from his left front
					{ 62.01f, 0.85f, -30f, 1.64f, 1.66f, -0.06f, 0f },
					{ 83f, 0.68f, -24f, 1.66f, 1.68f, -0.06f, 0f },
					// (under the eye, the black and the cracks: a close front shot of the head, never seen)
					{ 83.01f, 0.3f, -5f, 1.6f, 1.62f, 0f, 0f },
					{ 119.5f, 0.3f, 0f, 1.6f, 1.62f, 0f, 0f },
					// the long pull-back off the chest symbol
					{ 119.51f, 0.6f, 0f, 1.2f, 1.22f, 0f, 0f },
					{ 129f, 0.95f, 0f, 1.0f, 1.36f, 0f, 0f },
					{ 139f, 1.75f, -6f, 0.85f, 1.22f, 0f, 0f },
					{ 144f, 2.15f, -22f, 0.8f, 1.12f, 0f, 0f },
					// the stance
					{ 144.01f, 2.2f, -24f, 0.6f, 1.0f, 0f, 0f },
					{ 190f, 2.05f, -20f, 0.6f, 1.0f, 0f, 0f } }));

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
		}
		return fov;
	}

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		m.head.yRot = 0f;
		if (t < TRANSFORM_AT) {
			// the arm shot: the watch arm held straight out in front, so the camera can run along it from above
			if (t >= ARM_AT && t < FACE_AT) {
				rot(m.leftArm, 1f, -1.52f, 0.05f, 0f);
			}
			// the rock taking the arm: a tremble that builds
			float shake = smooth((t - 30f) / 25f);
			if (shake > 0f) {
				m.leftArm.xRot += 0.05f * shake * Mth.sin(t * 3.1f);
				m.leftArm.zRot += 0.04f * shake * Mth.cos(t * 3.9f);
				m.rightArm.xRot += 0.04f * shake * Mth.sin(t * 2.7f + 1f);
			}
			// the face: the head thrown back and turned, gritting against it; the arms drop out of the shot
			float back = smooth((t - FACE_AT + 2f) / 6f);
			if (back > 0f) {
				m.head.xRot = Mth.lerp(back, m.head.xRot, -0.14f + 0.05f * Mth.sin(t * 2.6f));
				m.head.yRot = Mth.lerp(back, 0f, 0.22f);
				m.head.zRot = Mth.lerp(back, 0f, -0.12f);
				m.body.xRot = Mth.lerp(back, m.body.xRot, -0.1f);
				rot(m.leftArm, back, 0.1f + 0.05f * Mth.sin(t * 3.1f), 0f, -0.4f);
				rot(m.rightArm, back, 0.1f + 0.05f * Mth.sin(t * 2.7f), 0f, 0.4f);
			}
			copyLayers(m);
			return;
		}
		float breathe = Mth.sin(t * 0.3f) * 0.04f;
		if (t < FLAME_AT) {
			// the chest: arms hanging a little out, fists clenched
			rot(m.rightArm, 1f, -0.08f, 0f, 0.22f + breathe);
			rot(m.leftArm, 1f, -0.08f, 0f, -0.22f - breathe);
			rot(m.head, 1f, 0.12f, 0f, 0f);
		} else if (t < CROSS_AT) {
			// the head bursts into flame: chin up, the fists flung down and out
			float w = smooth((t - FLAME_AT) / 3f);
			rot(m.rightArm, w, -0.2f, 0f, 0.62f + breathe);
			rot(m.leftArm, w, -0.2f, 0f, -0.62f - breathe);
			rot(m.head, w, -0.22f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, -0.06f);
		} else if (t < GUARD_AT) {
			// the fists slammed together in front of the chest
			float w = smooth((t - CROSS_AT) / 2.5f);
			rot(m.rightArm, w, -1.35f, -0.55f, 0.1f);
			rot(m.leftArm, w, -1.35f, 0.55f, -0.1f);
			rot(m.head, w, 0.15f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.12f);
		} else if (t < POSE_AT - 2f) {
			// up into a guard, turning
			float w = smooth((t - GUARD_AT) / 3f);
			rot(m.leftArm, w, -2.15f, 0.2f, -0.25f);
			rot(m.rightArm, w, -1.35f, -0.45f, 0.15f);
			rot(m.head, w, -0.05f, -0.3f, 0f);
			rot(m.rightLeg, w, -0.2f, 0f, 0.12f);
			rot(m.leftLeg, w, 0.25f, 0f, -0.12f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.08f);
		} else {
			// the stance: the right fist driven at the camera, the left cocked up by his head, legs wide
			float w = smooth((t - POSE_AT + 2f) / 4f);
			float push = 0.04f * Mth.sin(t * 0.5f);
			rot(m.rightArm, w, -1.32f + push, -0.12f, 0.55f);
			rot(m.leftArm, w, -0.5f - breathe, 0.1f, -2.05f);
			rot(m.rightLeg, w, 0.18f, 0f, 0.42f);
			rot(m.leftLeg, w, -0.32f, 0f, -0.36f);
			rot(m.head, w, -0.08f, 0.18f, 0.06f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.1f);
		}
		copyLayers(m);
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

	// ================================================================ the rock plates

	/**
	 * Patches of plates: bone (0 left arm, 1 body, 2 head, 3 right arm), the box x0 x1 y0 y1 z0 z1 (px), how many, the point they
	 * spread from (x y z), the first and last erupt ticks, the plate size, and which faces (bits: +x -x +z -z -y).
	 */
	private static final float[][] PATCHES = {
			// round the watch, then up the forearm and the upper arm
			{ 0, -1, 3, 5, 10, -2, 2, 34, 1, 8, 0, 27, 44, 2.0f, 0b1111 },
			{ 0, -1, 3, -2, 5, -2, 2, 30, 1, 8, 0, 42, 58, 2.1f, 0b11111 },
			// the shoulder, the chest and the back, the neck
			{ 1, -1, 4, 0, 8, -2, 2, 26, 4, 0, 0, 54, 74, 1.9f, 0b1100 },
			{ 1, -4, -1, 0, 7, -2, 2, 12, 4, 0, 0, 66, 80, 1.9f, 0b1100 },
			{ 3, -3, 1, -2, 6, -2, 2, 12, 1, -2, 0, 68, 82, 1.7f, 0b10111 },
			// up the neck and over the cheek and the jaw
			{ 2, -4, 4, -5, 0, -4, 4, 40, 4, 0, -2, 62, 86, 1.1f, 0b1101 } };
	private static final float[][] PLATES = Plates.gen(0x4EA7B1A5L, PATCHES);

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < 28f || t >= TRANSFORM_AT) {
			return;
		}
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		Plates.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ROCK)), bones, PLATES, t, 5f, 0.9f);
	}

	/**
	 * v0.17.3: angular plates growing over the body's parts (Heatblast's rock, Diamondhead's crystal, Wildmutt's fur): each a flat box
	 * on a face of its bone, turned a random way about the face's normal, popping up springily from its erupt tick.
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

	// ================================================================ in the world

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 34f);
		// the magma heat round the body as the head ignites
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, FLAME_AT - 1f, 134f, 0x4A1404, 0x6A3A10);
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
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - ARM_AT, ARM_DOTS);
		} else if (t < CHEST_AT) {
			// the warm light behind the face
			st.gradient(0xFFE2B0, 0xE9A86A, 0xA0502A);
			st.rays(st.seed + 9, t - FACE_AT, 0xFFF0D0, 10);
		} else if (t < WHITE_AT + 4f) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - CHEST_AT, KHAKI_DOTS);
		} else {
			st.gradient(0x8CF85A, 0x34D41C, 0x12820C);
			st.rays(st.seed + 3, t - WHITE_AT, 0x0E5A0A, 14);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)) + 5, t - WHITE_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 5f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 8f, 1f, 21f, 4f, 1f);
		ShowShots.dialBloom(st, t, DIAL_AT, ARM_AT);
		if (t >= EYE_AT && t < BLACK_AT + 1f) {
			eye(st, t);
		}
		if (t >= BLACK_AT && t < CRACK_AT + 1f) {
			streak(st, t);
		}
		if (t >= CRACK_AT && t < CHEST_AT + 0.5f) {
			cracks(st, t);
		}
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 1.5f, POSE_AT + 1f, 10f, 1f);
	}

	/** The eye close-up: Ben's green iris, swirling a little to the left, the pupil swelling until the frame is black. */
	private static void eye(Stage st, float t) {
		float u = t - EYE_AT;
		VertexConsumer flat = st.flat();
		PoseStack.Pose p = st.last();
		// the white of the eye on the right, the grey shading on the left
		st.fill(flat, p, 0xC9CFC4, 1f);
		st.rect(flat, p, -1.5f, -1.5f, -0.2f, 1.5f, 0x7B8F2A, 0.9f, 0f);
		VertexConsumer dots = st.dots();
		float k = st.k();
		float cx = (0.18f - 0.035f * u) * k, cy = 0.02f + 0.01f * u;
		float r = 0.48f + 0.05f * u;
		st.dot(dots, p, cx, cy, r * 1.08f, 0x2C3608, 255);
		st.dot(dots, p, cx, cy, r, 0xB2D234, 255);
		st.dot(dots, p, cx - 0.06f * k, cy + 0.04f, r * 0.7f, 0xCBE45C, 255);
		float grow = smooth((u - 2.5f) / 6.5f);
		float pr = 0.15f + grow * grow * 2.2f;
		st.dot(dots, p, cx, cy, pr, 0x040404, 255);
		// the light swirling round it
		ShowShots.flash(st, t, 0x000000, BLACK_AT - 1.5f, 1.5f, BLACK_AT + 1f, 0.1f, 1f);
	}

	/** Black, a hot white streak slicing across from the bottom left, sweeping right and thinning to a line. */
	private static void streak(Stage st, float t) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		st.fill(vc, p, 0x000000, 1f);
		float u = t - BLACK_AT;
		float vis = env(t, BLACK_AT + 2f, 2f, CRACK_AT - 3f, 2f);
		if (vis <= 0f) {
			return;
		}
		float off = -0.35f + 0.5f * smooth(u / 14f);
		float w = Mth.lerp(smooth((u - 3f) / 8f), 0.07f, 0.006f);
		line(st, vc, p, off - 0.45f, -1.2f, off + 0.25f, 1.2f, w * 3.5f, 0xB8A870, 0.35f * vis);
		line(st, vc, p, off - 0.45f, -1.2f, off + 0.25f, 1.2f, w, 0xFFF6D8, vis);
	}

	/** The crack shot's polylines (screen units): the main split and its branches. */
	private static final float[][] CRACKS = { { -1.2f, -1.05f, -0.75f, -0.62f, -0.42f, -0.3f, -0.05f, -0.12f, 0.25f, 0.12f, 0.42f, 0.55f, 0.55f, 1.15f },
			{ -0.42f, -0.3f, -0.62f, 0.1f, -0.7f, 0.32f }, { -0.05f, -0.12f, 0.15f, -0.45f, 0.48f, -0.62f },
			{ 0.25f, 0.12f, 0.62f, 0.05f, 0.95f, -0.22f }, { -0.75f, -0.62f, -1.0f, -0.35f } };

	/** Dark red rock, magma cracks splitting across it, glowing; then the whip pan's smear. */
	private static void cracks(Stage st, float t) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float u = t - CRACK_AT;
		float heat = smooth(u / 6f);
		int edge = ShowMath.lerpRgb(heat, 0x1A0000, 0x5A0606);
		st.radial(vc, p, ShowMath.lerpRgb(heat, 0x3C0404, 0xA81412), ShowMath.lerpRgb(heat, 0x2A0202, 0x8A0C0A), edge, 1f, 1f);
		float grow = smooth(u / 5f);
		float w = 0.012f + 0.022f * smooth(u / 7f);
		for (int c = 0; c < CRACKS.length; c++) {
			float[] pl = CRACKS[c];
			float g = c == 0 ? grow : smooth((u - 1.5f - c * 0.6f) / 3.5f);
			int segs = pl.length / 2 - 1;
			float shown = g * segs;
			for (int s = 0; s < segs && s < shown; s++) {
				float f = Math.min(1f, shown - s);
				float x0 = pl[s * 2], y0 = pl[s * 2 + 1];
				float x1 = Mth.lerp(f, x0, pl[s * 2 + 2]), y1 = Mth.lerp(f, y0, pl[s * 2 + 3]);
				line(st, vc, p, x0, y0, x1, y1, w * 3.2f, 0xFF7A20, 0.45f);
				line(st, vc, p, x0, y0, x1, y1, w, 0xFFF2C8, 1f);
			}
		}
		// the smear into the chest shot
		float smear = env(t, SMEAR_AT - 2f, 2f, CHEST_AT - 1.5f, 1f);
		if (smear > 0f) {
			st.fill(vc, p, 0x9A2424, 0.55f * smear);
			ShowShots.whipStreaks(st, t, SMEAR_AT - 2f, CHEST_AT - 0.5f);
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
