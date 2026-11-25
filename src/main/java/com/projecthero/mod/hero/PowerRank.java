package com.projecthero.mod.hero;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * v0.17.7 (user): the four balance tiers every power is tuned to. Damage is in health points (2 = one heart).
 *
 * <ul>
 *   <li><b>Street</b> -- melee 7-10, abilities 8-10, ultimate 15-20</li>
 *   <li><b>City</b> -- melee 11-14, abilities 11-15, ultimate 25-30</li>
 *   <li><b>Planet</b> -- melee 16-19, abilities 16-20, ultimate 32-40</li>
 *   <li><b>Cosmic</b> -- melee 21-28, abilities 22-30, ultimate 45-60</li>
 * </ul>
 *
 * <p>A power's place is decided by the user ({@code docs/POWER_TIERS_REFERENCE.md}); only placed powers get a
 * badge. Not to be confused with {@link PowerTier} (the old Experimental / Hero-Tier acquisition split).
 */
public enum PowerRank {
	STREET("street", 0xE07A4F, 7, 10, 8, 10, 15, 20), // brick
	CITY("city", 0x5AAEFF, 11, 14, 11, 15, 25, 30), // skyline blue
	PLANET("planet", 0x4FD08A, 16, 19, 16, 20, 32, 40), // earth green
	COSMIC("cosmic", 0xC07CFF, 21, 28, 22, 30, 45, 60); // nebula purple

	public final String id;
	public final int colour;
	public final float meleeMin, meleeMax, abilityMin, abilityMax, ultimateMin, ultimateMax;

	PowerRank(String id, int colour, float meleeMin, float meleeMax, float abilityMin, float abilityMax,
			float ultimateMin, float ultimateMax) {
		this.id = id;
		this.colour = colour;
		this.meleeMin = meleeMin;
		this.meleeMax = meleeMax;
		this.abilityMin = abilityMin;
		this.abilityMax = abilityMax;
		this.ultimateMin = ultimateMin;
		this.ultimateMax = ultimateMax;
	}

	/** "Street Tier" in the tier's colour, bold. */
	public Component badge() {
		return Component.translatable("projecthero.rank." + id)
				.withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(colour)).withBold(true));
	}

	/** The badge plus the tier's damage ranges, for the Guidebook / I menu line under a power's title. */
	public Component guideLine() {
		return Component.empty().append(badge())
				.append(Component.translatable("projecthero.rank.ranges", fmt(meleeMin), fmt(meleeMax), fmt(abilityMin),
						fmt(abilityMax), fmt(ultimateMin), fmt(ultimateMax)).withStyle(ChatFormatting.GRAY));
	}

	private static String fmt(float f) {
		return f == (int) f ? Integer.toString((int) f) : Float.toString(f);
	}

	/** Guidebook chapter key ("projecthero.guide.<key>") -> rank, or null while the user hasn't placed that power. */
	public static PowerRank ofGuideKey(String chapterKey) {
		return switch (chapterKey) {
			case "projecthero.guide.super_soldier", "projecthero.guide.punisher", "projecthero.guide.black_panther",
					"projecthero.guide.power_armour" -> STREET;
			case "projecthero.guide.thor" -> COSMIC; // v0.17.7 (user): Thor is Cosmic
			case "projecthero.guide.shazam" -> PLANET; // v0.17.7 (user): Shazam is Planet
			default -> null;
		};
	}
}
