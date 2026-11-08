package com.projecthero.mod.client.andy;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3: the three looks of a regrowing part, all over the same length of time (the server decides the time; the style is
 * only the drawing). Called from {@link AndyRender#regrow} in the part's own space (model px, +y down, -z the front).
 *
 * <ul>
 * <li>{@link AndyState#STYLE_STREAMING}: the skin's own pixels fly out of the stump on a curve, each to its exact place, and
 * click in from the joint outward.</li>
 * <li>{@link AndyState#STYLE_BUILD}: nothing flies. Each texel appears in its own place in the organic order of
 * {@link AndyTexels.Texel#grow} (spreading patches, broadly from the joint), popping in a little small and dark and settling
 * to full size and colour over a couple of ticks.</li>
 * <li>{@link AndyState#STYLE_BONE}: a thin voxel bone (a skull for the head, a spine and ribs for the body) pushes out of the
 * stump, dark red flesh wraps round it and fills the part out, then the skin closes over it in place from the joint.</li>
 * </ul>
 * No glow and no flash in any of them. Bone and flesh are flat-coloured cubes on one white texel of the palette (one buffer,
 * faces between two settled flesh cells skipped).
 */
public final class AndyRegrow {
	/** Streaming: the share of a regrowth each pixel spends flying in from the stump. */
	static final float FLIGHT = 0.34f;
	/** Build in place: the share of a regrowth a pixel takes to settle from popping in to full size. */
	static final float SETTLE = 0.075f;

	// Bone and flesh: when each layer runs (shares of the regrowth)
	static final float BONE_START = 0f, BONE_SPAN = 0.30f;
	static final float FLESH_START = 0.17f, FLESH_SPAN = 0.45f;
	static final float SKIN_START = 0.52f, SKIN_SPAN = 0.42f;

	/** The palette's plain white texel (column 5): bone and flesh are tinted from it. */
	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	private AndyRegrow() {
	}

	public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, int style) {
		switch (style) {
			case AndyState.STYLE_BUILD -> build(pose, buffers, light, tex, slim, part, p);
			case AndyState.STYLE_BONE -> boneAndFlesh(pose, buffers, light, tex, slim, part, p);
			default -> streaming(pose, buffers, light, tex, slim, part, p);
		}
	}

	// ---------------------------------------------------------------- Streaming

	static void streaming(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		float[] joint = AndyTexels.joint(part, slim);
		PoseStack.Pose last = pose.last();
		Quaternionf q = new Quaternionf();
		Vector3f[] c = corners8();
		for (AndyTexels.Texel t : texels) {
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float click = t.order * (1f - FLIGHT) + FLIGHT;
			float launch = click - FLIGHT;
			float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
			if (p >= click) {
				square(skin, last, t, u0, v0, u1, v1, light, 0f, 0f, 255);
			} else if (p >= launch) {
				float f = (p - launch) / FLIGHT;
				float e = f * f * (3f - 2f * f);
				// from the stump (a spot on the cut), bulging out past the surface, home to its own place
				float sx = joint[0] + (t.seed - 0.5f) * 2.4f, sy = joint[1], sz = joint[2] + (((t.seed * 7.31f) % 1f) - 0.5f) * 2.4f;
				float tx = t.x - t.nx * 0.5f, ty = t.y - t.ny * 0.5f, tz = t.z - t.nz * 0.5f;
				float bulge = 2.2f + t.seed * 2.2f;
				float mx = (sx + tx) * 0.5f + t.nx * bulge, my = (sy + ty) * 0.5f + t.ny * bulge, mz = (sz + tz) * 0.5f + t.nz * bulge;
				float a = 1f - e;
				float x = a * a * sx + 2 * a * e * mx + e * e * tx;
				float y = a * a * sy + 2 * a * e * my + e * e * ty;
				float z = a * a * sz + 2 * a * e * mz + e * e * tz;
				float size = (0.55f + 0.45f * e) * 0.5f;
				float spin = (1f - e) * (1f - e) * (4f + t.seed * 5f);
				q.setAngleAxis(spin, 0.4f + t.seed * 0.6f, 1f - t.seed, 0.3f);
				for (int i = 0; i < 8; i++) {
					c[i].set((i & 1) == 0 ? -size : size, (i & 2) == 0 ? -size : size, (i & 4) == 0 ? -size : size).rotate(q).add(x, y, z).mul(1f / 16f);
				}
				AndyRender.cubePublic(skin, last, c, u0, v0, u1, v1, light);
			}
		}
		// the inside of what is already there reads as flesh, not as the skin seen from behind
		inside(pose, buffers, light, tex, slim, part, t -> p >= t.order * (1f - FLIGHT) + FLIGHT);
	}

	// ---------------------------------------------------------------- Build in place

	static void build(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		PoseStack.Pose last = pose.last();
		for (AndyTexels.Texel t : AndyTexels.of(part, slim)) {
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float appear = t.grow * (1f - SETTLE);
			popIn(skin, last, t, light, (p - appear) / SETTLE);
		}
		inside(pose, buffers, light, tex, slim, part, t -> p >= t.grow * (1f - SETTLE) + SETTLE * 0.5f);
	}

	/** A texel growing in place: {@code f} 0..1 from popping in (small, dark, a hair proud) to settled. Nothing below 0. */
	private static void popIn(VertexConsumer skin, PoseStack.Pose last, AndyTexels.Texel t, int light, float f) {
		if (f <= 0f) {
			return;
		}
		float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
		if (f >= 1f) {
			square(skin, last, t, u0, v0, u1, v1, light, 0f, 0f, 255);
			return;
		}
		// ease out with a touch of overshoot: it swells into place
		float g = f - 1f;
		float s = 1f + 2.2f * g * g * g + 1.2f * g * g;
		float scale = 0.3f + 0.7f * s;
		float shrink = -(1f - scale) * Math.min(t.ha, t.hb);
		int shade = (int) (255 * (0.62f + 0.38f * Math.min(1f, f * 1.3f)));
		square(skin, last, t, u0, v0, u1, v1, light, 0.12f * (1f - f), shrink, shade);
	}

	// ---------------------------------------------------------------- Bone and flesh

	static void boneAndFlesh(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		PoseStack.Pose last = pose.last();
		boolean skinDone = true;
		for (AndyTexels.Texel t : AndyTexels.of(part, slim)) {
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float key = 0.7f * t.order + 0.3f * t.grow;
			float appear = SKIN_START + key * (SKIN_SPAN - SETTLE);
			float f = (p - appear) / SETTLE;
			if (f < 1f) {
				skinDone = false;
			}
			popIn(skin, last, t, light, f);
		}
		if (skinDone || p >= 0.999f) {
			return; // closed over: nothing inside can show
		}
		Body body = Body.of(part, slim);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		Vector3f[] c = corners8();
		for (Piece b : body.bones) {
			float appear = BONE_START + b.key * (BONE_SPAN - 0.06f);
			float f = (p - appear) / 0.06f;
			if (f <= 0f) {
				continue;
			}
			float s = f >= 1f ? 1f : 0.35f + 0.65f * ease(f);
			piece(vc, last, c, b, s, light, 63);
		}
		Piece[] grid = body.flesh;
		for (int i = 0; i < grid.length; i++) {
			Piece b = grid[i];
			if (b == null) {
				continue;
			}
			float f = fleshF(b, p);
			if (f <= 0f) {
				continue;
			}
			int mask = 63;
			if (f >= 1f) {
				// a face against a settled neighbour can't be seen
				mask = 0;
				for (int face = 0; face < 6; face++) {
					int j = body.neighbour(i, face);
					if (j < 0 || grid[j] == null || fleshF(grid[j], p) < 1f) {
						mask |= 1 << face;
					}
				}
				if (mask == 0) {
					continue;
				}
			}
			float s = f >= 1f ? 1f : 0.2f + 0.8f * ease(f);
			piece(vc, last, c, b, s, light, mask);
		}
	}

	private static float fleshF(Piece b, float p) {
		return (p - (FLESH_START + b.key * (FLESH_SPAN - 0.07f))) / 0.07f;
	}

	private static float ease(float f) {
		float g = f - 1f;
		return 1f + 2.2f * g * g * g + 1.2f * g * g;
	}

	/** One bone / flesh box, scaled by {@code s} about its centre, faces in {@code mask} (-x, +x, -y, +y, -z, +z). */
	private static void piece(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] c, Piece b, float s, int light, int mask) {
		float cx = (b.x0 + b.x1) * 0.5f, cy = (b.y0 + b.y1) * 0.5f, cz = (b.z0 + b.z1) * 0.5f;
		float hx = (b.x1 - b.x0) * 0.5f * s, hy = (b.y1 - b.y0) * 0.5f * s, hz = (b.z1 - b.z0) * 0.5f * s;
		for (int i = 0; i < 8; i++) {
			c[i].set((cx + ((i & 1) == 0 ? -hx : hx)) / 16f, (cy + ((i & 2) == 0 ? -hy : hy)) / 16f, (cz + ((i & 4) == 0 ? -hz : hz)) / 16f);
		}
		int r = (b.rgb >> 16) & 0xFF, g = (b.rgb >> 8) & 0xFF, bl = b.rgb & 0xFF;
		Matrix4f m = pose.pose();
		int[][] faces = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
		float[][] normals = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };
		for (int f = 0; f < 6; f++) {
			if ((mask & (1 << f)) == 0) {
				continue;
			}
			int[] fi = faces[f];
			float[] n = normals[f];
			for (int k = 0; k < 4; k++) {
				Vector3f v = c[fi[k]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(r, g, bl, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}

	// ---------------------------------------------------------------- the inside of a half-grown part

	/** Behind each placed base texel, a dark-flesh square facing in: the part's inside as seen through the gaps. */
	private static void inside(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part,
			java.util.function.Predicate<AndyTexels.Texel> placed) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		for (AndyTexels.Texel t : AndyTexels.of(part, slim)) {
			if (t.overlay || !placed.test(t)) {
				continue;
			}
			float[][] q = AndyRender.cornersPublic(t, -0.06f, 0f);
			int shade = 0x4A + (int) (t.seed * 0x22);
			for (int i = 3; i >= 0; i--) {
				vc.addVertex(m, q[i][0] / 16f, q[i][1] / 16f, q[i][2] / 16f).setColor(shade, 8, 14, 255).setUv(WU0, WV0).setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light).setNormal(last, -t.nx, -t.ny, -t.nz);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static Vector3f[] corners8() {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f();
		}
		return c;
	}

	/** One placed texel square: {@code out} px proud, grown by {@code grow} px (negative: smaller), shade 0..255. */
	private static void square(VertexConsumer vc, PoseStack.Pose pose, AndyTexels.Texel t, float u0, float v0, float u1, float v1, int light, float out,
			float grow, int shade) {
		float[][] p = AndyRender.cornersPublic(t, out, grow);
		Matrix4f m = pose.pose();
		for (int i = 0; i < 4; i++) {
			float uu = (i == 1 || i == 2) ? u1 : u0;
			float vv = (i >= 2) ? v1 : v0;
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(shade, shade, shade, 255).setUv(uu, vv)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}

	// ---------------------------------------------------------------- the bones and flesh of each part

	/** A coloured box in the part's space with its turn ({@code key} 0..1) within its layer. */
	static final class Piece {
		final float x0, y0, z0, x1, y1, z1;
		final int rgb;
		float key;

		Piece(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
			this.x0 = Math.min(x0, x1);
			this.y0 = Math.min(y0, y1);
			this.z0 = Math.min(z0, z1);
			this.x1 = Math.max(x0, x1);
			this.y1 = Math.max(y0, y1);
			this.z1 = Math.max(z0, z1);
			this.rgb = rgb;
		}

		float cy() {
			return (y0 + y1) * 0.5f;
		}
	}

	static final int BONE = 0xEDE6D2, BONE_SHADE = 0xD9CFB8, BONE_DARK = 0x3A2622, TOOTH = 0xF8F4EA;
	private static final int[] FLESH = { 0x4C060C, 0x5E0910, 0x700C15, 0x84111B, 0x9A1A24 };

	/** The bone pieces and the flesh grid of one part (cached). */
	static final class Body {
		final List<Piece> bones = new ArrayList<>();
		Piece[] flesh;
		int gw, gh, gd;

		private static final Body[][] CACHE = new Body[2][AndyState.PARTS];

		static Body of(int part, boolean slim) {
			int s = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 1 : 0;
			Body b = CACHE[s][part];
			if (b == null) {
				b = new Body();
				b.make(part, slim);
				CACHE[s][part] = b;
			}
			return b;
		}

		int neighbour(int i, int face) {
			int x = i % gw, y = (i / gw) % gh, z = i / (gw * gh);
			switch (face) {
				case 0 -> x--;
				case 1 -> x++;
				case 2 -> y--;
				case 3 -> y++;
				case 4 -> z--;
				default -> z++;
			}
			if (x < 0 || y < 0 || z < 0 || x >= gw || y >= gh || z >= gd) {
				return -1;
			}
			return x + y * gw + z * gw * gh;
		}

		/** 0 at the joint, 1 at the far end, for a y in the part's space. */
		private static float along(int part, float[] box, float y) {
			float a = switch (part) {
				case AndyState.HEAD -> -y / 8f;
				case AndyState.RIGHT_ARM, AndyState.LEFT_ARM -> (y - box[1]) / box[4];
				default -> y / box[4];
			};
			return Math.max(0f, Math.min(1f, a));
		}

		private void bone(float x0, float y0, float z0, float x1, float y1, float z1) {
			bones.add(new Piece(x0, y0, z0, x1, y1, z1, (bones.size() & 1) == 0 ? BONE : BONE_SHADE));
		}

		private void bone(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
			bones.add(new Piece(x0, y0, z0, x1, y1, z1, rgb));
		}

		/** A bone column along y in 1 px voxels from {@code ya} to {@code yb}, {@code r} px thick each way. */
		private void column(float cx, float cz, float ya, float yb, float rx, float rz) {
			float step = 1f;
			for (float y = ya; y < yb - 0.01f; y += step) {
				bone(cx - rx, y, cz - rz, cx + rx, Math.min(yb, y + step * 0.92f), cz + rz);
			}
		}

		private void make(int part, boolean slim) {
			float[] box = AndyTexels.box(part, slim);
			float cx = box[0] + box[3] * 0.5f, cz = box[2] + box[5] * 0.5f;
			switch (part) {
				case AndyState.HEAD -> skull();
				case AndyState.BODY -> ribcage();
				case AndyState.RIGHT_ARM, AndyState.LEFT_ARM -> arm(cx, cz, box[1], slim ? 0.8f : 1f);
				default -> leg(cx, cz);
			}
			for (Piece b : bones) {
				b.key = along(part, box, b.cy());
			}
			fleshGrid(part, box, slim);
		}

		private void arm(float cx, float cz, float y0, float k) {
			bone(cx - 1.05f * k, y0 + 0.25f, cz - 1.05f, cx + 1.05f * k, y0 + 1.45f, cz + 1.05f); // the head of the humerus
			column(cx, cz, y0 + 1.45f, y0 + 5.6f, 0.62f * k, 0.62f);
			bone(cx - 1.0f * k, y0 + 5.6f, cz - 0.95f, cx + 1.0f * k, y0 + 6.55f, cz + 0.95f); // the elbow
			column(cx - 0.5f * k, cz + 0.1f, y0 + 6.55f, y0 + 10.4f, 0.32f * k, 0.36f); // radius
			column(cx + 0.5f * k, cz - 0.1f, y0 + 6.55f, y0 + 10.4f, 0.32f * k, 0.36f); // ulna
			bone(cx - 0.95f * k, y0 + 10.4f, cz - 0.8f, cx + 0.95f * k, y0 + 10.95f, cz + 0.8f); // the wrist
			for (int f = -1; f <= 1; f++) {
				column(cx + f * 0.62f * k, cz - 0.2f, y0 + 10.95f, y0 + 11.85f, 0.2f * k, 0.22f); // fingers
			}
		}

		private void leg(float cx, float cz) {
			bone(cx - 1.25f, 0.2f, cz - 1.0f, cx + 1.25f, 1.3f, cz + 1.0f); // the hip joint
			column(cx, cz, 1.3f, 5.6f, 0.7f, 0.7f); // femur
			bone(cx - 1.05f, 5.6f, cz - 1.0f, cx + 1.05f, 6.6f, cz + 1.0f); // the knee
			bone(cx - 0.55f, 5.7f, cz - 1.35f, cx + 0.55f, 6.5f, cz - 1.0f, BONE_SHADE); // the kneecap
			column(cx - 0.35f, cz, 6.6f, 10.6f, 0.45f, 0.45f); // tibia
			column(cx + 0.65f, cz + 0.2f, 6.6f, 10.6f, 0.22f, 0.22f); // fibula
			bone(cx - 0.9f, 10.6f, cz - 1.75f, cx + 0.9f, 11.6f, cz + 0.7f); // the foot
		}

		private void ribcage() {
			for (float y = 0.3f; y < 11.6f; y += 1f) {
				bone(-0.6f, y, 0.35f, 0.6f, y + 0.78f, 1.55f); // the spine, vertebra by vertebra
			}
			for (float x = -3.5f; x < 3.4f; x += 1f) {
				bone(x, 0.35f, -1.35f, x + 0.92f, 0.95f, -0.8f); // the collarbones
			}
			float[] ribs = { 2.0f, 3.4f, 4.8f, 6.2f, 7.6f };
			for (int i = 0; i < ribs.length; i++) {
				float y = ribs[i];
				float w = i == ribs.length - 1 ? 2.1f : 2.55f;
				bone(-w, y, -1.3f, -w + 0.5f, y + 0.55f, 1.35f); // the sides
				bone(w - 0.5f, y, -1.3f, w, y + 0.55f, 1.35f);
				if (i < ribs.length - 1) {
					bone(-w, y, -1.3f, -0.55f, y + 0.55f, -0.85f); // round the front
					bone(0.55f, y, -1.3f, w, y + 0.55f, -0.85f);
				}
				bone(-w, y, 0.9f, -0.6f, y + 0.55f, 1.35f, BONE_SHADE); // round the back to the spine
				bone(0.6f, y, 0.9f, w, y + 0.55f, 1.35f, BONE_SHADE);
			}
			for (float y = 1.4f; y < 7.0f; y += 1f) {
				bone(-0.4f, y, -1.45f, 0.4f, y + 0.9f, -1.0f, BONE_SHADE); // the breastbone
			}
			bone(-2.9f, 9.9f, -1.1f, -1.0f, 11.7f, 1.1f); // the hips
			bone(1.0f, 9.9f, -1.1f, 2.9f, 11.7f, 1.1f);
			bone(-1.0f, 10.9f, -0.2f, 1.0f, 11.6f, 1.2f, BONE_SHADE);
		}

		/** A blocky skull, built as a shell of voxels from the neck up, with sockets, a nose and teeth. */
		private void skull() {
			bone(-0.6f, -1.75f, 0.2f, 0.6f, -0.95f, 1.4f); // the top of the neck
			bone(-0.6f, -0.85f, 0.2f, 0.6f, -0.1f, 1.4f, BONE_SHADE);
			// the cranium: 7 x 6 x 7 voxels over x, z -3.4..3.4 and y -7.7..-2.0
			float vx = 6.8f / 7f, vy = 5.7f / 6f;
			for (int iy = 0; iy < 6; iy++) {
				for (int ix = 0; ix < 7; ix++) {
					for (int iz = 0; iz < 7; iz++) {
						boolean shell = ix == 0 || ix == 6 || iz == 0 || iz == 6 || iy == 0;
						if (!shell) {
							continue;
						}
						boolean edgeX = ix == 0 || ix == 6, edgeZ = iz == 0 || iz == 6;
						if (edgeX && edgeZ) {
							continue; // round the vertical corners off
						}
						if (iy == 0 && (edgeX || edgeZ)) {
							continue; // and the top into a dome
						}
						float x0 = -3.4f + ix * vx, z0 = -3.4f + iz * vx, y0 = -7.7f + iy * vy;
						int rgb = ((ix + iy + iz) & 1) == 0 ? BONE : BONE_SHADE;
						if (iz == 0) {
							// the face: sockets, nose, upper teeth
							if ((iy == 2 || iy == 3) && (ix == 1 || ix == 2 || ix == 4 || ix == 5)) {
								rgb = BONE_DARK;
							} else if (iy == 4 && ix == 3) {
								rgb = BONE_DARK;
							} else if (iy == 5) {
								rgb = (ix & 1) == 0 ? TOOTH : 0xCFC6B0;
							}
						}
						if (iy == 0) {
							// the dome sits a little lower and in
							bone(x0 + 0.05f, y0 + 0.35f, z0 + 0.05f, x0 + vx - 0.05f, y0 + vy, z0 + vx - 0.05f, rgb);
						} else {
							bone(x0, y0, z0, x0 + vx, y0 + vy, z0 + vx, rgb);
						}
					}
				}
			}
			// the jaw: a U of voxels under the front, with a row of lower teeth
			for (int ix = 0; ix < 5; ix++) {
				float x0 = -2.5f + ix;
				bone(x0, -1.95f, -3.25f, x0 + 0.95f, -1.35f, -2.55f, (ix & 1) == 0 ? TOOTH : 0xCFC6B0);
				bone(x0, -1.35f, -3.25f, x0 + 0.95f, -0.45f, -2.3f);
			}
			for (int iz = 0; iz < 3; iz++) {
				float z0 = -2.3f + iz;
				bone(-2.5f, -1.35f, z0, -1.6f, -0.45f, z0 + 0.95f, BONE_SHADE);
				bone(1.6f, -1.35f, z0, 2.5f, -0.45f, z0 + 0.95f, BONE_SHADE);
			}
		}

		/** One flesh cell per px of the part, inset a little under the skin, skipping where the bone runs (it shows between). */
		private void fleshGrid(int part, float[] box, boolean slim) {
			gw = (int) box[3];
			gh = (int) box[4];
			gd = (int) box[5];
			flesh = new Piece[gw * gh * gd];
			float inset = 0.14f;
			float sx = (box[3] - 2 * inset) / gw, sy = (box[4] - 2 * inset) / gh, sz = (box[5] - 2 * inset) / gd;
			float cx = box[0] + box[3] * 0.5f, cz = box[2] + box[5] * 0.5f;
			long seed = 77L + part * 13L;
			java.util.Random rnd = new java.util.Random(seed);
			for (int z = 0; z < gd; z++) {
				for (int y = 0; y < gh; y++) {
					for (int x = 0; x < gw; x++) {
						float x0 = box[0] + inset + x * sx, y0 = box[1] + inset + y * sy, z0 = box[2] + inset + z * sz;
						float mx = x0 + sx * 0.5f, my = y0 + sy * 0.5f, mz = z0 + sz * 0.5f;
						if (keepOut(part, mx, my, mz, cx, cz, slim)) {
							continue;
						}
						// muscle: darker deep, fibres along the part, a little noise
						float fib = AndyTexels.noise(mx * 0.9f, my * 0.22f, mz * 0.9f, seed);
						float dx = (mx - cx) / (box[3] * 0.5f), dz = (mz - cz) / (box[5] * 0.5f);
						float radial = Math.min(1f, (float) Math.sqrt(dx * dx + dz * dz) / 1.414f);
						if (part == AndyState.HEAD) {
							float dy = (my + 4f) / 4f;
							radial = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 1.732f);
						}
						// fibres: bands that run along the part, broken up by the noise; deeper is darker
						float band = AndyTexels.hash(Math.round(mx * 1.3f), 0, Math.round(mz * 1.3f), seed + 9) * 0.9f;
						int shade = Math.max(0, Math.min(FLESH.length - 1, (int) (fib * 2.6f + band * 1.6f + radial * 1.2f - 0.6f)));
						int rgb = FLESH[shade];
						if (rnd.nextFloat() < 0.05f) {
							rgb = 0xB8545C; // a pale streak of sinew
						}
						Piece c = new Piece(x0, y0, z0, x0 + sx, y0 + sy, z0 + sz, rgb);
						float a = along(part, box, my);
						float jitter = AndyTexels.noise(mx * 0.7f + 11f, my * 0.7f, mz * 0.7f, seed + 5) * 0.22f + rnd.nextFloat() * 0.06f;
						c.key = Math.max(0f, Math.min(1f, 0.62f * a + 0.22f * radial + jitter - 0.04f));
						flesh[x + y * gw + z * gw * gh] = c;
					}
				}
			}
		}

		/** Where the bone runs (no flesh there, so the bone shows until the flesh round it closes over). */
		private static boolean keepOut(int part, float x, float y, float z, float cx, float cz, boolean slim) {
			return switch (part) {
				case AndyState.HEAD -> Math.abs(x) < 3.0f && Math.abs(z) < 3.0f && y > -7.0f && y < -1.0f;
				case AndyState.BODY -> Math.abs(x) < 2.6f && Math.abs(z) < 1.0f;
				default -> Math.abs(x - cx) < (slim ? 0.8f : 1.0f) && Math.abs(z - cz) < 1.0f;
			};
		}
	}
}
