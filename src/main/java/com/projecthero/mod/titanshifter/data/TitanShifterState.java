package com.projecthero.mod.titanshifter.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.projecthero.mod.titanshifter.TitanPhase;

/**
 * The whole Titan Shifter power for one player in one namespaced attachment
 * ({@code projecthero:titan_shifter_state}): persistent, {@code copyOnDeath} and synced to the owner (the
 * HUD reads it). Everything the client needs to <em>draw the Titan</em> lives on the form entity itself,
 * so other players never need this attachment. The server is authoritative; this is only ever mutated
 * through {@code TitanShifter}.
 */
public final class TitanShifterState {
	/** Titan Serum used -- the permanent unlock. */
	public boolean unlocked;
	public String phase;
	/** {@code TitanType#id} of the selected form. */
	public String typeId;
	public long phaseStartedAt;
	public long phaseUntil;
	/** Absolute game-time the next transformation is allowed (after reversion / defeat / forced end). */
	public long cooldownUntil;
	public long regenUntil;
	public long hardenUntil;
	/** Mirror of the live form's health for the HUD (throttled); 0 outside the form. */
	public float titanHealth;
	public float titanMaxHealth;
	public int lastAction;
	public long lastActionTick;
	/** {@code abilityId} -> absolute game-time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public TitanShifterState() {
		this(false, TitanPhase.HUMAN.name(), "generic_titan", 0L, 0L, 0L, 0L, 0L, 0f, 0f, 0, 0L, new HashMap<>());
	}

	public TitanShifterState(boolean unlocked, String phase, String typeId, long phaseStartedAt, long phaseUntil,
			long cooldownUntil, long regenUntil, long hardenUntil, float titanHealth, float titanMaxHealth,
			int lastAction, long lastActionTick, Map<String, Long> abilityReadyAt) {
		this.unlocked = unlocked;
		this.phase = phase;
		this.typeId = typeId;
		this.phaseStartedAt = phaseStartedAt;
		this.phaseUntil = phaseUntil;
		this.cooldownUntil = cooldownUntil;
		this.regenUntil = regenUntil;
		this.hardenUntil = hardenUntil;
		this.titanHealth = titanHealth;
		this.titanMaxHealth = titanMaxHealth;
		this.lastAction = lastAction;
		this.lastActionTick = lastActionTick;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public TitanPhase phase() {
		return TitanPhase.byName(phase);
	}

	public TitanShifterState copy() {
		return new TitanShifterState(unlocked, phase, typeId, phaseStartedAt, phaseUntil, cooldownUntil, regenUntil,
				hardenUntil, titanHealth, titanMaxHealth, lastAction, lastActionTick, abilityReadyAt);
	}

	public static final Codec<TitanShifterState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("unlocked", false).forGetter(s -> s.unlocked),
			Codec.STRING.optionalFieldOf("phase", "HUMAN").forGetter(s -> s.phase),
			Codec.STRING.optionalFieldOf("type", "generic_titan").forGetter(s -> s.typeId),
			Codec.LONG.optionalFieldOf("phase_started_at", 0L).forGetter(s -> s.phaseStartedAt),
			Codec.LONG.optionalFieldOf("phase_until", 0L).forGetter(s -> s.phaseUntil),
			Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(s -> s.cooldownUntil),
			Codec.LONG.optionalFieldOf("regen_until", 0L).forGetter(s -> s.regenUntil),
			Codec.LONG.optionalFieldOf("harden_until", 0L).forGetter(s -> s.hardenUntil),
			Codec.FLOAT.optionalFieldOf("titan_health", 0f).forGetter(s -> s.titanHealth),
			Codec.FLOAT.optionalFieldOf("titan_max_health", 0f).forGetter(s -> s.titanMaxHealth),
			Codec.INT.optionalFieldOf("last_action", 0).forGetter(s -> s.lastAction),
			Codec.LONG.optionalFieldOf("last_action_tick", 0L).forGetter(s -> s.lastActionTick),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(instance, TitanShifterState::new));
}
