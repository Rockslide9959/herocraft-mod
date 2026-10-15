package com.projecthero.mod.cyborg;

/**
 * v0.16.5: every Cyborg number in one place (static finals, like Nova's / Ghost Rider's). Times are in ticks. The kit
 * follows {@code docs/V0165_POWERS_PROPOSAL.md} section 1 as the user approved it.
 */
public final class CyborgConfig {
	private CyborgConfig() {
	}

	// ---------------------------------------------------------------- the Power Core
	public static final float CORE_MAX = 100f;
	public static final float CORE_REGEN = 2f;
	/** Next to a block of redstone (within 3 blocks) the core charges at this rate instead. */
	public static final float CORE_REGEN_REDSTONE = 6f;
	public static final int REDSTONE_RANGE = 3;

	// ---------------------------------------------------------------- getting it / the forms
	/** The Mother Box fuses with a player at this much health or less (4 hearts). */
	public static final float FUSE_HEALTH = 8f;
	public static final int CONVERT_TICKS = 200;
	/** v0.16.5 rework: the hologram dissolving (H again / a move) and building over him (H). */
	public static final int PLATES_ON_TICKS = 14;
	public static final int PLATES_OFF_TICKS = 50;
	public static final int TOGGLE_COOLDOWN = 20;

	// ---------------------------------------------------------------- passives (always)
	public static final double ARMOR_BONUS = 4.0;
	public static final float HUNGER_FACTOR = 0.5f;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Sonic Cannon / Shift+R: Sonic Burst
	public static final int CANNON_CHARGE_TICKS = 30;
	public static final float CANNON_MIN_DAMAGE = 6f;
	public static final float CANNON_MAX_DAMAGE = 16f;
	public static final double CANNON_RANGE = 40.0;
	public static final float CANNON_COST = 20f;
	public static final int CANNON_COOLDOWN = 20;
	public static final float BURST_DAMAGE = 8f;
	public static final double BURST_RANGE = 6.0;
	/** cos of the cone's half-angle (about 50 degrees). */
	public static final double BURST_CONE = 0.64;
	public static final float BURST_COST = 25f;
	public static final int BURST_COOLDOWN = 120;

	// ---------------------------------------------------------------- G: Hydraulic Punch / Shift+G: Grapple Claw
	public static final float PUNCH_DAMAGE = 12f;
	public static final double PUNCH_RANGE = 4.0;
	public static final int PUNCH_COOLDOWN = 120;
	public static final double GRAPPLE_RANGE = 30.0;
	public static final int GRAPPLE_COOLDOWN = 100;

	// ---------------------------------------------------------------- X: Thruster Dash / Shift+X or double-tap jump: Jet Flight
	public static final double DASH_DISTANCE = 10.0;
	public static final double DASH_SPEED = 1.6;
	public static final int DASH_COOLDOWN = 80;
	public static final float HOVER_COST_PER_SECOND = 4f;
	/** The jetpack building onto his back at take-off (and folding away on landing), a voxel at a time. */
	public static final int JETPACK_BUILD_TICKS = 16;

	// ---------------------------------------------------------------- Z: Techno-Override / Shift+Z: Scan
	public static final double OVERRIDE_RANGE = 24.0;
	public static final int OVERRIDE_TICKS = 400;
	public static final float OVERRIDE_COST = 40f;
	public static final int OVERRIDE_COOLDOWN = 400;
	public static final double SCAN_RANGE = 32.0;
	public static final int SCAN_TICKS = 200;
	public static final int SCAN_MAX_ORES = 256;
	public static final int SCAN_COOLDOWN = 300;

	// ---------------------------------------------------------------- V: Boom Tube / Shift+V: Waypoint
	public static final double BOOM_RANGE = 64.0;
	public static final double BOOM_SQUAD_RADIUS = 6.0;
	public static final int BOOM_COOLDOWN = 1800;

	// ---------------------------------------------------------------- C: Defense Matrix / Shift+C: Self-Repair
	public static final float MATRIX_HP = 30f;
	public static final int MATRIX_TICKS = 100;
	public static final int MATRIX_COOLDOWN = 300;
	/** cos of the half-angle the Matrix covers in front of him (about 70 degrees). */
	public static final double MATRIX_ARC = 0.34;
	public static final int REPAIR_IRON = 3;
	public static final float REPAIR_HEAL = 8f;
	public static final int REPAIR_TICKS = 80;
	public static final int REPAIR_COOLDOWN = 600;
}
