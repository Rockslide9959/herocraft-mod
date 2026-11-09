package com.projecthero.mod.andy.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.3: the Undead Card. Rare loot: one ancient city chest in {@link #ANCIENT_CITY_ODDS}, one stronghold chest (corridor,
 * crossing or library) in {@link #STRONGHOLD_ODDS}.
 */
public final class AndyItems {
	public static final int ANCIENT_CITY_ODDS = 25;
	public static final int STRONGHOLD_ODDS = 40;

	public static Item UNDEAD_CARD;
	/** v0.17.3: Andy's Suit (AndySuitItem): iron-level, self-repairing, the chestplate feeds him. */
	public static Holder<ArmorMaterial> SUIT_MATERIAL;
	public static AndySuitItem SUIT_CHESTPLATE, SUIT_LEGGINGS, SUIT_BOOTS;

	private AndyItems() {
	}

	public static void initialize() {
		UNDEAD_CARD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("undead_card"),
				new UndeadCardItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		// iron: 2 / 6 / 5 / 2, enchantability 9, the iron sound, repaired with iron; the vanilla layer is never drawn (AndySuitArmourMixin)
		SUIT_MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("andy_suit")),
				new ArmorMaterial(Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.CHESTPLATE, 6, ArmorItem.Type.HELMET, 2, ArmorItem.Type.BODY, 5),
						9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT), List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("andy_suit"))), 0f, 0f));
		SUIT_CHESTPLATE = suit("andy_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = suit("andy_suit_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = suit("andy_suit_boots", ArmorItem.Type.BOOTS);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int odds = 0;
			if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
				odds = ANCIENT_CITY_ODDS;
			} else if (key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR) || key.equals(BuiltInLootTables.STRONGHOLD_CROSSING)
					|| key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
				odds = STRONGHOLD_ODDS;
			}
			if (odds > 0) {
				builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(UNDEAD_CARD).setWeight(1))
						.add(EmptyLootItem.emptyItem().setWeight(odds - 1)));
			}
		});
	}

	private static AndySuitItem suit(String path, ArmorItem.Type type) {
		// iron durability (multiplier 15)
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new AndySuitItem(SUIT_MATERIAL, type, new Item.Properties().rarity(Rarity.UNCOMMON).durability(type.getDurability(15))));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(UNDEAD_CARD);
		output.accept(SUIT_CHESTPLATE);
		output.accept(SUIT_LEGGINGS);
		output.accept(SUIT_BOOTS);
	}
}
