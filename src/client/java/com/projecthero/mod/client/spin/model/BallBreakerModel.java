package com.projecthero.mod.client.spin.model;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 the Spin -- Ball Breaker, <b>Candidate A</b> (a custom model; PREVIEW ONLY, not wired into the Ball Breaker move).
 *
 * <p>Built from the manga / All-Star Battle look: a tall, slim, glossy lime figure floating in a forward-leaning crouch, arms spread with
 * open clawed hands; a rounded dome helmet (wider at the top) with a squarish faceplate and two pale stalks with pink bulbs; the two big
 * flat <b>discs</b> either side of the head (lime on top, dark underneath); raised magenta oval <b>studs</b> all over; a silver
 * <b>zigzag</b> trim across the chest and segmented abdomen plates; a dark grey <b>ribbed hose</b> down the outer side of every limb with
 * the lime plates over it; long-fingered hands; long shins ending in <b>pointed, spiked boots</b> with blade-like toe, heel and side flares.
 *
 * <p>Geometry is procedural (ellipsoids, elliptic frustums, boxes) in a small bone hierarchy, drawn straight into a buffer; every primitive
 * maps one whole 32x32 swatch of {@code ball_breaker_model.png} (made by {@code scratchpad/spin/gen_ball_breaker_model_tex.js}), which is
 * painted comic-style (toon ramp, inked rims, hatched shadows).
 *
 * <p>Model space: units = pixels (1/16 block), +Y up, +Z = the figure's front, origin under the pelvis at the soles of a standing figure
 * (about 41 px = 2.6 blocks tall with the discs). To face an entity's yaw, rotate {@code Axis.YP.rotationDegrees(-yaw)} first.
 */
public final class BallBreakerModel {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/spin/power/ball_breaker_model.png");

	// swatches (keep in sync with the generator)
	static final int S_ROUND = 0, S_PANEL = 1, S_RIB = 2, S_STUD = 3, S_SILVER = 4, S_FACE = 5, S_DISC_TOP = 6, S_DISC_UNDER = 7, S_DARK = 8,
			S_CLAW = 9, S_ROUND_DK = 10, S_STALK = 11, S_BULB = 12, S_GLOW = 13, S_RIM = 14, S_VISOR = 15;

	/** Draw the manga's two stalks with pink bulbs on top of the helmet (the All-Star Battle render has none). */
	public static boolean stalks = true;

	// the draw state of the pass in progress
	private static VertexConsumer vc;
	private static int light;
	private static int r = 255, g = 255, b = 255, a = 255;
	private static int forceSwatch = -1;

	private BallBreakerModel() {
	}

