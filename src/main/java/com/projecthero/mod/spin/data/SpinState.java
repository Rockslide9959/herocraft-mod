package com.projecthero.mod.spin.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 the Spin: the whole Spin for one player in one attachment ({@code projecthero:spin_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the balls on the belt, the Spin Shield orbit, the hidden name tag, the HUD and
 * the Golden Rectangle read it). Only {@code Spin} changes it. The Spin bar ticks in place on the server and is re-sent every few
 * ticks; everything else is saved through a {@link #copy()} at once. All times are game times (0 = never / none).
 */
public final class SpinState {
	public boolean hasPower;
	/** 0..100, pays for the moves. */
	public float spin;
	/** Bit i set: ball i is thrown (a {@code SteelBallEntity} is out). */
	public int thrown;
	/** Ball 0 / 1 is busy (Scan on the ground, Spin Shield orbit, a Quake) until this time. */
	public long busy0;
	public long busy1;
	/** Shift+G / Shift+Z went down at this time (the Golden Rectangle; 0 = not charging). */
	public long chargeStart;
	/** Which hold is charging: {@link #CHARGE_GOLDEN} or {@link #CHARGE_BREAKER}. */
	public int chargeMove;
	/** Ticks until the rectangle closes for this charge (half on a horse), fixed when the charge began. */
	public int chargeTicks;
	public long shieldUntil;
	/** Face Spin: disguised until this time. */
	public long disguiseUntil;
	/** Spin Leap: no fall damage until he lands (or this time). */
	public long leapUntil;
	/** Valkyrie's entity UUID ("" = none yet). */
	public String valkyrie;
	/** Valkyrie died: no whistling her back until this time. */
	public long valkyrieDeadUntil;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt;

	public static final int CHARGE_NONE = 0;
	public static final int CHARGE_GOLDEN = 1;
	public static final int CHARGE_BREAKER = 2;

	public SpinState() {
		this(false, 0f, 0, 0L, 0L, 0L, 0, 0, 0L, 0L, 0L, "", 0L, new HashMap<>());
	}

	public SpinState(boolean hasPower, float spin, int thrown, long busy0, long busy1, long chargeStart, int chargeMove, int chargeTicks,
			long shieldUntil, long disguiseUntil, long leapUntil, String valkyrie, long valkyrieDeadUntil, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.spin = spin;
		this.thrown = thrown;
		this.busy0 = busy0;
		this.busy1 = busy1;
		this.chargeStart = chargeStart;
		this.chargeMove = chargeMove;
		this.chargeTicks = chargeTicks;
		this.shieldUntil = shieldUntil;
		this.disguiseUntil = disguiseUntil;
		this.leapUntil = leapUntil;
		this.valkyrie = valkyrie == null ? "" : valkyrie;
		this.valkyrieDeadUntil = valkyrieDeadUntil;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public SpinState copy() {
		return new SpinState(hasPower, spin, thrown, busy0, busy1, chargeStart, chargeMove, chargeTicks, shieldUntil, disguiseUntil, leapUntil,
				valkyrie, valkyrieDeadUntil, abilityReadyAt);
	}

	public long busy(int ball) {
		return ball == 0 ? busy0 : busy1;
	}

	public void setBusy(int ball, long until) {
		if (ball == 0) {
			busy0 = until;
		} else {
			busy1 = until;
		}
	}

	/** Ball {@code i} is in his hand (or on his belt): not thrown and not busy. */
	public boolean ballReady(int i, long now) {
		return (thrown & (1 << i)) == 0 && now >= busy(i);
	}

	/** How many balls are in hand right now. */
	public int ballsReady(long now) {
		int n = 0;
		for (int i = 0; i < 2; i++) {
			if (ballReady(i, now)) {
				n++;
			}
		}
		return n;
	}

	public static final Codec<SpinState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("spin", 0f).forGetter(s -> s.spin),
			Codec.INT.optionalFieldOf("thrown", 0).forGetter(s -> s.thrown),
			Codec.LONG.optionalFieldOf("busy0", 0L).forGetter(s -> s.busy0),
			Codec.LONG.optionalFieldOf("busy1", 0L).forGetter(s -> s.busy1),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.INT.optionalFieldOf("charge_move", 0).forGetter(s -> s.chargeMove),
			Codec.INT.optionalFieldOf("charge_ticks", 0).forGetter(s -> s.chargeTicks),
			Codec.LONG.optionalFieldOf("shield_until", 0L).forGetter(s -> s.shieldUntil),
			Codec.LONG.optionalFieldOf("disguise_until", 0L).forGetter(s -> s.disguiseUntil),
			Codec.LONG.optionalFieldOf("leap_until", 0L).forGetter(s -> s.leapUntil),
			Codec.STRING.optionalFieldOf("valkyrie", "").forGetter(s -> s.valkyrie),
			Codec.LONG.optionalFieldOf("valkyrie_dead_until", 0L).forGetter(s -> s.valkyrieDeadUntil),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, SpinState::new));
}
