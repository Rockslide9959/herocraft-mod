package com.projecthero.mod.supersoldier;

/**
 * Every Super Soldier tunable (v0.14.8): plain constants, ticks unless the name says otherwise. He is peak human, not a
 * god -- every number here is meant to sit below the Hulk / Thor / All Might.
 */
public final class SuperSoldierConfig {
	private SuperSoldierConfig() {
	}

	// ---------------------------------------------------------------- passives
	/** Movement speed: +50% of base (ADD_MULTIPLIED_BASE). */
	public static final double SPEED_BONUS = 0.5;
	/** +5 hearts. */
	public static final double HEALTH_BONUS = 10.0;
	/**
	 * v0.17.7 (user: "their melee should deal 5 damage and should be +5 on top of tools and weapons and stuff"): his melee N under the
	 * shared rule ({@code combat.MeleeBonus}) -- a punch (empty hand or a non-weapon item) deals 5, a weapon or tool its own damage + 5
	 * (a netherite sword 13). Replaces the old fist 8 / +2 holding anything.
	 */
	public static final double MELEE = 5.0;
	/**
	 * Everything his strength adds to a weapon hit (= {@link #MELEE}: the N - 1 modifier plus the shared +1 weapon top-up). Kept
	 * under this name for the Thor-lent weapon movesets ({@code worthy.WorthyMoves}), which tune a lent Mjolnir / Stormbreaker hit
	 * to an exact total by taking this off.
	 */
	public static final double ARMED_BONUS = MELEE;
	/** v0.17.7 (Street tier): no single move hit goes over this, whatever buffs stack (Assemble x1.25 on a marked enemy, Strength). */
	public static final float MAX_HIT = 65f; // v0.17.7: raised from 20 so the Shield Storm's 45 lands whole (a safety ceiling only; the user sets every number)
	/** Damage taken is multiplied by this (30% less). */
	public static final float DAMAGE_TAKEN_FACTOR = 0.7f;
	/**
	 * Jump apex height in blocks (vanilla is about 1.25). v0.17.7 (user: "players should be able to jump 2 blocks"): 2.4 --
	 * his feet stay above a two-block wall for 6 ticks, so he clears it comfortably, and a three-block wall stays out of reach.
	 */
	public static final double JUMP_BLOCKS = 2.4;
	/** v0.17.7 (user): passive Resistance I (amplifier 0) the whole time he has the power. */
	public static final int PASSIVE_RESISTANCE_AMPLIFIER = 0;
	/** The passive Resistance is (re)applied for this long, topped up whenever it drops under {@link #PASSIVE_RESISTANCE_REFRESH}. */
	public static final int PASSIVE_RESISTANCE_TICKS = 300;
	public static final int PASSIVE_RESISTANCE_REFRESH = 220;
	/**
	 * v0.17.7 (user: "player should have passive regen 1" / "player should have passive speed 1"): Regeneration I and Speed I the
	 * whole time he has the power -- infinite, ambient (no swirl), re-given every tick if missing; a stronger one (a potion, Assemble's
	 * II) is left alone and ours comes back when it ends. Taken away with the power. Infinite, not a topped-up timer: Regeneration
	 * heals on its own clock, and a timer reset every few seconds would make it heal unevenly.
	 */
	public static final int PASSIVE_REGEN_AMPLIFIER = 0;
	public static final int PASSIVE_SPEED_AMPLIFIER = 0;
	/** v0.17.7 (user: "The armour should have diamond level durability"): the Captain America suit's durability multiplier (diamond 33). */
	public static final int SUIT_DURABILITY_MULTIPLIER = 33;
	/**
	 * v0.17.7 (user: "if player is blocking with shield and looking at ground then they dont take fall damage"): blocking with a
	 * shield (a raised vanilla-style shield or the Battle Suit slot's guard) while looking at least this far down cancels fall damage.
	 */
	public static final float SHIELD_LANDING_MIN_PITCH = 60.0f;
	/** Safe fall distance on top of vanilla's 3 blocks (matches the higher jump). */
	public static final double SAFE_FALL_BONUS = 3.0;
	/** Knockback resistance (0..1). */
	public static final double KNOCKBACK_RESISTANCE = 0.2;
	/** Attack speed: +15% of base. */
	public static final double ATTACK_SPEED_BONUS = 0.15;
	/** Out-of-combat healing: 1 HP every this many ticks, once this long has passed without a fight. */
	public static final int REGEN_INTERVAL = 40;
	public static final int REGEN_DELAY = 100;

