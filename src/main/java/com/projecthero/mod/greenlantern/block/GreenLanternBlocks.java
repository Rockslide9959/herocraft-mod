package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Green Lantern blocks: the Personal Power Battery and the Fallen Lantern Site's pedestal. */
public final class GreenLanternBlocks {
	// v0.11.5: explicit user request -- "make the power battery easily breakable as if it were dirt".
	// This reverses v0.11.4's blast-resistance raise (which existed only to guard against losing an
	// unrecoverable recharge point to a creeper); that tradeoff is now the player's own to manage.
	public static final Block POWER_BATTERY = register("power_battery",
			new PowerBatteryBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN).strength(0.5f).sound(SoundType.AMETHYST)
					.lightLevel(s -> 12).noOcclusion()));

	/** Not in the creative tab / not craftable -- placed only by the Fallen Lantern Site's worldgen. */
	public static final Block FALLEN_LANTERN_PEDESTAL = register("fallen_lantern_pedestal",
			new FallenLanternPedestalBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE).strength(50.0f, 1200.0f).sound(SoundType.DEEPSLATE)
					.noOcclusion()));

	public static final Item POWER_BATTERY_ITEM = registerItem("power_battery",
			new BlockItem(POWER_BATTERY, new Item.Properties().rarity(Rarity.RARE)));

	private GreenLanternBlocks() {
	}

	public static void initialize() {
		// Registration happens via the static initializers above; this exists for an explicit init call.
	}

	private static Block register(String path, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK,
				ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static Item registerItem(String path, Item item) {
		Item registered = Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
		if (registered instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, blockItem);
		}
		return registered;
	}
}
