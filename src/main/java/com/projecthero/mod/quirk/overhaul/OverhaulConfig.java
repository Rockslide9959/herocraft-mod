package com.projecthero.mod.quirk.overhaul;

/**
 * v0.16.7 revamp (docs/QUIRK_REVAMP_V0167.md): every number of the Overhaul Quirk (Kai Chisaki). Ticks unless named
 * otherwise; damage in half-hearts (HP). Focus is the meter (0..100): every touch spends it, it refills on its own after
 * a short pause (twice as fast with the gloves on).
 * The user's slot rule (2026-10-22): X = movement, V = utility / mode, the rest mainly attacks.
 * <pre>
 *   R  Disassemble            Shift+R  Disarm
 *   G  Ground Spikes          Shift+G  Spike Field
 *   X  Terrain Surf           Shift+X  Pillar Launch           (movement)
 *   Z  Overhaul Strike        Shift+Z  Disassemble Terrain (pits that close again)
 *   C  Wall Burst             Shift+C  Gloves (toggle; works while spent)
 *   V  Chronostasis Fusion    Shift+V  Reassemble (full heal + Restore broken ground, 60 s)   (mode / utility)
 * </pre>
 * The Quirk numbers are static (no config file), like every Quirk's; nothing to migrate.
 */
public final class OverhaulConfig {
	private OverhaulConfig() {
	}

	// ---------------------------------------------------------------- ability ids (quirk_overhaul_<move>)
	public static final String DISASSEMBLE = "quirk_overhaul_disassemble";
	public static final String REASSEMBLE = "quirk_overhaul_reassemble";
	/** Ground Spikes (the id predates the revamp's name). */
	public static final String SPIKE_WAVE = "quirk_overhaul_spike_wave";
	public static final String SPIKE_FIELD = "quirk_overhaul_spike_field";
	public static final String WALL = "quirk_overhaul_wall";
	/** Disassemble Terrain (the id predates the revamp's name). */
	public static final String PIT = "quirk_overhaul_pit";
	public static final String STRIKE = "quirk_overhaul_strike";
	public static final String DISARM = "quirk_overhaul_disarm";
	public static final String FUSION = "quirk_overhaul_fusion";
	public static final String GLOVES = "quirk_overhaul_gloves";
	public static final String SURF = "quirk_overhaul_surf";
	public static final String PILLAR = "quirk_overhaul_pillar";

	// ---------------------------------------------------------------- Focus
	/** Focus refilled a tick (6 a second)... */
	public static final float FOCUS_REGEN_PER_TICK = 0.3f;
	/** ...this many times faster with the gloves on. */
	public static final float GLOVES_REGEN_MULT = 2f;
	/** ...starting this long after the last spend. */
	public static final int FOCUS_REGEN_DELAY = 20;
	/** Spent to (almost) nothing: "your focus is spent" -- no moves (but Shift+C) for this long. */
	public static final int FOCUS_SPENT_TICKS = 50;

	// ---------------------------------------------------------------- shared
	/** Touch reach (Fusion adds {@link #FUSION_REACH_BONUS}). */
	public static final double TOUCH_RANGE = 3.5;
	/** Fusion: every move hits this much harder. */
	public static final float FUSION_DAMAGE_MULT = 1.3f;
	/** Bosses take at most this fraction of their max health from one hit, and are never knocked about. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- passive: the bare-hand touch (gloves off)
	/** A bare-handed (empty main hand) melee hit with the gloves off disassembles a little too: extra damage... */
	public static final float TOUCH_PASSIVE_DAMAGE = 4f;
	/** ...for this much Focus (no Focus: a plain punch). */
	public static final float TOUCH_PASSIVE_COST = 3f;

	// ---------------------------------------------------------------- R: Disassemble / Shift+V: Reassemble
	public static final float DISASSEMBLE_COST = 14f;
	public static final int DISASSEMBLE_COOLDOWN = 30;
	public static final float DISASSEMBLE_DAMAGE = 18f;
	public static final double DISASSEMBLE_KNOCKBACK = 0.6;
	/** A body Disassemble kills bursts: shards to everything this close... */
	public static final double DISASSEMBLE_BURST_RADIUS = 3.5;
	public static final float DISASSEMBLE_BURST_DAMAGE = 6f;

	public static final float REASSEMBLE_COST = 30f;
	/** Once per 60 s. */
	public static final int REASSEMBLE_COOLDOWN = 1200;
	/** Reaches the ally he looks at this far; otherwise he reassembles himself. */
	public static final double REASSEMBLE_RANGE = 6.0;

	// ---------------------------------------------------------------- G: Ground Spikes / Shift+G: Spike Field
	public static final float SPIKE_WAVE_COST = 18f;
	public static final int SPIKE_WAVE_COOLDOWN = 60;
	public static final int SPIKE_WAVE_LENGTH = 14;
	public static final float SPIKE_WAVE_DAMAGE = 10f;
	/** How far either side of a spike a creature is still caught. */
	public static final double SPIKE_HIT_RADIUS = 1.1;
	public static final double SPIKE_LIFT = 0.65;
	/** How long a spike stands. */
	public static final int SPIKE_TTL = 100;

