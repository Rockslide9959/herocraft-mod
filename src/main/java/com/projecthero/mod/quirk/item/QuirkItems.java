package com.projecthero.mod.quirk.item;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;

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
 * v0.16.7: the Quirk Serums -- one per Quirk ({@code quirk_serum_<id>}) and the <b>Quirk Trigger</b>
 * ({@code quirk_serum_random}, a random Quirk you don't have). Drunk like the other power serums; the Quirk is given
 * through {@code Quirks.grant} (one Primary power: it replaces whatever was held).
 *
 * <p>Crafted (shapeless, mid-game): glass bottle + diamond + amethyst shard + the Quirk's two themed ingredients (see
 * {@code data/projecthero/recipe/quirk_serum_*.json}). Loot: monster-room dungeon chests (one serum in
 * {@link #DUNGEON_ODDS}), stronghold corridor chests ({@link #STRONGHOLD_ODDS}) and ancient city chests
 * ({@link #ANCIENT_CITY_ODDS}); each hit is any of the eight with equal chance.
 */
public final class QuirkItems {
	private static final Map<Quirk, Item> SERUMS = new EnumMap<>(Quirk.class);
	public static Item QUIRK_TRIGGER;

	/** Loot odds: one serum in this many chests (EmptyLootItem weight = 8 * (odds - 1)). */
	public static final int DUNGEON_ODDS = 12;
	public static final int STRONGHOLD_ODDS = 10;
	public static final int ANCIENT_CITY_ODDS = 8;

	private QuirkItems() {
	}

	public static void initialize() {
		for (Quirk q : Quirk.values()) {
			SERUMS.put(q, register("quirk_serum_" + q.id, new QuirkSerumItem(q, new Item.Properties().rarity(Rarity.RARE))));
		}
		QUIRK_TRIGGER = register("quirk_serum_random", new QuirkSerumItem(null, new Item.Properties().rarity(Rarity.RARE)));
		// v0.16.7 (user): the seven craftable hero suits
		QuirkSuits.initialize();

		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int odds;
			if (key.equals(BuiltInLootTables.SIMPLE_DUNGEON)) {
				odds = DUNGEON_ODDS;
			} else if (key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)) {
				odds = STRONGHOLD_ODDS;
			} else if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
				odds = ANCIENT_CITY_ODDS;
			} else {
				return;
			}
			LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));
			for (Item serum : SERUMS.values()) {
				pool.add(LootItem.lootTableItem(serum).setWeight(1));
			}
			pool.add(LootItem.lootTableItem(QUIRK_TRIGGER).setWeight(1));
			pool.add(EmptyLootItem.emptyItem().setWeight((SERUMS.size() + 1) * (odds - 1)));
			builder.withPool(pool);
		});
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}

	public static Item serumOf(Quirk q) {
		return SERUMS.get(q);
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Quirk q : Quirk.values()) {
			output.accept(SERUMS.get(q));
		}
		output.accept(QUIRK_TRIGGER);
		output.accept(com.projecthero.mod.quirk.erasure.ErasureItems.EYE_DROPS); // v0.16.7 revamp: Erasure's Eye Drops
		QuirkSuits.addToCreativeTab(output);
	}
}
