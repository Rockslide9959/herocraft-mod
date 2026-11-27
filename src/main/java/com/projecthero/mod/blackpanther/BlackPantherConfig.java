package com.projecthero.mod.blackpanther;

/** v0.17.3: every Black Panther number in one place. Ticks unless named otherwise; damage in half hearts. */
public final class BlackPantherConfig {
	public static final int VISION_TICKS = 200; // the Ancestral Plane, 10 s
	/** v0.17.7: the nanotech build (no body clip any more, user), a little quicker than the old 40 / 32. */
	public static final int SUIT_UP_TICKS = 36;
	public static final int SUIT_DOWN_TICKS = 30;

	public static final float KINETIC_MAX = 100f;
	/** The suit soaks this share of a hit... */
	public static final float SOAK = 0.4f;
	/** ...and stores this much Kinetic per point soaked. */
	public static final float KINETIC_PER_DAMAGE = 4f;
	// v0.17.7 (user: "it doesnt dissipate in the movies"): the bar never drains while the suit is on; it only goes down when spent.
	/**
	 * v0.17.7, later the same day (user: "make kinetic energy dissipate slowly when suit is in necklace mode please"): with the suit
	 * away in the necklace (or no suit at all) the bar leaks this much...
	 */
	public static final float KINETIC_UNSUITED_DRAIN = 1f;
	/** ...every this many ticks (1 every 2 s: a full bar empties in about 3 min 20 s). Only while he is online. */
	public static final int KINETIC_UNSUITED_DRAIN_EVERY = 40;

	/**
	 * v0.17.7 (user: "make his melee deal 8 damage and 10 with the suit please so its more similar to super soldier"): a bare-handed
	 * hit out of the suit deals 8 (the fist's 1 + 7 from the herb; was 5)...
	 */
	public static final double UNSUITED_MELEE = 8.0;
	/** ...and 10 in it (user: the vibranium claws are always out, there is no claw toggle any more). */
	public static final double SUITED_MELEE = 10.0;
	/** The necklace suit's armour and toughness while suited (user: "make the black panther armour diamond level armour"). */
	public static final double SUIT_ARMOR = 20.0; // v0.17.7: full diamond (was 12)
	public static final double SUIT_TOUGHNESS = 8.0; // v0.17.7: full diamond (was 4)
	/** v0.17.7 (user): Regeneration I out of the suit, Regeneration II in it (effect amplifiers). */
	public static final int REGEN_UNSUITED = 0;
	public static final int REGEN_SUITED = 1;
	/** v0.17.7 (user: "give player passive resistance one"): his endless Resistance amplifier, suit on or off (0 = Resistance I). */
	public static final int RESISTANCE_LEVEL = 0;
	/**
	 * v0.17.7 (user: "player can jump 2 blocks without the suit / player can jump 3 blocks with the suit"): added to the vanilla
	 * jump strength (0.42, a 1.25-block hop) in place of the herb's old Jump Boost. Vanilla jump physics (each tick: y += v, then
	 * v = (v - 0.08) * 0.98) give a 0.61 take-off a 2.44-block peak (clears 2, not 3) and a 0.74 take-off a 3.46-block peak
	 * (clears 3, not 4).
	 */
	public static final double JUMP_UNSUITED = 0.19;
	public static final double JUMP_SUITED = 0.32;
	/** v0.17.7 (Street tier): no single hit goes over this, whatever stacks (Stalk, Kimoyo Scan, Kinetic Return). */
	public static final float MAX_HIT = 20f; // v0.17.7: was PowerRank.STREET.ultimateMax (tiers removed; the user rebalances powers one by one)
	/** Arrows and bullets below this much damage bounce off (the Iron Man bulletproof rule). */
	public static final float DEFLECT_LIMIT = 15f;

	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ================================================================ v0.17.7 suited kit (user-approved moveset)
	// Placeholder numbers from the approved table: the user sets the final values himself (v0.17.7: no power-tier ranges or tier
	// caps on the new moves; the boss cap still applies). Damage in half hearts, Kinetic in bar points (0..100), times in ticks.
	// Plain keys are free and add Kinetic when they land on a living target; Shift keys spend Kinetic.

