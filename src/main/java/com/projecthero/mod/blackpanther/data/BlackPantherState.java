package com.projecthero.mod.blackpanther.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3: the whole Black Panther power for one player in one attachment ({@code projecthero:black_panther_state}):
 * persistent, {@code copyOnDeath} and synced to every client. Only {@code BlackPanther} and its moves change it, always
 * through a {@link #copy()}. {@link #visionUntil} runs before {@link #hasPower}: the Ancestral Plane comes first.
 */
public final class BlackPantherState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_SUIT_UP = 1;
	public static final int ANIM_SUIT_DOWN = 2;
	public static final int ANIM_PULSE = 3;
	public static final int ANIM_OVERLOAD = 4;
	public static final int ANIM_CLAW = 5;
	public static final int ANIM_POUNCE = 6;
	public static final int ANIM_DROP = 7;
	public static final int ANIM_THROW = 8;
	public static final int ANIM_RETURN = 9;

	public boolean hasPower;
	public boolean suited;
	public long suitChangeAt;
	public float kinetic;
	public int animId;
	public long animStart;
	public Map<String, Long> abilityReadyAt;
	/** The last hit taken or landed (the bar only drains long after it). */
	public long lastFightAt;
	/** The Ancestral Plane shows until this game time, then the power is granted (0 = no vision running). */
	public long visionUntil;
	public boolean stalking;
	public long sensesUntil;
	/** C is held. */
	public boolean absorbing;
	/** Kinetic gained in the current Absorb Stance. */
	public float absorbed;
	/** The last shockwave: when, how wide, and where (x, y, z). */
	public long pulseStart;
	public float pulseRadius;
	public List<Double> pulsePos;

	public BlackPantherState() {
		this(false, false, 0L, 0f, 0, 0L, new HashMap<>(), 0L, 0L, false, 0L, false, 0f, 0L, 0f, new ArrayList<>());
	}

	public BlackPantherState(boolean hasPower, boolean suited, long suitChangeAt, float kinetic, int animId, long animStart,
			Map<String, Long> abilityReadyAt, long lastFightAt, long visionUntil, boolean stalking, long sensesUntil, boolean absorbing,
			float absorbed, long pulseStart, float pulseRadius, List<Double> pulsePos) {
		this.hasPower = hasPower;
		this.suited = suited;
		this.suitChangeAt = suitChangeAt;
		this.kinetic = kinetic;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.lastFightAt = lastFightAt;
		this.visionUntil = visionUntil;
		this.stalking = stalking;
		this.sensesUntil = sensesUntil;
		this.absorbing = absorbing;
		this.absorbed = absorbed;
		this.pulseStart = pulseStart;
		this.pulseRadius = pulseRadius;
		this.pulsePos = new ArrayList<>(pulsePos);
	}

	public BlackPantherState copy() {
		return new BlackPantherState(hasPower, suited, suitChangeAt, kinetic, animId, animStart, abilityReadyAt, lastFightAt, visionUntil,
				stalking, sensesUntil, absorbing, absorbed, pulseStart, pulseRadius, pulsePos);
	}

	public static final Codec<BlackPantherState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("suited", false).forGetter(s -> s.suited),
			Codec.LONG.optionalFieldOf("suit_change_at", 0L).forGetter(s -> s.suitChangeAt),
			Codec.FLOAT.optionalFieldOf("kinetic", 0f).forGetter(s -> s.kinetic),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("last_fight_at", 0L).forGetter(s -> s.lastFightAt),
			Codec.LONG.optionalFieldOf("vision_until", 0L).forGetter(s -> s.visionUntil),
			Codec.BOOL.optionalFieldOf("stalking", false).forGetter(s -> s.stalking),
			Codec.LONG.optionalFieldOf("senses_until", 0L).forGetter(s -> s.sensesUntil),
			Codec.BOOL.optionalFieldOf("absorbing", false).forGetter(s -> s.absorbing),
			Codec.FLOAT.optionalFieldOf("absorbed", 0f).forGetter(s -> s.absorbed),
			Codec.LONG.optionalFieldOf("pulse_start", 0L).forGetter(s -> s.pulseStart),
			Codec.FLOAT.optionalFieldOf("pulse_radius", 0f).forGetter(s -> s.pulseRadius),
			Codec.DOUBLE.listOf().optionalFieldOf("pulse_pos", List.of()).forGetter(s -> s.pulsePos)
	).apply(i, BlackPantherState::new));
}