	/**
	 * Draws the figure in the floating crouch.
	 *
	 * @param alpha  0..1 fade (below 1 it draws translucent)
	 * @param glow   0..1 golden glow (an additive gold pass over the figure plus a faint enlarged shell)
	 * @param strike 0..1 drives the right hand forward (the Ball Breaker strike)
	 * @param time   ticks, for the idle bob and claw flex
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int packedLight, float alpha, float glow, float strike, float time) {
		if (alpha <= 0.003f) {
			return;
		}
		light = packedLight;
		r = g = b = 255;
		a = Mth.clamp((int) (alpha * 255), 0, 255);
		forceSwatch = -1;
		vc = buffers.getBuffer(alpha >= 0.999f ? RenderType.entityCutoutNoCull(TEX) : RenderType.entityTranslucent(TEX));
		figure(pose, strike, time);
		if (glow > 0.003f) {
			light = LightTexture.FULL_BRIGHT;
			forceSwatch = S_GLOW;
			vc = buffers.getBuffer(RenderType.eyes(TEX));
			float k = glow * alpha;
			r = (int) (255 * 0.55f * k);
			g = (int) (205 * 0.55f * k);
			b = (int) (70 * 0.55f * k);
			a = 255;
			figure(pose, strike, time);
			// a faint shell, a little bigger, centred on the chest
			r = (int) (255 * 0.22f * k);
			g = (int) (200 * 0.22f * k);
			b = (int) (60 * 0.22f * k);
			pose.pushPose();
			pose.translate(0f, 26f / 16f, 0f);
			pose.scale(1.08f, 1.06f, 1.12f);
			pose.translate(0f, -26f / 16f, 0f);
			figure(pose, strike, time);
			pose.popPose();
			forceSwatch = -1;
		}
		vc = null;
	}

	// ------------------------------------------------------------------------------------------------------------------ the figure

	private static void figure(PoseStack pose, float strike, float time) {
		float bob = Mth.sin(time * 0.12f) * 0.6f;
		float flex = Mth.sin(time * 0.21f) * 0.08f;
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// root: the pelvis, the whole body tipped forward a little
		bone(pose, 0f, 21f + bob, 0f, 0.28f, 0f, 0f);
		pelvis(pose);
		// left leg (+X) hangs lower and straighter, the right leg is drawn up
		leg(pose, 1f, -0.9f, -0.04f, 1.35f, 0.65f);
		leg(pose, -1f, -1.5f, 0.12f, 1.95f, 0.8f);
		// the torso leans in from the waist
		bone(pose, 0f, 1f, 0f, 0.18f, 0f, 0f);
		abdomen(pose);
		bone(pose, 0f, 3.5f, 0f, 0.2f, 0f, 0f);
		chest(pose);
		// arms spread out, forearms raised, hands open beside the head; the right one strikes
		arm(pose, 1f, 0.05f, 1.35f, -0.35f, 1.75f, flex, 0f);
		arm(pose, -1f, Mth.lerp(strike, -0.15f, -1.6f), Mth.lerp(strike, 1.2f, 0.15f), Mth.lerp(strike, -0.45f, -0.1f), Mth.lerp(strike, 1.45f, 0f),
				flex, strike);
		// the head sits low between hunched shoulders, looking ahead
		bone(pose, 0f, 5.4f, 0.8f, -0.55f, 0f, 0f);
		head(pose, time);
		pose.popPose(); // head
		pose.popPose(); // chest
		pose.popPose(); // abdomen
		pose.popPose(); // root
		pose.popPose();
	}

	private static void pelvis(PoseStack pose) {
		// hips: a lime frustum, a dark crotch, a front plate
		taper(pose, 0f, -2.6f, 0f, 2.9f, 1.9f, 0f, 1.0f, 3.3f, 2.2f, 12, S_ROUND, true);
		box(pose, -1.3f, -3.4f, 1.2f, 1.3f, -0.6f, 2.35f, S_PANEL);
		stud(pose, 1.4f, -0.8f, 2.05f, 0.35f, 0.7f, 1.0f);
		stud(pose, -1.4f, -0.8f, 2.05f, -0.35f, 0.7f, 1.0f);
		stud(pose, 0f, -1.9f, 2.4f, 0f, 0.6f, 0.9f);
	}

	private static void abdomen(PoseStack pose) {
		// the narrow dark waist and three segmented lime plates over it
		taper(pose, 0f, 0f, 0f, 2.3f, 1.7f, 0f, 3.6f, 2.5f, 1.8f, 10, S_DARK, false);
		float[] ys = { 0.3f, 1.45f, 2.6f };
		float[] hw = { 1.7f, 1.95f, 2.2f };
		for (int i = 0; i < 3; i++) {
			box(pose, -hw[i], ys[i], 1.0f, hw[i], ys[i] + 0.95f, 2.15f, S_PANEL);
		}
		// flank plates with studs
		for (int s = -1; s <= 1; s += 2) {
			box(pose, s > 0 ? 1.9f : -2.75f, 0.4f, -1.2f, s > 0 ? 2.75f : -1.9f, 3.4f, 1.1f, S_PANEL);
			stud(pose, s * 2.75f, 1.4f, 0.2f, s * 1.5708f, 0.55f, 0.85f);
			stud(pose, s * 2.75f, 2.6f, 0.2f, s * 1.5708f, 0.55f, 0.85f);
		}
	}

	private static void chest(PoseStack pose) {
		// ribcage: a frustum widening to the shoulders, a dark back/neck, round shoulder pads
		taper(pose, 0f, 0f, 0f, 3.0f, 2.2f, 0f, 6.0f, 4.4f, 2.9f, 16, S_ROUND, true);
		ellipsoid(pose, 0f, 5.8f, 0f, 4.3f, 1.2f, 2.8f, 14, 6, S_ROUND, 0f, 1f);
		taper(pose, 0f, 5.6f, 0.2f, 1.25f, 1.15f, 0f, 7.0f, 1.15f, 1.05f, 8, S_DARK, false);
		// the silver zigzag trim across the chest (a W of strips following the curve), and a darker band under it
		float[] zx = { -4.0f, -3.0f, -2.0f, -1.0f, 0f, 1.0f, 2.0f, 3.0f, 4.0f };
		for (int i = 0; i < zx.length - 1; i++) {
			float y0 = (i % 2 == 0) ? 4.55f : 3.4f, y1 = (i % 2 == 0) ? 3.4f : 4.55f;
			strip(pose, zx[i], y0, zx[i + 1], y1, 0.42f, S_SILVER, 0.05f);
			strip(pose, zx[i], y0 - 0.55f, zx[i + 1], y1 - 0.55f, 0.22f, S_DARK, 0f);
		}
		// studs on the pecs (below the trim) and on the upper chest
		float[][] cs = { { -2.6f, 2.3f }, { -1.0f, 1.6f }, { 1.0f, 1.6f }, { 2.6f, 2.3f }, { -1.8f, 0.6f }, { 1.8f, 0.6f }, { -2.2f, 5.25f },
				{ 2.2f, 5.25f } };
		for (float[] p : cs) {
			float rz = radiusAt(p[1], 0f, 2.2f, 6.0f, 2.9f);
			float rx = radiusAt(p[1], 0f, 3.0f, 6.0f, 4.4f);
			float t = Mth.clamp(p[0] / rx, -0.95f, 0.95f);
			float z = rz * Mth.sqrt(1f - t * t);
			stud(pose, p[0], p[1], z, (float) Math.atan2(p[0] / (rx * rx), z / (rz * rz)), 0.6f, 0.9f);
		}
		// a stud row down the back
		for (int i = 0; i < 3; i++) {
			stud(pose, 0f, 1.2f + i * 1.5f, -radiusAt(1.2f + i * 1.5f, 0f, 2.2f, 6.0f, 2.9f), Mth.PI, 0.55f, 0.8f);
		}
		for (int s = -1; s <= 1; s += 2) {
			ellipsoid(pose, s * 4.6f, 5.2f, 0f, 2.2f, 2.0f, 2.5f, 14, 8, S_ROUND, 0f, 1f);
			stud(pose, s * 4.7f, 6.7f, 0.9f, s * 0.4f, 0.55f, 0.85f);
			stud(pose, s * 6.4f, 5.4f, 0.4f, s * 1.5f, 0.55f, 0.85f);
			stud(pose, s * 5.0f, 5.0f, 2.25f, s * 0.2f, 0.5f, 0.75f);
		}
	}

	private static void head(PoseStack pose, float time) {
		// neck stub
		taper(pose, 0f, -0.6f, -0.1f, 1.2f, 1.1f, 0f, 1.0f, 1.3f, 1.2f, 8, S_DARK, false);
		// the helmet: an egg, narrow at the chin, widest near the top, a smooth dome (big: about half the shoulder span)
		pose.pushPose();
		pose.scale(1.2f, 1.2f, 1.2f);
		taper(pose, 0f, 0.3f, 0f, 2.0f, 2.2f, 0f, 4.9f, 2.75f, 2.85f, 16, S_ROUND, false);
		ellipsoid(pose, 0f, 0.3f, 0f, 2.0f, 0.7f, 2.2f, 16, 4, S_ROUND, 0.5f, 1f);
		ellipsoid(pose, 0f, 5.2f, 0f, 2.75f, 2.9f, 2.85f, 16, 8, S_ROUND, 0f, 0.5f);
		// the tall squarish faceplate on the front, with slats, an eye band and a central ridge
		box(pose, -1.35f, 0.8f, 1.75f, 1.35f, 4.7f, 2.62f, S_FACE);
		box(pose, -1.2f, 3.2f, 2.55f, 1.2f, 3.85f, 2.72f, S_VISOR);
		box(pose, -0.16f, 1.0f, 2.55f, 0.16f, 4.6f, 2.8f, S_SILVER);
		// faceplate studs (two pairs, as in the concept sketch), studs over the brow, a ring round the dome, a crown ring
		stud(pose, -0.75f, 1.9f, 2.62f, 0f, 0.42f, 0.62f);
		stud(pose, 0.75f, 1.9f, 2.62f, 0f, 0.42f, 0.62f);
		stud(pose, -0.75f, 5.25f, 2.78f, 0f, 0.45f, 0.62f);
		stud(pose, 0.75f, 5.25f, 2.78f, 0f, 0.45f, 0.62f);
		for (int i = 0; i < 10; i++) {
			float ang = (i + 0.5f) / 10f * Mth.TWO_PI;
			stud(pose, Mth.sin(ang) * 2.55f, 6.25f, Mth.cos(ang) * 2.65f, ang, 0.5f, 0.75f);
		}
		for (int i = 0; i < 6; i++) {
			float ang = i / 6f * Mth.TWO_PI + 0.3f;
			stud(pose, Mth.sin(ang) * 1.55f, 7.55f, Mth.cos(ang) * 1.6f, ang, 0.42f, 0.6f);
		}
		// the stalks with pink bulbs (manga)
		if (stalks) {
			for (int s = -1; s <= 1; s += 2) {
				pose.pushPose();
				pose.translate(s * 0.85f, 7.8f, 0.3f);
				pose.mulPose(Axis.ZP.rotation(-s * 0.22f));
				pose.mulPose(Axis.XP.rotation(0.12f + Mth.sin(time * 0.17f + s) * 0.05f));
				taper(pose, 0f, 0f, 0f, 0.28f, 0.28f, 0f, 2.2f, 0.18f, 0.18f, 6, S_STALK, false);
				ellipsoid(pose, 0f, 2.55f, 0f, 0.55f, 0.65f, 0.55f, 8, 6, S_BULB, 0f, 1f);
				pose.popPose();
			}
		}
		// the two big discs either side of the top of the helmet, tucked under the dome's edge, tipped forward so their lime tops
		// face the front, outer edges dipping a little
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 5.3f, 6.6f, -0.3f);
			pose.mulPose(Axis.ZP.rotation(-s * 0.1f));
			pose.mulPose(Axis.XP.rotation(0.5f));
			disc(pose, 4.2f, 0.7f);
			pose.popPose();
		}
		pose.popPose();
	}

	/** A saucer: rim band, lime top with a raised hub ring, dark underside. Centre at the origin, faces +Y. */
	private static void disc(PoseStack pose, float rad, float th) {
		int n = 28;
		taper(pose, 0f, -th * 0.5f, 0f, rad * 0.94f, rad * 0.94f, 0f, th * 0.55f, rad, rad, n, S_RIM, false);
		taper(pose, 0f, th * 0.05f, 0f, rad, rad, 0f, th * 0.5f, rad * 0.97f, rad * 0.97f, n, S_RIM, false);
		cap(pose, 0f, th * 0.55f, 0f, rad * 0.97f, rad * 0.97f, n, S_DISC_TOP, true);
		cap(pose, 0f, -th * 0.5f, 0f, rad * 0.94f, rad * 0.94f, n, S_DISC_UNDER, false);
	}

