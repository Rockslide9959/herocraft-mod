package com.projecthero.mod.miraculous.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.6: one Miraculous holder ({@code projecthero:miraculous_state}): persistent, {@code copyOnDeath}, synced to every
 * client (others see the suit, the weapon, the poses; the owner's HUD needs the cooldowns and the countdown). Only
 * {@code Miraculous} and the kits change it, always through a {@link #copy()} (see {@code Miraculous.edit}).
 *
 * <p>The core fields are fixed; each kit keeps its own synced numbers in {@link #vals} (e.g. {@code "shield_until"}) and
 * positions in {@link #vecs} (e.g. {@code "yoyo_tip"} = x, y, z), so a kit never has to touch this codec.
 */
public final class MiraculousState {
	// shared animation ids (0..19); each kit numbers its own moves from 20 up (LadybugKit 20..39, CatNoirKit 40..59,
	// RenaRougeKit 60..79) so a client pose table can tell them apart
	public static final int ANIM_NONE = 0;
	public static final int ANIM_TRANSFORM = 1;
	public static final int ANIM_DETRANSFORM = 2;
	public static final int ANIM_FEED = 3;

	public boolean hasPower;
	/** {@code Kwami.id} (tikki / plagg / trixx); empty without the power. */
	public String kwami;
	/** Transformed (the suit is on, the Kwami is inside the jewel). */
	public boolean transformed;
	/** Game time the last transformation / detransformation started (the client plays the sequence from it). */
	public long formChangeAt;
	/** Shift+Z used: the forced detransformation lands at this game time (0 = no countdown running). */
	public long countdownEnd;
	/** The Kwami is worn out (the countdown ran out): no transforming until it has eaten its favourite food. */
	public boolean hungry;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** Kit-owned synced numbers (timers as absolute game times, toggles as 0/1, entity ids...). */
	public final Map<String, Long> vals;
	/** Kit-owned synced positions / vectors (x, y, z...). */
	public final Map<String, List<Double>> vecs;

	public MiraculousState() {
		this(false, "", false, 0L, 0L, false, 0, 0L, Map.of(), Map.of(), Map.of());
	}

	public MiraculousState(boolean hasPower, String kwami, boolean transformed, long formChangeAt, long countdownEnd, boolean hungry,
			int animId, long animStart, Map<String, Long> abilityReadyAt, Map<String, Long> vals, Map<String, List<Double>> vecs) {
		this.hasPower = hasPower;
		this.kwami = kwami;
		this.transformed = transformed;
		this.formChangeAt = formChangeAt;
		this.countdownEnd = countdownEnd;
		this.hungry = hungry;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.vals = new HashMap<>(vals);
		this.vecs = new HashMap<>();
		vecs.forEach((k, v) -> this.vecs.put(k, List.copyOf(v)));
	}

	public MiraculousState copy() {
		return new MiraculousState(hasPower, kwami, transformed, formChangeAt, countdownEnd, hungry, animId, animStart, abilityReadyAt, vals, vecs);
	}

	public long val(String key) {
		Long v = vals.get(key);
		return v == null ? 0L : v;
	}

	public List<Double> vec(String key) {
		List<Double> v = vecs.get(key);
		return v == null ? List.of() : v;
	}

	public static final Codec<MiraculousState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.STRING.optionalFieldOf("kwami", "").forGetter(s -> s.kwami),
			Codec.BOOL.optionalFieldOf("transformed", false).forGetter(s -> s.transformed),
			Codec.LONG.optionalFieldOf("form_change_at", 0L).forGetter(s -> s.formChangeAt),
			Codec.LONG.optionalFieldOf("countdown_end", 0L).forGetter(s -> s.countdownEnd),
			Codec.BOOL.optionalFieldOf("hungry", false).forGetter(s -> s.hungry),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of()).forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("vals", Map.of()).forGetter(s -> new HashMap<>(s.vals)),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE.listOf()).optionalFieldOf("vecs", Map.of()).forGetter(s -> new HashMap<>(s.vecs))
	).apply(i, MiraculousState::new));
}
