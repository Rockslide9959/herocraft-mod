package com.projecthero.mod.client.omnitrix.alien.alienx;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.util.Mth;

/**
 * v0.17.2 round 4: the shapes of the Celestial Council's hall ({@link AlienXHall}), worked out once and only lit and faded per frame:
 * the sky (stars with a faint band of the galaxy through them, and nebulae, at infinity), the dark glass (a round dais of curved
 * tiles, a causeway of straight ones, standing slabs round the rim, the screen's frame and a plinth for the point of light) and the
 * light (the inlaid rings, the low curtain of light along every edge, the rings under the heads, the halo round the screen). Plain
 * coloured vertices: the look comes from the void showing through the glass, not from block textures.
 */
final class AlienXHallMesh {
	private static final float SKY_R = 420.0f;
	private static final int STARS = 3200;
	private static final int NEBULAE = 15;
	private static final int NEBULA_SEGS = 22;

	final float screenW;
	/** Per star: x y z size colour phase. */
	private final float[] stars = new float[STARS * 6];
	/** Per nebula: x y z radius r g b alpha. */
	private final float[] nebulae = new float[NEBULAE * 8];
	/** Per vertex: x y z r g b a screenShare. */
	private float[] glass = new float[8 * 4 * 900];
	private int n;
	/** How much of the screen's light the next vertices take (its own frame stays dark against the picture). */
	private float shareScale = 1.0f;

	private AlienXHallMesh(float screenW) {
		this.screenW = screenW;
	}

	private static float hash(float x) {
		float s = Mth.sin(x * 12.9898f + 78.233f) * 43758.547f;
		return s - (float) Math.floor(s);
	}

	static AlienXHallMesh build(float screenW) {
		AlienXHallMesh m = new AlienXHallMesh(screenW);
		m.buildSky();
		m.buildGlass();
		return m;
	}

	// ---------------------------------------------------------------- the sky

	private void buildSky() {
		// the band of the galaxy: tilted across the sky, a little in front of the far screen wall
		float tilt = 0.55f;
		float ct = Mth.cos(tilt);
		float st = Mth.sin(tilt);
		for (int i = 0; i < STARS; i++) {
			float u = hash(i * 1.37f + 0.3f);
			float v = hash(i * 2.11f + 7.0f);
			float a = u * Mth.TWO_PI;
			float y;
			boolean band = i % 5 < 2;
			if (band) {
				float k = hash(i * 0.77f + 3.0f) + hash(i * 3.31f + 9.0f) - 1.0f; // bunched at the middle of the band
				y = k * 0.22f;
			} else {
				y = v * 2.0f - 1.0f;
			}
			float r = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
			float x = Mth.cos(a) * r;
			float z = Mth.sin(a) * r;
			float ty = y * ct - x * st;
			float tx = y * st + x * ct;
			int o = i * 6;
			stars[o] = tx * SKY_R;
			stars[o + 1] = ty * SKY_R;
			stars[o + 2] = z * SKY_R;
			float big = hash(i * 4.91f + 1.0f);
			stars[o + 3] = big > 0.985f ? 1.5f : big > 0.9f ? 0.95f : band ? 0.42f : 0.58f;
			stars[o + 4] = hash(i * 5.71f + 2.0f);
			stars[o + 5] = hash(i * 6.13f + 5.0f) * 6.28f;
		}
		float[][] clouds = {
				// direction, radius, colour (violet, blue, teal), alpha
				{ -0.75f, 0.28f, -0.6f, 170f, 0.42f, 0.22f, 0.95f, 15f }, { -0.55f, 0.42f, -0.72f, 120f, 0.62f, 0.3f, 1f, 13f },
				{ 0.8f, 0.12f, -0.6f, 190f, 0.2f, 0.42f, 1f, 15f }, { 0.62f, 0.3f, -0.72f, 120f, 0.35f, 0.62f, 1f, 12f },
				{ 0.1f, 0.72f, -0.68f, 210f, 0.3f, 0.2f, 0.75f, 12f }, { -0.15f, 0.55f, -0.82f, 110f, 0.7f, 0.34f, 0.95f, 8f },
				{ 0.2f, -0.42f, -0.88f, 170f, 0.14f, 0.3f, 0.7f, 10f }, { -0.82f, -0.2f, 0.52f, 180f, 0.5f, 0.22f, 0.8f, 11f },
				{ 0.72f, 0.3f, 0.62f, 170f, 0.16f, 0.5f, 0.8f, 11f }, { 0.08f, 0.2f, 0.98f, 200f, 0.34f, 0.2f, 0.9f, 9f },
				{ -0.96f, 0.05f, -0.1f, 140f, 0.2f, 0.34f, 0.85f, 10f }, { 0.96f, -0.1f, 0.1f, 150f, 0.46f, 0.22f, 0.86f, 10f },
				{ 0f, 0.98f, 0.1f, 180f, 0.22f, 0.2f, 0.62f, 9f }, { 0.3f, -0.9f, 0.3f, 200f, 0.3f, 0.16f, 0.58f, 8f },
				{ -0.36f, 0.08f, -0.94f, 90f, 0.3f, 0.75f, 0.95f, 7f } };
		for (int i = 0; i < NEBULAE; i++) {
			float[] c = clouds[i];
			float len = Mth.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
			int o = i * 8;
			nebulae[o] = c[0] / len * SKY_R;
			nebulae[o + 1] = c[1] / len * SKY_R;
			nebulae[o + 2] = c[2] / len * SKY_R;
			System.arraycopy(c, 3, nebulae, o + 3, 5);
		}
	}

