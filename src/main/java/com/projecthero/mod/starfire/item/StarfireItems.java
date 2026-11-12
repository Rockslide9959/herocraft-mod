package com.projecthero.mod.starfire.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * v0.17.3 Starfire: the power's items. The <b>Tamaranean Starstone</b> grants it ({@code StarfireLearning}: End City chests,
 * meteor craters; right-click in daylight). The <b>Starfire costume</b> (chestplate, leggings, boots) is crafted
 * ({@code data/projecthero/recipe/starfire_suit_*.json}) and worn like the other skin-costume suits. Icons from
 * {@code scratchpad/starfire/gen_starfire_textures.js}.
 */
public final class StarfireItems {
	public static Item STARSTONE;
	public static Holder<ArmorMaterial> SUIT_MATERIAL;
	public static StarfireSuitItem SUIT_CHESTPLATE, SUIT_LEGGINGS, SUIT_BOOTS;

	private StarfireItems() {
	}

	public static void initialize() {
		if (STARSTONE != null) {
			return;
		}
		STARSTONE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("tamaranean_starstone"),
				new StarstoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		// iron: 2 / 5 / 6 / 2, leather's sound, repaired with purple wool; the vanilla layer is never drawn (QuirkSuitArmourMixin)
		SUIT_MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("starfire_suit")),
				new ArmorMaterial(Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6,
						ArmorItem.Type.HELMET, 2, ArmorItem.Type.BODY, 5),
						12, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.PURPLE_WOOL),
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("starfire_suit"))), 0f, 0f));
		SUIT_CHESTPLATE = suit("starfire_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = suit("starfire_suit_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = suit("starfire_suit_boots", ArmorItem.Type.BOOTS);
	}

	private static StarfireSuitItem suit(String path, ArmorItem.Type type) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new StarfireSuitItem(SUIT_MATERIAL, type, new Item.Properties().rarity(Rarity.UNCOMMON).durability(type.getDurability(15))));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(STARSTONE);
		output.accept(SUIT_CHESTPLATE);
		output.accept(SUIT_LEGGINGS);
		output.accept(SUIT_BOOTS);
	}
}
