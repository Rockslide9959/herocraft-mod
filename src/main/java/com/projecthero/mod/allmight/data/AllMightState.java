package com.projecthero.mod.allmight.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole All Might / One For All power for one player in one attachment ({@code projecthero:all_might_state}):
 * persistent, {@code copyOnDeath} and synced to every client (other players render the form and the poses from it).
 * The server is authoritative; this is only ever mutated through {@code AllMight}.
 */
public final class AllMightState {
	/** Animation ids (see {@code AllMightPose} on the client). */
	public static final int ANIM_NONE = 0;
	public static final int ANIM_TRANSFORM_UP = 1;
	public static final int ANIM_TRANSFORM_DOWN = 2;
	public static final int ANIM_DETROIT = 3;
	public static final int ANIM_TEXAS = 4;
	public static final int ANIM_CAROLINA = 5;
	public static final int ANIM_NEW_HAMPSHIRE = 6;
	public static final int ANIM_COWL = 7;
	public static final int ANIM_UNITED_STATES = 8;
	public static final int ANIM_LEAP = 9;

	public boolean hasPower;
	/** false = contained / base form, true = full-power All Might (H). */
	public boolean fullPower;
	/** OFA Power, 0..{@code AllMightConfig.OFA_MAX}. */
	public float ofa;
	public long cowlUntil;
	/** Damage-proof while {@code gameTime < transformUntil}. */
	public long transformUntil;
	/** No new ability may start before this game time (wind-ups, transformation). */
	public long busyUntil;
	public long formChangedAt;
	public long lastCombatTick;
	/** Fall damage is fully cancelled until this game time (his own launches). */
	public long noFallUntil;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game-time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public AllMightState() {
		this(false, false, 0f, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0L, new HashMap<>());
	}

	public AllMightState(boolean hasPower, boolean fullPower, float ofa, long cowlUntil, long transformUntil, long busyUntil,
			long formChangedAt, long lastCombatTick, long noFallUntil, int animId, long animStart, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.fullPower = fullPower;
		this.ofa = ofa;
		this.cowlUntil = cowlUntil;
		this.transformUntil = transformUntil;
		this.busyUntil = busyUntil;
		this.formChangedAt = formChangedAt;
		this.lastCombatTick = lastCombatTick;
		this.noFallUntil = noFallUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public AllMightState copy() {
		return new AllMightState(hasPower, fullPower, ofa, cowlUntil, transformUntil, busyUntil, formChangedAt, lastCombatTick,
				noFallUntil, animId, animStart, abilityReadyAt);
	}

	public static final Codec<AllMightState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("full_power", false).forGetter(s -> s.fullPower),
			Codec.FLOAT.optionalFieldOf("ofa", 0f).forGetter(s -> s.ofa),
			Codec.LONG.optionalFieldOf("cowl_until", 0L).forGetter(s -> s.cowlUntil),
			Codec.LONG.optionalFieldOf("transform_until", 0L).forGetter(s -> s.transformUntil),
			Codec.LONG.optionalFieldOf("busy_until", 0L).forGetter(s -> s.busyUntil),
			Codec.LONG.optionalFieldOf("form_changed_at", 0L).forGetter(s -> s.formChangedAt),
			Codec.LONG.optionalFieldOf("last_combat_tick", 0L).forGetter(s -> s.lastCombatTick),
			Codec.LONG.optionalFieldOf("no_fall_until", 0L).forGetter(s -> s.noFallUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, AllMightState::new));
}
