package com.projecthero.mod.allmight;

import com.projecthero.mod.config.VersionedConfig;

/**
 * Every All Might / One For All tunable in one place (v0.12.33) -- resource, abilities, forms, Full Cowl,
 * landing impacts and visuals. Nothing else in the package hard-codes a balance number. All times are game ticks
 * (20 per second); all distances are blocks.
 *
 * <p>v0.16.6: the OFA timer is replaced by Output; v0.16.7: the Strain bar by the safe limit and limb strain; there are five new Shift Smashes, Symbol of Peace,
 * and passing on / lending One For All -- all of their numbers are below as well. v0.16.7 (user): the One For All TIMER is back,
 * at 20 minutes -- its numbers are the one part that lives in a file, {@code config/projecthero_one_for_all.json} ({@link #timer()}),
 * so a stale file picks up a new default through {@link VersionedConfig}.
 */
public final class AllMightConfig {
	private AllMightConfig() {
	}

	private static final int S = 20;

	// ---------------------------------------------------------------- v0.16.7: the One For All timer (a JSON file)
	// User (v0.16.7): "give one for all the old timer system again but raise the time limit to 20 minutes". The v0.12.39 rules
	// with every number scaled x4 (the old timer was 300 s): being in the Power Form drains it 1 s a second; only the Base Form
	// refills it, at half that (a full refill takes twice the timer: 40 min, was 10); Plus Ultra (1/6 of it) and the United States
	// of Smash (1/3) spend a one-off amount; at 0:00 he is forced back to the Base Form and H is refused until 4 s have come back.
	public static final VersionedConfig<TimerFile> SPEC = VersionedConfig.builder(TimerFile.class, "projecthero_one_for_all.json", TimerFile::new)
			.balance("timer")
			// a file from before the timer came back (no configVersion: the v0.12.39 five-minute numbers) gets today's 20 minutes
			.reset(1, "v0.16.7 the One For All timer is back -- 20 minutes of Power Form (was 5)", "timer")
			.build();

	private static TimerFile file = new TimerFile();

	/** The file's root: {@code configVersion} and the {@code timer} section. */
	public static final class TimerFile {
		public Integer configVersion;
		public Timer timer = new Timer();
	}

	/** The timer, in seconds of Power Form. */
	public static final class Timer {
		/** A full timer: 1200 s = 20:00 of Power Form (v0.12.39: 300). */
		public float powerFormSeconds = 1200.0f;
		/** Being in the Power Form drains this many seconds of it every second. */
		public float drainPerSecond = 1.0f;
		/** Only the Base Form refills it: this many seconds every second (v0.12.39: 1 every 2 s) -- 0:00 to 20:00 takes 40 minutes. */
		public float rechargePerSecond = 0.5f;
		/** H into the Power Form is refused below this (v0.12.39: 1 point of 300, i.e. 1/300 of the timer). */
		public float minimumToTransformSeconds = 4.0f;
		/** Plus Ultra (C) spends this once when pressed, and is refused without it (v0.12.39: 50 of 300). */
		public float plusUltraCostSeconds = 200.0f;
		/** The United States of Smash spends this when the punch is cast, and the hold is refused without it (v0.12.39: 100 of 300). */
		public float unitedStatesCostSeconds = 400.0f;
		/** Below this he vents steam: he is running out (v0.12.39: 30 of 300). */
		public float lowSteamSeconds = 120.0f;
	}

	public static Timer timer() {
		return file.timer;
	}

	/** Reads (migrating) {@code config/projecthero_one_for_all.json}; called at start-up. */
	public static void load() {
		file = SPEC.load();
	}

