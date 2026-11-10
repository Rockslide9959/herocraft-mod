package com.projecthero.mod.saiyan.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.saiyan.SaiyanForm;

/**
 * v0.17.3: the whole Saiyan power for one player in one attachment ({@code projecthero:saiyan_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the hair and aura follow {@link #form}). Only {@code Saiyan} changes it.
 * The Ki bar and the mastery clocks tick in place on the server and are re-sent every few ticks; everything else is saved
 * through a {@link #copy()} at once.
 */
public final class SaiyanState {
	public boolean hasPower;
	/** {@link SaiyanForm} ordinal. */
	public int form;
	public float ki;
	/** Q is held. */
	public boolean charging;
	/** Shift+X flight is on. */
	public boolean flying;
	/** Game time the current form was entered (the transformation burst plays from here). */
	public long formSince;
	/** Game time of the last transformation (the cooldown). */
	public long lastTransformAt;
	/** Ki ran out: no transforming, and slowed, until this game time. */
	public long exhaustedUntil;
	/** Ticks spent in each form, by ordinal. */
	public List<Integer> mastery;
	/** Forms unlocked by mastery, a bit per ordinal (Base and Super Saiyan always are). */
	public int unlocked;
	/** The core's own cooldowns (the Ki Dash, the flight toggle). The moves keep theirs in {@code SaiyanMoves}. */
	public Map<String, Long> abilityReadyAt;
	/** Instinct's next possible dodge. */
	public long dodgeReadyAt;

	public SaiyanState() {
		this(false, 0, 0f, false, false, 0L, 0L, 0L, new ArrayList<>(), 0, new HashMap<>(), 0L);
	}

	public SaiyanState(boolean hasPower, int form, float ki, boolean charging, boolean flying, long formSince, long lastTransformAt,
			long exhaustedUntil, List<Integer> mastery, int unlocked, Map<String, Long> abilityReadyAt, long dodgeReadyAt) {
		this.hasPower = hasPower;
		this.form = form;
		this.ki = ki;
		this.charging = charging;
		this.flying = flying;
		this.formSince = formSince;
		this.lastTransformAt = lastTransformAt;
		this.exhaustedUntil = exhaustedUntil;
		this.mastery = new ArrayList<>(mastery);
		while (this.mastery.size() < SaiyanForm.values().length) {
			this.mastery.add(0);
		}
		this.unlocked = unlocked;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.dodgeReadyAt = dodgeReadyAt;
	}

	public SaiyanState copy() {
		return new SaiyanState(hasPower, form, ki, charging, flying, formSince, lastTransformAt, exhaustedUntil, mastery, unlocked,
				abilityReadyAt, dodgeReadyAt);
	}

	public SaiyanForm form() {
		return SaiyanForm.byOrdinal(form);
	}

	public int masteryOf(SaiyanForm f) {
		return f.ordinal() < mastery.size() ? mastery.get(f.ordinal()) : 0;
	}

	public static final Codec<SaiyanState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.INT.optionalFieldOf("form", 0).forGetter(s -> s.form),
			Codec.FLOAT.optionalFieldOf("ki", 0f).forGetter(s -> s.ki),
			Codec.BOOL.optionalFieldOf("charging", false).forGetter(s -> s.charging),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.LONG.optionalFieldOf("form_since", 0L).forGetter(s -> s.formSince),
			Codec.LONG.optionalFieldOf("last_transform_at", 0L).forGetter(s -> s.lastTransformAt),
			Codec.LONG.optionalFieldOf("exhausted_until", 0L).forGetter(s -> s.exhaustedUntil),
			Codec.INT.listOf().optionalFieldOf("mastery", List.of()).forGetter(s -> s.mastery),
			Codec.INT.optionalFieldOf("unlocked", 0).forGetter(s -> s.unlocked),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("dodge_ready_at", 0L).forGetter(s -> s.dodgeReadyAt)
	).apply(i, SaiyanState::new));
}
