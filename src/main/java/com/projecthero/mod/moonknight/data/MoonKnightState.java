package com.projecthero.mod.moonknight.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Moon Knight's player data ({@code projecthero:moon_knight_state}): persistent, copied on death, synced to every
 * client (the HUD reads it for its owner; other players need {@link #transformed} / {@link #alter} to draw the suit
 * and cape). Mutable with copy-on-write, like every other hero state here: callers {@link #copy()}, change the copy,
 * and save it so the sync fires.
 *
 * <p>Game-time stamps ({@link #lastHostileKill}, {@link #fractureUntil}, {@link #abilityReadyAt}) are absolute
 * {@code level.getGameTime()} values. Moon-cycle numbers ({@link #resurrectionCycle}) are
 * {@code dayTime / MOON_CYCLE_TICKS} -- see {@code MoonKnightLunar#moonCycle}.
 */
public final class MoonKnightState {
	public boolean hasPact;
	public boolean transformed;
	/** {@code MoonKnightAlter} ordinal. */
	public int alter;
	public float vengeance;
	public boolean resurrectionCharged;
	/** The moon cycle the resurrection was last spent in; it recharges at the start of a later full moon night. */
	public long resurrectionCycle;
	/** The armour the player was wearing when they suited up -- handed back when they un-transform (Phase 2). */
	public ItemContainerContents storedArmor;
	/** Game time of the last hostile mob kill (Vengeance drains after two in-game days without one). */
	public long lastHostileKill;
	/** Per-ability cooldowns: ability id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt;
	/** While {@code > now}, a Fracture has forced {@link #alter}; it snaps back to {@link #fractureReturnAlter} after. */
	public long fractureUntil;
	public int fractureReturnAlter;
	/** Game time of the last Fracture -- they are kept rare. */
	public long lastFracture;
	/** Cape Glide active (Phase 3) -- synced for the HUD indicator and the spread-cape render. */
	public boolean gliding;
	/** Current keyframed move pose and when it started (later phases). */
	public int animId;
	public long animStart;
	/** Game time the current suit-up / suit-down started (Phase 2). */
	public long transformStart;

	public MoonKnightState() {
		this(false, false, 0, 0.0f, false, -1L, ItemContainerContents.EMPTY, 0L, new HashMap<>(), 0L, 0, -100000L, false,
				0, 0L, -100000L);
	}

	public MoonKnightState(boolean hasPact, boolean transformed, int alter, float vengeance, boolean resurrectionCharged,
			long resurrectionCycle, ItemContainerContents storedArmor, long lastHostileKill, Map<String, Long> abilityReadyAt,
			long fractureUntil, int fractureReturnAlter, long lastFracture, boolean gliding, int animId, long animStart,
			long transformStart) {
		this.hasPact = hasPact;
		this.transformed = transformed;
		this.alter = alter;
		this.vengeance = vengeance;
		this.resurrectionCharged = resurrectionCharged;
		this.resurrectionCycle = resurrectionCycle;
		this.storedArmor = storedArmor == null ? ItemContainerContents.EMPTY : storedArmor;
		this.lastHostileKill = lastHostileKill;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt == null ? Map.of() : abilityReadyAt);
		this.fractureUntil = fractureUntil;
		this.fractureReturnAlter = fractureReturnAlter;
		this.lastFracture = lastFracture;
		this.gliding = gliding;
		this.animId = animId;
		this.animStart = animStart;
		this.transformStart = transformStart;
	}

	public MoonKnightState copy() {
		return new MoonKnightState(hasPact, transformed, alter, vengeance, resurrectionCharged, resurrectionCycle,
				storedArmor, lastHostileKill, abilityReadyAt, fractureUntil, fractureReturnAlter, lastFracture, gliding,
				animId, animStart, transformStart);
	}

	public static final Codec<MoonKnightState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_pact", false).forGetter(s -> s.hasPact),
			Codec.BOOL.optionalFieldOf("transformed", false).forGetter(s -> s.transformed),
			Codec.INT.optionalFieldOf("alter", 0).forGetter(s -> s.alter),
			Codec.FLOAT.optionalFieldOf("vengeance", 0.0f).forGetter(s -> s.vengeance),
			Codec.BOOL.optionalFieldOf("resurrection_charged", false).forGetter(s -> s.resurrectionCharged),
			Codec.LONG.optionalFieldOf("resurrection_cycle", -1L).forGetter(s -> s.resurrectionCycle),
			ItemContainerContents.CODEC.optionalFieldOf("stored_armor", ItemContainerContents.EMPTY).forGetter(s -> s.storedArmor),
			Codec.LONG.optionalFieldOf("last_hostile_kill", 0L).forGetter(s -> s.lastHostileKill),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("fracture_until", 0L).forGetter(s -> s.fractureUntil),
			Codec.INT.optionalFieldOf("fracture_return_alter", 0).forGetter(s -> s.fractureReturnAlter),
			Codec.LONG.optionalFieldOf("last_fracture", -100000L).forGetter(s -> s.lastFracture),
			Codec.BOOL.optionalFieldOf("gliding", false).forGetter(s -> s.gliding),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.LONG.optionalFieldOf("transform_start", -100000L).forGetter(s -> s.transformStart)
	).apply(i, MoonKnightState::new));
}