	// ---------------------------------------------------------------- v0.16.7: Output, the safe limit and limb strain
	// User (v0.16.7): "come up with a better strain system for one for all". The design is docs/OFA_STRAIN.md. Output is a
	// percentage that landed hits charge up to your SAFE LIMIT (mastery); at or under the limit One For All (Full Cowl, every
	// Smash) is free. Plus Ultra (C) and the United States of Smash push Output PAST the limit, and every Smash thrown past it
	// strains the limb that threw it (right arm, left arm, legs). Strained limbs bruise and weaken; at 100 the limb breaks.
	/** Output never idles below this in the Power Form (the Full Cowl floor), percent. */
	public static final float OUTPUT_FLOOR = 5.0f;
	/** Output a landed Smash adds (once per Smash, however many it hits), and a plain punch (throttled), percentage points. */
	public static final float OUTPUT_PER_SMASH_HIT = 8.0f;
	public static final float OUTPUT_PER_PUNCH = 3.0f;
	/** Two punches closer together than this charge only once (no spam-clicking). */
	public static final int OUTPUT_PUNCH_SPACING_TICKS = 6;
	/** After this long without landing a hit, Output eases back down toward the floor at {@link #OUTPUT_DECAY_PER_SECOND}. */
	public static final int OUTPUT_DECAY_TICKS = 8 * S;
	public static final float OUTPUT_DECAY_PER_SECOND = 5.0f;
	/** Damage multiplier at Output o (0..100): {@code OUTPUT_DAMAGE_BASE + OUTPUT_DAMAGE_PER_100 * o / 100} -- x0.95 at 5%, x1.08 at 20%, x1.8 at 100%. */
	public static final float OUTPUT_DAMAGE_BASE = 0.9f;
	public static final float OUTPUT_DAMAGE_PER_100 = 0.9f;
	/** Shockwave width / radius multiplier at Output o: x1.04 at 20%, x1.15 at 45%, x1.4 at 100%. */
	public static final double OUTPUT_SIZE_BASE = 0.95;
	public static final double OUTPUT_SIZE_PER_100 = 0.45;
	/** The United States of Smash always fires at this Output (100%: past almost anyone's limit). */
	public static final float UNITED_STATES_OUTPUT = 100.0f;

	/** The safe limit (mastery) a new holder starts with, and its ceiling, percent. */
	public static final float MASTERY_START = 20.0f;
	public static final float MASTERY_MAX = 100.0f;
	/** Mastery every Smash thrown in the Power Form adds, plus the extra when it was thrown past the limit (the body adapts). */
	public static final float MASTERY_PER_SMASH = 0.15f;
	public static final float MASTERY_PER_OVER_SMASH = 0.35f;
	/** "Your body can take N% now" is said every time the limit crosses a multiple of this. */
	public static final float MASTERY_MILESTONE = 5.0f;

	/** Strain a Smash adds to its limb per percentage point of Output past the limit, times the move's weight (AllMightStrain.weightOf). */
	public static final float OVER_STRAIN_PER_POINT = 0.75f;
	/** HP an over-limit Smash costs him: the strain it added divided by this (never below 1 HP left). */
	public static final float OVER_STRAIN_HP_DIVISOR = 12.0f;
	/** Full Cowl past the limit (Plus Ultra) strains every limb this much a second at a full 100-point overshoot. */
	public static final float COWL_OVER_STRAIN_PER_SECOND = 0.8f;
	/** Limb strain tiers: Strained from 40 (x0.85 on that limb's Smashes), Hurt from 70 (x0.65), Broken at 100 (refused). */
	public static final float LIMB_STRAINED = 40.0f;
	public static final float LIMB_HURT = 70.0f;
	public static final float LIMB_BROKEN = 100.0f;
	public static final float LIMB_STRAINED_FACTOR = 0.85f;
	public static final float LIMB_HURT_FACTOR = 0.65f;
	/** A broken limb is usable again once it has healed down to this. */
	public static final float LIMB_MENDED = 50.0f;
	/** Healing per limb per second: the Base Form, Full Cowl at or under the limit (nothing heals past it); a broken limb heals at x0.6. */
	public static final float HEAL_BASE_FORM = 1.0f;
	public static final float HEAL_POWER_FORM = 0.5f;
	public static final float HEAL_BROKEN_FACTOR = 0.6f;
	/** Recovery: Regeneration adds this per second per level; sleeping in a bed this per second; food this per hunger point eaten. */
	public static final float HEAL_REGENERATION_PER_LEVEL = 1.5f;
	public static final float HEAL_SLEEPING = 20.0f;
	public static final float HEAL_PER_FOOD_POINT = 2.5f;
	/** Strain and healing are applied in steps this many ticks apart (one synced write per step, not one per tick). */
	public static final int STRAIN_STEP_TICKS = 5;
	/** A borrowed One For All: a fixed 15% limit, Output never past 45% (even in Plus Ultra), and limb strain builds twice as fast. */
	public static final float BORROWED_LIMIT = 15.0f;
	public static final float BORROWED_OUTPUT_CAP = 45.0f;
	public static final float BORROWED_STRAIN_MULTIPLIER = 2.0f;
	/** The broken-limb drawbacks while it mends: a broken arm Weakness I (both arms: II), broken legs Slowness I. */
	public static final int BROKEN_ARM_WEAKNESS_AMPLIFIER = 0;
	public static final int BROKEN_LEGS_SLOWNESS_AMPLIFIER = 0;

