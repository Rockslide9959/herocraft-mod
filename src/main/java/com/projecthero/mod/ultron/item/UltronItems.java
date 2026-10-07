package com.projecthero.mod.ultron.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.block.UltronBeaconBlock;
import com.projecthero.mod.ultron.block.UltronBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.15.12: the Ultron Uprising's items.
 * <ul>
 *   <li>{@link #ULTRON_BEACON} -- the block that starts it (crafted around a Master Mold Core and an Arc Reactor).</li>
 *   <li>{@link #VIBRANIUM_PLATING} -- the reward material: plates an Iron Man armour piece at a smithing table
 *       ({@link VibraniumPlating}).</li>
 *   <li>{@link #ULTRON_CORE} -- the trophy block.</li>
 *   <li>{@link #MIND_STONE} -- first clear only ({@link MindStoneItem}).</li>
 *   <li>Spawn eggs for the six robots and the relay pylon.</li>
 * </ul>
 */
public final class UltronItems {
	public static Item ULTRON_BEACON;
	public static Item ULTRON_CORE;
	public static Item VIBRANIUM_PLATING;
	public static Item MIND_STONE;
	public static Item DRONE_SPAWN_EGG;
	public static Item SENTINEL_DRONE_SPAWN_EGG;
	public static Item HEAVY_SPAWN_EGG;
	public static Item SNIPER_SPAWN_EGG;
	public static Item PRIME_SPAWN_EGG;
	public static Item SENTRY_SPAWN_EGG;
	public static Item PYLON_SPAWN_EGG;

	private UltronItems() {
	}

	public static void initialize() {
		ULTRON_BEACON = blockItem("ultron_beacon", new BlockItem(UltronBlocks.ULTRON_BEACON, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
				.fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				UltronBeaconBlock.appendTooltip(stack, context, tooltip, flag);
			}
		});
		ULTRON_CORE = blockItem("ultron_core", new BlockItem(UltronBlocks.ULTRON_CORE, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
				.fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.ultron_core.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		VIBRANIUM_PLATING = register("vibranium_plating", new Item(new Item.Properties().stacksTo(64).rarity(Rarity.RARE).fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("item.projecthero.vibranium_plating.hint").withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.translatable("item.projecthero.vibranium_plating.hint2").withStyle(ChatFormatting.DARK_GRAY,
						ChatFormatting.ITALIC));
			}
		});
		MIND_STONE = register("mind_stone", new MindStoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		DRONE_SPAWN_EGG = register("ultron_drone_spawn_egg", new SpawnEggItem(UltronEntityTypes.DRONE, 0x9AA0A8, 0xE01818, new Item.Properties()));
		SENTINEL_DRONE_SPAWN_EGG = register("ultron_sentinel_drone_spawn_egg",
				new SpawnEggItem(UltronEntityTypes.SENTINEL_DRONE, 0x5A5E66, 0xFF3020, new Item.Properties()));
		HEAVY_SPAWN_EGG = register("ultron_heavy_spawn_egg", new SpawnEggItem(UltronEntityTypes.HEAVY, 0x3A3D44, 0xC81010, new Item.Properties()));
		SNIPER_SPAWN_EGG = register("ultron_sniper_spawn_egg", new SpawnEggItem(UltronEntityTypes.SNIPER, 0x6E7480, 0xFF6050, new Item.Properties()));
		PRIME_SPAWN_EGG = register("ultron_prime_spawn_egg", new SpawnEggItem(UltronEntityTypes.PRIME, 0xC0C4CC, 0xFF0000, new Item.Properties()));
		SENTRY_SPAWN_EGG = register("ultron_sentry_spawn_egg", new SpawnEggItem(UltronEntityTypes.SENTRY, 0x2C2E33, 0xFF2010, new Item.Properties()));
		PYLON_SPAWN_EGG = register("ultron_pylon_spawn_egg", new SpawnEggItem(UltronEntityTypes.PYLON, 0x1E2026, 0xB00000, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ULTRON_BEACON);
		output.accept(VIBRANIUM_PLATING);
		output.accept(ULTRON_CORE);
		output.accept(MIND_STONE);
		output.accept(DRONE_SPAWN_EGG);
		output.accept(SENTINEL_DRONE_SPAWN_EGG);
		output.accept(HEAVY_SPAWN_EGG);
		output.accept(SNIPER_SPAWN_EGG);
		output.accept(PYLON_SPAWN_EGG);
		output.accept(PRIME_SPAWN_EGG);
		output.accept(SENTRY_SPAWN_EGG);
	}

	private static Item blockItem(String path, BlockItem item) {
		Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
