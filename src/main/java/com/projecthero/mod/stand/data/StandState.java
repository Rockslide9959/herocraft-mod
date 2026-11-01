package com.projecthero.mod.stand.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.2: a player's Stand in one attachment ({@code projecthero:stand_state}): persistent, {@code copyOnDeath} and synced
 * ONLY to the player and to other Stand users ({@code Stands.SYNC_PREDICATE}) -- a non-Stand user's client never even
 * learns the Stand exists (the user's rule: they must not see, hear or perceive it). Only {@code Stands} and the move
 * classes change it, always through a {@link #copy()}.
 */
public final class StandState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_SUMMON = 1;
	public static final int ANIM_PUNCH_L = 2;
	public static final int ANIM_PUNCH_R = 3;
	public static final int ANIM_PUNCH_HEAVY = 4;
	public static final int ANIM_RUSH_FINISH = 5;
	public static final int ANIM_FINGER = 6;
	public static final int ANIM_GRAB = 7;
	public static final int ANIM_THROW = 8;
	public static final int ANIM_FLICK = 9;
	public static final int ANIM_LEAP = 10;
	public static final int ANIM_DASH = 11;
	public static final int ANIM_BREAKER = 12;
	public static final int ANIM_THE_WORLD = 13;
	public static final int ANIM_INHALE = 14;
	public static final int ANIM_UPPERCUT = 15;
	public static final int ANIM_DISMISS = 16;
	public static final int ANIM_CATCH = 17;

	/** The Stand's id ("" = not a Stand user). */
	public String standId;
	public boolean summoned;
	/** 0..{@code StandConfig.STAMINA_MAX}. */
	public float stamina;
	public final Map<String, Long> abilityReadyAt;
	public int animId;
	public long animStart;
	/** Time stops done (the mastery). */
	public int timeStopUses;
	/** The pose N strikes (1..4, chosen in the menu). */
	public int pose;
	/** Settings: floating ORA text over the rush; the rush turns to the nearest enemy. */
	public boolean oraText;
	public boolean autoFace;
	/** The held ORA Rush is running (since {@link #rushStart}). */
	public boolean rushing;
	public long rushStart;
	/** The held Stand Guard is up. */
	public boolean guarding;
	/** Star Breaker charging since this game time (0 = not). */
	public long chargeStart;
	/** Power Grab: the held entity's id (-1 = none). */
	public int grabbedEntity;

	/** Power Grab: the held block's state id (0 = none). */
	public int grabbedBlock;
	/** Precision Sight runs until this game time. */
	public long sightUntil;
	/** Stand Leap: wall rebounds left (-1 = not leaping). */
	public int leapRebounds;
	public long inhaleUntil;
	/** Stand Dash: the catch window runs until this game time. */
	public long dashUntil;
	/** The empty-hand punch chain: hit number (0..2) and when the last one landed. */
	public int comboStep;
	public long comboAt;

	public StandState() {
		this("", false, 0f, new HashMap<>(), 0, 0L, 0, 1, true, true, false, 0L, false, 0L, -1, 0);
		this.leapRebounds = -1;
	}

	public StandState(String standId, boolean summoned, float stamina, Map<String, Long> abilityReadyAt, int animId, long animStart,
			int timeStopUses, int pose, boolean oraText, boolean autoFace, boolean rushing, long rushStart, boolean guarding,
			long chargeStart, int grabbedEntity, int grabbedBlock) {
		this.standId = standId;
		this.summoned = summoned;
		this.stamina = stamina;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.animId = animId;
		this.animStart = animStart;
		this.timeStopUses = timeStopUses;
		this.pose = pose;
		this.oraText = oraText;
		this.autoFace = autoFace;
		this.rushing = rushing;
		this.rushStart = rushStart;
		this.guarding = guarding;
		this.chargeStart = chargeStart;
		this.grabbedEntity = grabbedEntity;
		this.grabbedBlock = grabbedBlock;
		this.leapRebounds = -1;
	}

	public StandState copy() {
		StandState n = new StandState(standId, summoned, stamina, abilityReadyAt, animId, animStart, timeStopUses, pose, oraText, autoFace,
				rushing, rushStart, guarding, chargeStart, grabbedEntity, grabbedBlock);
		n.sightUntil = sightUntil;
		n.leapRebounds = leapRebounds;
		n.inhaleUntil = inhaleUntil;
		n.dashUntil = dashUntil;
		n.comboStep = comboStep;
		n.comboAt = comboAt;
		return n;
	}

	public boolean isUser() {
		return standId != null && !standId.isEmpty();
	}

	/** The first sixteen fields (a codec group holds at most sixteen); {@link #CODEC} adds the rest. */
	private static final MapCodec<StandState> BASE = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.optionalFieldOf("stand", "").forGetter(s -> s.standId),
			Codec.BOOL.optionalFieldOf("summoned", false).forGetter(s -> s.summoned),
			Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(s -> s.stamina),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.INT.optionalFieldOf("time_stop_uses", 0).forGetter(s -> s.timeStopUses),
			Codec.INT.optionalFieldOf("pose", 1).forGetter(s -> s.pose),
			Codec.BOOL.optionalFieldOf("ora_text", true).forGetter(s -> s.oraText),
			Codec.BOOL.optionalFieldOf("auto_face", true).forGetter(s -> s.autoFace),
			Codec.BOOL.optionalFieldOf("rushing", false).forGetter(s -> s.rushing),
			Codec.LONG.optionalFieldOf("rush_start", 0L).forGetter(s -> s.rushStart),
			Codec.BOOL.optionalFieldOf("guarding", false).forGetter(s -> s.guarding),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.INT.optionalFieldOf("grabbed_entity", -1).forGetter(s -> s.grabbedEntity),
			Codec.INT.optionalFieldOf("grabbed_block", 0).forGetter(s -> s.grabbedBlock)
	).apply(i, StandState::new));

	public static final Codec<StandState> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE.forGetter(s -> s),
			Codec.LONG.optionalFieldOf("sight_until", 0L).forGetter(s -> s.sightUntil),
			Codec.INT.optionalFieldOf("leap_rebounds", -1).forGetter(s -> s.leapRebounds),
			Codec.LONG.optionalFieldOf("inhale_until", 0L).forGetter(s -> s.inhaleUntil),
			Codec.LONG.optionalFieldOf("dash_until", 0L).forGetter(s -> s.dashUntil),
			Codec.INT.optionalFieldOf("combo_step", 0).forGetter(s -> s.comboStep),
			Codec.LONG.optionalFieldOf("combo_at", 0L).forGetter(s -> s.comboAt)
	).apply(i, (s, sight, leap, inhale, dash, step, at) -> {
		s.sightUntil = sight;
		s.leapRebounds = leap;
		s.inhaleUntil = inhale;
		s.dashUntil = dash;
		s.comboStep = step;
		s.comboAt = at;
		return s;
	}));
}
