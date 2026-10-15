package com.projecthero.mod.bluebeetle.item;

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
 * v0.16.5: Blue Beetle's things -- the Scarab. It turns up in the suspicious sand of desert pyramids (one more entry in
 * the brushing pool, so one brushed block in nine) and, rarely, in the pyramid's chests (1 in 30).
 */
public final class BlueBeetleItems {
	public static Item SCARAB;

	private BlueBeetleItems() {
	}

	public static void initialize() {
		SCARAB = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("scarab"),
				new ScarabItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			if (key.equals(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY)) {
				// brushing gives exactly one item, so the Scarab joins the existing pool instead of adding a second roll
				builder.modifyPools(pool -> pool.add(LootItem.lootTableItem(SCARAB).setWeight(1)));
			} else if (key.equals(BuiltInLootTables.DESERT_PYRAMID)) {
				builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(SCARAB).setWeight(1))
						.add(EmptyLootItem.emptyItem().setWeight(29)));
			}
		});
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(SCARAB);
	}
}
