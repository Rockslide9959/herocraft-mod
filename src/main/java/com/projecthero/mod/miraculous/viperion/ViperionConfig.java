package com.projecthero.mod.miraculous.viperion;

/**
 * v0.16.7: every Viperion number (the Lyre, the twelve moves, Second Chance). Ticks unless the name says otherwise. The
 * shared transformed stats come from the core ({@code MiraculousStats}) and are not repeated here.
 */
public final class ViperionConfig {
	private ViperionConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String STRIKE = "viperion_lyre_strike";
	public static final String SWEEP = "viperion_lyre_sweep";
	public static final String CHORD = "viperion_sonic_chord";
	public static final String RESONANCE = "viperion_resonance";
	public static final String DASH = "viperion_serpent_dash";
	public static final String COIL = "viperion_coil_leap";
	public static final String VENOM = "viperion_venom_strike";
	public static final String SECOND_CHANCE = "viperion_second_chance";
	public static final String CONSTRICT = "viperion_constrict";
	public static final String SHIELD = "viperion_string_shield";
	public static final String HEAT = "viperion_heat_sense";
	public static final String HISS = "viperion_hiss";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most {@link #BOSS_MAX_FRACTION_PER_HIT} a hit and are never knocked about, rooted, stunned or scared. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	/** "In front of him": within this dot of the flattened look (about 66 degrees either side). */
	public static final double FRONT_DOT = 0.4;

	// ---------------------------------------------------------------- R: Lyre Strike / Shift+R: Lyre Sweep
	/** Three blows, this far apart; the last one hits harder and knocks back. */
	public static final int STRIKE_HITS = 3;
	public static final int STRIKE_GAP = 5;
	public static final float STRIKE_DAMAGE = 7f;
	public static final float STRIKE_FINISHER_DAMAGE = 11f;
	public static final double STRIKE_REACH = 3.8;
	public static final double STRIKE_FINISHER_KNOCKBACK = 1.3;
	public static final int STRIKE_COOLDOWN = 30;
	public static final double SWEEP_RADIUS = 4.5;
	/** The sweep's half arc: everything with a flattened dot above this (about 100 degrees either side: a wide swing). */
	public static final double SWEEP_DOT = -0.2;
	public static final float SWEEP_DAMAGE = 12f;
	public static final double SWEEP_KNOCKBACK = 1.4;
	public static final int SWEEP_COOLDOWN = 100;

	// ---------------------------------------------------------------- G: Sonic Chord / Shift+G: Resonance
	public static final double CHORD_RANGE = 12.0;
	/** The cone: within this dot of his look (about 32 degrees either side). */
	public static final double CHORD_DOT = 0.85;
	public static final float CHORD_DAMAGE = 10f;
	public static final double CHORD_KNOCKBACK = 1.8;
	public static final double CHORD_LIFT = 0.3;
	public static final int CHORD_COOLDOWN = 120;
	public static final double RESONANCE_RADIUS = 6.0;
	public static final float RESONANCE_DAMAGE = 4f;
	public static final int RESONANCE_STUN_TICKS = 40;
	public static final int RESONANCE_COOLDOWN = 300;

	// ---------------------------------------------------------------- X: Serpent Dash / Shift+X: Coil Leap
	public static final double DASH_DISTANCE = 12.0;
	/** Blocks a tick (the slither lasts distance / speed ticks; he stays low a few ticks longer). */
	public static final double DASH_SPEED = 1.2;
	public static final int DASH_LOW_EXTRA = 6;
	public static final int DASH_COOLDOWN = 100;
	/** The spring: about this many blocks forward, up a good way. */
	public static final double COIL_BLOCKS = 14.0;
	public static final double COIL_UP = 0.95;
	public static final int COIL_WINDUP = 6;
	public static final int COIL_COOLDOWN = 120;

	// ---------------------------------------------------------------- Z: Venom Strike / Shift+Z: Second Chance
	public static final double VENOM_REACH = 4.5;
	public static final float VENOM_DAMAGE = 6f;
	/** Strong Poison: Poison III for 8 s (bosses too -- the damage is the effect's own). */
	public static final int VENOM_POISON_TICKS = 160;
	public static final int VENOM_POISON_AMP = 2;
	public static final int VENOM_COOLDOWN = 100;
	/** v0.16.7 (user): the checkpoint holds for 90 s (not 60). */
	public static final int SECOND_CHANCE_WINDOW = 90 * 20;
	/** Rewinds a checkpoint gives; then it is spent. */
	public static final int SECOND_CHANCE_REWINDS = 3;
	/** Everything within this of the checkpoint is remembered and rewound (mobs, squadmates, blocks). */
	public static final double SECOND_CHANCE_RADIUS = 32.0;
	/** At most this many creatures (nearest first) and block spots are remembered. */
	public static final int SECOND_CHANCE_MAX_MOBS = 96;
	public static final int SECOND_CHANCE_MAX_BLOCKS = 8192;
	/** Two rewinds at least this far apart (one key press, not a double one). */
	public static final int SECOND_CHANCE_REWIND_GAP = 20;
	/** The special's cooldown, from the checkpoint. */
	public static final int SECOND_CHANCE_COOLDOWN = 2400;

	// ---------------------------------------------------------------- V: Constrict / Shift+V: String Shield
	public static final double CONSTRICT_RANGE = 14.0;
	public static final double CONSTRICT_ASSIST_DEGREES = 7.0;
	public static final int CONSTRICT_TICKS = 80;
	/** A squeeze this often, this hard (bosses: capped like every hit, and never rooted -- slowed instead). */
	public static final int CONSTRICT_SQUEEZE_EVERY = 10;
	public static final float CONSTRICT_SQUEEZE_DAMAGE = 2.5f;
	public static final int CONSTRICT_COOLDOWN = 240;
	public static final int SHIELD_TICKS = 6 * 20;
	/** Projectiles within this of his chest, in front, bounce off the strings. */
	public static final double SHIELD_REACH = 2.8;
	public static final int SHIELD_COOLDOWN = 300;

	// ---------------------------------------------------------------- C: Heat Sense (toggle) / Shift+C: Hiss
	/** Every living thing within this glows for him (through walls). */
	public static final double HEAT_RANGE = 32.0;
	/** Switching it off starts this short cooldown. */
	public static final int HEAT_COOLDOWN = 20;
	public static final double HISS_RADIUS = 10.0;
	public static final int HISS_TICKS = 5 * 20;
	public static final int HISS_COOLDOWN = 400;
}
