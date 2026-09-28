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
		return new HulkState(hasPower, hulk, rage, lastCombatAt, exhaustedUntil, formChangedAt, abilityReadyAt, animId, animStart,
				leapChargeStart, leaping, sprintSmash);
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
			Codec.BOOL.optionalFieldOf("sprint_smash", true).forGetter(s -> s.sprintSmash)
	).apply(i, HulkState::new));
}
