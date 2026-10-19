package com.projecthero.mod.miraculous.catnoir;

/**
 * v0.16.6: every Cat Noir number in one place (static finals, like Ghost Rider's). Times are in ticks, distances in
 * blocks. The shared Miraculous stats (armour, health, melee, speed, jump, no fall damage) live in the core.
 */
public final class CatNoirConfig {
	private CatNoirConfig() {
	}

	// ---------------------------------------------------------------- bosses
	/** A creature with at least this much max health (or a TitanCombat boss) is a boss. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	/** A boss takes at most this fraction of its max health from one baton hit, and is never knocked about. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	/** ... and at most this much from Cataclysm. */
	public static final float BOSS_MAX_FRACTION_CATACLYSM = 0.15f;

	// ---------------------------------------------------------------- R: Baton Strike (3-hit combo)
	public static final float STRIKE_DAMAGE = 9f;
	/** Ticks (from the press) the three swings land on. */
	public static final int[] STRIKE_HITS = { 3, 8, 13 };
	public static final int STRIKE_TICKS = 18;
	public static final double STRIKE_REACH = 4.0;
	/** Cosine of the half-angle of each swing's cone (about 60 degrees either side). */
	public static final double STRIKE_CONE = 0.5;
	public static final double STRIKE_KNOCKBACK = 0.4;
	/** The last swing (the overhead smash) knocks harder. */
	public static final double STRIKE_FINISHER_KNOCKBACK = 1.0;
	public static final int STRIKE_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+R: Low Sweep
	public static final float SWEEP_DAMAGE = 6f;
	public static final double SWEEP_RADIUS = 3.0;
	/** The arc in front: cosine of its half-angle (about 100 degrees either side -- a wide sweep). */
	public static final double SWEEP_CONE = -0.2;
	public static final int SWEEP_HIT_TICK = 4;
	public static final int SWEEP_TICKS = 12;
	/** Tripped: Slowness IV for 3 s, knocked off its feet (a small pop, its path cleared). */
	public static final int SWEEP_SLOW_TICKS = 60;
	public static final int SWEEP_SLOW_AMPLIFIER = 3;
	public static final int SWEEP_COOLDOWN = 60;

	// ---------------------------------------------------------------- G: Baton Extend
	public static final float EXTEND_DAMAGE = 14f;
	public static final double EXTEND_RANGE = 15.0;
	/** Ticks the staff takes to shoot out to full length; it holds, then pulls back. */
	public static final int EXTEND_OUT_TICKS = 4;
	public static final int EXTEND_HOLD_TICKS = 4;
	public static final int EXTEND_BACK_TICKS = 6;
	public static final double EXTEND_KNOCKBACK = 2.6;
	public static final double EXTEND_LIFT = 0.45;
	public static final int EXTEND_COOLDOWN = 80;

	// ---------------------------------------------------------------- Shift+G: Baton Throw
	public static final float THROW_DAMAGE = 12f;
	/** Blocks a tick, out and back. */
	public static final double THROW_SPEED = 1.5;
	public static final double THROW_RANGE = 22.0;
	/** The baton is lost to the void / an unloaded chunk after this long and simply comes back. */
	public static final int THROW_MAX_TICKS = 100;
	public static final double THROW_KNOCKBACK = 0.8;
	public static final int THROW_COOLDOWN = 100;

	// ---------------------------------------------------------------- X: Pole Vault (v0.16.6: held -- VAULT_HEIGHT / VAULT_FORWARD are no longer used by the move)
	/** Blocks straight up. */
	public static final double VAULT_HEIGHT = 15.0;
	/** A little carry along the look (blocks a tick of horizontal speed). */
	public static final double VAULT_FORWARD = 0.35;
	/** The baton must find the ground within this far below the feet. */
	public static final double VAULT_GROUND_RANGE = 4.0;
	/** Ticks crouched planting the baton before the launch. */
	public static final int VAULT_PLANT_TICKS = 4;
	/** Ticks the extended pole stays drawn from the plant point to the hands. */
	public static final int VAULT_POLE_TICKS = 22;
	/** v0.16.6 (user: hold X to keep extending, no limit): the baton grows this many blocks a tick while X is held, carrying him along his look. */
	public static final double VAULT_EXTEND_SPEED = 0.9;
	/** The look is tipped up to at least this (sine above the horizon) -- the pole always lifts him. */
	public static final double VAULT_MIN_RISE = 0.25;
	/** A tap still vaults: he is carried at least this long before the fling. */
	public static final int VAULT_MIN_EXTEND_TICKS = 8;
	/** Let go: flung along the look at this speed (blocks a tick), plus {@link #VAULT_RELEASE_LIFT} up. */
	public static final double VAULT_RELEASE_SPEED = 1.6;
	public static final double VAULT_RELEASE_LIFT = 0.25;
	/** The planted pole stays drawn this long after the fling. */
	public static final int VAULT_LINGER_TICKS = 8;
	public static final int VAULT_COOLDOWN = 120;

