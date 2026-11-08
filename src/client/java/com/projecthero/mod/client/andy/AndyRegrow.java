package com.projecthero.mod.client.andy;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.render.VanillaSkeleton;

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
 * <li>{@link AndyState#STYLE_BONE} (the default): for an arm or leg ({@link #limb}, after the user's reference animations)
 * the skin creeps down from the joint with a ragged band of dark red flesh cubes riding its front and the vanilla skeleton's
 * thin bone poking out below the flesh, swallowed as they come down. For the head and body the vanilla skeleton mob's own
 * skull / ribcage ({@link VanillaSkeleton}, in skeleton.png) grows out of the stump, dark red flesh wraps round it and fills
 * the part out, then the skin closes over it in place from the joint.</li>
 * </ul>
 * No glow and no flash in any of them. The flesh is flat-coloured cubes on one white texel of the palette (one buffer, faces
 * between two settled flesh cells skipped).
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
		if (part >= AndyState.RIGHT_ARM) {
			limb(pose, buffers, light, tex, slim, part, p);
			return;
		}
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
		vanillaBone(pose, buffers, light, slim, part, p);
		Body body = Body.of(part, slim);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		Vector3f[] c = corners8();
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

	// ---------------------------------------------------------------- Bone and flesh: arms and legs

	/** Where the growing front starts (rows from the joint): a skin cap at the shoulder already, only flesh at the hip. */
	static final float LIMB_F0_ARM = 2.4f, LIMB_F0_LEG = 0f;
	/** How long (in rows of the front) a texel takes to pop in and settle, and a flesh cube to swell out. */
	static final float LIMB_SETTLE = 0.6f, LIMB_SWELL = 0.7f;
	/** Five dark reds, darkest first. */
	static final int[] LIMB_FLESH = { 0x3D0606, 0x5A0A0A, 0x7A0F12, 0x941A1A, 0xB02020 };

	/** The growing front for {@code p}, in rows (model px) from the joint: past the far end at 1, so the skin has closed. */
	static float limbFront(int part, boolean slim, float p) {
		float f0 = part <= AndyState.LEFT_ARM ? LIMB_F0_ARM : LIMB_F0_LEG;
		return f0 + (Limb.of(part, slim).end - f0) * p;
	}

	/**
	 * v0.17.3 (user's reference animations): an arm or leg regrows from the joint down. The skin (the player's own texels)
	 * creeps down from the shoulder / hip; right under it rides a ragged band of dark red flesh cubes (the boundary steps per
	 * texel column, the cubes bulge a little past the limb near the skin and taper in at the tip); out of the bottom of the
	 * flesh pokes the vanilla skeleton's thin arm / leg bone, which shoots out to nearly full length at the start and is
	 * swallowed as the flesh and skin come down over it. Last a little flesh cap at the hand / foot closes over with skin.
	 * Nothing flies and nothing glows; everything is steady from the joint to the far end.
	 */
	static void limb(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		Limb lb = Limb.of(part, slim);
		float f = limbFront(part, slim, p);
		PoseStack.Pose last = pose.last();
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		for (int i = 0; i < texels.size(); i++) {
			float g = (f - lb.texelAt[i]) / LIMB_SETTLE;
			if (g <= 0f) {
				continue;
			}
			AndyTexels.Texel t = texels.get(i);
			if (SkinPixels.shows(tex, t.u, t.v)) {
				popIn(skin, last, t, light, g);
			}
		}
		if (f >= lb.done) {
			return; // closed over
		}
		limbBone(pose, buffers, light, slim, part, p, f);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		float[] box = lb.box;
		for (int i = 0; i < lb.n; i++) {
			float lo = lb.fleshAt[i];
			if (f <= lo) {
				continue;
			}
			float hi = lb.skinAt[i];
			float x0 = box[0] + i % lb.w, y0 = box[1] + (i / lb.w) % lb.h, z0 = box[2] + i / (lb.w * lb.h);
			float s, out;
			if (f < hi) {
				// in the band: swelling in at its tip, bulging out near the skin, tapering in at the tip
				float d = (hi - f) / Math.max(0.01f, hi - lo); // 1 just appeared .. 0 about to be skinned
				float g = Math.min(1f, (f - lo) / LIMB_SWELL);
				s = lb.js[i] * (g >= 1f ? 1f : 0.25f + 0.75f * ease(g));
				out = 0.30f - 0.72f * d + lb.jo[i];
			} else if (f < hi + LIMB_SETTLE || (!Float.isNaN(lb.capAt[i]) && f < lb.capAt[i] + LIMB_SETTLE)) {
				// the skin is popping in over it (or, at the far end, it is the cap still waiting for its skin): just inside
				s = 0.96f;
				out = -0.04f;
			} else {
				continue;
			}
			int dir = lb.dir[i];
			float cx = x0 + 0.5f, cy = y0 + 0.5f, cz = z0 + 0.5f;
			if (dir != 0) {
				cx += (((dir & 2) != 0 ? 1 : 0) - ((dir & 1) != 0 ? 1 : 0)) * out;
				cz += (((dir & 8) != 0 ? 1 : 0) - ((dir & 4) != 0 ? 1 : 0)) * out;
			}
			float hlf = 0.5f * s;
			flatBox(vc, last, cx - hlf, cy - hlf, cz - hlf, cx + hlf, cy + hlf, cz + hlf, lb.rgb[i], light);
		}
	}

	/**
	 * The limb's bone: the vanilla skeleton's thin arm / leg ({@link VanillaSkeleton}), centred in the limb, drawn from just
	 * above the flesh front to its tip. It shoots out to most of its length in the first moments and is then swallowed by
	 * the flesh and skin coming down.
	 */
	private static void limbBone(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float p, float f) {
		float g = Math.min(1f, p / 0.15f);
		float reach = Math.min(11.9f, 7f + 4.9f * g * g * (3f - 2f * g));
		float top = Math.max(0f, f - 1.5f);
		if (reach <= top + 0.05f) {
			return;
		}
		float[] box = AndyTexels.box(part, slim);
		pose.pushPose();
		pose.translate((box[0] + box[3] * 0.5f) / 16f, 0f, 0f);
		pose.scale(0.94f, 1f, 0.94f); // a hair inside the flesh cubes round it
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VanillaSkeleton.TEXTURE));
		VanillaSkeleton.draw(pose.last(), vc, part, box[1] + top, box[1] + reach, light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}

	private static final int[][] BOX_FACES = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
	private static final float[][] BOX_NORMALS = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

	/** A flat-coloured box (model px) on the palette's white texel, all six faces. No allocation. */
	private static void flatBox(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		Matrix4f m = pose.pose();
		for (int f = 0; f < 6; f++) {
			int[] fi = BOX_FACES[f];
			float[] n = BOX_NORMALS[f];
			for (int k = 0; k < 4; k++) {
				int ci = fi[k];
				float x = ((ci & 1) == 0 ? x0 : x1) / 16f, y = ((ci & 2) == 0 ? y0 : y1) / 16f, z = ((ci & 4) == 0 ? z0 : z1) / 16f;
				vc.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}

	/**
	 * The fixed timetable of one limb (cached per part and skin model): for every 1 px cell of the limb (column x, z; row
	 * from the joint) the front at which its flesh appears and the front at which the skin covers it, and for every texel
	 * the front at which it pops in. The boundary is ragged per column (smooth noise down the rows plus a little per-cell
	 * scatter, kept monotonic down each column so nothing un-grows), and the band is 2.4-4 px tall.
	 */
	static final class Limb {
		float[] box;
		int w, h, d, n;
		float[] skinAt, fleshAt, capAt, jo, js;
		int[] rgb;
		/** Which outer sides a cell is on: 1 -x, 2 +x, 4 -z, 8 +z (0 inside). */
		byte[] dir;
		/** Per texel of {@link AndyTexels#of}. */
		float[] texelAt;
		/** The front past which nothing but finished skin is left; and the front at p = 1. */
		float done, end;

		private static final Limb[][] CACHE = new Limb[2][AndyState.PARTS];

		static Limb of(int part, boolean slim) {
			int s = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 1 : 0;
			Limb l = CACHE[s][part];
			if (l == null) {
				l = new Limb();
				l.make(part, slim);
				CACHE[s][part] = l;
			}
			return l;
		}

		private int cell(int x, int r, int z) {
			return x + r * w + z * w * h;
		}

		private void make(int part, boolean slim) {
			box = AndyTexels.box(part, slim);
			w = (int) box[3];
			h = (int) box[4];
			d = (int) box[5];
			n = w * h * d;
			skinAt = new float[n];
			fleshAt = new float[n];
			capAt = new float[n];
			jo = new float[n];
			js = new float[n];
			rgb = new int[n];
			dir = new byte[n];
			java.util.Arrays.fill(capAt, Float.NaN);
			long seed = 4242L + part * 101L + (slim ? 7L : 0L);
			float last = 0f;
			for (int z = 0; z < d; z++) {
				for (int x = 0; x < w; x++) {
					float prevS = -9f, prevF = -9f;
					for (int r = 0; r < h; r++) {
						int i = cell(x, r, z);
						float off = AndyTexels.noise(x * 0.85f + 3.1f, r * 0.55f, z * 0.85f + 1.7f, seed) * 2.6f - 1.3f
								+ (AndyTexels.hash(x, r, z, seed + 1) - 0.5f) * 0.5f;
						float s = Math.max(r + 0.5f - off, prevS + 0.3f);
						float fl = Math.max(s - (2.4f + 1.6f * AndyTexels.hash(x, r, z, seed + 2)), prevF + 0.3f);
						skinAt[i] = s;
						fleshAt[i] = fl;
						prevS = s;
						prevF = fl;
						jo[i] = (AndyTexels.hash(x, r, z, seed + 3) - 0.5f) * 0.24f;
						js[i] = 0.94f + 0.12f * AndyTexels.hash(x, r, z, seed + 4);
						// mostly the darker reds, a few bright ones
						float hc = AndyTexels.hash(x, r, z, seed + 5);
						rgb[i] = LIMB_FLESH[Math.min(LIMB_FLESH.length - 1, (int) (hc * hc * 1.25f * LIMB_FLESH.length))];
						int dr = (x == 0 ? 1 : 0) | (x == w - 1 ? 2 : 0) | (z == 0 ? 4 : 0) | (z == d - 1 ? 8 : 0);
						dir[i] = (byte) dr;
						if (r == h - 1) {
							capAt[i] = s + 0.9f + 1.1f * AndyTexels.hash(x, r, z, seed + 6);
							last = Math.max(last, capAt[i]);
						}
						last = Math.max(last, s);
					}
				}
			}
			List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
			texelAt = new float[texels.size()];
			float maxT = 0f;
			for (int k = 0; k < texelAt.length; k++) {
				AndyTexels.Texel t = texels.get(k);
				int xi = Math.max(0, Math.min(w - 1, (int) Math.floor(t.x - box[0])));
				int zi = Math.max(0, Math.min(d - 1, (int) Math.floor(t.z - box[2])));
				float at;
				if (t.ny < -0.5f) {
					at = skinAt[cell(xi, 0, zi)] - LIMB_SETTLE; // the joint end
				} else if (t.ny > 0.5f) {
					at = capAt[cell(xi, h - 1, zi)]; // the hand / foot end, last
				} else {
					int ri = Math.max(0, Math.min(h - 1, (int) Math.floor(t.y - box[1])));
					at = skinAt[cell(xi, ri, zi)];
				}
				if (t.overlay) {
					at += 0.35f;
				}
				texelAt[k] = at;
				maxT = Math.max(maxT, at);
			}
			done = Math.max(last, maxT) + LIMB_SETTLE + 0.05f;
			end = done + 0.05f;
		}
	}

	/**
	 * v0.17.3 (user): the bone is the vanilla skeleton mob's own part ({@link VanillaSkeleton}: the skull, the ribcage, the
	 * thin 2 px arm and leg bones, in skeleton.png), drawn in the player part's space (so it is posed exactly like the part
	 * it replaces), centred in it, and revealed from the joint outward: a band of the bone that widens from the stump to the
	 * far end, its growing tip closed with the bone's own end face. The skull and the ribcage are drawn a little smaller
	 * than the 8 px player head / body so the flesh can close over them.
	 */
	private static void vanillaBone(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float p) {
		float f = Math.max(0f, Math.min(1f, (p - BONE_START) / BONE_SPAN));
		if (f <= 0f) {
			return;
		}
		f = f * f * (3f - 2f * f);
		float[] box = AndyTexels.box(part, slim);
		float cx = box[0] + box[3] * 0.5f;
		float y0, y1;
		pose.pushPose();
		switch (part) {
			case AndyState.HEAD -> {
				// from the neck up
				y0 = -8f * f;
				y1 = 0f;
				pose.translate(0f, -4f / 16f, 0f);
				pose.scale(SKULL_SCALE, SKULL_SCALE, SKULL_SCALE);
				pose.translate(0f, 4f / 16f, 0f);
			}
			case AndyState.BODY -> {
				// from the neck down
				y0 = 0f;
				y1 = 12f * f;
				pose.scale(RIBS_SCALE_X, 1f, RIBS_SCALE_Z);
			}
			case AndyState.RIGHT_ARM, AndyState.LEFT_ARM -> {
				// from the shoulder down, centred in the (wide or slim) arm
				y0 = -2f;
				y1 = -2f + 12f * f;
				pose.translate(cx / 16f, 0f, 0f);
			}
			default -> {
				// from the hip down
				y0 = 0f;
				y1 = 12f * f;
				pose.translate(cx / 16f, 0f, 0f);
			}
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VanillaSkeleton.TEXTURE));
		VanillaSkeleton.draw(pose.last(), vc, part, y0, y1, light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}

	/** The skull and the ribcage are scaled in from the player's 8 px head / body so the flesh closes over them. */
	static final float SKULL_SCALE = 0.8f, RIBS_SCALE_X = 0.8f, RIBS_SCALE_Z = 0.7f;

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

	private static final int[] FLESH = { 0x4C060C, 0x5E0910, 0x700C15, 0x84111B, 0x9A1A24 };

	/** The flesh grid of one part (cached). */
	static final class Body {
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

		private void make(int part, boolean slim) {
			fleshGrid(part, AndyTexels.box(part, slim), slim);
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
				case AndyState.BODY -> Math.abs(x) < 3.0f && Math.abs(z) < 1.2f; // inside the scaled-in vanilla ribcage
				default -> Math.abs(x - cx) < (slim ? 0.8f : 1.0f) && Math.abs(z - cz) < 1.0f;
			};
		}
	}
}
