package com.projecthero.mod.saiyan.moves;

/**
 * v0.17.3: the Saiyan moves' numbers. Damage is base-form damage; every hit is multiplied by the form's
 * {@code SaiyanForm.damageMult()} (Base 1.0 .. Blue 2.3, Overdrive 2.7). Ticks unless named otherwise (20 a second).
 *
 * <p>Scale: a Kryptonian punches for 32 and barrages 3/12, so base-form Saiyan moves sit at roughly 40-60% of that and only
 * the top forms (x2.3-2.7) reach and pass it. Bosses take at most {@link #BOSS_MAX_FRACTION} of their max health a hit.
 */
public final class SaiyanMoveConfig {
	private SaiyanMoveConfig() {
	}

	// left click: Saiyan combo -- three quick hits, then a launching kick
	public static final float COMBO_HIT_DAMAGE = 4f;
	public static final float COMBO_KICK_DAMAGE = 8f;
	public static final double COMBO_RANGE = 3.6;
	/** Fastest a combo hit can follow the last one. */
	public static final int COMBO_STEP_TICKS = 5;
	/** No hit for this long and the combo starts again from the first hit. */
	public static final int COMBO_RESET_TICKS = 24;
	/** After the kick: a short recovery before the next combo. */
	public static final int COMBO_RECOVERY = 12;
	public static final double COMBO_KICK_KNOCKBACK = 1.1, COMBO_KICK_LIFT = 0.55;

	// R: Feint Strike -- a stunning jab, then a guard-breaking kick
	public static final float FEINT_JAB_DAMAGE = 6f;
	public static final float FEINT_KICK_DAMAGE = 9f;
	public static final int FEINT_KICK_AT = 8;
	public static final int FEINT_STUN_TICKS = 16;
	/** A blocking player's shield goes down for this long. */
	public static final int FEINT_SHIELD_BREAK_TICKS = 100;
	public static final double FEINT_RANGE = 3.6;
	public static final float FEINT_KI = 8f;
	public static final int FEINT_COOLDOWN = 8 * 20;

	// G: Ki Beam -- hold to charge, let go to fire
	public static final int BEAM_MIN_CHARGE = 6;
	public static final int BEAM_MAX_CHARGE = 30;
	public static final int BEAM_FIRE_TICKS = 40;
	public static final int BEAM_HIT_EVERY = 4;
	/** Per hit at no charge; a full charge doubles it. Full charge, all ten hits: 30 at base form. */
	public static final float BEAM_HIT_DAMAGE = 1.5f;
	public static final double BEAM_RANGE = 32.0;
	public static final float BEAM_KI = 15f;
	public static final float BEAM_KI_PER_TICK = 0.4f;
	public static final int BEAM_COOLDOWN = 8 * 20;

	// Shift+G: Spirit Orb -- a long charge (rooted), a giant ball thrown overhead, base form only
	public static final int ORB_MIN_CHARGE = 30;
	public static final int ORB_MAX_CHARGE = 80;
	public static final float ORB_MIN_DAMAGE = 20f;
	public static final float ORB_MAX_DAMAGE = 45f;
	public static final float ORB_MIN_RADIUS = 4f;
	public static final float ORB_MAX_RADIUS = 7f;
	/** How big it looks over his head, at no charge and at full (blocks). */
	public static final float ORB_SIZE_MIN = 0.5f, ORB_SIZE_MAX = 3.0f;
	public static final double ORB_SPEED = 0.9;
	public static final int ORB_LIFE = 70;
	public static final float ORB_KI = 50f;
	public static final int ORB_COOLDOWN = 45 * 20;

	// Z: Barrage -- rapid punches, a knockback finisher
	public static final int BARRAGE_TICKS = 30;
	public static final int BARRAGE_HIT_EVERY = 2;
	public static final float BARRAGE_HIT_DAMAGE = 1.25f;
	public static final float BARRAGE_FINISHER_DAMAGE = 10f;
	public static final double BARRAGE_RANGE = 3.4;
	public static final double BARRAGE_KNOCKBACK = 1.5;
	public static final float BARRAGE_KI = 10f;
	public static final int BARRAGE_COOLDOWN = 7 * 20;

	// Shift+Z: Sky Combo -- kick them up, chase, two kicks in the air, smash them down
	public static final double SKY_RANGE = 5.0;
	public static final float SKY_LAUNCH_DAMAGE = 6f;
	public static final float SKY_AIR_DAMAGE = 5f;
	public static final float SKY_SMASH_DAMAGE = 12f;
	public static final float SKY_SHOCKWAVE_DAMAGE = 6f;
	public static final double SKY_SHOCKWAVE_RADIUS = 4.0;
	public static final int SKY_AIR_1 = 10, SKY_AIR_2 = 18, SKY_SMASH = 26, SKY_MAX = 60;
	public static final float SKY_KI = 15f;
	public static final int SKY_COOLDOWN = 14 * 20;
	/** No one in reach: a short cooldown so a whiff is not free spam. */
	public static final int SKY_WHIFF_COOLDOWN = 20;

	// C: Dragon Rush -- a wind-up, then a rushing punch, Super Saiyan 3 and up
	public static final int RUSH_WINDUP = 14;
	public static final int RUSH_TICKS = 10;
	public static final double RUSH_SPEED = 2.2;
	public static final float RUSH_DAMAGE = 26f;
	public static final double RUSH_KNOCKBACK = 2.4;
	public static final float RUSH_KI = 30f;
	public static final int RUSH_COOLDOWN = 15 * 20;

	// V: Instant Step -- teleport to where he looks (behind a creature he looks at)
	public static final double STEP_RANGE = 40.0;
	public static final double STEP_BEHIND = 1.6;
	public static final float STEP_KI = 10f;
	public static final int STEP_COOLDOWN = 5 * 20;

	// shared
	public static final float BOSS_MAX_FRACTION = 0.08f;
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
}
