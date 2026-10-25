package com.projecthero.mod.falcon.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1: the whole Falcon power for one player in one attachment ({@code projecthero:falcon_state}): persistent,
 * {@code copyOnDeath} and synced to every client (others see the wings spread, the jets burn and the poses; the owner's HUD
 * needs Thrust, the cooldowns, the goggles). Only {@code Falcon} and its move classes change it, always through a
 * {@link #copy()}.
 */
public final class FalconState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_GUNS = 1;
	public static final int ANIM_MISSILES = 2;
	public static final int ANIM_SLASH = 3;
	public static final int ANIM_BASH = 4;
	public static final int ANIM_AFTERBURNER = 5;
	public static final int ANIM_ROLL = 6;
	public static final int ANIM_REDWING = 7;
	public static final int ANIM_CARRY = 8;
	public static final int ANIM_DIVE = 9;
	public static final int ANIM_TALON = 10;
	public static final int ANIM_LAND = 11;
	public static final int ANIM_TAKEOFF = 12;

	/** Wearing the full set (and the power not given up for another one). */
	public boolean hasPower;
	/** The power was replaced while the full set was on: it stays off until a piece comes off and goes back on. */
	public boolean blocked;
	/** 0..{@code FalconConfig.THRUST_MAX}. */
	public float thrust;
	/** The wings are out: gliding (the vanilla glide, kept alive while this is set). */
	public boolean flying;
	/** The jets are burning (Sprint held while gliding). */
	public boolean boosting;
	/** Tactical Goggles on (V). */
	public boolean goggles;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** The Wing Shield is up until this game time. */
	public long shieldUntil;
	public float shieldHp;
	/** Barrel Roll invulnerability until this game time. */
	public long rollUntil;
	/** The creature held by Lift & Carry / Talon Drop (-1 = none). */
	public int carryId;
	/** Wrist Guns firing until this game time (0 = not firing). */
	public long firingUntil;
	/** The Redwing drone's entity id (-1 = none). */
	public int redwingId;

	public FalconState() {
		this(false, false, 0f, false, false, false, 0, 0L, new HashMap<>(), 0L, 0f, 0L, -1, 0L, -1);
	}

	public FalconState(boolean hasPower, boolean blocked, float thrust, boolean flying, boolean boosting, boolean goggles, int animId,
			long animStart, Map<String, Long> abilityReadyAt, long shieldUntil, float shieldHp, long rollUntil, int carryId, long firingUntil,
			int redwingId) {
		this.hasPower = hasPower;
		this.blocked = blocked;
		this.thrust = thrust;
		this.flying = flying;
		this.boosting = boosting;
		this.goggles = goggles;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.shieldUntil = shieldUntil;
		this.shieldHp = shieldHp;
		this.rollUntil = rollUntil;
		this.carryId = carryId;
		this.firingUntil = firingUntil;
		this.redwingId = redwingId;
	}

	public FalconState copy() {
		return new FalconState(hasPower, blocked, thrust, flying, boosting, goggles, animId, animStart, abilityReadyAt, shieldUntil, shieldHp,
				rollUntil, carryId, firingUntil, redwingId);
	}

	public static final Codec<FalconState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("blocked", false).forGetter(s -> s.blocked),
			Codec.FLOAT.optionalFieldOf("thrust", 0f).forGetter(s -> s.thrust),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("boosting", false).forGetter(s -> s.boosting),
			Codec.BOOL.optionalFieldOf("goggles", false).forGetter(s -> s.goggles),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("shield_until", 0L).forGetter(s -> s.shieldUntil),
			Codec.FLOAT.optionalFieldOf("shield_hp", 0f).forGetter(s -> s.shieldHp),
			Codec.LONG.optionalFieldOf("roll_until", 0L).forGetter(s -> s.rollUntil),
			Codec.INT.optionalFieldOf("carry_id", -1).forGetter(s -> s.carryId),
			Codec.LONG.optionalFieldOf("firing_until", 0L).forGetter(s -> s.firingUntil),
			Codec.INT.optionalFieldOf("redwing_id", -1).forGetter(s -> s.redwingId)
	).apply(i, FalconState::new));
}
