package com.projecthero.mod.client.saiyan.hair;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

/**
 * v0.17.3 (PREVIEW, not shipped): Goku's Super Saiyan hair as real curved geometry, built once into a list of quads.
 *
 * <p>Every lock is a swept solid: a flattened six-sided ring (broad one way, thin the other) carried along a cubic bezier
 * spine, swelling a little past the root and narrowing to a sharp tip. The colours are baked anime cel shading in head
 * space: each face is either lit gold or shadow amber depending on which way it faces a fixed key light, the roots darken
 * to a deep amber, and a pale cream highlight runs along the lit ridge of each spike. A low scalp shell over the top, back
 * and sides hides the wearer's own head hair and gives the spikes something to grow out of.
 *
 * <p>Layout frame (pixels): x = the wearer's LEFT, y = up, z = forward (the face). The head cube is x,z in [-4, 4] and
 * y in [0, 8]; {@link #toModel} turns that into the head ModelPart's space.
 */
public final class SsjHairModel {
	/** One quad: four corners (model-space pixels) and a colour per corner. */
	public record Quad(float[] xyz, int[] rgb) {
	}

	// ----------------------------------------------------------------- palette
	static final int GOLD = 0xFFDA45; // lit
	static final int GOLD_TIP = 0xFFEC8A; // lit, toward the tip
	static final int AMBER = 0xE69E26; // shadow
	static final int AMBER_TIP = 0xF2BC40;
	static final int ROOT = 0xAE6E16; // deep crease at the roots
	static final int CREAM = 0xFFF7D2; // highlight ridge

	/** Key light (layout frame): above, in front, a little from the wearer's right (the viewer's left). */
	static final Vector3f KEY = new Vector3f(-0.35f, 1.0f, 0.6f).normalize();

	private static final int SIDES = 6;
	private static final int SEGMENTS = 12;

	/**
	 * A lock: bezier spine p0..p3, root half-width / half-thickness, and the direction its broad side should lie along
	 * (projected off the spine at every ring, so the spike never twists). {@code belly} swells the middle; {@code shade}
	 * scales how dark the root gets (bangs stay brighter).
	 */
	record Lock(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float width, float thick, Vector3f broad, float belly,
			float shade) {
	}

	private static Vector3f v(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	private static final Vector3f X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1);

	private static final List<Lock> LOCKS = new ArrayList<>();

	private static void lock(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float w, float d, Vector3f broad) {
		LOCKS.add(new Lock(p0, p1, p2, p3, w, d, broad, 0.2f, 1f));
	}

	private static void bang(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float w, float d) {
		LOCKS.add(new Lock(p0, p1, p2, p3, w, d, X, 0.12f, 0.45f));
	}

	/** v2: a big flame lock -- a fat leaf, narrower at the root, widest about 40% along, then the sharp tip. */
	private static void flame(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float w, float d, Vector3f broad) {
		LOCKS.add(new Lock(p0, p1, p2, p3, w, d, broad, FLAME_BELLY, 1f));
	}

	private static void flames(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float w, float d, Vector3f broad) {
		flame(p0, p1, p2, p3, w, d, broad);
		// the mirror isn't exact: drawn hair is never symmetric, and a perfect mirror reads as a lotus / pinecone. Alternate
		// pairs nudge the mirrored lock's tip a little higher or lower and longer or shorter.
		float s = (pairs++ % 2 == 0) ? 1f : -1f;
		Vector3f q3 = mx(p3).add(0, 0.7f * s, -0.4f * s), q2 = mx(p2).add(0, 0.35f * s, -0.2f * s);
		flame(mx(p0), mx(p1), q2, q3, w * (1f - 0.06f * s), d, mx(broad));
	}

	private static int pairs;

	/** Marks a flame lock (any belly at or above this uses the leaf profile in {@link #taper}). */
	private static final float FLAME_BELLY = 2.0f;

	/** A lock and its mirror across the face's centre line. */
	private static void pair(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float w, float d, Vector3f broad) {
		lock(p0, p1, p2, p3, w, d, broad);
		lock(mx(p0), mx(p1), mx(p2), mx(p3), w, d, mx(broad));
	}

	private static Vector3f mx(Vector3f p) {
		return v(-p.x, p.y, p.z);
	}

