package com.projecthero.mod.sentry;

/**
 * v0.17.5 Sentry &amp; the Void: every number (listed again in {@code docs/SENTRY_VOID_REFERENCE.md}). Damage is pitched at
 * Kryptonian / Thor parity (Kryptonian punch 32, Solar Flare 120): A Million Suns and Erase are the two big hitters. Ticks unless
 * named otherwise; meters are 0..100.
 */
public final class SentryConfig {
	private SentryConfig() {
	}

	// ---------------------------------------------------------------- meters
	public static final float SUN_MAX = 100f;
	public static final float VOID_MAX = 100f;
	/** Sun gained a second: direct sunlight, daytime shade, night under the sky, the dark (none). */
	public static final float SUN_DIRECT_PER_SECOND = 6.0f;
	public static final float SUN_SHADE_PER_SECOND = 2.0f;
	public static final float SUN_NIGHT_PER_SECOND = 0.5f;
	public static final float SUN_DARK_PER_SECOND = 0.0f;

	/**
	 * Round 3 (2026-11-18, user: "only make the players void bar increase when they use their powers, being out at night shouldnt do that
	 * ... sleeping lowers void bar a lot and doing tasks like fishing and stuff should also make it drain faster, void bar should drain
	 * slowly by default"): the Void rises only from the powers (each move's own gain, and being merged with the Void). Night, the dark,
	 * heavy hits and kills feed nothing any more. Out of the merged form it drains slowly on its own, a little faster in direct sunlight.
	 */
	public static final float VOID_DRAIN_PER_SECOND = -0.05f;
	/** On top of the drain in direct sunlight (so -0.3 a second in all). */
	public static final float VOID_DAYLIGHT_PER_SECOND = -0.25f;
	/** In bed: this a second while asleep... */
	public static final float VOID_SLEEP_PER_SECOND = -1.0f;
	/** ...and a night slept through (woken by the morning) takes this much more at once. */
	public static final float VOID_NIGHT_SLEPT = -40f;
	/** Calm everyday tasks: a bobber out in the water drains this much more a second... */
	public static final float VOID_FISHING_PER_SECOND = -0.15f;
	/** ...and each finished task takes a chunk: a fish (or anything) reeled in, a ripe crop harvested, a trade, animals bred, a disc played. */
	public static final float VOID_TASK_FISH = -2.0f;
	public static final float VOID_TASK_HARVEST = -0.4f;
	public static final float VOID_TASK_TRADE = -1.5f;
	public static final float VOID_TASK_BREED = -1.5f;
	public static final float VOID_TASK_MUSIC = -1.0f;
	/** Merged with the Void (Void Form) it climbs this a second on its own (was 1.0). */
	public static final float VOID_FORM_PER_SECOND = 0.5f;
	/** Every Void gain (passive and per event) is multiplied by this while merged with the Void... */
	public static final float MERGED_VOID_GAIN = 1.5f;
	/** ...and by this while the Sentry Suit (top + trousers) is worn; the suit also multiplies the Sun gained a second by {@link #SUIT_SUN_GAIN}. */
	public static final float SUIT_VOID_GAIN = 0.6f;
	public static final float SUIT_SUN_GAIN = 1.5f;
	// (round 3: heavy hits taken and kills no longer feed the Void -- only the powers do)
	/** The thresholds. */
	public static final float VOID_WHISPERS = 50f;
	public static final float VOID_MISFIRE = 75f;
	public static final float VOID_DIMMING = 90f;
	/** Above {@link #VOID_MISFIRE}: the chance a Sentry key misfires into a Void move. */
	public static final float MISFIRE_CHANCE = 0.12f;
	/** How often the meters are re-sent while they tick in place. */
	public static final int SYNC_INTERVAL = 10;

