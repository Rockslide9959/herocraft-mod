package com.projecthero.mod.syndicate;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.syndicate.item.KingpinCaneItem;
import com.projecthero.mod.syndicate.item.PoliceScannerItem;
import com.projecthero.mod.syndicate.item.VillainDossierItem;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** v0.14.25: the Syndicate Bust's block, items and spawn eggs. */
public final class SyndicateItems {
	public static SyndicateStashBlock STASH;
	public static Item STASH_ITEM;
	public static BlockEntityType<SyndicateStashBlockEntity> STASH_BLOCK_ENTITY;
	public static Item POLICE_SCANNER;
	public static Item VILLAIN_DOSSIER;
	public static Item KINGPIN_CANE;
	public static Item THUG_SPAWN_EGG;
	public static Item GUNMAN_SPAWN_EGG;
	public static Item ENFORCER_SPAWN_EGG;
	public static Item KINGPIN_SPAWN_EGG;

	private SyndicateItems() {
	}

	static void initialize() {
		STASH = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("syndicate_stash")),
				new SyndicateStashBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.WOOD)
						.strength(3.0f, 1200.0f)
						.sound(SoundType.WOOD)
						.lightLevel(s -> s.getValue(SyndicateStashBlock.ACTIVE) ? 7 : 0)));
		BlockItem stashItem = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("syndicate_stash")),
				new BlockItem(STASH, new Item.Properties().rarity(Rarity.RARE)));
		stashItem.registerBlocks(Item.BY_BLOCK, stashItem);
		STASH_ITEM = stashItem;
		STASH_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("syndicate_stash"),
				FabricBlockEntityTypeBuilder.create(SyndicateStashBlockEntity::new, STASH).build());

		POLICE_SCANNER = item("police_scanner", new PoliceScannerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
		VILLAIN_DOSSIER = item("villain_dossier", new VillainDossierItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		KINGPIN_CANE = item("kingpin_cane", new KingpinCaneItem(new Item.Properties().rarity(Rarity.EPIC)));
		THUG_SPAWN_EGG = item("syndicate_thug_spawn_egg", new SpawnEggItem(SyndicateEntityTypes.THUG, 0x6B3A2A, 0x1C1C1C, new Item.Properties()));
		GUNMAN_SPAWN_EGG = item("syndicate_gunman_spawn_egg", new SpawnEggItem(SyndicateEntityTypes.GUNMAN, 0x1C1C1C, 0x8A8A8A, new Item.Properties()));
		ENFORCER_SPAWN_EGG = item("syndicate_enforcer_spawn_egg", new SpawnEggItem(SyndicateEntityTypes.ENFORCER, 0x3B2A20, 0xB5543A, new Item.Properties()));
		KINGPIN_SPAWN_EGG = item("kingpin_spawn_egg", new SpawnEggItem(SyndicateEntityTypes.KINGPIN, 0xEDEDE6, 0x5A2D6E, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(POLICE_SCANNER);
		output.accept(VILLAIN_DOSSIER);
		output.accept(KINGPIN_CANE);
		output.accept(STASH_ITEM);
		output.accept(THUG_SPAWN_EGG);
		output.accept(GUNMAN_SPAWN_EGG);
		output.accept(ENFORCER_SPAWN_EGG);
		output.accept(KINGPIN_SPAWN_EGG);
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