	// ---------------------------------------------------------------- v0.16.6: Symbol of Peace (passive)
	/** Squadmates within this many blocks of him take {@link #SYMBOL_OF_PEACE_REDUCTION} less damage while he is in the Power Form. */
	public static final double SYMBOL_OF_PEACE_RADIUS = 16.0;
	public static final float SYMBOL_OF_PEACE_REDUCTION = 0.15f;

	// ---------------------------------------------------------------- v0.16.6: Passing On / Lending
	/** Sneak + right-click: the other player must be this close when the offer is made and accepted. */
	public static final double PASS_RANGE = 8.0;
	/** The chat offer's [Accept] / [Refuse] is open this long. */
	public static final int OFFER_TICKS = 30 * S;
	/** A loan lasts this long (time, death, logout or the lender's recall end it earlier). */
	public static final int LOAN_TICKS = 5 * 60 * S;
	/** After a loan ends the lender must wait this long before lending again. */
	public static final int LEND_COOLDOWN_TICKS = 60 * S;
	/** The lender's drawback for the whole loan: Weakness I + Slowness I (amplifier 0). */
	public static final int LENDER_WEAKNESS_AMPLIFIER = 0;
	public static final int LENDER_SLOWNESS_AMPLIFIER = 0;
	/** The gold glow that travels from the giver to the receiver, in ticks. */
	public static final int TRANSFER_GLOW_TICKS = 24;

	// ---------------------------------------------------------------- forms (v0.12.34: Base Form / Power Form)
	// Base Form is a plain Minecraft player: no bonuses, no passives, no abilities. Everything below is the Power Form (H).

	/** Power Form melee damage: a player's base 1 plus 12 = 13. */
	public static final double FULL_ATTACK_BONUS = 12.0;
	/** Power Form max health: 20 + 20 = 40. The current health keeps its percentage across the change. */
	public static final double FULL_HEALTH_BONUS = 20.0;
	/** Damage taken is multiplied by (1 - this) in the Power Form -- 50% less from everything. */
	public static final float FULL_DAMAGE_REDUCTION = 0.50f;
	/** Speed III (amplifier 2). */
	public static final int FULL_SPEED_AMPLIFIER = 2;
	/** v0.12.39: below Regeneration I -- a plain heal of {@link #FULL_REGEN_AMOUNT} HP every {@link #FULL_REGEN_INTERVAL_TICKS} ticks (1 HP every 4 s; Regeneration I is 1 HP every 2.5 s). */
	public static final int FULL_REGEN_INTERVAL_TICKS = 4 * S;
	public static final float FULL_REGEN_AMOUNT = 1.0f;
	/** How high a jump carries him, in blocks (a vanilla jump is about 1.25). */
	public static final double FULL_JUMP_BLOCKS = 3.0;
	/** The Power Form is 1.5x as tall: 1.8 -> 2.7 blocks. Added to Attributes.SCALE and eased in / out over {@link #GROWTH_TICKS}. */
	public static final double FULL_SCALE_BONUS = 0.5;
	public static final int GROWTH_TICKS = 20;

	/** H: the damage-proof, ability-locked transformation window (the one second he grows). */
	public static final int TRANSFORM_TICKS = 20;
	/** Changing back takes the same second (he shrinks). */
	public static final int DETRANSFORM_TICKS = 20;
	/** Minimum gap between two H presses. */
	public static final int FORM_TOGGLE_DEBOUNCE_TICKS = 8;

	// ---------------------------------------------------------------- Plus Ultra (C)
	/** v0.16.7: C pushes Output straight to 100% (past the limit) for a fixed 22 s; every Smash thrown in it strains its limb. */
	public static final int PLUS_ULTRA_DURATION_TICKS = 22 * S;
	/** Cooldown that starts when Plus Ultra ends (v0.16.6: 45 s, was 20 -- it is free now, no OFA to pay). */
	public static final int PLUS_ULTRA_COOLDOWN_TICKS = 45 * S;

	// ---------------------------------------------------------------- Smashes
	// v0.16.6: nothing costs anything -- the Output level scales every move and Strain is the limit. The *_COST constants are
	// kept at 0 for the HUD's old "can afford" check.
	// Detroit Smash (R): a devastating close-range punch
	public static final int DETROIT_COST = 0;
	public static final int DETROIT_COOLDOWN = 3 * S;
	public static final int DETROIT_WINDUP = 6;
	public static final float DETROIT_DAMAGE = 18.0f;
	public static final double DETROIT_RANGE = 10.0; // v0.12.44: air pressure, not reach
	public static final double DETROIT_WIDTH = 3.5;
	public static final double DETROIT_HEIGHT = 3.0;
	public static final double DETROIT_KNOCKBACK = 2.2;
	public static final double DETROIT_LIFT = 0.45;