	// ---------------------------------------------------------------- the body
	/** v0.17.5 rework (user: "Make his max hp 40"): +20 on the base 20 = 40, merged with the Void too (its buffs are damage and cooldowns). */
	public static final double SENTRY_HEALTH_BONUS = 20.0;
	public static final double VOID_HEALTH_BONUS = 20.0;
	/** Damage taken is cut by this (Kryptonian: 80%). */
	public static final float SENTRY_RESIST = 0.70f;
	public static final float VOID_RESIST = 0.75f;
	public static final double SENTRY_MELEE_BONUS = 12.0;
	public static final double VOID_MELEE_BONUS = 16.0;
	public static final double SPEED_BONUS = 0.30;
	/** Sun-fed healing: 1 HP every this many ticks while Sun &gt; 0 (costs {@link #REGEN_SUN} each). */
	public static final int REGEN_INTERVAL = 40;
	public static final float REGEN_SUN = 0.5f;

	// ---------------------------------------------------------------- Sentry kit (moveset 2, 2026-11-18)
	/**
	 * R Golden Fury: a three-punch flying combo. A target out past arm's reach (up to {@link #FURY_LUNGE_RANGE}) is closed on with a
	 * golden lunge; the 3rd punch is the Haymaker that launches it far (a launched creature that slams into a wall takes
	 * {@link #FURY_IMPACT_DAMAGE} more: "through the mountain").
	 */
	public static final float FURY_DAMAGE = 22f;
	public static final float FURY_HAYMAKER_DAMAGE = 34f;
	public static final double FURY_REACH = 4.5;
	public static final double FURY_LUNGE_RANGE = 9.0;
	public static final double FURY_LAUNCH_SPEED = 3.0;
	public static final double FURY_LAUNCH_LIFT = 0.75;
	public static final int FURY_LAUNCH_TICKS = 30;
	public static final float FURY_IMPACT_DAMAGE = 12f;
	public static final int FURY_COOLDOWN = 8;
	public static final int FURY_COMBO_WINDOW = 30;
	public static final float FURY_COST = 2f;

	/**
	 * Hold G Solar Gaze (round 3, user: "Make Solar Gaze his G move, remove the time limit on it, dont make it auto track, nerf its damage to
	 * make it as powerful as supermans heat vision"): the Kryptonian's Heat Vision numbers -- 4 every 5 ticks (16 a second), 32 blocks,
	 * 5 s of fire, blocks set alight once a second (griefing rule) -- held as long as he likes (it ends when the Sun runs out), exactly
	 * along his look (an exact ray, no snapping onto creatures near it). The colour still heats to orange after {@link #GAZE_HOT_TICKS}
	 * (looks only).
	 */
	public static final float GAZE_DAMAGE = 4f;
	public static final int GAZE_DAMAGE_INTERVAL = 5;
	public static final int GAZE_FIRE_SECONDS = 5;
	public static final int GAZE_HOT_TICKS = 40;
	public static final double GAZE_RANGE = 32.0;
	public static final float GAZE_COST_PER_SECOND = 5f;
	public static final int GAZE_COOLDOWN = 60;
	public static final float GAZE_CLASH_POWER = 1.5f;
	public static final float GAZE_VOID_PER_SECOND = 0.17f;

	/** Hold G Disassemble: the creature is lifted (bosses pinned) and unravels; at {@link #UNMAKE_TEAR_TICKS} it comes apart. Let go early: thrown. */
	public static final double UNMAKE_RANGE = 20.0;
	public static final float UNMAKE_TICK_DAMAGE = 2f;
	public static final int UNMAKE_TICK_INTERVAL = 5;
	public static final int UNMAKE_TEAR_TICKS = 40;
	public static final float UNMAKE_TEAR_DAMAGE = 50f;
	public static final int UNMAKE_MAX_TICKS = 100;
	public static final float UNMAKE_THROW_DAMAGE = 12f;
	public static final double UNMAKE_THROW_SPEED = 2.4;
	public static final float UNMAKE_COST = 10f;
	public static final float UNMAKE_TEAR_VOID = 1.7f;
	public static final int UNMAKE_COOLDOWN = 80;

