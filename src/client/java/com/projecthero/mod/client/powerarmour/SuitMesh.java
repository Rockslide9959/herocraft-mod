package com.projecthero.mod.client.powerarmour;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.powerarmour.SuitPanel.Part;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.17.5 Power Armour: the voxel mesher. A suit layer is a 128x128 texture in the player-skin layout (twice the resolution, so
 * a texel is half a model pixel); every opaque texel becomes a little block standing {@code depth} proud of the body box grown by
 * {@code inflate}. Neighbouring texels merge: the outward and inward faces into rectangles (per panel), the side walls into runs
 * along each edge (a wall samples the row / column of texels it borders, so a run of different colours is still one quad).
 *
 * <p>Each face's texel grid is stretched over the box grown by {@code inflate + depth}, so the blocks of two faces meet at the
 * corner instead of leaving a notch; a wall at the very edge of a face is pulled in a hair so the neighbouring face covers it
 * where that face has a block there.
 *
 * <p>Meshes are built once per (texture, inflate, depth) from the resource and kept until resources reload.
 */
public final class SuitMesh {
	public static final int TEX = 128;
	/** Floats per vertex: x, y, z, u, v, nx, ny, nz, shade. */
	public static final int STRIDE = 9;
	private static final float EDGE_INSET = 0.035f;

	private static final Map<String, SuitMesh> CACHE = new HashMap<>();

	/** Vertex data per part, per panel (4 vertices a quad). */
	private final Map<Part, Map<SuitPanel, float[]>> quads = new EnumMap<>(Part.class);

	private SuitMesh() {
	}

	public static void clearCache() {
		CACHE.clear();
	}

	/** The mesh of {@code texture} as a layer {@code inflate} out from the body, {@code depth} thick (null if unreadable). */
	public static SuitMesh of(ResourceLocation texture, float inflate, float depth) {
		String key = texture + "|" + inflate + "|" + depth;
		SuitMesh m = CACHE.get(key);
		if (m == null && !CACHE.containsKey(key)) {
			m = build(texture, inflate, depth);
			CACHE.put(key, m);
		}
		return m;
	}

	public float[] quads(Part part, SuitPanel panel) {
		Map<SuitPanel, float[]> map = quads.get(part);
		return map == null ? null : map.get(panel);
	}

	public Map<SuitPanel, float[]> quads(Part part) {
		return quads.getOrDefault(part, Map.of());
	}

	// ================================================================== building

