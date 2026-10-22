package com.projecthero.mod.quirk;

import org.jetbrains.annotations.Nullable;

/**
 * v0.16.7: the Quirks (My Hero Academia). Each is a Primary power of its own (one Primary at a time); they share
 * one core ({@link Quirks}), and each has its own 12-move kit ({@link QuirkKit}) in {@code quirk/<id>/**}.
 *
 * <p>{@code heroKey} is the HeroTiers / command / guide id. {@code animBase}: the kit numbers its pose ids from here
 * (20 per quirk) so one client pose table can tell them apart. {@code meterColour}: the HUD meter's colour (ARGB).
 *
 * <p><b>One For All</b> (v0.16.7, user: "add it to the quirk framework") is the eighth Quirk. It is registered, granted, listed,
 * shown on the Quirk HUD and in the guidebook like the others, but it keeps its own engine and save data
 * ({@code allmight.AllMight} / {@code AllMightState}: the forms, Output, the safe limit and limb strain), so it is
 * {@link #standalone}: its hero key stays {@code "all_might"} (old saves, commands, serums and the guidebook keep working),
 * holding it is read from the All Might state rather than {@code QuirkState}, and the generic per-Quirk extras (suit,
 * generated guide chapter) are its own All Might ones instead.
 */
public enum Quirk {
	HELLFLAME("hellflame", "Hellflame", "Endeavor", "heat", 0xFFFF6A1A, 20),
	OVERHAUL("overhaul", "Overhaul", "Overhaul", "focus", 0xFF8FD3C7, 40),
	ENGINE("engine", "Engine", "Ingenium", "engine", 0xFF5AA9FF, 60),
	DARK_SHADOW("dark_shadow", "Dark Shadow", "Tsukuyomi", "shadow", 0xFF7A3CFF, 80),
	ERASURE("erasure", "Erasure", "Eraser Head", "dry_eyes", 0xFFE04040, 100),
	EXPLOSION("explosion", "Explosion", "Ground Zero", "sweat", 0xFFFFB020, 120),
	ZERO_GRAVITY("zero_gravity", "Zero Gravity", "Uravity", "nausea", 0xFFFF8FC8, 140),
	/** v0.16.7: One For All -- standalone (its own All Might engine and state), hero key {@code all_might}. Keep it LAST. */
	ONE_FOR_ALL("one_for_all", "One For All", "One For All", "output", 0xFFFFC83A, 160, "all_might");

	/** State / lang / texture id: {@code projecthero.quirk.<id>}, {@code textures/entity/quirk/<id>_wide.png}. */
	public final String id;
	/** English names (lang has the real text; these are for logs, commands and tests). */
	public final String quirkName;
	public final String heroName;
	/** The meter's lang suffix: {@code projecthero.quirk.meter.<meterId>}. */
	public final String meterId;
	public final int meterColour;
	public final int animBase;
	/** @see #heroKey() */
	private final String heroKey;
	/** Runs its own engine and save data (One For All): see the class comment. */
	public final boolean standalone;

	Quirk(String id, String quirkName, String heroName, String meterId, int meterColour, int animBase) {
		this(id, quirkName, heroName, meterId, meterColour, animBase, null);
	}

	Quirk(String id, String quirkName, String heroName, String meterId, int meterColour, int animBase, @Nullable String ownHeroKey) {
		this.id = id;
		this.quirkName = quirkName;
		this.heroName = heroName;
		this.meterId = meterId;
		this.meterColour = meterColour;
		this.animBase = animBase;
		this.standalone = ownHeroKey != null;
		this.heroKey = ownHeroKey != null ? ownHeroKey : "quirk_" + id;
	}

	/** The HeroTiers key ({@code "quirk_hellflame"}...: keeps clear of any existing hero id; One For All keeps {@code "all_might"}). */
	public String heroKey() {
		return heroKey;
	}

	/** The seven Quirks that run on the shared {@code QuirkState} (everything but One For All), in order. */
	public static Quirk[] kitQuirks() {
		return java.util.Arrays.stream(values()).filter(q -> !q.standalone).toArray(Quirk[]::new);
	}

	public static @Nullable Quirk byId(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (Quirk q : values()) {
			if (q.id.equals(id) || q.heroKey().equals(id)) {
				return q;
			}
		}
		return null;
	}

	public static @Nullable Quirk byHeroKey(@Nullable String key) {
		return byId(key);
	}
}
