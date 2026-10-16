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
	/** v0.16.5 balance pass (user): 2 a second. */
	public static final float CORE_REGEN = 2f;
	/** Next to a block of redstone (within 3 blocks) the core charges at this rate instead. */
	public static final float CORE_REGEN_REDSTONE = 6f;
	public static final int REDSTONE_RANGE = 3;

	// ---------------------------------------------------------------- getting it / the forms
	/** The Mother Box fuses with a player at this much health or less (4 hearts). */
	public static final float FUSE_HEALTH = 8f;
	/** v0.16.5 (user): the first transformation takes 5 s, a pixel at a time, no glow. */
	public static final int CONVERT_TICKS = 100;
	/** v0.16.5 rework: the hologram dissolving (H again / a move) and building over him (H). */
	public static final int PLATES_ON_TICKS = 14;
	public static final int PLATES_OFF_TICKS = 50;
	public static final int TOGGLE_COOLDOWN = 20;

	// ---------------------------------------------------------------- passives (always)
	/** v0.16.5 balance pass (user): diamond-level armour with nothing worn (a full diamond set: 20 armour, 8 toughness). */
	public static final double ARMOR_BONUS = 20.0;
	public static final double TOUGHNESS_BONUS = 8.0;
	/** v0.16.5 balance pass (user): +9 melee. */
	public static final double MELEE_BONUS = 9.0;
	public static final float HUNGER_FACTOR = 0.5f;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Sonic Cannon / Shift+R: Sonic Burst
	public static final int CANNON_CHARGE_TICKS = 30;
	/** v0.16.5 balance pass (user): 15 damage, 5 Power Core (the charge widens the beam and the knockback). */
	public static final float CANNON_MIN_DAMAGE = 15f;
	public static final float CANNON_MAX_DAMAGE = 15f;
	public static final double CANNON_RANGE = 40.0;
	public static final float CANNON_COST = 5f;
	public static final int CANNON_COOLDOWN = 10;
	/** v0.16.5 balance pass (user): 24 damage, 10 Power Core. */
	public static final float BURST_DAMAGE = 24f;
	public static final double BURST_RANGE = 6.0;
	/** cos of the cone's half-angle (about 50 degrees). */
	public static final double BURST_CONE = 0.64;
	public static final float BURST_COST = 10f;
	public static final int BURST_COOLDOWN = 120;

	// ---------------------------------------------------------------- G: Hydraulic Punch / Shift+G: Arm Saw
	/** v0.16.5 balance pass (user): 24 damage, 5 Power Core. */
	public static final float PUNCH_DAMAGE = 24f;
	public static final float PUNCH_COST = 5f;
	public static final double PUNCH_RANGE = 4.0;
	public static final int PUNCH_COOLDOWN = 120;
	/** Shift+G Arm Saw: a buzzsaw on the forearm for 8 s, +10 melee, every hit breaks a raised shield. */
	public static final int SAW_TICKS = 160;
	public static final double SAW_BONUS = 10.0;
	public static final float SAW_COST = 5f;
	public static final int SAW_COOLDOWN = 300;

	// ---------------------------------------------------------------- X: Jet Dash / Shift+X: Grapple Claw / double-tap jump: Jet Flight
	/** v0.16.5 balance pass (user): a 20-block dash on the jetpack. */
	public static final double DASH_DISTANCE = 20.0;
	public static final double DASH_SPEED = 1.9;
	public static final int DASH_MAX_TICKS = 16;
	public static final int DASH_COOLDOWN = 80;
	/** v0.16.5 balance pass (user): 50 blocks, 2 Power Core, now on Shift+X. */
	public static final double GRAPPLE_RANGE = 50.0;
	public static final float GRAPPLE_COST = 2f;
	public static final int GRAPPLE_COOLDOWN = 100;
	public static final float HOVER_COST_PER_SECOND = 4f;
	/** Jet Flight speeds, blocks a second (user: cruise 18, sprint 33). */
	public static final double FLIGHT_CRUISE_BPS = 18.0;
	public static final double FLIGHT_SPRINT_BPS = 33.0;
	/** The jetpack building onto his back at take-off (and folding away on landing), a voxel at a time. */
	public static final int JETPACK_BUILD_TICKS = 8;

	// ---------------------------------------------------------------- Z: Missile Barrage / Shift+Z: Scan
	/** v0.16.5 (user: "a move that deals damage"): 8 homing micro-missiles from a shoulder launcher. */
	public static final int MISSILES = 8;
	public static final int MISSILE_INTERVAL = 2;
	public static final float MISSILE_DAMAGE = 5f;
	public static final double MISSILE_BLAST = 2.5;
	public static final double MISSILE_SPEED = 1.3;
	public static final double MISSILE_RANGE = 40.0;
	public static final int MISSILE_LIFE = 60;
	public static final float MISSILE_COST = 10f;
	public static final int MISSILE_COOLDOWN = 160;
	public static final double SCAN_RANGE = 32.0;
	public static final int SCAN_TICKS = 200;
	public static final int SCAN_MAX_ORES = 256;
	public static final int SCAN_COOLDOWN = 300;

	// ---------------------------------------------------------------- V: Circuit Link / Shift+V: the Boom Tube menu
	/** v0.16.5 (user: "works with redstone"): aim at a redstone part up to 32 blocks away and work it, or power it. */
	public static final double LINK_RANGE = 32.0;
	public static final float LINK_COST = 2f;
	public static final int LINK_COOLDOWN = 8;
	/** A tap powers the block for this long; holding V keeps it powered. */
	public static final int LINK_PULSE_TICKS = 30;
	/** Holding V keeps the power on for this many Power Core a second. */
	public static final float LINK_HOLD_COST_PER_SECOND = 1f;
	public static final int LINK_MAX_HOLD_TICKS = 1200;
	/** The Boom Tube menu (like the Bifrost): typed coordinates or one of three waypoints, any dimension. */
	public static final double BOOM_SQUAD_RADIUS = 6.0;
	public static final float BOOM_COST = 10f;
	public static final int BOOM_COOLDOWN = 1200;

	// ---------------------------------------------------------------- C: Defense Matrix / Shift+C: Self-Repair
	/** v0.16.5 (user): held on C, it soaks 100 damage from the front; let go and it drops, run it dry and it breaks. */
	public static final float MATRIX_HP = 100f;
	/** The longest it can be held (a safety net; letting go is the normal end). */
	public static final int MATRIX_TICKS = 20 * 60 * 10;
	/** The cooldown after letting go, and after it breaks. */
	public static final int MATRIX_COOLDOWN = 60;
	public static final int MATRIX_BREAK_COOLDOWN = 300;
	/** cos of the half-angle the Matrix covers in front of him (about 70 degrees). */
	public static final double MATRIX_ARC = 0.34;
	public static final int REPAIR_IRON = 3;
	public static final float REPAIR_HEAL = 8f;
	public static final int REPAIR_TICKS = 80;
	public static final int REPAIR_COOLDOWN = 600;
}
