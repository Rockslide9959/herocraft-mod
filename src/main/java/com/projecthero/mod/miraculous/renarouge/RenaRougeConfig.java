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
	/** v0.16.6 (user: the throw missed what was aimed at): aim assist -- a target this close to the crosshair (degrees) is locked. */
	public static final double THROW_ASSIST_DEGREES = 6.0;
	/** The arc's sideways bow is at most this share of the distance to the target (a near throw curves little). */
	public static final double THROW_CURVE_PER_BLOCK = 0.18;
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
	/** v0.16.7 (user): 2 minutes from the cast (the pick on the wheel), so twice in one transformation. */
	public static final int MIRAGE_COOLDOWN = 2400;

	// ---------------------------------------------------------------- v0.16.7: the Mirage wheel's eight illusions
	/** The wheel's offer stays good this long after Shift+Z (a pick after that is ignored). */
	public static final int MIRAGE_MENU_TICKS = 600;
	/** The aimed illusions (Mirage Zone, Golden Lure, Pitfall) land where she looks, at most this far. */
	public static final double MIRAGE_AIM_RANGE = 32.0;
	/** When any illusion ends, the mobs it fooled stay dazed (Slowness II, no target) this long. */
	public static final int MIRAGE_DAZE_TICKS = 60;
	/** Moving at most this (MOVEMENT_SPEED): a boss that can't really walk (Galactus) only stops attacking instead. */
	public static final double MIRAGE_IMMOBILE_SPEED = 0.01;
	// 1. Army of Foxes (the classic Mirage, plus the foxes)
	public static final int FOX_COUNT = 8;
	public static final float FOX_DAMAGE = 6f;
	/** Mob hits a fox takes before it pops into orange mist (a player's hit pops one at once). */
	public static final int FOX_HITS = 3;
	public static final double FOX_SPEED = 0.42;
	/** A fox goes for enemies within this of itself. */
	public static final double FOX_SEEK_RANGE = 16.0;
	/** Hitting a Mirage copy bursts it into mist: the hitter is blinded this long ... */
	public static final int COPY_BURST_BLIND_TICKS = 60;
	/** ... and confused (Nausea for a player; a mob loses its target and wanders) this long. */
	public static final int COPY_BURST_CONFUSE_TICKS = 100;
	// 2. Monster
	public static final double MONSTER_RADIUS = 24.0;
	/** The beast is the fox model this many times over (about 7 blocks tall, more when it rears). */
	public static final float MONSTER_SCALE = 10f;
	/** Panicked mobs run this far away from it. */
	public static final double MONSTER_FLEE = 16.0;
	// 3. Vanishing Act
	public static final int VANISH_RUNNERS = 3;
	public static final double VANISH_RUN_DISTANCE = 20.0;
	/** A mob she (or a squadmate) hit within this long may still fight back. */
	public static final int VANISH_RETALIATE_TICKS = 100;
	/** Mobs this close that were after her / her squad lose them. */
	public static final double VANISH_RADIUS = 32.0;
	// 4. Mirage Zone
	public static final double ZONE_RADIUS = 12.0;
	// 5. Golden Lure
	public static final double GOLD_RADIUS = 30.0;
	// 6. Pitfall
	public static final int PIT_SIZE = 4;
	public static final int PIT_DEPTH = 3;
	/** Mobs within this of the pit walk for its illusory floor. */
	public static final double PIT_STEER_RADIUS = 16.0;
	// 7. Eclipse
	/** The bubble of night round her: this radius (about 20 blocks across). */
	public static final double ECLIPSE_RADIUS = 10.0;
	/** Enemies inside lose track of anything farther than this. */
	public static final double ECLIPSE_SIGHT = 4.0;
	/** Their arrows / fireballs fly off up to this many degrees. */
	public static final double ECLIPSE_SCATTER_DEGREES = 25.0;
	// 8. Hall of Mirrors
	public static final double MIRRORS_RADIUS = 20.0;
	/** Every tick a mirrored mob whose fight broke off is put on another mirrored mob; a lone one stays off her for this long at a time. */
	public static final int MIRRORS_RETHINK = 10;

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
