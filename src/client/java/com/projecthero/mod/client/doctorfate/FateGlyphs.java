package com.projecthero.mod.client.doctorfate;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate effects pass: the glowing-geometry kit the spells are drawn with -- flat additive bars (both faces),
 * soft glow bars, rings, discs, gradient walls, the ankh and a small set of hieroglyphs (the Eye of Horus, the djed pillar,
 * water, Ma'at's feather, the was sceptre, the sun disc, a bird, a scarab, the house), all built from straight strokes so they
 * read as glowing lines. Positions are world space; {@link #begin} takes the camera so every vertex is written relative to it
 * (no float precision loss far from the origin).
 */
final class FateGlyphs {
	/** Strokes in a unit square (-0.5..0.5, y up): each row is one polyline as x,y pairs. Index 0 is the ankh (drawn by {@link #ankh}). */
	private static final float[][][] GLYPHS = {
			{},
			// 1 the Eye of Horus
			{ { -0.45f, 0.28f, 0.45f, 0.32f }, { -0.38f, 0f, -0.1f, 0.15f, 0.2f, 0.15f, 0.42f, 0f, 0.2f, -0.12f, -0.1f, -0.12f, -0.38f, 0f },
					{ 0.02f, -0.06f, 0.02f, 0.1f }, { -0.06f, -0.12f, -0.12f, -0.45f }, { 0.16f, -0.12f, 0.26f, -0.38f, 0.42f, -0.3f } },
			// 2 the djed pillar
			{ { 0f, -0.5f, 0f, 0.42f }, { -0.3f, 0.42f, 0.3f, 0.42f }, { -0.3f, 0.26f, 0.3f, 0.26f }, { -0.3f, 0.1f, 0.3f, 0.1f },
					{ -0.22f, -0.5f, 0.22f, -0.5f } },
			// 3 water
			{ { -0.5f, 0.12f, -0.33f, 0.27f, -0.17f, 0.0f, 0f, 0.27f, 0.17f, 0.0f, 0.33f, 0.27f, 0.5f, 0.12f },
					{ -0.5f, -0.2f, -0.33f, -0.05f, -0.17f, -0.32f, 0f, -0.05f, 0.17f, -0.32f, 0.33f, -0.05f, 0.5f, -0.2f } },
			// 4 Ma'at's feather
			{ { 0f, -0.5f, 0f, 0.45f, 0.2f, 0.32f, 0.22f, -0.15f, 0f, -0.42f }, { 0f, 0.1f, 0.18f, 0.18f }, { 0f, -0.12f, 0.2f, -0.04f } },
			// 5 the was sceptre
			{ { 0f, -0.42f, 0f, 0.3f, 0.2f, 0.47f, 0.32f, 0.36f }, { -0.12f, -0.5f, 0f, -0.4f, 0.12f, -0.5f } },
			// 6 the sun disc
			{ { 0.22f, 0f, 0.16f, 0.16f, 0f, 0.22f, -0.16f, 0.16f, -0.22f, 0f, -0.16f, -0.16f, 0f, -0.22f, 0.16f, -0.16f, 0.22f, 0f },
					{ 0f, 0.3f, 0f, 0.48f }, { 0f, -0.3f, 0f, -0.48f }, { 0.3f, 0f, 0.48f, 0f }, { -0.3f, 0f, -0.48f, 0f },
					{ 0.24f, 0.24f, 0.36f, 0.36f }, { -0.24f, -0.24f, -0.36f, -0.36f } },
			// 7 a bird
			{ { -0.45f, 0.02f, 0f, 0.2f, 0.32f, 0.12f, 0.46f, 0.22f }, { 0f, 0.2f, -0.12f, -0.32f }, { 0.12f, 0.16f, 0.1f, -0.32f },
					{ -0.12f, -0.32f, 0.2f, -0.32f } },
			// 8 a scarab
			{ { 0f, 0.3f, 0.15f, 0.15f, 0.15f, -0.25f, 0f, -0.38f, -0.15f, -0.25f, -0.15f, 0.15f, 0f, 0.3f }, { 0f, 0.3f, 0f, 0.45f },
					{ 0.15f, 0.05f, 0.45f, 0.25f }, { -0.15f, 0.05f, -0.45f, 0.25f }, { 0.15f, -0.15f, 0.42f, -0.35f },
					{ -0.15f, -0.15f, -0.42f, -0.35f } },
			// 9 the house
			{ { -0.1f, -0.42f, -0.36f, -0.42f, -0.36f, 0.4f, 0.36f, 0.4f, 0.36f, -0.42f, 0.1f, -0.42f } } };

	public static final int COUNT = GLYPHS.length;

	private static VertexConsumer vc;
	private static Matrix4f mat;
	private static double cx, cy, cz;

	private FateGlyphs() {
	}

	/** The strokes of hieroglyph {@code g} (1..COUNT-1), for 2D drawing (the mindscape's columns). */
	static float[][] strokes(int g) {
		return GLYPHS[Math.floorMod(g, COUNT)];
	}

	static void begin(VertexConsumer consumer, Matrix4f m, Vec3 cam) {
		vc = consumer;
		mat = m;
		cx = cam.x;
		cy = cam.y;
		cz = cam.z;
	}

	static Vec3 camera() {
		return new Vec3(cx, cy, cz);
	}

	private static void v(Vec3 p, int rgb, float a) {
		vc.addVertex(mat, (float) (p.x - cx), (float) (p.y - cy), (float) (p.z - cz))
				.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.round(255 * Math.max(0f, Math.min(1f, a))));
	}

	/** A quad, both faces. */
	static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}
		v(a, rgb, alpha);
		v(b, rgb, alpha);
		v(c, rgb, alpha);
		v(d, rgb, alpha);
		v(d, rgb, alpha);
		v(c, rgb, alpha);
		v(b, rgb, alpha);
		v(a, rgb, alpha);
	}

	/** A quad fading from {@code aBottom} along a-b to {@code aTop} along d-c, both faces. */
	static void gradient(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float aBottom, float aTop) {
		v(a, rgb, aBottom);
		v(b, rgb, aBottom);
		v(c, rgb, aTop);
		v(d, rgb, aTop);
		v(d, rgb, aTop);
		v(c, rgb, aTop);
		v(b, rgb, aBottom);
		v(a, rgb, aBottom);
	}

	/** A flat bar from a to b of half-width w, lying in the plane with this normal. */
	static void bar(Vec3 a, Vec3 b, Vec3 normal, double w, int rgb, float alpha) {
		Vec3 d = b.subtract(a);
		Vec3 side = d.cross(normal);
		if (side.lengthSqr() < 1.0e-10) {
			return;
		}
		side = side.normalize().scale(w);
		quad(a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, alpha);
	}

	/** A bar seen from the camera (normal toward the eye). */
	static void beam(Vec3 a, Vec3 b, double w, int rgb, float alpha) {
		Vec3 mid = a.add(b).scale(0.5);
		bar(a, b, camera().subtract(mid), w, rgb, alpha);
	}

	/** A glowing line: a wide soft halo, the coloured stroke and a white-hot core. */
	static void glow(Vec3 a, Vec3 b, Vec3 normal, double w, int rgb, float alpha) {
		bar(a, b, normal, w * 3.2, rgb, alpha * 0.16f);
		bar(a, b, normal, w, rgb, alpha * 0.85f);
		bar(a, b, normal, w * 0.38, 0xFFFBEA, alpha * 0.8f);
	}

	static void glowBeam(Vec3 a, Vec3 b, double w, int rgb, float alpha) {
		Vec3 mid = a.add(b).scale(0.5);
		glow(a, b, camera().subtract(mid), w, rgb, alpha);
	}

	/** Point at (x, y) of the plane through c spanned by u (x) and v (y). */
	static Vec3 at(Vec3 c, Vec3 u, Vec3 v, double x, double y) {
		return c.add(u.scale(x)).add(v.scale(y));
	}

	/** A ring of radius r in the plane (u, v) through c. */
	static void ring(Vec3 c, Vec3 u, Vec3 v, double r, double w, int segs, int rgb, float alpha, boolean glow) {
		Vec3 n = u.cross(v);
		Vec3 prev = at(c, u, v, r, 0);
		for (int i = 1; i <= segs; i++) {
			double a = i * Math.PI * 2 / segs;
			Vec3 p = at(c, u, v, Math.cos(a) * r, Math.sin(a) * r);
			if (glow) {
				glow(prev, p, n, w, rgb, alpha);
			} else {
				bar(prev, p, n, w, rgb, alpha);
			}
			prev = p;
		}
	}

	/** An ellipse ring (rx along u, ry along v). */
	static void ellipse(Vec3 c, Vec3 u, Vec3 v, double rx, double ry, double w, int segs, int rgb, float alpha) {
		Vec3 n = u.cross(v);
		Vec3 prev = at(c, u, v, rx, 0);
		for (int i = 1; i <= segs; i++) {
			double a = i * Math.PI * 2 / segs;
			Vec3 p = at(c, u, v, Math.cos(a) * rx, Math.sin(a) * ry);
			glow(prev, p, n, w, rgb, alpha);
			prev = p;
		}
	}

	/** A filled disc (or ellipse) in the plane (u, v). */
	static void disc(Vec3 c, Vec3 u, Vec3 v, double rx, double ry, int segs, int rgb, float alpha) {
		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
			Vec3 p0 = at(c, u, v, Math.cos(a0) * rx, Math.sin(a0) * ry);
			Vec3 p1 = at(c, u, v, Math.cos(a1) * rx, Math.sin(a1) * ry);
			quad(c, p0, p1, p1, rgb, alpha);
		}
	}

	/** A low glowing wall round a circle on the ground (a barrier's rim), fading upward. */
	static void wall(Vec3 c, double r, double h, int segs, int rgb, float alpha) {
		Vec3 up = new Vec3(0, h, 0);
		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
			Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
			Vec3 p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
			gradient(p0, p1, p1.add(up), p0.add(up), rgb, alpha, 0f);
		}
	}

	/** The ankh: the loop on top, the cross-bar and the stem, centred on c, {@code size} tall, upright along v. */
	static void ankh(Vec3 c, Vec3 u, Vec3 v, double size, double w, int rgb, float alpha) {
		Vec3 n = u.cross(v);
		double loopY = size * 0.24, loopRx = size * 0.17, loopRy = size * 0.24;
		Vec3 lc = at(c, u, v, 0, loopY);
		Vec3 prev = at(lc, u, v, 0, -loopRy);
		for (int i = 1; i <= 16; i++) {
			double a = -Math.PI / 2 + i * Math.PI * 2 / 16;
			Vec3 p = at(lc, u, v, Math.cos(a) * loopRx, Math.sin(a) * loopRy);
			glow(prev, p, n, w, rgb, alpha);
			prev = p;
		}
		glow(at(c, u, v, -size * 0.32, 0), at(c, u, v, size * 0.32, 0), n, w * 1.1, rgb, alpha);
		glow(at(c, u, v, 0, 0), at(c, u, v, 0, -size * 0.5), n, w * 1.1, rgb, alpha);
	}

	/** Hieroglyph {@code index} (1..COUNT-1; 0 is the ankh) centred on c in the plane (u, v), {@code size} across. */
	static void glyph(int index, Vec3 c, Vec3 u, Vec3 v, double size, double w, int rgb, float alpha) {
		int g = Math.floorMod(index, COUNT);
		if (g == 0) {
			ankh(c, u, v, size, w, rgb, alpha);
			return;
		}
		Vec3 n = u.cross(v);
		for (float[] line : GLYPHS[g]) {
			for (int i = 0; i + 3 < line.length; i += 2) {
				Vec3 a = at(c, u, v, line[i] * size, line[i + 1] * size);
				Vec3 b = at(c, u, v, line[i + 2] * size, line[i + 3] * size);
				glow(a, b, n, w, rgb, alpha);
			}
		}
	}

	/** A band of {@code n} hieroglyphs round a circle of radius r in the plane (u, v), each standing outward, turned by {@code spin}. */
	static void band(Vec3 c, Vec3 u, Vec3 v, double r, int n, double size, double w, double spin, int rgb, float alpha, int seed) {
		for (int i = 0; i < n; i++) {
			double a = spin + i * Math.PI * 2 / n;
			Vec3 radial = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
			Vec3 tangent = u.scale(-Math.sin(a)).add(v.scale(Math.cos(a)));
			glyph(1 + Math.floorMod(seed + i * 7, COUNT - 1), c.add(radial.scale(r)), tangent, radial, size, w, rgb, alpha);
		}
	}

	/** Two unit vectors spanning the plane facing {@code n} (u horizontal where it can be). */
	static Vec3[] basis(Vec3 n) {
		Vec3 nn = n.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : n.normalize();
		Vec3 ref = Math.abs(nn.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = ref.cross(nn).normalize();
		Vec3 v = nn.cross(u).normalize();
		return new Vec3[] { u, v };
	}
}