	void emitSky(BufferBuilder bb, float time, float cyaw, float cpitch) {
		float yr = cyaw * Mth.DEG_TO_RAD;
		float pr = cpitch * Mth.DEG_TO_RAD;
		float rx = -Mth.cos(yr);
		float rz = -Mth.sin(yr);
		float fx = -Mth.sin(yr) * Mth.cos(pr);
		float fy = -Mth.sin(pr);
		float fz = Mth.cos(yr) * Mth.cos(pr);
		float ux = rz * fy;
		float uy = rx * fz - rz * fx;
		float uz = -rx * fy;
		for (int i = 0; i < NEBULAE; i++) {
			int o = i * 8;
			float x = nebulae[o];
			float y = nebulae[o + 1];
			float z = nebulae[o + 2];
			float rad = nebulae[o + 3];
			int r = Math.round(nebulae[o + 4] * 255.0f);
			int g = Math.round(nebulae[o + 5] * 255.0f);
			int b = Math.round(nebulae[o + 6] * 255.0f);
			int a = Math.round(nebulae[o + 7]);
			// a soft disc: two rings of quads, so the falloff is round and not a cone
			for (int s = 0; s < NEBULA_SEGS; s++) {
				float a0 = Mth.TWO_PI * s / NEBULA_SEGS;
				float a1 = Mth.TWO_PI * (s + 1) / NEBULA_SEGS;
				float c0 = Mth.cos(a0);
				float s0 = Mth.sin(a0);
				float c1 = Mth.cos(a1);
				float s1 = Mth.sin(a1);
				float m = rad * 0.45f;
				vtx(bb, x, y, z, 0, 0, 0, 0, r, g, b, a);
				vtx(bb, x, y, z, 0, 0, 0, 0, r, g, b, a);
				rim(bb, x, y, z, rx, rz, ux, uy, uz, c1 * m, s1 * m, r, g, b, a * 2 / 3);
				rim(bb, x, y, z, rx, rz, ux, uy, uz, c0 * m, s0 * m, r, g, b, a * 2 / 3);
				rim(bb, x, y, z, rx, rz, ux, uy, uz, c0 * m, s0 * m, r, g, b, a * 2 / 3);
				rim(bb, x, y, z, rx, rz, ux, uy, uz, c1 * m, s1 * m, r, g, b, a * 2 / 3);
				rim(bb, x, y, z, rx, rz, ux, uy, uz, c1 * rad, s1 * rad, r, g, b, 0);
				rim(bb, x, y, z, rx, rz, ux, uy, uz, c0 * rad, s0 * rad, r, g, b, 0);
			}
		}
		for (int i = 0; i < STARS; i++) {
			int o = i * 6;
			float s = stars[o + 3];
			float h = stars[o + 4];
			float twinkle = 0.78f + 0.22f * Mth.sin(time * (0.7f + h * 1.6f) + stars[o + 5]);
			int a = Math.round((s > 0.9f ? 255.0f : 170.0f + 85.0f * h) * twinkle);
			int r = h > 0.8f ? 190 : h > 0.62f ? 255 : 225;
			int g = h > 0.8f ? 205 : h > 0.62f ? 225 : 232;
			int b = h > 0.62f && h <= 0.8f ? 215 : 255;
			float x = stars[o];
			float y = stars[o + 1];
			float z = stars[o + 2];
			float ax = rx * s;
			float az = rz * s;
			float bx = ux * s;
			float by = uy * s;
			float bz = uz * s;
			bb.addVertex(x - ax - bx, y - by, z - az - bz).setColor(r, g, b, a);
			bb.addVertex(x + ax - bx, y - by, z + az - bz).setColor(r, g, b, a);
			bb.addVertex(x + ax + bx, y + by, z + az + bz).setColor(r, g, b, a);
			bb.addVertex(x - ax + bx, y + by, z - az + bz).setColor(r, g, b, a);
		}
	}

