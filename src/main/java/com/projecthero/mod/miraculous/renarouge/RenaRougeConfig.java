package com.projecthero.mod.miraculous.renarouge;

/**
 * v0.16.6: every Rena Rouge number (the Flute, the illusions, the six keys). Ticks unless the name says otherwise. The
 * shared transformed stats (armour, health, melee, speed, jump, no fall damage) come from the core
 * ({@code MiraculousStats}) and are not repeated here.
 */
public final class RenaRougeConfig {
	private RenaRougeConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String STRIKE = "rena_rouge_flute_strike";
	public static final String SWEEP = "rena_rouge_flute_sweep";
	public static final String THROW = "rena_rouge_flute_throw";
	public static final String LURE = "rena_rouge_melody_lure";
	public static final String DASH = "rena_rouge_fox_dash";
	public static final String SWAP = "rena_rouge_illusion_swap";
	public static final String DOUBLE = "rena_rouge_illusion_double";
	public static final String MIRAGE = "rena_rouge_mirage";
	public static final String CAMO = "rena_rouge_fox_camouflage";
	public static final String WALL = "rena_rouge_fake_wall";
	public static final String POUNCE = "rena_rouge_fox_pounce";
	public static final String FEINT = "rena_rouge_fox_feint";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most {@link #BOSS_MAX_FRACTION_PER_HIT} a hit and are never knocked about. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Flute Strike / Shift+R: Flute Sweep
	public static final float STRIKE_DAMAGE = 9f;
	public static final double STRIKE_REACH = 4.0;
	/** The combo's third hit knocks back this far. */
	public static final double STRIKE_FINISHER_KNOCKBACK = 0.8;
	public static final int STRIKE_COOLDOWN = 10;
	/** A press within this long of the last hit carries the combo on (forehand, backhand, overhead). */
	public static final int STRIKE_COMBO_WINDOW = 30;
	public static final float SWEEP_DAMAGE = 7f;
	public static final double SWEEP_RADIUS = 4.0;
	public static final double SWEEP_KNOCKBACK = 1.3;
	public static final double SWEEP_LIFT = 0.25;
	public static final int SWEEP_COOLDOWN = 60;

	// ---------------------------------------------------------------- G: Flute Throw / Shift+G: Melody Lure
	public static final float THROW_DAMAGE = 10f;
	/** How far out the flute flies before it turns back. */
	public static final double THROW_RANGE = 16.0;
	/** Ticks of the outbound arc. */
	public static final int THROW_OUT_TICKS = 16;
	/** The arc bows this far to the side (blocks) at its widest. */
	public static final double THROW_CURVE = 3.5;
	/** Coming back: blocks a tick. */
	public static final double THROW_RETURN_SPEED = 1.3;
	/** It gives up and pops back into her hand after this long. */
	public static final int THROW_MAX_TICKS = 80;
	/** Starts when the flute is caught (it can't be thrown while out anyway). */
	public static final int THROW_COOLDOWN = 40;
	public static final double LURE_RADIUS = 15.0;
	public static final int LURE_TICKS = 100;
	/** Where she looks, at most this far. */
	public static final double LURE_RANGE = 24.0;
	public static final int LURE_COOLDOWN = 300;

	// ---------------------------------------------------------------- X: Fox Dash / Shift+X: Illusion Swap
	public static final double DASH_DISTANCE = 10.0;
	public static final int DASH_DECOY_TICKS = 20;
	public static final int DASH_COOLDOWN = 100;
	/** Her own illusions within this range can be swapped with. */
	public static final double SWAP_RANGE = 48.0;
	public static final int SWAP_COOLDOWN = 60;

	// ---------------------------------------------------------------- Z: Illusion Double / Shift+Z: Mirage
	public static final int DOUBLE_TICKS = 15 * 20;
	/** The Double hits for this share of her melee damage. */
	public static final float DOUBLE_DAMAGE_SHARE = 0.5f;
	/** Mob hits the Double can take before it dissolves (a player's hit dissolves any illusion at once). */
	public static final int DOUBLE_HITS = 6;
	public static final int DOUBLE_COOLDOWN = 600;
	public static final int MIRAGE_TICKS = 30 * 20;
	public static final int MIRAGE_COPIES = 5;
	/** Mob hits each Mirage copy can take. */
	public static final int MIRAGE_HITS = 3;
	public static final double MIRAGE_RADIUS = 20.0;
	/** Ticks of the flute melody before the bubble floats off, and of the bubble's float before it pops. */
	public static final int MIRAGE_PLAY_TICKS = 24;
	public static final int MIRAGE_BUBBLE_TICKS = 16;
	public static final int MIRAGE_WALL_WIDTH = 9;
	public static final int MIRAGE_WALL_HEIGHT = 4;
	/** A confused mob rethinks (another mob, an illusion, or wandering) this often. */
	public static final int CONFUSE_RETHINK = 30;
	public static final int MIRAGE_COOLDOWN = 1800;

	// ---------------------------------------------------------------- V: Fox Camouflage / Shift+V: Fake Wall
	public static final int CAMO_TICKS = 20 * 20;
	/** Starts when the camouflage ends. */
	public static final int CAMO_COOLDOWN = 300;
	public static final int WALL_WIDTH = 5;
	public static final int WALL_HEIGHT = 3;
	public static final int WALL_TICKS = 10 * 20;
	/** The wall stands this far ahead of her. */
	public static final double WALL_DISTANCE = 3.0;
	public static final int WALL_COOLDOWN = 300;

	// ---------------------------------------------------------------- C: Fox Pounce / Shift+C: Fox Feint
	public static final double POUNCE_UP = 1.15;
	public static final double POUNCE_FORWARD = 1.0;
	public static final double POUNCE_RADIUS = 3.0;
	public static final int POUNCE_STUN_TICKS = 40;
	/** Gives up looking for the landing after this long in the air. */
	public static final int POUNCE_MAX_TICKS = 80;
	public static final int POUNCE_COOLDOWN = 140;
	public static final int FEINT_TICKS = 3 * 20;
	public static final int FEINT_COOLDOWN = 240;

	// ---------------------------------------------------------------- the illusions
	/** An illusion fades out over this long once hit (sparkles of orange light). */
	public static final int DISSOLVE_TICKS = 12;
	/** Mobs within this of an illusion that were after her (or idle) go for the illusion instead. */
	public static final double ILLUSION_LURE_RANGE = 24.0;
}
