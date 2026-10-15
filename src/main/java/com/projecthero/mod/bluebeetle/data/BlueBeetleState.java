package com.projecthero.mod.bluebeetle.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.5: the whole Blue Beetle power for one player in one attachment ({@code projecthero:blue_beetle_state}):
 * persistent, {@code copyOnDeath} and synced to every client. The short-lived visuals (blade, shield, beam, bubble,
 * charge) sit in {@link Fx} so the codec stays inside the 16-field limit. Only {@code BlueBeetle} and its move classes
 * change it, always through a {@link #copy()}.
 */
public final class BlueBeetleState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_BOND = 1;
	public static final int ANIM_SUIT_UP = 2;
	public static final int ANIM_SUIT_DOWN = 3;
	public static final int ANIM_BLAST = 4;
	public static final int ANIM_CANNON = 5;
	public static final int ANIM_BLADE_1 = 6;
	public static final int ANIM_BLADE_2 = 7;
	public static final int ANIM_BLADE_3 = 8;
	public static final int ANIM_GRIP = 9;
	public static final int ANIM_AFTERBURNER = 10;
	public static final int ANIM_PULSE = 11;
	public static final int ANIM_SWARM = 12;
	public static final int ANIM_BUBBLE = 13;
	public static final int ANIM_CAMO = 14;
	public static final int ANIM_UNLEASH = 15;

	/** The short-lived, purely visual state. */
	public static final class Fx {
		/** The energy blade is out until this game time. */
		public long bladeUntil;
		/** The Energy Shield is held up. */
		public boolean shield;
		/** The Mega Cannon is charging since this game time (0 = not). */
		public long chargeStart;
		/** The cannon beam shows until this game time. */
		public long beamUntil;
		/** Where the beam ended: x, y, z. */
		public List<Double> beamEnd;
		/** The entity in the Bubble Prison (-1 = none), and until when. */
		public int bubbleTarget;
		public long bubbleUntil;
		/** The entity held in the Scarab Grip (-1 = none). */
		public int gripTarget;

		public Fx() {
			this(0L, false, 0L, 0L, List.of(), -1, 0L, -1);
		}

		public Fx(long bladeUntil, boolean shield, long chargeStart, long beamUntil, List<Double> beamEnd, int bubbleTarget, long bubbleUntil,
				int gripTarget) {
			this.bladeUntil = bladeUntil;
			this.shield = shield;
			this.chargeStart = chargeStart;
			this.beamUntil = beamUntil;
			this.beamEnd = List.copyOf(beamEnd);
			this.bubbleTarget = bubbleTarget;
			this.bubbleUntil = bubbleUntil;
			this.gripTarget = gripTarget;
		}

		public Fx copy() {
			return new Fx(bladeUntil, shield, chargeStart, beamUntil, beamEnd, bubbleTarget, bubbleUntil, gripTarget);
		}

		public static final Codec<Fx> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("blade_until", 0L).forGetter(f -> f.bladeUntil),
				Codec.BOOL.optionalFieldOf("shield", false).forGetter(f -> f.shield),
				Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(f -> f.chargeStart),
				Codec.LONG.optionalFieldOf("beam_until", 0L).forGetter(f -> f.beamUntil),
				Codec.DOUBLE.listOf().optionalFieldOf("beam_end", List.of()).forGetter(f -> f.beamEnd),
				Codec.INT.optionalFieldOf("bubble_target", -1).forGetter(f -> f.bubbleTarget),
				Codec.LONG.optionalFieldOf("bubble_until", 0L).forGetter(f -> f.bubbleUntil),
				Codec.INT.optionalFieldOf("grip_target", -1).forGetter(f -> f.gripTarget)
		).apply(i, Fx::new));
	}

	public boolean hasPower;
	/** The armour is on (H). */
	public boolean suited;
	/** Game time the last suit-up / suit-down (or the bonding) started. */
	public long suitChangeAt;
	/** 0..{@code BlueBeetleConfig.ENERGY_MAX}. */
	public float energy;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** The wings are out (flight lent). */
	public boolean flying;
	/** The damage type the armour has adapted to ("" = none) and until when. */
	public String adaptType;
	public long adaptUntil;
	/** Khaji-Da Unleashed runs until this game time. */
	public long unleashedUntil;
	/** After the Unleashed the bar refills at half speed until this game time. */
	public long slowRegenUntil;
	/** Energy Blade combo: how many swings so far (0..3). */
	public int bladeCombo;
	/** Camouflage runs until this game time. */
	public long camoUntil;
	public Fx fx;

	public BlueBeetleState() {
		this(false, false, 0L, 0f, 0, 0L, new HashMap<>(), false, "", 0L, 0L, 0L, 0, 0L, new Fx());
	}

	public BlueBeetleState(boolean hasPower, boolean suited, long suitChangeAt, float energy, int animId, long animStart,
			Map<String, Long> abilityReadyAt, boolean flying, String adaptType, long adaptUntil, long unleashedUntil, long slowRegenUntil,
			int bladeCombo, long camoUntil, Fx fx) {
		this.hasPower = hasPower;
		this.suited = suited;
		this.suitChangeAt = suitChangeAt;
		this.energy = energy;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.flying = flying;
		this.adaptType = adaptType;
		this.adaptUntil = adaptUntil;
		this.unleashedUntil = unleashedUntil;
		this.slowRegenUntil = slowRegenUntil;
		this.bladeCombo = bladeCombo;
		this.camoUntil = camoUntil;
		this.fx = fx.copy();
	}

	public BlueBeetleState copy() {
		return new BlueBeetleState(hasPower, suited, suitChangeAt, energy, animId, animStart, abilityReadyAt, flying, adaptType, adaptUntil,
				unleashedUntil, slowRegenUntil, bladeCombo, camoUntil, fx);
	}

	public static final Codec<BlueBeetleState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("suited", false).forGetter(s -> s.suited),
			Codec.LONG.optionalFieldOf("suit_change_at", 0L).forGetter(s -> s.suitChangeAt),
			Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(s -> s.energy),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(s -> s.flying),
			Codec.STRING.optionalFieldOf("adapt_type", "").forGetter(s -> s.adaptType),
			Codec.LONG.optionalFieldOf("adapt_until", 0L).forGetter(s -> s.adaptUntil),
			Codec.LONG.optionalFieldOf("unleashed_until", 0L).forGetter(s -> s.unleashedUntil),
			Codec.LONG.optionalFieldOf("slow_regen_until", 0L).forGetter(s -> s.slowRegenUntil),
			Codec.INT.optionalFieldOf("blade_combo", 0).forGetter(s -> s.bladeCombo),
			Codec.LONG.optionalFieldOf("camo_until", 0L).forGetter(s -> s.camoUntil),
			Fx.CODEC.optionalFieldOf("fx", new Fx()).forGetter(s -> s.fx)
	).apply(i, BlueBeetleState::new));
}
