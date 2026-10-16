package com.projecthero.mod.bluebeetle.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.5: the whole Blue Beetle power for one player in one attachment ({@code projecthero:blue_beetle_state}):
 * persistent, {@code copyOnDeath} and synced to every client. The short-lived visuals (blade, shield, blaster, beam,
 * bubble, tentacles, spikes) sit in {@link Fx} so the codec stays inside the 16-field limit. Only {@code BlueBeetle} and
 * its move classes change it, always through a {@link #copy()}.
 */
public final class BlueBeetleState {
	public static final int ANIM_NONE = 0;
	public static final int ANIM_BOND = 1;
	public static final int ANIM_SUIT_UP = 2;
	public static final int ANIM_SUIT_DOWN = 3;
	public static final int ANIM_BLAST = 4;
	public static final int ANIM_CANNON = 5;
	/** C: the energy blade forms (or folds away). */
	public static final int ANIM_BLADE = 6;
	/** G: Scarab Hammer. */
	public static final int ANIM_HAMMER = 7;
	/** Z: Plasma Spikes (the fist slam). */
	public static final int ANIM_SPIKES = 8;
	/** Shift+G: Scarab Grab (the tentacles). */
	public static final int ANIM_GRAB = 9;
	public static final int ANIM_PULSE = 11;
	public static final int ANIM_SWARM = 12;
	public static final int ANIM_BUBBLE = 13;
	public static final int ANIM_CAMO = 14;
	public static final int ANIM_UNLEASH = 15;

	/** The short-lived, purely visual state. */
	public static final class Fx {
		/** C: the energy blade is out (a toggle). */
		public boolean blade;
		/** V: the Energy Shield is held up. */
		public boolean shield;
		/** R is held: the Plasma Blaster is built on the arm. */
		public boolean blasting;
		/** The Mega Cannon is charging since this game time (0 = not). */
		public long chargeStart;
		/** The cannon beam shows until this game time. */
		public long beamUntil;
		/** Where the beam ended: x, y, z. */
		public List<Double> beamEnd;
		/** The entity in the Bubble Prison (-1 = none), and until when. */
		public int bubbleTarget;
		public long bubbleUntil;
		/** Scarab Grab: the entity the two tentacles strike (-1 = none) and when they shot out. */
		public int grabTarget;
		public long grabStart;
		/** Plasma Spikes: when the fist hit the ground (0 = none). */
		public long spikeStart;
		/** Plasma Spikes: x, z of the line's start, its direction dx, dz, then the ground y under every spike. */
		public List<Double> spikeLine;
		/** Khaji-Da's takeover: the entity it is fighting (-1 = none), so every client turns the body at it. */
		public int takeTarget;
		/** V: what is left of the held shield's 100-point pool. */
		public float shieldHp;

		public Fx() {
			this(false, false, false, 0L, 0L, List.of(), -1, 0L, -1, 0L, 0L, List.of(), -1, 0f);
		}

		public Fx(boolean blade, boolean shield, boolean blasting, long chargeStart, long beamUntil, List<Double> beamEnd, int bubbleTarget,
				long bubbleUntil, int grabTarget, long grabStart, long spikeStart, List<Double> spikeLine, int takeTarget,
				float shieldHp) {
			this.blade = blade;
			this.shield = shield;
			this.blasting = blasting;
			this.chargeStart = chargeStart;
			this.beamUntil = beamUntil;
			this.beamEnd = List.copyOf(beamEnd);
			this.bubbleTarget = bubbleTarget;
			this.bubbleUntil = bubbleUntil;
			this.grabTarget = grabTarget;
			this.grabStart = grabStart;
			this.spikeStart = spikeStart;
			this.spikeLine = List.copyOf(spikeLine);
			this.takeTarget = takeTarget;
			this.shieldHp = shieldHp;
		}

		public Fx copy() {
			return new Fx(blade, shield, blasting, chargeStart, beamUntil, beamEnd, bubbleTarget, bubbleUntil, grabTarget, grabStart, spikeStart,
					spikeLine, takeTarget, shieldHp);
		}

		public static final Codec<Fx> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("blade", false).forGetter(f -> f.blade),
				Codec.BOOL.optionalFieldOf("shield", false).forGetter(f -> f.shield),
				Codec.BOOL.optionalFieldOf("blasting", false).forGetter(f -> f.blasting),
				Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(f -> f.chargeStart),
				Codec.LONG.optionalFieldOf("beam_until", 0L).forGetter(f -> f.beamUntil),
				Codec.DOUBLE.listOf().optionalFieldOf("beam_end", List.of()).forGetter(f -> f.beamEnd),
				Codec.INT.optionalFieldOf("bubble_target", -1).forGetter(f -> f.bubbleTarget),
				Codec.LONG.optionalFieldOf("bubble_until", 0L).forGetter(f -> f.bubbleUntil),
				Codec.INT.optionalFieldOf("grab_target", -1).forGetter(f -> f.grabTarget),
				Codec.LONG.optionalFieldOf("grab_start", 0L).forGetter(f -> f.grabStart),
				Codec.LONG.optionalFieldOf("spike_start", 0L).forGetter(f -> f.spikeStart),
				Codec.DOUBLE.listOf().optionalFieldOf("spike_line", List.of()).forGetter(f -> f.spikeLine),
				Codec.INT.optionalFieldOf("take_target", -1).forGetter(f -> f.takeTarget),
				Codec.FLOAT.optionalFieldOf("shield_hp", 0f).forGetter(f -> f.shieldHp)
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
	/** Flying (double-tap jump; flight lent). */
	public boolean flying;
	/** The damage type the armour has adapted to ("" = none) and until when. */
	public String adaptType;
	public long adaptUntil;
	/** Khaji-Da Unleashed runs until this game time. */
	public long unleashedUntil;
	/** After the Unleashed the bar refills at half speed until this game time. */
	public long slowRegenUntil;
	/** Camouflage is on (no timer: it lasts until he touches, hits or uses anything, or flies). */
	public boolean camo;
	public Fx fx;

	public BlueBeetleState() {
		this(false, false, 0L, 0f, 0, 0L, new HashMap<>(), false, "", 0L, 0L, 0L, false, new Fx());
	}

	public BlueBeetleState(boolean hasPower, boolean suited, long suitChangeAt, float energy, int animId, long animStart,
			Map<String, Long> abilityReadyAt, boolean flying, String adaptType, long adaptUntil, long unleashedUntil, long slowRegenUntil,
			boolean camo, Fx fx) {
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
		this.camo = camo;
		this.fx = fx.copy();
	}

	public BlueBeetleState copy() {
		return new BlueBeetleState(hasPower, suited, suitChangeAt, energy, animId, animStart, abilityReadyAt, flying, adaptType, adaptUntil,
				unleashedUntil, slowRegenUntil, camo, fx);
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
			Codec.BOOL.optionalFieldOf("camo", false).forGetter(s -> s.camo),
			Fx.CODEC.optionalFieldOf("fx", new Fx()).forGetter(s -> s.fx)
	).apply(i, BlueBeetleState::new));
}
