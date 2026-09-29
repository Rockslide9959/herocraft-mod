package com.projecthero.mod.moonknight.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.item.ModArmorMaterials;
import com.projecthero.mod.moonknight.MoonKnightAlter;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Moon Knight's items. The four suit pieces only ever exist on a transformed Moon Knight (see {@code MoonKnightSuit});
 * the Truncheon (Phase 4) and the temple's Scarab (Phase 7) register alongside.
 */
public final class MoonKnightItems {
	public static MoonKnightArmorItem HELMET;
	public static MoonKnightArmorItem CHESTPLATE;
	public static MoonKnightArmorItem LEGGINGS;
	public static MoonKnightArmorItem BOOTS;

	private MoonKnightItems() {
	}

	public static void initialize() {
		HELMET = register("moon_knight_helmet", ArmorItem.Type.HELMET);
		CHESTPLATE = register("moon_knight_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = register("moon_knight_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = register("moon_knight_boots", ArmorItem.Type.BOOTS);

		// v0.13.21: one suit per alter, from the user's three Blockbench models (moonknightmarc / moonknightsteven /
		// moonknightJake.bbmodel, converted by scratchpad/gen_moonknight_alters.js). The three rigs are identical 64x64
		// player skins, so they share one geometry and differ only in texture; each alter picks its own set up through
		// MoonKnightArmorItem#armorSetId. The plain "moon_knight" set (no wearer known) is Marc's.
		SuperheroArmorVisuals.register(MoonKnightArmorItem.SET_ID, new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/moon_knight.geo.json"),
				ProjectHeroMod.id("textures/armor/moon_knight_marc.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
		for (MoonKnightAlter alter : MoonKnightAlter.values()) {
			SuperheroArmorVisuals.register(MoonKnightArmorItem.SET_ID + "_" + alter.id(), new ArmorVisualDefinition(
					ProjectHeroMod.id("geo/moon_knight.geo.json"),
					ProjectHeroMod.id(alter.suitTexture()),
					SuperheroArmorVisuals.SHARED_ANIMATION));
		}

		// Phase 4: the Truncheon (C since v0.13.21) -- only ever exists in a transformed Moon Knight's hands.
		TRUNCHEON = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("moon_knight_truncheon"),
				new MoonKnightTruncheonItem());
	}

	/** Phase 4: C's summoned weapon. */
	public static MoonKnightTruncheonItem TRUNCHEON;

	private static MoonKnightArmorItem register(String path, ArmorItem.Type type) {
		MoonKnightArmorItem item = new MoonKnightArmorItem(ModArmorMaterials.THOR, type,
				new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
