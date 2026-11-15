package com.projecthero.mod.hideout.block;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** v0.17.5: the villain-hideout blocks -- core, sealed vault, alarm, vent, and the Symbiote Hive's biomass and pods. */
public final class HideoutBlocks {
	public static final EnumProperty<HideoutFaction> FACTION = EnumProperty.create("faction", HideoutFaction.class);

	public static HideoutCoreBlock CORE;
	public static HideoutSealBlock SEAL;
	public static HideoutAlarmBlock ALARM;
	public static HideoutVentBlock VENT;
	public static HiveBiomassBlock HIVE_BIOMASS;
	public static HiveBiomassBlock HIVE_POD;

	public static BlockItem CORE_ITEM;
	public static BlockItem SEAL_ITEM;
	public static BlockItem ALARM_ITEM;
	public static BlockItem VENT_ITEM;
	public static BlockItem HIVE_BIOMASS_ITEM;
	public static BlockItem HIVE_POD_ITEM;

	private HideoutBlocks() {
	}

	public static void initialize() {
		CORE = block("hideout_core", new HideoutCoreBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL).strength(6.0f, 3600000.0f).sound(SoundType.LODESTONE)
				.lightLevel(s -> s.getValue(HideoutCoreBlock.SEALED) ? 9 : 14).pushReaction(PushReaction.BLOCK)
				.noLootTable()));
		SEAL = block("hideout_seal", new HideoutSealBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).sound(SoundType.VAULT).lightLevel(s -> 4)
				.pushReaction(PushReaction.BLOCK).noLootTable()));
		ALARM = block("hideout_alarm", new HideoutAlarmBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_RED).strength(2.0f, 6.0f).sound(SoundType.METAL)
				.lightLevel(s -> s.getValue(HideoutAlarmBlock.SPENT) ? 0 : 5).noLootTable()));
		VENT = block("hideout_vent", new HideoutVentBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY).strength(4.0f, 12.0f).sound(SoundType.METAL)
				.lightLevel(s -> s.getValue(HideoutVentBlock.PHASE) == 1 ? 10 : 3).noLootTable()));
		HIVE_BIOMASS = block("hive_biomass", new HiveBiomassBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK).strength(1.2f, 3.0f).sound(SoundType.SLIME_BLOCK).speedFactor(0.7f).jumpFactor(0.8f)));
		HIVE_POD = block("hive_pod", new HiveBiomassBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.SNOW).strength(0.8f, 1.0f).sound(SoundType.HONEY_BLOCK).lightLevel(s -> 6)));

		CORE_ITEM = item("hideout_core", CORE);
		SEAL_ITEM = item("hideout_seal", SEAL);
		ALARM_ITEM = item("hideout_alarm", ALARM);
		VENT_ITEM = item("hideout_vent", VENT);
		HIVE_BIOMASS_ITEM = item("hive_biomass", HIVE_BIOMASS);
		HIVE_POD_ITEM = item("hive_pod", HIVE_POD);
	}

	private static <B extends Block> B block(String path, B block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static BlockItem item(String path, Block block) {
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties()));
		return item;
	}
}