	// ---------------------------------------------------------------- Shift+X: Rooftop Run (toggle)
	/** Extra speed while sprinting, multiplied total (on top of the suit's +25%). */
	public static final double ROOFTOP_SPRINT_BONUS = 0.35;
	/** v0.16.7 (user): Rooftop Run step assist -- added to the 0.6 base step height for 2 blocks. */
	public static final double ROOFTOP_STEP_BONUS = 1.4;
	/** v0.16.7 (user): after his hands leave the top of a wall he keeps rising this long (0.2 blocks a tick) to get over it. */
	public static final int CLIMB_TOP_GRACE_TICKS = 6;
	public static final int ROOFTOP_TOGGLE_COOLDOWN = 20;

	// ---------------------------------------------------------------- Z: Baton Spin
	public static final float SPIN_DAMAGE = 8f;
	public static final double SPIN_RADIUS = 4.0;
	public static final int SPIN_TICKS = 40;
	/** A creature can be hit by one spin again after this many ticks (so about 3 hits in a full spin). */
	public static final int SPIN_REHIT_TICKS = 15;
	public static final double SPIN_KNOCKBACK = 1.1;
	/** Projectiles within this distance are batted back. */
	public static final double SPIN_DEFLECT_RADIUS = 4.0;
	public static final int SPIN_COOLDOWN = 100;

	// ---------------------------------------------------------------- Shift+Z: CATACLYSM
	/** The hand crackles this long waiting for a touch. */
	public static final int CATACLYSM_CHARGE_TICKS = 200;
	public static final float CATACLYSM_DAMAGE = 40f;
	/** Wither III for 8 s. */
	public static final int CATACLYSM_WITHER_TICKS = 160;
	public static final int CATACLYSM_WITHER_AMPLIFIER = 2;
	/** A touched block and everything joined to it within this radius crumbles. */
	public static final double CATACLYSM_RADIUS = 4.0;
	/** Ticks between rings of the crumble (one block further out each step: about 1 s to the edge). */
	public static final int CATACLYSM_STEP_TICKS = 5;
	/** Ticks a block cracks and darkens before it falls to dust. */
	public static final int CATACLYSM_CRACK_TICKS = 6;
	/** Hard limit of blocks one touch can crumble (a 4-block ball is about 270). */
	public static final int CATACLYSM_MAX_BLOCKS = 300;
	/** If the 10 s run out with nothing touched, Cataclysm can be called again after this long. */
	public static final int CATACLYSM_COOLDOWN = 400;

	// ---------------------------------------------------------------- V: Cat Leap
	public static final double LEAP_DISTANCE = 12.0;
	public static final int LEAP_TICKS = 18;
	public static final int LEAP_COOLDOWN = 60;

	// ---------------------------------------------------------------- Shift+V: Pounce Strike
	public static final float POUNCE_DAMAGE = 12f;
	public static final double POUNCE_RANGE = 24.0;
	/** The landing hits when Cat Noir comes within this distance of the target's box. */
	public static final double POUNCE_HIT_DISTANCE = 2.2;
	public static final int POUNCE_MAX_TICKS = 40;
	public static final double POUNCE_KNOCKBACK = 1.2;
	public static final int POUNCE_COOLDOWN = 100;

	// ---------------------------------------------------------------- C: Night Vision (toggle)
	/** Creatures within this distance are outlined (the owner's screen only). */
	public static final double NIGHT_VISION_RANGE = 20.0;
	/** The Night Vision effect is topped up to this length (it flickers under 200 ticks). */
	public static final int NIGHT_VISION_EFFECT_TICKS = 400;
	public static final int NIGHT_VISION_TOGGLE_COOLDOWN = 20;
	/** The outline colour: Plagg green. */
	public static final int NIGHT_VISION_COLOR = 0x7EE23A;

	// ---------------------------------------------------------------- Shift+C: Cat Reflexes
	public static final int REFLEX_DODGES = 3;
	public static final int REFLEX_TICKS = 100;
	/** The sidestep's push, blocks a tick. */
	public static final double REFLEX_SIDESTEP = 0.75;
	public static final int REFLEX_COOLDOWN = 300;
}
