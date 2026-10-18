package com.projecthero.mod.quirk.explosion;

/**
 * v0.16.7: every Explosion (Bakugo / Ground Zero) number. Ticks unless the name says otherwise. Damage is at a blast's
 * centre with "normal" Sweat; {@link #powerAt} scales it (0.6x dry .. 1.4x soaked) and it falls off to half at the
 * blast's edge.
 */
public final class ExplosionConfig {
	private ExplosionConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String AP_SHOT = "quirk_explosion_ap_shot";
	public static final String AUTO_CANNON = "quirk_explosion_ap_auto_cannon";
	public static final String BLAST_PUNCH = "quirk_explosion_blast_punch";
	public static final String UPPERCUT = "quirk_explosion_uppercut";
	public static final String STUN_GRENADE = "quirk_explosion_stun_grenade";
	public static final String SMOKE_BLAST = "quirk_explosion_smoke_blast";
	public static final String BLAST_RUSH = "quirk_explosion_blast_rush";
	public static final String BLAST_DASH = "quirk_explosion_blast_dash";
	public static final String HOWITZER = "quirk_explosion_howitzer_impact";
	public static final String MAX_SHOT = "quirk_explosion_max_shot";
	public static final String CLUSTER = "quirk_explosion_cluster";
	public static final String COUNTER = "quirk_explosion_explosive_counter";

	// ---------------------------------------------------------------- shared
	/** Bosses ({@code TitanCombat.isBoss} or this much max health) take at most this fraction of max health a hit and are never knocked about. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	/** Blast power at 0 Sweat and at 100 Sweat (linear between). */
	public static final float POWER_DRY = 0.6f;
	public static final float POWER_SOAKED = 1.4f;
	/** Damage at the very edge of a blast, as a fraction of its centre damage. */
	public static final float EDGE_FALLOFF = 0.5f;
	/** mobGriefing on (and the terrain-damage config): a medium blast breaks at most this many soft blocks, a big one {@link #GRIEF_BIG_BLOCKS}. */
	public static final int GRIEF_MEDIUM_BLOCKS = 2;
	public static final int GRIEF_BIG_BLOCKS = 6;
	/** Only blocks this soft or softer break (dirt 0.5, sand 0.5, grass 0.6, glass 0.3; stone 1.5 never). */
	public static final float GRIEF_MAX_HARDNESS = 0.8f;
	/** After one of his own launches, fall / wall damage is ignored until he lands, at most this long. */
	public static final int SOFT_LANDING_MAX = 200;

	// ---------------------------------------------------------------- Sweat (the meter, 0..100)
	/** A tick of walking / sprinting / being in a fight. */
	public static final float SWEAT_WALK = 0.08f;
	public static final float SWEAT_SPRINT = 0.14f;
	public static final float SWEAT_COMBAT = 0.08f;
	/** Each hit he lands / takes. */
	public static final float SWEAT_HIT_DEALT = 2.0f;
	public static final float SWEAT_HIT_TAKEN = 3.0f;
	/** Hits he lands add Sweat at most once this often. */
	public static final int HIT_SWEAT_INTERVAL = 10;
	/** "In a fight" for this long after a hit either way. */
	public static final int COMBAT_TICKS = 100;
	/** Hot biomes (base temperature at least {@link #HOT_TEMPERATURE}) / the Nether / standing in fire multiply every gain. */
	public static final float HOT_TEMPERATURE = 1.5f;
	public static final float HOT_MULTIPLIER = 1.75f;
	public static final float NETHER_MULTIPLIER = 2.0f;
	public static final float BURNING_MULTIPLIER = 2.0f;
	/** Cold biomes (base temperature below this) sweat slower. */
	public static final float COLD_TEMPERATURE = 0.15f;
	public static final float COLD_MULTIPLIER = 0.6f;
	/** Standing still out of a fight for {@link #DRY_DELAY}: it dries off this much a tick; water / rain wash it off faster. */
	public static final int DRY_DELAY = 100;
	public static final float SWEAT_DRY = 0.03f;
	public static final float SWEAT_WASH = 0.35f;