	/** A refused Shift move (too little Kinetic) flashes the HUD's Kinetic bar this long. */
	public static final int KINETIC_DENIED_FLASH_TICKS = 12;

	// ---- R: Vibranium Combo (three claw strikes; the 3rd leaves a violet slash mark)
	public static final float VIBRANIUM_COMBO_HIT_1 = 8f;
	public static final float VIBRANIUM_COMBO_HIT_2 = 9f;
	public static final float VIBRANIUM_COMBO_HIT_3 = 10f;
	/** Kinetic gained for every strike that lands on a living target. */
	public static final float VIBRANIUM_COMBO_KINETIC_PER_HIT = 5f;
	public static final double VIBRANIUM_COMBO_REACH = 3.5;
	/** Ticks between strikes (the first lands on the press). */
	public static final int VIBRANIUM_COMBO_STEP = 6;
	public static final int VIBRANIUM_COMBO_COOLDOWN = 30;
	/** Legacy aliases: BlackPantherUnsuited.comboDamage(n, true) (the suited Warrior's Combo of the first v0.17.7 build). */
	public static final float SUITED_COMBO_JAB = VIBRANIUM_COMBO_HIT_1;
	public static final float SUITED_COMBO_CROSS = VIBRANIUM_COMBO_HIT_2;
	public static final float SUITED_COMBO_KICK = VIBRANIUM_COMBO_HIT_3;

	// ---- Shift+R: Kinetic Combo (three fast charged slashes; the finisher releases a forward cone shockwave)
	public static final float KINETIC_COMBO_COST = 25f;
	public static final float KINETIC_COMBO_DAMAGE = 10f; // each slash
	public static final double KINETIC_COMBO_REACH = 3.5;
	public static final int KINETIC_COMBO_STEP = 4;
	/** The shockwave: this much to everyone else in the cone (the slash's own targets are not hit twice)... */
	public static final float KINETIC_COMBO_WAVE_DAMAGE = 8f;
	/** ...out to this far ahead... */
	public static final double KINETIC_COMBO_WAVE_RANGE = 4.0;
	/** ...within this dot of his flat facing (0.5 = 60 degrees either side). */
	public static final double KINETIC_COMBO_WAVE_ARC = 0.5;
	public static final int KINETIC_COMBO_COOLDOWN = 40;

	// ---- G: Pounce (a leap along the aim; damage on landing on a creature, then pinned)
	// v0.17.7 (user: "dont make it need an enemy in order to activate, give it slight aim assist tho so that it cant track targets
	// still"): it always fires. At launch only, a creature within POUNCE_ASSIST_DEGREES of the crosshair (in range, in sight) is picked
	// and the leap is aimed at where it stands; the direction is then fixed -- no steering in the air. With nothing picked he leaps
	// straight along his aim (pitch included). The first creature he reaches in flight is the one he lands on.
	public static final double POUNCE_RANGE = 8.0;
	public static final double POUNCE_SPEED = 1.6;
	/** The launch-only aim assist's cone: half-angle round the crosshair (deg). */
	public static final double POUNCE_ASSIST_DEGREES = 8.0;
	/** He lands on a creature once it is this close to him (or its width + 1.2, whichever is more). */
	public static final double POUNCE_CONTACT = 2.0;
	/** Launched off the ground with a flat or downward aim, the leap rises at least this much (y of the unit direction). */
	public static final double POUNCE_MIN_RISE = 0.15;
	/** The leap gives up (lands where he is) after this many ticks. */
	public static final int POUNCE_MAX_FLIGHT_TICKS = 16;
	public static final float POUNCE_LAND_DAMAGE = 12f; // v0.17.7 (user: "Pounce: increase damage to 12"): was 9
	public static final int POUNCE_PIN_TICKS = 20;
	public static final float POUNCE_KINETIC_GAIN = 10f;
	public static final int POUNCE_COOLDOWN = 120;