	/** Shift+G Skyward Hurl: grab the creature in reach and throw it straight up into the sky (the fall does the rest). */
	public static final double HURL_REACH = 6.0;
	public static final float HURL_DAMAGE = 16f;
	public static final double HURL_SPEED = 3.2;
	/** A boss (not liftable) is slammed instead: this much, and Slowness III for {@link #HURL_BOSS_SLOW_TICKS}. */
	public static final float HURL_BOSS_DAMAGE = 30f;
	public static final int HURL_BOSS_SLOW_TICKS = 60;
	public static final float HURL_COST = 14f;
	public static final int HURL_COOLDOWN = 200;

	/** Z Sunfall: from the ground he leaps up first; then a fist-first dive and a sonic-boom slam (inner hit, then the outer ring). */
	public static final double SUNFALL_LEAP = 1.5;
	public static final int SUNFALL_LEAP_TICKS = 14;
	public static final double SUNFALL_SPEED = 2.8;
	public static final int SUNFALL_MAX_TICKS = 60;
	public static final float SUNFALL_DAMAGE = 34f;
	public static final double SUNFALL_RADIUS = 6.0;
	public static final float SUNFALL_RING_DAMAGE = 12f;
	public static final double SUNFALL_RING_RADIUS = 11.0;
	public static final float SUNFALL_COST = 12f;
	public static final int SUNFALL_COOLDOWN = 140;

	/** Shift+Z A Million Suns: a 4 s wind-up rising into the air (damage taken halved meanwhile), then the explosion of light. */
	public static final int SUNS_CHARGE_TICKS = 80;
	public static final float SUNS_CHARGE_RESIST = 0.5f;
	public static final float SUNS_DAMAGE = 150f;
	public static final float SUNS_EDGE_DAMAGE = 70f;
	public static final double SUNS_RADIUS = 14.0;
	public static final float SUNS_COST = 60f;
	public static final float SUNS_VOID = 8f;
	public static final int SUNS_COOLDOWN = 1200;

	/** C Golden Guardian: heals and shields allies; the undead in the light burn. */
	public static final double GUARDIAN_RADIUS = 10.0;
	public static final float GUARDIAN_HEAL = 8f;
	public static final int GUARDIAN_REGEN_TICKS = 120;
	public static final int GUARDIAN_ABSORB_TICKS = 200;
	public static final float GUARDIAN_UNDEAD_DAMAGE = 12f;
	public static final float GUARDIAN_COST = 20f;
	public static final float GUARDIAN_VOID = -8f;
	public static final float GUARDIAN_VOID_PER_ALLY = -1f;
	public static final int GUARDIAN_COOLDOWN = 240;

	/**
	 * Shift+R Solar Flare (round 3, new; user: "Make shift+R an aoe ability"): a burst of sunlight all round him -- {@link #FLARE_DAMAGE} at
	 * the centre falling to {@link #FLARE_EDGE_DAMAGE} at {@link #FLARE_RADIUS}, thrown back and dazzled (Blindness for mobs, a short
	 * Glowing), the undead burn x{@link #FLARE_UNDEAD} and catch fire.
	 */
	public static final double FLARE_RADIUS = 8.0;
	public static final float FLARE_DAMAGE = 24f;
	public static final float FLARE_EDGE_DAMAGE = 12f;
	public static final float FLARE_UNDEAD = 1.5f;
	public static final int FLARE_BLIND_TICKS = 60;
	public static final double FLARE_KNOCKBACK = 1.6;
	public static final float FLARE_COST = 18f;
	public static final float FLARE_VOID = 0.6f;
	public static final int FLARE_COOLDOWN = 200;

