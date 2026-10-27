package com.projecthero.mod.quirk.explosion;

/**
 * v0.16.7 (revamp), v0.17.1 (the Quirks revamp, {@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.6): every Explosion (Bakugo /
 * Ground Zero) number. Ticks unless the name says otherwise. Damage is at a blast's centre in the Primed band; the Sweat
 * band scales it ({@link #powerAt}: Dry x0.6, Primed x1.0, Soaked x1.4), strained arms add {@link #STRAIN_HOT_BONUS}, and it
 * falls off to half at the blast's edge.
 *
 * <p>Arm Strain costs (the {@code *_STRAIN} numbers) are RAW: {@link ExplosionMeter#BUILD} paces every one of them (the
 * user: "i dont want like heat or darkness or whatever to build up too quick"); the pacing gametest drives the same numbers.
 *
 * <p>These are code constants, not a config file, so there is nothing on disk to migrate. Old save data (the pre-revamp
 * Blast Rush flight / smoke / Max Shot / Counter keys) is dropped by {@code ExplosionMoves.tick} on sight.
 */
public final class ExplosionConfig {
	private ExplosionConfig() {
	}

	// ---------------------------------------------------------------- the ability ids (kept ids keep their cooldown saves)
	// X: movement (tap / Shift / hold)
	public static final String DASH = "quirk_explosion_blast_dash";
	public static final String SPIN_DODGE = "quirk_explosion_spin_dodge";
	public static final String PROPULSION = "quirk_explosion_propulsion";
	// V: utility
	public static final String STUN_GRENADE = "quirk_explosion_stun_grenade";
	public static final String COOL_OFF = "quirk_explosion_cool_off";
	// R / G / Z / C: attacks
	public static final String AP_SHOT = "quirk_explosion_ap_shot";
	public static final String AUTO_CANNON = "quirk_explosion_ap_auto_cannon";
	public static final String PIERCE = "quirk_explosion_ap_pierce";
	public static final String PUNCH = "quirk_explosion_blast_punch";
	public static final String BLAST_RUSH = "quirk_explosion_blast_rush";
	public static final String CRUSH_GRIP = "quirk_explosion_crush_grip";
	public static final String CLUSTER = "quirk_explosion_cluster";
	public static final String UPPERCUT = "quirk_explosion_uppercut";
	public static final String HOWITZER = "quirk_explosion_howitzer_impact";
	public static final String GAUNTLET = "quirk_explosion_gauntlet_grenade";
	/** The Plus Ultra ultimate (hold C at a full gauge): Howitzer Impact: Cluster. Not a key move: no cooldown of its own. */
	public static final String HOWITZER_CLUSTER = "quirk_explosion_howitzer_cluster";

	// ---------------------------------------------------------------- shared
	/** A key held this long (ticks) is its HOLD move; released sooner it is the TAP move (which fires on the release). */
	public static final int HOLD_TICKS = 6;
	/** Bosses ({@code TitanCombat.isBoss} or this much max health) take at most this fraction of max health a hit and are never knocked about. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	/** Blast power per Sweat band (Dry 0-29 / Primed 30-79 / Soaked 80-100). */
	public static final float POWER_DRY = 0.6f;
	public static final float POWER_PRIMED = 1.0f;
	public static final float POWER_SOAKED = 1.4f;
	public static final float PRIMED_FROM = 30f;
	public static final float SOAKED_FROM = 80f;
	/** Damage at the very edge of a blast, as a fraction of its centre damage. */
	public static final float EDGE_FALLOFF = 0.5f;
	/** mobGriefing on (and the terrain-damage config): a medium blast breaks at most this many soft blocks, a big one {@link #GRIEF_BIG_BLOCKS}. */
	public static final int GRIEF_MEDIUM_BLOCKS = 2;
	public static final int GRIEF_BIG_BLOCKS = 6;
	/** Only blocks this soft or softer break (dirt 0.5, sand 0.5, grass 0.6, glass 0.3; stone 1.5 never). */
	public static final float GRIEF_MAX_HARDNESS = 0.8f;
	/** After one of his own launches, fall / wall damage is ignored until he lands, at most this long. */
	public static final int SOFT_LANDING_MAX = 200;