	private static void vtx(BufferBuilder bb, float x, float y, float z, float dx, float dy, float dz, int unused, int r, int g, int b, int a) {
		bb.addVertex(x + dx, y + dy, z + dz).setColor(r, g, b, a);
	}

	private static void rim(BufferBuilder bb, float x, float y, float z, float rx, float rz, float ux, float uy, float uz, float cu, float cv, int r, int g, int b,
			int a) {
		bb.addVertex(x + rx * cu + ux * cv, y + uy * cv, z + rz * cu + uz * cv).setColor(r, g, b, a);
	}

	// ---------------------------------------------------------------- the dark glass

	private void buildGlass() {
		float gap = 0.05f;
		// the dais: rings of curved tiles
		float rIn = 0.0f;
		int ring = 0;
		while (rIn < AlienXHall.DAIS_R - 0.01f) {
			float rOut = Math.min(AlienXHall.DAIS_R, rIn + (ring == 0 ? 1.8f : 2.3f));
			int segs = ring == 0 ? 8 : Math.max(10, Math.round(Mth.TWO_PI * (rIn + rOut) * 0.5f / 2.6f));
			for (int s = 0; s < segs; s++) {
				float a0 = Mth.TWO_PI * s / segs + ring * 0.37f;
				float a1 = Mth.TWO_PI * (s + 1) / segs + ring * 0.37f;
				float da = rOut > 0.0f ? gap / rOut : 0.0f;
				float r0 = rIn <= 0.0f ? 0.0f : rIn + gap;
				float r1 = rOut - gap;
				float v = 0.86f + 0.3f * hash(s * 1.3f + ring * 7.7f);
				quad(px(a0 + da, r0), 0.0f, pz(a0 + da, r0), px(a1 - da, r0), 0.0f, pz(a1 - da, r0), px(a1 - da, r1), 0.0f, pz(a1 - da, r1), px(a0 + da, r1), 0.0f,
						pz(a0 + da, r1), 0.075f * v, 0.07f * v, 0.15f * v, 0.86f);
			}
			rIn = rOut;
			ring++;
		}
		// the causeway
		float w = AlienXHall.WAY_HALF * 2.0f / 3.0f;
		int row = 0;
		for (float z = AlienXHall.DAIS_Z + AlienXHall.DAIS_R - 1.2f; z < AlienXHall.WAY_END; z += 2.4f, row++) {
			float z1 = Math.min(AlienXHall.WAY_END, z + 2.4f);
			for (int c = 0; c < 3; c++) {
				float x0 = -AlienXHall.WAY_HALF + c * w;
				float v = 0.86f + 0.3f * hash(row * 2.3f + c * 5.1f);
				quad(x0 + gap, -0.01f, z + gap, x0 + w - gap, -0.01f, z + gap, x0 + w - gap, -0.01f, z1 - gap, x0 + gap, -0.01f, z1 - gap, 0.075f * v, 0.07f * v,
						0.15f * v, 0.86f);
			}
		}
		// slabs standing round the rim, thinning toward the screen so they never stand in front of it
		for (int i = 0; i < 11; i++) {
			float a = Mth.PI * (0.5f + 0.15f + i * 0.1f); // the far (+Z) side is the causeway's, the near side the screen's
			if (Math.abs(Mth.sin(a)) > 0.92f && Mth.cos(a) < 0.0f) {
				continue;
			}
			float x = -Mth.sin(a) * (AlienXHall.DAIS_R + 0.9f);
			float z = AlienXHall.DAIS_Z - Mth.cos(a) * (AlienXHall.DAIS_R + 0.9f);
			if (z < AlienXHall.SCREEN_Z + 6.0f || Math.abs(x) < AlienXHall.WAY_HALF + 2.0f) {
				continue;
			}
			float h = 7.0f + 5.0f * hash(i * 3.3f);
			box(x - 0.55f, -1.5f, z - 0.55f, x + 0.55f, h, z + 0.55f, 0.06f, 0.055f, 0.12f, 1.0f);
		}
		// the screen's frame and its back
		float half = screenW * 0.5f;
		float y0 = AlienXHall.SCREEN_Y0;
		float y1 = y0 + AlienXHall.SCREEN_H;
		float b = AlienXHall.BEZEL;
		float zb = AlienXHall.SCREEN_Z - 0.3f;
		float zf = AlienXHall.SCREEN_Z + 0.35f;
		shareScale = 0.12f;
		box(-half - b, y0 - b, zb - 0.4f, half + b, y1 + b, zb, 0.05f, 0.05f, 0.1f, 1.0f);
		box(-half - b, y0 - b, zb, half + b, y0, zf, 0.1f, 0.1f, 0.2f, 1.0f);
		box(-half - b, y1, zb, half + b, y1 + b, zf, 0.1f, 0.1f, 0.2f, 1.0f);
		box(-half - b, y0, zb, -half, y1, zf, 0.1f, 0.1f, 0.2f, 1.0f);
		box(half, y0, zb, half + b, y1, zf, 0.1f, 0.1f, 0.2f, 1.0f);
		shareScale = 1.0f;
		// the plinth of the point of light
		float pz = AlienXHall.CONSOLE_Z;
		box(-0.5f, 0.0f, pz - 0.5f, 0.5f, 0.75f, pz + 0.5f, 0.12f, 0.11f, 0.24f, 1.0f);
	}