	// ---- Shift+G: Kinetic Pounce (the same leap and launch-only assist, ending in a violet ground burst instead of the claws;
	// always fires: with no creature reached the burst goes off where he lands)
	public static final float KINETIC_POUNCE_COST = 30f;
	public static final float KINETIC_POUNCE_DAMAGE = 25f; // v0.17.7 (user: "Kinetic Pounce: increase damage to 25"): was 10
	public static final double KINETIC_POUNCE_RADIUS = 3.0;
	public static final double KINETIC_POUNCE_KNOCKBACK = 1.4;
	public static final int KINETIC_POUNCE_COOLDOWN = 160;

	// ---- X: Panther Leap (v0.17.7, user: "make it dash player in direction theyre aiming in instead of just up and forward please")
	/** Launch speed along the full look direction (pitch included): straight up that is still about 12 blocks. */
	public static final double LEAP_SPEED = 1.62;
	/** Off the ground with a flat or downward aim, the launch keeps at least this much upward speed (so he leaves the ground). */
	public static final double LEAP_MIN_LIFT = 0.42;
	public static final int LEAP_COOLDOWN = 60;
	public static final int LEDGE_TICKS = 60;

	// ---- Shift+X: Kinetic Dash (where he aims, pitch included; untouchable while it runs)
	public static final float DASH_COST = 20f;
	public static final double DASH_DISTANCE = 10.0;
	public static final int DASH_TICKS = 5;
	/** Damage-immune for the dash plus this many ticks. */
	public static final int DASH_IFRAME_EXTRA_TICKS = 2;
	public static final float DASH_DAMAGE = 18f; // each creature passed through, once (v0.17.7 user: "Kinetic Dash: increase to 18 damage", was 8)
	public static final double DASH_HIT_RADIUS = 0.6;
	public static final int DASH_COOLDOWN = 60;

	// ---- Z: Vibranium Dagger (unchanged, + Kinetic on a hit)
	public static final float DAGGER_DAMAGE = 8f;
	public static final int DAGGER_BOUNCES = 3;
	public static final double DAGGER_SPEED = 1.7;
	public static final double DAGGER_RANGE = 14.0; // was 24
	public static final double DAGGER_HOP = 6.0; // was 10
	public static final int DAGGER_COOLDOWN = 60; // v0.17.7 (user: "Lower dagger throw cooldown in suit and not in suit"): 3 s, was 7 s
	public static final float DAGGER_KINETIC_GAIN = 5f; // each target struck

	// ---- Shift+Z: Kinetic Overload (ULTIMATE: needs a FULL bar and spends all of it; ground slam + violet dome)
	public static final float OVERLOAD_DAMAGE = 20f; // at the centre
	public static final float OVERLOAD_EDGE_DAMAGE = 15f; // at the radius (straight line in between)
	public static final double OVERLOAD_RADIUS = 10.0;
	public static final int OVERLOAD_COOLDOWN = 100;

	// ---- V: Stalk (unchanged)
	public static final double STALK_SEEN_WITHIN = 6.0;
	public static final float STALK_BACKSTAB = 3f; // on mobs
	public static final float STALK_BACKSTAB_PLAYER = 1.75f; // tester round: PvP
	public static final int STALK_COOLDOWN = 200; // was 40
	/** Hunter's Tracking (unsuited Shift+V): footprints kept per creature. */
	public static final int FOOTPRINTS = 10;

