package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.1 (user: "make the omnitrix model look better"): the Omnitrix's geometry -- the classic Ben 10 watch in chunky
 * voxels, drawn in the LEFT arm's space (pixels; +x = the back of the wrist, +y = down the arm toward the hand, z across).
 * <ul>
 *   <li>a thick charcoal cuff round the wrist, its long edges chamfered (stepped), lighter bevelled plates on both sides and
 *       raised plates at both ends of the top;</li>
 *   <li>a raised round housing on top: a stepped charcoal base, the black bezel ring with four lime lights round it, a grey
 *       stud with a light on its top edge, and the core inside it -- the grey disc with the green hourglass, which pops up
 *       ({@code lift}) and twists ({@code twist});</li>
 *   <li>four white prongs curling from the top down over the sides, and a round button with a green lens on the front side.</li>
 * </ul>
 * Every box face is nine-sliced from a swatch of {@link OmnitrixWatch#TEX} (a dark outline, a lit bevel and the material),
 * so the cartoon's outlines hold at any box size. Used by the wrist ({@link OmnitrixWatch}), the item
 * ({@link OmnitrixItemRenderer}) and the pod's reveal.
 */
public final class OmnitrixModel {
	private static final float T = 1f / 128f;
	/** The cuff's centre down the arm. */
	public static final float CY = 7.1f;

	// swatch origins (texels) in the 128x128 sheet
	static final int CUFF = 0, PLATE = 1, BLACK = 2, GREY = 3, WHITE = 4, GREEN = 5, INNER = 6, RING = 7, DIMGREEN = 8;
	private static final int[][] SW = { { 0, 0 }, { 16, 0 }, { 32, 0 }, { 48, 0 }, { 0, 16 }, { 16, 16 }, { 32, 16 }, { 48, 16 }, { 0, 32 } };

	private OmnitrixModel() {
	}

	/** x of the core's top (the face) for a lift, so callers can place the silhouette / hologram over it. */
	public static float coreTop(float L, float lift) {
		return L + 1.8f + lift;
	}

	/**
	 * Draws the watch. {@code L}: the arm's outer x (3 wide, 2 slim). {@code lift}: how far the core stands out of the bezel
	 * (0 at rest, ~1.3 popped, negative while slapped in). {@code twist}: the dial's turn, degrees. {@code screen}: the face
	 * shows the dark selection screen instead of the hourglass. {@code vc}: an entity cutout buffer on the watch texture.
	 */
	public static void draw(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float twist, boolean screen) {
		int lit = LightTexture.FULL_BRIGHT;
		float cy = CY;
		// ---- the cuff: four slabs round the wrist (covering the sleeve layer), the long edges stepped like a chamfer
		float xi0 = -1.3f, xi1 = L + 0.3f, zi = 2.3f, th = 0.55f;
		box(pose, vc, xi1, cy - 2.35f, -zi, xi1 + th, cy + 2.35f, zi, CUFF, light);                // top
		box(pose, vc, xi0 - th, cy - 1.65f, -zi + 0.2f, xi0, cy + 1.65f, zi - 0.2f, CUFF, light);   // underside (narrower, like a strap)
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			float za = sgn > 0 ? zi : -zi - th, zb = sgn > 0 ? zi + th : -zi;
			box(pose, vc, xi0, cy - 2.15f, za, xi1, cy + 2.15f, zb, CUFF, light);                    // the side
			// chamfer steps on the top and bottom long edges
			float zc0 = sgn > 0 ? zi : -zi - 0.3f, zc1 = sgn > 0 ? zi + 0.3f : -zi;
			box(pose, vc, xi1, cy - 2.15f, zc0, xi1 + 0.3f, cy + 2.15f, zc1, CUFF, light);
			box(pose, vc, xi0 - 0.3f, cy - 1.65f, zc0, xi0, cy + 1.65f, zc1, CUFF, light);
			// the bevelled side plate
			float zp0 = sgn > 0 ? zi + th : -zi - th - 0.22f, zp1 = sgn > 0 ? zi + th + 0.22f : -zi - th;
			box(pose, vc, -0.7f, cy - 1.65f, zp0, L - 0.1f, cy + 1.65f, zp1, PLATE, light);
			// raised plates at both ends of the top
			float e0 = sgn < 0 ? cy - 2.35f : cy + 1.5f, e1 = sgn < 0 ? cy - 1.5f : cy + 2.35f;
			box(pose, vc, xi1 + th, e0, -1.9f, xi1 + th + 0.28f, e1, 1.9f, PLATE, light);
		}
		float xo = xi1 + th;
		// ---- the button on the front (-z) side: a grey ring, a green lens
		float bx = L - 1.0f;
		box(pose, vc, bx - 0.7f, cy - 0.7f, -zi - th - 0.55f, bx + 0.7f, cy + 0.7f, -zi - th - 0.22f, RING, light);
		box(pose, vc, bx - 0.45f, cy - 0.45f, -zi - th - 0.62f, bx + 0.45f, cy + 0.45f, -zi - th - 0.5f, GREEN, lit);
		lens(pose, vc, bx, cy, -zi - th - 0.63f, 0.42f, lit);
		// a small green light on the back (+z) side
		box(pose, vc, bx - 0.35f, cy - 0.55f, zi + th + 0.22f, bx + 0.35f, cy + 0.55f, zi + th + 0.32f, GREEN, lit);
		// ---- the housing on top: a stepped base, the bezel (solid under, a ring above), four lights round it
		disc(pose, vc, xo - 0.1f, xo + 0.42f, 2.3f, 0f, CUFF, light);
		disc(pose, vc, xo + 0.42f, xo + 0.75f, 2.05f, 0f, BLACK, light);
		disc(pose, vc, xo + 0.75f, xo + 1.1f, 2.05f, 1.64f, BLACK, light);
		float ringTop = xo + 1.1f;
		float lr = 1.85f, ls = 0.18f;
		for (float[] d : new float[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
			float ly = cy + d[0] * lr, lz = d[1] * lr;
			box(pose, vc, ringTop - 0.05f, ly - ls, lz - ls, ringTop + 0.14f, ly + ls, lz + ls, GREEN, lit);
		}
		// the stud on the top (+z) edge, with its light
		box(pose, vc, xo, cy - 0.62f, 2.0f, xo + 1.0f, cy + 0.62f, 2.85f, GREY, light);
		box(pose, vc, xo + 1.0f, cy - 0.3f, 2.2f, xo + 1.12f, cy + 0.3f, 2.65f, GREEN, lit);
		// ---- the prongs: four white claws curling from the top down over the sides
		for (int end = -1; end <= 1; end += 2) {
			float yc = cy + end * 1.78f, w = 0.36f;
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				prong(pose, vc, L, xo, yc - w, yc + w, sgn, light);
			}
		}
		// ---- the core: pops up out of the bezel, twists a notch per dial turn
		float top = coreTop(L, lift);
		pose.pushPose();
		pose.translate(0f, cy / 16f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(twist));
		pose.translate(0f, -cy / 16f, 0f);
		float cr = 1.62f;
		disc(pose, vc, Math.min(xo + 0.45f, top - 0.2f), top, cr, 0f, GREY, light);
		// the face: the hourglass (or the dark selection screen) -- u down the arm, v across it (v 0 at +z)
		float s = 1f / 16f, fx = (top + 0.01f) * s;
		float u0 = 64 * T, v0 = screen ? 0 : 64 * T, u1 = 1f, v1 = screen ? 64 * T : 1f;
		float a = (cy - cr) * s, b = (cy + cr) * s, zp = cr * s;
		quad(vc, pose.last(), lit, fx, a, zp, fx, b, zp, fx, b, -zp, fx, a, -zp, 1, 0, 0, u0, v0, u1, v1);
		pose.popPose();
	}

	/** One prong on the {@code sgn} side, between y0 and y1: a stepped arc from the housing's edge round to the cuff's side. */
	private static void prong(PoseStack pose, VertexConsumer vc, float L, float xo, float y0, float y1, int sgn, int light) {
		float[][] seg = {
				// x0, x1, z0, z1 (for +z; mirrored for -z)
				{ xo - 0.1f, xo + 0.78f, 1.62f, 2.3f },   // rising beside the housing
				{ xo - 0.3f, xo + 0.6f, 2.2f, 2.9f },     // arching over the edge
				{ xo - 0.75f, xo + 0.1f, 2.8f, 3.38f },
				{ L - 0.9f, xo - 0.55f, 3.15f, 3.62f },   // down the side
				{ -0.6f, L - 0.75f, 3.28f, 3.7f },
				{ -1.25f, -0.4f, 3.0f, 3.48f },           // the tip curling under
		};
		for (float[] g : seg) {
			float z0 = sgn > 0 ? g[2] : -g[3], z1 = sgn > 0 ? g[3] : -g[2];
			box(pose, vc, g[0], y0, z0, g[1], y1, z1, WHITE, light);
		}
	}

	/** A stepped disc (or ring, {@code ri > 0}) round the face's axis, from x0 to x1, radius {@code r}: rows across z. */
	static void disc(PoseStack pose, VertexConsumer vc, float x0, float x1, float r, float ri, int sw, int light) {
		int rows = Math.max(3, Math.round(r * 2f / 0.4f));
		float step = r * 2f / rows;
		for (int i = 0; i < rows; i++) {
			float z0 = -r + i * step, z1 = z0 + step, zc = (z0 + z1) * 0.5f;
			float ext = (float) Math.sqrt(Math.max(0f, r * r - zc * zc));
			if (ext < 0.05f) {
				continue;
			}
			if (ri > 0f && Math.abs(zc) < ri) {
				float in = (float) Math.sqrt(ri * ri - zc * zc);
				box(pose, vc, x0, CY - ext, z0, x1, CY - in, z1, sw, light, false);
				box(pose, vc, x0, CY + in, z0, x1, CY + ext, z1, sw, light, false);
			} else {
				box(pose, vc, x0, CY - ext, z0, x1, CY + ext, z1, sw, light, false);
			}
		}
	}

	/** A round lens quad facing -z at (y, z) centre (x, y), radius r. */
	private static void lens(PoseStack pose, VertexConsumer vc, float x, float y, float z, float r, int light) {
		float s = 1f / 16f;
		float x0 = (x - r) * s, x1 = (x + r) * s, y0 = (y - r) * s, y1 = (y + r) * s, zz = z * s;
		quad(vc, pose.last(), light, x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, 0, 0, -1, 0, 48 * T, 16 * T, 64 * T);
	}

	static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int light) {
		box(pose, vc, x0, y0, z0, x1, y1, z1, sw, light, true);
	}

	/**
	 * A box (pixels) with every face nine-sliced from swatch {@code sw}: outline + bevel border of fixed size, stretched middle.
	 * {@code bevel} false: plain faces (the stepped discs' rows, which would otherwise read as a comb of outlines).
	 */
	static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int light, boolean bevel) {
		float s = 1f / 16f;
		x0 *= s;
		y0 *= s;
		z0 *= s;
		x1 *= s;
		y1 *= s;
		z1 *= s;
		PoseStack.Pose p = pose.last();
		int[] o = SW[sw];
		face(vc, p, light, o, bevel, x0, y0, z0, x1, y0, z0, x0, y1, z0, 0, 0, -1);
		face(vc, p, light, o, bevel, x1, y0, z1, x0, y0, z1, x1, y1, z1, 0, 0, 1);
		face(vc, p, light, o, bevel, x0, y0, z1, x0, y0, z0, x0, y1, z1, -1, 0, 0);
		face(vc, p, light, o, bevel, x1, y0, z0, x1, y0, z1, x1, y1, z0, 1, 0, 0);
		face(vc, p, light, o, bevel, x0, y0, z1, x1, y0, z1, x0, y0, z0, 0, -1, 0);
		face(vc, p, light, o, bevel, x0, y1, z0, x1, y1, z0, x0, y1, z1, 0, 1, 0);
	}

	/** One face from corner A, with edge A->B (u) and A->D (v), nine-sliced: a 2-texel border mapped to ~0.1 px. */
	private static void face(VertexConsumer vc, PoseStack.Pose p, int light, int[] sw, boolean bevel, float ax, float ay, float az, float bx, float by, float bz,
			float dx, float dy, float dz, float nx, float ny, float nz) {
		float ux = bx - ax, uy = by - ay, uz = bz - az, vx = dx - ax, vy = dy - ay, vz = dz - az;
		float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz), lv = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (lu < 1e-5f || lv < 1e-5f) {
			return;
		}
		float su = sw[0] * T, sv = sw[1] * T, e = 0.02f * T;
		if (!bevel) {
			quad(vc, p, light, ax, ay, az, bx, by, bz, bx + vx, by + vy, bz + vz, dx, dy, dz, nx, ny, nz, su + 3 * T, sv + 3 * T, su + 13 * T, sv + 13 * T);
			return;
		}
		float bord = 0.1f / 16f;
		float fu = Math.min(0.3f, bord / lu), fv = Math.min(0.3f, bord / lv);
		float[] pu = { 0, fu, 1 - fu, 1 }, pv = { 0, fv, 1 - fv, 1 };
		float[] tu = { su + e, su + 2 * T, su + 14 * T, su + 16 * T - e }, tv = { sv + e, sv + 2 * T, sv + 14 * T, sv + 16 * T - e };
		for (int j = 0; j < 3; j++) {
			for (int i = 0; i < 3; i++) {
				float a0 = pu[i], a1 = pu[i + 1], b0 = pv[j], b1 = pv[j + 1];
				quad(vc, p, light, ax + ux * a0 + vx * b0, ay + uy * a0 + vy * b0, az + uz * a0 + vz * b0, ax + ux * a1 + vx * b0, ay + uy * a1 + vy * b0,
						az + uz * a1 + vz * b0, ax + ux * a1 + vx * b1, ay + uy * a1 + vy * b1, az + uz * a1 + vz * b1, ax + ux * a0 + vx * b1,
						ay + uy * a0 + vy * b1, az + uz * a0 + vz * b1, nx, ny, nz, tu[i], tv[j], tu[i + 1], tv[j + 1]);
			}
		}
	}

	static void quad(VertexConsumer vc, PoseStack.Pose p, int light, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
			float cz, float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1) {
		vc.addVertex(p, ax, ay, az).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, bx, by, bz).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, dx, dy, dz).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
