package com.projecthero.mod.client.render.workbench;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * v0.16.7 (user): the Stark Workbench's hologram geometry, shared by the in-world block entity renderer and the screen's
 * preview so both show the same thing (the approved concept, {@code scratchpad/gen_stark_workbench_concept.js}):
 * <ul>
 *   <li>a stylised suit figure (head = helmet, torso + arms = chestplate, thighs = leggings, shins = boots) drawn as
 *       scan-lined faces + edge lines -- pieces already built bright, the rest a dim wireframe, the piece being fabricated
 *       "printed" bright from the bottom up to the real progress;</li>
 *   <li>the 8-segment ring, the projector beam, the idle arc-reactor hologram, and the two floating screens (stat bars;
 *       recipe slot boxes + progress bar).</li>
 * </ul>
 * Units are model pixels (the caller scales). The suit mesh is built ONCE into flat arrays; drawing allocates nothing.
 * Everything goes into {@link WorkbenchHoloRenderType#HOLO} (additive, full-bright, no depth write).
 */
public final class WorkbenchHolo {
	public static final int R = 96;
	public static final int G = 220;
	public static final int B = 255;

	// ---- the suit mesh: one quad = 12 floats; per quad: piece (0 helmet .. 3 boots), kind, mid height
	private static final int KIND_BAND_A = 0;
	private static final int KIND_BAND_B = 1;
	private static final int KIND_CAP = 2;
	private static final int KIND_EDGE = 3;
	/** The figure's height in model pixels (feet at 0). */
	public static final float FIGURE_H = 32f;
	/** Each piece's height range (helmet, chest, legs, boots) -- the "print" line of a piece being built runs through it. */
	private static final float[][] PIECE_Y = { { 24, 32 }, { 12, 24 }, { 4, 12 }, { 0, 4 } };

	private static float[] quads = new float[12 * 1024];
	private static byte[] piece = new byte[1024];
	private static byte[] kind = new byte[1024];
	private static float[] midY = new float[1024];
	private static int count;

	static {
		box(0, -4, 24, -4, 4, 32, 4);           // helmet
		box(1, -4, 12, -2, 4, 24, 2);           // chest
		box(1, -8, 12, -2, -4, 24, 2);          // arms
		box(1, 4, 12, -2, 8, 24, 2);
		box(2, -4, 4, -2, -0.15f, 12, 2);        // legs
		box(2, 0.15f, 4, -2, 4, 12, 2);
		box(3, -4.2f, 0, -2.2f, -0.15f, 4, 2.2f); // boots
		box(3, 0.15f, 0, -2.2f, 4.2f, 4, 2.2f);
		// the chest arc reactor + eye slit as bright little plates on the front (+Z)
		plate(1, -1.1f, 18.6f, 1.1f, 20.8f, 2.06f);
		plate(0, -3f, 28.2f, -0.8f, 28.9f, 4.06f);
		plate(0, 0.8f, 28.2f, 3f, 28.9f, 4.06f);
		quads = java.util.Arrays.copyOf(quads, count * 12);
	}

	private WorkbenchHolo() {
	}

	private static void ensure() {
		if (count >= piece.length) {
			quads = java.util.Arrays.copyOf(quads, quads.length * 2);
			piece = java.util.Arrays.copyOf(piece, piece.length * 2);
			kind = java.util.Arrays.copyOf(kind, kind.length * 2);
			midY = java.util.Arrays.copyOf(midY, midY.length * 2);
		}
	}

	private static void put(int p, int k, float... v) {
		ensure();
		System.arraycopy(v, 0, quads, count * 12, 12);
		piece[count] = (byte) p;
		kind[count] = (byte) k;
		midY[count] = (v[1] + v[4] + v[7] + v[10]) / 4f;
		count++;
	}

	private static void plate(int p, float x0, float y0, float x1, float y1, float z) {
		put(p, KIND_EDGE, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
	}

	/** A box: four scan-lined sides (1 px bands), top + bottom caps, and its 12 edges as crossed thin strips. */
	private static void box(int p, float x0, float y0, float z0, float x1, float y1, float z1) {
		int bands = Math.max(1, Math.round(y1 - y0));
		for (int i = 0; i < bands; i++) {
			float a = y0 + (y1 - y0) * i / bands;
			float b = y0 + (y1 - y0) * (i + 1) / bands;
			int k = (i & 1) == 0 ? KIND_BAND_A : KIND_BAND_B;
			put(p, k, x0, a, z1, x1, a, z1, x1, b, z1, x0, b, z1); // front
			put(p, k, x1, a, z0, x0, a, z0, x0, b, z0, x1, b, z0); // back
			put(p, k, x0, a, z0, x0, a, z1, x0, b, z1, x0, b, z0); // left
			put(p, k, x1, a, z1, x1, a, z0, x1, b, z0, x1, b, z1); // right
		}
		put(p, KIND_CAP, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		put(p, KIND_CAP, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
		float w = 0.22f;
		float[] xs = { x0, x1 };
		float[] ys = { y0, y1 };
		float[] zs = { z0, z1 };
		for (float y : ys) {
			for (float z : zs) {
				edge(p, x0, y, z, x1, y, z, w);
			}
		}
		for (float x : xs) {
			for (float z : zs) {
				edge(p, x, y0, z, x, y1, z, w);
			}
		}
		for (float x : xs) {
			for (float y : ys) {
				edge(p, x, y, z0, x, y, z1, w);
			}
		}
	}

	/** An axis-aligned line as two crossed strips (visible from every angle). */
	private static void edge(int p, float ax, float ay, float az, float bx, float by, float bz, float w) {
		float h = w / 2f;
		if (ax != bx) {
			put(p, KIND_EDGE, ax, ay - h, az, bx, ay - h, az, bx, ay + h, az, ax, ay + h, az);
			put(p, KIND_EDGE, ax, ay, az - h, bx, ay, az - h, bx, ay, az + h, ax, ay, az + h);
		} else if (ay != by) {
			put(p, KIND_EDGE, ax - h, ay, az, ax + h, ay, az, ax + h, by, az, ax - h, by, az);
			put(p, KIND_EDGE, ax, ay, az - h, ax, ay, az + h, ax, by, az + h, ax, by, az - h);
		} else {
			put(p, KIND_EDGE, ax - h, ay, az, ax + h, ay, az, ax + h, ay, bz, ax - h, ay, bz);
			put(p, KIND_EDGE, ax, ay - h, az, ax, ay + h, az, ax, ay + h, bz, ax, ay - h, bz);
		}
	}

	// ------------------------------------------------------------------ drawing

	private static void quad(VertexConsumer vc, Matrix4f m, float[] q, int o, int a) {
		vc.addVertex(m, q[o], q[o + 1], q[o + 2]).setColor(R, G, B, a);
		vc.addVertex(m, q[o + 3], q[o + 4], q[o + 5]).setColor(R, G, B, a);
		vc.addVertex(m, q[o + 6], q[o + 7], q[o + 8]).setColor(R, G, B, a);
		vc.addVertex(m, q[o + 9], q[o + 10], q[o + 11]).setColor(R, G, B, a);
	}

	/** A flat rectangle in the XY plane at depth z. */
	public static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int a) {
		if (a <= 0) {
			return;
		}
		vc.addVertex(m, x0, y0, z).setColor(R, G, B, a);
		vc.addVertex(m, x1, y0, z).setColor(R, G, B, a);
		vc.addVertex(m, x1, y1, z).setColor(R, G, B, a);
		vc.addVertex(m, x0, y1, z).setColor(R, G, B, a);
	}

	private static void outline(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float t, int a) {
		rect(vc, m, x0, y0, x1, y0 + t, 0, a);
		rect(vc, m, x0, y1 - t, x1, y1, 0, a);
		rect(vc, m, x0, y0 + t, x0 + t, y1 - t, 0, a);
		rect(vc, m, x1 - t, y0 + t, x1, y1 - t, 0, a);
	}

	private static int alpha(float base, float k) {
		return Math.max(0, Math.min(255, Math.round(base * k)));
	}

	/**
	 * The suit figure, feet at y = 0, facing +Z. {@code built[i]} lights piece i; {@code building} (0..3, or -1) is printed
	 * bright up to {@code buildFrac}; {@code sweep} is the running scan line's height (pixels; anything off-figure = none).
	 */
	public static void suit(VertexConsumer vc, Matrix4f m, boolean[] built, int building, float buildFrac, float k, float sweep) {
		float printY = building >= 0 ? PIECE_Y[building][0] + (PIECE_Y[building][1] - PIECE_Y[building][0]) * buildFrac : -1f;
		for (int i = 0; i < count; i++) {
			int p = piece[i];
			boolean lit = built[p] || (p == building && midY[i] <= printY);
			int kd = kind[i];
			float a = switch (kd) {
				case KIND_BAND_A -> lit ? 62 : 9;
				case KIND_BAND_B -> lit ? 34 : 4;
				case KIND_CAP -> lit ? 48 : 6;
				default -> lit ? 235 : 75;
			};
			if (kd != KIND_EDGE && Math.abs(midY[i] - sweep) < 0.9f) {
				a += 55;
			}
			if (p == building && kd != KIND_EDGE && Math.abs(midY[i] - printY) < 0.6f) {
				a += 120; // the bright print line of the piece being built
			}
			quad(vc, m, quads, i * 12, alpha(a, k));
		}
	}

	/** The rotating ring: 8 thin segments of radius {@code r} round the Y axis at height y. */
	public static void ring(VertexConsumer vc, Matrix4f m, float angleRad, float r, float y, float k) {
		int a = alpha(200, k);
		float h = 0.18f;
		for (int i = 0; i < 8; i++) {
			double a0 = angleRad + i * Math.PI / 4 + 0.09;
			double a1 = angleRad + (i + 1) * Math.PI / 4 - 0.09;
			float c0 = (float) Math.cos(a0) * r;
			float s0 = (float) Math.sin(a0) * r;
			float c1 = (float) Math.cos(a1) * r;
			float s1 = (float) Math.sin(a1) * r;
			vc.addVertex(m, c0, y - h, s0).setColor(R, G, B, a);
			vc.addVertex(m, c1, y - h, s1).setColor(R, G, B, a);
			vc.addVertex(m, c1, y + h, s1).setColor(R, G, B, a);
			vc.addVertex(m, c0, y + h, s0).setColor(R, G, B, a);
			float ri = r - 0.35f;
			float ro = r + 0.35f;
			float ci0 = (float) Math.cos(a0);
			float si0 = (float) Math.sin(a0);
			float ci1 = (float) Math.cos(a1);
			float si1 = (float) Math.sin(a1);
			vc.addVertex(m, ci0 * ri, y, si0 * ri).setColor(R, G, B, a / 2);
			vc.addVertex(m, ci1 * ri, y, si1 * ri).setColor(R, G, B, a / 2);
			vc.addVertex(m, ci1 * ro, y, si1 * ro).setColor(R, G, B, a / 2);
			vc.addVertex(m, ci0 * ro, y, si0 * ro).setColor(R, G, B, a / 2);
		}
	}

	/** The projector's light: a soft square column from the lens (y 0) up to {@code top}, brightest at the lens. */
	public static void beam(VertexConsumer vc, Matrix4f m, float r0, float r1, float top, float k) {
		int a0 = alpha(70, k);
		int a1 = 0;
		float[][] c = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
		for (int i = 0; i < 4; i++) {
			float[] p = c[i];
			float[] q = c[(i + 1) & 3];
			vc.addVertex(m, p[0] * r0, 0, p[1] * r0).setColor(R, G, B, a0);
			vc.addVertex(m, q[0] * r0, 0, q[1] * r0).setColor(R, G, B, a0);
			vc.addVertex(m, q[0] * r1, top, q[1] * r1).setColor(R, G, B, a1);
			vc.addVertex(m, p[0] * r1, top, p[1] * r1).setColor(R, G, B, a1);
		}
		// the lit lens disc
		int al = alpha(90, k);
		vc.addVertex(m, -r0, 0.05f, -r0).setColor(R, G, B, al);
		vc.addVertex(m, -r0, 0.05f, r0).setColor(R, G, B, al);
		vc.addVertex(m, r0, 0.05f, r0).setColor(R, G, B, al);
		vc.addVertex(m, r0, 0.05f, -r0).setColor(R, G, B, al);
	}

	private static void sector(VertexConsumer vc, Matrix4f m, float r0, float r1, double a0, double a1, int a) {
		int steps = Math.max(1, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 16)));
		for (int s = 0; s < steps; s++) {
			double t0 = a0 + (a1 - a0) * s / steps;
			double t1 = a0 + (a1 - a0) * (s + 1) / steps;
			float c0 = (float) Math.cos(t0);
			float s0 = (float) Math.sin(t0);
			float c1 = (float) Math.cos(t1);
			float s1 = (float) Math.sin(t1);
			vc.addVertex(m, c0 * r1, s0 * r1, 0).setColor(R, G, B, a);
			vc.addVertex(m, c0 * r0, s0 * r0, 0).setColor(R, G, B, a);
			vc.addVertex(m, c1 * r0, s1 * r0, 0).setColor(R, G, B, a);
			vc.addVertex(m, c1 * r1, s1 * r1, 0).setColor(R, G, B, a);
		}
	}

	/** The idle hologram: an arc reactor in the XY plane round the origin (radius ~5 px); the caller turns it. */
	public static void reactor(VertexConsumer vc, Matrix4f m, float spinRad, float k) {
		int bright = alpha(210, k);
		int mid = alpha(110, k);
		int faint = alpha(28, k);
		// outer ring: ten coil blocks
		for (int i = 0; i < 10; i++) {
			double a0 = spinRad + i * Math.PI / 5 + 0.08;
			sector(vc, m, 4.2f, 5.3f, a0, a0 + Math.PI / 5 - 0.16, mid);
		}
		sector(vc, m, 5.3f, 5.55f, 0, Math.PI * 2, bright);
		sector(vc, m, 2.5f, 2.8f, 0, Math.PI * 2, bright);
		sector(vc, m, 0f, 4.2f, 0, Math.PI * 2, faint);
		// the inner triangle
		for (int i = 0; i < 3; i++) {
			double a = -spinRad * 0.5 + i * Math.PI * 2 / 3 + Math.PI / 2;
			double b = a + Math.PI * 2 / 3;
			float ax = (float) Math.cos(a) * 2.4f;
			float ay = (float) Math.sin(a) * 2.4f;
			float bx = (float) Math.cos(b) * 2.4f;
			float by = (float) Math.sin(b) * 2.4f;
			float nx = -(by - ay);
			float ny = bx - ax;
			float len = (float) Math.sqrt(nx * nx + ny * ny);
			nx = nx / len * 0.18f;
			ny = ny / len * 0.18f;
			vc.addVertex(m, ax - nx, ay - ny, 0).setColor(R, G, B, bright);
			vc.addVertex(m, bx - nx, by - ny, 0).setColor(R, G, B, bright);
			vc.addVertex(m, bx + nx, by + ny, 0).setColor(R, G, B, bright);
			vc.addVertex(m, ax + nx, ay + ny, 0).setColor(R, G, B, bright);
		}
		sector(vc, m, 0f, 1.1f, 0, Math.PI * 2, alpha(255, k));
	}

	/**
	 * The left floating screen: a {@code w} x {@code h} panel (origin bottom-left, XY plane) with a title bar and four stat
	 * bars ({@code bars} 0..1: armour, integrity, reactor energy, flight).
	 */
	public static void statsScreen(VertexConsumer vc, Matrix4f m, float w, float h, float[] bars, float k) {
		rect(vc, m, 0, 0, w, h, 0, alpha(22, k));
		outline(vc, m, 0, 0, w, h, 0.22f, alpha(220, k));
		rect(vc, m, w * 0.06f, h * 0.82f, w * 0.6f, h * 0.9f, 0, alpha(190, k));
		for (int i = 0; i < 4; i++) {
			float y1 = h * (0.7f - i * 0.16f);
			float y0 = y1 - h * 0.075f;
			float x0 = w * 0.08f;
			float x1 = w * 0.92f;
			rect(vc, m, x0, y0, x1, y1, 0, alpha(45, k));
			float v = bars == null || i >= bars.length ? 0f : Math.max(0f, Math.min(1f, bars[i]));
			rect(vc, m, x0, y0, x0 + (x1 - x0) * v, y1, 0, alpha(200, k));
		}
	}

	/**
	 * The right floating screen: title bar, up to 8 recipe slot boxes (2 x 4: lit = in the grid, dim = still missing) and
	 * the real fabrication progress bar.
	 */
	public static void recipeScreen(VertexConsumer vc, Matrix4f m, float w, float h, int slots, int mask, float progress, float k) {
		rect(vc, m, 0, 0, w, h, 0, alpha(22, k));
		outline(vc, m, 0, 0, w, h, 0.22f, alpha(220, k));
		rect(vc, m, w * 0.06f, h * 0.82f, w * 0.5f, h * 0.9f, 0, alpha(190, k));
		for (int i = 0; i < 8; i++) {
			int r = i / 4;
			int c = i % 4;
			float x0 = w * (0.08f + c * 0.215f);
			float x1 = x0 + w * 0.17f;
			float y1 = h * (0.7f - r * 0.27f);
			float y0 = y1 - h * 0.21f;
			if (i >= slots) {
				outline(vc, m, x0, y0, x1, y1, 0.15f, alpha(40, k));
				continue;
			}
			boolean have = (mask & (1 << i)) != 0;
			outline(vc, m, x0, y0, x1, y1, 0.2f, alpha(220, k));
			rect(vc, m, x0 + 0.3f, y0 + 0.3f, x1 - 0.3f, y1 - 0.3f, 0, alpha(have ? 120 : 18, k));
		}
		float x0 = w * 0.08f;
		float x1 = w * 0.92f;
		rect(vc, m, x0, h * 0.1f, x1, h * 0.16f, 0, alpha(45, k));
		rect(vc, m, x0, h * 0.1f, x0 + (x1 - x0) * Math.max(0f, Math.min(1f, progress)), h * 0.16f, 0, alpha(220, k));
	}
}
