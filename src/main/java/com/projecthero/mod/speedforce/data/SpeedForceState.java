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
	/** v0.17.1 (user) the hold moves: their poses last while the key is held ({@link #holdAnim}). */
	public static final int ANIM_MASS_WINDUP = 11;
	public static final int ANIM_THROW_CHARGE = 12;
	public static final int ANIM_ARMS = 13;
	public static final int ANIM_BLITZ = 14;
	/** v0.17.1 (user) what a held Z is doing ({@link #flashLeft}): Flash Time, or (pressed at 100% momentum) the portal charge. */
	public static final int Z_FLASH_TIME = 1;
	public static final int Z_PORTAL = 2;

	public boolean hasPower;
	/** 0..1. */
	public float momentum;
	/** The Speed Force meter, 0..{@code SpeedForceConfig.METER_MAX}. */
	public float meter;
	/** C: Speed Force Surge runs until this game time (momentum locked at 100%). */
	public long surgeUntil;
	/**
	 * Z: v0.17.1 (user) what a held Z is doing -- flashLeft is {@link #Z_FLASH_TIME} for Flash Time, {@link #Z_PORTAL} for the
	 * Speed Force portal charge (Z pressed at 100% momentum: no Flash Time with it), 0 otherwise; flashTotal is the portal's
	 * charge in ticks (0..{@code SpeedForceConfig.PORTAL_TICKS}).
	 */
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
	/** v0.17.1: the anim is a hold move's pose -- it lasts until the key is let go (then animId goes back to NONE). */
	public boolean holdAnim;
	/**
	 * v0.17.1 (user): ticks left of the drain after leaving the Speed Force (no Speed Force gain, 50% slower, 50% weaker),
	 * 0..{@code SpeedForceConfig.DRAINED_TICKS}. Saved (it lasts through a logout).
	 */
	public int drained;
	/**
	 * v0.17.1 (user, the Shift+N menu): Auto-Slowdown -- the run eases off before a big drop or lava / fire ahead. Saved
	 * inverted (bit 3 set = off), so every older save reads as on.
	 */
	public boolean autoSlow = true;
	/** v0.17.1 (user, the Shift+N menu): Speed Mode vein mining (an ore takes the connected same ores with it). Bit 4 set = off. */
	public boolean veinMine = true;
	/**
	 * v0.17.1 (user, the Shift+N menu): the top speed -- momentum never builds past {@code speedCap / 10}, 1..10 (10 = no
	 * cap). Saved as {@code 10 - speedCap} in bits 12-15 (so 0, every older save, is no cap).
	 */
	public int speedCap = 10;

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

	/**
	 * Speed Mode, Ground-Stick, the hold pose, Auto-Slowdown off (bit 3), vein mining off (bit 4), the colour (bits 8-11: ten
	 * colours fit in four), the speed cap (bits 12-15, {@code 10 - speedCap}) and the drain (bits 16-31) in one saved int (the
	 * codec is at its 16 fields). Bits 5-7 are still free.
	 */
	int flags() {
		int cap = 10 - Math.max(1, Math.min(10, speedCap));
		return (speedMode ? 1 : 0) | (groundStick ? 2 : 0) | (holdAnim ? 4 : 0) | (autoSlow ? 0 : 8) | (veinMine ? 0 : 16)
				| ((colour & 0x0F) << 8) | ((cap & 0x0F) << 12) | ((Math.max(0, Math.min(0xFFFF, drained)) & 0xFFFF) << 16);
	}

	private SpeedForceState withFlags(int flags) {
		this.speedMode = (flags & 1) != 0;
		this.groundStick = (flags & 2) != 0;
		this.holdAnim = (flags & 4) != 0;
		this.autoSlow = (flags & 8) == 0;
		this.veinMine = (flags & 16) == 0;
		this.colour = (flags >> 8) & 0x0F;
		this.speedCap = Math.max(1, 10 - ((flags >> 12) & 0x0F));
		this.drained = (flags >>> 16) & 0xFFFF;
		return this;
	}

	/** v0.17.1: the speed cap as a fraction of full momentum (0.1..1). */
	public float capFraction() {
		return Math.max(1, Math.min(10, speedCap)) / 10f;
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
