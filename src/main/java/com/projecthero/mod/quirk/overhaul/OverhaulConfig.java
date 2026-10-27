package com.projecthero.mod.quirk.overhaul;

/**
 * v0.17.1 Overhaul revamp ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.2): every number of the Overhaul Quirk (Kai Chisaki).
 * Ticks unless named otherwise; damage in half-hearts (HP).
 *
 * <p><b>Matter</b> is the meter (0..100, it replaced Focus): the disassembled matter he rebuilds with. It refills from the
 * ground under his feet -- {@link #MATTER_GROUND_PER_SECOND} a second standing on stone or earth, {@link #MATTER_AIR_PER_SECOND}
 * on wood or in the air -- and a Disassemble that kills gives {@link #MATTER_PER_KILL}. Spikes, walls and Fusion SPEND it;
 * the disassembling moves (Disassemble, Shrapnel Burst, Disassemble Terrain) GAIN it. Bands: Starved 0-24 (constructs
 * small), Stocked 25-79, Overflowing 80-100 (spikes and walls x1.5).
 *
 * <p>The gloves: on by default; every move tugs them off for {@link #BARE_TICKS} (8 s) and while they are off a bare-hand
 * punch disassembles too. The old Gloves toggle and its slot are gone.
 * <pre>
 *   R  Disassemble (+5)           Shift+R  Disarm
 *   G  Ground Spikes              Shift+G  Spike Field            hold G  Thorn Forest
 *   X  Ground Shift               Shift+X  Pillar Launch          hold X  Terrain Surf     (movement)
 *   Z  Overhaul Strike            Shift+Z  Disassemble Terrain (+10)
 *   C  Shrapnel Burst (+8)        Shift+C  Wall Burst             hold C  Plus Ultra: Total Disassembly
 *   V  Reassemble                 Shift+V  Chronostasis Fusion (Mend with a damaged item in the off hand)   hold V  Restore
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
	/** v0.17.1: no longer a key (the gloves come off on their own); the id stays for old saves' cooldowns. */
	public static final String GLOVES = "quirk_overhaul_gloves";
	public static final String SURF = "quirk_overhaul_surf";
	public static final String PILLAR = "quirk_overhaul_pillar";
	// v0.17.1 revamp
	public static final String THORN = "quirk_overhaul_thorn_forest";
	public static final String GROUND_SHIFT = "quirk_overhaul_ground_shift";
	public static final String SHRAPNEL = "quirk_overhaul_shrapnel";
	public static final String RESTORE = "quirk_overhaul_restore";
	public static final String MEND = "quirk_overhaul_mend";
	public static final String MINING = "quirk_overhaul_mining";

	// ---------------------------------------------------------------- Matter
	/** Refilled a second standing on stone or earth (his feet draw it from the ground)... */
	public static final float MATTER_GROUND_PER_SECOND = 10f;
	/** ...on wood, anything else, or in the air. */
	public static final float MATTER_AIR_PER_SECOND = 3f;
	/** In the Fusion form it refills at this fraction. */
	public static final float FUSION_REGEN_MULT = 0.5f;
	/** The refill starts this long after the last spend. */
	public static final int MATTER_REGEN_DELAY = 4;
	/** A Disassemble (or any of his moves) that kills a body gives this much. */
	public static final float MATTER_PER_KILL = 15f;
	/** Construct size by band: Starved (below 25)... */
	public static final float STARVED_SCALE = 0.7f;
	/** ...Overflowing (80 and up). */
	public static final float OVERFLOWING_SCALE = 1.5f;

	// ---------------------------------------------------------------- the gloves (drawn on by default) / passives
	/** A move tugs the gloves off for this long (8 s): bare-hand punches disassemble meanwhile. */
	public static final int BARE_TICKS = 160;
	/** A bare-handed melee hit with the gloves off disassembles a little too: extra damage (free). */
	public static final float TOUCH_PASSIVE_DAMAGE = 4f;
	/** Germophobe: after a mob hits him, his next move within this long hits harder... */
	public static final int GERMOPHOBE_TICKS = 60;
	/** ...by this much. */
	public static final float GERMOPHOBE_MULT = 1.2f;

	// ---------------------------------------------------------------- shared
	/** Touch reach (Fusion adds {@link #FUSION_REACH_BONUS}). */
	public static final double TOUCH_RANGE = 3.5;
	/** Fusion: every move hits this much harder. */
	public static final float FUSION_DAMAGE_MULT = 1.3f;
	/** Bosses take at most this fraction of their max health from one hit, and are never knocked about. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- R: Disassemble / Shift+R: Disarm
	/** Matter a Disassemble GAINS (the body it takes apart). */
	public static final float DISASSEMBLE_GAIN = 5f;
	/** v0.17.1: 1 s (it gains Matter now, so the cooldown is what paces it). */
	public static final int DISASSEMBLE_COOLDOWN = 20;
	public static final float DISASSEMBLE_DAMAGE = 18f;
	public static final double DISASSEMBLE_KNOCKBACK = 0.6;
	/** A body Disassemble kills bursts: shards to everything this close... */
	public static final double DISASSEMBLE_BURST_RADIUS = 3.5;
	public static final float DISASSEMBLE_BURST_DAMAGE = 6f;
	/** The victim cracks (red lines) for this long before it bursts. */
	public static final int DISASSEMBLE_CRACK_TICKS = 4;

	/** Disarm gains a little (the item it takes apart). */
	public static final float DISARM_GAIN = 4f;
	public static final int DISARM_COOLDOWN = 30;
	public static final int DISARM_MISS_COOLDOWN = 8;

	// ---------------------------------------------------------------- G: Ground Spikes / Shift+G: Spike Field / hold G: Thorn Forest
	public static final float SPIKE_WAVE_COST = 12f;
	public static final int SPIKE_WAVE_COOLDOWN = 16;
	public static final int SPIKE_WAVE_LENGTH = 14;
	public static final float SPIKE_WAVE_DAMAGE = 10f;
	/** How far either side of a spike a creature is still caught. */
	public static final double SPIKE_HIT_RADIUS = 1.1;
	public static final double SPIKE_LIFT = 0.65;
	/** How long a spike mesh stands (rise, hold, sink). */
	public static final int SPIKE_TTL = 40;

	public static final float SPIKE_FIELD_COST = 20f;
	public static final int SPIKE_FIELD_COOLDOWN = 30;
	public static final double SPIKE_FIELD_INNER = 2.5;
	public static final double SPIKE_FIELD_OUTER = 4.5;
	public static final float SPIKE_FIELD_DAMAGE = 9f;

	/** Hold G this long (ticks) and the tap becomes the Thorn Forest aim. */
	public static final int HOLD_TICKS = 7;
	public static final float THORN_COST = 40f;
	public static final int THORN_COOLDOWN = 200;
	public static final double THORN_RANGE = 24.0;
	/** A 7x7 forest: cells this far either side of the centre. */
	public static final int THORN_HALF = 3;
	public static final float THORN_HEIGHT = 6f;
	public static final float THORN_DAMAGE = 16f;
	public static final double THORN_LIFT = 0.85;
	/** It stands this long as cover (what walks in is pushed out, arrows break on it). */
	public static final int THORN_STAND_TICKS = 80;
	/** Ticks between the forest's three waves (centre, middle ring, edge). */
	public static final int THORN_WAVE_GAP = 4;
	/** The aim held at most this long before it fires on its own. */
	public static final int THORN_MAX_AIM = 100;

	// ---------------------------------------------------------------- C: Shrapnel Burst / Shift+C: Wall Burst
	public static final float SHRAPNEL_GAIN = 8f;
	public static final int SHRAPNEL_COOLDOWN = 30;
	public static final double SHRAPNEL_RANGE = 8.0;
	/** The cone's half-angle (degrees). */
	public static final double SHRAPNEL_HALF_ANGLE = 32.0;
	/** "3 x 5": each shard 3, up to 5 shards on one body (point blank; 3 shards at mid range, 2 at the edge). */
	public static final float SHRAPNEL_SHARD_DAMAGE = 3f;
	public static final int SHRAPNEL_SHARDS = 5;

	public static final float WALL_COST = 16f;
	public static final int WALL_COOLDOWN = 24;
	public static final int WALL_WIDTH = 5;
	public static final int WALL_HEIGHT = 3;
	public static final int WALL_DISTANCE = 2;
	public static final int WALL_TTL = 200;
	/** Anything standing where the wall bursts up is shoved back and takes this. */
	public static final float WALL_BURST_DAMAGE = 5f;

	// ---------------------------------------------------------------- Z: Overhaul Strike / Shift+Z: Disassemble Terrain
	/** v0.17.1: free (a touch); a touch that lands gains this. */
	public static final float STRIKE_GAIN = 3f;
	public static final int STRIKE_COOLDOWN = 14;
	/** The dash: blocks a tick along the ground, for {@link #STRIKE_TICKS}. */
	public static final double STRIKE_SPEED = 1.35;
	public static final int STRIKE_TICKS = 7;
	/** The first enemy his reaching hand touches on the way. */
	public static final double STRIKE_HIT_RADIUS = 1.4;
	public static final float STRIKE_DAMAGE = 14f;

	public static final float PIT_GAIN = 10f;
	/** v0.17.1: 5 s (it gains Matter now, so the cooldown paces six-mob pits). */
	public static final int PIT_COOLDOWN = 100;
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

	// ---------------------------------------------------------------- V: Reassemble / Shift+V: Fusion (or Mend) / hold V: Restore
	public static final float REASSEMBLE_COST = 30f;
	/** Once per 45 s (a big move keeps a real cooldown). */
	public static final int REASSEMBLE_COOLDOWN = 900;
	/** Reaches the ally he looks at this far; otherwise he reassembles himself. */
	public static final double REASSEMBLE_RANGE = 6.0;

	public static final float FUSION_COST = 45f;
	/** Starts when Fusion ends (30 s: a big move keeps a real cooldown). */
	public static final int FUSION_COOLDOWN = 600;
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

	/** Mend: Shift+V with a damaged tool / armour piece in the off hand puts back this much durability... */
	public static final int MEND_DURABILITY = 100;
	/** ...for this much Matter. */
	public static final float MEND_COST = 20f;
	public static final int MEND_COOLDOWN = 20;

	/** Restore: hold V this long. Free (it only puts back what explosions broke). */
	public static final int RESTORE_HOLD_TICKS = 20;
	public static final int RESTORE_COOLDOWN = 200;
	/** Blocks broken (exploded) within this of him in the last {@link #RESTORE_MEMORY_TICKS} are rebuilt. */
	public static final double RESTORE_RADIUS = 12.0;
	public static final int RESTORE_MEMORY_TICKS = 6000;
	/** At most this many blocks a Restore... */
	public static final int RESTORE_MAX_BLOCKS = 384;
	/** ...rebuilt as a wave, nearest first, over this long. */
	public static final int RESTORE_WAVE_TICKS = 24;
	/** The ledger keeps at most this many broken blocks per level (oldest dropped first). */
	public static final int RESTORE_LEDGER_CAP = 16384;

	// ---------------------------------------------------------------- X: Ground Shift / Shift+X: Pillar Launch / hold X: Terrain Surf
	public static final float GROUND_SHIFT_COST = 6f;
	public static final int GROUND_SHIFT_COOLDOWN = 16;
	/** The floor carries him this far along his look... */
	public static final double GROUND_SHIFT_DISTANCE = 8.0;
	/** ...over this long. */
	public static final int GROUND_SHIFT_TICKS = 8;

	/** Matter to start the wave... */
	public static final float SURF_COST = 6f;
	/** ...and drained a tick while he rides it (8 a second, no refill while surfing): about 12 s from full. */
	public static final float SURF_DRAIN_PER_TICK = 0.4f;
	/** Starts when the surf ends. */
	public static final int SURF_COOLDOWN = 10;
	/** Step assist while surfing: the wave carries him up rises this tall (blocks; a player's own is 0.6). */
	public static final double SURF_STEP_HEIGHT = 2.0;
	/** The wave moves at this speed, blocks a tick, along the way he looks. */
	public static final double SURF_SPEED = 0.85;
	/** The wave of earth rising behind him stands this long. */
	public static final int SURF_WAKE_TTL = 16;
	/** What it runs over is thrown aside... */
	public static final float SURF_DAMAGE = 5f;
	/** ...at most once in this long per creature (a long ride can run the same one over again). */
	public static final int SURF_REHIT_TICKS = 20;
	/** No fall damage while surfing and this long after it ends. */
	public static final int SURF_SAFE_TICKS = 40;

	public static final float PILLAR_COST = 10f;
	public static final int PILLAR_COOLDOWN = 20;
	public static final int PILLAR_HEIGHT = 3;
	public static final int PILLAR_TTL = 60;
	public static final double PILLAR_UP = 1.35;
	public static final double PILLAR_FORWARD = 0.35;
	public static final int PILLAR_SAFE_TICKS = 100;

	// ---------------------------------------------------------------- survival: Disassembly mining
	/** Sneak + a bare-hand punch on stone / earth: the 3x3 in that face comes apart into its drops, for this much Matter. */
	public static final float MINING_COST = 6f;
	public static final int MINING_COOLDOWN = 8;

	// ---------------------------------------------------------------- Plus Ultra: Total Disassembly
	public static final double ULT_RADIUS = 16.0;
	/** The three waves start at these ticks after the palms land... */
	public static final int[] ULT_WAVES = { 6, 24, 42 };
	/** ...each rolls from the centre to the edge in this long. */
	public static final int ULT_WAVE_TICKS = 14;
	public static final float ULT_DAMAGE = 12f;
	public static final double ULT_LIFT = 0.75;
	/** How long the lifted ground stays apart before it reassembles behind the wave. */
	public static final int ULT_REASSEMBLE_TICKS = 10;
	/** He is fused (x1.5) and can't be knocked back this long. */
	public static final int ULT_TICKS = 64;
	/** The drawback: Matter is spent to this. */
	public static final float ULT_MATTER_AFTER = 0f;
}