	private static SuitMesh build(ResourceLocation texture, float inflate, float depth) {
		NativeImage img = load(texture);
		if (img == null) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] power armour layer {} could not be read", texture);
			return null;
		}
		try {
			SuitMesh mesh = new SuitMesh();
			for (Part part : Part.values()) {
				Map<SuitPanel, FloatList> lists = new EnumMap<>(SuitPanel.class);
				for (int f = 0; f < 6; f++) {
					buildFace(img, part, f, inflate, depth, lists);
				}
				Map<SuitPanel, float[]> out = new EnumMap<>(SuitPanel.class);
				lists.forEach((p, l) -> out.put(p, l.toArray()));
				mesh.quads.put(part, out);
			}
			return mesh;
		} finally {
			img.close();
		}
	}

	private static NativeImage load(ResourceLocation id) {
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
			if (res.isEmpty()) {
				return null;
			}
			try (InputStream in = res.get().open()) {
				return NativeImage.read(in);
			}
		} catch (Exception e) {
			return null;
		}
	}

	/** Face index: 0 north, 1 south, 2 west, 3 east, 4 down (top), 5 up (bottom). */
	private static final int[] FACE_BITS = { SuitPanel.F.N, SuitPanel.F.S, SuitPanel.F.W, SuitPanel.F.E, SuitPanel.F.D, SuitPanel.F.U };

	private static void buildFace(NativeImage img, Part part, int f, float inflate, float depth, Map<SuitPanel, FloatList> lists) {
		float x0 = part.x0, y0 = part.y0, z0 = part.z0, x1 = x0 + part.w, y1 = y0 + part.h, z1 = z0 + part.d;
		float g = inflate + depth;
		float gx0 = x0 - g, gx1 = x1 + g, gy0 = y0 - g, gy1 = y1 + g, gz0 = z0 - g, gz1 = z1 + g;
		int u = part.u * 2, v = part.v * 2, w = (int) (part.w * 2), h = (int) (part.h * 2), d = (int) (part.d * 2);
		int ox, oy, gw, gh;
		// origin at (s=0, t=0) without the depth term, U / V spans, N normal; depth e moves along N
		float[] o, U, V, N;
		switch (f) {
			case 0 -> { ox = u + d; oy = v + d; gw = w; gh = h; o = new float[] { gx0, gy0, z0 }; U = new float[] { gx1 - gx0, 0, 0 };
				V = new float[] { 0, gy1 - gy0, 0 }; N = new float[] { 0, 0, -1 }; }
			case 1 -> { ox = u + 2 * d + w; oy = v + d; gw = w; gh = h; o = new float[] { gx1, gy0, z1 }; U = new float[] { -(gx1 - gx0), 0, 0 };
				V = new float[] { 0, gy1 - gy0, 0 }; N = new float[] { 0, 0, 1 }; }
			case 2 -> { ox = u; oy = v + d; gw = d; gh = h; o = new float[] { x0, gy0, gz1 }; U = new float[] { 0, 0, -(gz1 - gz0) };
				V = new float[] { 0, gy1 - gy0, 0 }; N = new float[] { -1, 0, 0 }; }
			case 3 -> { ox = u + d + w; oy = v + d; gw = d; gh = h; o = new float[] { x1, gy0, gz0 }; U = new float[] { 0, 0, gz1 - gz0 };
				V = new float[] { 0, gy1 - gy0, 0 }; N = new float[] { 1, 0, 0 }; }
			case 4 -> { ox = u + d; oy = v; gw = w; gh = d; o = new float[] { gx0, y0, gz1 }; U = new float[] { gx1 - gx0, 0, 0 };
				V = new float[] { 0, 0, -(gz1 - gz0) }; N = new float[] { 0, -1, 0 }; }
			default -> { ox = u + d + w; oy = v; gw = w; gh = d; o = new float[] { gx0, y1, gz1 }; U = new float[] { gx1 - gx0, 0, 0 };
				V = new float[] { 0, 0, -(gz1 - gz0) }; N = new float[] { 0, 1, 0 }; }
		}
		boolean[][] solid = new boolean[gw][gh];
		SuitPanel[][] panel = new SuitPanel[gw][gh];
		boolean any = false;
		float mid = inflate + depth * 0.5f;
		for (int a = 0; a < gw; a++) {
			for (int b = 0; b < gh; b++) {
				int px = ox + a, py = oy + b;
				if (px >= img.getWidth() || py >= img.getHeight()) {
					continue;
				}
				int abgr = img.getPixelRGBA(px, py);
				if (((abgr >>> 24) & 0xFF) < 16) {
					continue;
				}
				solid[a][b] = true;
				any = true;
				float s = (a + 0.5f) / gw, t = (b + 0.5f) / gh;
				float cx = o[0] + s * U[0] + t * V[0] + N[0] * mid;
				float cy = o[1] + s * U[1] + t * V[1] + N[1] * mid;
				float cz = o[2] + s * U[2] + t * V[2] + N[2] * mid;
				panel[a][b] = SuitPanel.of(part, FACE_BITS[f], cx, cy, cz);
			}
		}
		if (!any) {
			return;
		}
		Face face = new Face(o, U, V, N, gw, gh, ox, oy);
		// ---- outward + inward faces: greedy rectangles of solid texels sharing a panel
		boolean[][] used = new boolean[gw][gh];
		for (int b = 0; b < gh; b++) {
			for (int a = 0; a < gw; a++) {
				if (!solid[a][b] || used[a][b]) {
					continue;
				}
				SuitPanel p = panel[a][b];
				int a1 = a;
				while (a1 + 1 < gw && solid[a1 + 1][b] && !used[a1 + 1][b] && panel[a1 + 1][b] == p) {
					a1++;
				}
				int b1 = b;
				outer:
				while (b1 + 1 < gh) {
					for (int k = a; k <= a1; k++) {
						if (!solid[k][b1 + 1] || used[k][b1 + 1] || panel[k][b1 + 1] != p) {
							break outer;
						}
					}
					b1++;
				}
				for (int k = a; k <= a1; k++) {
					for (int j = b; j <= b1; j++) {
						used[k][j] = true;
					}
				}
				FloatList list = lists.computeIfAbsent(p, k -> new FloatList());
				face.rect(list, a, b, a1 + 1, b1 + 1, inflate + depth, 1f, false);
				face.rect(list, a, b, a1 + 1, b1 + 1, inflate, 0.5f, true);
			}
		}
		// ---- side walls, merged into runs along each edge
		for (int dir = 0; dir < 4; dir++) {
			// 0: -s (left), 1: +s (right), 2: -t (top), 3: +t (bottom)
			boolean vertical = dir < 2;
			int lines = vertical ? gw : gh;
			int len = vertical ? gh : gw;
			for (int line = 0; line < lines; line++) {
				int runStart = -1;
				SuitPanel runPanel = null;
				boolean runEdge = false;
				for (int k = 0; k <= len; k++) {
					boolean need = false;
					SuitPanel p = null;
					boolean edge = false;
					if (k < len) {
						int a = vertical ? line : k, b = vertical ? k : line;
						if (solid[a][b]) {
							int na = a + (dir == 0 ? -1 : dir == 1 ? 1 : 0), nb = b + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
							edge = na < 0 || nb < 0 || na >= gw || nb >= gh;
							p = panel[a][b];
							need = edge || !solid[na][nb] || panel[na][nb] != p;
						}
					}
					boolean continues = need && runStart >= 0 && p == runPanel && edge == runEdge;
					if (!continues && runStart >= 0) {
						face.wall(lists.computeIfAbsent(runPanel, q -> new FloatList()), dir, line, runStart, k, inflate, depth,
								runEdge ? EDGE_INSET : 0f);
						runStart = -1;
					}
					if (need && runStart < 0) {
						runStart = k;
						runPanel = p;
						runEdge = edge;
					}
				}
			}
		}
	}

	/** One face's grid in 3D. */
	private record Face(float[] o, float[] U, float[] V, float[] N, int gw, int gh, int ox, int oy) {
		float[] at(float s, float t, float e) {
			return new float[] { o[0] + s * U[0] + t * V[0] + N[0] * e, o[1] + s * U[1] + t * V[1] + N[1] * e,
					o[2] + s * U[2] + t * V[2] + N[2] * e };
		}

		void rect(FloatList list, int a0, int b0, int a1, int b1, float e, float shade, boolean inward) {
			float s0 = a0 / (float) gw, s1 = a1 / (float) gw, t0 = b0 / (float) gh, t1 = b1 / (float) gh;
			float u0 = (ox + a0) / (float) TEX, u1 = (ox + a1) / (float) TEX, v0 = (oy + b0) / (float) TEX, v1 = (oy + b1) / (float) TEX;
			float[] n = inward ? new float[] { -N[0], -N[1], -N[2] } : N;
			quad(list, at(s0, t0, e), u0, v0, at(s1, t0, e), u1, v0, at(s1, t1, e), u1, v1, at(s0, t1, e), u0, v1, n, shade);
		}

		/** A wall along the {@code dir} edge of texels {@code k0..k1-1} of grid line {@code line}, from inflate e to e + depth. */
		void wall(FloatList list, int dir, int line, int k0, int k1, float e, float depth, float inset) {
			float[] n;
			float[] p0, p1;
			float uA, vA, uB, vB;
			if (dir < 2) {
				float s = (line + (dir == 1 ? 1 : 0)) / (float) gw;
				p0 = new float[] { s, k0 / (float) gh };
				p1 = new float[] { s, k1 / (float) gh };
				n = norm(U, dir == 1 ? 1 : -1);
				uA = uB = (ox + line + 0.5f) / TEX;
				vA = (oy + k0) / (float) TEX;
				vB = (oy + k1) / (float) TEX;
			} else {
				float t = (line + (dir == 3 ? 1 : 0)) / (float) gh;
				p0 = new float[] { k0 / (float) gw, t };
				p1 = new float[] { k1 / (float) gw, t };
				n = norm(V, dir == 3 ? 1 : -1);
				vA = vB = (oy + line + 0.5f) / TEX;
				uA = (ox + k0) / (float) TEX;
				uB = (ox + k1) / (float) TEX;
			}
			float[] a = at(p0[0], p0[1], e), b = at(p1[0], p1[1], e), c = at(p1[0], p1[1], e + depth), d = at(p0[0], p0[1], e + depth);
			if (inset > 0f) {
				for (float[] q : new float[][] { a, b, c, d }) {
					q[0] -= n[0] * inset;
					q[1] -= n[1] * inset;
					q[2] -= n[2] * inset;
				}
			}
			quad(list, a, uA, vA, b, uB, vB, c, uB, vB, d, uA, vA, n, 0.82f);
		}

		private static float[] norm(float[] axis, int sign) {
			float l = (float) Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
			return new float[] { sign * axis[0] / l, sign * axis[1] / l, sign * axis[2] / l };
		}
	}

	/** Adds a quad, wound so its front faces along {@code n} (vanilla's convention: (p1 - p0) x (p2 - p0) points along n). */
	static void quad(FloatList list, float[] p0, float u0, float v0, float[] p1, float u1, float v1, float[] p2, float u2, float v2,
			float[] p3, float u3, float v3, float[] n, float shade) {
		float ax = p1[0] - p0[0], ay = p1[1] - p0[1], az = p1[2] - p0[2];
		float bx = p2[0] - p0[0], by = p2[1] - p0[1], bz = p2[2] - p0[2];
		float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
		boolean flip = cx * n[0] + cy * n[1] + cz * n[2] < 0f;
		if (flip) {
			vert(list, p0, u0, v0, n, shade);
			vert(list, p3, u3, v3, n, shade);
			vert(list, p2, u2, v2, n, shade);
			vert(list, p1, u1, v1, n, shade);
		} else {
			vert(list, p0, u0, v0, n, shade);
			vert(list, p1, u1, v1, n, shade);
			vert(list, p2, u2, v2, n, shade);
			vert(list, p3, u3, v3, n, shade);
		}
	}

	private static void vert(FloatList list, float[] p, float u, float v, float[] n, float shade) {
		list.add(p[0], p[1], p[2], u, v, n[0], n[1], n[2], shade);
	}

	// ================================================================== drawing

	/** Emits {@code data} (part-space pixels) with the colour {@code rgb} times each vertex's shade. */
	public static void emit(PoseStack pose, VertexConsumer vc, float[] data, int rgb, float alpha, int light, int overlay) {
		if (data == null) {
			return;
		}
		PoseStack.Pose p = pose.last();
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		int a = Math.round(alpha * 255f);
		for (int i = 0; i < data.length; i += STRIDE) {
			float sh = data[i + 8];
			vc.addVertex(p, data[i] / 16f, data[i + 1] / 16f, data[i + 2] / 16f)
					.setColor(Math.round(r * sh * 255f), Math.round(g * sh * 255f), Math.round(b * sh * 255f), a)
					.setUv(data[i + 3], data[i + 4]).setOverlay(overlay).setLight(light)
					.setNormal(p, data[i + 5], data[i + 6], data[i + 7]);
		}
	}

	/** A growable float array. */
	static final class FloatList {
		private float[] a = new float[256];
		private int n;

		void add(float... v) {
			if (n + v.length > a.length) {
				a = java.util.Arrays.copyOf(a, Math.max(a.length * 2, n + v.length));
			}
			System.arraycopy(v, 0, a, n, v.length);
			n += v.length;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(a, n);
		}
	}
}
