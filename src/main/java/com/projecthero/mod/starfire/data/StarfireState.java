package com.projecthero.mod.starfire.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Starfire: the whole power for one player in one attachment ({@code projecthero:starfire_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the HUD gauge, the glow overlays, the eye beams, the flight and the Supernova flash
 * read it). Only {@code Starfire} changes it. The Starbolt bar and the mood tick in place on the server and are re-sent every few
 * ticks; everything else is saved through a {@link #copy()} at once. Times are game times (0 = never / none).
 */
public final class StarfireState {
	public boolean hasPower;
	/** 0..100: pays for every move and the flight. */
	public float starbolt;
	/** -100 (full Joy) .. +100 (full Fury): the gauge's one needle. */
	public float mood;
	public boolean flying;
	/** Shift+G held: the eye beams are burning. */
	public boolean beams;
	/** Shift+V: Glorious Radiance is on. */
	public boolean radiance;
	/** The hold that is charging ({@link #CHARGE_BURST} / {@link #CHARGE_NOVA}; 0 = none) and when it began. */
	public int chargeMove;
	public long chargeStart;
	/** When the last Supernova went off (the client's flash). */
	public long novaAt;
	/** Comet Dive running (green fire round her) until this time. */
	public long diveUntil;
	/** Star Dash: the streak and the i-frames until this time. */
	public long dashUntil;
	/** Language Kiss on a player: whose name tag she sees through walls ("" = nobody), until when. */
	public String bondWith;
	public long bondUntil;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt;
	/** Villagers she has kissed: villager UUID -> the day she last got a gift from them. */
	public Map<String, Long> villagers;

	public static final int CHARGE_NONE = 0;
	public static final int CHARGE_BURST = 1;
	public static final int CHARGE_NOVA = 2;

	public StarfireState() {
		this(false, 0f, 0f, false, false, false, 0, 0L, 0L, 0L, 0L, "", 0L, new HashMap<>(), new HashMap<>());
	}

	public StarfireState(boolean hasPower, float starbolt, float mood, boolean flying, boolean beams, boolean radiance, int chargeMove,
			long chargeStart, long novaAt, long diveUntil, long dashUntil, String bondWith, long bondUntil, Map<String, Long> abilityReadyAt,
			Map<String, Long> villagers) {
		this.hasPower = hasPower;
		this.starbolt = starbolt;
		this.mood = mood;
		this.flying = flying;
		this.beams = beams;
		this.radiance = radiance;
		this.chargeMove = chargeMove;
		this.chargeStart = chargeStart;
		this.novaAt = novaAt;
		this.diveUntil = diveUntil;
		this.dashUntil = dashUntil;
		this.bondWith = bondWith == null ? "" : bondWith;
		this.bondUntil = bondUntil;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.villagers = new HashMap<>(villagers);
	}

	public StarfireState copy() {
		return new StarfireState(hasPower, starbolt, mood, flying, beams, radiance, chargeMove, chargeStart, novaAt, diveUntil, dashUntil,
				bondWith, bondUntil, abilityReadyAt, villagers);
	}

	/** 0..1: how much Joy the needle shows. */
	public float joy() {
		return Math.max(0f, -mood) / 100f;
	}

	/** 0..1: how much Fury the needle shows. */
	public float fury() {
		return Math.max(0f, mood) / 100f;
	}

	public static final Codec<StarfireState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("starbolt", 0f).forGetter(s -> s.starbolt),
			Codec.FLOAT.optionalFieldOf("mood", 0f).forGetter(s -> s.mood),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("beams", false).forGetter(s -> s.beams),
			Codec.BOOL.optionalFieldOf("radiance", false).forGetter(s -> s.radiance),
			Codec.INT.optionalFieldOf("charge_move", 0).forGetter(s -> s.chargeMove),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.LONG.optionalFieldOf("nova_at", 0L).forGetter(s -> s.novaAt),
			Codec.LONG.optionalFieldOf("dive_until", 0L).forGetter(s -> s.diveUntil),
			Codec.LONG.optionalFieldOf("dash_until", 0L).forGetter(s -> s.dashUntil),
			Codec.STRING.optionalFieldOf("bond_with", "").forGetter(s -> s.bondWith),
			Codec.LONG.optionalFieldOf("bond_until", 0L).forGetter(s -> s.bondUntil),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("villagers", Map.of())
					.forGetter(s -> new HashMap<>(s.villagers))
	).apply(i, StarfireState::new));
}