	// ---------------------------------------------------------------- Arm Strain (hidden, 0..100)
	/** Full strain: Arm Strain for this long (Weakness II, no moves). */
	public static final int STRAIN_TICKS = 100;
	public static final int STRAIN_WEAKNESS_AMP = 1;
	/** It eases off this much a tick once he has not blasted for {@link #STRAIN_DECAY_DELAY}. */
	public static final int STRAIN_DECAY_DELAY = 40;
	public static final float STRAIN_DECAY = 0.35f;
	/** Above this the arms smoke (a warning). */
	public static final float STRAIN_WARN = 70f;

	// ---------------------------------------------------------------- R: AP Shot / Shift+R: AP Shot Auto-Cannon
	public static final double AP_RANGE = 32.0;
	public static final float AP_DAMAGE = 10f;
	public static final double AP_RADIUS = 2.0;
	public static final double AP_KNOCKBACK = 1.0;
	public static final float AP_SWEAT = 6f;
	public static final float AP_STRAIN = 6f;
	public static final int AP_COOLDOWN = 20;
	public static final int AUTO_SHOTS = 8;
	/** A shot every this many ticks. */
	public static final int AUTO_INTERVAL = 2;
	public static final double AUTO_RANGE = 28.0;
	public static final float AUTO_DAMAGE = 4f;
	public static final double AUTO_RADIUS = 1.4;
	public static final double AUTO_SPREAD_DEGREES = 2.5;
	public static final float AUTO_SWEAT = 1.5f;
	public static final float AUTO_STRAIN = 2f;
	public static final int AUTO_COOLDOWN = 80;

	// ---------------------------------------------------------------- G: Blast Punch / Shift+G: Explosion Uppercut
	public static final double PUNCH_REACH = 3.0;
	public static final float PUNCH_DAMAGE = 14f;
	public static final double PUNCH_RADIUS = 2.5;
	public static final double PUNCH_KNOCKBACK = 1.6;
	public static final float PUNCH_SWEAT = 8f;
	public static final float PUNCH_STRAIN = 8f;
	public static final int PUNCH_COOLDOWN = 30;
	public static final double UPPERCUT_REACH = 3.5;
	public static final float UPPERCUT_DAMAGE = 12f;
	public static final double UPPERCUT_RADIUS = 2.0;
	/** The launch straight up (blocks a tick). */
	public static final double UPPERCUT_LIFT = 1.3;
	public static final float UPPERCUT_SWEAT = 8f;
	public static final float UPPERCUT_STRAIN = 8f;
	public static final int UPPERCUT_COOLDOWN = 50;

	// ---------------------------------------------------------------- X: Stun Grenade / Shift+X: Smoke Blast
	public static final double STUN_RADIUS = 8.0;
	public static final float STUN_DAMAGE = 2f;
	public static final int STUN_BLIND_TICKS = 100;
	public static final int STUN_SLOW_TICKS = 60;
	public static final float STUN_SWEAT = 12f;
	public static final float STUN_STRAIN = 10f;
	public static final int STUN_COOLDOWN = 200;
	public static final double SMOKE_RADIUS = 6.0;
	public static final int SMOKE_TICKS = 160;
	/** Inside the smoke a creature is blinded this long (refreshed) and can't keep a target. */
	public static final int SMOKE_BLIND_TICKS = 30;
	public static final float SMOKE_SWEAT = 10f;
	public static final float SMOKE_STRAIN = 4f;
	public static final int SMOKE_COOLDOWN = 300;

	// ---------------------------------------------------------------- Z: Blast Rush / Shift+Z: Blast Dash
	/** Blast Rush (client-simulated, like every flight): cruise / sprint along the look, blocks a tick. */
	public static final double RUSH_SPEED = 0.95;
	public static final double RUSH_SPRINT_SPEED = 1.4;
	public static final double RUSH_VERTICAL = 0.45;
	public static final double RUSH_STRAFE = 0.8;
	public static final double RUSH_ACCELERATION = 0.2;
	public static final double RUSH_REVERSE = 0.3;
	/** No input: it eases toward a slowly sinking hover (the blasts can't hold him perfectly still). */
	public static final double RUSH_IDLE = 0.12;
	public static final double RUSH_IDLE_SINK = 0.06;
	/** A tick of rushing. */
	public static final float RUSH_SWEAT = 0.2f;
	public static final float RUSH_STRAIN = 0.4f;
	/** A palm pop sound every this many ticks. */
	public static final int RUSH_POP_INTERVAL = 3;
	public static final int RUSH_COOLDOWN = 20;
	/** The longest one Rush can last (the strain usually ends it first). */
	public static final int RUSH_MAX_TICKS = 400;
	public static final double DASH_DISTANCE = 12.0;
	public static final float DASH_DAMAGE = 4f;
	public static final double DASH_RADIUS = 2.0;
	public static final float DASH_SWEAT = 5f;
	public static final float DASH_STRAIN = 5f;
	public static final int DASH_COOLDOWN = 30;