	// ---- Shift+V: Wakanda Forever in the suit (v0.17.7, user: "Kinetic Shroud: Make this the Wakanda forever ability, can be
	// activated whenever, if player has 30 kinetic energy then activate the violet shimmer and 50% less damage and the other stuff for
	// the kinetic shroud ability please, should still last the same amount of time as unsuited Wakanda forever"). Always usable: the
	// unsuited Wakanda Forever's buffs (RESOLVE_*, below) and its cooldown, shared between both kits. With SHROUD_COST or more stored at
	// the cast it spends that and adds the old Kinetic Shroud for SHROUD_TICKS (the same 50 s).
	public static final float SHROUD_COST = 30f;
	/** Share of each hit taken off while the Shroud is up. */
	public static final float SHROUD_REDUCTION = 0.5f;
	/** Damage thrown back at a creature or player that lands a hit on him. */
	public static final float SHROUD_THORNS = 4f;

	// ---- C: Absorb Stance (unchanged) / Shift+C: Kinetic Return (unchanged)
	public static final float ABSORB_MAX = 40f;
	public static final int ABSORB_MAX_TICKS = 120;
	public static final int ABSORB_COOLDOWN = 80;
	public static final double ABSORB_SLOW = -0.6;
	/** Dot of the facing and the attacker: about 60 degrees either side counts as the front. */
	public static final double FRONT_ARC = 0.5;
	public static final int RETURN_WINDOW = 200;
	public static final double RETURN_RANGE = 48.0;
	public static final float RETURN_FACTOR = 1.25f; // tester round: scales with the stored bar too
	public static final float RETURN_MIN_DAMAGE = 8f; // v0.17.7 Street ability floor: was 4 (and it never goes over MAX_HIT)
	/** Kinetic Return spends up to this much of the bar... */
	public static final float RETURN_MAX_SPEND = 40f;
	/** ...and adds this much damage per point spent. */
	public static final float RETURN_PER_KINETIC = 0.25f;
	public static final int RETURN_COOLDOWN = 120;

	// ================================================================ v0.17.4: the passives
	/** Out of the suit, falls hurt this much (in it they do nothing). */
	public static final float CAT_LANDING = 0.5f;
	/** Night Eyes (out of the suit): night vision while the light at his eyes is at or under this... */
	public static final int NIGHT_EYES_DARK = 6;
	/** ...and off again once it is over this. */
	public static final int NIGHT_EYES_BRIGHT = 9;
	/** Hunter's Instinct: creatures this close that are hunting him are outlined for him. */
	public static final double INSTINCT_RANGE = 20.0;