	private static float px(float a, float r) {
		return Mth.cos(a) * r;
	}

	private static float pz(float a, float r) {
		return AlienXHall.DAIS_Z + Mth.sin(a) * r;
	}

	private void box(float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
		quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r * 0.7f, g * 0.7f, b * 0.7f, a);
		quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * 0.85f, g * 0.85f, b * 0.85f, a);
		quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r * 0.85f, g * 0.85f, b * 0.85f, a);
		quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r * 1.2f, g * 1.2f, b * 1.2f, a);
		quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r * 0.5f, g * 0.5f, b * 0.5f, a);
	}

	private void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float r, float g,
			float b, float a) {
		add(x0, y0, z0, r, g, b, a);
		add(x1, y1, z1, r, g, b, a);
		add(x2, y2, z2, r, g, b, a);
		add(x3, y3, z3, r, g, b, a);
	}

	private void add(float x, float y, float z, float r, float g, float b, float a) {
		if ((n + 1) * 8 > glass.length) {
			glass = Arrays.copyOf(glass, glass.length * 2);
		}
		int o = n * 8;
		glass[o] = x;
		glass[o + 1] = y;
		glass[o + 2] = z;
		glass[o + 3] = r;
		glass[o + 4] = g;
		glass[o + 5] = b;
		glass[o + 6] = a;
		float half = screenW * 0.5f;
		float qx = Mth.clamp(x, -half, half);
		float qy = Mth.clamp(y, AlienXHall.SCREEN_Y0, AlienXHall.SCREEN_Y0 + AlienXHall.SCREEN_H);
		float dx = qx - x;
		float dy = qy - y;
		float dz = AlienXHall.SCREEN_Z - z;
		glass[o + 7] = z < AlienXHall.SCREEN_Z ? 0.0f : shareScale / (1.0f + (dx * dx + dy * dy + dz * dz) / 110.0f);
		n++;
	}

	void emitGlass(BufferBuilder bb, double cx, double cy, double cz, float screen) {
		float[] v = glass;
		for (int i = 0; i < n; i++) {
			int o = i * 8;
			double dx = v[o] - cx;
			double dy = v[o + 1] - cy;
			double dz = v[o + 2] - cz;
			double d2 = dx * dx + dy * dy + dz * dz;
			// a little brighter round him, as if the glass woke where he walks
			float near = 0.34f / (1.0f + (float) d2 / 40.0f);
			float s = v[o + 7] * screen;
			float k = 1.0f + near * 4.0f;
			float r = v[o + 3] * k + s * 0.34f;
			float g = v[o + 4] * k + s * 0.42f;
			float b = v[o + 5] * k + s * 0.62f;
			bb.addVertex((float) dx, (float) dy, (float) dz).setColor(c(r), c(g), c(b), c(v[o + 6]));
		}
	}

	private static int c(float x) {
		return Math.round(Mth.clamp(x, 0.0f, 1.0f) * 255.0f);
	}

	// ---------------------------------------------------------------- the light

	private static final int VIOLET_R = 150;
	private static final int VIOLET_G = 120;
	private static final int VIOLET_B = 255;
	private static final int BLUE_R = 150;
	private static final int BLUE_G = 195;
	private static final int BLUE_B = 255;

	void emitLight(BufferBuilder bb, double cx, double cy, double cz, float time, float screen, float atHeads, float atConsole, double walkerX) {
		float breathe = 0.82f + 0.18f * Mth.sin(time * 0.9f);
		// the inlaid rings
		disc(bb, 0.0f, AlienXHall.DAIS_Z, 7.0f, 0.07f, cx, cy, cz, VIOLET_R, VIOLET_G, VIOLET_B, Math.round(150.0f * breathe), time * 0.02f);
		disc(bb, 0.0f, AlienXHall.DAIS_Z, 14.0f, 0.07f, cx, cy, cz, BLUE_R, BLUE_G, BLUE_B, Math.round(120.0f * breathe), -time * 0.015f);
		disc(bb, 0.0f, AlienXHall.DAIS_Z, AlienXHall.DAIS_R - 0.14f, 0.12f, cx, cy, cz, BLUE_R, BLUE_G, BLUE_B, 235, 0.0f);
		// the rings under the heads (Serena pale blue, Bellicus red), and the point of light's
		float pulse = 0.72f + 0.28f * Mth.sin(time * 2.2f);
		int sa = Math.round((walkerX < 0.0 ? 150.0f + 105.0f * atHeads : 150.0f) * pulse);
		int ba = Math.round((walkerX > 0.0 ? 150.0f + 105.0f * atHeads : 150.0f) * pulse);
		disc(bb, -AlienXHall.CIRCLE_X, AlienXHall.CIRCLE_Z, AlienXHall.CIRCLE_R, 0.09f, cx, cy, cz, 159, 232, 255, sa, 0.0f);
		disc(bb, AlienXHall.CIRCLE_X, AlienXHall.CIRCLE_Z, AlienXHall.CIRCLE_R, 0.09f, cx, cy, cz, 255, 96, 70, ba, 0.0f);
		disc(bb, 0.0f, AlienXHall.CONSOLE_Z, AlienXHall.CONSOLE_REACH, 0.07f, cx, cy, cz, 235, 240, 255, Math.round((130.0f + 125.0f * atConsole) * pulse), 0.0f);
		// the curtain along the dais' rim (open where the causeway meets it, and at the screen's foot)
		int segs = 96;
		float h = 0.7f;
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.TWO_PI * i / segs;
			float a1 = Mth.TWO_PI * (i + 1) / segs;
			float r = AlienXHall.DAIS_R;
			float x0 = px(a0, r);
			float z0 = pz(a0, r);
			float x1 = px(a1, r);
			float z1 = pz(a1, r);
			if (z0 > AlienXHall.DAIS_Z && Math.abs(x0) < AlienXHall.WAY_HALF && Math.abs(x1) < AlienXHall.WAY_HALF) {
				continue;
			}
			wall(bb, x0, z0, x1, z1, h, cx, cy, cz, 90);
		}
		// and along the causeway
		float start = AlienXHall.DAIS_Z + Mth.sqrt(AlienXHall.DAIS_R * AlienXHall.DAIS_R - AlienXHall.WAY_HALF * AlienXHall.WAY_HALF);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * AlienXHall.WAY_HALF;
			wall(bb, x, start, x, AlienXHall.WAY_END, h, cx, cy, cz, 90);
			edge(bb, x, start, x, AlienXHall.WAY_END, 0.06f, cx, cy, cz, 235);
		}
		wall(bb, -AlienXHall.WAY_HALF, AlienXHall.WAY_END, AlienXHall.WAY_HALF, AlienXHall.WAY_END, h, cx, cy, cz, 90);
		// a soft light down the slabs' faces and a halo round the screen
		float half = screenW * 0.5f + AlienXHall.BEZEL;
		float y0 = AlienXHall.SCREEN_Y0 - AlienXHall.BEZEL;
		float y1 = AlienXHall.SCREEN_Y0 + AlienXHall.SCREEN_H + AlienXHall.BEZEL;
		if (screen > 0.01f) {
			halo(bb, half, y0, y1, 1.6f, Math.round(150.0f * screen), cx, cy, cz);
			halo(bb, half, y0, y1, 7.0f, Math.round(46.0f * screen), cx, cy, cz);
			double d = Math.sqrt(cx * cx + cz * cz);
			float distant = Mth.clamp((float) (d - 18.0) / 40.0f, 0.0f, 1.0f);
			if (distant > 0.01f) {
				halo(bb, half, y0, y1, 2.0f + (float) d * 0.06f, Math.round(90.0f * screen * distant), cx, cy, cz);
			}
			// its glow on the glass
			for (int i = 0; i < 7; i++) {
				float z0 = AlienXHall.SCREEN_Z + 0.6f + i * 2.2f;
				float z1 = z0 + 2.2f;
				int a0 = Math.round(58.0f * screen * (1.0f - i / 7.0f));
				int a1 = Math.round(58.0f * screen * (1.0f - (i + 1) / 7.0f));
				float hw = half + 1.0f;
				bb.addVertex((float) (-hw - cx), (float) (0.03 - cy), (float) (z0 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a0);
				bb.addVertex((float) (hw - cx), (float) (0.03 - cy), (float) (z0 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a0);
				bb.addVertex((float) (hw - cx), (float) (0.03 - cy), (float) (z1 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a1);
				bb.addVertex((float) (-hw - cx), (float) (0.03 - cy), (float) (z1 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a1);
			}
		}
	}

	/** A thin flat ring of light at floor level, dashed by {@code spin} when it is not zero. */
	private static void disc(BufferBuilder bb, float x, float z, float r, float half, double cx, double cy, double cz, int cr, int cg, int cb, int a, float spin) {
		int segs = 72;
		float y = 0.025f;
		for (int i = 0; i < segs; i++) {
			if (spin != 0.0f && i % 6 == 5) {
				continue;
			}
			float a0 = Mth.TWO_PI * i / segs + spin;
			float a1 = Mth.TWO_PI * (i + 1) / segs + spin;
			float c0 = Mth.cos(a0);
			float s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1);
			float s1 = Mth.sin(a1);
			float in = r - half;
			float out = r + half;
			bb.addVertex((float) (x + c0 * in - cx), (float) (y - cy), (float) (z + s0 * in - cz)).setColor(cr, cg, cb, a);
			bb.addVertex((float) (x + c1 * in - cx), (float) (y - cy), (float) (z + s1 * in - cz)).setColor(cr, cg, cb, a);
			bb.addVertex((float) (x + c1 * out - cx), (float) (y - cy), (float) (z + s1 * out - cz)).setColor(cr, cg, cb, a);
			bb.addVertex((float) (x + c0 * out - cx), (float) (y - cy), (float) (z + s0 * out - cz)).setColor(cr, cg, cb, a);
		}
	}

	/** A low curtain of light standing on an edge, fading upward. */
	private static void wall(BufferBuilder bb, float x0, float z0, float x1, float z1, float h, double cx, double cy, double cz, int a) {
		bb.addVertex((float) (x0 - cx), (float) (0.0 - cy), (float) (z0 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (x1 - cx), (float) (0.0 - cy), (float) (z1 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (x1 - cx), (float) (h - cy), (float) (z1 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, 0);
		bb.addVertex((float) (x0 - cx), (float) (h - cy), (float) (z0 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, 0);
	}

	private static void edge(BufferBuilder bb, float x0, float z0, float x1, float z1, float half, double cx, double cy, double cz, int a) {
		float y = 0.03f;
		bb.addVertex((float) (x0 - half - cx), (float) (y - cy), (float) (z0 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (x0 + half - cx), (float) (y - cy), (float) (z0 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (x1 + half - cx), (float) (y - cy), (float) (z1 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (x1 - half - cx), (float) (y - cy), (float) (z1 - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
	}

	/** A soft ring of light round the frame: {@code a} at its edge, nothing {@code spread} blocks out. */
	private static void halo(BufferBuilder bb, float half, float y0, float y1, float spread, int a, double cx, double cy, double cz) {
		float z = AlienXHall.SCREEN_Z - 0.05f;
		float ox = half + spread;
		float oy0 = y0 - spread;
		float oy1 = y1 + spread;
		ring(bb, -half, y0, half, y0, ox, oy0, -ox, oy0, z, a, cx, cy, cz);
		ring(bb, half, y1, -half, y1, -ox, oy1, ox, oy1, z, a, cx, cy, cz);
		ring(bb, -half, y1, -half, y0, -ox, oy0, -ox, oy1, z, a, cx, cy, cz);
		ring(bb, half, y0, half, y1, ox, oy1, ox, oy0, z, a, cx, cy, cz);
	}

	private static void ring(BufferBuilder bb, float ix0, float iy0, float ix1, float iy1, float ox0, float oy0, float ox1, float oy1, float z, int a, double cx,
			double cy, double cz) {
		bb.addVertex((float) (ix0 - cx), (float) (iy0 - cy), (float) (z - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (ix1 - cx), (float) (iy1 - cy), (float) (z - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, a);
		bb.addVertex((float) (ox0 - cx), (float) (oy0 - cy), (float) (z - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, 0);
		bb.addVertex((float) (ox1 - cx), (float) (oy1 - cy), (float) (z - cz)).setColor(BLUE_R, BLUE_G, BLUE_B, 0);
	}
}