	/**
	 * Shift+V Golden Sense (round 3, new utility replacing Calm): his senses flare -- every creature within {@link #SENSE_RADIUS} glows
	 * (seen through walls) for {@link #SENSE_TICKS}, and he gets Night Vision for {@link #SENSE_NIGHT_VISION_TICKS}.
	 */
	public static final double SENSE_RADIUS = 40.0;
	public static final int SENSE_TICKS = 200;
	public static final int SENSE_NIGHT_VISION_TICKS = 1200;
	public static final float SENSE_COST = 10f;
	public static final int SENSE_COOLDOWN = 600;

	/** V Molecular Shield (round 3: V; was Shift+C): a golden aura; projectiles turn to light (feeding Sun), hits taken are cut further, melee is deflected. */
	public static final double MSHIELD_RADIUS = 3.5;
	public static final int MSHIELD_TICKS = 160;
	public static final float MSHIELD_SUN_PER_PROJECTILE = 2f;
	public static final float MSHIELD_RESIST = 0.4f;
	public static final float MSHIELD_DEFLECT_DAMAGE = 6f;
	public static final float MSHIELD_COST = 15f;
	public static final int MSHIELD_COOLDOWN = 400;

	/** Flight: double-tap Space (Sentry and merged alike). X: the sonic-boom dash (regular Sentry). */
	public static final double FLIGHT_CRUISE_BPS = 22.0;
	public static final double FLIGHT_SPRINT_BPS = 44.0;
	/** A take-off this soon after a landing is ignored (vanilla double-tap can drop flight the same tick). */
	public static final int FLIGHT_TOGGLE_DEBOUNCE = 6;
	public static final double DASH_BLOCKS = 18.0;
	public static final float DASH_DAMAGE = 14f;
	public static final float DASH_COST = 5f;
	public static final int DASH_COOLDOWN = 80;
	/**
	 * Round 3 (user: "if sonic boom is used while flying it does something different, makes player fly 50% faster for 30 seconds"): X while
	 * flying is the Flight Boost -- flight speed x{@link #BOOST_SPEED} for {@link #BOOST_TICKS}, a sonic boom as it starts and a golden
	 * contrail behind him; its cooldown runs from the start (so 15 s after it ends).
	 */
	public static final double BOOST_SPEED = 1.5;
	public static final int BOOST_TICKS = 600;
	public static final float BOOST_COST = 10f;
	public static final int BOOST_COOLDOWN = 900;

	/** Round 3: the superhero landing (fast flight touchdown, the shared {@code HeroLanding}): an impact around him, by its strength 0..1. */
	public static final float LANDING_DAMAGE = 8f;
	public static final float LANDING_DAMAGE_PER_STRENGTH = 14f;
	public static final double LANDING_RADIUS = 3.0;
	public static final double LANDING_RADIUS_PER_STRENGTH = 2.5;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	/** H Let It Out (round 3: H; was Shift+V): locked this long after the Void is thrown out of a takeover. (v0.17.5 rework: no Void gate any more, the trial replaces it.) */
	public static final int LET_OUT_LOCK_TICKS = 6000;
	/** The trial ("Hold the Light" minigame): it lasts this long on the client... */
	public static final int TRIAL_TICKS = 160;
	/** ...a win counts only this far in on the server clock (nobody honest fills the bar faster)... */
	public static final int TRIAL_MIN_WIN_TICKS = 50;
	/** ...and with no report this long after it should have ended, it counts as lost. */
	public static final int TRIAL_GRACE_TICKS = 100;
	/** A lost trial: the Void jumps by this (flat: not scaled by the suit or merging). */
	public static final float TRIAL_LOSE_VOID = 15f;
	/** Merged with the Void: every hit does this much more, and the Void moves recharge this much faster (cooldown x). */
	public static final float MERGED_DAMAGE = 1.5f; // round 3 (user: "50% more damage"; was 1.25)
	public static final float MERGED_COOLDOWN = 0.75f;
	/** The suit fades between the Sentry and the Void suit over this many ticks. */
	public static final int FORM_FADE_TICKS = 50;