	// ---------------------------------------------------------------- Sweat (the meter, 0..100: fuel, not paced)
	/** v0.16.7 (user): the sweat builds on its own, 4 a second (0.2 a tick), on top of what moving / fighting adds. */
	public static final float SWEAT_NATURAL = 0.2f;
	public static final float SWEAT_WALK = 0.08f;
	public static final float SWEAT_SPRINT = 0.14f;
	public static final float SWEAT_COMBAT = 0.08f;
	public static final float SWEAT_HIT_DEALT = 2.0f;
	public static final float SWEAT_HIT_TAKEN = 3.0f;
	public static final int HIT_SWEAT_INTERVAL = 10;
	public static final int COMBAT_TICKS = 100;
	public static final float HOT_TEMPERATURE = 1.5f;
	public static final float HOT_MULTIPLIER = 1.75f;
	public static final float NETHER_MULTIPLIER = 2.0f;
	public static final float BURNING_MULTIPLIER = 2.0f;
	public static final float COLD_TEMPERATURE = 0.15f;
	public static final float COLD_MULTIPLIER = 0.6f;
	public static final int DRY_DELAY = 100;
	public static final float SWEAT_DRY = 0.03f;
	public static final float SWEAT_WASH = 0.35f;

	// ---------------------------------------------------------------- the Ground Zero gauntlets (full suit set perk)
	/** In the full suit, the gauntlets bank Sweat (up to this) for the Gauntlet Grenade... */
	public static final float BANK_MAX = 50f;
	/** ...this much a tick while the Sweat is Soaked (a full bank in about 12.5 s of Soaked sweating). */
	public static final float BANK_FILL = 0.2f;

	// ---------------------------------------------------------------- Arm Strain (0..100, the pacing / danger channel)
	/** At 100 the arms are shot: this long with no blasts and Weakness II. */
	public static final int STRAIN_TICKS = 120;
	public static final int STRAIN_WEAKNESS_AMP = 1;
	/** It eases off this much a tick once he has not blasted for {@link #STRAIN_DECAY_DELAY}. */
	public static final int STRAIN_DECAY_DELAY = 40;
	public static final float STRAIN_DECAY = 0.35f;
	/** From here the arms smoke, every blast is {@link #STRAIN_HOT_BONUS} stronger and costs {@link #STRAIN_HOT_HP} HP. */
	public static final float STRAIN_WARN = 70f;
	public static final float STRAIN_HOT_BONUS = 1.15f;
	public static final float STRAIN_HOT_HP = 0.5f;

	// ---------------------------------------------------------------- R: AP Shot / Shift+R: Auto-Cannon / hold R: Pierce
	public static final double AP_RANGE = 32.0;
	public static final float AP_DAMAGE = 10f;
	public static final double AP_RADIUS = 2.0;
	public static final double AP_KNOCKBACK = 1.0;
	public static final float AP_SWEAT = 6f;
	public static final float AP_STRAIN = 6f;
	public static final int AP_COOLDOWN = 20;
	public static final int AUTO_SHOTS = 8;
	public static final int AUTO_INTERVAL = 2;
	public static final double AUTO_RANGE = 28.0;
	public static final float AUTO_DAMAGE = 4f;
	public static final double AUTO_RADIUS = 1.4;
	public static final double AUTO_SPREAD_DEGREES = 2.5;
	public static final float AUTO_SWEAT = 1.5f;
	public static final float AUTO_STRAIN = 2f;
	public static final int AUTO_COOLDOWN = 80;
	/** Pierce: charges while R is held (up to {@link #PIERCE_CHARGE_TICKS}), fires on the release (or when full). */
	public static final int PIERCE_CHARGE_TICKS = 30;
	public static final double PIERCE_RANGE = 48.0;
	/** Damage from a bare charge to a full one. */
	public static final float PIERCE_MIN_DAMAGE = 8f;
	public static final float PIERCE_MAX_DAMAGE = 22f;
	public static final double PIERCE_RADIUS = 0.7;
	public static final float PIERCE_SWEAT = 10f;
	public static final float PIERCE_STRAIN = 14f;
	public static final int PIERCE_COOLDOWN = 120;