	/**
	 * One arm. {@code s} = +1 left (+X), -1 right. Angles: the upper arm's raise forward (xr, negative = forward) and spread out (spread),
	 * the elbow bend (negative = forearm forward/up).
	 */
	private static void arm(PoseStack pose, float s, float xr, float spread, float elbow, float elbowOut, float flex, float strike) {
		bone(pose, s * 4.9f, 5.0f, 0f, xr, 0f, s * spread);
		// upper arm: the grey ribbed hose down the outer back side, a lime plate over the front, a stud pair
		ribTube(pose, s * 0.75f, -0.35f, -0.5f, -7.6f, 0.95f);
		taper(pose, s * -0.1f, -7.8f, 0.25f, 1.05f, 1.1f, 0f, 7.8f, 1.3f, 1.35f, 10, S_ROUND, false);
		armStuds(pose, s, -2.2f, -5.0f, 1.25f);
		// elbow
		ellipsoid(pose, 0f, -8.0f, 0f, 1.15f, 1.1f, 1.15f, 10, 6, S_DARK, 0f, 1f);
		bone(pose, 0f, -8.0f, 0f, elbow, 0f, s * elbowOut);
		// forearm: wider plate near the elbow, tapering to the wrist
		ribTube(pose, s * 0.7f, -0.3f, -0.4f, -6.8f, 0.85f);
		taper(pose, s * -0.1f, -6.9f, 0.3f, 0.9f, 0.95f, 0f, 6.9f, 1.3f, 1.3f, 10, S_ROUND, false);
		armStuds(pose, s, -1.6f, -4.4f, 1.15f);
		stud(pose, s * -0.2f, -3.0f, 1.55f, 0f, 0.5f, 0.75f);
		// hand: palm turned forward, fingers spread and curled into claws
		bone(pose, 0f, -7.0f, 0.1f, -0.35f + strike * 0.25f, s * 0.15f, s * 0.45f * (1f - strike));
		hand(pose, s, flex, strike);
		pose.popPose(); // hand
		pose.popPose(); // forearm
		pose.popPose(); // upper arm
	}

