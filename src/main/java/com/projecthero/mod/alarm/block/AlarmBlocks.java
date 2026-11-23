package com.projecthero.mod.alarm.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.7: the alarm blocks -- Alarm Panel, Alarm Lamp, Blast Door, Security Camera, Laser Tripwire and the invisible
 * reinforcement spawn point -- and the lamp registry (other mods' / places' blocks that should light up on Alert).
 */
public final class AlarmBlocks {
	public static final EnumProperty<AlarmStyle> STYLE = EnumProperty.create("style", AlarmStyle.class);

	public static AlarmPanelBlock PANEL;
	public static AlarmLampBlock LAMP;
	public static BlastDoorBlock BLAST_DOOR;
	public static SecurityCameraBlock CAMERA;
	public static LaserTripwireBlock LASER;
	public static AlarmSpawnPointBlock SPAWN_POINT;

	public static BlockItem PANEL_ITEM;
	public static BlockItem LAMP_ITEM;
	public static BlockItem BLAST_DOOR_ITEM;
	public static BlockItem CAMERA_ITEM;
	public static BlockItem LASER_ITEM;
	public static BlockItem SPAWN_POINT_ITEM;

	public static BlockEntityType<SecurityCameraBlockEntity> CAMERA_BE;
	public static BlockEntityType<LaserTripwireBlockEntity> LASER_BE;

	/** Block -> its "lit" property, for everything that lights on Alert (the Alarm Lamp, plus registered extras). */
	private static final Map<Block, BooleanProperty> LAMPS = new LinkedHashMap<>();

	private AlarmBlocks() {
	}

	public static void initialize() {
		PANEL = block("alarm_panel", new AlarmPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
				.strength(-1.0f, 3600000.0f).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noLootTable()
				.lightLevel(s -> s.getValue(AlarmPanelBlock.SABOTAGED) ? 0 : s.getValue(AlarmPanelBlock.ALERT) ? 9 : 5)));
		LAMP = block("alarm_lamp", new AlarmLampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
				.strength(1.5f, 6.0f).sound(SoundType.GLASS).noLootTable().lightLevel(s -> s.getValue(AlarmLampBlock.LIT) ? 13 : 0)));
		BLAST_DOOR = block("blast_door", new BlastDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
				.strength(-1.0f, 3600000.0f).sound(SoundType.NETHERITE_BLOCK).pushReaction(PushReaction.BLOCK).noLootTable()
				.noOcclusion().isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> !s.getValue(BlastDoorBlock.OPEN))
				.isViewBlocking((s, l, p) -> !s.getValue(BlastDoorBlock.OPEN))));
		CAMERA = block("security_camera", new SecurityCameraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
				.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().noLootTable()));
		LASER = block("laser_tripwire", new LaserTripwireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
				.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().noLootTable()
				.lightLevel(s -> s.getValue(SensorBlockEntity.LIVE) ? 4 : 0)));
		SPAWN_POINT = block("alarm_spawn_point", new AlarmSpawnPointBlock(BlockBehaviour.Properties.of().noCollission()
				.noOcclusion().replaceable().noLootTable().strength(-1.0f, 3600000.0f).pushReaction(PushReaction.DESTROY)));

		PANEL_ITEM = item("alarm_panel", PANEL);
		LAMP_ITEM = item("alarm_lamp", LAMP);
		BLAST_DOOR_ITEM = item("blast_door", BLAST_DOOR);
		CAMERA_ITEM = item("security_camera", CAMERA);
		LASER_ITEM = item("laser_tripwire", LASER);
		SPAWN_POINT_ITEM = item("alarm_spawn_point", SPAWN_POINT);

		CAMERA_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("security_camera"),
				FabricBlockEntityTypeBuilder.create(SecurityCameraBlockEntity::new, CAMERA).build());
		LASER_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("laser_tripwire"),
				FabricBlockEntityTypeBuilder.create(LaserTripwireBlockEntity::new, LASER).build());

		registerLamp(LAMP, AlarmLampBlock.LIT);
	}

	/** Makes {@code block} light up (its {@code lit} property true) while its zone is Alert. */
	public static synchronized void registerLamp(Block block, BooleanProperty lit) {
		LAMPS.put(block, lit);
	}

	public static boolean isLamp(BlockState s) {
		return LAMPS.containsKey(s.getBlock());
	}

	public static void setLampLit(ServerLevel level, BlockPos pos, boolean lit) {
		BlockState s = level.getBlockState(pos);
		BooleanProperty p = LAMPS.get(s.getBlock());
		if (p != null && s.hasProperty(p) && s.getValue(p) != lit) {
			level.setBlock(pos, s.setValue(p, lit), 3);
		}
	}

	/** Blocks a zone files when it scans: panels, lamps, doors, spawn points. */
	public static boolean isZoneBlock(BlockState s) {
		return s.is(PANEL) || s.is(BLAST_DOOR) || s.is(SPAWN_POINT) || LAMPS.containsKey(s.getBlock());
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(PANEL_ITEM);
		output.accept(LAMP_ITEM);
		output.accept(BLAST_DOOR_ITEM);
		output.accept(CAMERA_ITEM);
		output.accept(LASER_ITEM);
		output.accept(SPAWN_POINT_ITEM);
		output.accept(com.projecthero.mod.alarm.AlarmKeycards.KEYCARD);
	}

	private static <B extends Block> B block(String path, B block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static BlockItem item(String path, Block block) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties()));
	}
}
