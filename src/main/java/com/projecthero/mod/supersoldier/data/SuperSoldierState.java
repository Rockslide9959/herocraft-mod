package com.projecthero.mod.supersoldier.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole Super Soldier power for one player in one attachment ({@code projecthero:super_soldier_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the owner's HUD reads the cooldowns and the Onslaught / Focus timers).
 * The server is authoritative; this is only ever mutated through {@code SuperSoldier}.
 */
public final class SuperSoldierState {
	public boolean hasPower;
	/** No new move may start before this game time (the Combo Strike's three punches, the Bash charge). */
	public long busyUntil;
	/** The Tactical Roll's invulnerability frames run until this game time. */
	public long iframeUntil;
	/** Fall damage is cancelled until this game time (his own leaps). */
	public long noFallUntil;
	/** Super Soldier Onslaught (Shift+Z) runs until this game time; 0 when it is off. */
	public long onslaughtUntil;
	/** Tactical Focus (Shift+V) runs until this game time; 0 when it is off. */
	public long focusUntil;
	/** Last game time he hurt or was hurt by something (the out-of-combat regeneration waits on it). */
	public long lastCombatTick;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public SuperSoldierState() {
		this(false, 0L, 0L, 0L, 0L, 0L, 0L, new HashMap<>());
	}

	public SuperSoldierState(boolean hasPower, long busyUntil, long iframeUntil, long noFallUntil, long onslaughtUntil,
			long focusUntil, long lastCombatTick, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.busyUntil = busyUntil;
		this.iframeUntil = iframeUntil;
		this.noFallUntil = noFallUntil;
		this.onslaughtUntil = onslaughtUntil;
		this.focusUntil = focusUntil;
		this.lastCombatTick = lastCombatTick;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public SuperSoldierState copy() {
		return new SuperSoldierState(hasPower, busyUntil, iframeUntil, noFallUntil, onslaughtUntil, focusUntil, lastCombatTick,
				abilityReadyAt);
	}

	public static final Codec<SuperSoldierState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.LONG.optionalFieldOf("busy_until", 0L).forGetter(s -> s.busyUntil),
			Codec.LONG.optionalFieldOf("iframe_until", 0L).forGetter(s -> s.iframeUntil),
			Codec.LONG.optionalFieldOf("no_fall_until", 0L).forGetter(s -> s.noFallUntil),
			Codec.LONG.optionalFieldOf("onslaught_until", 0L).forGetter(s -> s.onslaughtUntil),
			Codec.LONG.optionalFieldOf("focus_until", 0L).forGetter(s -> s.focusUntil),
			Codec.LONG.optionalFieldOf("last_combat_tick", 0L).forGetter(s -> s.lastCombatTick),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, SuperSoldierState::new));
}
