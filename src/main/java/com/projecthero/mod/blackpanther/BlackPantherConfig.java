package com.projecthero.mod.blackpanther;

/** v0.17.3: every Black Panther number in one place. Ticks unless named otherwise; damage in half hearts. */
public final class BlackPantherConfig {
	public static final int VISION_TICKS = 200; // the Ancestral Plane, 10 s
	public static final int SUIT_UP_TICKS = 40;
	public static final int SUIT_DOWN_TICKS = 32;

	public static final float KINETIC_MAX = 100f;
	/** The suit soaks this share of a hit... */
	public static final float SOAK = 0.4f;
	/** ...and stores this much Kinetic per point soaked. */
	public static final float KINETIC_PER_DAMAGE = 4f;
	/** Out of combat the bar drains this much a second, after DRAIN_DELAY ticks without a hit taken or landed. */
	public static final float DRAIN = 2f;
	public static final int DRAIN_DELAY = 200;

	/** v0.17.7 (user): a bare-handed hit out of the suit deals 5 (the fist's 1 + 4 from the herb)... */
	public static final double UNSUITED_MELEE = 5.0;
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
	public static final float MAX_HIT = com.projecthero.mod.hero.PowerRank.STREET.ultimateMax;
	/** Arrows and bullets below this much damage bounce off (the Iron Man bulletproof rule). */
	public static final float DEFLECT_LIMIT = 15f;

	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// R (v0.17.7: R is the Warrior's Combo in the suit too; Shift+R the Kinetic Pulse, or Kinetic Overload on a full bar)
	public static final float SUITED_COMBO_JAB = 8f;
	public static final float SUITED_COMBO_CROSS = 9f;
	public static final float SUITED_COMBO_KICK = 10f;
	public static final float PULSE_COST = 30f;
	public static final float PULSE_DAMAGE = 10f; // v0.17.7 (user): was 8
	public static final double PULSE_RADIUS = 5.0;
	public static final int PULSE_COOLDOWN = 50; // tester round: was 30
	public static final float OVERLOAD_DAMAGE = 20f; // v0.17.7 Street ultimate: was 30 (15 at the edge)
	public static final double OVERLOAD_RADIUS = 10.0;
	public static final int OVERLOAD_COOLDOWN = 100;

	// G
	public static final float CLAW_DAMAGE = 7f; // tester round: was 6, then 5; v0.17.7 Street melee: 7
	public static final float CLAW_FINISH_DAMAGE = 9f; // was 9, then 8; v0.17.7 Street: 9
	public static final double CLAW_REACH = 3.5;
	public static final int CLAW_STEP = 6;
	public static final int CLAW_COOLDOWN = 40; // was 24
	public static final int BLEED_TICKS = 80;
	public static final float BLEED_DAMAGE = 1f;
	public static final double POUNCE_RANGE = 12.0;
	public static final double POUNCE_SPEED = 1.6;
	public static final float POUNCE_LAND_DAMAGE = 8f;
	public static final float POUNCE_STRIKE_DAMAGE = 10f; // v0.17.7 Street ability: was 12
	public static final int POUNCE_PIN_TICKS = 40;
	public static final int POUNCE_COOLDOWN = 240; // was 200

	// X
	public static final double LEAP_SPEED = 1.62; // about 12 blocks up
	public static final double LEAP_FORWARD = 0.55;
	public static final int LEAP_COOLDOWN = 60;
	public static final int LEDGE_TICKS = 60;
	public static final double DROP_SPEED = 2.4;
	public static final float DROP_DAMAGE = 10f;
	public static final double DROP_RADIUS = 4.0;
	public static final int DROP_COOLDOWN = 160; // was 80

	// Z
	public static final float DAGGER_DAMAGE = 8f;
	public static final int DAGGER_BOUNCES = 3;
	public static final double DAGGER_SPEED = 1.7;
	public static final double DAGGER_RANGE = 14.0; // was 24
	public static final double DAGGER_HOP = 6.0; // was 10
	public static final int DAGGER_COOLDOWN = 140; // was 100
	public static final double FLASH_RADIUS = 6.0;
	public static final int FLASH_TICKS = 60;
	public static final int FLASH_COOLDOWN = 200;

	// V
	public static final double STALK_SEEN_WITHIN = 6.0;
	public static final float STALK_BACKSTAB = 3f; // on mobs
	public static final float STALK_BACKSTAB_PLAYER = 1.75f; // tester round: PvP
	public static final int STALK_COOLDOWN = 200; // was 40
	public static final double SENSES_RANGE = 40.0;
	public static final int SENSES_COOLDOWN = 0; // a toggle (was 400)
	public static final int FOOTPRINTS = 10;

	// C
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
	public static final int KNIFE_COOLDOWN = 80;
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
	public static final int RESOLVE_COOLDOWN = 100 * 20;
	/** The purple aura is puffed out every this many ticks while it lasts. */
	public static final int RESOLVE_PARTICLE_EVERY = 3;

	private BlackPantherConfig() {
	}
}
