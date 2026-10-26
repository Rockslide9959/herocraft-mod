package com.projecthero.mod.speedforce.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1: the whole Speed Force Speedster for one player in one attachment ({@code projecthero:speed_force_state}):
 * persistent, kept through death and synced to every client (others see the skate run, the lightning growing with the
 * momentum and the phase; the owner's HUD needs the momentum, the Speed Force and the cooldowns). Only
 * {@code SpeedForceSpeedster} and its move classes change it, always through a {@link #copy()}.
 */
public final class SpeedForceState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_PUNCHES = 1;
	public static final int ANIM_MASS = 2;
	public static final int ANIM_THROW = 3;
	public static final int ANIM_TORNADO = 4;
	public static final int ANIM_STEP = 5;
	public static final int ANIM_HEAL = 6;
	public static final int ANIM_SURGE = 7;
	public static final int ANIM_AWAKEN = 8;
	public static final int ANIM_GRAB = 9;
	public static final int ANIM_CHARGE = 10;

	public boolean hasPower;
	/** 0..1. */
	public float momentum;
	/** The Speed Force meter, 0..{@code SpeedForceConfig.METER_MAX}. */
	public float meter;
	/** C: Speed Force Surge runs until this game time (momentum locked at 100%). */
	public long surgeUntil;
	/** Z: Flash Time's ticks left on the caster's own clock (HUD mirror), and how long it was. */
	public int flashLeft;
	public int flashTotal;
	/** Shift+X held. */
	public boolean phasing;
	/** V: the entity carried (0 = nothing). */
	public int carryId;
	/** Shift+G: the Speed Tornado's ticks left. */
	public int tornadoLeft;
	/** G: the Lightning Throw's wind-up ticks left. */
	public int throwLeft;
	/** Shift+Z: the Time Remnant fights until this game time. */
	public long remnantUntil;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** v0.17.1 (user): C -- Speed Mode on. Off, the speedster runs like anyone (no momentum, no trail, no lightning). */
	public boolean speedMode;
	/** v0.17.1 (user): Shift+C held -- the charge stance started at this game time (0 = not charging). */
	public long chargeStart;
	/** v0.17.1 (user, the Shift+N menu): Ground-Stick on. */
	public boolean groundStick;
	/** v0.17.1 (user, the Shift+N menu): the lightning colour, an index into {@code SpeedForceColours} (0 = gold). */
	public int colour;

	public SpeedForceState() {
		this(false, 0f, 0f, 0L, 0, 0, false, 0, 0, 0, 0L, 0, 0L, new HashMap<>(), false, 0L);
	}

	public SpeedForceState(boolean hasPower, float momentum, float meter, long surgeUntil, int flashLeft, int flashTotal,
			boolean phasing, int carryId, int tornadoLeft, int throwLeft, long remnantUntil, int animId, long animStart,
			Map<String, Long> abilityReadyAt, boolean speedMode, long chargeStart) {
		this.hasPower = hasPower;
		this.momentum = momentum;
		this.meter = meter;
		this.surgeUntil = surgeUntil;
		this.flashLeft = flashLeft;
		this.flashTotal = flashTotal;
		this.phasing = phasing;
		this.carryId = carryId;
		this.tornadoLeft = tornadoLeft;
		this.throwLeft = throwLeft;
		this.remnantUntil = remnantUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.speedMode = speedMode;
		this.chargeStart = chargeStart;
	}

	/** Speed Mode, Ground-Stick and the colour in one saved int (the codec is at its 16 fields). */
	int flags() {
		return (speedMode ? 1 : 0) | (groundStick ? 2 : 0) | ((colour & 0xFF) << 8);
	}

	private SpeedForceState withFlags(int flags) {
		this.speedMode = (flags & 1) != 0;
		this.groundStick = (flags & 2) != 0;
		this.colour = (flags >> 8) & 0xFF;
		return this;
	}

	public SpeedForceState copy() {
		return new SpeedForceState(hasPower, momentum, meter, surgeUntil, flashLeft, flashTotal, phasing, carryId, tornadoLeft,
				throwLeft, remnantUntil, animId, animStart, abilityReadyAt, speedMode, chargeStart).withFlags(flags());
	}

	public static final Codec<SpeedForceState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("momentum", 0f).forGetter(s -> s.momentum),
			Codec.FLOAT.optionalFieldOf("meter", 0f).forGetter(s -> s.meter),
			Codec.LONG.optionalFieldOf("surge_until", 0L).forGetter(s -> s.surgeUntil),
			Codec.INT.optionalFieldOf("flash_left", 0).forGetter(s -> s.flashLeft),
			Codec.INT.optionalFieldOf("flash_total", 0).forGetter(s -> s.flashTotal),
			Codec.BOOL.optionalFieldOf("phasing", false).forGetter(s -> s.phasing),
			Codec.INT.optionalFieldOf("carry_id", 0).forGetter(s -> s.carryId),
			Codec.INT.optionalFieldOf("tornado_left", 0).forGetter(s -> s.tornadoLeft),
			Codec.INT.optionalFieldOf("throw_left", 0).forGetter(s -> s.throwLeft),
			Codec.LONG.optionalFieldOf("remnant_until", 0L).forGetter(s -> s.remnantUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
				Codec.INT.optionalFieldOf("flags", 0).forGetter(SpeedForceState::flags),
				Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart)
	).apply(i, (hp, mo, me, su, fl, ft, ph, ca, to, th, re, an, as, ready, flags, cs) ->
			new SpeedForceState(hp, mo, me, su, fl, ft, ph, ca, to, th, re, an, as, ready, false, cs).withFlags(flags)));
}
