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
	/** Titan Energy (v0.12.32): 0..{@code TitanShifterConfig.energy().max}. Synced so the HUD can draw the bar. */
	public float energy;
	/** v0.12.39: game time the Emergency Titan is forced out; 0 when the shifter is not in (or making) an emergency form. */
	public long emergencyUntil;
	/** v0.12.39: set when an Emergency Titan ends -- Titan Energy refills 3x slower and there is no passive regen until the penalty clears. */
	public boolean emergencyPenalty;
	/** v0.12.43: N in the base form switches the passive (Regeneration II) healing off / on. Persisted in the {@code flags} codec field. */
	public boolean regenOff;
	/**
	 * v0.17.5 (user): Titan charges -- each shift spends one; they come back one at a time ({@code TitanCharges}). -1 = never
	 * set (a save from before charges): read as a full set of the selected Titan's charges.
	 */
	public int charges = -1;
	/** v0.17.5: ticks the next charge has regenerated so far (0..charges.regenTicks); a counter, not a game time, so it survives relogs and world switches. */
	public int chargeProgress;

	public TitanShifterState() {
		this(false, TitanPhase.HUMAN.name(), "generic_titan", 0L, 0L, 0L, 0L, 0L, 0f, 0f, 0, 0L, new HashMap<>(), 0f, 0L, 0);
	}

	public TitanShifterState(boolean unlocked, String phase, String typeId, long phaseStartedAt, long phaseUntil,
			long cooldownUntil, long regenUntil, long hardenUntil, float titanHealth, float titanMaxHealth,
			int lastAction, long lastActionTick, Map<String, Long> abilityReadyAt, float energy,
			long emergencyUntil, int flags) {
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
		this.energy = energy;
		this.emergencyUntil = emergencyUntil;
		this.emergencyPenalty = (flags & 1) != 0;
		this.regenOff = (flags & 2) != 0;
	}

	/** Bit 0 = emergency penalty, bit 1 = passive regeneration switched off (packed to stay within the codec's 16-field limit). */
	public int flags() {
		return (emergencyPenalty ? 1 : 0) | (regenOff ? 2 : 0);
	}

	public boolean inEmergency() {
		return emergencyUntil > 0L;
	}

	/** The emergency form (or its penalty bookkeeping) just ended: the form is over and the penalty starts. */
	public void endEmergency() {
		if (emergencyUntil > 0L) {
			emergencyUntil = 0L;
			emergencyPenalty = true;
		}
	}

	public TitanPhase phase() {
		return TitanPhase.byName(phase);
	}

	public TitanShifterState copy() {
		TitanShifterState c = new TitanShifterState(unlocked, phase, typeId, phaseStartedAt, phaseUntil, cooldownUntil, regenUntil,
				hardenUntil, titanHealth, titanMaxHealth, lastAction, lastActionTick, abilityReadyAt, energy, emergencyUntil, flags());
		c.charges = charges;
		c.chargeProgress = chargeProgress;
		return c;
	}

	/**
	 * v0.17.5: the codec already had RecordCodecBuilder's 16 fields, so the last two (emergency_until, flags) and the new charge
	 * fields share one inline map codec -- the keys stay flat in the NBT, so older saves read exactly as before.
	 */
	private record Extras(long emergencyUntil, int flags, int charges, int chargeProgress) {
		static final com.mojang.serialization.MapCodec<Extras> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.LONG.optionalFieldOf("emergency_until", 0L).forGetter(Extras::emergencyUntil),
				Codec.INT.optionalFieldOf("flags", 0).forGetter(Extras::flags),
				Codec.INT.optionalFieldOf("charges", -1).forGetter(Extras::charges),
				Codec.INT.optionalFieldOf("charge_progress", 0).forGetter(Extras::chargeProgress)
		).apply(i, Extras::new));
	}

	private static TitanShifterState fromCodec(boolean unlocked, String phase, String typeId, long phaseStartedAt, long phaseUntil,
			long cooldownUntil, long regenUntil, long hardenUntil, float titanHealth, float titanMaxHealth, int lastAction,
			long lastActionTick, Map<String, Long> abilityReadyAt, float energy, Extras x) {
		TitanShifterState s = new TitanShifterState(unlocked, phase, typeId, phaseStartedAt, phaseUntil, cooldownUntil, regenUntil,
				hardenUntil, titanHealth, titanMaxHealth, lastAction, lastActionTick, abilityReadyAt, energy, x.emergencyUntil(), x.flags());
		s.charges = x.charges();
		s.chargeProgress = Math.max(0, x.chargeProgress());
		return s;
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
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			// saves from v0.12.31 have no energy: an existing shifter simply starts with a full bar
			Codec.FLOAT.optionalFieldOf("energy", 100f).forGetter(s -> s.energy),
			Extras.MAP_CODEC.forGetter(s -> new Extras(s.emergencyUntil, s.flags(), s.charges, s.chargeProgress))
	).apply(instance, TitanShifterState::fromCodec));
}