	static {
		// v2 (user's turnaround sheets): the whole mass is a flame blown up and back. The front and crown locks rise UP and
		// sweep BACK; the back and nape locks leave the back of the head and curl UP and FORWARD to meet them, so the outline
		// is a teardrop peaking above and behind the crown (PEAK). Every big lock is a fat leaf: widest about 40% along.
		// --- the crown: tallest in the middle, fanning out and shorter to the sides
		flame(v(0, 7.4f, 0.4f), v(0, 13f, 0.4f), v(0, 17.2f, -2.0f), v(0, 19.8f, -6.0f), 2.3f, 1.7f, X);
		flames(v(2.2f, 7.4f, 0.5f), v(2.9f, 12.4f, 0.5f), v(4.1f, 15.8f, -1.6f), v(5.2f, 18.0f, -4.6f), 2.1f, 1.5f, X);
		flames(v(3.5f, 7, 0.8f), v(4.9f, 10.8f, 0.8f), v(6.6f, 13.2f, -0.6f), v(8.2f, 14.6f, -3.0f), 2.0f, 1.35f, X);
		// temples: flaring out sideways and up
		flames(v(3.8f, 5.9f, 2), v(5.5f, 7.0f, 2.2f), v(7.4f, 8.0f, 1.6f), v(9.2f, 10.2f, 0.4f), 1.7f, 1.1f, Y);
		// behind the temples: out, up and back
		flames(v(3.9f, 6.2f, -0.8f), v(5.8f, 8.0f, -1.4f), v(7.6f, 10.2f, -3.0f), v(8.6f, 12.8f, -5.0f), 1.8f, 1.25f, Y);
		// --- the front row: rising straight up from the hairline behind the fringe
		// (their bases come forward over the fringe's roots, so the bangs seem to fall from under them)
		lock(v(-0.4f, 7.7f, 4.0f), v(-0.4f, 10.4f, 5.0f), v(-0.2f, 12.4f, 2.8f), v(0.2f, 13.6f, 0.4f), 2.5f, 1.45f, X);
		pair(v(2.5f, 7.7f, 3.8f), v(2.8f, 10.2f, 4.7f), v(3.5f, 11.6f, 2.6f), v(4.5f, 12.5f, 0.4f), 2.4f, 1.35f, X);
		// --- behind the tallest, filling the crown toward the peak
		flames(v(1.3f, 7.7f, -1.2f), v(1.6f, 12.4f, -2.6f), v(2.3f, 15.8f, -5.4f), v(2.8f, 17.8f, -8.4f), 2.0f, 1.45f, X);
		// --- the back, upper row: back off the top of the back of the head, then UP, tips leaning forward toward the peak
		flame(v(0, 7.0f, -2.8f), v(0, 10.0f, -5.4f), v(0, 12.6f, -8.4f), v(0, 16.2f, -10.0f), 2.4f, 1.7f, X);
		flames(v(2.7f, 6.8f, -2.8f), v(4.0f, 9.2f, -5.2f), v(5.6f, 11.4f, -7.6f), v(6.8f, 14.2f, -9.0f), 2.2f, 1.55f, v(1, -0.5f, 0));
		// --- the back, middle row: further back, then up
		flame(v(0, 5.0f, -3.8f), v(0, 6.0f, -6.8f), v(0, 8.0f, -9.8f), v(0, 11.4f, -11.6f), 2.3f, 1.6f, X);
		flames(v(2.8f, 5.0f, -3.8f), v(4.0f, 6.2f, -6.2f), v(5.6f, 8.6f, -8.0f), v(7.0f, 11.6f, -9.0f), 2.1f, 1.5f, v(1, -0.6f, 0));
		flames(v(4.0f, 5.4f, -2.6f), v(6.2f, 6.4f, -3.8f), v(8.4f, 7.4f, -4.8f), v(10.4f, 8.8f, -5.4f), 1.8f, 1.2f, v(0.3f, -1, 0));
		// --- the nape: back and a little down, the tips curling up
		flame(v(0, 3.4f, -3.8f), v(0, 2.6f, -5.8f), v(0, 1.6f, -7.2f), v(0, 0.4f, -8.6f), 1.7f, 1.2f, X);
		flames(v(2.6f, 3.0f, -3.8f), v(3.6f, 2.2f, -5.4f), v(4.5f, 1.4f, -6.6f), v(5.4f, 0.8f, -7.8f), 1.6f, 1.1f, v(1, 0.6f, 0));
		flames(v(3.6f, 3.2f, -2.8f), v(4.4f, 2.3f, -4.0f), v(4.9f, 1.9f, -5.0f), v(5.2f, 1.3f, -6.0f), 1.2f, 0.8f, v(0.3f, -1, 0));
		// sideburns: flat locks down in front of the ears
		pair(v(4.1f, 6.8f, 2.4f), v(4.5f, 5.8f, 2.9f), v(4.6f, 4.7f, 3.0f), v(4.45f, 3.4f, 2.6f), 1.35f, 0.6f, Z);
		// --- the fringe: thick clumped locks hooking down over the forehead, the longest front-left (the viewer's left)
		// (they leave the hairline with only a small lift forward -- a high arch reads as a helmet brim)
		bang(v(-1.6f, 9.0f, 4.4f), v(-1.8f, 8.0f, 5.6f), v(-1.4f, 6.4f, 5.6f), v(-0.3f, 4.0f, 4.85f), 2.0f, 1.3f);
		bang(v(1.3f, 9.1f, 4.4f), v(1.4f, 8.1f, 5.5f), v(1.1f, 6.7f, 5.4f), v(0.2f, 5.3f, 4.8f), 1.85f, 1.2f);
		bang(v(-3.5f, 8.8f, 4.1f), v(-3.9f, 7.9f, 5.2f), v(-3.6f, 6.6f, 5.15f), v(-2.3f, 5.5f, 4.7f), 1.65f, 1.1f);
		bang(v(3.4f, 8.85f, 4.1f), v(3.8f, 7.9f, 5.1f), v(3.6f, 6.7f, 5.05f), v(2.4f, 5.8f, 4.7f), 1.6f, 1.05f);
	}

