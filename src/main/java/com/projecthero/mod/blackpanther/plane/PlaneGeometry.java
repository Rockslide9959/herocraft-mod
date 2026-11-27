package com.projecthero.mod.blackpanther.plane;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther Ancestral Plane: where everything stands in a plane slot, relative to the slot's origin {@code O} (the
 * grass surface: a player standing on the savanna has his feet at {@code O.y}). The plane runs along +X, toward the setting sun:
 * <pre>
 *   spawn (x 0) ---- 70 blocks of savanna ---- the ancestors under the great acacia (x 70..78)
 *       ---- the Panther Shrine (centre x 110, round, 14 across the steps) ---- the spirit path (x 128..241, rising 3..15)
 * </pre>
 * Pure numbers: shared by the server (the story, the build) and the client (the cutscene cameras).
 */
public final class PlaneGeometry {
	private PlaneGeometry() {
	}

	/** The player wakes into the plane here, facing the sunset (+X). */
	public static final double SPAWN_X = 0.5, SPAWN_Z = 0.5;
	/** Yaw that faces +X (toward the sun, the ancestors and the shrine). */
	public static final float FACE_EAST = -90f;
	public static final float FACE_WEST = 90f;

	/** The eldest ancestor, who speaks, and the four behind her (x, z). */
	public static final double[][] ANCESTORS = { { 70.5, 0.5 }, { 72.0, -2.6 }, { 72.0, 3.6 }, { 74.2, -5.0 }, { 74.2, 6.0 } };
	public static final int ANCESTOR_COUNT = ANCESTORS.length;
	/** The great acacia the ancestors wait under. */
	public static final int GREAT_TREE_X = 80, GREAT_TREE_Z = 13;
	/** Walking within this many blocks (flat) of the eldest starts the greeting. */
	public static final double MEET_RANGE = 9.0;
	/** Where the player is placed for the greeting: before the eldest, facing her. */
	public static final double MEET_X = 64.5, MEET_Z = 0.5;

	/** The Panther Shrine: a round raised floor (top surface at {@code O.y + SHRINE_FLOOR}) inside a ring of pillars. */
	public static final int SHRINE_X = 110;
	public static final int SHRINE_FLOOR = 2;
	public static final int SHRINE_R = 12;
	public static final int SHRINE_STEP_R = 14;
	public static final int PILLAR_R = 11;
	public static final int PILLAR_H = 9;
	/** The statue's pedestal centre (x offset from the shrine centre) and the altar before it. */
	public static final int PEDESTAL_DX = 10;
	/** The statue is built at this scale of its base shape (about 14 blocks tall). */
	public static final double STATUE_SCALE = 1.4;
	public static final int ALTAR_DX = 2;
	/** The ancestors' places at the shrine's west entrance once the procession arrives (x, z). */
	public static final double[][] ANCESTORS_AT_SHRINE = { { 94.5, -2.5 }, { 93.5, -5.2 }, { 93.5, 6.2 }, { 92.0, -7.8 }, { 92.0, 8.8 } };
	/** Where the procession leaves the player, at the foot of the shrine steps, facing in. */
	public static final double SHRINE_ENTRY_X = 92.5, SHRINE_ENTRY_Z = 0.5;

	/** The trial's arena: the shrine floor west of the pedestal; the player starts here, facing the statue. */
	public static final double ARENA_X = SHRINE_X - 3 + 0.5, ARENA_Z = 0.5;
	/** Shades appear on this radius round the arena centre. */
	public static final double SHADE_RING = 8.5;
	/** The player is kept inside this radius of the shrine centre during the trial. */
	public static final double ARENA_KEEP = 12.0;

	/** The spirit path's base: its platforms' y offsets count from {@code O.y + PATH_BASE}, x from {@code PATH_X}. */
	public static final int PATH_X = 128;
	public static final int PATH_BASE = 3;

	/** Where Bast's spirit appears for the finale: above the savanna beyond the end of the path. */
	public static final double SPIRIT_DX = 17.0, SPIRIT_DY = 4.0;

	/** The plane's walkable box around a slot (x from, x to, half z): stray outside and the plane draws you back. */
	public static final int BOX_MIN_X = -60, BOX_MAX_X = 300, BOX_HALF_Z = 110;

	// ---------------------------------------------------------------- helpers

	public static Vec3 at(BlockPos o, double x, double y, double z) {
		return new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z);
	}

	public static Vec3 spawn(BlockPos o) {
		return at(o, SPAWN_X, 0, SPAWN_Z);
	}

	public static Vec3 ancestor(BlockPos o, int i) {
		return at(o, ANCESTORS[i][0], 0, ANCESTORS[i][1]);
	}

	public static Vec3 ancestorAtShrine(BlockPos o, int i) {
		return at(o, ANCESTORS_AT_SHRINE[i][0], 0, ANCESTORS_AT_SHRINE[i][1]);
	}

	public static Vec3 meetSpot(BlockPos o) {
		return at(o, MEET_X, 0, MEET_Z);
	}

	public static Vec3 shrineEntry(BlockPos o) {
		return at(o, SHRINE_ENTRY_X, 0, SHRINE_ENTRY_Z);
	}

	public static Vec3 shrineCentre(BlockPos o) {
		return at(o, SHRINE_X + 0.5, SHRINE_FLOOR, 0.5);
	}

	public static Vec3 arena(BlockPos o) {
		return at(o, ARENA_X, SHRINE_FLOOR, ARENA_Z);
	}

	/** The statue's head (camera target): seated panther on the pedestal, facing west. */
	public static Vec3 statueHead(BlockPos o) {
		return at(o, SHRINE_X + PEDESTAL_DX + 0.5 - 1.5 * STATUE_SCALE, SHRINE_FLOOR + 2 + 7.6 * STATUE_SCALE, 0.5);
	}

	/** Is block {@code (x, y, z)} (offsets from O) part of the statue, its pedestal or the altar -- the right-click target? */
	public static boolean isShrineHeart(int x, int y, int z) {
		int dx = x - SHRINE_X;
		return dx >= ALTAR_DX - 1 && dx <= PEDESTAL_DX + 7 && Math.abs(z) <= 6 && y >= 0 && y <= SHRINE_FLOOR + 20;
	}

	public static Vec3 spirit(BlockPos o) {
		ParkourCourse.Platform f = ParkourCourse.finish();
		return at(o, PATH_X + f.x0() + f.sx() + SPIRIT_DX, PATH_BASE + f.y() + SPIRIT_DY, f.z0() + f.sz() / 2.0);
	}

	/** Is {@code pos} inside slot origin {@code o}'s walkable box? */
	public static boolean inBox(BlockPos o, Vec3 pos) {
		double x = pos.x - o.getX(), z = pos.z - o.getZ(), y = pos.y - o.getY();
		return x >= BOX_MIN_X && x <= BOX_MAX_X && Math.abs(z) <= BOX_HALF_Z && y > -40 && y < 120;
	}

	/** Yaw looking from {@code from} to {@code to}. */
	public static float lookYaw(Vec3 from, Vec3 to) {
		return (float) (Math.atan2(-(to.x - from.x), to.z - from.z) * 180.0 / Math.PI);
	}

	public static double flat(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}
}
