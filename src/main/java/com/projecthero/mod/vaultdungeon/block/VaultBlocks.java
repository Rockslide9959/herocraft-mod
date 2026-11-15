package com.projecthero.mod.vaultdungeon.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.5: The Vault's blocks -- the sealed Dungeon Vault, the Lockdown Shutter (Warden Kross's arena seal), the laser
 * tripwire alarm, the ceiling security turret, the security console (opens the lockdown gate) and the hazard-striped
 * lockdown track (marks the doorways a Lockdown seals).
 */
public final class VaultBlocks {
	public static VaultSealBlock SEAL;
	public static LockdownShutterBlock SHUTTER;
	public static VaultAlarmBlock ALARM;
	public static VaultTurretBlock TURRET;
	public static VaultConsoleBlock CONSOLE;
	public static Block TRACK;

	public static BlockEntityType<VaultTurretBlockEntity> TURRET_BE;

	public static BlockItem SEAL_ITEM;
	public static BlockItem SHUTTER_ITEM;
	public static BlockItem ALARM_ITEM;
	public static BlockItem TURRET_ITEM;
	public static BlockItem CONSOLE_ITEM;
	public static BlockItem TRACK_ITEM;

	private VaultBlocks() {
	}

	public static void initialize() {
		SEAL = block("vault_seal", new VaultSealBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.DIAMOND).strength(-1.0f, 3600000.0f).sound(SoundType.VAULT).lightLevel(s -> 6)
				.pushReaction(PushReaction.BLOCK).noLootTable()));
		SHUTTER = block("lockdown_shutter", new LockdownShutterBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_RED).strength(-1.0f, 3600000.0f).sound(SoundType.METAL).lightLevel(s -> 3)
				.pushReaction(PushReaction.BLOCK).noLootTable()));
		ALARM = block("vault_laser_alarm", new VaultAlarmBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_RED).strength(2.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()
				.lightLevel(s -> s.getValue(VaultAlarmBlock.SPENT) ? 0 : 7).noLootTable()));
		TURRET = block("vault_turret", new VaultTurretBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL).strength(4.0f, 12.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()
				.lightLevel(s -> 4).noLootTable()));
		CONSOLE = block("vault_console", new VaultConsoleBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 12.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()
				.lightLevel(s -> 10).noLootTable()));
		TRACK = block("lockdown_track", new Block(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_YELLOW).strength(3.0f, 9.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

		TURRET_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("vault_turret"),
				BlockEntityType.Builder.of(VaultTurretBlockEntity::new, TURRET).build(null));

		SEAL_ITEM = item("vault_seal", SEAL);
		SHUTTER_ITEM = item("lockdown_shutter", SHUTTER);
		ALARM_ITEM = item("vault_laser_alarm", ALARM);
		TURRET_ITEM = item("vault_turret", TURRET);
		CONSOLE_ITEM = item("vault_console", CONSOLE);
		TRACK_ITEM = item("lockdown_track", TRACK);
	}

	private static <B extends Block> B block(String path, B block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static BlockItem item(String path, Block block) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties()));
	}
}
