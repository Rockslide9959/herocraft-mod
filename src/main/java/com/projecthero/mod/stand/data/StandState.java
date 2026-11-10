package com.projecthero.mod.stand.data;

import java.util.HashMap;
import java.util.List;
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
	/** Unused since v0.17.3 (Stand Dash became Ora Rush, {@link #ANIM_ORA_RUSH}). */
	public static final int ANIM_DASH = 11;
	public static final int ANIM_BREAKER = 12;
	public static final int ANIM_THE_WORLD = 13;
	public static final int ANIM_INHALE = 14;
	public static final int ANIM_UPPERCUT = 15;
	public static final int ANIM_DISMISS = 16;
	public static final int ANIM_CATCH = 17;
	/** v0.17.3: Stand Grab's combo punch (one hand holds, the other hits). */
	public static final int ANIM_GRAB_PUNCH = 18;
	/** v0.17.3: Ora Rush (Shift+X), the dash behind its fists. */
	public static final int ANIM_ORA_RUSH = 19;
	/** v0.17.3: Ora Chase's throw. */
	public static final int ANIM_CHASE_THROW = 20;
	/** v0.17.3: putting a held creature down gently. */
	public static final int ANIM_PUT_DOWN = 21;

	/** The Stand's id ("" = not a Stand user). */
	public String standId;
	public boolean summoned;
	/** 0..{@code StandConfig.STAMINA_MAX}. */
	public float stamina;
	public final Map<String, Long> abilityReadyAt;
	public int animId;
	public long animStart;
	/** The pose N strikes (1..4, chosen in the menu). */
	public int pose;
	/** Settings: floating ORA text over the barrage; the barrage turns to the nearest enemy. */
	public boolean oraText;
	public boolean autoFace;
	/** v0.17.3: the held ORA Barrage (hold R) is running (since {@link #rushStart}). */
	public boolean rushing;
	public long rushStart;
	/** The held Stand Guard is up. */
	public boolean guarding;
	/** Star Breaker charging since this game time (0 = not). */
	public long chargeStart;
	/** Stand Grab (Shift+V): the held entity's id (-1 = none). */
	public int grabbedEntity;
	/** Unused since v0.17.3 (Stand Grab holds creatures only); kept so old saves still read. */
	public int grabbedBlock;
	/** Stand Jump: -1 = not jumping (no fall damage while it is 0 or more). */
	public int leapRebounds;
	public long inhaleUntil;
	/** v0.17.3 Ora Rush (Shift+X): the dash runs until this game time. */
	public long dashUntil;
	/** The empty-hand punch chain: hit number (0..2) and when the last one landed. */
	public int comboStep;
	public long comboAt;
	/** Star Finger: where the fingertip lands, x, y, z (empty = no finger out). */
	public List<Double> fingerEnd = List.of();
	/** v0.17.3 Platinum Smash: the next hit (0..3) and when the last R input landed. */
	public int smashStep;
	public long smashAt;
	/** v0.17.3 Ora Chase: when it was sent (0 = not out), when it grabbed (0 = still flying), whom, and where the Stand is now. */
	public long chaseStart;
	public long chaseGrabAt;
	public int chaseTarget = -1;
	public List<Double> chasePos = List.of();
	/** v0.17.3: after a time stop the Stamina refills at half speed until this game time. */
	public long regenSlowUntil;
	/** v0.17.3 Stand Grab: combo punches landed on the held creature, and when the last one did. */
	public int grabCombo;
	public long grabComboAt;

	public StandState() {
		this("", false, 0f, new HashMap<>(), 0, 0L, 1, true, true, false, 0L, false, 0L, -1, 0);
		this.leapRebounds = -1;
	}

	public StandState(String standId, boolean summoned, float stamina, Map<String, Long> abilityReadyAt, int animId, long animStart,
			int pose, boolean oraText, boolean autoFace, boolean rushing, long rushStart, boolean guarding,
			long chargeStart, int grabbedEntity, int grabbedBlock) {
		this.standId = standId;
		this.summoned = summoned;
		this.stamina = stamina;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.animId = animId;
		this.animStart = animStart;
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
		StandState n = new StandState(standId, summoned, stamina, abilityReadyAt, animId, animStart, pose, oraText, autoFace,
				rushing, rushStart, guarding, chargeStart, grabbedEntity, grabbedBlock);
		n.leapRebounds = leapRebounds;
		n.inhaleUntil = inhaleUntil;
		n.dashUntil = dashUntil;
		n.comboStep = comboStep;
		n.comboAt = comboAt;
		n.fingerEnd = fingerEnd;
		n.smashStep = smashStep;
		n.smashAt = smashAt;
		n.chaseStart = chaseStart;
		n.chaseGrabAt = chaseGrabAt;
		n.chaseTarget = chaseTarget;
		n.chasePos = chasePos;
		n.regenSlowUntil = regenSlowUntil;
		n.grabCombo = grabCombo;
		n.grabComboAt = grabComboAt;
		return n;
	}

	public boolean isUser() {
		return standId != null && !standId.isEmpty();
	}

	/** v0.17.3: Ora Chase has sent the Stand off (it is away from its user). */
	public boolean chasing() {
		return chaseStart != 0L && chasePos.size() == 3;
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

	/** The later fields (v0.17.3: the old {@code sight_until} went with Precision Sight; an old save's copy is ignored). */
	private static final MapCodec<StandState> MORE = RecordCodecBuilder.mapCodec(i -> i.group(
			BASE.forGetter(s -> s),
			Codec.INT.optionalFieldOf("leap_rebounds", -1).forGetter(s -> s.leapRebounds),
			Codec.LONG.optionalFieldOf("inhale_until", 0L).forGetter(s -> s.inhaleUntil),
			Codec.LONG.optionalFieldOf("dash_until", 0L).forGetter(s -> s.dashUntil),
			Codec.INT.optionalFieldOf("combo_step", 0).forGetter(s -> s.comboStep),
			Codec.LONG.optionalFieldOf("combo_at", 0L).forGetter(s -> s.comboAt),
			Codec.DOUBLE.listOf().optionalFieldOf("finger_end", List.of()).forGetter(s -> s.fingerEnd)
	).apply(i, (s, leap, inhale, dash, step, at, finger) -> {
		s.leapRebounds = leap;
		s.inhaleUntil = inhale;
		s.dashUntil = dash;
		s.comboStep = step;
		s.comboAt = at;
		s.fingerEnd = List.copyOf(finger);
		return s;
	}));

	public static final Codec<StandState> CODEC = RecordCodecBuilder.create(i -> i.group(
			MORE.forGetter(s -> s),
			Codec.INT.optionalFieldOf("smash_step", 0).forGetter(s -> s.smashStep),
			Codec.LONG.optionalFieldOf("smash_at", 0L).forGetter(s -> s.smashAt),
			Codec.LONG.optionalFieldOf("chase_start", 0L).forGetter(s -> s.chaseStart),
			Codec.LONG.optionalFieldOf("chase_grab_at", 0L).forGetter(s -> s.chaseGrabAt),
			Codec.INT.optionalFieldOf("chase_target", -1).forGetter(s -> s.chaseTarget),
			Codec.DOUBLE.listOf().optionalFieldOf("chase_pos", List.of()).forGetter(s -> s.chasePos),
			Codec.LONG.optionalFieldOf("regen_slow_until", 0L).forGetter(s -> s.regenSlowUntil),
			Codec.INT.optionalFieldOf("grab_combo", 0).forGetter(s -> s.grabCombo),
			Codec.LONG.optionalFieldOf("grab_combo_at", 0L).forGetter(s -> s.grabComboAt)
	).apply(i, (s, smashStep, smashAt, chaseStart, chaseGrabAt, chaseTarget, chasePos, regenSlow, grabCombo, grabComboAt) -> {
		s.smashStep = smashStep;
		s.smashAt = smashAt;
		s.chaseStart = chaseStart;
		s.chaseGrabAt = chaseGrabAt;
		s.chaseTarget = chaseTarget;
		s.chasePos = List.copyOf(chasePos);
		s.regenSlowUntil = regenSlow;
		s.grabCombo = grabCombo;
		s.grabComboAt = grabComboAt;
		return s;
	}));
}
