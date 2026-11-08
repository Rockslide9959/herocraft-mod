package com.projecthero.mod.client.andy;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.andy.data.AndyState;

/**
 * v0.17.3: every skin texel of the six player parts as a little square in its part's own space -- the geometry Andy's
 * pixel bursts and his regrowth are made of. One entry per texel of both layers (the base and the overlay, which sits
 * 0.5 / 0.25 px proud as in vanilla), with the same face / UV mapping {@link com.projecthero.mod.client.render.OverlayStrips}
 * uses: positions in model pixels relative to the part's pivot (vanilla model space: +y down, -z the front).
 *
 * <p>{@link Texel#order} runs 0 at the part's joint (neck, shoulder, hip; the neck for the body) to 1 at its far end, with a
 * little scatter so a regrowing part fills in like a wave of pixels rather than a ruler-straight wipe.
 */
public final class AndyTexels {
	private AndyTexels() {
	}

	/** One texel: skin coords, the centre of its square, its outward normal, its two in-plane half-extents and its order. */
	public static final class Texel {
		public final int u;
		public final int v;
		public final float x;
		public final float y;
		public final float z;
		public final float nx;
		public final float ny;
		public final float nz;
		/** Half size along the square's two in-plane axes (model px) -- 0.5 for a base texel, a little more for the overlay. */
		public final float ha;
		public final float hb;
		public final int plane; // 0: the square is in YZ (normal x), 1: XZ (normal y), 2: XY (normal z)
		public final boolean overlay;
		public float order;
		/** A fixed random number per texel (scatter, spin axes). */
		public final float seed;

		Texel(int u, int v, float x, float y, float z, float nx, float ny, float nz, float ha, float hb, int plane, boolean overlay, float seed) {
			this.u = u;
			this.v = v;
			this.x = x;
			this.y = y;
			this.z = z;
			this.nx = nx;
			this.ny = ny;
			this.nz = nz;
			this.ha = ha;
			this.hb = hb;
			this.plane = plane;
			this.overlay = overlay;
			this.seed = seed;
		}
	}

	private static final List<Texel>[][] CACHE = newCache();

	@SuppressWarnings("unchecked")
	private static List<Texel>[][] newCache() {
		return new List[2][AndyState.PARTS];
	}

	/** The texels of {@code part} ({@link AndyState#HEAD}..{@link AndyState#LEFT_LEG}) for a wide or slim skin. */
	public static List<Texel> of(int part, boolean slim) {
		int s = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 1 : 0;
		List<Texel> l = CACHE[s][part];
		if (l == null) {
			l = build(part, slim);
			CACHE[s][part] = l;
		}
		return l;
	}

	/** Where the part joins the body, in its own space (model px): the regrowth streams out of here. */
	public static float[] joint(int part, boolean slim) {
		return switch (part) {
			case AndyState.HEAD -> new float[] { 0f, -0.5f, 0f };
			case AndyState.BODY -> new float[] { 0f, 0.5f, 0f };
			case AndyState.RIGHT_ARM -> new float[] { slim ? -0.5f : -1f, -1.5f, 0f };
			case AndyState.LEFT_ARM -> new float[] { slim ? 0.5f : 1f, -1.5f, 0f };
			default -> new float[] { 0f, 0.5f, 0f };
		};
	}

	/** The part's box in its own space: x0, y0, z0, w, h, d (model px). */
	public static float[] box(int part, boolean slim) {
		int aw = slim ? 3 : 4;
		return switch (part) {
			case AndyState.HEAD -> new float[] { -4f, -8f, -4f, 8, 8, 8 };
			case AndyState.BODY -> new float[] { -4f, 0f, -2f, 8, 12, 4 };
			case AndyState.RIGHT_ARM -> new float[] { slim ? -2f : -3f, -2f, -2f, aw, 12, 4 };
			case AndyState.LEFT_ARM -> new float[] { -1f, -2f, -2f, aw, 12, 4 };
			default -> new float[] { -2f, 0f, -2f, 4, 12, 4 };
		};
	}

	/** Base-layer UV origin, overlay-layer UV origin and overlay growth for a part. */
	private static int[] uv(int part) {
		return switch (part) {
			case AndyState.HEAD -> new int[] { 0, 0, 32, 0 };
			case AndyState.BODY -> new int[] { 16, 16, 16, 32 };
			case AndyState.RIGHT_ARM -> new int[] { 40, 16, 40, 32 };
			case AndyState.LEFT_ARM -> new int[] { 32, 48, 48, 48 };
			case AndyState.RIGHT_LEG -> new int[] { 0, 16, 0, 32 };
			default -> new int[] { 16, 48, 0, 48 };
		};
	}

