package com.projecthero.mod.quirk.hellflame;

/**
 * v0.16.7: every Hellflame (Endeavor) number. Ticks unless the name says otherwise; Heat is the Quirk meter (0..100).
 */
public final class HellflameConfig {
	private HellflameConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String SPIDER = "quirk_hellflame_hell_spider";
	public static final String NET = "quirk_hellflame_hell_spider_net";
	public static final String JET = "quirk_hellflame_jet_burn";
	public static final String BEAM = "quirk_hellflame_hellflame_beam";
	public static final String BOOST = "quirk_hellflame_flame_boost";
	public static final String DODGE = "quirk_hellflame_burst_dodge";
	public static final String FIST = "quirk_hellflame_flashfire_fist";
	public static final String UPPERCUT = "quirk_hellflame_flashfire_uppercut";
	public static final String PROMINENCE = "quirk_hellflame_prominence_burn";
	public static final String SUN = "quirk_hellflame_hellflame_sun";
	public static final String CLOAK = "quirk_hellflame_flame_cloak";
	public static final String VENT = "quirk_hellflame_vent";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most this fraction of their max health a hit, and no knockback. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- Heat / Overheat
	/** Heat vents this much a tick (4 / s) once no fire move has run for {@link #VENT_DELAY}. */
	public static final float HEAT_VENT_PER_TICK = 0.2f;
	public static final int VENT_DELAY = 30;
	/** In water or rain the Heat vents this many times faster. */
	public static final float HEAT_VENT_WET_MULT = 2.5f;
	/** Overheat at 100 Heat: this long, Slowness II, a short self-burn, no fire moves (Shift+C still works). */
	public static final int OVERHEAT_TICKS = 160;
	public static final int OVERHEAT_SELF_BURN_SECONDS = 2;
	/** While overheated the Heat drains this much a tick (100 -> ~36 over the 8 s). */
	public static final float OVERHEAT_VENT_PER_TICK = 0.4f;

	// ---------------------------------------------------------------- R: Hell Spider / Shift+R: Hell Spider Net
	public static final double SPIDER_RANGE = 16.0;
	public static final int SPIDER_THREADS = 5;
	/** Degrees between neighbouring threads. */
	public static final double SPIDER_SPREAD_DEGREES = 7.0;
	/** A target this close to a thread is sliced (once, however many threads cross it). */
	public static final double SPIDER_THREAD_RADIUS = 0.8;
	public static final float SPIDER_DAMAGE = 9f;
	public static final int SPIDER_BURN_SECONDS = 4;
	public static final float SPIDER_HEAT = 12f;
	public static final int SPIDER_COOLDOWN = 40;

	public static final double NET_RANGE = 24.0;
	public static final double NET_RADIUS = 3.5;
	public static final int NET_ROOT_TICKS = 60;
	/** Burn damage every {@link #NET_BURN_INTERVAL} while rooted. */
	public static final float NET_BURN_DAMAGE = 2.5f;
	public static final int NET_BURN_INTERVAL = 10;
	public static final float NET_HEAT = 20f;
	public static final int NET_COOLDOWN = 240;

	// ---------------------------------------------------------------- G: Jet Burn / Shift+G: Hellflame Beam (hold)
	public static final double JET_RANGE = 7.0;
	/** Half-angle of the cone, degrees. */
	public static final double JET_HALF_ANGLE = 30.0;
	public static final float JET_DAMAGE = 10f;
	public static final double JET_KNOCKBACK = 0.7;
	public static final int JET_BURN_SECONDS = 5;
	public static final float JET_HEAT = 10f;
	public static final int JET_COOLDOWN = 30;

	public static final double BEAM_RANGE = 32.0;
	public static final double BEAM_RADIUS = 0.9;
	/** Damage every {@link #BEAM_HIT_INTERVAL} ticks to everything on the beam (15 / s). */
	public static final float BEAM_DAMAGE = 3f;
	public static final int BEAM_HIT_INTERVAL = 4;
	public static final float BEAM_HEAT_PER_TICK = 1.2f;
	/** The beam gives out on its own after this long (or at Overheat). */
	public static final int BEAM_MAX_TICKS = 100;
	public static final int BEAM_COOLDOWN = 120;

