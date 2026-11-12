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
	/**
	 * The Steel Ball items (v0.17.3 round 2). Bit i: ball i was taken out of his hands by Scan / Quake / Shield and is owed back when
	 * it is no longer busy; bit i + 2: it came from the off hand.
	 */
	public int owed;
	/** Ball 0 / 1's generation: a ball item whose generation is older is a stale copy (replaced, respawned) and vanishes. */
	public int gen0;
	public int gen1;
	/** Ball 0 / 1 was destroyed (lava, the void): a new one appears in his inventory at this time (0 = none). */
	public long respawn0;
	public long respawn1;

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

	public SpinState(boolean hasPower, float spin, int thrown, long busy0, long busy1, long chargeStart, int chargeMove, int chargeTicks,
			long shieldUntil, long disguiseUntil, long leapUntil, String valkyrie, long valkyrieDeadUntil, Map<String, Long> abilityReadyAt,
			Balls balls) {
		this(hasPower, spin, thrown, busy0, busy1, chargeStart, chargeMove, chargeTicks, shieldUntil, disguiseUntil, leapUntil, valkyrie,
				valkyrieDeadUntil, abilityReadyAt);
		this.owed = balls.owed();
		this.gen0 = balls.gen0();
		this.gen1 = balls.gen1();
		this.respawn0 = balls.respawn0();
		this.respawn1 = balls.respawn1();
	}

	public SpinState copy() {
		return new SpinState(hasPower, spin, thrown, busy0, busy1, chargeStart, chargeMove, chargeTicks, shieldUntil, disguiseUntil, leapUntil,
				valkyrie, valkyrieDeadUntil, abilityReadyAt, balls());
	}

	/** The ball-item bookkeeping as one value (saved as one field). */
	public Balls balls() {
		return new Balls(owed, gen0, gen1, respawn0, respawn1);
	}

	public int gen(int ball) {
		return ball == 0 ? gen0 : gen1;
	}

	public void bumpGen(int ball) {
		if (ball == 0) {
			gen0++;
		} else {
			gen1++;
		}
	}

	public long respawn(int ball) {
		return ball == 0 ? respawn0 : respawn1;
	}

	public void setRespawn(int ball, long at) {
		if (ball == 0) {
			respawn0 = at;
		} else {
			respawn1 = at;
		}
	}

	/** The ball-item bookkeeping (see the fields). */
	public record Balls(int owed, int gen0, int gen1, long respawn0, long respawn1) {
		public static final Balls NONE = new Balls(0, 0, 0, 0L, 0L);
		public static final Codec<Balls> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("owed", 0).forGetter(Balls::owed),
				Codec.INT.optionalFieldOf("gen0", 0).forGetter(Balls::gen0),
				Codec.INT.optionalFieldOf("gen1", 0).forGetter(Balls::gen1),
				Codec.LONG.optionalFieldOf("respawn0", 0L).forGetter(Balls::respawn0),
				Codec.LONG.optionalFieldOf("respawn1", 0L).forGetter(Balls::respawn1)
		).apply(i, Balls::new));
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
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Balls.CODEC.optionalFieldOf("balls", Balls.NONE).forGetter(SpinState::balls)
	).apply(i, SpinState::new));
}