	// ---------------------------------------------------------------- general
	/** Anything with this much max health (or a Titan / Wither / Dragon) is a boss: capped damage, no knockback. */
	public static final float BOSS_HEALTH_THRESHOLD = 300.0f;
	/** A single move never takes more than this fraction of a boss's max health. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.06f;
	/** Minimum gap between any two moves. */
	public static final int GLOBAL_LOCK_TICKS = 4;
	/** His own leaps never cause fall damage for this long after take-off. */
	public static final int LEAP_NO_FALL_TICKS = 100;

	// ---------------------------------------------------------------- R: Combo Strike
	public static final int COMBO_COOLDOWN = 100;
	/** v0.17.7 (user: "match black panther combo damage please"): Black Panther's unsuited Warrior's Combo, 5 / 7 / 10 (was 7 / 7 / 9). */
	public static final float COMBO_HIT_1_DAMAGE = 5.0f;
	public static final float COMBO_HIT_2_DAMAGE = 7.0f;
	public static final float COMBO_FINISHER_DAMAGE = 10.0f;
	/** Ticks between the three punches. */
	public static final int COMBO_INTERVAL = 5;
	public static final double COMBO_RANGE = 4.0;
	public static final double COMBO_FINISHER_KNOCKBACK = 1.0;

	// ---------------------------------------------------------------- Shift+R: Uppercut Launcher
	public static final int UPPERCUT_COOLDOWN = 160;
	public static final float UPPERCUT_DAMAGE = 10.0f;
	/** Upward speed given to the target: about 5 blocks of air. */
	public static final double UPPERCUT_LIFT = 1.05;
	public static final double UPPERCUT_RANGE = 4.0;

	// ---------------------------------------------------------------- G: Flying Kick (v0.14.9)
	public static final int KICK_COOLDOWN = 160;
	public static final float KICK_DAMAGE = 12.0f; // v0.17.7 (user: "increase to 12 damage please"; the Street pass had it at 10)
	/** How far away the enemy he lunges at may be. */
	public static final double KICK_RANGE = 8.0;
	/** Within this distance (feet to the target's box) the kick lands -- at once if he starts this close. */
	public static final double KICK_REACH = 2.4;
	/** Lunge speed (blocks / tick) and how long the lunge may last before it whiffs. */
	public static final double KICK_SPEED = 1.15;
	public static final int KICK_TICKS = 10;
	public static final double KICK_KNOCKBACK = 1.5;
	public static final double KICK_LIFT = 0.3;

	// ---------------------------------------------------------------- Shift+G: Judo Takedown (v0.14.9)
	public static final int TAKEDOWN_COOLDOWN = 200;
	public static final float TAKEDOWN_DAMAGE = 15.0f; // v0.17.7 (user: "increase to 15 damage"; the Street pass had it at 10)
	public static final double TAKEDOWN_RANGE = 3.5;
	/** Ticks between the grab (the enemy hoisted over his shoulder) and the slam behind him. */
	public static final int TAKEDOWN_SLAM_DELAY = 5;
	/** The stun after the slam: Slowness IV + Weakness I for this long. */
	public static final int TAKEDOWN_STUN_TICKS = 30;

