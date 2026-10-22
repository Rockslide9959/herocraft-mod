package com.projecthero.mod.quirk.explosion;

/**
 * v0.16.7 (revamp): every Explosion (Bakugo / Ground Zero) number. Ticks unless the name says otherwise. Damage is at a
 * blast's centre with "normal" Sweat; {@link #powerAt} scales it (0.6x dry .. 1.4x soaked) and it falls off to half at
 * the blast's edge.
 *
 * <p>These are code constants, not a config file, so there is nothing on disk to migrate. Old save data (the
 * pre-revamp Blast Rush flight / smoke / Max Shot / Counter keys) is dropped by {@code ExplosionMoves.tick} on sight.
 */
public final class ExplosionConfig {
	private ExplosionConfig() {
	}

	// ---------------------------------------------------------------- the ability ids (kept ids keep their cooldown saves)
	// X: movement
	public static final String PROPULSION = "quirk_explosion_propulsion";
	public static final String DASH = "quirk_explosion_blast_dash";
	// V: utility
	public static final String STUN_GRENADE = "quirk_explosion_stun_grenade";
	public static final String COOL_OFF = "quirk_explosion_cool_off";
	// R / G / Z / C: attacks
	public static final String AP_SHOT = "quirk_explosion_ap_shot";
	public static final String AUTO_CANNON = "quirk_explosion_ap_auto_cannon";
	public static final String PUNCH = "quirk_explosion_blast_punch";
	public static final String BLAST_RUSH = "quirk_explosion_blast_rush";
	public static final String CLUSTER = "quirk_explosion_cluster";
	public static final String HOWITZER_CLUSTER = "quirk_explosion_howitzer_cluster";
	public static final String HOWITZER = "quirk_explosion_howitzer_impact";
	public static final String GAUNTLET = "quirk_explosion_gauntlet_grenade";

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
	/** v0.16.7 (user): the sweat builds on its own, 4 a second (0.2 a tick), on top of what moving / fighting adds. */
	public static final float SWEAT_NATURAL = 0.2f;
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


	// ---------------------------------------------------------------- X (hold): Explosion Propulsion / Shift+X: Explosion Dash
	/** Propulsion (client-simulated, like every flight): cruise / sprint along the look, blocks a tick. */
	// v0.16.7 (user): 22 blocks a second cruise (was 19); sprint scaled to match
	public static final double PROPEL_SPEED = 1.1;
	public static final double PROPEL_SPRINT_SPEED = 1.65;
	public static final double PROPEL_VERTICAL = 0.5;
	public static final double PROPEL_STRAFE = 0.8;
	public static final double PROPEL_ACCELERATION = 0.2;
	public static final double PROPEL_REVERSE = 0.3;
	/** No input: it eases toward a slowly sinking hover (the blasts can't hold him perfectly still). */
	public static final double PROPEL_IDLE = 0.12;
	public static final double PROPEL_IDLE_SINK = 0.06;
	/** A tick of flying burns this much Sweat (net of what flying sweats back: about 30 s from soaked); dry, it sputters out. */
	// v0.16.7 (user): 0.5 (was 0.3) so the natural 4/s sweat still leaves the same net drain -- about 30 s of flight from full
	public static final float PROPEL_SWEAT = 0.5f;
	public static final float PROPEL_MIN_SWEAT = 2f;
	public static final float PROPEL_STRAIN = 0.35f;
	/** A palm blast sound every this many ticks. */
	public static final int PROPEL_POP_INTERVAL = 3;
	public static final int PROPEL_COOLDOWN = 10;
	/** The longest one flight lasts (the Sweat or the strain usually end it first). */
	public static final int PROPEL_MAX_TICKS = 1200;
	public static final double DASH_DISTANCE = 12.0;
	public static final float DASH_DAMAGE = 4f;
	public static final double DASH_RADIUS = 2.0;
	public static final float DASH_SWEAT = 5f;
	public static final float DASH_STRAIN = 5f;
	public static final int DASH_COOLDOWN = 30;

	// ---------------------------------------------------------------- V: Stun Grenade / Shift+V: Cool Off
	public static final double STUN_RADIUS = 8.0;
	public static final float STUN_DAMAGE = 2f;
	public static final int STUN_BLIND_TICKS = 100;
	public static final int STUN_SLOW_TICKS = 60;
	public static final float STUN_SWEAT = 12f;
	public static final float STUN_STRAIN = 10f;
	public static final int STUN_COOLDOWN = 200;
	/** Cool Off: dry the sweat off (less Sweat, much less Arm Strain). Works while strained. */
	public static final float COOL_SWEAT = 40f;
	public static final float COOL_STRAIN = 50f;
	public static final int COOL_TICKS = 40;
	public static final int COOL_COOLDOWN = 300;

