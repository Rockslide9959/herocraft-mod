package com.projecthero.mod.quirk.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.7: one Quirk holder ({@code projecthero:quirk_state}): persistent, {@code copyOnDeath}, synced to every client
 * (others see the costume, the poses and the kit's visible effects; the owner's HUD needs the cooldowns and the meter).
 * Only {@code Quirks} and the kits change it, always through a {@link #copy()} (see {@code Quirks.edit}).
 *
 * <p>The core fields are fixed; each kit keeps its own synced numbers in {@link #vals} and positions in {@link #vecs},
 * so a kit never has to touch this codec. {@link #meter} is the synced copy of the server's meter (flushed a few times
 * a second, see {@code Quirks.meter}).
 */
public final class QuirkState {
	public static final int ANIM_NONE = 0;

	public boolean hasPower;
	/** {@code Quirk.id}; empty without the power. */
	public String quirk;
	/** v0.16.7: the hero costume is drawn over the player's skin (default on; {@code /projecthero quirk costume}). */
	public boolean costume;
	/** The kit's meter, 0..100 (Heat, Engine, Sweat...). */
	public float meter;
	/** Overloaded (overheat / stall / arm strain...) until this game time; 0 = not. */
	public long overloadUntil;
	public int animId;
	public long animStart;
	/** {@code abilityId} -> absolute game time it is ready again. */
	public final Map<String, Long> abilityReadyAt;
	/** Kit-owned synced numbers (timers as absolute game times, toggles as 0/1, entity ids...). */
	public final Map<String, Long> vals;
	/** Kit-owned synced positions / vectors. */
	public final Map<String, List<Double>> vecs;

	public QuirkState() {
		this(false, "", true, 0f, 0L, 0, 0L, Map.of(), Map.of(), Map.of());
	}

	public QuirkState(boolean hasPower, String quirk, boolean costume, float meter, long overloadUntil, int animId, long animStart,
			Map<String, Long> abilityReadyAt, Map<String, Long> vals, Map<String, List<Double>> vecs) {
		this.hasPower = hasPower;
		this.quirk = quirk;
		this.costume = costume;
		this.meter = meter;
		this.overloadUntil = overloadUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.vals = new HashMap<>(vals);
		this.vecs = new HashMap<>();
		vecs.forEach((k, v) -> this.vecs.put(k, List.copyOf(v)));
	}

	public QuirkState copy() {
		return new QuirkState(hasPower, quirk, costume, meter, overloadUntil, animId, animStart, abilityReadyAt, vals, vecs);
	}

	public long val(String key) {
		Long v = vals.get(key);
		return v == null ? 0L : v;
	}

	public List<Double> vec(String key) {
		List<Double> v = vecs.get(key);
		return v == null ? List.of() : v;
	}

	public static final Codec<QuirkState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.STRING.optionalFieldOf("quirk", "").forGetter(s -> s.quirk),
			Codec.BOOL.optionalFieldOf("costume", true).forGetter(s -> s.costume),
			Codec.FLOAT.optionalFieldOf("meter", 0f).forGetter(s -> s.meter),
			Codec.LONG.optionalFieldOf("overload_until", 0L).forGetter(s -> s.overloadUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of()).forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("vals", Map.of()).forGetter(s -> new HashMap<>(s.vals)),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE.listOf()).optionalFieldOf("vecs", Map.of()).forGetter(s -> new HashMap<>(s.vecs))
	).apply(i, QuirkState::new));
}