	// ---------------------------------------------------------------- C: Shield Throw (v0.14.9: the shield in his hand)
	// v0.17.7 (user: "change the shield targeting please its too op right now, make it so that the shield throws where the
	// player aims with some aim assist, the shield can bounce between enemies if they are in close proximity to whats being
	// hit, maximum 5 bounces, make the shield get thrown slower please"). Every throw -- C (hand or Battle Suit slot) and the
	// Shield Storm -- flies along his look (pitch included) with a light aim assist, and bounces only from an enemy it hit.
	/**
	 * v0.17.7 (user: "Captain America shield should do 6 damage"): every shield throw hits for 6, the first hit and each bounce --
	 * ordinary, Adamantium and Vibranium alike (were 8 / 9 / 10). The Shield Storm ({@link #STORM_HIT_DAMAGE}) and Shield Bash
	 * ({@link #SHIELD_BASH_DAMAGE}) keep their own numbers. The shield has no melee of its own: it is not a weapon, so a punch with
	 * it in hand is his plain punch ({@link #MELEE}).
	 */
	public static final float SHIELD_THROW_DAMAGE = 6.0f;
	/** Adamantium Shield: damage per hit, bounces per throw after the first hit (so up to 6 enemies), range, cooldown. */
	public static final float SHIELD_DAMAGE = SHIELD_THROW_DAMAGE;
	public static final int SHIELD_MAX_BOUNCES = 5;
	public static final double SHIELD_RANGE = 25.0; // v0.14.11: was 24
	public static final int SHIELD_THROW_COOLDOWN = 0; // v0.14.11: no cooldown (was 5 s) -- throw again as soon as it is back in your hand
	/** An ordinary shield: weaker, two bounces (three enemies), and it still comes back. */
	public static final float NORMAL_SHIELD_DAMAGE = SHIELD_THROW_DAMAGE; // v0.17.7 (user): 6 (was 8)
	/** The Vibranium Shield's throw (VibraniumShieldItem.THROW_DAMAGE): 6 like the rest (was 10). */
	public static final float VIBRANIUM_SHIELD_DAMAGE = SHIELD_THROW_DAMAGE;
	public static final int NORMAL_SHIELD_MAX_BOUNCES = 2;
	public static final double NORMAL_SHIELD_RANGE = 25.0; // v0.14.11: was 16, every shield flies 25 blocks
	public static final int NORMAL_SHIELD_THROW_COOLDOWN = 0; // v0.14.11: no cooldown (was 6 s)
	/** Outbound flight speed (blocks / tick). v0.17.7: 1.0, was 1.6 (62.5%) -- you can watch it fly and bounce. */
	public static final double SHIELD_SPEED = 1.0;
	/** The flight home: starts at this speed and speeds up with distance (dist x {@link #SHIELD_RETURN_ACCEL}) to at most the max. */
	public static final double SHIELD_RETURN_SPEED = 1.2;
	public static final double SHIELD_RETURN_MAX_SPEED = 2.0;
	public static final double SHIELD_RETURN_ACCEL = 0.12;
	/** Aim assist: an enemy within this many degrees of his aim (and in range, in sight) pulls the throw onto it. */
	public static final double SHIELD_ASSIST_CONE_DEG = 8.0;
	/** In flight it keeps tracking that enemy, turning at most this many degrees a tick... */
	public static final double SHIELD_ASSIST_TURN_DEG = 3.0;
	/** ...and lets go if it drifts more than this far off the shield's heading or out of sight (no curving round corners). */
	public static final double SHIELD_ASSIST_KEEP_DEG = 16.0;
	/** A bounce goes to the nearest unhit enemy this close to the one just hit (centre to centre), in its line of sight. */
	public static final double SHIELD_BOUNCE_RANGE = 6.0;
	public static final double SHIELD_KNOCKBACK = 0.6;
	/** A throw that has not been caught by then drops the shield where it is (never lost). */
	public static final int SHIELD_MAX_LIFE = 240;

	// ---------------------------------------------------------------- Z: Leaping Slam
	public static final int SLAM_COOLDOWN = 240;
	public static final float SLAM_DAMAGE = 10.0f; // v0.17.7 Street: was 12
	public static final double SLAM_RADIUS = 5.0;
	public static final double SLAM_HEIGHT = 4.0;
	public static final double SLAM_FORWARD = 0.8;
	public static final double SLAM_KNOCKBACK = 1.2;
	public static final double SLAM_LIFT = 0.4;
	/** The slam lands on its own after this long even if he never touches the ground (a cliff edge, water). */
	public static final int SLAM_MAX_AIR_TICKS = 60;