	// Texas Smash (G): a wide travelling air blast
	public static final int TEXAS_COST = 0;
	public static final int TEXAS_COOLDOWN = 6 * S;
	public static final int TEXAS_WINDUP = 8;
	public static final float TEXAS_DAMAGE = 24.0f;
	public static final double TEXAS_RANGE = 18.0; // v0.12.44
	/** v0.12.44: the wave starts this wide and widens to {@link #TEXAS_END_WIDTH} at the far end (an expanding cone). */
	public static final double TEXAS_WIDTH = 4.0;
	public static final double TEXAS_END_WIDTH = 10.0;
	public static final double TEXAS_HEIGHT = 4.0;
	public static final double TEXAS_KNOCKBACK = 3.0;
	public static final double TEXAS_LIFT = 0.5;
	/** The wave advances this many blocks per tick (18 blocks take 9 ticks). */
	public static final double TEXAS_WAVE_SPEED = 2.0;

	// Carolina Smash (V): a high-speed offensive dash
	public static final int CAROLINA_COST = 0;
	public static final int CAROLINA_COOLDOWN = 5 * S;
	public static final int CAROLINA_WINDUP = 5;
	public static final float CAROLINA_DAMAGE = 20.0f;
	public static final double CAROLINA_DISTANCE = 30.0; // v0.12.44: the slide covers 15 blocks; v0.16.6 (user): launches him further -- 30
	public static final double CAROLINA_SPEED = 2.0; // v0.16.6: was 1.5 (the longer slide still takes 15 ticks, not 20)
	public static final double CAROLINA_KNOCKBACK = 2.4;

	// New Hampshire Smash (Shift+R): an aerial launch and a crashing landing
	public static final int NEW_HAMPSHIRE_COST = 0;
	public static final int NEW_HAMPSHIRE_COOLDOWN = 8 * S;
	public static final int NEW_HAMPSHIRE_WINDUP = 6;
	public static final float NEW_HAMPSHIRE_DAMAGE = 20.0f;
	/** Launch apex height above the take-off point (12-18 blocks). */
	public static final double NEW_HAMPSHIRE_HEIGHT = 15.0;
	public static final double NEW_HAMPSHIRE_FORWARD_SPEED = 0.9;
	public static final double NEW_HAMPSHIRE_LANDING_RADIUS = 6.0;
	/** v0.12.44: the launch fires a forward air-pressure blast this long / wide / tall (it reaches flyers and big bosses). */
	public static final double NEW_HAMPSHIRE_RANGE = 20.0;
	public static final double NEW_HAMPSHIRE_WIDTH = 6.0;
	public static final double NEW_HAMPSHIRE_BLAST_HEIGHT = 8.0;
	public static final double NEW_HAMPSHIRE_KNOCKBACK = 2.6;

	// United States of Smash (Z, hold 5 s): the ultimate
	public static final int UNITED_STATES_COST = 0; // v0.16.7: always fires at 100% Output -- past the limit it strains the right arm
	public static final int UNITED_STATES_COOLDOWN = 75 * S; // starts only once the punch has been cast
	/** Hold Z this long: the aura grows and the wind builds; the punch lands the moment the charge completes. */
	public static final int UNITED_STATES_CHARGE_TICKS = 5 * S;
	/** The punch pose starts this many ticks before the hit (the rest of the charge is aura and wind only). */
	public static final int UNITED_STATES_POSE_LEAD = 30;
	public static final float UNITED_STATES_DAMAGE = 75.0f;
	/** v0.12.38: the punch is an AoE -- a full-damage shockwave that expands to this radius all around him. */
	public static final double UNITED_STATES_RANGE = 35.0; // v0.12.44: the secondary shockwave reaches 35 blocks
	/** v0.12.44: the primary punch is a forward cone 25 blocks long, widening from {@link #UNITED_STATES_WIDTH} to {@link #UNITED_STATES_END_WIDTH}. */
	public static final double UNITED_STATES_PRIMARY_RANGE = 25.0;
	public static final double UNITED_STATES_END_WIDTH = 14.0;
	public static final double UNITED_STATES_WIDTH = 8.0;
	public static final double UNITED_STATES_HEIGHT = 8.0;
	public static final double UNITED_STATES_KNOCKBACK = 5.0;
	public static final double UNITED_STATES_LIFT = 0.9;
	/** Ticks the shockwave takes to expand from him to the full range. */
	public static final int UNITED_STATES_SECONDARY_EXPAND_TICKS = 10;

