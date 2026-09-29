package com.projecthero.mod.moonknight;

import net.minecraft.ChatFormatting;

/**
 * The three alters sharing Moon Knight's body. Stored by ordinal in {@code MoonKnightState}; their passives and
 * specials arrive in Phase 5, their suit textures hook in through {@link #suitTexture} (Phase 2).
 */
public enum MoonKnightAlter {
	MARC("marc", ChatFormatting.WHITE),
	STEVEN("steven", ChatFormatting.GOLD),
	JAKE("jake", ChatFormatting.DARK_GRAY);

	private final String id;
	private final ChatFormatting colour;

	MoonKnightAlter(String id, ChatFormatting colour) {
		this.id = id;
		this.colour = colour;
	}

	public String id() {
		return id;
	}

	public ChatFormatting colour() {
		return colour;
	}

	/** Lang key of the alter's full name. */
	public String nameKey() {
		return "projecthero.moon_knight.alter." + id;
	}

	/** Marc -> Steven -> Jake -> Marc. */
	public MoonKnightAlter next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/**
	 * The suit texture for this alter, if the user's files include one (Phase 2 hook): {@code null} means use the
	 * shared Moon Knight texture for every alter.
	 */
	public String suitTexture() {
		return null;
	}

	public static MoonKnightAlter byOrdinal(int i) {
		MoonKnightAlter[] v = values();
		return v[Math.floorMod(i, v.length)];
	}
}
