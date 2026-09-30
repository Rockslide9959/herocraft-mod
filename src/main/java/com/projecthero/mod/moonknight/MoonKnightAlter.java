package com.projecthero.mod.moonknight;

import net.minecraft.ChatFormatting;

/**
 * The three alters sharing Moon Knight's body. Stored by ordinal in {@code MoonKnightState}; their passives and
 * specials arrive in Phase 5, and each wears his own suit ({@link #suitTexture}, v0.13.21).
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
	 * v0.13.21: this alter's own suit texture (path in the mod's namespace), from the user's per-alter Blockbench
	 * models -- Marc's white-and-gold armour, Steven's Mr. Knight suit, Jake's dark suit. All three share
	 * {@code geo/moon_knight.geo.json}.
	 */
	public String suitTexture() {
		return "textures/armor/moon_knight_" + id + ".png";
	}

	/**
	 * v0.14.4: Steven Grant's Mr. Knight suit has no cape -- no cape drawn, and so no Cape Glide or Cape Block while
	 * he is in control.
	 */
	public boolean hasCape() {
		return this != STEVEN;
	}

	/** v0.13.21: this alter's cape texture (Marc keeps the original off-white one). */
	public String capeTexture() {
		return this == MARC ? "textures/entity/moon_knight_cape.png" : "textures/entity/moon_knight_cape_" + id + ".png";
	}

	public static MoonKnightAlter byOrdinal(int i) {
		MoonKnightAlter[] v = values();
		return v[Math.floorMod(i, v.length)];
	}
}
