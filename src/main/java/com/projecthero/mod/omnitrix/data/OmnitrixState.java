package com.projecthero.mod.omnitrix.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1: the whole Omnitrix for one player in one attachment ({@code projecthero:omnitrix_state}): persistent, kept
 * through death (the watch never comes off by dying) and synced to every client (others see the alien, its poses and the
 * forced-transform flicker during an extraction; the owner's HUD needs the timer, the recharge and the cooldowns). Only
 * {@code Omnitrix} and the alien kits change it, always through a {@link #copy()}.
 */
public final class OmnitrixState {
	public boolean hasPower;
	/** The alien picked on the dial (an {@code Aliens} id). */
	public String selected = "heatblast";
	/** The alien the wearer is right now ("" = human). */
	public String active = "";
	/** Game time the current transformation started / ends (the 3-minute timeout). */
	public long transformedAt;
	public long endsAt;
	/** The watch recharges until this game time (V does nothing before it). */
	public long rechargeUntil;
	/** The pose playing (kit-defined ids; 0 = none) and the game time it started. */
	public int animId;
	public long animStart;
	/** Kit-defined synced bits (Heatblast's jets / shield, Four Arms' stance...). */
	public int kitFlags;
	/** The human body's health fraction when the transformation started (0..1). */
	public float humanHealth = 1f;
	/** The Containment Pod's forced transform: the alien shown ("" = none) until {@link #flickerUntil}. */
	public String flicker = "";
	public long flickerUntil;
	/** An entity the alien holds (Four Arms' grab), 0 = none. */
	public int heldEntity;
	/** The watch is up for picking an alien (V while human): the core has popped up, the face shows the silhouette. */
	public boolean selecting;
	/** Game time the watch went up (or came down) -- the raise / lower animation runs off it. */
	public long selectStart;
	/** {@code "<alien>.<move>"} -> the game time the move is ready again; also kit timers. */
	public final Map<String, Long> timers;

	public OmnitrixState() {
		this.timers = new HashMap<>();
	}

	private OmnitrixState(Map<String, Long> timers) {
		this.timers = new HashMap<>(timers);
	}

	public boolean transformed() {
		return !active.isEmpty();
	}

	public long readyAt(String key) {
		Long v = timers.get(key);
		return v == null ? 0L : v;
	}

	public OmnitrixState copy() {
		OmnitrixState n = new OmnitrixState(timers);
		n.hasPower = hasPower;
		n.selected = selected;
		n.active = active;
		n.transformedAt = transformedAt;
		n.endsAt = endsAt;
		n.rechargeUntil = rechargeUntil;
		n.animId = animId;
		n.animStart = animStart;
		n.kitFlags = kitFlags;
		n.humanHealth = humanHealth;
		n.flicker = flicker;
		n.flickerUntil = flickerUntil;
		n.heldEntity = heldEntity;
		n.selecting = selecting;
		n.selectStart = selectStart;
		return n;
	}

	/**
	 * v0.17.1: aliens taken off the dial, and who took their slot -- a saved {@code selected} / {@code active} / {@code flicker} of
	 * one loads as its replacement (Wildvine was never one of the original ten; Wildmutt was). The removed alien's kit timers
	 * are dropped.
	 */
	public static final java.util.Map<String, String> RENAMED = java.util.Map.of("wildvine", "wildmutt");

	public static String renamed(String id) {
		return id == null ? "" : RENAMED.getOrDefault(id, id);
	}

	public static final Codec<OmnitrixState> CODEC =RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.STRING.optionalFieldOf("selected", "heatblast").forGetter(s -> s.selected),
			Codec.STRING.optionalFieldOf("active", "").forGetter(s -> s.active),
			Codec.LONG.optionalFieldOf("transformed_at", 0L).forGetter(s -> s.transformedAt),
			Codec.LONG.optionalFieldOf("ends_at", 0L).forGetter(s -> s.endsAt),
			Codec.LONG.optionalFieldOf("recharge_until", 0L).forGetter(s -> s.rechargeUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.INT.optionalFieldOf("kit_flags", 0).forGetter(s -> s.kitFlags),
			Codec.FLOAT.optionalFieldOf("human_health", 1f).forGetter(s -> s.humanHealth),
			Codec.STRING.optionalFieldOf("flicker", "").forGetter(s -> s.flicker),
			Codec.LONG.optionalFieldOf("flicker_until", 0L).forGetter(s -> s.flickerUntil),
			Codec.INT.optionalFieldOf("held_entity", 0).forGetter(s -> s.heldEntity),
			Codec.BOOL.optionalFieldOf("selecting", false).forGetter(s -> s.selecting),
			Codec.LONG.optionalFieldOf("select_start", 0L).forGetter(s -> s.selectStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("timers", Map.of()).forGetter(s -> new HashMap<>(s.timers))
	).apply(i, (hp, sel, act, ta, ea, ru, an, as, kf, hh, fl, fu, he, sg, ss, tm) -> {
		OmnitrixState s = new OmnitrixState(tm);
		s.hasPower = hp;
		s.selected = renamed(sel);
		s.active = renamed(act);
		if (!s.active.equals(act)) {
			// v0.17.1: a save made as a removed alien -- its kit's bits and pose meant nothing to the new one
			kf = 0;
			an = 0;
		}
		s.timers.keySet().removeIf(k -> RENAMED.containsKey(k.substring(0, Math.max(0, k.indexOf('.')))));
		fl = renamed(fl);
		s.transformedAt = ta;
		s.endsAt = ea;
		s.rechargeUntil = ru;
		s.animId = an;
		s.animStart = as;
		s.kitFlags = kf;
		s.humanHealth = hh;
		s.flicker = fl;
		s.flickerUntil = fu;
		s.heldEntity = he;
		s.selecting = sg;
		s.selectStart = ss;
		return s;
	}));
}