	// ---------------------------------------------------------------- Void Form kit (moveset 2, 2026-11-18)
	/** R Tendril Lash: two whips (a 100-degree cone), then the Impale (one creature, further out, pinned and pulled in). */
	public static final float LASH_DAMAGE = 16f;
	public static final double LASH_RANGE = 6.0;
	public static final float IMPALE_DAMAGE = 30f;
	public static final double IMPALE_RANGE = 9.0;
	public static final int IMPALE_PIN_TICKS = 20;
	public static final int LASH_COOLDOWN = 10;
	public static final int LASH_COMBO_WINDOW = 30;
	public static final float LASH_VOID = 0.3f;

	/** Shift+R Erase: the creature is seized and darkens for {@link #ERASE_DELAY_TICKS}, then wiped into a burnt shadow. */
	public static final float ERASE_DAMAGE = 110f;
	public static final double ERASE_RANGE = 24.0;
	public static final int ERASE_DELAY_TICKS = 15;
	public static final int ERASE_COOLDOWN = 400;
	public static final float ERASE_VOID = 2f;

	/** G Shadow Pool: a pool of shadow spreads on the ground; whoever stands in it is held down and hurt. */
	public static final double POOL_RANGE = 24.0;
	public static final double POOL_START_RADIUS = 2.0;
	public static final double POOL_RADIUS = 6.0;
	public static final int POOL_GROW_TICKS = 40;
	public static final int POOL_TICKS = 120;
	public static final float POOL_DAMAGE = 4f;
	public static final int POOL_DAMAGE_INTERVAL = 10;
	public static final int POOL_COOLDOWN = 240;
	public static final float POOL_VOID = 1f;

	/** Shift+G Swallow: the creature is shut inside a black void sphere (held, blind), then spat out. */
	public static final double SPHERE_RANGE = 16.0;
	public static final int SPHERE_TICKS = 60;
	public static final float SPHERE_DAMAGE = 6f;
	public static final int SPHERE_DAMAGE_INTERVAL = 10;
	public static final float SPHERE_SPIT_DAMAGE = 18f;
	/** A creature that dies inside is absorbed: the body heals this much. */
	public static final float SPHERE_ABSORB_HEAL = 6f;
	public static final int SPHERE_COOLDOWN = 360;
	public static final float SPHERE_VOID = 1.7f;

	/** Z Nightmare: a wave of horror; mobs flee, players see darkness and reel. */
	public static final double NIGHTMARE_RADIUS = 12.0;
	public static final float NIGHTMARE_DAMAGE = 8f;
	public static final int NIGHTMARE_TICKS = 100;
	public static final int NIGHTMARE_COOLDOWN = 400;
	public static final float NIGHTMARE_VOID = 1.7f;

	/** Shift+Z Shadow Eclipse: a wind-up, then a black sun over him; everything under it is drained, he regenerates; it ends in a collapse. */
	public static final int ECLIPSE_WINDUP_TICKS = 30;
	public static final double ECLIPSE_RADIUS = 16.0;
	public static final int ECLIPSE_TICKS = 200;
	public static final float ECLIPSE_DAMAGE = 6f;
	public static final float ECLIPSE_HEAL = 2f;
	public static final float ECLIPSE_COLLAPSE_DAMAGE = 40f;
	public static final int ECLIPSE_COOLDOWN = 1200;
	public static final float ECLIPSE_VOID = 3f;

	/** C Blot Out: drains life and light around him to heal (his remaining Sun burns into health too). */
	public static final double BLOT_RADIUS = 10.0;
	public static final float BLOT_DAMAGE = 10f;
	public static final float BLOT_HEAL_PER_VICTIM = 3f;
	public static final float BLOT_HEAL_MAX = 15f;
	/** Each this much Sun burned heals 1. */
	public static final float BLOT_SUN_PER_HEALTH = 5f;
	public static final int BLOT_COOLDOWN = 300;
	public static final float BLOT_VOID = 1f;

