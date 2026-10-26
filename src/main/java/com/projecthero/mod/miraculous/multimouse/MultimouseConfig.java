package com.projecthero.mod.miraculous.multimouse;

/**
 * v0.17.1: every Multimouse number (the jump rope, the twelve moves, Multitude, the two passives). Ticks unless the
 * name says otherwise. The shared transformed stats come from the core ({@code MiraculousStats}) and are not repeated.
 */
public final class MultimouseConfig {
	private MultimouseConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String LASH = "multimouse_rope_lash";
	public static final String LASSO = "multimouse_lasso_yank";
	public static final String TWIRL = "multimouse_rope_twirl";
	public static final String TRIP = "multimouse_skipping_trip";
	public static final String SWING = "multimouse_rope_swing";
	public static final String SCURRY = "multimouse_scurry";
	public static final String SWARM = "multimouse_mouse_swarm";
	public static final String MULTITUDE = "multimouse_multitude";
	public static final String HEARING = "multimouse_mouse_hearing";
	public static final String GNAW = "multimouse_gnaw";
	public static final String BIND = "multimouse_rope_bind";
	public static final String TRAP = "multimouse_mouse_trap";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most {@link #BOSS_MAX_FRACTION_PER_HIT} a hit and are never yanked, rooted, tripped or stunned. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	/** "In front of her": within this dot of the flattened look. */
	public static final double FRONT_DOT = 0.4;

	// ---------------------------------------------------------------- passives
	/** Light on her feet: an ordinary fall hurts a quarter (75% less), and never kills (she is left on at least half a heart). */
	public static final float FALL_DAMAGE_FACTOR = 0.25f;
	/** Quiet as a mouse: crouching, mobs farther than this lose track of her. */
	public static final double QUIET_RANGE = 6.0;
	/** ...looked for this far around her. */
	public static final double QUIET_SCAN = 32.0;

	// ---------------------------------------------------------------- R: Rope Lash / Shift+R: Lasso Yank
	/** As Cat Noir's Baton Strike: three presses, one swing each (the third knocks back); the cooldown starts with the third. */
	public static final int LASH_HITS = 3;
	public static final int LASH_WINDUP = 3;
	public static final int LASH_GAP = 5;
	public static final int LASH_WINDOW = 20;
	public static final int LASH_SWING_TICKS = 10;
	public static final float LASH_DAMAGE = 8f;
	public static final double LASH_REACH = 5.0;
	public static final double LASH_CONE = 0.5;
	public static final double LASH_KNOCKBACK = 0.35;
	public static final double LASH_FINISHER_KNOCKBACK = 1.1;
	public static final double LASH_FINISHER_LIFT = 0.2;
	public static final int LASH_COOLDOWN = 30;
	public static final double LASSO_RANGE = 16.0;
	public static final double LASSO_ASSIST_DEGREES = 7.0;
	public static final float LASSO_DAMAGE = 6f;
	public static final int LASSO_STUN_TICKS = 30;
	public static final int LASSO_COOLDOWN = 160;

	// ---------------------------------------------------------------- G: Rope Twirl / Shift+G: Skipping Trip
	public static final int TWIRL_TICKS = 80;
	public static final double TWIRL_RADIUS = 3.5;
	public static final float TWIRL_DAMAGE = 4f;
	public static final int TWIRL_EVERY = 10;
	public static final int TWIRL_COOLDOWN = 200;
	public static final double TRIP_RADIUS = 6.0;
	/** The low sweep's half arc (the front half). */
	public static final double TRIP_DOT = 0.0;
	public static final float TRIP_DAMAGE = 6f;
	/** Tripped: knocked down, slowed hard and unable to attack (a stun) this long. */
	public static final int TRIP_TICKS = 40;
	public static final int TRIP_COOLDOWN = 160;

	// ---------------------------------------------------------------- X: Rope Swing / Shift+X: Scurry
	public static final double SWING_RANGE = 30.0;
	/** The longest a swing pulls (it ends sooner once she is there). */
	public static final int SWING_TICKS = 40;
	/** Top speed along the rope (blocks a tick) and how hard the rope pulls each tick. */
	public static final double SWING_SPEED = 1.5;
	public static final double SWING_PULL = 0.28;
	/** "There": this close to the hook. */
	public static final double SWING_ARRIVE = 2.2;
	public static final int SWING_COOLDOWN = 60;
	/** Mouse size: about a third of her height. */
	public static final double SCURRY_SCALE = 0.33;
	/** 40% faster. */
	public static final double SCURRY_SPEED = 0.4;
	public static final int SCURRY_TICKS = 20 * 20;
	/** Growing back starts this cooldown. */
	public static final int SCURRY_COOLDOWN = 100;
	/** Small (Scurry or Multitude): hard to hit -- this chance a mob's hit or a projectile misses her. */
	public static final float SMALL_DODGE_CHANCE = 0.35f;

	// ---------------------------------------------------------------- Z: Mouse Swarm / Shift+Z: MULTITUDE
	public static final int SWARM_COUNT = 3;
	public static final int SWARM_TICKS = 6 * 20;
	public static final float SWARM_BITE = 2f;
	public static final double SWARM_RANGE = 20.0;
	public static final int SWARM_COOLDOWN = 280;
	/** v0.17.1 (user): ten copies (her plus 10 minis). */
	public static final int MULTITUDE_COPIES = 10;
	public static final int MULTITUDE_TICKS = 60 * 20;
	/** Her size while split (the vanilla generic.scale attribute). */
	public static final double MULTITUDE_SCALE = 0.25;
	/** Tiny and quick: this much faster. */
	public static final double MULTITUDE_SPEED = 0.3;
	/** Her own moves while she is tiny. */
	public static final float MINI_DAMAGE_FACTOR = 0.5f;
	public static final float MINI_BITE = 3f;
	public static final double MINI_HEALTH = 6.0;
	/** The copies' scale (they are as small as she is). */
	public static final double MINI_SCALE = 0.25;
	/** A copy bites at most once this often. */
	public static final int MINI_BITE_EVERY = 16;
	/** Recombining: the survivors run back for at most this long, then the shockwave. */
	public static final int RECOMBINE_TICKS = 20;
	public static final double RECOMBINE_RADIUS = 6.0;
	/** 2 damage for each copy that made it back. */
	public static final float RECOMBINE_DAMAGE_PER_COPY = 2f;
	public static final double RECOMBINE_KNOCKBACK = 1.4;
	/** The special's cooldown, from its start. */
	public static final int MULTITUDE_COOLDOWN = 2400;

	// ---------------------------------------------------------------- V: Mouse Hearing (toggle) / Shift+V: Gnaw
	public static final double HEARING_RANGE = 32.0;
	public static final int HEARING_COOLDOWN = 20;
	/** v0.17.1 (user): a 1x2 hole up to 3 deep through ANY breakable block, stone included (never bedrock, barriers or other unbreakables). */
	public static final int GNAW_DEPTH = 3;
	public static final int GNAW_COOLDOWN = 80;

	// ---------------------------------------------------------------- C: Rope Bind / Shift+C: Mouse Trap
	public static final double BIND_RANGE = 14.0;
	public static final double BIND_ASSIST_DEGREES = 7.0;
	public static final int BIND_TICKS = 80;
	public static final int BIND_COOLDOWN = 240;
	public static final int TRAP_MAX = 3;
	public static final double TRAP_PLACE_RANGE = 6.0;
	/** A mob whose feet come this close (flat) to the snare steps in. */
	public static final double TRAP_TRIGGER = 0.8;
	public static final int TRAP_SNARE_TICKS = 100;
	public static final float TRAP_DAMAGE = 8f;
	/** An unsprung snare frays away after this long. */
	public static final int TRAP_LIFE = 120 * 20;
	public static final int TRAP_COOLDOWN = 20;
}
