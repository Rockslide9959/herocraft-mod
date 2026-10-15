package com.projecthero.mod.spider;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.1: every Spider-Man tunable from the revamp, at {@code config/projecthero_spider_man.json} (loaded through the
 * shared {@link VersionedConfig}, see {@code docs/CONFIGS.md}). Times are game ticks (20 a second); costs are Web
 * Reserve points (200 full, 400 once bonded with a Symbiote).
 *
 * <p>{@link Webs} is server preference (web lifetime, the per-player cap, the block-grab switch, ...); {@link Moves}
 * is balance owned by the mod, reset to today's numbers whenever a release changes them.
 */
public final class SpiderManConfig {
	private static SpiderManConfig instance = new SpiderManConfig();

	public Integer configVersion;

	public Webs webs = new Webs();
	public Moves moves = new Moves();
	public Struggle struggle = new Struggle(); // v0.16.4

	/** One move: its cooldown (ticks), its Web Reserve cost and (where it hits) its damage. */
	public static final class Move {
		public int cooldown;
		public float cost;
		public float damage;

		public Move() {
		}

		Move(int cooldown, float cost, float damage) {
			this.cooldown = cooldown;
			this.cost = cost;
			this.damage = damage;
		}
	}

	public static final class Webs {
		/** Woven webs, tightropes and web walls fade after this many seconds. */
		public int fadeSeconds = 300;
		/** At most this many web structures per Spider-Man; a new one past the cap replaces his oldest. */
		public int maxStructuresPerPlayer = 12;
		/** Shift+Z Grab & Hurl may rip a loose block out of the world and throw it (false: mobs only). */
		public boolean blockGrabbing = true;
		/** A non-ally touching one of his webs pings his Spider-Sense from up to this far away. */
		public double alarmRange = 160.0;
		/** Per web: at most one alarm this often. */
		public int alarmCooldownTicks = 60;
		/** Tightrope: the two anchors may be at most this far apart. */
		public double tightropeMaxLength = 40.0;
		/** Weave Mode: at most this many radial strands, each at most this long. */
		public int weaveMaxStrands = 10;
		public double weaveStrandLength = 30.0;
		/** Anyone who is not Spider-Man or his squad is slowed to this fraction while touching a web (cobweb is 0.25). */
		public double strangerSlowFactor = 0.08;
		/** Ticks a key must be held before the hold move takes over (the tap fires on a shorter release). */
		public int holdTicks = 8;
		/** v0.16.1: Web Bomb / Impact Web / Web Blossom also spin REAL cobweb blocks (temporary, never dropped as items). */
		public boolean placeCobwebs = true; // v0.16.2: back on (user) -- Web Bomb / Impact Web / Blossom leave real cobwebs again
		/** How long those cobwebs last (ticks) before they are taken away again. */
		public int cobwebTicks = 360;
		/** At most this many cobwebs per cast, and this many of his cobwebs in the world at once (the oldest go first). */
		public int cobwebsPerCast = 18;
		public int cobwebsMax = 256;
	}

