package com.projecthero.mod.saiyan.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Saiyan revamp: the whole Saiyan core for one player in one attachment ({@code projecthero:saiyan_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the hair, the aura and the HUD read it). Only {@code Saiyan} changes it. The
 * Ki and Rage bars tick in place on the server and are re-sent every few ticks; everything else is saved through a
 * {@link #copy()} at once. All times are game times (0 = never / none).
 *
 * <p>Old saves (the first Saiyan build: form, mastery, ...) load fine: the codec ignores the fields it no longer knows, and the
 * player starts the new core in Base.
 */
public final class SaiyanState {
	public boolean hasPower;
	public float ki;
	/** 0..100; at 100 H transforms into Super Saiyan. */
	public float rage;
	/** V: charging Ki. */
	public boolean charging;
	/** Double-tap jump flight is on. */
	public boolean flying;
	/** Super Saiyan is on until {@link #ssjUntil}. */
	public boolean ssj;
	/** When the current form began (the transformation burst plays from here). */
	public long formSince;
	public long ssjUntil;
	/** After Super Saiyan ends: no transforming until this time. */
	public long ssjLockUntil;
	/** Kaioken is on since this time (0 = off). */
	public long kaiokenSince;
	public long kaiokenReadyAt;
	/** Shift+V went down at this time (the 3 s Kaioken wind-up; 0 = not held). */
	public long kaiokenHoldAt;
	/** Zenkai Boost: +50% damage until this time; the next one not before {@link #zenkaiReadyAt}. */
	public long zenkaiUntil;
	public long zenkaiReadyAt;
	/** Fighting a significantly stronger enemy: the bonus lasts until this time. */
	public long strongFoeUntil;
	/** The core's own cooldowns (the flight toggle). The moves keep theirs in {@code SaiyanKit}. */
	public Map<String, Long> abilityReadyAt;

	public SaiyanState() {
		this(false, 0f, 0f, false, false, false, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, new HashMap<>());
	}

	public SaiyanState(boolean hasPower, float ki, float rage, boolean charging, boolean flying, boolean ssj, long formSince, long ssjUntil,
			long ssjLockUntil, long kaiokenSince, long kaiokenReadyAt, long kaiokenHoldAt, long zenkaiUntil, long zenkaiReadyAt,
			long strongFoeUntil, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.ki = ki;
		this.rage = rage;
		this.charging = charging;
		this.flying = flying;
		this.ssj = ssj;
		this.formSince = formSince;
		this.ssjUntil = ssjUntil;
		this.ssjLockUntil = ssjLockUntil;
		this.kaiokenSince = kaiokenSince;
		this.kaiokenReadyAt = kaiokenReadyAt;
		this.kaiokenHoldAt = kaiokenHoldAt;
		this.zenkaiUntil = zenkaiUntil;
		this.zenkaiReadyAt = zenkaiReadyAt;
		this.strongFoeUntil = strongFoeUntil;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public SaiyanState copy() {
		return new SaiyanState(hasPower, ki, rage, charging, flying, ssj, formSince, ssjUntil, ssjLockUntil, kaiokenSince, kaiokenReadyAt,
				kaiokenHoldAt, zenkaiUntil, zenkaiReadyAt, strongFoeUntil, abilityReadyAt);
	}

	public boolean kaioken() {
		return kaiokenSince > 0L;
	}

	public static final Codec<SaiyanState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("ki", 0f).forGetter(s -> s.ki),
			Codec.FLOAT.optionalFieldOf("rage", 0f).forGetter(s -> s.rage),
			Codec.BOOL.optionalFieldOf("charging", false).forGetter(s -> s.charging),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.BOOL.optionalFieldOf("ssj", false).forGetter(s -> s.ssj),
			Codec.LONG.optionalFieldOf("form_since", 0L).forGetter(s -> s.formSince),
			Codec.LONG.optionalFieldOf("ssj_until", 0L).forGetter(s -> s.ssjUntil),
			Codec.LONG.optionalFieldOf("ssj_lock_until", 0L).forGetter(s -> s.ssjLockUntil),
			Codec.LONG.optionalFieldOf("kaioken_since", 0L).forGetter(s -> s.kaiokenSince),
			Codec.LONG.optionalFieldOf("kaioken_ready_at", 0L).forGetter(s -> s.kaiokenReadyAt),
			Codec.LONG.optionalFieldOf("kaioken_hold_at", 0L).forGetter(s -> s.kaiokenHoldAt),
			Codec.LONG.optionalFieldOf("zenkai_until", 0L).forGetter(s -> s.zenkaiUntil),
			Codec.LONG.optionalFieldOf("zenkai_ready_at", 0L).forGetter(s -> s.zenkaiReadyAt),
			Codec.LONG.optionalFieldOf("strong_foe_until", 0L).forGetter(s -> s.strongFoeUntil),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, SaiyanState::new));
}