	// ---------------------------------------------------------------- X: Dash / Shift+X: Spin Dodge / hold X: Propulsion
	public static final double PROPEL_SPEED = 1.1;
	public static final double PROPEL_SPRINT_SPEED = 1.65;
	public static final double PROPEL_VERTICAL = 0.5;
	public static final double PROPEL_STRAFE = 0.8;
	public static final double PROPEL_ACCELERATION = 0.2;
	public static final double PROPEL_REVERSE = 0.3;
	public static final double PROPEL_IDLE = 0.12;
	public static final double PROPEL_IDLE_SINK = 0.06;
	public static final float PROPEL_SWEAT = 0.5f;
	public static final float PROPEL_MIN_SWEAT = 2f;
	public static final float PROPEL_STRAIN = 0.35f;
	public static final int PROPEL_POP_INTERVAL = 3;
	public static final int PROPEL_COOLDOWN = 10;
	public static final int PROPEL_MAX_TICKS = 1200;
	/** The Dash: a back-blast toward the WASD direction (the look when no key is held), about 12 blocks. */
	public static final double DASH_DISTANCE = 12.0;
	public static final float DASH_DAMAGE = 4f;
	public static final double DASH_RADIUS = 2.0;
	public static final float DASH_SWEAT = 5f;
	public static final float DASH_STRAIN = 5f;
	public static final int DASH_COOLDOWN = 30;
	/** Spin Dodge: a sideways blast spin (A / D picks the side), invulnerable for a moment, ending facing the attacker. */
	public static final double DODGE_DISTANCE = 5.0;
	public static final int DODGE_IFRAMES = 6;
	public static final int DODGE_TICKS = 7;
	public static final double DODGE_FACE_RANGE = 16.0;
	public static final float DODGE_SWEAT = 4f;
	public static final float DODGE_STRAIN = 4f;
	public static final int DODGE_COOLDOWN = 40;

	// ---------------------------------------------------------------- V: Stun Grenade / Shift+V: Cool Off
	public static final double STUN_RADIUS = 8.0;
	public static final float STUN_DAMAGE = 2f;
	public static final int STUN_BLIND_TICKS = 100;
	public static final int STUN_SLOW_TICKS = 100;
	public static final float STUN_SWEAT = 12f;
	public static final float STUN_STRAIN = 10f;
	public static final int STUN_COOLDOWN = 200;
	public static final float COOL_SWEAT = 40f;
	public static final float COOL_STRAIN = 50f;
	public static final int COOL_TICKS = 40;
	public static final int COOL_COOLDOWN = 300;

	// ---------------------------------------------------------------- G: Punch / Shift+G: Blast Rush / hold G: Crush Grip
	public static final double PUNCH_REACH = 3.0;
	public static final float PUNCH_DAMAGE = 14f;
	public static final double PUNCH_RADIUS = 2.5;
	public static final double PUNCH_KNOCKBACK = 1.6;
	public static final double PUNCH_LUNGE = 0.45;
	public static final float PUNCH_SWEAT = 8f;
	public static final float PUNCH_STRAIN = 8f;
	public static final int PUNCH_COOLDOWN = 30;
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
	/** Crush Grip: grab the face in reach, three detonations (6 / 6 / 12) at these ticks, then the throw. Bosses: hits, no grab. */
	public static final double GRIP_REACH = 3.5;
	public static final int[] GRIP_BEATS = { 4, 10, 18 };
	public static final float[] GRIP_DAMAGE = { 6f, 6f, 12f };
	public static final int GRIP_THROW_AT = 22;
	public static final double GRIP_THROW = 1.6;
	public static final float GRIP_SWEAT = 12f;
	public static final float GRIP_STRAIN = 12f;
	public static final int GRIP_COOLDOWN = 160;
	/** Nothing in reach: a short cooldown, nothing spent. */
	public static final int GRIP_WHIFF_COOLDOWN = 20;

	// ---------------------------------------------------------------- Z: Cluster / Shift+Z: Explosive Uppercut
	public static final double CLUSTER_INNER = 3.0;
	public static final double CLUSTER_OUTER = 5.5;
	public static final float CLUSTER_DAMAGE = 9f;
	public static final double CLUSTER_KNOCKBACK = 1.4;
	public static final int CLUSTER_RING_COUNT = 8;
	public static final int CLUSTER_RING_DELAY = 3;
	public static final float CLUSTER_SWEAT = 10f;
	public static final float CLUSTER_STRAIN = 12f;
	public static final int CLUSTER_COOLDOWN = 80;
	/** The Uppercut: a rising blast into the target in reach, launching it, and him after it for an air follow-up. */
	public static final double UPPERCUT_REACH = 3.5;
	public static final float UPPERCUT_DAMAGE = 12f;
	public static final double UPPERCUT_LIFT = 1.25;
	public static final double UPPERCUT_SELF_LIFT = 1.05;
	public static final float UPPERCUT_SWEAT = 8f;
	public static final float UPPERCUT_STRAIN = 10f;
	public static final int UPPERCUT_COOLDOWN = 60;

