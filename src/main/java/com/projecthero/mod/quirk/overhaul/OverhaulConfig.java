package com.projecthero.mod.quirk.overhaul;

/**
 * v0.16.7: every number of the Overhaul Quirk (Kai Chisaki). Ticks unless named otherwise; damage in half-hearts (HP).
 * Focus is the meter (0..100): every move spends it, it refills on its own after a short pause.
 */
public final class OverhaulConfig {
	private OverhaulConfig() {
	}

	// ---------------------------------------------------------------- ability ids (quirk_overhaul_<move>)
	public static final String DISASSEMBLE = "quirk_overhaul_disassemble";
	public static final String SHRAPNEL = "quirk_overhaul_shrapnel";
	public static final String SPIKE_WAVE = "quirk_overhaul_spike_wave";
	public static final String SPIKE_FIELD = "quirk_overhaul_spike_field";
	public static final String WALL = "quirk_overhaul_wall";
	public static final String RAMP = "quirk_overhaul_ramp";
	public static final String RESTORE = "quirk_overhaul_restore";
	public static final String REASSEMBLE = "quirk_overhaul_reassemble";
	public static final String FIELD = "quirk_overhaul_field";
	public static final String PIT = "quirk_overhaul_pit";
	public static final String FUSION = "quirk_overhaul_fusion";
	public static final String SHARD = "quirk_overhaul_shard";

	// ---------------------------------------------------------------- Focus
	/** Focus refilled a tick (6 a second)... */
	public static final float FOCUS_REGEN_PER_TICK = 0.3f;
	/** ...starting this long after the last spend. */
	public static final int FOCUS_REGEN_DELAY = 20;
	/** Spent to (almost) nothing: "your focus is spent" -- no moves for this long. */
	public static final int FOCUS_SPENT_TICKS = 50;

	// ---------------------------------------------------------------- shared
	/** Touch reach (Fusion adds {@link #FUSION_REACH_BONUS}). */
	public static final double TOUCH_RANGE = 3.5;
	/** Fusion: every move hits this much harder. */
	public static final float FUSION_DAMAGE_MULT = 1.3f;
	/** Bosses take at most this fraction of their max health from one hit, and are never knocked about. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- R: Disassemble / Shift+R: Shrapnel Burst
	public static final float DISASSEMBLE_COST = 12f;
	public static final int DISASSEMBLE_COOLDOWN = 30;
	public static final float DISASSEMBLE_DAMAGE = 14f;
	public static final double DISASSEMBLE_KNOCKBACK = 0.6;

	public static final float SHRAPNEL_COST = 22f;
	public static final int SHRAPNEL_COOLDOWN = 80;
	public static final float SHRAPNEL_DAMAGE = 10f;
	public static final float SHRAPNEL_SPLASH_DAMAGE = 8f;
	public static final double SHRAPNEL_RADIUS = 4.5;

	// ---------------------------------------------------------------- G: Spike Wave / Shift+G: Spike Field
	public static final float SPIKE_WAVE_COST = 18f;
	public static final int SPIKE_WAVE_COOLDOWN = 60;
	public static final int SPIKE_WAVE_LENGTH = 12;
	public static final float SPIKE_WAVE_DAMAGE = 9f;
	/** How far either side of a spike a creature is still caught. */
	public static final double SPIKE_HIT_RADIUS = 1.1;
	public static final double SPIKE_LIFT = 0.65;
	/** How long a spike stands. */
	public static final int SPIKE_TTL = 100;

	public static final float SPIKE_FIELD_COST = 28f;
	public static final int SPIKE_FIELD_COOLDOWN = 140;
	public static final double SPIKE_FIELD_INNER = 2.5;
	public static final double SPIKE_FIELD_OUTER = 4.5;
	public static final float SPIKE_FIELD_DAMAGE = 8f;

	// ---------------------------------------------------------------- X: Reshape Wall / Shift+X: Launch Ramp
	public static final float WALL_COST = 18f;
	public static final int WALL_COOLDOWN = 100;
	public static final int WALL_WIDTH = 5;
	public static final int WALL_HEIGHT = 3;
	public static final int WALL_DISTANCE = 2;
	public static final int WALL_TTL = 200;

	public static final float RAMP_COST = 12f;
	public static final int RAMP_COOLDOWN = 80;
	public static final int RAMP_TTL = 40;
	public static final double RAMP_FORWARD = 1.45;
	public static final double RAMP_UP = 1.25;
	/** No fall damage for this long after the launch. */
	public static final int RAMP_SAFE_TICKS = 80;

	// ---------------------------------------------------------------- Z: Restore / Shift+Z: Reassemble Armour
	public static final float RESTORE_COST = 35f;
	public static final int RESTORE_COOLDOWN = 900;
	public static final double RESTORE_RANGE = 6.0;
	/** Squadmates this close are restored too. */
	public static final double RESTORE_SQUAD_RADIUS = 5.0;

	public static final float REASSEMBLE_COST = 25f;
	public static final int REASSEMBLE_COOLDOWN = 600;
	/** Absorption amplifier (1 = 8 HP) and duration. */
	public static final int REASSEMBLE_ABSORPTION_AMP = 1;
	public static final int REASSEMBLE_ABSORPTION_TICKS = 600;

	// ---------------------------------------------------------------- V: Overhaul Field / Shift+V: Collapse Pit
	public static final float FIELD_COST = 55f;
	public static final int FIELD_COOLDOWN = 900;
	public static final double FIELD_RADIUS = 7.0;
	public static final float FIELD_DAMAGE = 12f;
	public static final int FIELD_TTL = 140;

	public static final float PIT_COST = 30f;
	public static final int PIT_COOLDOWN = 300;
	public static final double PIT_RANGE = 12.0;
	/** How long the pit holds its prisoner under the lid. */
	public static final int PIT_TICKS = 80;
	public static final int PIT_DEPTH = 2;
	public static final float PIT_DAMAGE = 6f;
	/** Only creatures that fit a 1x1x2 hole. */
	public static final double PIT_MAX_WIDTH = 1.0;
	public static final double PIT_MAX_HEIGHT = 2.0;

	// ---------------------------------------------------------------- C: Fusion / Shift+C: Shard Shot
	public static final float FUSION_COST = 45f;
	/** Starts when Fusion ends. */
	public static final int FUSION_COOLDOWN = 1200;
	public static final int FUSION_TICKS = 600;
	/** Added to Attributes.SCALE (ADD_MULTIPLIED_BASE): 1.8 -> 2.7 blocks. */
	public static final double FUSION_SCALE = 0.5;
	public static final int FUSION_GROW_TICKS = 12;
	public static final double FUSION_REACH_BONUS = 2.0;
	public static final double FUSION_ATTACK_BONUS = 8.0;
	public static final double FUSION_ARMOR = 12.0;
	public static final double FUSION_TOUGHNESS = 6.0;
	public static final double FUSION_KNOCKBACK_RESIST = 0.6;
	public static final double FUSION_SPEED = -0.1;
	public static final double FUSION_ABSORB_RADIUS = 5.0;

	public static final float SHARD_COST = 15f;
	public static final int SHARD_COOLDOWN = 50;
	public static final double SHARD_BLOCK_RANGE = 6.0;
	public static final double SHARD_RANGE = 10.0;
	public static final double SHARD_HALF_ANGLE = 35.0;
	public static final float SHARD_DAMAGE = 9f;
	/** The block that burst comes back after this long. */
	public static final int SHARD_BLOCK_TTL = 100;
}
