package com.projecthero.mod.kryptonian.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole Kryptonian power for one player in one attachment ({@code projecthero:kryptonian_state}): persistent,
 * {@code copyOnDeath} and synced to every client (others see the flight pose and the heat-vision beams; the owner's
 * HUD needs the Solar Energy, the cooldowns and the timers). The server is authoritative; only {@code Kryptonian}
 * mutates it.
 *
 * <p>v0.14.16: X-Ray Vision is a toggle ({@link #xray}; the old {@code xray_until} timer is simply ignored on load),
 * Solar Energy remembers when it was last drained ({@link #lastDrain}; v0.14.17: informational only -- the bar refills all the time), and the held
 * Freeze Breath has its own flag ({@link #breathing}) so every viewer sees the pose for as long as it blows.
 */
public final class KryptonianState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_PUNCH = 1;
	public static final int ANIM_BREATH = 2;
	public static final int ANIM_CLAP = 3;
	public static final int ANIM_SLAM = 4;
	public static final int ANIM_FLARE = 5;
	public static final int ANIM_DASH = 6;
	public static final int ANIM_THROW = 7;
	/** v0.14.16: C -- the Super-Speed Barrage. */
	public static final int ANIM_BARRAGE = 8;
	/** v0.14.16: Shift+C -- Meteor Strike (the rise, then the dive). */
	public static final int ANIM_METEOR = 9;

	public boolean hasPower;
	/** 0..{@code KryptonianConfig.SOLAR_MAX}. */
	public float solar;
	/** Flying under his own power (double-tap jump). */
	public boolean flying;
	/** Near kryptonite: no passives, no flight, no moves, and it hurts. */
	public boolean weakened;
	/** After the Solar Flare: no flight, no moves, no passives until this game time. */
	public long depoweredUntil;
	/** v0.14.16: X-Ray Vision is switched on (a toggle; only the owner's own client draws the outlines). */
	public boolean xray;
	/** Heat Vision is firing right now. */
	public boolean heatVision;
	public int animId;
	public long animStart;
	/** Game time the Solar Flare started charging, 0 when not charging. */
	public long flareChargeStart;
	/** v0.14.11: Flight Boost (X while flying) -- faster sprint flight, the only time the sonic boom fires. */
	public boolean flightBoost;
	/** v0.14.16: game time Solar Energy was last drained by anything (moves, flight, regeneration, kryptonite). */
	public long lastDrain;
	/** v0.14.16: the held Freeze Breath is blowing right now. */
	public boolean breathing;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public KryptonianState() {
		this(false, 0f, false, false, 0L, false, false, 0, 0L, 0L, new HashMap<>(), false, 0L, false);
	}

	public KryptonianState(boolean hasPower, float solar, boolean flying, boolean weakened, long depoweredUntil, boolean xray,
			boolean heatVision, int animId, long animStart, long flareChargeStart, Map<String, Long> abilityReadyAt,
			boolean flightBoost, long lastDrain, boolean breathing) {
		this.hasPower = hasPower;
		this.solar = solar;
		this.flying = flying;
		this.weakened = weakened;
		this.depoweredUntil = depoweredUntil;
		this.xray = xray;
		this.heatVision = heatVision;
		this.animId = animId;
		this.animStart = animStart;
		this.flareChargeStart = flareChargeStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.flightBoost = flightBoost;
		this.lastDrain = lastDrain;
		this.breathing = breathing;
	}

	public KryptonianState copy() {
		return new KryptonianState(hasPower, solar, flying, weakened, depoweredUntil, xray, heatVision, animId, animStart,
				flareChargeStart, abilityReadyAt, flightBoost, lastDrain, breathing);
	}

	public static final Codec<KryptonianState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("solar", 0f).forGetter(s -> s.solar),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("weakened", false).forGetter(s -> s.weakened),
			Codec.LONG.optionalFieldOf("depowered_until", 0L).forGetter(s -> s.depoweredUntil),
			Codec.BOOL.optionalFieldOf("xray", false).forGetter(s -> s.xray),
			Codec.BOOL.optionalFieldOf("heat_vision", false).forGetter(s -> s.heatVision),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.LONG.optionalFieldOf("flare_charge_start", 0L).forGetter(s -> s.flareChargeStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.BOOL.optionalFieldOf("flight_boost", false).forGetter(s -> s.flightBoost),
			Codec.LONG.optionalFieldOf("last_drain", 0L).forGetter(s -> s.lastDrain),
			Codec.BOOL.optionalFieldOf("breathing", false).forGetter(s -> s.breathing)
	).apply(i, KryptonianState::new));
}