	// ---------------------------------------------------------------- G: Explosive Punch / Shift+G: Blast Rush
	public static final double PUNCH_REACH = 3.0;
	public static final float PUNCH_DAMAGE = 14f;
	public static final double PUNCH_RADIUS = 2.5;
	public static final double PUNCH_KNOCKBACK = 1.6;
	/** The punch carries him into it a little. */
	public static final double PUNCH_LUNGE = 0.45;
	public static final float PUNCH_SWEAT = 8f;
	public static final float PUNCH_STRAIN = 8f;
	public static final int PUNCH_COOLDOWN = 30;
	/** Blast Rush: a chain of blasts marching down a line along the ground, one every few ticks. */
	public static final int BLAST_RUSH_COUNT = 6;
	public static final int BLAST_RUSH_INTERVAL = 2;
	public static final double BLAST_RUSH_START = 2.0;
	public static final double BLAST_RUSH_STEP = 2.5;
	public static final float BLAST_RUSH_DAMAGE = 8f;
	public static final double BLAST_RUSH_RADIUS = 2.0;
	public static final double BLAST_RUSH_KNOCKBACK = 0.9;
	public static final float BLAST_RUSH_SWEAT = 10f;
	public static final float BLAST_RUSH_STRAIN = 14f;
	public static final int BLAST_RUSH_COOLDOWN = 100;

	// ---------------------------------------------------------------- Z: Cluster / Shift+Z: Howitzer Impact: Cluster
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

	// ---------------------------------------------------------------- C: Howitzer Impact / Shift+C: Gauntlet Grenade
	/** Needs at least this much Sweat; spends this fraction of what he has (at least the minimum). */
	public static final float HOWITZER_MIN_SWEAT = 30f;
	public static final float HOWITZER_SWEAT_FRACTION = 0.7f;
	public static final double HOWITZER_RANGE = 24.0;
	/** The rise: blasting himself up (from the ground) this long, this fast, spinning faster and faster. */
	public static final int HOWITZER_RISE_TICKS = 8;
	public static final double HOWITZER_RISE_SPEED = 0.85;
	/** The dive: at the target this fast (blocks a tick), a small blast in the vortex every {@link #HOWITZER_VORTEX_INTERVAL}. */
	public static final int HOWITZER_SPIN_TICKS = 36;
	public static final double HOWITZER_SPEED = 1.3;
	public static final int HOWITZER_VORTEX_INTERVAL = 2;
	public static final float HOWITZER_VORTEX_DAMAGE = 3f;
	public static final double HOWITZER_VORTEX_RADIUS = 3.0;
	/** It detonates this close to the target (or on the ground, or when the time runs out). */
	public static final double HOWITZER_ARRIVE = 2.5;
	public static final float HOWITZER_DAMAGE = 30f;
	public static final double HOWITZER_RADIUS = 6.0;
	public static final double HOWITZER_KNOCKBACK = 2.5;
	/** Degrees the body turns a tick while spinning (the render). */
	public static final float HOWITZER_SPIN_DEGREES = 60f;
	public static final float HOWITZER_STRAIN = 45f;
	public static final int HOWITZER_COOLDOWN = 400;
	/** Howitzer Impact: Cluster -- no rise, a quicker spin straight along the look, a smaller blast. */
	public static final float HCLUSTER_MIN_SWEAT = 20f;
	public static final float HCLUSTER_SWEAT = 20f;
	public static final int HCLUSTER_SPIN_TICKS = 14;
	public static final double HCLUSTER_SPEED = 1.2;
	public static final double HCLUSTER_RANGE = 14.0;
	public static final float HCLUSTER_DAMAGE = 18f;
	public static final double HCLUSTER_RADIUS = 4.0;
	public static final float HCLUSTER_STRAIN = 25f;
	public static final int HCLUSTER_COOLDOWN = 200;
	/** Gauntlet Grenade: every drop of stored sweat in one cannon blast down the aim. Needs a lot; big recoil. */
	public static final float GAUNTLET_MIN_SWEAT = 50f;
	public static final int GAUNTLET_CHARGE = 14;
	public static final double GAUNTLET_RANGE = 40.0;
	public static final float GAUNTLET_DAMAGE = 28f;
	/** Explosions down the line every this many blocks, this big. */
	public static final double GAUNTLET_STEP = 3.0;
	public static final double GAUNTLET_RADIUS = 3.0;
	public static final double GAUNTLET_KNOCKBACK = 2.0;
	public static final double GAUNTLET_RECOIL = 1.2;
	public static final float GAUNTLET_STRAIN = 55f;
	public static final int GAUNTLET_COOLDOWN = 500;

	/** Blast power at this much Sweat. */
	public static float powerAt(float sweat) {
		float t = Math.max(0f, Math.min(100f, sweat)) / 100f;
		return POWER_DRY + (POWER_SOAKED - POWER_DRY) * t;
	}
}