	/** Shift+C Shadow Clones: shadow doubles rise from his shadow and hunt. */
	public static final int CLONE_COUNT = 3;
	public static final int CLONE_TICKS = 160;
	public static final double CLONE_SPEED = 0.35;
	public static final double CLONE_HUNT_RANGE = 16.0;
	public static final float CLONE_DAMAGE = 10f;
	public static final int CLONE_STRIKE_INTERVAL = 20;
	public static final int CLONE_COOLDOWN = 600;
	public static final float CLONE_VOID = 2f;

	/** X Shadow Step (moveset 2): a blink along his look through a smear of shadow (no light rule any more); things on the path are hit. */
	public static final double STEP_RANGE = 24.0;
	public static final float STEP_DAMAGE = 10f;
	public static final int STEP_COOLDOWN = 60;
	public static final float STEP_VOID = 0.35f;

	/**
	 * V Umbral Shroud (round 3, new; the Void's answer to the Molecular Shield): a cloak of darkness for {@link #SHROUD_TICKS} -- damage taken
	 * x(1 - {@link #SHROUD_RESIST}), projectiles reaching it are swallowed, whoever strikes him in melee is withered and blinded.
	 */
	public static final double SHROUD_RADIUS = 3.0;
	public static final int SHROUD_TICKS = 160;
	public static final float SHROUD_RESIST = 0.4f;
	public static final int SHROUD_WITHER_TICKS = 60;
	public static final int SHROUD_COOLDOWN = 400;
	public static final float SHROUD_VOID = 1f;

	/**
	 * Shift+V Shadow Meld (round 3, new utility): he sinks into the shadows -- invisible, faster (Speed {@link #MELD_SPEED_AMP} + 1), and every
	 * mob hunting him within {@link #MELD_RADIUS} loses him -- for {@link #MELD_TICKS}, or until he uses a Void attack.
	 */
	public static final int MELD_TICKS = 120;
	public static final int MELD_SPEED_AMP = 1;
	public static final double MELD_RADIUS = 32.0;
	public static final int MELD_COOLDOWN = 600;
	public static final float MELD_VOID = 0.6f;

	/** H back to the Sentry while merged (round 3: H; was Shift+V / V): only below this. */
	public static final float RETURN_MAX_VOID = 90f;

