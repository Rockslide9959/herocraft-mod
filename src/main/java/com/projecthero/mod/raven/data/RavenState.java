package com.projecthero.mod.raven.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Raven: the whole power for one player in one attachment ({@code projecthero:raven_state}): persistent, {@code copyOnDeath}
 * and synced to every client (the HUD, the aura overlay, the cloak colour, the shield dome, the meditation pose read it). Only
 * {@code Raven} changes it. The two bars tick in place on the server and are re-sent every few ticks; everything else is saved
 * through a {@link #copy()} at once. Times are game times (0 = never).
 */
public final class RavenState {
	public boolean hasPower;
	/** Dark Energy, 0..100: pays for the moves. */
	public float energy;
	/** Composure, 0..100: 100 is calm, under 30 her emotions leak, under 10 is Rage. */
	public float composure;
	/** Double-tap Space levitation. */
	public boolean flying;
	/** Shift+V held: floating cross-legged. */
	public boolean meditating;
	/** Z held: the dome is up. */
	public boolean shield;
	/** What the dome can still absorb this time. */
	public float shieldHp;
	/** Trigon's Wrath ends at this time. */
	public long wrathUntil;
	/** White glowing eyes until this time (she just cast something). */
	public long castUntil;
	/** Shadow Dash / Shadow Step: a streak of shadow (no damage taken) until this time. */
	public long shadowUntil;
	/** The entity held by Telekinesis (-1 = none). */
	public int tkTarget;
	/** Debris Storm: blocks still orbiting her. */
	public int debris;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt;

	public RavenState() {
		this(false, 0f, 100f, false, false, false, 0f, 0L, 0L, 0L, -1, 0, new HashMap<>());
	}

	public RavenState(boolean hasPower, float energy, float composure, boolean flying, boolean meditating, boolean shield, float shieldHp,
			long wrathUntil, long castUntil, long shadowUntil, int tkTarget, int debris, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.energy = energy;
		this.composure = composure;
		this.flying = flying;
		this.meditating = meditating;
		this.shield = shield;
		this.shieldHp = shieldHp;
		this.wrathUntil = wrathUntil;
		this.castUntil = castUntil;
		this.shadowUntil = shadowUntil;
		this.tkTarget = tkTarget;
		this.debris = debris;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public RavenState copy() {
		return new RavenState(hasPower, energy, composure, flying, meditating, shield, shieldHp, wrathUntil, castUntil, shadowUntil, tkTarget,
				debris, abilityReadyAt);
	}

	public static final Codec<RavenState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(s -> s.energy),
			Codec.FLOAT.optionalFieldOf("composure", 100f).forGetter(s -> s.composure),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("meditating", false).forGetter(s -> s.meditating),
			Codec.BOOL.optionalFieldOf("shield", false).forGetter(s -> s.shield),
			Codec.FLOAT.optionalFieldOf("shield_hp", 0f).forGetter(s -> s.shieldHp),
			Codec.LONG.optionalFieldOf("wrath_until", 0L).forGetter(s -> s.wrathUntil),
			Codec.LONG.optionalFieldOf("cast_until", 0L).forGetter(s -> s.castUntil),
			Codec.LONG.optionalFieldOf("shadow_until", 0L).forGetter(s -> s.shadowUntil),
			Codec.INT.optionalFieldOf("tk_target", -1).forGetter(s -> s.tkTarget),
			Codec.INT.optionalFieldOf("debris", 0).forGetter(s -> s.debris),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, RavenState::new));
}
