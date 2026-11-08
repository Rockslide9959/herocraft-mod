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
 * little scatter so a regrowing part fills in like a wave of pixels rather than a ruler-straight wipe. {@link Texel#grow} is
 * the organic order the "Build in place" style uses: a few growth seeds (the first at the joint, the rest kicking in later
 * the further they sit from it) flood out over the surface through seeded noise at their own speeds, so the skin fills in
 * irregular patches that spread and merge, broadly from the joint outward, at a steady rate of pixels.
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
		/** The organic, patchy order (0..1) of "Build in place" (and the skin closing over in "Bone and flesh"). */
		public float grow;
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
		growOrder(out, part, slim, h);
		return out;
	}

	/**
	 * Fills {@link Texel#grow}: arrival times of several growth fronts flooding over the base layer's surface graph (edge cost
	 * scaled by smooth 3D value noise, so the fronts are ragged), blended a little with the joint-outward order, then ranked so
	 * pixels appear at an even rate. Each overlay texel follows the base texel under it a moment later.
	 */
	private static void growOrder(List<Texel> all, int part, boolean slim, float length) {
		int n = all.size() / 2; // addCube adds the base layer, then the overlay in the very same order
		List<Texel> base = all.subList(0, n);
		java.util.Random rnd = new java.util.Random(9001L + part * 17L + (slim ? 3 : 0));
		long noiseSeed = rnd.nextLong();
		// the surface graph: 4-neighbours on a face and the ones across an edge (0.71 px apart)
		int[][] nbr = new int[n][];
		float[][] cost = new float[n][];
		for (int i = 0; i < n; i++) {
			Texel a = base.get(i);
			int[] tmpI = new int[8];
			float[] tmpC = new float[8];
			int k = 0;
			for (int j = 0; j < n && k < 8; j++) {
				if (j == i) {
					continue;
				}
				Texel b = base.get(j);
				float dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
				float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (d < 1.05f) {
					float mx = (a.x + b.x) * 0.5f, my = (a.y + b.y) * 0.5f, mz = (a.z + b.z) * 0.5f;
					tmpI[k] = j;
					tmpC[k] = d * (0.35f + 1.3f * noise(mx / 2.6f, my / 2.6f, mz / 2.6f, noiseSeed));
					k++;
				}
			}
			nbr[i] = java.util.Arrays.copyOf(tmpI, k);
			cost[i] = java.util.Arrays.copyOf(tmpC, k);
		}
		float[] arrival = new float[n];
		java.util.Arrays.fill(arrival, Float.MAX_VALUE);
		// texels by their joint-outward order
		Integer[] byOrder = new Integer[n];
		for (int i = 0; i < n; i++) {
			byOrder[i] = i;
		}
		java.util.Arrays.sort(byOrder, (x, y) -> Float.compare(base.get(x).order, base.get(y).order));
		int seeds = part == AndyState.HEAD || part == AndyState.BODY ? 7 : 5;
		for (int s = 0; s < seeds; s++) {
			int at;
			float start;
			float speed;
			if (s < 2) {
				// at the joint, from the start
				at = byOrder[rnd.nextInt(Math.max(1, n / 10))];
				start = 0f;
				speed = 0.9f + rnd.nextFloat() * 0.3f;
			} else {
				// further out, kicking in a while before the joint fronts get there: islands that spread and join up
				at = byOrder[(int) (n * (0.2f + 0.75f * rnd.nextFloat()))];
				start = base.get(at).order * length * (0.3f + 0.4f * rnd.nextFloat());
				speed = 0.55f + rnd.nextFloat() * 0.9f;
			}
			float[] d = dijkstra(n, nbr, cost, at);
			for (int i = 0; i < n; i++) {
				if (d[i] < Float.MAX_VALUE) {
					arrival[i] = Math.min(arrival[i], start + d[i] / speed);
				}
			}
		}
		float max = 0f;
		for (int i = 0; i < n; i++) {
			if (arrival[i] == Float.MAX_VALUE) {
				arrival[i] = base.get(i).order * length * 2f;
			}
			max = Math.max(max, arrival[i]);
		}
		float[] key = new float[n];
		for (int i = 0; i < n; i++) {
			key[i] = 0.78f * (arrival[i] / Math.max(1e-3f, max)) + 0.22f * base.get(i).order;
		}
		Integer[] rank = new Integer[n];
		for (int i = 0; i < n; i++) {
			rank[i] = i;
		}
		java.util.Arrays.sort(rank, (x, y) -> Float.compare(key[x], key[y]));
		for (int r = 0; r < n; r++) {
			int i = rank[r];
			Texel b = base.get(i);
			b.grow = r / (float) Math.max(1, n - 1) * 0.92f;
			Texel o = all.get(n + i);
			o.grow = Math.min(1f, b.grow + 0.03f + o.seed * 0.05f);
		}
	}

	private static float[] dijkstra(int n, int[][] nbr, float[][] cost, int from) {
		float[] d = new float[n];
		java.util.Arrays.fill(d, Float.MAX_VALUE);
		d[from] = 0f;
		java.util.PriorityQueue<float[]> q = new java.util.PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
		q.add(new float[] { 0f, from });
		while (!q.isEmpty()) {
			float[] top = q.poll();
			int i = (int) top[1];
			if (top[0] > d[i]) {
				continue;
			}
			for (int k = 0; k < nbr[i].length; k++) {
				int j = nbr[i][k];
				float nd = d[i] + cost[i][k];
				if (nd < d[j]) {
					d[j] = nd;
					q.add(new float[] { nd, j });
				}
			}
		}
		return d;
	}

	/** Smooth 3D value noise in 0..1 (trilinear over a hashed lattice). */
	static float noise(float x, float y, float z, long seed) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
		float fx = x - x0, fy = y - y0, fz = z - z0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		float r = 0f;
		for (int i = 0; i < 8; i++) {
			int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
			float w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
			r += w * hash(x0 + dx, y0 + dy, z0 + dz, seed);
		}
		return r;
	}

	static float hash(int x, int y, int z, long seed) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= h >>> 33;
		return (h >>> 40) / (float) (1L << 24);
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