	// ---------------------------------------------------------------- the takeover
	/** Moveset 2 (user: "when the void takes control only make him attack once every 2 seconds with an ability or an M1"): one shared cooldown
	 * for every attack of the takeover's brain -- a Void move or a fist swing alike. (The decay disc and its weak-mob damage are not attacks.) */
	public static final int TAKEOVER_ATTACK_INTERVAL = 40;
	/** Its fists (an M1). */
	public static final float TAKEOVER_FIST_DAMAGE = 18f;
	public static final double TAKEOVER_FIST_REACH = 3.5;
	/** Nothing for the first 30 s. */
	public static final int MIND_DELAY_TICKS = 600;
	/** A failed room adds 20 s. */
	public static final int ROOM_FAIL_TICKS = 400;
	/** A room can't honestly be won faster than this (server check). */
	public static final int ROOM_MIN_TICKS = 80;
	public static final int ROOMS = 3;
	/** Outside help: grip drained per right-click tick of a helper (held use repeats every 4 ticks). */
	public static final float HELP_PER_CLICK = 2.0f;
	/** Grip creeps back a second (day / night). */
	public static final float GRIP_REGEN_DAY = 0.25f;
	public static final float GRIP_REGEN_NIGHT = 1.0f;
	/** When the grip breaks a room open it comes back to this. */
	public static final float GRIP_AFTER_BREAK = 60f;
	/** The body flies at this (blocks a tick) and hunts within this. */
	public static final double TAKEOVER_SPEED = 0.55;
	public static final double TAKEOVER_HUNT_RANGE = 24.0;
	public static final double TAKEOVER_WANDER_RANGE = 128.0;
	/** Rework 2 (2026-11-17): it hangs this high above the ground under it (clamped under the build height)... */
	public static final double TAKEOVER_HOVER_HEIGHT = 50.0;
	/** ...rising at most this fast (blocks a tick; it eases in over the last ~10 blocks: ~5-6 s from the ground)... */
	public static final double TAKEOVER_RISE_SPEED = 0.6;
	/** ...and comes down only for a strong target, slowly (blocks a tick). */
	public static final double TAKEOVER_DESCEND_SPEED = 0.25;
	/** A strong target this far away horizontally (and no more than 80 below / 40 above) brings it down. */
	public static final double TAKEOVER_ENGAGE_RANGE = 64.0;
	/** "Strong": any player, a {@code #c:bosses} mob, or max health / armour at least this. */
	public static final float TAKEOVER_STRONG_HEALTH = 40f;
	public static final double TAKEOVER_STRONG_ARMOR = 15.0;
	/** No progress for this long while it wants to move: it decays its way out. */
	public static final int TAKEOVER_STUCK_TICKS = 20;
	/** The sphere it carves around itself when stuck (and the most blocks one carve may take). */
	public static final double TAKEOVER_CARVE_RADIUS = 2.5;
	public static final int TAKEOVER_CARVE_MAX = 96;
	/** Weak mobs inside the spreading destruction take this much every second. */
	public static final float TAKEOVER_CONSUME_DAMAGE = 6f;
	/** A kill is absorbed: it heals the body this much. */
	public static final float ABSORB_HEAL = 4f;
	/** Void after the takeover: thrown out, death. */
	public static final float VOID_AFTER_WIN = 40f;
	public static final float VOID_AFTER_DEATH = 60f;
	public static final int KNEEL_TICKS = 60;

	// ---------------------------------------------------------------- the void matter
	/**
	 * Rework 2 (2026-11-17): the destruction is a disc on the ground under the Void, 8 blocks at first, growing 0.4 a second for the
	 * whole takeover up to 56 (reached after 2 minutes: ~9,850 columns).
	 */
	public static final double MATTER_START_RADIUS = 8.0;
	public static final double MATTER_GROWTH_PER_SECOND = 0.4;
	public static final double MATTER_MAX_RADIUS = 56.0;
	/** Each column is eaten from its original surface down this many layers at the edge... */
	public static final int MATTER_DEPTH_EDGE = 3;
	/** ...to this many under the centre (a bowl). Trees, plants and builds above the surface go too. */
	public static final int MATTER_DEPTH_CENTRE = 10;
	/** Blocks that start decaying per tick at most, out of this many column tries. */
	public static final int MATTER_BLOCKS_PER_TICK = 48;
	public static final int MATTER_TRIES_PER_TICK = 160;
	/** A decaying block fades to black over this many ticks (plus up to a quarter more by position) before it goes. */
	public static final int DECAY_TICKS = 40;
	/** Blocks carved out of its way (stuck, or a wall ahead) fade faster. */
	public static final int CARVE_DECAY_TICKS = 14;
	/** Decay batches are sent to players within this of the batch. */
	public static final double DECAY_SYNC_RANGE = 192.0;
	/** The old void matter block (kept registered for old worlds) turns to nothing after this. */
	public static final int MATTER_DISSOLVE_TICKS = 30;
	/** The eaten world comes back this long after control returns... */
	public static final int RESTORE_DELAY_TICKS = 1200;
	/** ...this many blocks a tick (in loaded chunks; the rest wait for theirs to load). */
	public static final int RESTORE_PER_TICK = 64;

	/** A burnt shadow on the ground fades after this. */
	public static final int SHADOW_TICKS = 2400;

	// ---------------------------------------------------------------- the Sentry Suit
	/** The suit mends 1 durability every this many ticks, worn or anywhere in the inventory. */
	public static final int SUIT_REPAIR_INTERVAL = 60;
}