	private static List<Quad> mesh, outline;

	public static List<Quad> mesh() {
		if (mesh == null) {
			List<Quad> out = new ArrayList<>(), line = new ArrayList<>();
			shell(out);
			for (Lock l : LOCKS) {
				build(l, out, line);
			}
			outline = line;
			mesh = out;
		}
		return mesh;
	}

	/** The toon outline: each lock again, a touch fatter, wound inside out (drawn with back-face culling, so only its rim shows). */
	public static List<Quad> outline() {
		mesh();
		return outline;
	}

	static final int LINE = 0x7A480A;
	private static final float LINE_W = 0.16f;

	/** Layout frame -> head ModelPart pixels (model y points down, the face is -z). */
	static float[] toModel(Vector3f p) {
		return new float[] { p.x, -p.y, -p.z };
	}

	// ----------------------------------------------------------------- locks

	private static Vector3f bez(Lock l, float t) {
		float u = 1 - t;
		return new Vector3f(l.p0).mul(u * u * u).add(new Vector3f(l.p1).mul(3 * u * u * t)).add(new Vector3f(l.p2).mul(3 * u * t * t))
				.add(new Vector3f(l.p3).mul(t * t * t));
	}

	private static Vector3f tangent(Lock l, float t) {
		float u = 1 - t;
		Vector3f d = new Vector3f(l.p1).sub(l.p0).mul(3 * u * u).add(new Vector3f(l.p2).sub(l.p1).mul(6 * u * t))
				.add(new Vector3f(l.p3).sub(l.p2).mul(3 * t * t));
		return d.lengthSquared() < 1e-6f ? new Vector3f(0, 1, 0) : d.normalize();
	}

	/** Width along the lock: full at the root, a slight belly, then a sharp flame tip. */
	private static float taper(float t, float belly) {
		if (belly >= FLAME_BELLY) {
			// v2 leaf: ~68% at the root, full width about 40% along, then a sharp tip (normalised so the peak is 1)
			return (float) (Math.pow(1 - t, 1.15) * (0.62 + 2.0 * t) / 0.81);
		}
		return (float) (Math.pow(1 - t, 0.8) * (1 + belly * Math.sin(Math.PI * t)));
	}