	/** Which part a BASE-layer skin texel belongs to (-1 for none). */
	public static int partOfBase(int u, int v) {
		if (v < 16) {
			return u < 32 ? AndyState.HEAD : -1;
		}
		if (v < 32) {
			if (u < 16) {
				return AndyState.RIGHT_LEG;
			}
			if (u < 40) {
				return AndyState.BODY;
			}
			return u < 56 ? AndyState.RIGHT_ARM : -1;
		}
		if (v >= 48) {
			if (u >= 16 && u < 32) {
				return AndyState.LEFT_LEG;
			}
			if (u >= 32 && u < 48) {
				return AndyState.LEFT_ARM;
			}
		}
		return -1;
	}

	private static List<Texel> build(int part, boolean slim) {
		List<Texel> out = new ArrayList<>();
		float[] b = box(part, slim);
		int[] uv = uv(part);
		float grow = part == AndyState.HEAD ? 0.5f : 0.25f;
		java.util.Random rnd = new java.util.Random(1234L + part * 31L + (slim ? 7 : 0));
		addCube(out, uv[0], uv[1], b, 0f, false, rnd);
		addCube(out, uv[2], uv[3], b, grow, true, rnd);
		float y0 = b[1], h = b[4];
		for (Texel t : out) {
			float o;
			switch (part) {
				case AndyState.HEAD -> o = (0f - t.y) / 8f; // the neck up
				case AndyState.RIGHT_ARM, AndyState.LEFT_ARM -> o = (t.y - y0) / h; // the shoulder down to the hand
				default -> o = t.y / h; // the neck / hip down
			}
			o = Math.max(0f, Math.min(1f, o));
			// a little scatter, and the overlay just behind the skin under it
			t.order = Math.max(0f, Math.min(1f, o * 0.92f + t.seed * 0.06f + (t.overlay ? 0.02f : 0f)));
		}
		return out;
	}

	private static void addCube(List<Texel> out, int u, int v, float[] b, float grow, boolean overlay, java.util.Random rnd) {
		int w = (int) b[3], h = (int) b[4], d = (int) b[5];
		float minX = b[0] - grow, maxX = b[0] + w + grow;
		float minY = b[1] - grow, maxY = b[1] + h + grow;
		float minZ = b[2] - grow, maxZ = b[2] + d + grow;
		float sx = (maxX - minX) / w, sy = (maxY - minY) / h, sz = (maxZ - minZ) / d;
		// top (y = minY): texels (u+d+tx, v+ty)
		for (int ty = 0; ty < d; ty++) {
			for (int tx = 0; tx < w; tx++) {
				out.add(new Texel(u + d + tx, v + ty, minX + (tx + 0.5f) * sx, minY, maxZ - (ty + 0.5f) * sz, 0, -1, 0, sx * 0.5f, sz * 0.5f, 1, overlay, rnd.nextFloat()));
			}
		}
		// bottom (y = maxY): texels (u+d+w+tx, v+ty)
		for (int ty = 0; ty < d; ty++) {
			for (int tx = 0; tx < w; tx++) {
				out.add(new Texel(u + d + w + tx, v + ty, minX + (tx + 0.5f) * sx, maxY, maxZ - (ty + 0.5f) * sz, 0, 1, 0, sx * 0.5f, sz * 0.5f, 1, overlay, rnd.nextFloat()));
			}
		}
		for (int row = 0; row < h; row++) {
			float y = minY + (row + 0.5f) * sy;
			int tv = v + d + row;
			for (int k = 0; k < d; k++) { // west (-x)
				out.add(new Texel(u + k, tv, minX, y, maxZ - (k + 0.5f) * sz, -1, 0, 0, sz * 0.5f, sy * 0.5f, 0, overlay, rnd.nextFloat()));
			}
			for (int k = 0; k < w; k++) { // north (front, -z)
				out.add(new Texel(u + d + k, tv, minX + (k + 0.5f) * sx, y, minZ, 0, 0, -1, sx * 0.5f, sy * 0.5f, 2, overlay, rnd.nextFloat()));
			}
			for (int k = 0; k < d; k++) { // east (+x)
				out.add(new Texel(u + d + w + k, tv, maxX, y, minZ + (k + 0.5f) * sz, 1, 0, 0, sz * 0.5f, sy * 0.5f, 0, overlay, rnd.nextFloat()));
			}
			for (int k = 0; k < w; k++) { // south (back, +z)
				out.add(new Texel(u + 2 * d + w + k, tv, maxX - (k + 0.5f) * sx, y, maxZ, 0, 0, 1, sx * 0.5f, sy * 0.5f, 2, overlay, rnd.nextFloat()));
			}
		}
	}
}
