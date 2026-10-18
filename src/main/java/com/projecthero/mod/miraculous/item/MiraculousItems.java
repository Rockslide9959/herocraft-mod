package com.projecthero.mod.miraculous.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.Kwami;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.16.6: the Miraculous things -- the Guardian's <b>Miraculous Box</b> (rare loot: jungle temple chests 1 in 8,
 * woodland mansion chests 1 in 6), the three jewels it holds (each grants its Kwami), Hawk Moth's Butterfly Brooch (never
 * in the box: stronghold library chests 1 in 6, ancient city chests 1 in 10), and the Kwamis' favourite foods (Tikki's
 * cookies and Nooroo's honey bottles are vanilla): {@link #CAMEMBERT} (shapeless: milk bucket + wheat + brown mushroom, the bucket comes
 * back) and {@link #MACARON} (shapeless, makes 4: 2 sugar + egg + pink dye).
 */
public final class MiraculousItems {
	public static Item MIRACULOUS_BOX;
	public static Item LADYBUG_EARRINGS;
	public static Item CAT_RING;
	public static Item FOX_NECKLACE;
	/** Hawk Moth's jewel: NOT in the box -- stronghold library chests (1 in 6), ancient city chests (1 in 10). */
	public static Item BUTTERFLY_BROOCH;
	/** v0.16.6: Carapace's jewel (the core build adds its model, loot and worn render). */
	public static Item TURTLE_BRACELET;
	/** v0.16.6: Pegasus's jewel. */
	public static Item HORSESHOE_GLASSES;
	public static Item CAMEMBERT;
	public static Item MACARON;

	/** Loot odds: one box in this many chests (EmptyLootItem weight = odds - 1). */
	public static final int JUNGLE_TEMPLE_ODDS = 8;
	public static final int MANSION_ODDS = 6;
	public static final int STRONGHOLD_LIBRARY_ODDS = 6;
	public static final int ANCIENT_CITY_ODDS = 10;

	private MiraculousItems() {
	}

	public static void initialize() {
		MIRACULOUS_BOX = register("miraculous_box", new MiraculousBoxItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		LADYBUG_EARRINGS = register("ladybug_earrings", new KwamiJewelItem(Kwami.TIKKI, jewel()));
		CAT_RING = register("cat_ring", new KwamiJewelItem(Kwami.PLAGG, jewel()));
		FOX_NECKLACE = register("fox_necklace", new KwamiJewelItem(Kwami.TRIXX, jewel()));
		BUTTERFLY_BROOCH = register("butterfly_brooch", new KwamiJewelItem(Kwami.NOOROO, jewel()));
		TURTLE_BRACELET = register("turtle_bracelet", new KwamiJewelItem(Kwami.WAYZZ, jewel()));
		HORSESHOE_GLASSES = register("horseshoe_glasses", new KwamiJewelItem(Kwami.KAALKI, jewel()));
		CAMEMBERT = register("camembert", new Item(new Item.Properties()
				.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build())));
		MACARON = register("macaron", new Item(new Item.Properties()
				.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).fast().build())));

		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int odds;
			Item item = MIRACULOUS_BOX;
			if (key.equals(BuiltInLootTables.JUNGLE_TEMPLE)) {
				odds = JUNGLE_TEMPLE_ODDS;
			} else if (key.equals(BuiltInLootTables.WOODLAND_MANSION)) {
				odds = MANSION_ODDS;
			} else if (key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
				odds = STRONGHOLD_LIBRARY_ODDS;
				item = BUTTERFLY_BROOCH;
			} else if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
				odds = ANCIENT_CITY_ODDS;
				item = BUTTERFLY_BROOCH;
			} else {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(item).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(odds - 1)));
		});
	}

	private static Item.Properties jewel() {
		return new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant();
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}

	public static Item jewelOf(Kwami kwami) {
		return switch (kwami) {
			case TIKKI -> LADYBUG_EARRINGS;
			case PLAGG -> CAT_RING;
			case TRIXX -> FOX_NECKLACE;
			case NOOROO -> BUTTERFLY_BROOCH;
			case WAYZZ -> TURTLE_BRACELET;
			case KAALKI -> HORSESHOE_GLASSES;
		};
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(MIRACULOUS_BOX);
		output.accept(LADYBUG_EARRINGS);
		output.accept(CAT_RING);
		output.accept(FOX_NECKLACE);
		output.accept(BUTTERFLY_BROOCH);
		output.accept(CAMEMBERT);
		output.accept(MACARON);
	}
}
