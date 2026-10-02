package com.projecthero.mod.ironman.sorter;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.14.16: registration for the Stark Sorting Station and its Sorter Bot -- block, block item, block entity,
 * menu and entity type. Kept out of {@code IronManBlocks} so the sorter stays one self-contained package.
 */
public final class StarkSorter {
	public static final Block STATION = Registry.register(BuiltInRegistries.BLOCK,
			ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("stark_sorting_station")),
			new SortingStationBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED).strength(3.5f, 8.0f).sound(SoundType.NETHERITE_BLOCK)
					.requiresCorrectToolForDrops().lightLevel(s -> 6).noOcclusion()));

	public static final Item STATION_ITEM = registerItem("stark_sorting_station",
			new BlockItem(STATION, new Item.Properties().rarity(Rarity.UNCOMMON)) {
				@Override
				public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
					tooltip.add(Component.translatable("item.projecthero.stark_sorting_station.desc1").withStyle(ChatFormatting.GRAY));
					tooltip.add(Component.translatable("item.projecthero.stark_sorting_station.desc2").withStyle(ChatFormatting.DARK_AQUA));
					tooltip.add(Component.translatable("item.projecthero.stark_sorting_station.desc3").withStyle(ChatFormatting.DARK_AQUA));
				}
			});

	public static final BlockEntityType<SortingStationBlockEntity> STATION_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("stark_sorting_station"),
			BlockEntityType.Builder.of(SortingStationBlockEntity::new, STATION).build(null));

	public static final MenuType<SortingStationMenu> STATION_MENU = Registry.register(BuiltInRegistries.MENU,
			ProjectHeroMod.id("stark_sorting_station"),
			new ExtendedScreenHandlerType<>(SortingStationMenu::new, BlockPos.STREAM_CODEC));

	public static final EntityType<SorterBotEntity> BOT = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("stark_sorter_bot")),
			EntityType.Builder.<SorterBotEntity>of(SorterBotEntity::new, MobCategory.MISC)
					.sized(0.6f, 0.9f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.fireImmune()
					.build("stark_sorter_bot"));

	private StarkSorter() {
	}

	/** Referencing this class registers everything above. */
	public static void initialize() {
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
