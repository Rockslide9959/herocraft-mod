package com.projecthero.mod.nova.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.15.13: the whole Nova power for one player in one attachment ({@code projecthero:nova_state}): persistent,
 * {@code copyOnDeath} and synced to every client (others see the suit, the flight pose, the beam, the shield bubble, the
 * Gravity Well and the Overload aura; the owner's HUD needs the Nova Force, the cooldowns and the timers). The server is
 * authoritative; only {@code Nova} and its move classes mutate it, always through a {@link #copy()}.
 */
public final class NovaState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_VOLLEY = 1;
	public static final int ANIM_PULSE = 2;
	public static final int ANIM_SLAM = 3;
	public static final int ANIM_OVERLOAD = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_LAUNCH = 6;
	public static final int ANIM_WELL = 7;
	public static final int ANIM_LOCK = 8;
	public static final int ANIM_SCAN = 9;
	public static final int ANIM_TRANSFER = 10;
	public static final int ANIM_BURST = 11;

	public boolean hasPower;
	/** The Nova Corps uniform is on (H). */
	public boolean suited;
	/** Game time the last suit-up / suit-down started (the client plays the wrap / unravel from it). */
	public long suitChangeAt;
	/** 0..{@code NovaConfig.FORCE_MAX}. */
	public float force;
	/** Flying under the Nova Force (double-tap jump). */
	public boolean flying;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** The held Nova Blast is firing right now. */
	public boolean blasting;
	/** Force Shield up until this game time. */
	public long shieldUntil;
	/** NOVA OVERLOAD running until this game time. */
	public long overloadUntil;
	/** The Gravity Well collapses at this game time (0 = none). */
	public long wellUntil;
	/** Where the Gravity Well sits: x, y, z (empty when none). */
	public List<Double> wellPos;
	/** Diving in a Gravity Slam (pose). */
	public boolean slamming;

	public NovaState() {
		this(false, false, 0L, 0f, false, 0, 0L, new HashMap<>(), false, 0L, 0L, 0L, List.of(), false);
	}

	public NovaState(boolean hasPower, boolean suited, long suitChangeAt, float force, boolean flying, int animId, long animStart,
			Map<String, Long> abilityReadyAt, boolean blasting, long shieldUntil, long overloadUntil, long wellUntil,
			List<Double> wellPos, boolean slamming) {
		this.hasPower = hasPower;
		this.suited = suited;
		this.suitChangeAt = suitChangeAt;
		this.force = force;
		this.flying = flying;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.blasting = blasting;
		this.shieldUntil = shieldUntil;
		this.overloadUntil = overloadUntil;
		this.wellUntil = wellUntil;
		this.wellPos = List.copyOf(wellPos);
		this.slamming = slamming;
	}

	public NovaState copy() {
		return new NovaState(hasPower, suited, suitChangeAt, force, flying, animId, animStart, abilityReadyAt, blasting,
				shieldUntil, overloadUntil, wellUntil, wellPos, slamming);
	}

	public static final Codec<NovaState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("suited", false).forGetter(s -> s.suited),
			Codec.LONG.optionalFieldOf("suit_change_at", 0L).forGetter(s -> s.suitChangeAt),
			Codec.FLOAT.optionalFieldOf("force", 0f).forGetter(s -> s.force),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.BOOL.optionalFieldOf("blasting", false).forGetter(s -> s.blasting),
			Codec.LONG.optionalFieldOf("shield_until", 0L).forGetter(s -> s.shieldUntil),
			Codec.LONG.optionalFieldOf("overload_until", 0L).forGetter(s -> s.overloadUntil),
			Codec.LONG.optionalFieldOf("well_until", 0L).forGetter(s -> s.wellUntil),
			Codec.DOUBLE.listOf().optionalFieldOf("well_pos", List.of()).forGetter(s -> s.wellPos),
			Codec.BOOL.optionalFieldOf("slamming", false).forGetter(s -> s.slamming)
	).apply(i, NovaState::new));
}