	public static final class Moves {
		// R -- web shooters
		/**
		 * v0.16.1: how long every web debuff lasts on an ordinary enemy -- Web Shot's slow, rooted by a Web Bomb, pinned by an
		 * Impact Web, trapped by Web Blossom, stuck in a web wall or strand: 18 s. Bosses get bossWebDebuffTicks of a
		 * weaker version (lower Slowness, never fully pinned).
		 */
		public int webDebuffTicks = 360;
		/** v0.16.1: a projectile Spider-Sense dodges is sent straight back at its shooter this often (else caught / swatted aside). */
		public float deflectReturnChance = 0.4f;
		// ---- v0.16.1 (later): faster web swinging (config v3). Per-tick velocities in blocks / tick. ----
		/** Forward key push while swinging (was 0.100). */
		public double swingForwardAccel = 0.13;
		/** Push along the arc through its bottom, strongest with the anchor overhead (was 0.042). */
		public double swingBottomPush = 0.068;
		/** Extra pull down while falling on a taut line -- the gravity pump into the bottom of the arc (was 0). */
		public double swingGravityPump = 0.03;
		/** Nudge toward the facing direction (was 0.022). */
		public double swingFacingAssist = 0.03;
		/** Rope snap-back per block of overshoot -- how quickly a fresh line goes taut (was 0.14). */
		public double swingTension = 0.2;
		/** Terminal swing speed (was 3.2). */
		public double swingMaxSpeed = 4.6;
		/** Velocity multiplier on letting go in the air (was 1.0 -- untouched). */
		public double swingReleaseBoost = 1.15;
		/** Lift added on letting go in the air (was 0). */
		public double swingReleaseLift = 0.12;
		/** A new line fired within this many ticks of letting go chains: it adds swingChainBoost along the travel (was none). */
		public int swingChainWindowTicks = 20;
		public double swingChainBoost = 0.35;
		// ---- v0.16.4 (user): air momentum + aerial control (config v11). See SpiderAirControl. ----
		/** After a swing release / zip / glide he keeps his horizontal speed in the air (no air drag) until he lands. */
		public boolean airMomentum = true;
		/** How fast WASD / the look turn his airborne heading (degrees a tick). */
		public double airTurnDegPerTick = 6.0;
		/** Push along the held direction each tick (blocks / tick^2)... */
		public double airAccel = 0.03;
		/** ...but only up to this horizontal speed (faster than this, steering only turns him). */
		public double airAccelCap = 1.0;
		/** S in the air: this fraction of his horizontal speed is shed each tick. */
		public double airBrake = 0.06;
		public int bossWebDebuffTicks = 90;
		public int impactPinTicks = 360;
		public int blossomTrapTicks = 360;
		public Move webShot = new Move(0, 2.0f, 2.0f); // v0.16.2 (user): no cooldown
		public Move webBomb = new Move(0, 15.0f, 3.0f); // v0.16.2 (user): no cooldown
		public double webBombRadius = 6.0; // v0.16.2 (user): bigger splash (was 4)
		public int webBombRootTicks = 360;
		public Move impactWeb = new Move(30, 12.0f, 10.0f);
		/** A full Impact Web charge (ticks of holding past the hold threshold) adds half the damage again. */
		public int impactChargeTicks = 10; // v0.16.2 (user): full charge in 0.5 s
		// G -- zip
		public Move pointZip = new Move(10, 5.0f, 0.0f);
		public Move webYank = new Move(0, 6.0f, 0.0f); // v0.16.2 (user): no cooldown -- the Web Reserve cost paces it
		public Move zipKick = new Move(100, 10.0f, 14.0f);
		public double zipKickRange = 30.0;
		// X -- melee
		public Move webStrike = new Move(50, 8.0f, 5.0f);
		/** The third Web Strike punch lands this much instead. */
		public float webStrikeFinisher = 9.0f;
		public double webStrikeRange = 30.0;
		public Move uppercut = new Move(120, 6.0f, 8.0f);
		/** After an uppercut, your hits keep the enemy in the air for this long. */
		public int airComboTicks = 60;
		public Move spiderCounter = new Move(160, 4.0f, 12.0f);
		/** How long the counter stance waits for a hit. */
		public int counterStanceTicks = 20;
		// Z -- throws
		public Move webWhip = new Move(80, 10.0f, 7.0f);
		public double webWhipRadius = 4.0;
		public Move grabHurl = new Move(100, 10.0f, 10.0f);
		public double grabRange = 16.0;
		public Move swingThrow = new Move(60, 10.0f, 6.0f);
		// C -- web building
		public Move tightrope = new Move(10, 10.0f, 0.0f);
		public Move webWall = new Move(200, 25.0f, 0.0f);
		/** Weave Mode: every radial strand costs this; finishing the web (the spiral rings) costs weaveFinishCost. */
		public Move weave = new Move(300, 8.0f, 0.0f);
		public float weaveFinishCost = 30.0f;
		// V -- big moves
		public Move spiderPulse = new Move(400, 10.0f, 0.0f);
		public int pulseHighlightTicks = 120;
		public double pulseRadius = 32.0;
		/** The pulse's dodge window: every hit with an attacker behind it is dodged for this long. */
		public int pulseDodgeTicks = 40;
		public Move webCocoon = new Move(200, 25.0f, 0.0f);
		public int webCocoonTicks = 600;
		/** Cocooned enemies take this much more damage from Spider-Man and his squad. */
		public float cocoonDamageMultiplier = 1.6f;
		public Move webBlossom = new Move(400, 75.0f, 40.0f);
		public int blossomChargeTicks = 40;
		// passive traversal
		public Move zipBoost = new Move(16, 3.0f, 0.0f);
		/** v0.16.1: Zip Boost needs a block to web onto within this range (it never webs onto air). */
		public double zipBoostRange = 50.0;
	}