	private static void armStuds(PoseStack pose, float s, float yTop, float yBot, float rad) {
		stud(pose, s * rad * 0.15f, yTop, rad + 0.2f, s * 0.1f, 0.5f, 0.8f);
		stud(pose, s * rad * 0.15f, yBot, rad + 0.1f, s * 0.1f, 0.5f, 0.8f);
		stud(pose, s * (rad + 0.05f), (yTop + yBot) * 0.5f, 0.5f, s * 1.2f, 0.45f, 0.7f);
	}

	private static void hand(PoseStack pose, float s, float flex, float strike) {
		// the palm (flattened, broad) and the back-of-hand plate
		box(pose, -1.15f, -2.6f, -0.45f, 1.15f, 0f, 0.45f, S_PANEL);
		box(pose, -0.9f, -2.2f, -0.65f, 0.9f, -0.3f, -0.4f, S_PANEL);
		float curl = 0.42f + flex - strike * 0.25f;
		// four long fingers fanned out, and a thumb on the inner side
		float[] fx = { -0.85f, -0.3f, 0.3f, 0.85f };
		float[] fan = { -0.3f, -0.1f, 0.1f, 0.3f };
		float[] len = { 2.6f, 3.1f, 3.0f, 2.5f };
		for (int i = 0; i < 4; i++) {
			finger(pose, fx[i], -2.5f, 0f, -0.2f, fan[i], len[i], curl);
		}
		finger(pose, -s * 1.15f, -0.9f, 0.25f, -0.4f, -s * 0.95f, 1.6f, curl * 0.8f);
	}