	// ---------------------------------------------------------------- Shift+Z: the ULTIMATE (v0.17.7 rework, two versions)
	/** Both versions share one cooldown (95 s): 5 s faster than the old Onslaught, which did far less. */
	public static final int ULTIMATE_COOLDOWN = 1900;

	// ---- unarmed: Peak Human Barrage
	/** He dashes onto the enemy in front within this many blocks... */
	public static final double BARRAGE_RANGE = 6.0;
	/** ...at this speed (blocks / tick) for at most this many ticks, and starts swinging once this close (feet to its box). */
	public static final double BARRAGE_DASH_SPEED = 1.5;
	public static final int BARRAGE_DASH_TICKS = 8;
	public static final double BARRAGE_REACH = 1.9;
	/** The flurry: this many hits, this many ticks apart (the first one tick after he arrives), this much each. */
	public static final int BARRAGE_HITS = 6;
	public static final int BARRAGE_HIT_INTERVAL = 3;
	public static final float BARRAGE_HIT_DAMAGE = 7.5f;
	/** The haymaker lands this many ticks after he arrives (a 7-tick wind-up after the last flurry hit). */
	public static final int BARRAGE_HAYMAKER_TICK = 24;
	public static final float BARRAGE_HAYMAKER_DAMAGE = 20.0f;
	public static final double BARRAGE_HAYMAKER_KNOCKBACK = 2.4;
	public static final double BARRAGE_HAYMAKER_LIFT = 0.9;
	/** The haymaker's burst: everyone else this close to the target takes this much. */
	public static final double BARRAGE_BURST_RADIUS = 4.0;
	public static final float BARRAGE_BURST_DAMAGE = 10.0f;

	// ---- with a shield: Shield Storm
	/**
	 * An enemy must be this close for the Storm to start, and the throw flies at most this far. v0.17.7: the throw itself follows
	 * the C rules -- along his aim with the same light assist, bouncing only to enemies within {@link #SHIELD_BOUNCE_RANGE} of
	 * the one it hit, at most {@link #STORM_MAX_BOUNCES} times.
	 */
	public static final double STORM_RANGE = 16.0;
	/** The leap before the throw (blocks up), and the throw this many ticks after take-off. */
	public static final double STORM_LEAP_HEIGHT = 4.5;
	public static final int STORM_THROW_DELAY = 7;
	/** v0.17.7 (user: "Increase the shield storm damage to match the peak human barrage damage, it does a maximum of 65"): the shield hit
	 * 45 + the slam's 20 = 65 on one enemy, the Barrage's 6 x 7.5 + 20. Was 15. */
	public static final float STORM_HIT_DAMAGE = 45.0f;
	/** v0.17.7: 5 bounces (6 enemies), was 8 ricochets anywhere within 16 blocks. */
	public static final int STORM_MAX_BOUNCES = 5;
	/** v0.17.7: 1.3, was 2.1 -- slowed by the same 62.5% as the C throw, still a touch faster than it. */
	public static final double STORM_SHIELD_SPEED = 1.3;
	/** He hangs in the air until the shield is back -- at most this long, then it is delivered and he drops anyway. */
	public static final int STORM_MAX_HOVER_TICKS = 100;
	/** His vertical speed while he hangs there (blocks / tick): a slow sink, never "floating" to the anti-fly check. */
	public static final double STORM_HOVER_SINK = -0.05;
	/** The dive after the catch: downward speed, and the slam goes off on landing or after this long. */
	public static final double STORM_DIVE_SPEED = 2.2;
	public static final int STORM_DIVE_MAX_TICKS = 30;
	/** The star shockwave: 20 at the centre, 60% at the edge, knocked up. */
	public static final float STORM_SLAM_DAMAGE = 20.0f;
	public static final double STORM_SLAM_RADIUS = 6.0;
	public static final double STORM_SLAM_KNOCKBACK = 0.8;
	public static final double STORM_SLAM_LIFT = 0.85;