	/** Ring angles: a flattened hexagon with its sharp edges on the broad axis. */
	private static final float[] ANG = { 0f, 58f, 122f, 180f, 238f, 302f };

	private static void build(Lock l, List<Quad> out, List<Quad> line) {
		Vector3f[][] rings = new Vector3f[SEGMENTS + 1][SIDES];
		Vector3f[][] hull = new Vector3f[SEGMENTS + 1][SIDES];
		Vector3f[] axis = new Vector3f[SEGMENTS + 1];
		float[] ts = new float[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			// more rings toward the tip, where the bend and the taper are tightest
			float t = (float) Math.pow(i / (float) SEGMENTS, 0.9);
			ts[i] = t;
			Vector3f c = bez(l, t);
			Vector3f tan = tangent(l, t);
			Vector3f b = new Vector3f(l.broad).sub(new Vector3f(tan).mul(l.broad.dot(tan)));
			if (b.lengthSquared() < 1e-4f) {
				b = new Vector3f(tan).cross(Z);
			}
			b.normalize();
			Vector3f n = new Vector3f(tan).cross(b).normalize();
			float k = taper(t, l.belly);
			float w = 0.04f + (l.width - 0.04f) * k, d = 0.03f + (l.thick - 0.03f) * k;
			axis[i] = c;
			for (int s = 0; s < SIDES; s++) {
				double a = Math.toRadians(ANG[s]);
				rings[i][s] = new Vector3f(c).add(new Vector3f(b).mul((float) Math.cos(a) * w)).add(new Vector3f(n).mul((float) Math.sin(a) * d));
				// the outline hull: a constant rim, carried a little past the tip so the point is outlined too
				Vector3f cc = i == SEGMENTS ? new Vector3f(c).add(new Vector3f(tan).mul(LINE_W)) : c;
				hull[i][s] = new Vector3f(cc).add(new Vector3f(b).mul((float) Math.cos(a) * (w + LINE_W)))
						.add(new Vector3f(n).mul((float) Math.sin(a) * (d + LINE_W)));
			}
		}
		// the hull, wound the other way round (its outward faces are culled, its inner ones show as a rim)
		for (int i = 0; i < SEGMENTS; i++) {
			for (int s = 0; s < SIDES; s++) {
				int s2 = (s + 1) % SIDES;
				line.add(quad(hull[i][s], hull[i + 1][s], hull[i + 1][s2], hull[i][s2], LINE, LINE, LINE, LINE));
			}
		}
		// the side faces, cel shaded per face
		float best = -9;
		int bestFace = 0;
		for (int i = 0; i < SEGMENTS; i++) {
			Vector3f mid = new Vector3f(axis[i]).add(axis[i + 1]).mul(0.5f);
			for (int s = 0; s < SIDES; s++) {
				int s2 = (s + 1) % SIDES;
				Vector3f a = rings[i][s], b = rings[i][s2], c = rings[i + 1][s2], d = rings[i + 1][s];
				Vector3f fc = new Vector3f(a).add(b).add(c).add(d).mul(0.25f);
				Vector3f nrm = fc.sub(mid).normalize();
				float lit = nrm.dot(KEY);
				boolean bright = lit > -0.05f;
				if (i == SEGMENTS / 3) {
					if (lit > best) {
						best = lit;
						bestFace = s;
					}
				}
				int ca = colour(bright, ts[i], l.shade), cb = colour(bright, ts[i + 1], l.shade);
				out.add(quad(a, b, c, d, ca, ca, cb, cb));
			}
		}
		// the highlight ridge: a thin cream band on the most lit face, hugging its upper edge, fading in and out
		int s = bestFace, s2 = (s + 1) % SIDES;
		// which edge of that face is the higher one (the ridge the light runs along)
		int edge = rings[SEGMENTS / 3][s].y >= rings[SEGMENTS / 3][s2].y ? s : s2;
		int other = edge == s ? s2 : s;
		for (int i = 0; i < SEGMENTS; i++) {
			float t0 = ts[i], t1 = ts[i + 1];
			float f0 = ridge(t0), f1 = ridge(t1);
			if (f0 <= 0 && f1 <= 0) {
				continue;
			}
			Vector3f mid0 = axis[i], mid1 = axis[i + 1];
			Vector3f e0 = lift(rings[i][edge], mid0), e1 = lift(rings[i + 1][edge], mid1);
			Vector3f o0 = lift(lerp(rings[i][edge], rings[i][other], f0), mid0), o1 = lift(lerp(rings[i + 1][edge], rings[i + 1][other], f1), mid1);
			out.add(quad(e0, o0, o1, e1, CREAM, mix(CREAM, GOLD, 0.5f), mix(CREAM, GOLD, 0.5f), CREAM));
		}
	}