	// ---------------------------------------------------------------- C: Howitzer Impact / Shift+C: Gauntlet Grenade
	public static final float HOWITZER_MIN_SWEAT = 30f;
	public static final float HOWITZER_SWEAT_FRACTION = 0.7f;
	public static final double HOWITZER_RANGE = 24.0;
	public static final int HOWITZER_RISE_TICKS = 8;
	public static final double HOWITZER_RISE_SPEED = 0.85;
	public static final int HOWITZER_SPIN_TICKS = 36;
	public static final double HOWITZER_SPEED = 1.3;
	public static final int HOWITZER_VORTEX_INTERVAL = 2;
	public static final float HOWITZER_VORTEX_DAMAGE = 3f;
	public static final double HOWITZER_VORTEX_RADIUS = 3.0;
	public static final double HOWITZER_ARRIVE = 2.5;
	public static final float HOWITZER_DAMAGE = 30f;
	public static final double HOWITZER_RADIUS = 6.0;
	public static final double HOWITZER_KNOCKBACK = 2.5;
	public static final float HOWITZER_SPIN_DEGREES = 60f;
	public static final float HOWITZER_STRAIN = 45f;
	public static final int HOWITZER_COOLDOWN = 400;
	public static final float GAUNTLET_MIN_SWEAT = 50f;
	public static final int GAUNTLET_CHARGE = 14;
	public static final double GAUNTLET_RANGE = 40.0;
	public static final float GAUNTLET_DAMAGE = 28f;
	public static final double GAUNTLET_STEP = 3.0;
	public static final double GAUNTLET_RADIUS = 3.0;
	public static final double GAUNTLET_KNOCKBACK = 2.0;
	public static final double GAUNTLET_RECOIL = 1.2;
	public static final float GAUNTLET_STRAIN = 55f;
	public static final int GAUNTLET_COOLDOWN = 500;

	// ---------------------------------------------------------------- Plus Ultra: Howitzer Impact -- Cluster
	/** The spinning rise, marking up to {@link #ULT_TARGETS} targets within {@link #ULT_MARK_RADIUS}. */
	public static final int ULT_RISE_TICKS = 20;
	public static final double ULT_RISE_SPEED = 0.55;
	public static final int ULT_TARGETS = 5;
	public static final double ULT_MARK_RADIUS = 20.0;
	/** The chain-dive: this fast between targets, 15 at each, at most this long a leg. */
	public static final double ULT_DIVE_SPEED = 1.7;
	public static final int ULT_LEG_TICKS = 24;
	public static final float ULT_TARGET_DAMAGE = 15f;
	public static final double ULT_TARGET_RADIUS = 3.0;
	/** The finishing blast. */
	public static final float ULT_FINAL_DAMAGE = 40f;
	public static final double ULT_FINAL_RADIUS = 8.0;

	// ---------------------------------------------------------------- passives / survival
	/** A fall over this many blocks with {@link #LANDING_SWEAT}+ Sweat auto-blasts under him: no fall damage, that Sweat spent. */
	public static final float LANDING_FALL = 8f;
	public static final float LANDING_SWEAT = 10f;
	public static final float LANDING_DAMAGE = 6f;
	public static final double LANDING_RADIUS = 3.0;
	/** Blast mining: Sneak + punch with an empty hand blasts a 3x3 of soft stone / earth into drops. */
	public static final float MINING_SWEAT = 5f;
	public static final float MINING_MAX_HARDNESS = 1.5f;
	public static final int MINING_COOLDOWN = 8;

	/** Blast power at this much Sweat (the band's multiplier). */
	public static float powerAt(float sweat) {
		return sweat >= SOAKED_FROM ? POWER_SOAKED : sweat >= PRIMED_FROM ? POWER_PRIMED : POWER_DRY;
	}
}