	// ---------------------------------------------------------------- X: Dash (v0.17.7; was the Tactical Roll)
	// user: "X - Dash ability that dashs player in direction they aim"
	public static final int DASH_COOLDOWN = 60;
	/** Along his full look (pitch included): this fast (blocks / tick) for this many ticks -- about 8 blocks. */
	public static final double DASH_SPEED = 1.6;
	public static final int DASH_TICKS = 5;
	/** No fall damage for this long after a dash starts (a dash off a ledge or straight down). */
	public static final int DASH_NO_FALL_TICKS = 60;

	// ---------------------------------------------------------------- Shift+X: Shoulder Bash / Shield Bash (v0.17.7; was the High Leap)
	// user: "Shift+X - Shoulder bash without shield, shield bash ability with shield, dash player and deals damage in direction theyre
	// looking, knocks enemies back too, player doesn't take damage when using this move, shoulder bash deals 10 damage, shield bash
	// deals 14 damage"
	public static final int BASH_COOLDOWN = 160;
	/** The charge: along his look, this fast for this many ticks (about 8 blocks). */
	public static final double BASH_SPEED = 1.35;
	public static final int BASH_TICKS = 6;
	public static final float SHOULDER_BASH_DAMAGE = 10.0f;
	public static final float SHIELD_BASH_DAMAGE = 14.0f;
	/** Anything this close to his path (blocks around his body, swept a tick ahead) is hit -- once per bash. */
	public static final double BASH_HIT_RADIUS = 1.2;
	/** Knocked along the charge's direction, and up a little. */
	public static final double BASH_KNOCKBACK = 1.4;
	public static final double BASH_LIFT = 0.35;
	/** He cannot be hurt for the whole charge plus this many ticks. */
	public static final int BASH_IFRAME_EXTRA = 2;

	// ---------------------------------------------------------------- V: Spinning Roundhouse (v0.17.7; replaced the Counter Stance)
	// user: "counter stance: replace this move with another attack ability"
	public static final int ROUNDHOUSE_COOLDOWN = 120;
	/** The chamber (knee drawn up as he turns) before the kick lands. */
	public static final int ROUNDHOUSE_WINDUP = 3;
	public static final float ROUNDHOUSE_DAMAGE = 11.0f;
	/** Everything in front of him in this arc (degrees, centred on his facing) within this reach (blocks, his feet to its box). */
	public static final double ROUNDHOUSE_ARC_DEG = 150.0;
	public static final double ROUNDHOUSE_RANGE = 3.5;
	public static final double ROUNDHOUSE_KNOCKBACK = 1.2;
	public static final double ROUNDHOUSE_LIFT = 0.3;
	/** Dazed: Slowness II for this long. */
	public static final int ROUNDHOUSE_DAZE_TICKS = 20;

	// ---------------------------------------------------------------- Shift+V: Assemble (v0.17.7)
	// user: "Assemble: For 50 seconds player and squad mates get buffed, strength 2, resistance 2, regeneration 2, speed 2, 100 second
	// cooldown on activation". Buffs only now -- the debuffs, marks, x1.25 on the marked, punch shock and the ring's knockback (from the
	// merged Battle Cry / Tactical Focus / Onslaught) are gone; the call, the animation and the gold / blue ring stay as visuals.
	public static final int ASSEMBLE_COOLDOWN = 2000;
	public static final int ASSEMBLE_DURATION = 1000;
	/** He and every squadmate this close get the buffs. */
	public static final double ASSEMBLE_ALLY_RADIUS = 24.0;
	/** Strength, Resistance, Regeneration and Speed at this amplifier (1 = II). */
	public static final int ASSEMBLE_AMPLIFIER = 1;
	/** The size of the gold / blue rally ring (visual only). */
	public static final double ASSEMBLE_RING_RADIUS = 6.0;
}
