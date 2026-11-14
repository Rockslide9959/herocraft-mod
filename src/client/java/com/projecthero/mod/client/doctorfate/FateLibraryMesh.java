package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.4 round 2: the geometry of Nabu's library ({@link FateLibrary}), as block-atlas textured quads with the light baked per vertex
 * on the CPU (the Hulk mind hall's method): a long sandstone hallway with chiseled pillars and gold braziers, opening through a gold
 * door into a great round library -- 18-block walls of bookshelves on four tiers split by gold ledges, a lapis ceiling, a sandstone
 * floor, twelve lecterns in a ring, a gold-framed screen on a plinth on the far wall. Each frame only the fog (distance to the
 * camera), the braziers' flicker and the screen's light are applied. Rebuilt when the screen's width (window aspect) or the atlas
 * changes. Coordinates: the library's centre is the origin, the screen is toward -Z, the hallway runs out toward +Z.
 */
final class FateLibraryMesh {
	// ---- the layout (blocks)
	static final float R = 17.0f;
	static final float WALL_H = 18.0f;
	static final int SEGMENTS = 96;
	/** The doorway is exactly two wall segments either side of +Z, so the hallway's walls meet the round wall's edges. */
	static final float GAP = 2.0f * Mth.TWO_PI / SEGMENTS;
	static final float HW = R * Mth.sin(GAP);
	static final float DOOR_Z = R * Mth.cos(GAP);
	static final float HH = 5.5f;
	static final float HALL_LEN = 64.0f;
	static final float HALL_END = DOOR_Z + HALL_LEN;
	static final float PILLAR_STEP = 7.0f;
	static final float[] LEDGES = { 4.5f, 9.0f, 13.5f };
	static final float LEDGE_OUT = 0.9f;
	static final float SCREEN_Z = -14.0f;
	static final float SCREEN_Y0 = 2.5f;
	static final float SCREEN_H = 9.0f;
	static final float BEZEL = 0.45f;
	static final float PLINTH_D = 1.2f;
	static final int LECTERNS = 12;
	static final float LECTERN_R = 12.0f;
	static final float EYE_R = 2.6f;

	private static final float AMBIENT = 0.075f;
	/** Per vertex: x y z u v lamp screen. */
	private static final int STRIDE = 7;

	final float screenW;
	final TextureAtlasSprite probe;
	private float[] v = new float[STRIDE * 4 * 8000];
	private int n;
	/** Lamps: x y z strength reach. */
	private final List<float[]> lamps = new ArrayList<>();

	private FateLibraryMesh(float screenW, TextureAtlasSprite probe) {
		this.screenW = screenW;
		this.probe = probe;
	}

	static TextureAtlasSprite sprite(Minecraft mc, String block) {
		return mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/" + block));
	}

	/** Lectern {@code i}: six down each side, from near the doorway to near the screen. */
	static float lecternX(int i) {
		float side = i < LECTERNS / 2 ? 1.0f : -1.0f;
		return side * Mth.sin(lecternAngle(i)) * LECTERN_R;
	}

	static float lecternZ(int i) {
		return Mth.cos(lecternAngle(i)) * LECTERN_R;
	}

	private static float lecternAngle(int i) {
		int k = i % (LECTERNS / 2);
		return (36.0f + k * (108.0f / (LECTERNS / 2 - 1))) * Mth.DEG_TO_RAD;
	}

	static FateLibraryMesh build(Minecraft mc, float screenW) {
		TextureAtlasSprite shelf = sprite(mc, "bookshelf");
		FateLibraryMesh m = new FateLibraryMesh(screenW, shelf);
		TextureAtlasSprite gold = sprite(mc, "gold_block");
		TextureAtlasSprite floor = sprite(mc, "sandstone_top");
		TextureAtlasSprite cut = sprite(mc, "cut_sandstone");
		TextureAtlasSprite sand = sprite(mc, "sandstone");
		TextureAtlasSprite chis = sprite(mc, "chiseled_sandstone");
		TextureAtlasSprite lapis = sprite(mc, "lapis_block");
		TextureAtlasSprite spruce = sprite(mc, "spruce_planks");
		TextureAtlasSprite black = sprite(mc, "black_concrete");
		TextureAtlasSprite lecSide = sprite(mc, "lectern_sides");
		TextureAtlasSprite lecTop = sprite(mc, "lectern_top");
		TextureAtlasSprite lecBase = sprite(mc, "lectern_base");
		TextureAtlasSprite lecFront = sprite(mc, "lectern_front");

		// ---- the lamps (baked): a great chandelier, sconces between the shelves, braziers down the hall
		m.lamps.add(new float[] { 0.0f, 12.5f, 0.0f, 2.4f, 120.0f });
		m.lamps.add(new float[] { 0.0f, 5.0f, 0.0f, 0.9f, 30.0f }); // the Eye's own glow
		for (int i = 0; i < 10; i++) {
			float a = (i + 0.5f) * Mth.TWO_PI / 10;
			if (Math.abs(Mth.wrapDegrees(a * Mth.RAD_TO_DEG)) < 20.0f) {
				continue; // none over the doorway
			}
			m.lamps.add(new float[] { Mth.sin(a) * (R - 1.4f), 3.4f, Mth.cos(a) * (R - 1.4f), 0.7f, 14.0f });
			m.lamps.add(new float[] { Mth.sin(a) * (R - 1.4f), 11.0f, Mth.cos(a) * (R - 1.4f), 0.5f, 14.0f });
		}
		int k = 0;
		for (float z = DOOR_Z + 3.0f; z < HALL_END - 1.0f; z += PILLAR_STEP, k++) {
			float far = Mth.clamp((z - DOOR_Z) / HALL_LEN, 0.0f, 1.0f);
			m.lamps.add(new float[] { (k % 2 == 0 ? 1 : -1) * (HW - 0.5f), 3.2f, z, 0.85f - 0.45f * far, 10.0f });
		}
		m.lamps.add(new float[] { 0.0f, 3.5f, HALL_END - 2.0f, 0.35f, 10.0f });

		// ---- the library
		m.planeY(0.0f, -R, R, -R, DOOR_Z, 1, floor);
		m.planeY(WALL_H, -R, R, -R, R, -1, lapis);
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS;
			float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float mid = Mth.wrapDegrees((a0 + a1) * 0.5f * Mth.RAD_TO_DEG);
			boolean door = Math.abs(mid) < GAP * Mth.RAD_TO_DEG;
			float x0 = Mth.sin(a0) * R;
			float z0 = Mth.cos(a0) * R;
			float x1 = Mth.sin(a1) * R;
			float z1 = Mth.cos(a1) * R;
			float nx = -Mth.sin((a0 + a1) * 0.5f);
			float nz = -Mth.cos((a0 + a1) * 0.5f);
			for (float y = door ? HH : 0.0f; y < WALL_H - 1.0e-3f; y = next(y, WALL_H)) {
				float yb = next(y, WALL_H);
				TextureAtlasSprite s = yb > WALL_H - 1.0f ? chis : y < 0.5f ? cut : shelf;
				m.wall(x0, z0, x1, z1, y, yb, s, nx, nz);
			}
			// the gold ledges between the tiers
			float ri = R - LEDGE_OUT;
			float ix0 = Mth.sin(a0) * ri;
			float iz0 = Mth.cos(a0) * ri;
			float ix1 = Mth.sin(a1) * ri;
			float iz1 = Mth.cos(a1) * ri;
			for (float ly : LEDGES) {
				if (door && ly < HH + 0.5f) {
					continue;
				}
				float top = ly + 0.22f;
				float bot = ly - 0.18f;
				m.quad(x0, top, z0, x1, top, z1, ix1, top, iz1, ix0, top, iz0, gold, 0.0f, 0.0f, 1.0f, LEDGE_OUT, 0, 1, 0);
				m.quad(ix0, bot, iz0, ix1, bot, iz1, ix1, top, iz1, ix0, top, iz0, gold, 0.0f, 0.6f, 1.0f, 1.0f, nx, 0, nz);
				m.quad(ix0, bot, iz0, ix1, bot, iz1, x1, bot, z1, x0, bot, z0, spruce, 0.0f, 0.0f, 1.0f, LEDGE_OUT, 0, -1, 0);
			}
		}
		// the gold lintel over the doorway, inside the library
		m.box(0.0f, HH, DOOR_Z - 0.2f, HW + 0.5f, 0.6f, 0.25f, 0.0f, gold, gold, gold, gold);
		m.box(HW + 0.25f, 0.0f, DOOR_Z - 0.2f, 0.3f, HH, 0.3f, 0.0f, gold, gold, gold, gold);
		m.box(-HW - 0.25f, 0.0f, DOOR_Z - 0.2f, 0.3f, HH, 0.3f, 0.0f, gold, gold, gold, gold);

		// the screen: a sandstone plinth and a gold frame standing out from the far wall
		float half = screenW * 0.5f;
		m.box(0.0f, 0.0f, SCREEN_Z + PLINTH_D * 0.5f - 0.4f, half + 0.8f, SCREEN_Y0 - BEZEL, PLINTH_D * 0.5f + 0.4f, 0.0f, cut, chis, chis, cut);
		float ox = half + BEZEL;
		float bz0 = SCREEN_Z - 0.4f;
		float bz1 = SCREEN_Z + 0.25f;
		m.slab(-ox, SCREEN_Y0 - BEZEL, bz0, ox, SCREEN_Y0, bz1, gold);
		m.slab(-ox, SCREEN_Y0 + SCREEN_H, bz0, ox, SCREEN_Y0 + SCREEN_H + BEZEL, bz1, gold);
		m.slab(-ox, SCREEN_Y0, bz0, -half, SCREEN_Y0 + SCREEN_H, bz1, gold);
		m.slab(half, SCREEN_Y0, bz0, ox, SCREEN_Y0 + SCREEN_H, bz1, gold);
		m.planeZ(SCREEN_Z - 0.05f, -half, half, SCREEN_Y0, SCREEN_Y0 + SCREEN_H, 1, black);
		// a crest over the frame: a gold block with lapis either side
		m.box(0.0f, SCREEN_Y0 + SCREEN_H + BEZEL, SCREEN_Z - 0.1f, 1.2f, 1.4f, 0.3f, 0.0f, gold, gold, gold, gold);

		// the Eye of Nabu: a low gold-rimmed dais at the heart
		for (int i = 0; i < 24; i++) {
			float a0 = i * Mth.TWO_PI / 24;
			float a1 = (i + 1) * Mth.TWO_PI / 24;
			float h = 0.14f;
			m.quad(Mth.sin(a0) * EYE_R, h, Mth.cos(a0) * EYE_R, Mth.sin(a1) * EYE_R, h, Mth.cos(a1) * EYE_R, 0.0f, h, 0.0f, 0.0f, h, 0.0f, lapis,
					0.0f, 0.0f, 1.0f, 1.0f, 0, 1, 0);
			m.quad(Mth.sin(a0) * EYE_R, 0.0f, Mth.cos(a0) * EYE_R, Mth.sin(a1) * EYE_R, 0.0f, Mth.cos(a1) * EYE_R, Mth.sin(a1) * EYE_R, h,
					Mth.cos(a1) * EYE_R, Mth.sin(a0) * EYE_R, h, Mth.cos(a0) * EYE_R, gold, 0.0f, 0.85f, 1.0f, 1.0f, Mth.sin((a0 + a1) * 0.5f), 0,
					Mth.cos((a0 + a1) * 0.5f));
		}

		// the lecterns, each facing the heart
		for (int i = 0; i < LECTERNS; i++) {
			float lx = lecternX(i);
			float lz = lecternZ(i);
			float yaw = (float) Math.atan2(-lx, -lz);
			m.box(lx, 0.0f, lz, 0.5f, 0.125f, 0.5f, yaw, lecBase, lecBase, lecBase, lecBase);
			m.box(lx, 0.125f, lz, 0.25f, 0.8f, 0.25f, yaw, lecFront, lecFront, lecFront, lecFront);
			m.box(lx, 0.925f, lz, 0.5f, 0.15f, 0.42f, yaw, lecTop, lecSide, lecSide, lecSide);
		}

		// ---- the hallway
		m.planeY(0.0f, -HW, HW, DOOR_Z, HALL_END, 1, cut);
		m.planeY(HH, -HW, HW, DOOR_Z, HALL_END, -1, floor);
		m.planeX(-HW, DOOR_Z, HALL_END, 0.0f, HH, 1, sand);
		m.planeX(HW, DOOR_Z, HALL_END, 0.0f, HH, -1, sand);
		m.planeZ(HALL_END, -HW, HW, 0.0f, HH, -1, cut);
		for (float z = DOOR_Z + 3.0f; z < HALL_END - 1.0f; z += PILLAR_STEP) {
			for (int side = -1; side <= 1; side += 2) {
				m.box(side * (HW - 0.25f), 0.0f, z, 0.25f, HH, 0.5f, 0.0f, chis, chis, chis, chis);
			}
			m.box(0.0f, HH - 0.4f, z, HW, 0.4f, 0.5f, 0.0f, chis, chis, chis, chis);
		}
		return m;
	}

	/** Emits every quad with this frame's light and fog, camera-relative. */
	void emit(BufferBuilder bb, double cx, double cy, double cz, float lampLevel, float screen, float rage) {
		float[] a = v;
		for (int i = 0; i < n; i++) {
			int o = i * STRIDE;
			double dx = a[o] - cx;
			double dy = a[o + 1] - cy;
			double dz = a[o + 2] - cz;
			float f = fog(Math.sqrt(dx * dx + dy * dy + dz * dz));
			float lamp = a[o + 5] * lampLevel;
			float scr = a[o + 6] * screen;
			// lamps: warm gold (redder as Nabu's anger rises); the screen: a cool white
			float r = (AMBIENT * 1.1f + lamp * 1.0f + scr * 0.72f) * f;
			float g = (AMBIENT + lamp * (0.80f - 0.35f * rage) + scr * 0.82f) * f;
			float b = (AMBIENT * 1.5f + lamp * (0.50f - 0.3f * rage) + scr * 0.95f) * f;
			bb.addVertex((float) dx, (float) dy, (float) dz).setUv(a[o + 3], a[o + 4]).setColor(c(r), c(g), c(b), 255);
		}
	}

	static float fog(double d) {
		double k = d / 34.0;
		return (float) Math.max(Math.exp(-k * k), 0.22 * Math.exp(-d / 30.0));
	}

	private static int c(float x) {
		return Math.round(Mth.clamp(x, 0.0f, 1.0f) * 255.0f);
	}

	// ---------------------------------------------------------------- building

	private static float next(float a, float end) {
		float nx = (float) Math.floor(a + 1.0e-4f) + 1.0f;
		return Math.min(nx, end);
	}

	/** One cell of the round wall: from (x0, z0) to (x1, z1), y0 to y1, facing (nx, nz). */
	private void wall(float x0, float z0, float x1, float z1, float y0, float y1, TextureAtlasSprite s, float nx, float nz) {
		float fy = (float) Math.floor(y0 + 1.0e-4f);
		float vTop = 1.0f - (y1 - fy);
		float vBot = 1.0f - (y0 - fy);
		quad(x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, s, 0.0f, vBot, 1.0f, vTop, nx, 0, nz);
	}

	/**
	 * A quad a-b-c-d (a bottom-left, b bottom-right, c top-right, d top-left as seen from its front), the sprite mapped u0..u1 along
	 * a-b and v0..v1 along a-d.
	 */
	void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			TextureAtlasSprite s, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
		add(ax, ay, az, s, u0, v0, nx, ny, nz);
		add(bx, by, bz, s, u1, v0, nx, ny, nz);
		add(cx, cy, cz, s, u1, v1, nx, ny, nz);
		add(dx, dy, dz, s, u0, v1, nx, ny, nz);
	}

	/** A floor / ceiling at {@code y}, one quad per block cell, facing up (+1) or down (-1). */
	void planeY(float y, float x0, float x1, float z0, float z1, int ny, TextureAtlasSprite s) {
		for (float x = x0; x < x1 - 1.0e-4f; x = next(x, x1)) {
			float xb = next(x, x1);
			for (float z = z0; z < z1 - 1.0e-4f; z = next(z, z1)) {
				float zb = next(z, z1);
				float fx = (float) Math.floor(x + 1.0e-4f);
				float fz = (float) Math.floor(z + 1.0e-4f);
				quad(x, y, z, xb, y, z, xb, y, zb, x, y, zb, s, x - fx, z - fz, xb - fx, zb - fz, 0, ny, 0);
			}
		}
	}

	/** A wall at {@code x}, facing +X (+1) or -X (-1). */
	void planeX(float x, float z0, float z1, float y0, float y1, int nx, TextureAtlasSprite s) {
		for (float z = z0; z < z1 - 1.0e-4f; z = next(z, z1)) {
			float zb = next(z, z1);
			for (float y = y0; y < y1 - 1.0e-4f; y = next(y, y1)) {
				float yb = next(y, y1);
				float fz = (float) Math.floor(z + 1.0e-4f);
				float fy = (float) Math.floor(y + 1.0e-4f);
				quad(x, y, z, x, y, zb, x, yb, zb, x, yb, z, s, z - fz, 1.0f - (y - fy), zb - fz, 1.0f - (yb - fy), nx, 0, 0);
			}
		}
	}

	/** A wall at {@code z}, facing +Z (+1) or -Z (-1). */
	void planeZ(float z, float x0, float x1, float y0, float y1, int nz, TextureAtlasSprite s) {
		for (float x = x0; x < x1 - 1.0e-4f; x = next(x, x1)) {
			float xb = next(x, x1);
			for (float y = y0; y < y1 - 1.0e-4f; y = next(y, y1)) {
				float yb = next(y, y1);
				float fx = (float) Math.floor(x + 1.0e-4f);
				float fy = (float) Math.floor(y + 1.0e-4f);
				quad(x, y, z, xb, y, z, xb, yb, z, x, yb, z, s, x - fx, 1.0f - (y - fy), xb - fx, 1.0f - (yb - fy), 0, 0, nz);
			}
		}
	}

	/** An axis-aligned slab from (x0, y0, z0) to (x1, y1, z1), every face tiled per block. */
	void slab(float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite s) {
		planeZ(z1, x0, x1, y0, y1, 1, s);
		planeZ(z0, x0, x1, y0, y1, -1, s);
		planeX(x0, z0, z1, y0, y1, -1, s);
		planeX(x1, z0, z1, y0, y1, 1, s);
		planeY(y1, x0, x1, z0, z1, 1, s);
		planeY(y0, x0, x1, z0, z1, -1, s);
	}

	/**
	 * A box standing on {@code y0}, centred on (cx, cz), half-sizes hx / hz, {@code h} tall, turned {@code yaw} radians about Y (its
	 * local +Z is the front). One sprite stretched over each face.
	 */
	void box(float cx, float y0, float cz, float hx, float h, float hz, float yaw, TextureAtlasSprite top, TextureAtlasSprite side,
			TextureAtlasSprite front, TextureAtlasSprite bottom) {
		float cs = Mth.cos(yaw);
		float sn = Mth.sin(yaw);
		float y1 = y0 + h;
		float[][] c = new float[4][];
		float[][] local = { { -hx, -hz }, { hx, -hz }, { hx, hz }, { -hx, hz } };
		for (int i = 0; i < 4; i++) {
			float lx = local[i][0];
			float lz = local[i][1];
			c[i] = new float[] { cx + lx * cs + lz * sn, cz - lx * sn + lz * cs };
		}
		// the four sides: 0-1 back (-Z local), 1-2 right, 2-3 front (+Z local), 3-0 left
		for (int i = 0; i < 4; i++) {
			float[] p = c[i];
			float[] q = c[(i + 1) % 4];
			float ex = q[0] - p[0];
			float ez = q[1] - p[1];
			float len = Mth.sqrt(ex * ex + ez * ez) + 1.0e-6f;
			float nx = ez / len;
			float nz = -ex / len;
			TextureAtlasSprite s = i == 2 ? front : side;
			quad(p[0], y0, p[1], q[0], y0, q[1], q[0], y1, q[1], p[0], y1, p[1], s, 0.0f, 1.0f, 1.0f, 0.0f, nx, 0, nz);
		}
		quad(c[0][0], y1, c[0][1], c[1][0], y1, c[1][1], c[2][0], y1, c[2][1], c[3][0], y1, c[3][1], top, 0.0f, 0.0f, 1.0f, 1.0f, 0, 1, 0);
		quad(c[0][0], y0, c[0][1], c[1][0], y0, c[1][1], c[2][0], y0, c[2][1], c[3][0], y0, c[3][1], bottom, 0.0f, 0.0f, 1.0f, 1.0f, 0, -1, 0);
	}

	private void add(float x, float y, float z, TextureAtlasSprite s, float u, float vv, float nx, float ny, float nz) {
		if ((n + 1) * STRIDE > v.length) {
			v = java.util.Arrays.copyOf(v, v.length * 2);
		}
		int o = n * STRIDE;
		v[o] = x;
		v[o + 1] = y;
		v[o + 2] = z;
		v[o + 3] = s.getU0() + (s.getU1() - s.getU0()) * Mth.clamp(u, 0.0f, 1.0f);
		v[o + 4] = s.getV0() + (s.getV1() - s.getV0()) * Mth.clamp(vv, 0.0f, 1.0f);
		float lamp = 0.0f;
		for (float[] l : lamps) {
			float dx = l[0] - x;
			float dy = l[1] - y;
			float dz = l[2] - z;
			float d2 = dx * dx + dy * dy + dz * dz;
			float d = Mth.sqrt(d2) + 1.0e-3f;
			float facing = Math.max(0.0f, (dx * nx + dy * ny + dz * nz) / d) * 0.7f + 0.3f;
			lamp += l[3] * facing / (1.0f + d2 / l[4]);
		}
		v[o + 5] = Math.min(1.4f, lamp);
		v[o + 6] = screenShare(x, y, z, nx, ny, nz);
		n++;
	}

	/** How much of the screen's light reaches this point. */
	private float screenShare(float x, float y, float z, float nx, float ny, float nz) {
		float half = screenW * 0.5f;
		if (z < SCREEN_Z - 0.01f) {
			return 0.0f;
		}
		float qx = Mth.clamp(x, -half, half);
		float qy = Mth.clamp(y, SCREEN_Y0, SCREEN_Y0 + SCREEN_H);
		float dx = qx - x;
		float dy = qy - y;
		float dz = SCREEN_Z - z;
		float d2 = dx * dx + dy * dy + dz * dz;
		float d = Mth.sqrt(d2) + 1.0e-3f;
		float out = Math.max(0.0f, (z - SCREEN_Z) / d) * 0.8f + 0.2f;
		float facing = Math.max(0.0f, (dx * nx + dy * ny + dz * nz) / d) * 0.8f + 0.2f;
		return 1.6f * out * facing / (1.0f + d2 / 60.0f);
	}
}
