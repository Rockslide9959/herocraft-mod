package com.projecthero.mod.spider.web;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.1: the shape of one of Spider-Man's web structures -- a list of strand segments (a tightrope is one, a woven
 * web is its radial strands plus the spiral rings between them) and, for a Web Wall, one thin upright panel.
 *
 * <p>Pure geometry, identical on both sides (it is rebuilt from the same synced tag), and it never touches the
 * world's blocks: the collision it offers is a list of small boxes handed to the mover through
 * {@code EntityWebCollisionMixin}, and only to the people allowed to use the web (see {@link WebStructures#mayUse}).
 */
public final class WebGeometry {
	public static final byte TIGHTROPE = 0;
	public static final byte WALL = 1;
	public static final byte WEAVE = 2;

	/** Spacing of the collision boxes along a strand, and their half-width / depth below the strand. */
	public static final double STEP = 0.4;
	public static final double HALF = 0.22;
	public static final double BELOW = 0.22;
	/** How close (blocks) a body has to be to a strand to count as touching it. */
	public static final double TOUCH = 0.18;
	/** A web wall's half-thickness. */
	public static final double WALL_HALF_THICK = 0.12;

	public final byte kind;
	/**
	 * 6 doubles per segment: ax ay az bx by bz. v0.16.1: doubles, not floats -- at world coordinates in the millions a
	 * float is only good to a block or so, which put strands (and their footing / anchors) up to half a block off.
	 */
	public final double[] segs;
	/** Web Wall only: the solid panel box and which horizontal axis it faces (0 = its normal is X, 1 = Z). */
	public final AABB wallBox;
	public final int wallAxis;
	/** Weave only: the plane normal the rings were laid out in (rendering uses it). */
	public final Vec3 normal;
	public final AABB bounds;

	private WebGeometry(byte kind, double[] segs, AABB wallBox, int wallAxis, Vec3 normal) {
		this.kind = kind;
		this.segs = segs;
		this.wallBox = wallBox;
		this.wallAxis = wallAxis;
		this.normal = normal;
		AABB b = wallBox;
		for (int i = 0; i + 5 < segs.length; i += 6) {
			AABB s = new AABB(segs[i], segs[i + 1], segs[i + 2], segs[i + 3], segs[i + 4], segs[i + 5]);
			b = b == null ? s : b.minmax(s);
		}
		this.bounds = b == null ? new AABB(0, 0, 0, 0, 0, 0) : b.inflate(0.5);
	}

	public int segmentCount() {
		return segs.length / 6;
	}

	public Vec3 a(int i) {
		return new Vec3(segs[i * 6], segs[i * 6 + 1], segs[i * 6 + 2]);
	}

	public Vec3 b(int i) {
		return new Vec3(segs[i * 6 + 3], segs[i * 6 + 4], segs[i * 6 + 5]);
	}

	// ---------------- builders ----------------

	public static WebGeometry tightrope(Vec3 a, Vec3 b) {
		return new WebGeometry(TIGHTROPE, new double[] {a.x, a.y, a.z, b.x, b.y, b.z},
				null, 0, Vec3.ZERO);
	}

	/**
	 * A web wall: a 3 x 3 panel standing on {@code base} (its bottom centre), facing {@code axis}, with a few
	 * strands drawn over it for the look (they are part of the render and the touch test, the panel is the solid).
	 */
	public static WebGeometry wall(Vec3 base, int axis, double halfWidth, double height) {
		double t = WALL_HALF_THICK;
		AABB box = axis == 0
				? new AABB(base.x - t, base.y, base.z - halfWidth, base.x + t, base.y + height, base.z + halfWidth)
				: new AABB(base.x - halfWidth, base.y, base.z - t, base.x + halfWidth, base.y + height, base.z + t);
		return new WebGeometry(WALL, new double[0], box, axis, axis == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1));
	}

	/**
	 * A woven web: radial strands from {@code centre} to each of {@code ends}; once {@code complete}, a spiral thread is
	 * laid across them (consecutive radials, sorted by angle round {@code normal}, are joined at a radius that creeps
	 * out by {@code ringSpacing} every full turn), skipping any gap wider than 150 degrees so an open side stays open.
	 */
	public static WebGeometry weave(Vec3 centre, List<Vec3> ends, Vec3 normal, boolean complete, double ringSpacing) {
		List<double[]> out = new ArrayList<>();
		for (Vec3 e : ends) {
			out.add(seg(centre, e));
		}
		Vec3 n = normal.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : normal.normalize();
		if (complete && ends.size() >= 2) {
			Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
			Vec3 v = n.cross(u).normalize();
			int count = ends.size();
			double[] ang = new double[count];
			Vec3[] dir = new Vec3[count];
			double[] len = new double[count];
			Integer[] order = new Integer[count];
			for (int i = 0; i < count; i++) {
				Vec3 d = ends.get(i).subtract(centre);
				len[i] = d.length();
				dir[i] = len[i] < 1.0e-4 ? u : d.scale(1.0 / len[i]);
				ang[i] = Math.atan2(d.dot(v), d.dot(u));
				order[i] = i;
			}
			java.util.Arrays.sort(order, (p, q) -> Double.compare(ang[p], ang[q]));
			double start = 1.0;
			int turns = 64;
			for (int k = 0; k < turns; k++) {
				boolean any = false;
				for (int s = 0; s < count; s++) {
					int i = order[s];
					int j = order[(s + 1) % count];
					double gap = ang[j] - ang[i];
					if (s == count - 1) {
						gap += Math.PI * 2.0;
					}
					double q = k * count + s;
					double r1 = start + ringSpacing * q / count;
					double r2 = start + ringSpacing * (q + 1) / count;
					if (r1 > len[i] * 0.97 || r2 > len[j] * 0.97) {
						continue;
					}
					any = true;
					if (gap > Math.toRadians(150.0) || count < 3 && s == count - 1) {
						continue;
					}
					out.add(seg(centre.add(dir[i].scale(r1)), centre.add(dir[j].scale(r2))));
				}
				if (!any) {
					break;
				}
			}
		}
		double[] all = new double[out.size() * 6];
		for (int i = 0; i < out.size(); i++) {
			System.arraycopy(out.get(i), 0, all, i * 6, 6);
		}
		return new WebGeometry(WEAVE, all, null, 0, n);
	}

	private static double[] seg(Vec3 a, Vec3 b) {
		return new double[] {a.x, a.y, a.z, b.x, b.y, b.z};
	}

	// ---------------- (de)serialisation ----------------

	public CompoundTag encode() {
		CompoundTag tag = new CompoundTag();
		tag.putByte("k", kind);
		long[] bits = new long[segs.length];
		for (int i = 0; i < segs.length; i++) {
			bits[i] = Double.doubleToLongBits(segs[i]);
		}
		tag.putLongArray("d", bits);
		if (wallBox != null) {
			tag.putDouble("x0", wallBox.minX);
			tag.putDouble("y0", wallBox.minY);
			tag.putDouble("z0", wallBox.minZ);
			tag.putDouble("x1", wallBox.maxX);
			tag.putDouble("y1", wallBox.maxY);
			tag.putDouble("z1", wallBox.maxZ);
			tag.putInt("axis", wallAxis);
		}
		tag.putDouble("nx", normal.x);
		tag.putDouble("ny", normal.y);
		tag.putDouble("nz", normal.z);
		return tag;
	}

	/** Null for an empty / unreadable tag (a structure that has not been given its shape yet). */
	public static WebGeometry decode(CompoundTag tag) {
		if (tag == null || !tag.contains("k")) {
			return null;
		}
		double[] segs;
		if (tag.contains("d")) {
			long[] bits = tag.getLongArray("d");
			segs = new double[bits.length - bits.length % 6];
			for (int i = 0; i < segs.length; i++) {
				segs[i] = Double.longBitsToDouble(bits[i]);
			}
		} else {
			// older (float) encoding
			int[] bits = tag.getIntArray("s");
			segs = new double[bits.length - bits.length % 6];
			for (int i = 0; i < segs.length; i++) {
				segs[i] = Float.intBitsToFloat(bits[i]);
			}
		}
		AABB wall = tag.contains("x0") ? new AABB(tag.getDouble("x0"), tag.getDouble("y0"), tag.getDouble("z0"),
				tag.getDouble("x1"), tag.getDouble("y1"), tag.getDouble("z1")) : null;
		return new WebGeometry(tag.getByte("k"), segs, wall, tag.getInt("axis"),
				new Vec3(tag.getDouble("nx"), tag.getDouble("ny"), tag.getDouble("nz")));
	}

	// ---------------- queries ----------------

	private AABB segBox(int i) {
		int o = i * 6;
		return new AABB(segs[o], segs[o + 1], segs[o + 2], segs[o + 3], segs[o + 4], segs[o + 5]);
	}

	/**
	 * The solid boxes this web offers inside {@code query} (strand steps + the wall panel). Strands are one-way, like a
	 * platform: with {@code feetY} given, a strand step only counts when the feet are no more than half a block below
	 * its top (so you can jump up through a web and land on it, and walk up a sloping rope); NaN = every box (climbing).
	 */
	public void collisionBoxes(AABB query, List<VoxelShape> out, double feetY) {
		if (!bounds.intersects(query)) {
			return;
		}
		if (wallBox != null && wallBox.intersects(query)) {
			out.add(Shapes.create(wallBox));
		}
		AABB grown = query.inflate(HALF + 0.05);
		for (int i = 0; i < segmentCount(); i++) {
			if (!segBox(i).inflate(HALF).intersects(grown)) {
				continue;
			}
			Vec3 a = a(i);
			Vec3 d = b(i).subtract(a);
			double len = d.length();
			int n = Math.max(1, (int) Math.ceil(len / STEP));
			for (int k = 0; k <= n; k++) {
				Vec3 p = a.add(d.scale(k / (double) n));
				AABB box = new AABB(p.x - HALF, p.y - BELOW, p.z - HALF, p.x + HALF, p.y + 0.02, p.z + HALF);
				if (box.intersects(query) && (Double.isNaN(feetY) || feetY >= box.maxY - 0.5)) {
					out.add(Shapes.create(box));
				}
			}
		}
	}

	/** Whether a body with this box is in contact with the web. */
	public boolean touches(AABB body) {
		if (!bounds.intersects(body)) {
			return false;
		}
		AABB grown = body.inflate(TOUCH);
		if (wallBox != null && wallBox.intersects(grown)) {
			return true;
		}
		for (int i = 0; i < segmentCount(); i++) {
			if (!segBox(i).intersects(grown)) {
				continue;
			}
			Vec3 a = a(i);
			Vec3 d = b(i).subtract(a);
			int n = Math.max(1, (int) Math.ceil(d.length() / STEP));
			for (int k = 0; k <= n; k++) {
				if (grown.contains(a.add(d.scale(k / (double) n)))) {
					return true;
				}
			}
		}
		return false;
	}

	/** The point of the web nearest {@code p} (strands only; a wall answers with its panel centre). */
	public Vec3 closestPoint(Vec3 p) {
		Vec3 best = wallBox != null ? wallBox.getCenter() : null;
		double bestD = best == null ? Double.MAX_VALUE : best.distanceToSqr(p);
		for (int i = 0; i < segmentCount(); i++) {
			Vec3 c = closestOnSegment(a(i), b(i), p);
			double dd = c.distanceToSqr(p);
			if (dd < bestD) {
				bestD = dd;
				best = c;
			}
		}
		return best;
	}

	public static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 p) {
		Vec3 d = b.subtract(a);
		double l2 = d.lengthSqr();
		if (l2 < 1.0e-9) {
			return a;
		}
		double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(d) / l2));
		return a.add(d.scale(t));
	}

	/**
	 * Where the ray {@code from -> to} first passes within {@code radius} of a strand (or through the wall), as a
	 * fraction 0..1 of the ray, or -1 for a miss. Used for cutting a web and for its anchor search.
	 */
	public double rayHit(Vec3 from, Vec3 to, double radius) {
		double best = -1.0;
		if (wallBox != null) {
			var hit = wallBox.inflate(radius).clip(from, to);
			if (hit.isPresent()) {
				best = hit.get().distanceTo(from) / Math.max(1.0e-6, to.distanceTo(from));
			}
		}
		Vec3 r = to.subtract(from);
		for (int i = 0; i < segmentCount(); i++) {
			Vec3 a = a(i);
			Vec3 s = b(i).subtract(a);
			double[] st = closestParams(from, r, a, s);
			Vec3 p1 = from.add(r.scale(st[0]));
			Vec3 p2 = a.add(s.scale(st[1]));
			if (p1.distanceToSqr(p2) <= radius * radius && (best < 0.0 || st[0] < best)) {
				best = st[0];
			}
		}
		return best;
	}

	/** Closest-approach parameters (each clamped to 0..1) of segments p+t*r and q+u*s. */
	private static double[] closestParams(Vec3 p, Vec3 r, Vec3 q, Vec3 s) {
		double a = r.dot(r);
		double e = s.dot(s);
		Vec3 w = p.subtract(q);
		double f = s.dot(w);
		if (a < 1.0e-9 && e < 1.0e-9) {
			return new double[] {0, 0};
		}
		double t;
		double u;
		if (a < 1.0e-9) {
			t = 0;
			u = clamp(f / e);
		} else {
			double c = r.dot(w);
			if (e < 1.0e-9) {
				u = 0;
				t = clamp(-c / a);
			} else {
				double b = r.dot(s);
				double den = a * e - b * b;
				t = den != 0.0 ? clamp((b * f - c * e) / den) : 0.0;
				u = (b * t + f) / e;
				if (u < 0) {
					u = 0;
					t = clamp(-c / a);
				} else if (u > 1) {
					u = 1;
					t = clamp((b - c) / a);
				}
			}
		}
		return new double[] {t, u};
	}

	private static double clamp(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/** v0.16.1: like {@link #rayHit} but strands only (never the wall panel): the fraction 0..1 along the ray, or -1. */
	public double strandRayHit(Vec3 from, Vec3 to, double radius) {
		double best = -1.0;
		Vec3 r = to.subtract(from);
		for (int i = 0; i < segmentCount(); i++) {
			Vec3 a = a(i);
			Vec3 s = b(i).subtract(a);
			double[] st = closestParams(from, r, a, s);
			if (from.add(r.scale(st[0])).distanceToSqr(a.add(s.scale(st[1]))) <= radius * radius && (best < 0.0 || st[0] < best)) {
				best = st[0];
			}
		}
		return best;
	}

	/** v0.16.1: the strand segment nearest {@code p} (its index), or -1 when there are none. */
	public int nearestSegment(Vec3 p) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < segmentCount(); i++) {
			double d = closestOnSegment(a(i), b(i), p).distanceToSqr(p);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	/** Total strand length (the web reserve price of a woven web scales with it on the HUD). */
	public double totalLength() {
		double sum = 0;
		for (int i = 0; i < segmentCount(); i++) {
			sum += a(i).distanceTo(b(i));
		}
		return sum;
	}
}
