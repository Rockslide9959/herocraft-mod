package com.projecthero.mod.moonknight.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.item.ModArmorMaterials;

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

		SuperheroArmorVisuals.register(MoonKnightArmorItem.SET_ID, new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/moon_knight.geo.json"),
				ProjectHeroMod.id("textures/armor/moon_knight.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
		// Per-alter suits: register "moon_knight_marc" / "_steven" / "_jake" here with their own texture and each
		// alter picks it up automatically (MoonKnightArmorItem#armorSetId). One texture for all three for now.

		// Phase 4: the Truncheon (Z) -- only ever exists in a transformed Moon Knight's hands.
		TRUNCHEON = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("moon_knight_truncheon"),
				new MoonKnightTruncheonItem());
	}

	/** Phase 4: Z's summoned weapon. */
	public static MoonKnightTruncheonItem TRUNCHEON;

	private static MoonKnightArmorItem register(String path, ArmorItem.Type type) {
		MoonKnightArmorItem item = new MoonKnightArmorItem(ModArmorMaterials.THOR, type,
				new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