	private static void finger(PoseStack pose, float x, float y, float z, float xr, float zr, float len, float curl) {
		bone(pose, x, y, z, xr - curl * 0.4f, 0f, zr);
		taper(pose, 0f, -len * 0.55f, 0f, 0.24f, 0.27f, 0f, len * 0.55f, 0.3f, 0.33f, 6, S_CLAW, false);
		bone(pose, 0f, -len * 0.55f, 0f, -curl, 0f, 0f);
		// the claw: a long tapering point
		taper(pose, 0f, -len * 0.75f, 0.05f, 0.03f, 0.03f, 0f, len * 0.75f, 0.24f, 0.27f, 6, S_CLAW, false);
		pose.popPose();
		pose.popPose();
	}

	/**
	 * One leg. Angles: thigh raise (negative = forward), hip spread, knee bend (positive = shin back), ankle (positive = toes down).
	 */
	private static void leg(PoseStack pose, float s, float thigh, float spread, float knee, float ankle) {
		bone(pose, s * 1.75f, -1.6f, 0f, thigh, 0f, s * (0.1f + spread));
		// thigh: the grey hose runs down the outer side, a broad lime plate in front, studs
		ribTube(pose, s * 1.0f, -0.45f, -0.6f, -9.0f, 1.05f);
		taper(pose, s * -0.1f, -9.2f, 0.3f, 1.4f, 1.5f, 0f, 9.4f, 1.95f, 2.0f, 12, S_ROUND, false);
		stud(pose, s * 0.35f, -2.6f, 1.95f, s * 0.15f, 0.55f, 0.9f);
		stud(pose, s * 0.2f, -5.1f, 1.75f, s * 0.1f, 0.55f, 0.9f);
		stud(pose, s * -0.6f, -7.2f, 1.5f, s * -0.3f, 0.5f, 0.8f);
		stud(pose, s * -1.45f, -3.8f, 0.6f, s * -1.2f, 0.5f, 0.8f);
		// knee cap
		ellipsoid(pose, 0f, -9.4f, 0.35f, 1.35f, 1.25f, 1.4f, 10, 6, S_ROUND, 0f, 1f);
		stud(pose, 0f, -9.2f, 1.75f, 0f, 0.5f, 0.75f);
		bone(pose, 0f, -9.4f, 0f, knee, 0f, 0f);
		// shin: long, slim, hose outside, lime plate, studs down the front
		ribTube(pose, s * 0.85f, -0.5f, -0.6f, -9.4f, 0.9f);
		taper(pose, s * -0.05f, -9.8f, 0.3f, 0.95f, 1.05f, 0f, 9.8f, 1.45f, 1.55f, 12, S_ROUND, false);
		stud(pose, 0f, -2.4f, 1.6f, 0f, 0.5f, 0.85f);
		stud(pose, 0f, -4.8f, 1.45f, 0f, 0.5f, 0.85f);
		stud(pose, s * 0.35f, -7.1f, 1.2f, s * 0.2f, 0.45f, 0.75f);
		stud(pose, s * 1.2f, -3.6f, 0.35f, s * 1.3f, 0.45f, 0.7f);
		// the spiked boot
		bone(pose, 0f, -9.8f, 0.2f, ankle, 0f, 0f);
		boot(pose, s);
		pose.popPose(); // boot
		pose.popPose(); // shin
		pose.popPose(); // thigh
	}