	/** How far across its face the cream ridge reaches at t (0 = none). */
	private static float ridge(float t) {
		if (t < 0.12f || t > 0.82f) {
			return 0f;
		}
		return 0.42f * (float) Math.sin(Math.PI * (t - 0.12f) / 0.7f);
	}

	private static Vector3f lerp(Vector3f a, Vector3f b, float f) {
		return new Vector3f(a).lerp(b, f);
	}

	/** Nudged a hair off the surface so the ridge doesn't z-fight the face under it. */
	private static Vector3f lift(Vector3f p, Vector3f axis) {
		Vector3f d = new Vector3f(p).sub(axis);
		float len = d.length();
		return len < 1e-4f ? new Vector3f(p) : new Vector3f(p).add(d.mul(0.05f / len));
	}

	private static int colour(boolean lit, float t, float rootShade) {
		int base = lit ? mix(GOLD, GOLD_TIP, smooth(0.45f, 1f, t)) : mix(AMBER, AMBER_TIP, smooth(0.5f, 1f, t));
		float r = (1f - smooth(0f, 0.3f, t)) * rootShade;
		return mix(base, ROOT, r * (lit ? 0.75f : 1f));
	}

	private static float smooth(float a, float b, float t) {
		float x = Math.max(0f, Math.min(1f, (t - a) / (b - a)));
		return x * x * (3 - 2 * x);
	}

	static int mix(int a, int b, float f) {
		f = Math.max(0f, Math.min(1f, f));
		int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
		int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
		int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
		return (r << 16) | (g << 8) | bl;
	}

	private static Quad quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd) {
		float[] xyz = new float[12];
		Vector3f[] ps = { a, b, c, d };
		for (int k = 0; k < 4; k++) {
			float[] m = toModel(ps[k]);
			xyz[k * 3] = m[0];
			xyz[k * 3 + 1] = m[1];
			xyz[k * 3 + 2] = m[2];
		}
		return new Quad(xyz, new int[] { ca, cb, cc, cd });
	}

	// ----------------------------------------------------------------- scalp shell

	private static final float S = 4.3f, TOP = 8.3f;

	/** A cap over the top, back and sides: down to the temples at the front, to the nape at the back. */
	private static void shell(List<Quad> out) {
		int top = mix(GOLD, AMBER, 0.35f), side = mix(AMBER, ROOT, 0.25f), back = mix(AMBER, ROOT, 0.45f);
		// top
		out.add(quad(v(-S, TOP, S), v(S, TOP, S), v(S, TOP, -S), v(-S, TOP, -S), top, top, top, top));
		// back, down to the nape
		out.add(quad(v(-S, TOP, -S), v(S, TOP, -S), v(S, 0.6f, -S), v(-S, 0.6f, -S), back, back, ROOT, ROOT));
		// the hairline band across the top of the forehead
		out.add(quad(v(-S, TOP, S), v(-S, 7.3f, S), v(S, 7.3f, S), v(S, TOP, S), top, side, side, top));
		// sides (v2: down to ear level, so the skin's own painted hair doesn't show under the cap): the temple at the front,
		// the ear in the middle, the nape at the back
		for (int sx = -1; sx <= 1; sx += 2) {
			float x = S * sx;
			out.add(quad(v(x, TOP, S), v(x, TOP, 0), v(x, 2.6f, 0), v(x, 4.6f, S), side, side, ROOT, side));
			out.add(quad(v(x, TOP, 0), v(x, TOP, -S), v(x, 0.6f, -S), v(x, 2.6f, 0), side, side, ROOT, ROOT));
		}
	}

	private SsjHairModel() {
	}
}
