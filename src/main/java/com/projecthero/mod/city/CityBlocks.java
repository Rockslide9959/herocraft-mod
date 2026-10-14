package com.projecthero.mod.city;

import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.city.bank.SafeDepositBlock;
import com.projecthero.mod.city.bank.SafeDepositMenu;
import com.projecthero.mod.item.ModCreativeTab;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.16.2 City part B: the City's blocks, items, job-site POI types, villager professions and menu.
 *
 * <p><b>Shared ids (City part A places these in its templates):</b> {@code projecthero:precinct_desk} (City Cop job
 * site), {@code projecthero:repair_bench} (Repair Worker job site), {@code projecthero:safe_deposit} (the bank vault).
 * All three are horizontal blocks with a {@code facing} property. Professions: {@code projecthero:city_cop},
 * {@code projecthero:repair_worker}.
 */
public final class CityBlocks {
	public static final Block PRECINCT_DESK = registerBlock("precinct_desk", new PrecinctDeskBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE).strength(2.5f).sound(SoundType.WOOD).ignitedByLava()));
	public static final Block REPAIR_BENCH = registerBlock("repair_bench", new RepairBenchBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(2.5f).sound(SoundType.WOOD).ignitedByLava()));
	public static final Block SAFE_DEPOSIT = registerBlock("safe_deposit", new SafeDepositBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(5.0f, 1200.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

	public static final Item PRECINCT_DESK_ITEM = registerBlockItem("precinct_desk", PRECINCT_DESK, Rarity.COMMON);
	public static final Item REPAIR_BENCH_ITEM = registerBlockItem("repair_bench", REPAIR_BENCH, Rarity.COMMON);
	public static final Item SAFE_DEPOSIT_ITEM = registerBlockItem("safe_deposit", SAFE_DEPOSIT, Rarity.UNCOMMON);
	public static final Item DAILY_BULLETIN = Registry.register(BuiltInRegistries.ITEM,
			ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("daily_bulletin")), new DailyBulletinItem(new Item.Properties().stacksTo(16)));

	public static final ResourceKey<PoiType> PRECINCT_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ProjectHeroMod.id("precinct_desk"));
	public static final ResourceKey<PoiType> REPAIR_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ProjectHeroMod.id("repair_bench"));

	static {
		PointOfInterestHelper.register(PRECINCT_DESK_POI.location(), 1, 1, PRECINCT_DESK);
		PointOfInterestHelper.register(REPAIR_BENCH_POI.location(), 1, 1, REPAIR_BENCH);
	}

	public static final VillagerProfession CITY_COP = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, ProjectHeroMod.id("city_cop"),
			new VillagerProfession("city_cop", h -> h.is(PRECINCT_DESK_POI), h -> h.is(PRECINCT_DESK_POI), ImmutableSet.of(), ImmutableSet.of(),
					SoundEvents.VILLAGER_WORK_ARMORER));
	public static final VillagerProfession REPAIR_WORKER = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION,
			ProjectHeroMod.id("repair_worker"),
			new VillagerProfession("repair_worker", h -> h.is(REPAIR_BENCH_POI), h -> h.is(REPAIR_BENCH_POI), ImmutableSet.of(), ImmutableSet.of(),
					SoundEvents.VILLAGER_WORK_TOOLSMITH));

	public static final MenuType<SafeDepositMenu> SAFE_DEPOSIT_MENU = Registry.register(BuiltInRegistries.MENU, ProjectHeroMod.id("safe_deposit"),
			new MenuType<>(SafeDepositMenu::new, FeatureFlags.VANILLA_SET));

	private CityBlocks() {
	}

	static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> {
			entries.accept(PRECINCT_DESK_ITEM);
			entries.accept(REPAIR_BENCH_ITEM);
			entries.accept(SAFE_DEPOSIT_ITEM);
			entries.accept(DAILY_BULLETIN);
		});
	}

	public static boolean isPoi(Holder<PoiType> holder, ResourceKey<PoiType> key) {
		return holder.is(key);
	}

	private static Block registerBlock(String path, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static Item registerBlockItem(String path, Block block, Rarity rarity) {
		BlockItem item = new BlockItem(block, new Item.Properties().rarity(rarity)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero." + path + ".desc").withStyle(ChatFormatting.GRAY));
			}
		};
		Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}
}