	private static void boot(PoseStack pose, float s) {
		// the cuff: flares out over the ankle
		taper(pose, 0f, -1.4f, 0f, 1.25f, 1.35f, 0f, 1.6f, 0.9f, 1.0f, 12, S_ROUND, true);
		// the toe spike: a long point forward and down
		spike(pose, 0f, -1.3f, 0.4f, 1.15f, 0f, 1.05f, 0.7f, 3.9f, S_ROUND);
		// the heel blade: back and down
		spike(pose, 0f, -1.1f, -0.4f, 2.2f, 0f, 0.55f, 0.75f, 2.1f, S_ROUND_DK);
		// blade flares either side of the ankle, swept down and back
		spike(pose, 1.0f, -0.4f, -0.1f, 2.35f, -0.9f, 0.18f, 0.55f, 1.8f, S_ROUND);
		spike(pose, -1.0f, -0.4f, -0.1f, 2.35f, 0.9f, 0.18f, 0.55f, 1.8f, S_ROUND);
		// a raised fin along the top of the foot, and a stud
		spike(pose, 0f, -0.8f, 0.9f, 0.75f, 0f, 0.14f, 0.5f, 2.6f, S_ROUND);
		stud(pose, 0f, -0.6f, 1.15f, 0f, 0.45f, 0.65f);
	}

	// -------------------------------------------------------------------------------------------------------------- composites

	/** A grey ribbed hose from y0 down to y1 at (x, z): a dark core with raised rib rings. */
	private static void ribTube(PoseStack pose, float x, float z, float y0, float y1, float rad) {
		float len = y0 - y1;
		taper(pose, x, y1, z, rad * 0.78f, rad * 0.78f, 0f, len, rad * 0.78f, rad * 0.78f, 8, S_DARK, false);
		int n = Math.max(2, Math.round(len / 0.8f));
		float step = len / n;
		for (int i = 0; i < n; i++) {
			float top = y0 - i * step;
			taper(pose, x, top - step * 0.62f, z, rad, rad, 0f, step * 0.62f, rad, rad, 10, S_RIB, false);
		}
	}

