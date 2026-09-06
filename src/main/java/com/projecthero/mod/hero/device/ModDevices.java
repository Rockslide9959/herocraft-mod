package com.projecthero.mod.hero.device;

import java.util.LinkedHashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.MutationTrigger.Kind;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** The 8 (+1) reusable laboratory device blocks. All in the {@code projecthero:superheroes} tab. */
public final class ModDevices {
	private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

	public static final Block OVERLOADED_REDSTONE_COIL = device("overloaded_redstone_coil", MapColor.FIRE, true, Kind.ELECTRICAL_DISCHARGE);
	public static final Block EXPERIMENTAL_LIGHT_PROJECTOR = device("experimental_light_projector", MapColor.SNOW, true, Kind.HIGH_INTENSITY_LIGHT);
	public static final Block UNSTABLE_GRAVITY_PLATE = device("unstable_gravity_plate", MapColor.COLOR_PURPLE, true, Kind.GRAVITY_DISTORTION);
	public static final Block GEOLOGICAL_RESONANCE_CHAMBER = device("geological_resonance_chamber", MapColor.STONE, false, Kind.GEOLOGICAL_RESONANCE, Kind.AMETHYST_GEODE);
	public static final Block MOLECULAR_COMPRESSION_CHAMBER = device("molecular_compression_chamber", MapColor.METAL, true, Kind.MOLECULAR_COMPRESSION);
	public static final Block MASS_COMPRESSION_CHAMBER = device("mass_compression_chamber", MapColor.METAL, true, Kind.MASS_COMPRESSION);
	public static final Block PRESSURE_CHAMBER = device("pressure_chamber", MapColor.COLOR_LIGHT_BLUE, true, Kind.PRESSURE_CHAMBER);
	public static final Block HYDROSTATIC_TEST_TANK = device("hydrostatic_test_tank", MapColor.WATER, false, Kind.SUBMERSION);
	public static final Block ELECTROMAGNETIC_COIL = device("electromagnetic_coil", MapColor.COLOR_ORANGE, true, Kind.MAGNETIC_FIELD);

	private ModDevices() {
	}

	public static void initialize() {
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		ITEMS.values().forEach(output::accept);
	}

	public static Block block(String id) {
		return BLOCKS.get(id);
	}

	private static Block device(String id, MapColor color, boolean redstone, Kind... kinds) {
		Block block = Registry.register(BuiltInRegistries.BLOCK,
				ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(id)),
				new LabDeviceBlock(BlockBehaviour.Properties.of().mapColor(color).strength(3.5f, 6.0f)
						.sound(SoundType.COPPER).requiresCorrectToolForDrops(), redstone, kinds));
		BlockItem item = Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(id)),
				new BlockItem(block, new Item.Properties()));
		item.registerBlocks(Item.BY_BLOCK, item);
		BLOCKS.put(id, block);
		ITEMS.put(id, item);
		return block;
	}
}
