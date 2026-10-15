package com.projecthero.mod.ghostrider.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.5: the whole Ghost Rider power for one player in one attachment ({@code projecthero:ghost_rider_state}): persistent,
 * {@code copyOnDeath} and synced to every client (others see the flaming skull, the chain, the poses; the owner's HUD
 * needs Hellfire, Vengeance and the cooldowns). Only {@code GhostRider} and its move classes change it, always through
 * a {@link #copy()}.
 */
public final class GhostRiderState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_TRANSFORM = 1;
	public static final int ANIM_REVERT = 2;
	public static final int ANIM_WAVE = 3;
	public static final int ANIM_WHIP = 4;
	public static final int ANIM_SPIN = 5;
	public static final int ANIM_CYCLE_JUMP = 6;
	public static final int ANIM_MARK = 7;
	public static final int ANIM_STARE = 8;
	public static final int ANIM_RUSH = 9;
	public static final int ANIM_ERUPTION = 10;
	public static final int ANIM_SHIELD = 11;
	public static final int ANIM_WRATH = 12;
	public static final int ANIM_SUMMON = 13;

	public boolean hasPower;
	/** The Spirit of Vengeance is out (H). */
	public boolean transformed;
	/** Game time the last change started (the client plays the flames climbing / dying from it). */
	public long formChangeAt;
	/** False until the first transformation: the contract's blood price (5 hearts) is still owed until then. */
	public boolean bonded;
	/** 0..{@code GhostRiderConfig.HELLFIRE_MAX}. */
	public float hellfire;
	/** 0..{@code GhostRiderConfig.VENGEANCE_MAX}: filled by hitting the guilty, spent by the Penance Stare. */
	public float vengeance;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** The held Hellfire Blast is firing. */
	public boolean blasting;
	/** The chain is out: the whip lash (to {@link #chainTarget}) or the spin, until this game time. */
	public long chainUntil;
	/** Where the whip's tip is: x, y, z (empty = spinning round him). */
	public List<Double> chainTarget;
	/** Chain Spin runs until this game time. */
	public long spinUntil;
	/** Soul Shield runs until this game time. */
	public long shieldUntil;
	/** The entity the Penance Stare is locked on (-1 = none). */
	public int stareTarget;
	/** Hellfire Rush is charging forward. */
	public boolean rushing;

	public GhostRiderState() {
		this(false, false, 0L, false, 0f, 0f, 0, 0L, new HashMap<>(), false, 0L, List.of(), 0L, 0L, -1, false);
	}

	public GhostRiderState(boolean hasPower, boolean transformed, long formChangeAt, boolean bonded, float hellfire, float vengeance,
			int animId, long animStart, Map<String, Long> abilityReadyAt, boolean blasting, long chainUntil, List<Double> chainTarget,
			long spinUntil, long shieldUntil, int stareTarget, boolean rushing) {
		this.hasPower = hasPower;
		this.transformed = transformed;
		this.formChangeAt = formChangeAt;
		this.bonded = bonded;
		this.hellfire = hellfire;
		this.vengeance = vengeance;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.blasting = blasting;
		this.chainUntil = chainUntil;
		this.chainTarget = List.copyOf(chainTarget);
		this.spinUntil = spinUntil;
		this.shieldUntil = shieldUntil;
		this.stareTarget = stareTarget;
		this.rushing = rushing;
	}

	public GhostRiderState copy() {
		return new GhostRiderState(hasPower, transformed, formChangeAt, bonded, hellfire, vengeance, animId, animStart, abilityReadyAt,
				blasting, chainUntil, chainTarget, spinUntil, shieldUntil, stareTarget, rushing);
	}

	public static final Codec<GhostRiderState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("transformed", false).forGetter(s -> s.transformed),
			Codec.LONG.optionalFieldOf("form_change_at", 0L).forGetter(s -> s.formChangeAt),
			Codec.BOOL.optionalFieldOf("bonded", false).forGetter(s -> s.bonded),
			Codec.FLOAT.optionalFieldOf("hellfire", 0f).forGetter(s -> s.hellfire),
			Codec.FLOAT.optionalFieldOf("vengeance", 0f).forGetter(s -> s.vengeance),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.BOOL.optionalFieldOf("blasting", false).forGetter(s -> s.blasting),
			Codec.LONG.optionalFieldOf("chain_until", 0L).forGetter(s -> s.chainUntil),
			Codec.DOUBLE.listOf().optionalFieldOf("chain_target", List.of()).forGetter(s -> s.chainTarget),
			Codec.LONG.optionalFieldOf("spin_until", 0L).forGetter(s -> s.spinUntil),
			Codec.LONG.optionalFieldOf("shield_until", 0L).forGetter(s -> s.shieldUntil),
			Codec.INT.optionalFieldOf("stare_target", -1).forGetter(s -> s.stareTarget),
			Codec.BOOL.optionalFieldOf("rushing", false).forGetter(s -> s.rushing)
	).apply(i, GhostRiderState::new));
}