	// Leap (X) -- a utility ability: the mod has no reusable enhanced-leap for this kit, and the Smashes are all attacks
	public static final int LEAP_COST = 0;
	public static final int LEAP_COOLDOWN = 30; // 1.5 s
	public static final double LEAP_HEIGHT = 17.0;
	public static final double LEAP_FORWARD_SPEED = 0.7;
	/** v0.12.38: the leap launches along his look direction (with at least LEAP_MIN_LIFT of upward lift) at this speed. */
	public static final double LEAP_SPEED = 2.4;
	public static final double LEAP_MIN_LIFT = 0.45;
	/** v0.12.43: a regular armour piece torn off by the transformation loses this much durability. */
	public static final int ARMOUR_TEAR_DAMAGE = 50;
	/** v0.12.38: extra entity reach (blocks) in the Power Form -- 3 + 3 = a 6-block hit range. */
	public static final double FULL_REACH_BONUS = 3.0;

	// ---------------------------------------------------------------- v0.16.6: the Shift moves
	// Delaware Smash (Shift+G): a finger flick -- three compressed-air bullets
	public static final int DELAWARE_COOLDOWN = 7 * S;
	public static final int DELAWARE_WINDUP = 5;
	/** Ticks between the three flicks. */
	public static final int DELAWARE_FLICK_GAP = 4;
	public static final int DELAWARE_BULLETS = 3;
	public static final float DELAWARE_DAMAGE = 10.0f;
	public static final double DELAWARE_RANGE = 30.0;
	/** Blocks a bullet travels per tick (30 blocks in 5 ticks). */
	public static final double DELAWARE_SPEED = 6.0;
	/** How close to the bullet's path a body must be to be hit (scaled by Output). */
	public static final double DELAWARE_HIT_RADIUS = 0.7;
	public static final double DELAWARE_KNOCKBACK = 3.4;
	public static final double DELAWARE_LIFT = 0.45;

	// Manchester Smash (Shift+X): a flip in the air and an axe kick straight down
	public static final int MANCHESTER_COOLDOWN = 8 * S;
	public static final int MANCHESTER_WINDUP = 4;
	/** From the ground he first springs this high, flipping, before the kick. */
	public static final double MANCHESTER_HOP_HEIGHT = 5.0;
	/** Ticks of the flip before the kick drives him down. */
	public static final int MANCHESTER_FLIP_TICKS = 12;
	public static final double MANCHESTER_DIVE_SPEED = 2.6;
	public static final float MANCHESTER_DAMAGE = 22.0f;
	public static final double MANCHESTER_RADIUS = 6.0;
	public static final double MANCHESTER_KNOCKBACK = 2.0;
	public static final double MANCHESTER_LIFT = 0.8;
	public static final float MANCHESTER_BLOCK_HARDNESS = 2.0f;
	public static final double MANCHESTER_BLOCK_RADIUS = 3.0;
	public static final int MANCHESTER_BLOCK_MAX = 40;

	// Oklahoma Smash (Shift+Z): he spins in place -- a 360-degree tornado of air
	public static final int OKLAHOMA_COOLDOWN = 10 * S;
	public static final int OKLAHOMA_WINDUP = 4;
	public static final int OKLAHOMA_SPIN_TICKS = 14;
	public static final float OKLAHOMA_DAMAGE = 16.0f;
	public static final double OKLAHOMA_RADIUS = 7.0;
	public static final double OKLAHOMA_KNOCKBACK = 3.2;
	public static final double OKLAHOMA_LIFT = 0.9;

	// Missouri Smash (Shift+V): a dashing chop that cuts a line of air
	public static final int MISSOURI_COOLDOWN = 8 * S;
	public static final int MISSOURI_WINDUP = 6;
	public static final float MISSOURI_DAMAGE = 20.0f;
	public static final double MISSOURI_RANGE = 12.0;
	public static final double MISSOURI_WIDTH = 2.5;
	public static final double MISSOURI_HEIGHT = 3.0;
	public static final double MISSOURI_DASH_SPEED = 1.6;
	public static final double MISSOURI_KNOCKBACK = 2.0;
	/** A boss it cuts is staggered: Slowness VI + Weakness I for this long (bosses are never knocked back). */
	public static final int MISSOURI_STAGGER_TICKS = 2 * S;