	// ---------------------------------------------------------------- X: Flame Boost (hold) / Shift+X: Burst Dodge
	/** Flight cruise, blocks a tick (14 b/s); Sprint makes it {@link #BOOST_SPRINT_SPEED}. */
	public static final double BOOST_SPEED = 0.7;
	public static final double BOOST_SPRINT_SPEED = 1.15;
	public static final double BOOST_VERTICAL = 0.45;
	/** Fraction of the gap to the wanted velocity closed a tick. */
	public static final double BOOST_ACCEL = 0.22;
	public static final float BOOST_HEAT_PER_TICK = 0.25f;
	public static final int BOOST_COOLDOWN = 20;

	public static final double DODGE_DISTANCE = 8.0;
	public static final double DODGE_RADIUS = 2.5;
	public static final float DODGE_DAMAGE = 5f;
	public static final float DODGE_HEAT = 8f;
	public static final int DODGE_COOLDOWN = 60;

	// ---------------------------------------------------------------- Z: Flashfire Fist / Shift+Z: Flashfire Uppercut
	/** Dash speed, blocks a tick, for {@link #FIST_DASH_TICKS} (about 10 blocks). */
	public static final double FIST_DASH_SPEED = 1.7;
	public static final int FIST_DASH_TICKS = 7;
	/** Whatever is this close to the fist during the dash is punched (and the dash ends). */
	public static final double FIST_REACH = 1.9;
	public static final float FIST_DAMAGE = 18f;
	public static final double FIST_KNOCKBACK = 1.8;
	public static final int FIST_BURN_SECONDS = 5;
	public static final float FIST_HEAT = 15f;
	public static final int FIST_COOLDOWN = 120;
	public static final float UPPERCUT_DAMAGE = 15f;
	/** The uppercut throws the target up this fast (blocks a tick: ~8 blocks high). */
	public static final double UPPERCUT_LIFT = 1.35;
	public static final float UPPERCUT_HEAT = 15f;
	public static final int UPPERCUT_COOLDOWN = 140;

	// ---------------------------------------------------------------- V: Prominence Burn (hold) / Shift+V: Hellflame Sun
	/** Full charge after this long; still held this much longer and it fires by itself. */
	public static final int PROMINENCE_CHARGE_TICKS = 60;
	public static final int PROMINENCE_AUTO_FIRE_EXTRA = 40;
	/** Radius / damage / Heat at no charge, plus the extra at full charge. */
	public static final double PROMINENCE_RADIUS = 3.0;
	public static final double PROMINENCE_RADIUS_EXTRA = 4.0;
	public static final float PROMINENCE_DAMAGE = 10f;
	public static final float PROMINENCE_DAMAGE_EXTRA = 22f;
	public static final float PROMINENCE_HEAT = 20f;
	public static final float PROMINENCE_HEAT_EXTRA = 40f;
	public static final double PROMINENCE_HEIGHT = 9.0;
	public static final double PROMINENCE_LIFT = 0.9;
	public static final int PROMINENCE_BURN_SECONDS = 6;
	public static final int PROMINENCE_COOLDOWN = 300;

	public static final int SUN_TICKS = 120;
	/** The sun hangs this far above his head and follows him. */
	public static final double SUN_HEIGHT = 5.0;
	public static final double SUN_RANGE = 14.0;
	/** A fireball every this many ticks at the nearest threat (aimed at one not yet hit hardest first). */
	public static final int SUN_INTERVAL = 8;
	/** How long a fireball falls. */
	public static final int SUN_FALL_TICKS = 8;
	public static final float SUN_DAMAGE = 6f;
	public static final double SUN_SPLASH = 1.6;
	public static final int SUN_BURN_SECONDS = 3;
	public static final float SUN_HEAT = 35f;
	public static final int SUN_COOLDOWN = 800;

	// ---------------------------------------------------------------- C: Flame Cloak (toggle) / Shift+C: Vent
	public static final float CLOAK_HEAT_PER_TICK = 0.1f;
	public static final int CLOAK_IGNITE_SECONDS = 4;
	/** An attacker within this distance is set alight. */
	public static final double CLOAK_RETALIATE_RANGE = 5.0;
	public static final int CLOAK_COOLDOWN = 20;

	public static final float VENT_BASE_DAMAGE = 4f;
	/** Extra damage per Heat point dumped (100 Heat: 4 + 22 = 26). */
	public static final float VENT_DAMAGE_PER_HEAT = 0.22f;
	public static final double VENT_BASE_RADIUS = 3.0;
	/** Extra radius per Heat point (100 Heat: 3 + 5 = 8 blocks). */
	public static final double VENT_RADIUS_PER_HEAT = 0.05;
	public static final double VENT_KNOCKBACK = 1.2;
	public static final int VENT_BURN_SECONDS = 4;
	public static final int VENT_COOLDOWN = 400;
}
