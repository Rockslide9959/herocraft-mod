package com.projecthero.mod.grave.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.grave.CursedGraveBlock;
import com.projecthero.mod.grave.TrophyHeadBlock;
import com.projecthero.mod.grave.TrophyHeadBlockEntity;
import com.projecthero.mod.grave.TrophyHeadWallBlock;
import com.projecthero.mod.grave.TrophyHeads;

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
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
	/** Grave Essence pressed into a bar -- the crafting metal for the Necrotic Blade and Heart. */
	public static Item GRAVEBOUND_INGOT;
	/** Three-charge Totem of Undying. */
	public static Item UNDYING_TOTEM;
	public static Item NECROTIC_BLADE;
	public static Item GRAVEKEEPER_SHIELD;
	/** First-clear reward; the ingredient that unlocks repeatable raids. */
	public static Item HEART_OF_THE_GRAVE;
	public static Item GRAVE_RITUAL_TOTEM;
	public static Item BOSS_TROPHY;
	public static Item FINAL_BOSS_TROPHY;
	/** v0.13.6: an Abyssal Core surrounded by Grave Essence. Right-click a Lodestone with it to summon
	 * The Oathbreaker -- see {@link KnightsSoulItem}. */
	public static Item KNIGHTS_SOUL;
	public static Item BROKEN_OATH;

	/** The focal point of a Graveyard: right-click it to take the Gravebound Curse. */
	public static Block CURSED_GRAVE;
	public static Item CURSED_GRAVE_ITEM;

	/** v0.9.22: the wave-12 trophy as a placeable, wearable head block. The item is {@link #FINAL_BOSS_TROPHY}. */
	public static Block GRAVE_CHAMPION_HEAD;
	/** v0.14.4: the trophy heads' wall-mounted twins, and the Empowered Zombie Head's blocks (item {@link #BOSS_TROPHY}). */
	public static Block GRAVE_CHAMPION_WALL_HEAD;
	public static Block EMPOWERED_ZOMBIE_HEAD;
	public static Block EMPOWERED_ZOMBIE_WALL_HEAD;
	/** v0.14.4: remembers a placed trophy's power and kill record. */
	public static BlockEntityType<TrophyHeadBlockEntity> TROPHY_HEAD_BE;

	private GraveItems() {
	}

	public static void initialize() {
		GraveComponents.initialize();

		CURSED_GRAVE = Registry.register(BuiltInRegistries.BLOCK,
				ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("cursed_grave")),
				new CursedGraveBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.DEEPSLATE)
						.strength(4.0f, 1200.0f)
						.sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)
						.requiresCorrectToolForDrops()
						// Never let a piston shuffle the one interactive block in the structure away.
						.pushReaction(PushReaction.BLOCK)));
		CURSED_GRAVE_ITEM = registerBlockItem("cursed_grave", CURSED_GRAVE);

		// v0.14.4: floor + wall blocks for both trophy heads (vanilla mob-head behaviour: fragile, pistons pop them).
		GRAVE_CHAMPION_HEAD = registerBlock("grave_champion_head",
				new TrophyHeadBlock(TrophyHeads.Kind.CHAMPION, headProperties(MapColor.DEEPSLATE)));
		GRAVE_CHAMPION_WALL_HEAD = registerBlock("grave_champion_wall_head",
				new TrophyHeadWallBlock(TrophyHeads.Kind.CHAMPION, headProperties(MapColor.DEEPSLATE)));
		EMPOWERED_ZOMBIE_HEAD = registerBlock("empowered_zombie_head",
				new TrophyHeadBlock(TrophyHeads.Kind.ZOMBIE, headProperties(MapColor.COLOR_GREEN)));
		EMPOWERED_ZOMBIE_WALL_HEAD = registerBlock("empowered_zombie_wall_head",
				new TrophyHeadWallBlock(TrophyHeads.Kind.ZOMBIE, headProperties(MapColor.COLOR_GREEN)));
		TROPHY_HEAD_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("trophy_head"),
				BlockEntityType.Builder.of(TrophyHeadBlockEntity::new, GRAVE_CHAMPION_HEAD, GRAVE_CHAMPION_WALL_HEAD,
						EMPOWERED_ZOMBIE_HEAD, EMPOWERED_ZOMBIE_WALL_HEAD).build(null));

		GRAVE_ESSENCE = register("grave_essence", new Item(new Item.Properties()));
		GRAVEBOUND_INGOT = register("gravebound_ingot",
				new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
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
				new HeartOfTheGraveItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
						.food(new net.minecraft.world.food.FoodProperties.Builder()
								.nutrition(4).saturationModifier(0.4f).alwaysEdible().build())));
		GRAVE_RITUAL_TOTEM = register("grave_ritual_totem",
				new GraveRitualTotemItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE)));
		BOSS_TROPHY = register("boss_trophy",
				new BossTrophyItem(EMPOWERED_ZOMBIE_HEAD, EMPOWERED_ZOMBIE_WALL_HEAD,
						new Item.Properties().stacksTo(16).rarity(Rarity.RARE), false));
		((BlockItem) BOSS_TROPHY).registerBlocks(Item.BY_BLOCK, BOSS_TROPHY);
		FINAL_BOSS_TROPHY = register("final_boss_trophy",
				new BossTrophyItem(GRAVE_CHAMPION_HEAD, GRAVE_CHAMPION_WALL_HEAD,
						new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
		((BlockItem) FINAL_BOSS_TROPHY).registerBlocks(Item.BY_BLOCK, FINAL_BOSS_TROPHY);
		// A dispenser puts a trophy head on whoever stands in front of it, like armour (else it drops the head).
		DispenserBlock.registerBehavior(BOSS_TROPHY, net.minecraft.world.item.ArmorItem.DISPENSE_ITEM_BEHAVIOR);
		DispenserBlock.registerBehavior(FINAL_BOSS_TROPHY, net.minecraft.world.item.ArmorItem.DISPENSE_ITEM_BEHAVIOR);
		KNIGHTS_SOUL = register("knights_soul",
				new KnightsSoulItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
		// v0.14.0: the Oathbreaker's guaranteed drop. A crafting material with no use yet (reserved for a
		// future weapon); texture generated by scratchpad/build_oathbreaker_assets.js.
		BROKEN_OATH = register("broken_oath", new Item(new Item.Properties().rarity(Rarity.EPIC)));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(CURSED_GRAVE_ITEM);
		output.accept(GRAVE_ESSENCE);
		output.accept(GRAVEBOUND_INGOT);
		output.accept(UndyingTotemItem.fresh(UNDYING_TOTEM));
		output.accept(NECROTIC_BLADE);
		output.accept(GRAVEKEEPER_SHIELD);
		output.accept(HEART_OF_THE_GRAVE);
		output.accept(GRAVE_RITUAL_TOTEM);
		output.accept(BOSS_TROPHY);
		output.accept(FINAL_BOSS_TROPHY);
		output.accept(KNIGHTS_SOUL);
		output.accept(BROKEN_OATH);
	}

	private static BlockBehaviour.Properties headProperties(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(1.0f)
				.sound(net.minecraft.world.level.block.SoundType.BONE_BLOCK)
				.noOcclusion()
				.pushReaction(PushReaction.DESTROY);
	}

	private static Block registerBlock(String path, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}

	private static Item registerBlockItem(String path, Block block) {
		BlockItem item = Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties()));
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}
}
