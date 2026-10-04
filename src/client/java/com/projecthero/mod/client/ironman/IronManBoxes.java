package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * v0.14.21 round two: a tiny immediate-mode box drawer for the hand-built Iron Man parts (per-mark first-person
 * gauntlets, the Mark V blade in first person, the worn Repulsor Boots). Coordinates are model pixels in the space of
 * the {@code ModelPart} the caller already applied ({@code translateAndRotate}), i.e. vanilla's y-down limb space.
 *
 * <p>UVs follow vanilla's {@code ModelPart.Cube} box layout exactly (same vertex order and face rectangles), but with
 * UV dimensions independent of the geometry -- so a 4.8-wide gauntlet can sample the 4-texel-wide gauntlet panel of a
 * mark's 64x64 player-skin-layout texture, like the GeckoLib model does. A {@link Box#swatch} box samples one
 * rectangle on every face (material swatches).
 */
public final class IronManBoxes {
	private IronManBoxes() {
	}

	/**
	 * One box. {@code (x0,y0,z0)-(x1,y1,z1)} in pixels, grown by {@code inflate}; box UV at ({@code u}, {@code v}) with
	 * UV dimensions {@code uw x uh x ud}, or -- when {@code swatch} -- the rectangle ({@code u}, {@code v}, {@code uw},
	 * {@code uh}) on all six faces.
	 */
	public record Box(float x0, float y0, float z0, float x1, float y1, float z1, float inflate,
			float u, float v, float uw, float uh, float ud, boolean swatch) {
		public static Box uv(float x0, float y0, float z0, float x1, float y1, float z1, float inflate,
				float u, float v, float uw, float uh, float ud) {
			return new Box(x0, y0, z0, x1, y1, z1, inflate, u, v, uw, uh, ud, false);
		}

		public static Box swatch(float x0, float y0, float z0, float x1, float y1, float z1, float u, float v, float w, float h) {
			return new Box(x0, y0, z0, x1, y1, z1, 0f, u, v, w, h, 0f, true);
		}

		/** Mirror across x = 0 (right limb -> left limb), with a different UV origin. */
		public Box mirrored(float nu, float nv) {
			return new Box(-x1, y0, z0, -x0, y1, z1, inflate, nu, nv, uw, uh, ud, swatch);
		}

		public Box mirrored() {
			return mirrored(u, v);
		}
	}

	public static void draw(PoseStack pose, VertexConsumer vc, Box b, int texW, int texH, int light, int overlay, int argb) {
		PoseStack.Pose p = pose.last();
		float g = b.inflate();
		float x0 = (b.x0() - g) / 16f, y0 = (b.y0() - g) / 16f, z0 = (b.z0() - g) / 16f;
		float x1 = (b.x1() + g) / 16f, y1 = (b.y1() + g) / 16f, z1 = (b.z1() + g) / 16f;
		float[][] v = {
				{ x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 },
				{ x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
		float u = b.u(), t = b.v(), w = b.uw(), h = b.uh(), d = b.ud();
		float[][] rect; // per face: u1, v1, u2, v2 (vanilla Polygon convention)
		if (b.swatch()) {
			float[] r = { u, t, u + w, t + h };
			rect = new float[][] { r, r, r, r, r, r };
		} else {
			rect = new float[][] {
					{ u + d, t, u + d + w, t + d },                    // DOWN (min y)
					{ u + d + w, t + d, u + d + w + w, t },            // UP (max y)
					{ u, t + d, u + d, t + d + h },                    // WEST (min x)
					{ u + d, t + d, u + d + w, t + d + h },            // NORTH (min z)
					{ u + d + w, t + d, u + d + w + d, t + d + h },    // EAST (max x)
					{ u + d + w + d, t + d, u + d + w + d + w, t + d + h } }; // SOUTH (max z)
		}
		int[][] faces = { { 5, 4, 0, 1 }, { 2, 3, 7, 6 }, { 0, 4, 7, 3 }, { 1, 0, 3, 2 }, { 5, 1, 2, 6 }, { 4, 5, 6, 7 } };
		float[][] normals = { { 0, -1, 0 }, { 0, 1, 0 }, { -1, 0, 0 }, { 0, 0, -1 }, { 1, 0, 0 }, { 0, 0, 1 } };
		for (int f = 0; f < 6; f++) {
			float[] r = rect[f];
			float[][] uvs = { { r[2], r[1] }, { r[0], r[1] }, { r[0], r[3] }, { r[2], r[3] } };
			for (int i = 0; i < 4; i++) {
				float[] q = v[faces[f][i]];
				vc.addVertex(p, q[0], q[1], q[2]).setColor(argb).setUv(uvs[i][0] / texW, uvs[i][1] / texH)
						.setOverlay(overlay).setLight(light).setNormal(p, normals[f][0], normals[f][1], normals[f][2]);
			}
		}
	}

	/**
	 * A flat glow quad facing along {@code axis} (0 = x, 1 = y, 2 = z; {@code sign} +-1) centred on ({@code cx}, {@code cy},
	 * {@code cz}) with half-size {@code r}, full texture 0..1 (the radial palm-glow sprite).
	 */
	public static void glowQuad(PoseStack pose, VertexConsumer vc, int axis, int sign, float cx, float cy, float cz, float r,
			int light, int overlay, int argb) {
		PoseStack.Pose p = pose.last();
		float[][] c = new float[4][3];
		float[][] offs = { { -r, -r }, { r, -r }, { r, r }, { -r, r } };
		for (int i = 0; i < 4; i++) {
			float a = offs[i][0], b = offs[i][1];
			c[i] = switch (axis) {
				case 0 -> new float[] { cx, cy + a, cz + b };
				case 1 -> new float[] { cx + a, cy, cz + b };
				default -> new float[] { cx + a, cy + b, cz };
			};
		}
		float nx = axis == 0 ? sign : 0, ny = axis == 1 ? sign : 0, nz = axis == 2 ? sign : 0;
		float[][] uvs = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
		// both windings so it shows from either side (no-cull is not guaranteed by every render type)
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				int i = pass == 0 ? k : 3 - k;
				vc.addVertex(p, c[i][0] / 16f, c[i][1] / 16f, c[i][2] / 16f).setColor(argb).setUv(uvs[i][0], uvs[i][1])
						.setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
			}
		}
	}
}
