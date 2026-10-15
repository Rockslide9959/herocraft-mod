package com.projecthero.mod.ghostrider.item;

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
 * v0.16.5: Ghost Rider's things -- Mephisto's Contract, slipped into Nether fortress chests (1 in 25) and bastion
 * treasure / other chests (1 in 12 / 1 in 40).
 */
public final class GhostRiderItems {
	public static Item MEPHISTO_CONTRACT;

	private GhostRiderItems() {
	}

	public static void initialize() {
		MEPHISTO_CONTRACT = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("mephisto_contract"),
				new MephistoContractItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int empty;
			if (key.equals(BuiltInLootTables.NETHER_BRIDGE)) {
				empty = 24;
			} else if (key.equals(BuiltInLootTables.BASTION_TREASURE)) {
				empty = 11;
			} else if (key.equals(BuiltInLootTables.BASTION_OTHER)) {
				empty = 39;
			} else {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(MEPHISTO_CONTRACT).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(empty)));
		});
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(MEPHISTO_CONTRACT);
	}
}