	/** A raised magenta oval stud at (x, y, z) facing out along yaw {@code ang} (0 = +Z). */
	private static void stud(PoseStack pose, float x, float y, float z, float ang, float w, float h) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(Axis.YP.rotation(ang));
		ellipsoid(pose, 0f, 0f, 0.05f, w * 0.8f, h * 0.8f, w * 0.55f, 8, 6, S_STUD, 0f, 1f);
		pose.popPose();
	}

	/** A thin strip from (x0, y0) to (x1, y1) laid over the chest curve (rx 4.4 / rz 2.9 at the top). */
	private static void strip(PoseStack pose, float x0, float y0, float x1, float y1, float w, int sw, float lift) {
		int seg = 3;
		for (int i = 0; i < seg; i++) {
			float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
			float ax = Mth.lerp(t0, x0, x1), ay = Mth.lerp(t0, y0, y1), bx = Mth.lerp(t1, x0, x1), by = Mth.lerp(t1, y0, y1);
			float az = chestZ(ax, ay) + lift, bz = chestZ(bx, by) + lift;
			float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
			float dx = bx - ax, dy = by - ay, dz = bz - az;
			float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			pose.pushPose();
			pose.translate(mx, my, mz);
			pose.mulPose(new Quaternionf().rotationTo(1f, 0f, 0f, dx / l, dy / l, dz / l));
			box(pose, -l * 0.55f, -w * 0.5f, -0.12f, l * 0.55f, w * 0.5f, 0.14f, sw);
			pose.popPose();
		}
	}

	private static float chestZ(float x, float y) {
		float rz = radiusAt(y, 0f, 2.2f, 6.0f, 2.9f);
		float rx = radiusAt(y, 0f, 3.0f, 6.0f, 4.4f);
		float t = Mth.clamp(x / rx, -0.98f, 0.98f);
		return rz * Mth.sqrt(1f - t * t);
	}

	private static float radiusAt(float y, float y0, float r0, float y1, float r1) {
		return Mth.lerp(Mth.clamp((y - y0) / (y1 - y0), 0f, 1f), r0, r1);
	}

	/**
	 * A cone from a base at (x, y, z) pointing along yaw {@code yaw} and pitch {@code pitch} (0 = straight forward +Z, positive = down),
	 * base half-width {@code w} and half-height {@code h}, length {@code len}.
	 */
	private static void spike(PoseStack pose, float x, float y, float z, float pitch, float yaw, float w, float h, float len, int sw) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(Axis.YP.rotation(yaw));
		pose.mulPose(Axis.XP.rotation(Mth.HALF_PI + pitch));
		// local +Y now points along the spike
		taper(pose, 0f, 0f, 0f, w, h, 0f, len, 0.02f, 0.02f, 8, sw, true);
		pose.popPose();
	}

	// -------------------------------------------------------------------------------------------------------------- primitives

	/** Push a bone: pivot, then rotation Z * Y * X (like ModelPart). Pop it yourself. */
	private static void bone(PoseStack pose, float px, float py, float pz, float xr, float yr, float zr) {
		pose.pushPose();
		pose.translate(px, py, pz);
		if (xr != 0f || yr != 0f || zr != 0f) {
			pose.mulPose(new Quaternionf().rotationZYX(zr, yr, xr));
		}
	}

	/**
	 * An elliptic frustum: bottom ring at (x, y, z) radii (rx0, rz0), top ring {@code h} above with radii (rx1, rz1); {@code dz} shears
	 * the top forward. u runs round from the back (0) through the front (0.5); v = 0 at the top.
	 */
	private static void taper(PoseStack pose, float x, float y, float z, float rx0, float rz0, float dz, float h, float rx1, float rz1, int n,
			int sw, boolean caps) {
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < n; i++) {
			float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
			// angle 0 = back (-Z), PI = front (+Z): sin -> x, -cos -> z
			float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
			float slope0x = (rx0 - rx1) / Math.max(h, 0.01f);
			float nx0 = s0 / Math.max(rx0, 0.01f), nz0 = c0 / Math.max(rz0, 0.01f), nx1 = s1 / Math.max(rx0, 0.01f), nz1 = c1 / Math.max(rz0, 0.01f);
			float ny = slope0x * 0.6f;
			float u0 = i / (float) n, u1 = (i + 1) / (float) n;
			vert(p, x + s0 * rx1, y + h, z + c0 * rz1 + dz, u0, 0f, sw, nx0, ny, nz0);
			vert(p, x + s0 * rx0, y, z + c0 * rz0, u0, 1f, sw, nx0, ny, nz0);
			vert(p, x + s1 * rx0, y, z + c1 * rz0, u1, 1f, sw, nx1, ny, nz1);
			vert(p, x + s1 * rx1, y + h, z + c1 * rz1 + dz, u1, 0f, sw, nx1, ny, nz1);
		}
		if (caps) {
			cap(pose, x, y + h, z + dz, rx1, rz1, n, sw, true);
			cap(pose, x, y, z, rx0, rz0, n, sw, false);
		}
	}

	/** A flat elliptic cap (planar UV), facing up or down. */
	private static void cap(PoseStack pose, float x, float y, float z, float rx, float rz, int n, int sw, boolean up) {
		if (rx < 0.03f && rz < 0.03f) {
			return;
		}
		PoseStack.Pose p = pose.last();
		float ny = up ? 1f : -1f;
		for (int i = 0; i < n; i++) {
			float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
			float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
			// a degenerate quad (centre twice) per slice
			vert(p, x, y, z, 0.5f, 0.5f, sw, 0f, ny, 0f);
			vert(p, x + s0 * rx, y, z + c0 * rz, 0.5f + s0 * 0.5f, 0.5f - c0 * 0.5f, sw, 0f, ny, 0f);
			vert(p, x + s1 * rx, y, z + c1 * rz, 0.5f + s1 * 0.5f, 0.5f - c1 * 0.5f, sw, 0f, ny, 0f);
			vert(p, x, y, z, 0.5f, 0.5f, sw, 0f, ny, 0f);
		}
	}

	/** An ellipsoid (or the band of it from latitude {@code v0} to {@code v1}, 0 = top pole, 1 = bottom), u round from the back. */
	private static void ellipsoid(PoseStack pose, float x, float y, float z, float rx, float ry, float rz, int nu, int nv, int sw, float v0,
			float v1) {
		PoseStack.Pose p = pose.last();
		for (int j = 0; j < nv; j++) {
			float t0 = Mth.lerp(j / (float) nv, v0, v1), t1 = Mth.lerp((j + 1) / (float) nv, v0, v1);
			float la0 = t0 * Mth.PI, la1 = t1 * Mth.PI;
			float y0 = Mth.cos(la0), r0 = Mth.sin(la0), y1 = Mth.cos(la1), r1 = Mth.sin(la1);
			for (int i = 0; i < nu; i++) {
				float a0 = i / (float) nu * Mth.TWO_PI, a1 = (i + 1) / (float) nu * Mth.TWO_PI;
				float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
				float u0 = i / (float) nu, u1 = (i + 1) / (float) nu;
				ev(p, x, y, z, rx, ry, rz, s0 * r0, y0, c0 * r0, u0, t0, sw);
				ev(p, x, y, z, rx, ry, rz, s0 * r1, y1, c0 * r1, u0, t1, sw);
				ev(p, x, y, z, rx, ry, rz, s1 * r1, y1, c1 * r1, u1, t1, sw);
				ev(p, x, y, z, rx, ry, rz, s1 * r0, y0, c1 * r0, u1, t0, sw);
			}
		}
	}

	private static void ev(PoseStack.Pose p, float x, float y, float z, float rx, float ry, float rz, float ux, float uy, float uz, float u, float v,
			int sw) {
		vert(p, x + ux * rx, y + uy * ry, z + uz * rz, u, v, sw, ux / Math.max(rx, 0.01f), uy / Math.max(ry, 0.01f), uz / Math.max(rz, 0.01f));
	}

	/** An axis-aligned box, every face mapping the whole swatch (front/back/sides upright, top/bottom flat). */
	private static void box(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int sw) {
		PoseStack.Pose p = pose.last();
		// +Z front
		vert(p, x0, y1, z1, 0f, 0f, sw, 0, 0, 1);
		vert(p, x0, y0, z1, 0f, 1f, sw, 0, 0, 1);
		vert(p, x1, y0, z1, 1f, 1f, sw, 0, 0, 1);
		vert(p, x1, y1, z1, 1f, 0f, sw, 0, 0, 1);
		// -Z back
		vert(p, x1, y1, z0, 0f, 0f, sw, 0, 0, -1);
		vert(p, x1, y0, z0, 0f, 1f, sw, 0, 0, -1);
		vert(p, x0, y0, z0, 1f, 1f, sw, 0, 0, -1);
		vert(p, x0, y1, z0, 1f, 0f, sw, 0, 0, -1);
		// +X
		vert(p, x1, y1, z1, 0f, 0f, sw, 1, 0, 0);
		vert(p, x1, y0, z1, 0f, 1f, sw, 1, 0, 0);
		vert(p, x1, y0, z0, 1f, 1f, sw, 1, 0, 0);
		vert(p, x1, y1, z0, 1f, 0f, sw, 1, 0, 0);
		// -X
		vert(p, x0, y1, z0, 0f, 0f, sw, -1, 0, 0);
		vert(p, x0, y0, z0, 0f, 1f, sw, -1, 0, 0);
		vert(p, x0, y0, z1, 1f, 1f, sw, -1, 0, 0);
		vert(p, x0, y1, z1, 1f, 0f, sw, -1, 0, 0);
		// +Y
		vert(p, x0, y1, z0, 0f, 0f, sw, 0, 1, 0);
		vert(p, x0, y1, z1, 0f, 1f, sw, 0, 1, 0);
		vert(p, x1, y1, z1, 1f, 1f, sw, 0, 1, 0);
		vert(p, x1, y1, z0, 1f, 0f, sw, 0, 1, 0);
		// -Y
		vert(p, x0, y0, z1, 0f, 0f, sw, 0, -1, 0);
		vert(p, x0, y0, z0, 0f, 1f, sw, 0, -1, 0);
		vert(p, x1, y0, z0, 1f, 1f, sw, 0, -1, 0);
		vert(p, x1, y0, z1, 1f, 0f, sw, 0, -1, 0);
	}

	/** One vertex; (u, v) 0..1 inside swatch {@code sw} of the 128x128 atlas (half a texel inset). */
	private static void vert(PoseStack.Pose p, float x, float y, float z, float u, float v, int sw, float nx, float ny, float nz) {
		int s = forceSwatch >= 0 ? forceSwatch : sw;
		float su = (s % 4) * 32f, sv = (s / 4) * 32f;
		float tu = (su + 0.5f + Mth.clamp(u, 0f, 1f) * 31f) / 128f;
		float tv = (sv + 0.5f + Mth.clamp(v, 0f, 1f) * 31f) / 128f;
		float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (l < 1.0e-5f) {
			ny = 1f;
			l = 1f;
		}
		vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(tu, tv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx / l, ny / l,
				nz / l);
	}
}
