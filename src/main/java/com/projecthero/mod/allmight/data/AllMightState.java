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
	/** false = Base Form (a plain player), true = Power Form (H). */
	public boolean fullPower;
	/** OFA Power, 0..{@code AllMightConfig.OFA_MAX}. */
	public float ofa;
	/** Plus Ultra (C) is on: OFA drains and every ability hits 30% harder. */
	public boolean plusUltra;
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
	/** v0.12.38: game time the United States of Smash charge began, 0 when not charging (drives the HUD's charge bar). */
	public long chargeStart;
	/** {@code abilityId} -> absolute game-time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public AllMightState() {
		this(false, false, 0f, false, 0L, 0L, 0L, 0L, 0L, 0, 0L, new HashMap<>(), 0L);
	}

	public AllMightState(boolean hasPower, boolean fullPower, float ofa, boolean plusUltra, long transformUntil, long busyUntil,
			long formChangedAt, long lastCombatTick, long noFallUntil, int animId, long animStart, Map<String, Long> abilityReadyAt, long chargeStart) {
		this.hasPower = hasPower;
		this.fullPower = fullPower;
		this.ofa = ofa;
		this.plusUltra = plusUltra;
		this.transformUntil = transformUntil;
		this.busyUntil = busyUntil;
		this.formChangedAt = formChangedAt;
		this.lastCombatTick = lastCombatTick;
		this.noFallUntil = noFallUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.chargeStart = chargeStart;
	}

	public AllMightState copy() {
		return new AllMightState(hasPower, fullPower, ofa, plusUltra, transformUntil, busyUntil, formChangedAt, lastCombatTick,
				noFallUntil, animId, animStart, abilityReadyAt, chargeStart);
	}

	public static final Codec<AllMightState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("full_power", false).forGetter(s -> s.fullPower),
			Codec.FLOAT.optionalFieldOf("ofa", 0f).forGetter(s -> s.ofa),
			Codec.BOOL.optionalFieldOf("plus_ultra", false).forGetter(s -> s.plusUltra),
			Codec.LONG.optionalFieldOf("transform_until", 0L).forGetter(s -> s.transformUntil),
			Codec.LONG.optionalFieldOf("busy_until", 0L).forGetter(s -> s.busyUntil),
			Codec.LONG.optionalFieldOf("form_changed_at", 0L).forGetter(s -> s.formChangedAt),
			Codec.LONG.optionalFieldOf("last_combat_tick", 0L).forGetter(s -> s.lastCombatTick),
			Codec.LONG.optionalFieldOf("no_fall_until", 0L).forGetter(s -> s.noFallUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart)
	).apply(i, AllMightState::new));
}
