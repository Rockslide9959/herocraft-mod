package com.projecthero.mod.quirk;

import org.jetbrains.annotations.Nullable;

/**
 * v0.16.7: the seven Quirks (My Hero Academia). Each is a Primary power of its own (one Primary at a time); they share
 * one state ({@code QuirkState}) and core ({@link Quirks}), and each has its own 12-move kit ({@link QuirkKit}) in
 * {@code quirk/<id>/**}.
 *
 * <p>{@code heroKey} is the HeroTiers / command / guide id. {@code animBase}: the kit numbers its pose ids from here
 * (20 per quirk) so one client pose table can tell them apart. {@code meterColour}: the HUD meter's colour (ARGB).
 */
public enum Quirk {
	HELLFLAME("hellflame", "Hellflame", "Endeavor", "heat", 0xFFFF6A1A, 20),
	OVERHAUL("overhaul", "Overhaul", "Overhaul", "focus", 0xFF8FD3C7, 40),
	ENGINE("engine", "Engine", "Ingenium", "engine", 0xFF5AA9FF, 60),
	DARK_SHADOW("dark_shadow", "Dark Shadow", "Tsukuyomi", "shadow", 0xFF7A3CFF, 80),
	ERASURE("erasure", "Erasure", "Eraser Head", "dry_eyes", 0xFFE04040, 100),
	EXPLOSION("explosion", "Explosion", "Ground Zero", "sweat", 0xFFFFB020, 120),
	ZERO_GRAVITY("zero_gravity", "Zero Gravity", "Uravity", "nausea", 0xFFFF8FC8, 140);

	/** State / lang / texture id: {@code projecthero.quirk.<id>}, {@code textures/entity/quirk/<id>_wide.png}. */
	public final String id;
	/** English names (lang has the real text; these are for logs, commands and tests). */
	public final String quirkName;
	public final String heroName;
	/** The meter's lang suffix: {@code projecthero.quirk.meter.<meterId>}. */
	public final String meterId;
	public final int meterColour;
	public final int animBase;

	Quirk(String id, String quirkName, String heroName, String meterId, int meterColour, int animBase) {
		this.id = id;
		this.quirkName = quirkName;
		this.heroName = heroName;
		this.meterId = meterId;
		this.meterColour = meterColour;
		this.animBase = animBase;
	}

	/** The HeroTiers key ({@code "quirk_hellflame"}...): keeps clear of any existing hero id. */
	public String heroKey() {
		return "quirk_" + id;
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
