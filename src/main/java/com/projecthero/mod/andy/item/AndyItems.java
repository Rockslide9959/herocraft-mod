package com.projecthero.mod.andy.item;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.Registry;
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

	private AndyItems() {
	}

	public static void initialize() {
		UNDEAD_CARD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("undead_card"),
				new UndeadCardItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
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

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(UNDEAD_CARD);
	}
}
