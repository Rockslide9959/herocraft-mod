package com.projecthero.mod.hulk.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole Hulk power for one player in one namespaced attachment ({@code projecthero:hulk_state}),
 * persistent, {@code copyOnDeath} and synced to every client (other players need {@link #hulk} and the
 * animation clock to draw him, the owner's HUD needs {@link #rage}, the cooldowns and the leap charge). The
 * server stays authoritative.
 *
 * <p>{@link #hasPower} is the spec's "Gamma power": only a player holding it builds rage or transforms.
 */
public final class HulkState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_CLAP = 1;
	public static final int ANIM_SMASH = 2;
	public static final int ANIM_LEAP = 3;
	public static final int ANIM_LEAP_CHARGE = 4;
	public static final int ANIM_PUNCH = 5;
	public static final int ANIM_HULK_SMASH = 6;
	public static final int ANIM_THROW = 7;
	public static final int ANIM_CRUSH = 8;
	public static final int ANIM_PICKUP = 9;
	// v0.16.4 Hulk revamp (docs/HULK_V0164_REVAMP_CONTRACT.md) -- one-shot / held move animations, 60..79 (Gladiator uses 40..53)
	public static final int ANIM_SUPER_PUNCH = 60;
	public static final int ANIM_GROUND_SMASH = 61;
	public static final int ANIM_THUNDERCLAP = 62;
	public static final int ANIM_FAULT_STOMP = 63;
	public static final int ANIM_BULLDOZE = 64;
	public static final int ANIM_UPPERCUT = 65;
	public static final int ANIM_GAMMA_BURST = 66;
	public static final int ANIM_EARTH_WALL = 67;
	public static final int ANIM_GRAB = 68;
	public static final int ANIM_THROW_FAR = 69;
	public static final int ANIM_DEBRIS_RIP = 70;
	public static final int ANIM_DEBRIS_THROW = 71;
	public static final int ANIM_LOKI_SLAM = 72;
	public static final int ANIM_ROAR = 73;
	public static final int ANIM_LEAP_LAUNCH = 74;
	public static final int ANIM_LEAP_LAND = 75;
	public static final int ANIM_DEATH_FALL = 76;
	public static final int ANIM_RAMPAGE = 77;
	/** v0.16.4: Combat.transformStyle values -- which of the six changes plays (see the contract doc). */
	public static final int TRANSFORM_QUICK = 0;
	public static final int TRANSFORM_FORCED = 1;
	public static final int TRANSFORM_RIP = 2;
	public static final int TRANSFORM_POUND = 3;
	public static final int TRANSFORM_ARCH = 4;
	public static final int TRANSFORM_STAGGER = 5;

	/** Has the Gamma power. */
	public boolean hasPower;
	/** Is the Hulk right now. */
	public boolean hulk;
	/** 0..{@code HulkConfig.RAGE_MAX}. */
	public float rage;
	/** Game time of the last hit taken or dealt (the calm-down delay counts from here). */
	public long lastCombatAt;
	/** Game time the post-Hulk exhaustion ends (no rage builds, no change, until then). */
	public long exhaustedUntil;
	/** Game time of the last change either way (drives the growth easing, the transform animation and the H debounce). */
	public long formChangedAt;
	/** v0.13.12: ability id -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** v0.13.12: the one-shot animation playing ({@link #ANIM_CLAP} ...) and when it started, for every client. */
	public int animId;
	public long animStart;
	/** v0.13.12: game time Super Leap started charging (0 = not charging). */
	public long leapChargeStart;
	/** v0.13.12: in the air after a Super Leap -- the landing makes a shockwave. */
	public boolean leaping;
	/** v0.13.12: the player's own Sprint Smash switch (C). */
	public boolean sprintSmash;
	/** v0.13.14 (the "combat" group, one nested codec: the record codec stops at 16 fields). */
	public Combat combat = new Combat();
	/** v0.16.4 (Hulk revamp, RULES): the change / rampage / death rules ({@link Rules}), a nested group of its own. */
	public Rules rules = new Rules();

	/**
	 * v0.16.4 (user, docs/HULK_V0164_REVAMP_CONTRACT.md): the new Hulk rules, synced to every client -- the minute-by-minute
	 * rampage clock and its arrow-key warning, the regain-control ring streak, the hold-H cooldown after a willing change back
	 * and the Hulk-form death sequence. Mutable; {@link HulkState#copy} deep-copies it.
	 */
	public static final class Rules {
		/** Game time the next rampage warning appears (0 = not set; only meaningful as the Hulk). */
		public long nextRampageAt;
		/** The warning's arrow string, 2 bits per arrow (0 up, 1 left, 2 down, 3 right), first arrow lowest. */
		public int warnSeq;
		/** How many arrows the warning has (0 = no warning showing). */
		public int warnLen;
		/** How many of them the player has matched so far. */
		public int warnPos;
		/** Game time the warning runs out (then he rampages). */
		public long warnUntil;
		/** Space presses in a row that caught the ring on the circle (3 ends the rampage). */
		public int ringStreak;
		/** Game time hold-H works again after a WILLING Hulk changed back (dying still forces the change). */
		public long holdCooldownUntil;
		/** Game time the Hulk took his killing blow (0 = not dying): flat on his back, the slow change back, then death. */
		public long deathFallAt;

		public Rules copy() {
			Rules r = new Rules();
			r.nextRampageAt = nextRampageAt;
			r.warnSeq = warnSeq;
			r.warnLen = warnLen;
			r.warnPos = warnPos;
			r.warnUntil = warnUntil;
			r.ringStreak = ringStreak;
			r.holdCooldownUntil = holdCooldownUntil;
			r.deathFallAt = deathFallAt;
			return r;
		}

		public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("next_rampage_at", 0L).forGetter(r -> r.nextRampageAt),
				Codec.INT.optionalFieldOf("warn_seq", 0).forGetter(r -> r.warnSeq),
				Codec.INT.optionalFieldOf("warn_len", 0).forGetter(r -> r.warnLen),
				Codec.INT.optionalFieldOf("warn_pos", 0).forGetter(r -> r.warnPos),
				Codec.LONG.optionalFieldOf("warn_until", 0L).forGetter(r -> r.warnUntil),
				Codec.INT.optionalFieldOf("ring_streak", 0).forGetter(r -> r.ringStreak),
				Codec.LONG.optionalFieldOf("hold_cooldown_until", 0L).forGetter(r -> r.holdCooldownUntil),
				Codec.LONG.optionalFieldOf("death_fall_at", 0L).forGetter(r -> r.deathFallAt)
		).apply(i, (next, seq, len, pos, until, streak, cooldown, death) -> {
			Rules r = new Rules();
			r.nextRampageAt = next;
			r.warnSeq = seq;
			r.warnLen = len;
			r.warnPos = pos;
			r.warnUntil = until;
			r.ringStreak = streak;
			r.holdCooldownUntil = cooldown;
			r.deathFallAt = death;
			return r;
		}));
	}

	/**
	 * v0.13.14: everything the new kit, the death save, the calm minigame and the control / rampage system need on
	 * every client. Mutable; {@link HulkState#copy} deep-copies it.
	 */
	public static final class Combat {
		/** Game time C's Charge run ends (0 = not charging). */
		public long chargeUntil;
		/** Game time Shift+Z started charging HULK SMASH (0 = not charging). */
		public long smashChargeStart;
		/** V: holding a mob or a chunk of earth overhead. */
		public boolean holding;
		/** 0..100: how much of the Hulk the player still controls. */
		public float control = 100.0f;
		/** Game time the Hulk last dealt damage (control only drains once this is old). */
		public long lastDealtAt;
		/** Game time the current rampage ends (0 = in control). */
		public long rampageUntil;
		/** The key the player must press to keep control: 0 none, 1 forward, 2 left, 3 back, 4 right. */
		public int promptKey;
		/** Game time the current prompt runs out. */
		public long promptUntil;
		/** In the calm-down minigame. v0.16.4: calming is gone -- kept only so old saves load (always false now). */
		public boolean calming;
		/** Game time the "the Hulk refuses to die" save was ready again (v0.13.17: unused -- the save has no cooldown; kept so old saves load). */
		public long deathSaveReadyAt;
		/**
		 * v0.13.15: this Hulk came out on his own (rage hit the top, or the death save) rather than by the player's H. Only
		 * an unwilling change drops Banner to his knees for the slow change, and only an unwilling Hulk fights the player
		 * for control.
		 */
		public boolean unwilling;
		/** v0.13.17: game time he last TOOK damage -- Banner's rage only starts bleeding off 5 s after that. */
		public long lastHurtAt;
		/**
		 * v0.15.18: game time Banner started holding H below the manual-change rage to strain the Hulk out (0 = not
		 * straining). The server times it; clients draw the progress from it.
		 */
		public long strainStart;
		/** v0.16.4: which transformation animation the current change plays ({@code TRANSFORM_*}). */
		public int transformStyle;

		public Combat copy() {
			Combat c = new Combat();
			c.chargeUntil = chargeUntil;
			c.smashChargeStart = smashChargeStart;
			c.holding = holding;
			c.control = control;
			c.lastDealtAt = lastDealtAt;
			c.rampageUntil = rampageUntil;
			c.promptKey = promptKey;
			c.promptUntil = promptUntil;
			c.calming = calming;
			c.deathSaveReadyAt = deathSaveReadyAt;
			c.unwilling = unwilling;
			c.lastHurtAt = lastHurtAt;
			c.strainStart = strainStart;
			c.transformStyle = transformStyle;
			return c;
		}

		public static final Codec<Combat> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("charge_until", 0L).forGetter(c -> c.chargeUntil),
				Codec.LONG.optionalFieldOf("smash_charge_start", 0L).forGetter(c -> c.smashChargeStart),
				Codec.BOOL.optionalFieldOf("holding", false).forGetter(c -> c.holding),
				Codec.FLOAT.optionalFieldOf("control", 100.0f).forGetter(c -> c.control),
				Codec.LONG.optionalFieldOf("last_dealt_at", 0L).forGetter(c -> c.lastDealtAt),
				Codec.LONG.optionalFieldOf("rampage_until", 0L).forGetter(c -> c.rampageUntil),
				Codec.INT.optionalFieldOf("prompt_key", 0).forGetter(c -> c.promptKey),
				Codec.LONG.optionalFieldOf("prompt_until", 0L).forGetter(c -> c.promptUntil),
				Codec.BOOL.optionalFieldOf("calming", false).forGetter(c -> c.calming),
				Codec.LONG.optionalFieldOf("death_save_ready_at", 0L).forGetter(c -> c.deathSaveReadyAt),
				Codec.BOOL.optionalFieldOf("unwilling", false).forGetter(c -> c.unwilling),
				Codec.LONG.optionalFieldOf("last_hurt_at", 0L).forGetter(c -> c.lastHurtAt),
				Codec.LONG.optionalFieldOf("strain_start", 0L).forGetter(c -> c.strainStart),
				Codec.INT.optionalFieldOf("transform_style", 0).forGetter(c -> c.transformStyle)
		).apply(i, (chargeUntil, smashChargeStart, holding, control, lastDealtAt, rampageUntil, promptKey, promptUntil, calming,
				deathSaveReadyAt, unwilling, lastHurtAt, strainStart, transformStyle) -> {
			Combat c = new Combat();
			c.chargeUntil = chargeUntil;
			c.smashChargeStart = smashChargeStart;
			c.holding = holding;
			c.control = control;
			c.lastDealtAt = lastDealtAt;
			c.rampageUntil = rampageUntil;
			c.promptKey = promptKey;
			c.promptUntil = promptUntil;
			c.calming = calming;
			c.deathSaveReadyAt = deathSaveReadyAt;
			c.unwilling = unwilling;
			c.lastHurtAt = lastHurtAt;
			c.strainStart = strainStart;
			c.transformStyle = transformStyle;
			return c;
		}));
	}

	public HulkState() {
		this(false, false, 0.0f, 0L, 0L, 0L, new HashMap<>(), ANIM_NONE, 0L, 0L, false, true);
	}

	public HulkState(boolean hasPower, boolean hulk, float rage, long lastCombatAt, long exhaustedUntil, long formChangedAt) {
		this(hasPower, hulk, rage, lastCombatAt, exhaustedUntil, formChangedAt, new HashMap<>(), ANIM_NONE, 0L, 0L, false, true);
	}

	public HulkState(boolean hasPower, boolean hulk, float rage, long lastCombatAt, long exhaustedUntil, long formChangedAt,
			Map<String, Long> abilityReadyAt, int animId, long animStart, long leapChargeStart, boolean leaping, boolean sprintSmash) {
		this.hasPower = hasPower;
		this.hulk = hulk;
		this.rage = rage;
		this.lastCombatAt = lastCombatAt;
		this.exhaustedUntil = exhaustedUntil;
		this.formChangedAt = formChangedAt;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.animId = animId;
		this.animStart = animStart;
		this.leapChargeStart = leapChargeStart;
		this.leaping = leaping;
		this.sprintSmash = sprintSmash;
	}

	public HulkState copy() {
		HulkState n = new HulkState(hasPower, hulk, rage, lastCombatAt, exhaustedUntil, formChangedAt, abilityReadyAt, animId, animStart,
				leapChargeStart, leaping, sprintSmash);
		n.combat = combat.copy();
		n.rules = rules.copy();
		return n;
	}

	/** v0.16.4: the Hulk-form death sequence is under way (client-safe). */
	public boolean dying() {
		return rules.deathFallAt != 0L;
	}

	/** True while the Hulk is rampaging on his own (client-safe with the synced game time). */
	public boolean rampaging(long now) {
		return hulk && combat.rampageUntil > now;
	}

	public static final Codec<HulkState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("hulk", false).forGetter(s -> s.hulk),
			Codec.FLOAT.optionalFieldOf("rage", 0.0f).forGetter(s -> s.rage),
			Codec.LONG.optionalFieldOf("last_combat_at", 0L).forGetter(s -> s.lastCombatAt),
			Codec.LONG.optionalFieldOf("exhausted_until", 0L).forGetter(s -> s.exhaustedUntil),
			Codec.LONG.optionalFieldOf("form_changed_at", 0L).forGetter(s -> s.formChangedAt),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.INT.optionalFieldOf("anim_id", ANIM_NONE).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.LONG.optionalFieldOf("leap_charge_start", 0L).forGetter(s -> s.leapChargeStart),
			Codec.BOOL.optionalFieldOf("leaping", false).forGetter(s -> s.leaping),
			Codec.BOOL.optionalFieldOf("sprint_smash", true).forGetter(s -> s.sprintSmash),
			Combat.CODEC.optionalFieldOf("combat").forGetter(s -> java.util.Optional.of(s.combat)),
			Rules.CODEC.optionalFieldOf("rules").forGetter(s -> java.util.Optional.of(s.rules))
	).apply(i, (hasPower, hulk, rage, lastCombatAt, exhaustedUntil, formChangedAt, ready, animId, animStart, leapCharge, leaping,
			sprintSmash, combat, rules) -> {
		HulkState s = new HulkState(hasPower, hulk, rage, lastCombatAt, exhaustedUntil, formChangedAt, ready, animId, animStart,
				leapCharge, leaping, sprintSmash);
		s.combat = combat.orElseGet(Combat::new);
		s.rules = rules.orElseGet(Rules::new);
		return s;
	}));
}
