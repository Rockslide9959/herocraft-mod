package com.projecthero.mod.hideout;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * v0.17.5: the five villain factions that each run one Tier 3 hideout. The serialized name is the block-state value,
 * the loot-condition value and the save-file value, so it must never change.
 */
public enum HideoutFaction implements StringRepresentable {
	KINGPIN("kingpin", "kingpins_front", 0xE0B23A),
	ULTRON("ultron", "ultron_forge", 0xE0281E),
	APOKOLIPS("apokolips", "apokolips_outpost", 0xFF7A1A),
	SYMBIOTE("symbiote", "symbiote_hive", 0xE8E8F0),
	FROST("frost", "frost_giant_hall", 0x7FD8FF),
	// v0.17.7: three more Tier 3 hideouts (docs/STRUCTURES_V0177_DESIGN.md section 4)
	GRAVE("gravebound", "gravebound_crypt", 0x9A6BE0),
	CARNAGE("carnage", "carnage_nest", 0xD8222C),
	OATH("oathbreaker", "oathbreaker_keep", 0x4FC9D9);

	private final String name;
	private final String structure;
	private final int colour;

	HideoutFaction(String name, String structure, int colour) {
		this.name = name;
		this.structure = structure;
		this.colour = colour;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** The structure id path ({@code projecthero:<structure>}). */
	public String structurePath() {
		return structure;
	}

	public ResourceLocation structureId() {
		return ProjectHeroMod.id(structure);
	}

	/** The faction's colour (core glow, particles, chat). */
	public int colour() {
		return colour;
	}

	/** "Kingpin's Front", "Ultron Forge" ... */
	public Component hideoutName() {
		return Component.translatable("structure.projecthero." + structure);
	}

	/** "the Syndicate", "Ultron", ... (the faction itself, for the raid-calm messages). */
	public Component factionName() {
		return Component.translatable("projecthero.hideout.faction." + name);
	}

	/** The mini-boss's name key. */
	public Component bossName() {
		return Component.translatable("projecthero.hideout.boss." + name);
	}

	public static HideoutFaction byName(String name) {
		for (HideoutFaction f : values()) {
			if (f.name.equals(name)) {
				return f;
			}
		}
		return null;
	}

	public static final StringRepresentable.EnumCodec<HideoutFaction> CODEC = StringRepresentable.fromEnum(HideoutFaction::values);
}
