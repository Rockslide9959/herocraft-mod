package com.projecthero.mod.miraculous.carapace;

/**
 * v0.16.6: every Carapace number (the Shield, the energy shell, the six keys). Ticks unless the name says otherwise. The
 * shared transformed stats (armour, health, melee, speed, jump, no fall damage, Regeneration I) come from the core
 * ({@code MiraculousStats}) and are not repeated here.
 */
public final class CarapaceConfig {
	private CarapaceConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String BASH = "carapace_shield_bash";
	public static final String THROW = "carapace_shield_throw";
	public static final String SLAM = "carapace_shield_slam";
	public static final String TAUNT = "carapace_taunt";
	public static final String SURF = "carapace_shield_surf";
	public static final String ROLL = "carapace_shell_roll";
	public static final String GUARD = "carapace_shell_guard";
	public static final String SHELTER = "carapace_shelter";
	public static final String BARRIER = "carapace_shell_barrier";
	public static final String PROTECT = "carapace_protect";
	public static final String STEADFAST = "carapace_steadfast";
	public static final String TIDAL = "carapace_tidal_swim";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most {@link #BOSS_MAX_FRACTION_PER_HIT} a hit, are never knocked about, stunned or taunted (a stun is a short slow on them). */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	/** "In front of him": within this dot of the flattened look (about 66 degrees either side). */
	public static final double FRONT_DOT = 0.4;

	// ---------------------------------------------------------------- R: Shield Bash / Shift+R: Shield Throw
	public static final double BASH_DISTANCE = 6.0;
	/** Blocks a tick while charging (the charge lasts distance / speed ticks). */
	public static final double BASH_SPEED = 1.0;
	public static final float BASH_DAMAGE = 12f;
	public static final double BASH_KNOCKBACK = 2.2;
	public static final double BASH_LIFT = 0.45;
	/** The first thing within this of the shield's face ends the charge. */
	public static final double BASH_REACH = 1.6;
	public static final int BASH_COOLDOWN = 100;
	public static final float THROW_DAMAGE = 10f;
	/** Targets the spinning shield bounces between before it comes home. */
	public static final int THROW_BOUNCES = 3;
	/** The first target: under the crosshair (aim assist this many degrees) within this range. */
	public static final double THROW_RANGE = 20.0;
	public static final double THROW_ASSIST_DEGREES = 7.0;
	/** The next target must be within this of the last one (and in sight). */
	public static final double THROW_BOUNCE_RANGE = 10.0;
	/** Blocks a tick out, between targets and home. */
	public static final double THROW_SPEED = 1.5;
	/** It gives up and snaps back to his arm after this long. */
	public static final int THROW_MAX_TICKS = 100;
	/** Starts when the shield is caught. */
	public static final int THROW_COOLDOWN = 120;

	// ---------------------------------------------------------------- G: Shield Slam / Shift+G: Taunt
	public static final double SLAM_RADIUS = 5.0;
	public static final float SLAM_DAMAGE = 10f;
	public static final int SLAM_STUN_TICKS = 30;
	public static final double SLAM_LIFT = 0.35;
	public static final int SLAM_COOLDOWN = 160;
	public static final double TAUNT_RADIUS = 16.0;
	public static final int TAUNT_TICKS = 6 * 20;
	/** Damage taken while the Taunt runs is multiplied by this (30% less). */
	public static final float TAUNT_DAMAGE_TAKEN = 0.7f;
	public static final int TAUNT_COOLDOWN = 400;

	// ---------------------------------------------------------------- X: Shield Surf / Shift+X: Shell Roll
	/** Surfing speed (blocks a tick) over ground, snow and water; ice is faster. */
	public static final double SURF_SPEED = 0.6;
	public static final double SURF_ICE_SPEED = 0.85;
	/** Extra step height while surfing (it rides up slabs and single blocks). */
	public static final double SURF_STEP = 0.6;
	/** Starts when the surf ends (X again, a move that needs the shield, or detransforming). */
	public static final int SURF_COOLDOWN = 40;
	public static final double ROLL_DISTANCE = 10.0;
	public static final double ROLL_SPEED = 0.9;
	/** No damage at all while rolling (plus a few ticks to get up). */
	public static final int ROLL_GRACE = 4;
	public static final int ROLL_COOLDOWN = 160;

	// ---------------------------------------------------------------- Z: Shell Guard (held) / Shift+Z: Shelter
	/** The guard drops by itself after this long held. */
	public static final int GUARD_MAX_TICKS = 10 * 20;
	/** Projectiles within this of his chest, in front, are sent back the way they came. */
	public static final double GUARD_REACH = 2.6;
	/** Melee attackers in front are shoved back this hard. */
	public static final double GUARD_KNOCKBACK = 0.8;
	/** Starts when the guard drops. */
	public static final int GUARD_COOLDOWN = 60;
	public static final double SHELTER_RADIUS = 6.0;
	public static final int SHELTER_TICKS = 30 * 20;
	/** Regeneration (amplifier) for him and his squadmates inside, refreshed every second. */
	public static final int SHELTER_REGEN_AMP = 1;
	/** The raise animation before the dome stands. */
	public static final int SHELTER_RAISE_TICKS = 12;
	public static final int SHELTER_COOLDOWN = 2400;

	// ---------------------------------------------------------------- V: Shell Barrier / Shift+V: Protect
	public static final int BARRIER_WIDTH = 5;
	public static final int BARRIER_HEIGHT = 3;
	public static final double BARRIER_DISTANCE = 3.0;
	public static final int BARRIER_TICKS = 10 * 20;
	public static final int BARRIER_COOLDOWN = 400;
	/** The shield bubble soaks this much damage (Absorption: 10 hearts). */
	public static final float PROTECT_ABSORB = 20f;
	public static final int PROTECT_TICKS = 15 * 20;
	/** He can put it on a player he looks at this far away (else on himself). */
	public static final double PROTECT_RANGE = 24.0;
	public static final int PROTECT_COOLDOWN = 600;

	// ---------------------------------------------------------------- C: Steadfast / Shift+C: Tidal Swim
	public static final int STEADFAST_TICKS = 8 * 20;
	/** Resistance amplifier (1 = Resistance II). */
	public static final int STEADFAST_RESISTANCE_AMP = 1;
	public static final int STEADFAST_COOLDOWN = 400;
	public static final int TIDAL_TICKS = 60 * 20;
	/** Extra water movement efficiency (swimming like a turtle). */
	public static final double TIDAL_SWIM_BONUS = 1.0;
	public static final int TIDAL_COOLDOWN = 1800;
}
