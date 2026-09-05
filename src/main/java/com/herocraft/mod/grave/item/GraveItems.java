package com.herocraft.mod.grave.item;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.grave.CursedGraveBlock;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Everything the Zombie Raid adds to the item and block registries. Kept in its own class for the
 * same reason {@code IronManItems} and {@code HeroPackItems} are: each feature owns its registrations
 * and its creative-tab contribution, and nothing in Thor's {@code ModItems} has to change.
 */
public final class GraveItems {
	/** The raid's currency and crafting material. */
	public static Item GRAVE_ESSENCE;
	/** Remembers the Experimental Power of the boss it dropped from. */
	public static Item CORRUPTED_POWER_CORE;
	/** Below-20%-health emergency artifact. */
	public static Item GRAVEWALKER_CHARM;
	/** Three-charge Totem of Undying. */
	public static Item UNDYING_TOTEM;
	public static Item NECROTIC_BLADE;
	public static Item GRAVEKEEPER_SHIELD;
	/** First-clear reward; the ingredient that unlocks repeatable raids. */
	public static Item HEART_OF_THE_GRAVE;
	public static Item GRAVE_RITUAL_TOTEM;
	public static Item BOSS_TROPHY;
	public static Item FINAL_BOSS_TROPHY;

	/** The focal point of a Graveyard: right-click it to take the Gravebound Curse. */
	public static Block CURSED_GRAVE;
	public static Item CURSED_GRAVE_ITEM;

	private GraveItems() {
	}

	public static void initialize() {
		GraveComponents.initialize();

		CURSED_GRAVE = Registry.register(BuiltInRegistries.BLOCK,
				ResourceKey.create(Registries.BLOCK, HeroCraftMod.id("cursed_grave")),
				new CursedGraveBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.DEEPSLATE)
						.strength(4.0f, 1200.0f)
						.sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)
						.requiresCorrectToolForDrops()
						// Never let a piston shuffle the one interactive block in the structure away.
						.pushReaction(PushReaction.BLOCK)));
		CURSED_GRAVE_ITEM = registerBlockItem("cursed_grave", CURSED_GRAVE);

		GRAVE_ESSENCE = register("grave_essence", new Item(new Item.Properties()));
		CORRUPTED_POWER_CORE = register("corrupted_power_core",
				new CorruptedPowerCoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		GRAVEWALKER_CHARM = register("gravewalker_charm",
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
		UNDYING_TOTEM = register("undying_totem",
				new UndyingTotemItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
		NECROTIC_BLADE = register("necrotic_blade",
				// Netherite tier for durability and mining behaviour; the attack values are set here
				// rather than inherited so the base damage lands on the design's target of 9.
				new NecroticBladeItem(Tiers.NETHERITE, new Item.Properties()
						.rarity(Rarity.RARE)
						.attributes(net.minecraft.world.item.SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.4f))));
		GRAVEKEEPER_SHIELD = register("gravekeeper_shield",
				new GravekeeperShieldItem(new Item.Properties().durability(420).rarity(Rarity.RARE)));
		HEART_OF_THE_GRAVE = register("heart_of_the_grave",
				new Item(new Item.Properties().stacksTo(4).rarity(Rarity.EPIC)));
		GRAVE_RITUAL_TOTEM = register("grave_ritual_totem",
				new GraveRitualTotemItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE)));
		BOSS_TROPHY = register("boss_trophy",
				new BossTrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), false));
		FINAL_BOSS_TROPHY = register("final_boss_trophy",
				new BossTrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
	}

	/** Appended to the existing {@code herocraft:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(CURSED_GRAVE_ITEM);
		output.accept(GRAVE_ESSENCE);
		output.accept(CORRUPTED_POWER_CORE);
		output.accept(GRAVEWALKER_CHARM);
		output.accept(UndyingTotemItem.fresh(UNDYING_TOTEM));
		output.accept(NECROTIC_BLADE);
		output.accept(GRAVEKEEPER_SHIELD);
		output.accept(HEART_OF_THE_GRAVE);
		output.accept(GRAVE_RITUAL_TOTEM);
		output.accept(BOSS_TROPHY);
		output.accept(FINAL_BOSS_TROPHY);
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, HeroCraftMod.id(path)), item);
	}

	private static Item registerBlockItem(String path, Block block) {
		BlockItem item = Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, HeroCraftMod.id(path)),
				new BlockItem(block, new Item.Properties()));
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}
}
