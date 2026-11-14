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
	// v0.17.4: the unsuited kit (T'Challa without the suit), and the suited moves that had no clip of their own
	public static final int ANIM_COMBO = 10;
	public static final int ANIM_SWEEP = 11;
	public static final int ANIM_TAKEDOWN = 12;
	public static final int ANIM_DISARM = 13;
	public static final int ANIM_VAULT = 14;
	public static final int ANIM_BACKFLIP = 15;
	public static final int ANIM_KNIFE = 16;
	public static final int ANIM_SCAN = 17;
	public static final int ANIM_RIPOSTE = 18;
	public static final int ANIM_RESOLVE = 19;
	public static final int ANIM_LEAP = 20;
	public static final int ANIM_FLASH = 21;
	public static final int ANIM_POUNCE_STRIKE = 22;

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
	/** Panther Senses is on (a toggle since the v0.17.3 tester round; it ends when the suit comes off). */
	public boolean senses;
	/** N: the vibranium claws are out (the +5 melee only applies while they are). */
	public boolean clawsOut;
	/** Shift+N: the mask is off (the suit's head is not drawn). */
	public boolean unmasked;
	/** Hold H: the necklace is off -- no suit and no moves, only the herb's Speed I and Jump Boost I. */
	public boolean necklaceOff;
	/** v0.17.4: the crafted Panther Habit is in the necklace (H only suits up once it is). */
	public boolean habit;
	/** v0.17.4: this state has been through the habit migration (a power held in 0.17.3 keeps its suit). */
	public boolean v174;
	/** C is held. */
	public boolean absorbing;
	/** Kinetic gained in the current Absorb Stance. */
	public float absorbed;
	/** The last shockwave: when, how wide, and where (x, y, z). */
	public long pulseStart;
	public float pulseRadius;
	public List<Double> pulsePos;

	public BlackPantherState() {
		this(false, false, 0L, 0f, 0, 0L, new HashMap<>(), 0L, 0L, false, 0, false, 0f, 0L, 0f, new ArrayList<>());
	}

	public BlackPantherState(boolean hasPower, boolean suited, long suitChangeAt, float kinetic, int animId, long animStart,
			Map<String, Long> abilityReadyAt, long lastFightAt, long visionUntil, boolean stalking, int flags, boolean absorbing,
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
		this.senses = (flags & 1) != 0;
		this.clawsOut = (flags & 2) != 0;
		this.unmasked = (flags & 4) != 0;
		this.necklaceOff = (flags & 8) != 0;
		this.habit = (flags & 16) != 0;
		this.v174 = (flags & 32) != 0;
		this.absorbing = absorbing;
		this.absorbed = absorbed;
		this.pulseStart = pulseStart;
		this.pulseRadius = pulseRadius;
		this.pulsePos = new ArrayList<>(pulsePos);
	}

	public BlackPantherState copy() {
		return new BlackPantherState(hasPower, suited, suitChangeAt, kinetic, animId, animStart, abilityReadyAt, lastFightAt, visionUntil,
				stalking, flags(), absorbing, absorbed, pulseStart, pulseRadius, pulsePos);
	}

	/** The four toggles packed in one int (the codec is at RecordCodecBuilder's 16-field limit). */
	public int flags() {
		return (senses ? 1 : 0) | (clawsOut ? 2 : 0) | (unmasked ? 4 : 0) | (necklaceOff ? 8 : 0) | (habit ? 16 : 0) | (v174 ? 32 : 0);
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
			Codec.INT.optionalFieldOf("flags", 0).forGetter(BlackPantherState::flags),
			Codec.BOOL.optionalFieldOf("absorbing", false).forGetter(s -> s.absorbing),
			Codec.FLOAT.optionalFieldOf("absorbed", 0f).forGetter(s -> s.absorbed),
			Codec.LONG.optionalFieldOf("pulse_start", 0L).forGetter(s -> s.pulseStart),
			Codec.FLOAT.optionalFieldOf("pulse_radius", 0f).forGetter(s -> s.pulseRadius),
			Codec.DOUBLE.listOf().optionalFieldOf("pulse_pos", List.of()).forGetter(s -> s.pulsePos)
	).apply(i, BlackPantherState::new));
}
