package com.projecthero.mod.stand.item;

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
 * v0.17.2: the Stand Arrow, slipped into desert pyramid chests (1 in 12), trail ruins' rare archaeology (1 in 20) and
 * ancient city chests (1 in 10).
 */
public final class StandItems {
	public static Item STAND_ARROW;

	private StandItems() {
	}

	public static void initialize() {
		STAND_ARROW = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("stand_arrow"),
				new StandArrowItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int empty;
			if (key.equals(BuiltInLootTables.DESERT_PYRAMID)) {
				empty = 11;
			} else if (key.equals(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE)) {
				empty = 19;
			} else if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
				empty = 9;
			} else {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(STAND_ARROW).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(empty)));
		});
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(STAND_ARROW);
	}
}