	// ---------------------------------------------------------------- V: Howitzer Impact / Shift+V: Max Shot
	/** Needs at least this much Sweat; spends this fraction of what he has (at least the minimum). */
	public static final float HOWITZER_MIN_SWEAT = 30f;
	public static final float HOWITZER_SWEAT_FRACTION = 0.7f;
	public static final double HOWITZER_RANGE = 24.0;
	/** The spin: this long, flying at the target this fast (blocks a tick), a small blast in the vortex every {@link #HOWITZER_VORTEX_INTERVAL}. */
	public static final int HOWITZER_SPIN_TICKS = 24;
	public static final double HOWITZER_SPEED = 1.1;
	public static final int HOWITZER_VORTEX_INTERVAL = 2;
	public static final float HOWITZER_VORTEX_DAMAGE = 3f;
	public static final double HOWITZER_VORTEX_RADIUS = 3.0;
	/** It detonates this close to the target (or when the spin runs out). */
	public static final double HOWITZER_ARRIVE = 2.5;
	public static final float HOWITZER_DAMAGE = 30f;
	public static final double HOWITZER_RADIUS = 6.0;
	public static final double HOWITZER_KNOCKBACK = 2.5;
	/** Degrees the body turns a tick while spinning. */
	public static final float HOWITZER_SPIN_DEGREES = 60f;
	public static final float HOWITZER_STRAIN = 45f;
	public static final int HOWITZER_COOLDOWN = 400;
	public static final float MAX_SHOT_MIN_SWEAT = 40f;
	public static final int MAX_SHOT_CHARGE = 12;
	public static final double MAX_SHOT_RANGE = 40.0;
	public static final float MAX_SHOT_DAMAGE = 26f;
	/** Explosions down the line every this many blocks, this big. */
	public static final double MAX_SHOT_STEP = 3.0;
	public static final double MAX_SHOT_RADIUS = 3.0;
	public static final double MAX_SHOT_KNOCKBACK = 2.0;
	public static final double MAX_SHOT_RECOIL = 0.9;
	public static final float MAX_SHOT_STRAIN = 50f;
	public static final int MAX_SHOT_COOLDOWN = 500;

	// ---------------------------------------------------------------- C: Cluster / Shift+C: Explosive Counter
	public static final double CLUSTER_INNER = 3.0;
	public static final double CLUSTER_OUTER = 5.5;
	/** Every creature within {@link #CLUSTER_OUTER} + a margin is hit once. */
	public static final float CLUSTER_DAMAGE = 9f;
	public static final double CLUSTER_KNOCKBACK = 1.4;
	public static final int CLUSTER_RING_COUNT = 8;
	public static final int CLUSTER_RING_DELAY = 3;
	public static final float CLUSTER_SWEAT = 10f;
	public static final float CLUSTER_STRAIN = 12f;
	public static final int CLUSTER_COOLDOWN = 80;
	public static final int COUNTER_WINDOW = 40;
	/** An attacker this close counts as a melee hit. */
	public static final double COUNTER_REACH = 5.0;
	public static final float COUNTER_DAMAGE = 14f;
	public static final double COUNTER_RADIUS = 2.0;
	public static final double COUNTER_KNOCKBACK = 1.8;
	public static final float COUNTER_SWEAT = 6f;
	public static final float COUNTER_STRAIN = 6f;
	public static final int COUNTER_COOLDOWN = 160;

	/** Blast power at this much Sweat. */
	public static float powerAt(float sweat) {
		float t = Math.max(0f, Math.min(100f, sweat)) / 100f;
		return POWER_DRY + (POWER_SOAKED - POWER_DRY) * t;
	}
}