	// St. Louis Smash (Shift+C): a spinning kick that throws a horizontal crescent of air
	public static final int ST_LOUIS_COOLDOWN = 9 * S;
	public static final int ST_LOUIS_WINDUP = 7;
	public static final float ST_LOUIS_DAMAGE = 18.0f;
	public static final double ST_LOUIS_RANGE = 20.0;
	public static final double ST_LOUIS_WIDTH = 4.0;
	public static final double ST_LOUIS_END_WIDTH = 10.0;
	public static final double ST_LOUIS_HEIGHT = 3.0;
	/** Blocks the crescent advances per tick (20 blocks in 8 ticks). */
	public static final double ST_LOUIS_SPEED = 2.5;
	public static final double ST_LOUIS_KNOCKBACK = 2.4;
	public static final double ST_LOUIS_LIFT = 0.4;

	// Every Smash is locked out for this long after another starts (except the ones that set their own, longer window)
	public static final int GLOBAL_LOCK_TICKS = 6;

	// ---------------------------------------------------------------- bosses
	/** A boss (max health >= this, or a Wither / Ender Dragon) loses at most this fraction of its max health per Smash hit and is not knocked back. */
	public static final float BOSS_HEALTH_THRESHOLD = 300.0f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- passive punches
	public static final double PUNCH_KNOCKBACK = 1.2;
	public static final double SPRINT_PUNCH_KNOCKBACK = 1.9;

	// ---------------------------------------------------------------- landing impacts (fall distance in blocks)
	public static final float LANDING_SMALL_MIN_FALL = 6.0f;
	public static final float LANDING_MEDIUM_MIN_FALL = 12.0f;
	public static final float LANDING_HEAVY_MIN_FALL = 20.0f;
	public static final float LANDING_SMALL_DAMAGE = 2.0f;
	public static final float LANDING_MEDIUM_DAMAGE = 8.0f;
	public static final float LANDING_HEAVY_DAMAGE = 20.0f;
	public static final double LANDING_SMALL_RADIUS = 2.0;
	public static final double LANDING_MEDIUM_RADIUS = 4.0;
	public static final double LANDING_HEAVY_RADIUS = 7.0;
	/** Fall damage is fully cancelled for this long after any of his own launches (Leap, New Hampshire, Carolina). */
	public static final int LAUNCH_NO_FALL_TICKS = 15 * S;

	// ---------------------------------------------------------------- environment (block destruction)
	/** Master switch. Also requires the server's normal ability-griefing rule ({@code AbilityHelpers.canGrief}). */
	public static final boolean BLOCK_DESTRUCTION = true;
	/** Blocks harder than this are never broken (stone 1.5, obsidian 50; bedrock and other unbreakables are -1 and always excluded). */
	public static final float DETROIT_BLOCK_HARDNESS = 2.0f;
	public static final double DETROIT_BLOCK_RADIUS = 1.6;
	public static final int DETROIT_BLOCK_MAX = 12;
	public static final float TEXAS_BLOCK_HARDNESS = 2.0f;
	public static final double TEXAS_BLOCK_RADIUS = 2.6;
	public static final int TEXAS_BLOCK_MAX = 30;
	public static final float CAROLINA_BLOCK_HARDNESS = 1.0f;
	public static final double CAROLINA_BLOCK_RADIUS = 1.2;
	public static final int CAROLINA_BLOCK_MAX = 4;
	public static final float NEW_HAMPSHIRE_BLOCK_HARDNESS = 2.0f;
	public static final double NEW_HAMPSHIRE_BLOCK_RADIUS = 3.2;
	public static final int NEW_HAMPSHIRE_BLOCK_MAX = 36;
	public static final float UNITED_STATES_BLOCK_HARDNESS = 5.0f;
	public static final double UNITED_STATES_BLOCK_RADIUS = 9.0;
	public static final int UNITED_STATES_BLOCK_MAX = 360;

	// ---------------------------------------------------------------- visuals
	/** Multiplies every particle count (1.0 = default; 0 turns the particles off). */
	public static final float PARTICLE_INTENSITY = 1.0f;
	/** Camera shake: radius in blocks within which players feel the big impacts. */
	public static final double SHAKE_RADIUS = 40.0;
	public static final boolean SCREEN_SHAKE = true;
}
