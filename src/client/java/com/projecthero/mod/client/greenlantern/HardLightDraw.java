package com.projecthero.mod.client.greenlantern;

import org.joml.Vector3f;

import com.projecthero.mod.client.maxsteel.TurboDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.14.3: the Green Lantern's hard-light look for models built in code. Units are blocks.
 *
 * <p>v0.17.1 (user: "I like the holographic kinda transparent look ... make all the constructs have that same look"):
 * every part is now drawn the way the Hard-Light Mech's texture reads -- a see-through green fill, bright near-white
 * edge lines along all twelve edges, faint horizontal scanlines on the sides with a sheen of light rolling up through
 * them, and a soft pale halo shell. Every construct that is built from {@link #part} (the block constructs, the Arsenal,
 * the survival kit, the vehicles, the drone, the gateway, the hand constructs and the mech's weapons) shares it.
 *
 * <p>v0.17.1: <b>the build-up</b>. While {@link #beginBuild} is in effect (the Car and the Jet, their first
 * {@code VEHICLE_BUILD_TICKS}), parts are formed one after another in the order they are drawn, each one pixel by pixel:
 * a part not reached yet is only a faint blueprint outline, the part being formed fills in 1/16-block cells from the
 * bottom up in a scattered order with the newest cells white-hot, and a finished part is drawn whole.
 */
public final class HardLightDraw {
	public static final int GREEN = 0x35F075;
	public static final int PALE = 0xA8FFC0;
	public static final int DEEP = 0x13A044;
	public static final int WHITE = 0xFFFFFF;
	/** v0.17.1: the bright edge line of every hard-light part. */
	public static final int EDGE = 0xD4FFE0;
	/** v0.17.1: the white-hot colour of light that is still forming. */
	public static final int HOT = 0xF4FFF6;
	/** v0.17.1: a Corps Legend's (Lantern Rank 5) golden-green. */
	public static final int GOLD_GREEN = 0xC8E83A;
	public static final int GOLD_PALE = 0xF6FFB8;
	/** v0.17.1: set by the renderer while it draws a Corps Legend's construct -- the greens turn golden. */
	public static boolean golden;

	/** v0.17.1 holographic look: alphas of the fill, edge lines, halo and scanlines. */
	static final float FILL_A = 0.5f, EDGE_A = 1.0f, HALO_A = 0.14f, SCAN_A = 0.12f;
	/** World size of one build cell and of the scanline spacing (blocks). */
	static final float CELL = 1f / 16f, SCAN_STEP = 0.125f;

	/** {@code rgb} as the current tint draws it (green -> golden-green while {@link #golden}). */
	public static int tint(int rgb) {
		if (!golden) {
			return rgb;
		}
		return rgb == GREEN ? GOLD_GREEN : rgb == PALE ? GOLD_PALE : rgb == DEEP ? 0x8AA012 : rgb == EDGE ? 0xFBFFD8 : rgb;
	}

	private HardLightDraw() {
	}

	public static VertexConsumer buffer(MultiBufferSource buffers) {
		return TurboDraw.buffer(buffers);
	}

	// ---------------------------------------------------------------- v0.17.1 build-up

	/** The build in progress (rendering is single-threaded): null when nothing is being built. */
	private static Build build;

	private static final class Build {
		float p;
		int index;
		int expected;
		float window;
	}

	private static final Build BUILD = new Build();

	/**
	 * v0.17.1: the next parts drawn are a construct being built, {@code p} (0..1) of the way through. {@code expected} is
	 * how many parts the model has (the count {@link #endBuild} returned last frame); {@code window} is the share of the
	 * build each part takes to form (they overlap, so several are forming at once).
	 */
	public static void beginBuild(float p, int expected, float window) {
		BUILD.p = Mth.clamp(p, 0f, 1f);
		BUILD.index = 0;
		BUILD.expected = Math.max(1, expected);
		BUILD.window = Mth.clamp(window, 0.05f, 1f);
		build = BUILD;
	}

	/** Ends the build; returns how many parts were drawn (pass it as {@code expected} next frame). */
	public static int endBuild() {
		int n = build == null ? 0 : build.index;
		build = null;
		return n;
	}

	public static boolean building() {
		return build != null;
	}

	/**
	 * v0.17.1: reserves the next piece of the build and returns how far it has formed: below 0 not started, 0..1 forming,
	 * 1 done (always 1 when nothing is being built). For hard light drawn by hand (glass, lenses, exhaust flames).
	 */
	public static float piece() {
		Build b = build;
		if (b == null) {
			return 1f;
		}
		int i = b.index++;
		float start = b.expected <= 1 ? 0f : Math.min(i, b.expected - 1) / (float) (b.expected - 1) * (1f - b.window);
		return Math.min(1f, (b.p - start) / b.window);
	}

	// ---------------------------------------------------------------- parts

	/** A part centred at {@code (x, y, z)} with half-extents {@code (hx, hy, hz)}: the holographic hard-light box. */
	public static void part(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, float alpha) {
		part(vc, pose, x, y, z, hx, hy, hz, GREEN, alpha);
	}

	public static void part(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb,
			float alpha) {
		float u = piece();
		pose.pushPose();
		pose.translate(x, y, z);
		float scale = scaleOf(pose);
		if (u >= 1f) {
			holo(vc, pose, hx, hy, hz, rgb, alpha, scale);
		} else if (u <= 0f) {
			// not reached yet: a faint blueprint outline of where it will be
			edges(vc, pose.last(), hx, hy, hz, scale, tint(rgb == DEEP ? PALE : EDGE), 0.13f * alpha);
		} else {
			forming(vc, pose, hx, hy, hz, rgb, alpha, scale, u);
		}
		pose.popPose();
	}

	/** The finished look: fill, scanlines with their sheen, edge lines, halo. */
	private static void holo(VertexConsumer vc, PoseStack pose, float hx, float hy, float hz, int rgb, float alpha, float scale) {
		PoseStack.Pose last = pose.last();
		int fill = tint(rgb);
		TurboDraw.box(vc, last, hx, hy, hz, fill, FILL_A * alpha * (rgb == PALE ? 1.15f : 1f));
		scanlines(vc, last, hx, hy, hz, scale, alpha);
		edges(vc, last, hx, hy, hz, scale, tint(rgb == DEEP ? PALE : EDGE), EDGE_A * alpha);
		float rim = Math.min(0.045f / scale, 0.18f * Math.min(hx, Math.min(hy, hz))) + 0.012f / scale;
		TurboDraw.box(vc, last, hx + rim, hy + rim, hz + rim, tint(PALE), HALO_A * alpha);
	}

	/** A part being formed: cells of light filling in bottom-up, the newest white-hot, a faint outline round the rest. */
	private static void forming(VertexConsumer vc, PoseStack pose, float hx, float hy, float hz, int rgb, float alpha, float scale,
			float u) {
		PoseStack.Pose last = pose.last();
		int fill = tint(rgb);
		int seed = build == null ? 0 : build.index * 7919;
		float cell = CELL / scale;
		float[] h = { hx, hy, hz };
		for (int axis = 0; axis < 3; axis++) {
			int a1 = (axis + 1) % 3;
			int a2 = (axis + 2) % 3;
			int n1 = Mth.clamp(Mth.ceil(2f * h[a1] / cell), 1, 40);
			int n2 = Mth.clamp(Mth.ceil(2f * h[a2] / cell), 1, 40);
			float d1 = h[a1] / n1;
			float d2 = h[a2] / n2;
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				int face = axis * 2 + (sgn > 0 ? 1 : 0);
				for (int i = 0; i < n1; i++) {
					for (int j = 0; j < n2; j++) {
						float c1 = -h[a1] + d1 * (2 * i + 1);
						float c2 = -h[a2] + d2 * (2 * j + 1);
						// the sweep runs bottom-up (local y), scattered
						float cy = axis == 1 ? sgn * hy : a1 == 1 ? c1 : c2;
						float sweep = hy <= 0f ? 0f : (cy + hy) / (2f * hy);
						float t = 0.55f * sweep + 0.45f * hash(seed + face * 131071 + i * 977 + j * 31);
						t *= 0.9f; // the last cells land a little before the part is called done
						if (t > u) {
							continue;
						}
						boolean hot = u - t < 0.12f;
						float[] c = new float[3];
						c[axis] = sgn * h[axis];
						c[a1] = c1;
						c[a2] = c2;
						cellQuad(vc, last, axis, sgn, c, a1, d1, a2, d2, hot ? HOT : fill, hot ? 0.95f * alpha : (FILL_A + 0.1f) * alpha);
					}
				}
			}
		}
		// the outline brightens as the part comes together
		edges(vc, last, hx, hy, hz, scale, tint(EDGE), (0.2f + 0.72f * u * u) * alpha);
	}

	/** One flat cell on a box face: normal along {@code axis} ({@code sgn}), half-sizes {@code d1}, {@code d2} in-plane. */
	private static void cellQuad(VertexConsumer vc, PoseStack.Pose pose, int axis, int sgn, float[] c, int a1, float d1, int a2,
			float d2, int rgb, float alpha) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
		float[] n = new float[3];
		n[axis] = sgn;
		float[][] corners = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
		for (float[] k : corners) {
			float[] v = c.clone();
			v[a1] += k[0] * d1;
			v[a2] += k[1] * d2;
			vc.addVertex(pose, v[0], v[1], v[2]).setColor(r, g, b, a).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.FULL_BRIGHT).setNormal(pose, n[0], n[1], n[2]);
		}
	}

	/** The twelve edge lines of a box. */
	private static void edges(VertexConsumer vc, PoseStack.Pose last, float hx, float hy, float hz, float scale, int rgb, float alpha) {
		float t = Math.min(0.02f / scale, 0.3f * Math.min(hx, Math.min(hy, hz)) + 0.005f / scale);
		PoseStack ps = new PoseStack();
		ps.last().pose().set(last.pose());
		ps.last().normal().set(last.normal());
		for (int s1 = -1; s1 <= 1; s1 += 2) {
			for (int s2 = -1; s2 <= 1; s2 += 2) {
				bar(vc, ps, 0f, s1 * hy, s2 * hz, hx + t, t, t, rgb, alpha);
				bar(vc, ps, s1 * hx, 0f, s2 * hz, t, hy, t, rgb, alpha);
				bar(vc, ps, s1 * hx, s2 * hy, 0f, t, t, hz, rgb, alpha);
			}
		}
	}

	private static void bar(VertexConsumer vc, PoseStack ps, float x, float y, float z, float hx, float hy, float hz, int rgb, float a) {
		ps.pushPose();
		ps.translate(x, y, z);
		TurboDraw.box(vc, ps.last(), hx, hy, hz, rgb, a);
		ps.popPose();
	}

	/** Faint horizontal scanlines on the four sides, with a band of light rolling up through them. */
	private static void scanlines(VertexConsumer vc, PoseStack.Pose last, float hx, float hy, float hz, float scale, float alpha) {
		float step = SCAN_STEP / scale;
		if (2f * hy < 1.6f * step || alpha < 0.05f) {
			return;
		}
		int lines = Math.min(24, (int) (2f * hy / step));
		float th = 0.006f / scale;
		float off = 0.003f / scale;
		float time = clock();
		int rgb = tint(PALE);
		for (int k = 1; k <= lines; k++) {
			float y = -hy + 2f * hy * k / (lines + 1);
			float wave = Mth.sin(y * scale * 2.4f - time * 0.11f);
			float sheen = wave > 0f ? (float) Math.pow(wave, 10) : 0f;
			float a = (SCAN_A + 0.42f * sheen) * alpha;
			if (a < 0.02f) {
				continue;
			}
			strip(vc, last, 0, 1, hx + off, y, -hz, hz, th, rgb, a);
			strip(vc, last, 0, -1, -hx - off, y, -hz, hz, th, rgb, a);
			strip(vc, last, 2, 1, hz + off, y, -hx, hx, th, rgb, a);
			strip(vc, last, 2, -1, -hz - off, y, -hx, hx, th, rgb, a);
		}
	}

	/** A thin horizontal strip on the plane {@code axis = at} (x or z), from {@code w0} to {@code w1} along the other one. */
	private static void strip(VertexConsumer vc, PoseStack.Pose pose, int axis, int sgn, float at, float y, float w0, float w1,
			float th, int rgb, float alpha) {
		float[] c = new float[3];
		c[axis] = at;
		c[1] = y;
		int other = axis == 0 ? 2 : 0;
		c[other] = (w0 + w1) * 0.5f;
		cellQuad(vc, pose, axis, sgn, c, 1, th, other, (w1 - w0) * 0.5f, rgb, alpha);
	}

	/** How many blocks one unit of {@code pose} is (the hand constructs are drawn in 1/16-block pixel units). */
	private static float scaleOf(PoseStack pose) {
		Vector3f s = pose.last().pose().getScale(new Vector3f());
		float k = (s.x + s.y + s.z) / 3f;
		return k < 1.0e-4f ? 1f : k;
	}

	/** The game clock in ticks (for the sheen); 0 off-world. */
	private static float clock() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 0f;
		}
		return (mc.level.getGameTime() % 24000L) + mc.getTimer().getGameTimeDeltaPartialTick(false);
	}

	private static float hash(int n) {
		n = (n << 13) ^ n;
		n = n * (n * n * 15731 + 789221) + 1376312589;
		return (n & 0x7fffffff) / (float) 0x7fffffff;
	}

	/** A bright, nearly white accent (gems, emblems, nose cones, exhausts). Formed (if building) halfway through its turn. */
	public static void glow(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, float alpha) {
		if (piece() < 0.5f) {
			return;
		}
		pose.pushPose();
		pose.translate(x, y, z);
		TurboDraw.box(vc, pose.last(), hx, hy, hz, 0xE8FFEE, 0.9f * alpha);
		TurboDraw.box(vc, pose.last(), hx * 1.8f + 0.02f, hy * 1.8f + 0.02f, hz * 1.8f + 0.02f, tint(GREEN), 0.25f * alpha);
		pose.popPose();
	}

	/** A streak of light from the origin along +Z for {@code len} blocks, half-width {@code w}. */
	public static void streak(VertexConsumer vc, PoseStack pose, float len, float w, float age, float alpha) {
		pose.pushPose();
		pose.translate(0f, 0f, len / 2f);
		pose.mulPose(Axis.ZP.rotationDegrees(age * 25f));
		float half = len / 2f;
		TurboDraw.box(vc, pose.last(), w * 0.35f, w * 0.35f, half, 0xF0FFF4, alpha);
		TurboDraw.box(vc, pose.last(), w * 0.7f, w * 0.7f, half, tint(GREEN), 0.6f * alpha);
		pose.mulPose(Axis.ZP.rotationDegrees(45f));
		TurboDraw.box(vc, pose.last(), w, w, half, tint(GREEN), 0.22f * alpha);
		pose.popPose();
	}

	/** The Green Lantern emblem (a ring between two bars) facing +Z, {@code s} blocks across, at the origin. */
	public static void emblem(VertexConsumer vc, PoseStack pose, float s, float alpha) {
		if (piece() < 0.6f) {
			return;
		}
		float t = s * 0.09f;
		float r = s * 0.28f;
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 45f));
			pose.translate(0f, r, 0f);
			TurboDraw.box(vc, pose.last(), r * 0.42f, t, t, 0xE8FFEE, 0.9f * alpha);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(0f, -r * 1.1f, 0f);
		TurboDraw.box(vc, pose.last(), s * 0.5f, t, t, 0xE8FFEE, 0.85f * alpha);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, r * 1.1f, 0f);
		TurboDraw.box(vc, pose.last(), s * 0.5f, t, t, 0xE8FFEE, 0.85f * alpha);
		pose.popPose();
	}
}
