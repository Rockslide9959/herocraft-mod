package com.projecthero.mod.allmight.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.item.ModArmorMaterials;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items belonging to the All Might / One For All Hero-Tier power (v0.12.36: a craftable two-piece costume, kept in the N locker and worn automatically in the Power Form). */
public final class AllMightItems {
	/** Craftable: using it grants the power. */
	public static Item ONE_FOR_ALL_VESTIGE;

	/** v0.12.38: the two-slot costume locker (N in the Power Form). */
	public static net.minecraft.world.inventory.MenuType<com.projecthero.mod.allmight.AllMightLockerMenu> LOCKER_MENU;

	public static AllMightArmorItem CHESTPLATE;
	public static AllMightArmorItem LEGGINGS;

	private AllMightItems() {
	}

	public static void initialize() {
		ONE_FOR_ALL_VESTIGE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("one_for_all_vestige"),
				new OneForAllVestigeItem(new Item.Properties().rarity(Rarity.EPIC)));

		LOCKER_MENU = Registry.register(BuiltInRegistries.MENU, ProjectHeroMod.id("all_might_locker"),
				new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(com.projecthero.mod.allmight.AllMightLockerMenu::new,
						net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
		CHESTPLATE = registerPiece("all_might_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = registerPiece("all_might_leggings", ArmorItem.Type.LEGGINGS);

		SuperheroArmorVisuals.register("all_might", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/all_might.geo.json"),
				ProjectHeroMod.id("textures/armor/all_might.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ONE_FOR_ALL_VESTIGE);
		output.accept(CHESTPLATE);
		output.accept(LEGGINGS);
	}

	private static AllMightArmorItem registerPiece(String path, ArmorItem.Type type) {
		AllMightArmorItem item = new AllMightArmorItem(ModArmorMaterials.THOR, type,
				new Item.Properties().rarity(Rarity.EPIC).durability(type.getDurability(37)));
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
