package com.projecthero.mod.vision.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Vision: the whole power for one player in one attachment ({@code projecthero:vision_state}): persistent, {@code copyOnDeath}
 * and synced to every client (the HUD, the suit / hologram / phase looks, the beams, the flight read it). Only {@code Vision} changes
 * it. The energy bar ticks in place on the server and is re-sent every few ticks; everything else is saved through a {@link #copy()}
 * at once. Times are game times (0 = never / none).
 */
public final class VisionState {
	public boolean hasPower;
	/** 0..100 Mind Stone energy. */
	public float energy;
	public boolean flying;
	/** R held: the Mind Stone Beam is burning. */
	public boolean beam;
	/** Shift+Z: Mind Stone Unleashed is channelling (since {@link #unleashStart}). */
	public boolean unleash;
	public long unleashStart;
	/** Shift+R: the Solar Lance charging since this time (0 = not charging). */
	public long lanceStart;
	/** V: the Human Hologram is up; when it last changed (the shimmer). */
	public boolean hologram;
	public long holoChangeAt;
	/** Shift+V: Intangibility. */
	public boolean intangible;
	/** Shift+C: Diamond Density. */
	public boolean density;
	/** Phase Dash: intangible until this time. */
	public long dashUntil;
	/** Density Dive running (the gold streak) until this time. */
	public long diveUntil;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt;

	public VisionState() {
		this(false, 0f, false, false, false, 0L, 0L, false, 0L, false, false, 0L, 0L, new HashMap<>());
	}

	public VisionState(boolean hasPower, float energy, boolean flying, boolean beam, boolean unleash, long unleashStart, long lanceStart,
			boolean hologram, long holoChangeAt, boolean intangible, boolean density, long dashUntil, long diveUntil,
			Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.energy = energy;
		this.flying = flying;
		this.beam = beam;
		this.unleash = unleash;
		this.unleashStart = unleashStart;
		this.lanceStart = lanceStart;
		this.hologram = hologram;
		this.holoChangeAt = holoChangeAt;
		this.intangible = intangible;
		this.density = density;
		this.dashUntil = dashUntil;
		this.diveUntil = diveUntil;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public VisionState copy() {
		return new VisionState(hasPower, energy, flying, beam, unleash, unleashStart, lanceStart, hologram, holoChangeAt, intangible, density,
				dashUntil, diveUntil, abilityReadyAt);
	}

	public static final Codec<VisionState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(s -> s.energy),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("beam", false).forGetter(s -> s.beam),
			Codec.BOOL.optionalFieldOf("unleash", false).forGetter(s -> s.unleash),
			Codec.LONG.optionalFieldOf("unleash_start", 0L).forGetter(s -> s.unleashStart),
			Codec.LONG.optionalFieldOf("lance_start", 0L).forGetter(s -> s.lanceStart),
			Codec.BOOL.optionalFieldOf("hologram", false).forGetter(s -> s.hologram),
			Codec.LONG.optionalFieldOf("holo_change_at", 0L).forGetter(s -> s.holoChangeAt),
			Codec.BOOL.optionalFieldOf("intangible", false).forGetter(s -> s.intangible),
			Codec.BOOL.optionalFieldOf("density", false).forGetter(s -> s.density),
			Codec.LONG.optionalFieldOf("dash_until", 0L).forGetter(s -> s.dashUntil),
			Codec.LONG.optionalFieldOf("dive_until", 0L).forGetter(s -> s.diveUntil),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, VisionState::new));
}
