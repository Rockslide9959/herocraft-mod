package com.projecthero.mod.blackpanther.plane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>the spirit path</b>, the second half of Bast's test -- a parkour course of floating
 * platforms over the savanna, 113 blocks long from the first checkpoint to the finish, rising 12 blocks toward the sunset.
 * Pure data and rules (no Minecraft types), so the build, the server's checkpoint logic and the tests share one source.
 *
 * <p>Coordinates are offsets from the path's base ({@link PlaneGeometry#PATH_X}, {@link PlaneGeometry#PATH_BASE} above the
 * grass, z 0): {@code x0 / z0} the platform's min corner, {@code sx / sz} its size, {@code y} its top block (you stand at
 * {@code y + 1}). Gaps are 1-2 blocks (a 2-block gap with a 1-block rise at most), with 1x1 panther totems and one long log
 * beam for the harder moments: moderate, never a sprint-jump-or-die. Checkpoints (gold-inlaid 5x5 platforms) every ~20 blocks:
 * x 0, 20, 43, 65, 86, then the 7x7 finish at x 107. Falling more than {@link #FALL_DROP} below the last checkpoint, or down to
 * the grass, puts you back on it.
 */
public final class ParkourCourse {
	public enum Kind {
		/** Checkpoint / finish: polished blackstone bricks with a gold inlay. */
		CHECKPOINT,
		STONE,
		ACACIA,
		AMETHYST,
		/** A 1x1 panther totem pillar top. */
		TOTEM,
		/** A 1-wide stripped acacia log beam. */
		BEAM,
		FINISH
	}

	public record Platform(int x0, int y, int z0, int sx, int sz, Kind kind) {
		public double centreX() {
			return x0 + sx / 2.0;
		}

		public double centreZ() {
			return z0 + sz / 2.0;
		}

		/** Is offset point (x, z) over this platform (with a little slack at the edges)? */
		public boolean over(double x, double z, double slack) {
			return x >= x0 - slack && x <= x0 + sx + slack && z >= z0 - slack && z <= z0 + sz + slack;
		}
	}

	/** Below the last checkpoint's standing height by this much (or on the grass) = a fall. */
	public static final double FALL_DROP = 4.0;

	private static final List<Platform> PLATFORMS;
	private static final List<Integer> CHECKPOINTS;
	static {
		List<Platform> p = new ArrayList<>();
		// ---- checkpoint 1 (the start)
		p.add(new Platform(0, 0, -2, 5, 5, Kind.CHECKPOINT));
		p.add(new Platform(7, 0, -1, 2, 2, Kind.STONE));
		p.add(new Platform(11, 1, -1, 2, 2, Kind.ACACIA));
		p.add(new Platform(15, 1, -3, 1, 1, Kind.TOTEM));
		p.add(new Platform(17, 2, -4, 1, 1, Kind.TOTEM));
		// ---- checkpoint 2
		p.add(new Platform(20, 2, -6, 5, 5, Kind.CHECKPOINT));
		p.add(new Platform(27, 3, -3, 2, 2, Kind.ACACIA));
		p.add(new Platform(31, 3, 0, 1, 1, Kind.TOTEM));
		p.add(new Platform(33, 4, 2, 1, 1, Kind.TOTEM));
		p.add(new Platform(36, 4, 1, 3, 2, Kind.AMETHYST));
		p.add(new Platform(41, 5, 0, 1, 1, Kind.TOTEM));
		// ---- checkpoint 3
		p.add(new Platform(43, 5, -2, 5, 5, Kind.CHECKPOINT));
		p.add(new Platform(50, 5, 1, 6, 1, Kind.BEAM));
		p.add(new Platform(58, 6, 2, 2, 2, Kind.STONE));
		p.add(new Platform(62, 6, 5, 1, 1, Kind.TOTEM));
		// ---- checkpoint 4
		p.add(new Platform(65, 7, 2, 5, 5, Kind.CHECKPOINT));
		p.add(new Platform(72, 7, 3, 2, 2, Kind.ACACIA));
		p.add(new Platform(76, 8, 1, 1, 1, Kind.TOTEM));
		p.add(new Platform(79, 8, -1, 2, 2, Kind.AMETHYST));
		p.add(new Platform(83, 9, -2, 1, 1, Kind.TOTEM));
		// ---- checkpoint 5
		p.add(new Platform(86, 9, -4, 5, 5, Kind.CHECKPOINT));
		p.add(new Platform(93, 10, -2, 2, 2, Kind.STONE));
		p.add(new Platform(97, 10, 0, 1, 1, Kind.TOTEM));
		p.add(new Platform(99, 11, 1, 1, 1, Kind.TOTEM));
		p.add(new Platform(102, 11, 0, 3, 3, Kind.AMETHYST));
		// ---- the finish
		p.add(new Platform(107, 12, -2, 7, 7, Kind.FINISH));
		PLATFORMS = Collections.unmodifiableList(p);
		List<Integer> c = new ArrayList<>();
		for (int i = 0; i < p.size(); i++) {
			if (p.get(i).kind() == Kind.CHECKPOINT) {
				c.add(i);
			}
		}
		CHECKPOINTS = Collections.unmodifiableList(c);
	}

	private ParkourCourse() {
	}

	public static List<Platform> platforms() {
		return PLATFORMS;
	}

	public static int checkpointCount() {
		return CHECKPOINTS.size();
	}

	public static Platform checkpoint(int index) {
		return PLATFORMS.get(CHECKPOINTS.get(Math.max(0, Math.min(CHECKPOINTS.size() - 1, index))));
	}

	public static Platform finish() {
		return PLATFORMS.get(PLATFORMS.size() - 1);
	}

	/** The course's length along x, from the start of the first checkpoint to the end of the finish. */
	public static int length() {
		Platform f = finish();
		return f.x0() + f.sx() - PLATFORMS.get(0).x0();
	}

	/** Where a reset puts you: the middle of checkpoint {@code index}, standing on it (offsets: x, y, z). */
	public static double[] respawn(int index) {
		Platform p = checkpoint(index);
		return new double[] { p.centreX(), p.y() + 1, p.centreZ() };
	}

	/**
	 * The checkpoint a player standing at offset ({@code x, y, z}) has just reached, or -1. Standing means on top of it: feet
	 * within half a block of its surface.
	 */
	public static int reachedCheckpoint(double x, double y, double z) {
		for (int i = 0; i < CHECKPOINTS.size(); i++) {
			Platform p = PLATFORMS.get(CHECKPOINTS.get(i));
			if (p.over(x, z, 0.3) && y >= p.y() + 0.9 && y <= p.y() + 1.6) {
				return i;
			}
		}
		return -1;
	}

	/** True if the player at offset ({@code x, y, z}) stands on the finish. */
	public static boolean onFinish(double x, double y, double z) {
		Platform f = finish();
		return f.over(x, z, 0.3) && y >= f.y() + 0.9 && y <= f.y() + 1.6;
	}

	/** The next checkpoint index after reaching {@code reached} with {@code current} saved: checkpoints only move forward. */
	public static int advance(int current, int reached) {
		return Math.max(current, reached);
	}

	/**
	 * Has the player fallen? {@code y} is his height as an offset from the path base, {@code groundY} the grass as the same
	 * offset (negative), {@code checkpoint} his saved checkpoint.
	 */
	public static boolean fell(double y, double groundY, int checkpoint) {
		double standAt = checkpoint(checkpoint).y() + 1;
		return y < standAt - FALL_DROP || y < groundY + 0.6;
	}

	/** 0..1 how far along the course offset {@code x} is. */
	public static double progress(double x) {
		return Math.max(0.0, Math.min(1.0, (x - PLATFORMS.get(0).x0()) / length()));
	}
}