	/**
	 * v0.16.4 (user): "if players get trapped in a web cocoon give them a minigame to break out, dont make it too easy" --
	 * a cocooned player mashes the strafe keys (A / D) ALTERNATELY to fill a struggle bar ({@code SpiderStruggle}). Progress
	 * runs 0..1; it breaks out at 1. Per server tick it loses {@code decayPerTick + decayFractionPerTick * progress}, plus
	 * {@code idleDecayPerTick} once no good press has come for {@code idleGraceTicks}. With these numbers a steady 8 alternate
	 * presses a second breaks out in about 6 s, 10 a second in about 3.7 s, and 6 or fewer a second never do.
	 */
	public static final class Struggle {
		/** Players can struggle out of a cocoon at all (false: only Spider-Man cuts them free, as before). */
		public boolean enabled = true;
		/** Progress per press of the OTHER key than last time. */
		public float gainPerPress = 0.05f;
		/** Progress lost when the same key is pressed twice in a row (the press does not count). */
		public float slipPenalty = 0.03f;
		public float decayPerTick = 0.006f;
		public float decayFractionPerTick = 0.010f;
		public int idleGraceTicks = 10;
		public float idleDecayPerTick = 0.0175f;
		/** Presses past this many in one second are ignored (anti-macro). */
		public int maxPressesPerSecond = 20;
	}

	public static SpiderManConfig get() {
		return instance;
	}

	public static Webs webs() {
		return instance.webs;
	}

	public static Moves moves() {
		return instance.moves;
	}

	public SpiderManConfig() {
	}

	public static final VersionedConfig<SpiderManConfig> SPEC = VersionedConfig
			.builder(SpiderManConfig.class, "projecthero_spider_man.json", SpiderManConfig::new)
			.balance("moves", "struggle")
			.introduce(1, "v0.16.1 the Spider-Man revamp")
			// v0.16.1 (later): every web debuff lasts 18 s (webDebuffTicks), Web Cocoon 30 s -- old files take the new numbers
			.resetBalance(2, "v0.16.1 18-second web debuffs, 30-second Web Cocoon")
			// v0.16.1 (later): faster web swinging -- stronger pump, higher top speed, release / chain momentum
			.resetBalance(3, "v0.16.1 faster web swinging")
			// v0.16.1 (later, user request): longer tightropes (32 -> 40 blocks) and weave strands (24 -> 30 blocks)
			.reset(4, "v0.16.1 tightropes 40 blocks, weave strands 30 blocks", "webs.tightropeMaxLength", "webs.weaveStrandLength")
			// v0.16.2 (user request): Web Yank has no cooldown
			.reset(5, "v0.16.2 Web Yank has no cooldown", "moves.webYank")
			// v0.16.2 (user request): Web Shot and Web Bomb have no cooldown; Impact Web charges fully in 0.5 s
			.reset(6, "v0.16.2 no Web Shot / Web Bomb cooldown, 0.5 s Impact Web charge", "moves.webShot", "moves.webBomb", "moves.impactChargeTicks")
			// v0.16.2 (user request): no more real cobweb blocks from his webs
			.reset(7, "v0.16.2 webs no longer place cobweb blocks", "webs.placeCobwebs")
			// v0.16.2 (user request): a bigger Web Bomb
			.reset(8, "v0.16.2 Web Bomb radius 6", "moves.webBombRadius")
			// v0.16.2 (user): cobwebs back on -- v7 turned them off by mistake
			.reset(9, "v0.16.2 cobwebs back on", "webs.placeCobwebs")
			// v0.16.4 (user request): the cocoon struggle minigame -- a new balance section, defaults fill in
			.introduce(10, "v0.16.4 cocoon struggle minigame")
			// v0.16.4 (user): no air drag after a swing + WASD aerial control (new keys only -- they take their defaults)
			.introduce(11, "v0.16.4 air momentum and aerial control")
			.build();

	public static void load() {
		instance = SPEC.load();
	}
}
