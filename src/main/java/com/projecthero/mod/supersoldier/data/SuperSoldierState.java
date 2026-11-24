package com.projecthero.mod.supersoldier.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole Super Soldier power for one player in one attachment ({@code projecthero:super_soldier_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the owner's HUD reads the cooldowns and the Assemble / Counter timers; every
 * viewer reads the animation id to pose him). The server is authoritative; this is only ever mutated through {@code SuperSoldier}.
 *
 * <p>v0.17.7 rework: {@code onslaughtUntil} / {@code focusUntil} became {@link #assembleUntil}; {@link #counterUntil} (V: Counter
 * Stance) and the animation fields ({@link #animId}, {@link #animStart}, {@link #animOff}) are new. Old saves' extra keys are ignored.
 */
public final class SuperSoldierState {
	// ---- animation ids (client: SuperSoldierPose). 0 = none.
	public static final int ANIM_NONE = 0;
	/** Shift+V: feet planted, fist (or shield) raised -- "Assemble!". */
	public static final int ANIM_ASSEMBLE = 1;
	/** V: the brace (held for the window). */
	public static final int ANIM_COUNTER = 2;
	/** V: the counter strike after a negated hit. */
	public static final int ANIM_COUNTER_HIT = 3;
	/** Shift+Z unarmed: the dash in. */
	public static final int ANIM_BARRAGE_DASH = 4;
	/** Shift+Z unarmed: the flurry, wind-up and haymaker (timed from his arrival). */
	public static final int ANIM_BARRAGE = 5;
	/** Shift+Z with a shield: the leap, the throw and the hover (held until the catch). */
	public static final int ANIM_STORM_THROW = 6;
	/** Shift+Z with a shield: the catch, a front flip and the dive (held until the landing). */
	public static final int ANIM_STORM_CATCH = 7;
	/** Shift+Z with a shield: the slam into the ground. */
	public static final int ANIM_STORM_SLAM = 8;

	public boolean hasPower;
	/** No new move may start before this game time (the Combo Strike's three punches, the Bash charge). */
	public long busyUntil;
	/** The Tactical Roll's invulnerability frames run until this game time. */
	public long iframeUntil;
	/** Fall damage is cancelled until this game time (his own leaps). */
	public long noFallUntil;
	/** Assemble (Shift+V) runs until this game time; 0 when it is off. */
	public long assembleUntil;
	/** Counter Stance (V) is braced until this game time; 0 when it is off. */
	public long counterUntil;
	/** Last game time he hurt or was hurt by something (the out-of-combat regeneration waits on it). */
	public long lastCombatTick;
	/** The move animation playing (ANIM_*) and the game time it started. */
	public int animId;
	public long animStart;
	/** The animation's lead arm is the OFF hand's (the shield arm: a shield in the off hand raised, thrown, caught). */
	public boolean animOff;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	public SuperSoldierState() {
		this(false, 0L, 0L, 0L, 0L, 0L, 0L, ANIM_NONE, 0L, false, new HashMap<>());
	}

	public SuperSoldierState(boolean hasPower, long busyUntil, long iframeUntil, long noFallUntil, long assembleUntil, long counterUntil,
			long lastCombatTick, int animId, long animStart, boolean animOff, Map<String, Long> abilityReadyAt) {
		this.hasPower = hasPower;
		this.busyUntil = busyUntil;
		this.iframeUntil = iframeUntil;
		this.noFallUntil = noFallUntil;
		this.assembleUntil = assembleUntil;
		this.counterUntil = counterUntil;
		this.lastCombatTick = lastCombatTick;
		this.animId = animId;
		this.animStart = animStart;
		this.animOff = animOff;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
	}

	public SuperSoldierState copy() {
		return new SuperSoldierState(hasPower, busyUntil, iframeUntil, noFallUntil, assembleUntil, counterUntil, lastCombatTick, animId,
				animStart, animOff, abilityReadyAt);
	}

	public static final Codec<SuperSoldierState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.LONG.optionalFieldOf("busy_until", 0L).forGetter(s -> s.busyUntil),
			Codec.LONG.optionalFieldOf("iframe_until", 0L).forGetter(s -> s.iframeUntil),
			Codec.LONG.optionalFieldOf("no_fall_until", 0L).forGetter(s -> s.noFallUntil),
			Codec.LONG.optionalFieldOf("assemble_until", 0L).forGetter(s -> s.assembleUntil),
			Codec.LONG.optionalFieldOf("counter_until", 0L).forGetter(s -> s.counterUntil),
			Codec.LONG.optionalFieldOf("last_combat_tick", 0L).forGetter(s -> s.lastCombatTick),
			Codec.INT.optionalFieldOf("anim_id", ANIM_NONE).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.BOOL.optionalFieldOf("anim_off", false).forGetter(s -> s.animOff),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt))
	).apply(i, SuperSoldierState::new));
}
