package com.projecthero.mod.client.symbiote.monster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form: procedural living geometry for the cocoon and the melt (the same technique as the suit's
 * {@code GooMesh}: quads pushed straight into a buffer, rebuilt every frame so they can writhe).
 *
 * <p>Polish pass: the geometry is collected first ({@link Batch}: per-vertex positions, smooth normals and UVs, a kind per quad) and
 * then emitted once per render pass -- the opaque base, then the additive gloss / vein / inner-glow layers -- because a buffer source
 * flushes (and invalidates) the previous buffer when another render type is asked for.
 */
final class MonsterMesh {
	/** Quad kinds (which passes draw them). */
	static final int SHELL = 0, STRAND = 1, POOL = 2, INNER = 3;

	private MonsterMesh() {
	}

	/** The colour of one vertex in a pass, from its camera-relative position and normal; negative = skip the quad. */
	interface Shade {
		int rgb(int kind, double x, double y, double z, float nx, float ny, float nz);
	}

	/** Quads in camera-relative space: 4 vertices x (x, y, z, nx, ny, nz, u, v) + the kind. */
	static final class Batch {
		float[] data = new float[4096];
		int[] kinds = new int[128];
		int quads;

		void clear() {
			quads = 0;
		}

		void quad(int kind, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 na, Vec3 nb, Vec3 nc, Vec3 nd, float u0, float v0, float u1, float v1) {
			int need = (quads + 1) * 32;
			if (need > data.length) {
				data = java.util.Arrays.copyOf(data, Math.max(need, data.length * 2));
			}
			if (quads + 1 > kinds.length) {
				kinds = java.util.Arrays.copyOf(kinds, kinds.length * 2);
			}
			int o = quads * 32;
			o = put(o, a, na, u0, v0);
			o = put(o, b, nb, u1, v0);
			o = put(o, c, nc, u1, v1);
			put(o, d, nd, u0, v1);
			kinds[quads++] = kind;
		}

		/** A quad with its own UV per corner ({@code uv} = u0, v0, u1, v1, u2, v2, u3, v3) and one normal. */
		void quadUv(int kind, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, float[] uv) {
			quad(kind, a, b, c, d, n, n, n, n, 0, 0, 0, 0);
			int o = (quads - 1) * 32;
			for (int k = 0; k < 4; k++) {
				data[o + k * 8 + 6] = uv[k * 2];
				data[o + k * 8 + 7] = uv[k * 2 + 1];
			}
		}

		/** A flat quad (one face normal for all four corners). */
		void flat(int kind, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1) {
			Vec3 n = b.subtract(a).cross(d.subtract(a));
			n = n.lengthSqr() < 1.0e-12 ? new Vec3(0, 1, 0) : n.normalize();
			quad(kind, a, b, c, d, n, n, n, n, u0, v0, u1, v1);
		}

		private int put(int o, Vec3 p, Vec3 n, float u, float v) {
			data[o] = (float) p.x;
			data[o + 1] = (float) p.y;
			data[o + 2] = (float) p.z;
			data[o + 3] = (float) n.x;
			data[o + 4] = (float) n.y;
			data[o + 5] = (float) n.z;
			data[o + 6] = u;
			data[o + 7] = v;
			return o + 8;
		}

		/** Emits every quad {@code shade} colours (a quad whose first vertex shades negative is skipped). */
		void emit(VertexConsumer vc, PoseStack.Pose pose, int light, Shade shade) {
			for (int q = 0; q < quads; q++) {
				int kind = kinds[q];
				int o = q * 32;
				int first = shade.rgb(kind, data[o], data[o + 1], data[o + 2], data[o + 3], data[o + 4], data[o + 5]);
				if (first < 0) {
					continue;
				}
				for (int k = 0; k < 4; k++) {
					int i = o + k * 8;
					int rgb = k == 0 ? first : shade.rgb(kind, data[i], data[i + 1], data[i + 2], data[i + 3], data[i + 4], data[i + 5]);
					if (rgb < 0) {
						rgb = 0;
					}
					vc.addVertex(pose, data[i], data[i + 1], data[i + 2])
							.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255)
							.setUv(data[i + 6], data[i + 7])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(light)
							.setNormal(pose, data[i + 3], data[i + 4], data[i + 5]);
				}
			}
		}

		/**
		 * A tube through {@code points} with per-point radii (frame carried along the curve, so it neither twists nor pinches), smooth
		 * radial normals; the texture wraps {@code uWraps} times round it and runs {@code vScale} per block along it from {@code v0}.
		 */
		void tube(int kind, Vec3[] points, float[] radii, int sides, float uWraps, float vScale, float v0) {
			int n = points.length;
			if (n < 2) {
				return;
			}
			Vec3[][] rings = new Vec3[n][sides];
			Vec3[][] normals = new Vec3[n][sides];
			float[] along = new float[n];
			Vec3 prevNormal = null;
			for (int i = 0; i < n; i++) {
				if (i > 0) {
					along[i] = along[i - 1] + (float) points[i].distanceTo(points[i - 1]);
				}
				Vec3 t = (i < n - 1 ? points[i + 1].subtract(points[i]) : points[i].subtract(points[i - 1]));
				if (t.lengthSqr() < 1.0e-10) {
					t = new Vec3(0, 1, 0);
				}
				t = t.normalize();
				Vec3 normal;
				if (prevNormal == null) {
					Vec3 ref = Math.abs(t.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
					normal = t.cross(ref).normalize();
				} else {
					normal = prevNormal.subtract(t.scale(prevNormal.dot(t)));
					normal = normal.lengthSqr() < 1.0e-8 ? t.cross(new Vec3(0, 1, 0)).normalize() : normal.normalize();
				}
				prevNormal = normal;
				Vec3 binormal = t.cross(normal);
				for (int k = 0; k < sides; k++) {
					double ang = Math.PI * 2 * k / sides;
					Vec3 dir = normal.scale(Math.cos(ang)).add(binormal.scale(Math.sin(ang)));
					normals[i][k] = dir;
					rings[i][k] = points[i].add(dir.scale(radii[i]));
				}
			}
			for (int i = 0; i < n - 1; i++) {
				float va = v0 + along[i] * vScale, vb = v0 + along[i + 1] * vScale;
				for (int k = 0; k < sides; k++) {
					int k2 = (k + 1) % sides;
					float u0 = uWraps * k / sides, u1 = uWraps * (k + 1) / sides;
					quad(kind, rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k], normals[i][k], normals[i][k2], normals[i + 1][k2],
							normals[i + 1][k], u0, va, u1, vb);
				}
			}
		}
	}
}