	// ================================================================ the unsuited kit (T'Challa without the suit; v0.17.7 user numbers)
	// R
	public static final float COMBO_JAB = 5f; // v0.17.7 (user): 5 / 7 / 10, was 3 / 3 / 5
	public static final float COMBO_CROSS = 7f;
	public static final float COMBO_KICK = 10f;
	public static final int COMBO_STEP = 5;
	public static final double COMBO_REACH = 3.0;
	public static final int COMBO_COOLDOWN = 30;
	public static final float SWEEP_DAMAGE = 7f; // v0.17.7 (user): was 4
	public static final double SWEEP_RADIUS = 2.6;
	public static final int SWEEP_TRIP_TICKS = 30;
	public static final int SWEEP_COOLDOWN = 120;
	// G
	public static final double TAKEDOWN_RANGE = 3.0; // v0.17.7 (user): 3 blocks
	public static final float TAKEDOWN_DAMAGE = 12f; // v0.17.7 (user: "takedown: increase damage to 12"): was 10
	public static final int TAKEDOWN_DAZE_TICKS = 40;
	/** The slam lands this many ticks after the grab (the flip over the shoulder). */
	public static final int TAKEDOWN_SLAM_AT = 9;
	public static final int TAKEDOWN_COOLDOWN = 160;
	/** v0.17.7 Ground Pound (Shift+G, was Disarm): a short hop, then he drives down and slams the ground round him. */
	public static final float POUND_DAMAGE = 15f; // v0.17.7 (user: "Ground pound: increase damage to 15"): was 9
	public static final double POUND_RADIUS = 3.5;
	public static final double POUND_UP = 0.62;
	/** He is driven down this fast from POUND_DIVE_AT ticks after the hop... */
	public static final double POUND_DOWN = 1.6;
	public static final int POUND_DIVE_AT = 7;
	/** ...and the slam lands when he touches down, or at the latest this many ticks after the hop. */
	public static final int POUND_MAX_TICKS = 24;
	public static final int POUND_SLOW_TICKS = 30;
	public static final int POUND_COOLDOWN = 160;
	// X
	public static final double VAULT_FORWARD = 0.7;
	public static final double VAULT_UP = 0.62;
	public static final int VAULT_COOLDOWN = 50;
	/** v0.17.7 Flying Kick (Shift+X, was Backflip): a dash forward into a flying kick on the first creature in the way. */
	public static final float KICK_DAMAGE = 10f;
	public static final double KICK_SPEED = 1.25;
	/** v0.17.7: kicked off the ground, he hops at least this much first (a flat or low aim skims instead of scraping). */
	public static final double KICK_HOP = 0.22;
	/** The dash, along the full look (pitch included), lasts at most this many ticks (about 8 blocks)... */
	public static final int KICK_TICKS = 8;
	/** ...and the kick lands on the first creature within this of him. */
	public static final double KICK_REACH = 1.9;
	public static final int KICK_COOLDOWN = 120;
	// Z
	public static final float KNIFE_DAMAGE = 8f; // v0.17.7 (user): was 5
	public static final double KNIFE_RANGE = 12.0;
	public static final int KNIFE_COOLDOWN = 40; // v0.17.7 (user: "Lower dagger throw cooldown in suit and not in suit"): 2 s, was 4 s
	public static final double SCAN_RANGE = 32.0;
	public static final int SCAN_TICKS = 200;
	/** A Kimoyo-scanned creature takes this much more from his hits. */
	public static final float SCAN_BONUS = 1.25f;
	public static final int SCAN_COOLDOWN = 300;
	// V
	/** v0.17.7 Rising Uppercut (V, was Shadow Step): a crouching uppercut that launches the creature in front of him. */
	public static final float UPPERCUT_DAMAGE = 9f;
	public static final double UPPERCUT_RANGE = 3.0;
	public static final double UPPERCUT_LIFT = 0.85;
	public static final int UPPERCUT_STUN_TICKS = 30;
	public static final int UPPERCUT_COOLDOWN = 120;
	/** Hunter's Tracking (Shift+V since v0.17.7). */
	public static final double TRACKING_RANGE = 24.0;
	// C
	/** v0.17.7 Block (hold C, was Parry): every hit from the front is cut by this share while it is up... */
	public static final float BLOCK_REDUCTION = 0.6f;
	/** v0.17.7 (user: "Block: remove time limit, 6 second cooldown when block is released"): up for as long as C is held; the cooldown starts on release. */
	public static final int BLOCK_COOLDOWN = 120;
	public static final double BLOCK_SLOW = -0.35;
	/**
	 * v0.17.7 Wakanda Forever (Shift+C, was Ancestral Resolve; user: "player gets speed 2, strength 2, regeneration 2, resistance 2,
	 * lasts 50 seconds, 100 second cooldown upon activation, players emits purple particle effects while this is active").
	 */
	public static final int RESOLVE_TICKS = 50 * 20;
	/** Effect amplifiers: Speed II, Strength II, Regeneration II, Resistance II. */
	public static final int RESOLVE_SPEED = 1, RESOLVE_STRENGTH = 1, RESOLVE_REGEN = 1, RESOLVE_RESISTANCE = 1;
	/** From the cast; one cooldown for the suited and the unsuited Wakanda Forever (suiting up does not reset it). */
	public static final int RESOLVE_COOLDOWN = 100 * 20;
	/** v0.17.7: the suited Wakanda Forever's Kinetic Shroud lasts as long as the buffs. */
	public static final int SHROUD_TICKS = RESOLVE_TICKS;
	/** The purple aura is puffed out every this many ticks while it lasts. */
	public static final int RESOLVE_PARTICLE_EVERY = 3;

	private BlackPantherConfig() {
	}
}