	public static final float SPIKE_FIELD_COST = 28f;
	public static final int SPIKE_FIELD_COOLDOWN = 140;
	public static final double SPIKE_FIELD_INNER = 2.5;
	public static final double SPIKE_FIELD_OUTER = 4.5;
	public static final float SPIKE_FIELD_DAMAGE = 9f;

	// ---------------------------------------------------------------- C: Wall Burst / Shift+Z: Disassemble Terrain
	public static final float WALL_COST = 18f;
	public static final int WALL_COOLDOWN = 100;
	public static final int WALL_WIDTH = 5;
	public static final int WALL_HEIGHT = 3;
	public static final int WALL_DISTANCE = 2;
	public static final int WALL_TTL = 200;
	/** Anything standing where the wall bursts up is shoved back and takes this. */
	public static final float WALL_BURST_DAMAGE = 5f;

	public static final float PIT_COST = 30f;
	public static final int PIT_COOLDOWN = 260;
	public static final double PIT_RANGE = 14.0;
	/** Everything this close to where he aims drops in (a pit each)... */
	public static final double PIT_AREA_RADIUS = 3.0;
	/** ...at most this many. */
	public static final int PIT_MAX_TARGETS = 6;
	/** How long the pits hold their prisoners under the lids before the ground closes back up (pushing them out). */
	public static final int PIT_TICKS = 80;
	public static final int PIT_DEPTH = 2;
	public static final float PIT_DAMAGE = 6f;
	/** Only creatures that fit a 1x1x2 hole. */
	public static final double PIT_MAX_WIDTH = 1.0;
	public static final double PIT_MAX_HEIGHT = 2.0;

	// ---------------------------------------------------------------- Z: Overhaul Strike / Shift+R: Disarm
	public static final float STRIKE_COST = 16f;
	public static final int STRIKE_COOLDOWN = 70;
	/** The dash: blocks a tick along the ground, for {@link #STRIKE_TICKS}. */
	public static final double STRIKE_SPEED = 1.35;
	public static final int STRIKE_TICKS = 7;
	/** The first enemy his reaching hand touches on the way. */
	public static final double STRIKE_HIT_RADIUS = 1.4;
	public static final float STRIKE_DAMAGE = 14f;

	public static final float DISARM_COST = 20f;
	public static final int DISARM_COOLDOWN = 200;
	public static final int DISARM_MISS_COOLDOWN = 15;

	// ---------------------------------------------------------------- V: Chronostasis Fusion (Shift+V Reassemble: above)
	public static final float FUSION_COST = 45f;
	/** Starts when Fusion ends. */
	public static final int FUSION_COOLDOWN = 1200;
	/** 20 s. */
	public static final int FUSION_TICKS = 400;
	/** Added to Attributes.SCALE (ADD_MULTIPLIED_BASE): 1.8 -> 2.7 blocks. */
	public static final double FUSION_SCALE = 0.5;
	public static final int FUSION_GROW_TICKS = 12;
	public static final double FUSION_REACH_BONUS = 2.5;
	public static final double FUSION_ATTACK_BONUS = 8.0;
	public static final double FUSION_ARMOR = 12.0;
	public static final double FUSION_TOUGHNESS = 6.0;
	public static final double FUSION_KNOCKBACK_RESIST = 0.6;
	public static final double FUSION_SPEED = -0.1;
	public static final double FUSION_ABSORB_RADIUS = 5.0;

	// ---- Restore: part of Reassemble (Shift+V) since the slot rule -- it also puts back the broken ground around him
	/** Blocks broken (exploded) within this of him in the last {@link #RESTORE_MEMORY_TICKS} are rebuilt. */
	public static final double RESTORE_RADIUS = 12.0;
	public static final int RESTORE_MEMORY_TICKS = 6000;
	/** At most this many blocks a Restore... */
	public static final int RESTORE_MAX_BLOCKS = 384;
	/** ...rebuilt as a wave, nearest first, over this long. */
	public static final int RESTORE_WAVE_TICKS = 24;
	/** The ledger keeps at most this many broken blocks per level (oldest dropped first). */
	public static final int RESTORE_LEDGER_CAP = 16384;

	// ---------------------------------------------------------------- Shift+C: Gloves
	public static final int GLOVES_COOLDOWN = 12;

	// ---------------------------------------------------------------- X: Terrain Surf / Shift+X: Pillar Launch
	public static final float SURF_COST = 14f;
	public static final int SURF_COOLDOWN = 60;
	/** The ground carries him this long (X again ends it early)... */
	public static final int SURF_TICKS = 34;
	/** ...at this speed, blocks a tick, along the way he looks. */
	public static final double SURF_SPEED = 0.85;
	/** The wave of earth rising behind him stands this long. */
	public static final int SURF_WAKE_TTL = 16;
	/** What it runs over is thrown aside. */
	public static final float SURF_DAMAGE = 5f;
	/** No fall damage while surfing and this long after. */
	public static final int SURF_SAFE_TICKS = 40;

	public static final float PILLAR_COST = 12f;
	public static final int PILLAR_COOLDOWN = 80;
	public static final int PILLAR_HEIGHT = 3;
	public static final int PILLAR_TTL = 60;
	public static final double PILLAR_UP = 1.35;
	public static final double PILLAR_FORWARD = 0.35;
	public static final int PILLAR_SAFE_TICKS = 100;
}
