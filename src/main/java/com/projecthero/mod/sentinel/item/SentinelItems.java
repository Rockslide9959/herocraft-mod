package com.projecthero.mod.sentinel.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.sentinel.entity.SentinelEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.15.1: the Sentinel Purge's items.
 * <ul>
 *   <li>{@link #TRASK_SIGNAL} -- starts a purge where you stand. Crafted from iron blocks, redstone blocks, a sculk sensor
 *       and an eye of ender (or, for a rematch, Sentinel Circuitry and a redstone block).</li>
 *   <li>{@link #SENTINEL_CIRCUITRY} -- salvage: a reward, and some Sentinels drop it. Crafting material for a new signal.</li>
 *   <li>{@link #MASTER_MOLD_CORE} -- the guaranteed victory trophy (a crafting material).</li>
 *   <li>Spawn eggs for the three robots.</li>
 * </ul>
 */
public final class SentinelItems {
	public static Item TRASK_SIGNAL;
	public static Item SENTINEL_CIRCUITRY;
	public static Item MASTER_MOLD_CORE;
	public static Item SENTINEL_SPAWN_EGG;
	public static Item SENTINEL_DRONE_SPAWN_EGG;
	public static Item MASTER_MOLD_SPAWN_EGG;

	private SentinelItems() {
	}

	public static void initialize() {
		TRASK_SIGNAL = register("trask_signal", new TraskSignalItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
		SENTINEL_CIRCUITRY = register("sentinel_circuitry", material(Rarity.UNCOMMON, 64, "item.projecthero.sentinel_circuitry.hint", false));
		MASTER_MOLD_CORE = register("master_mold_core", material(Rarity.EPIC, 16, "item.projecthero.master_mold_core.hint", true));
		SENTINEL_SPAWN_EGG = register("sentinel_spawn_egg", new SpawnEggItem(SentinelEntityTypes.SENTINEL, 0x642C8C, 0xCC349C, new Item.Properties()));
		SENTINEL_DRONE_SPAWN_EGG = register("sentinel_drone_spawn_egg", new SpawnEggItem(SentinelEntityTypes.SENTINEL_DRONE, 0x642C8C, 0xFF4030, new Item.Properties()));
		MASTER_MOLD_SPAWN_EGG = register("master_mold_spawn_egg", new SpawnEggItem(SentinelEntityTypes.MASTER_MOLD, 0x3A1854, 0xFF60DC, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(TRASK_SIGNAL);
		output.accept(SENTINEL_CIRCUITRY);
		output.accept(MASTER_MOLD_CORE);
		output.accept(SENTINEL_DRONE_SPAWN_EGG);
		output.accept(SENTINEL_SPAWN_EGG);
		output.accept(MASTER_MOLD_SPAWN_EGG);
	}

	private static Item material(Rarity rarity, int stack, String hint, boolean foil) {
		return new Item(new Item.Properties().stacksTo(stack).rarity(rarity).fireResistant()) {
			@Override
			public boolean isFoil(ItemStack s) {
				return foil;
			}

			@Override
			public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		};
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
