package com.projecthero.mod.frostgiant.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;
import com.projecthero.mod.frostgiant.block.FrostGiantBlocks;
import com.projecthero.mod.frostgiant.block.JotunheimRuneStoneBlock;

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
 * v0.16.1: the Frost Giant Assault's items.
 * <ul>
 *   <li>{@link #RUNE_STONE} -- the Jotunheim Rune Stone that starts it.</li>
 *   <li>{@link #RIME_SHARD} -- the reward material: Rimed netherite armour and Rime-touched Thor weapons at a smithing
 *       table ({@link FrostGear}), and the repeat-run Rune Stone recipe.</li>
 *   <li>{@link #JOTUN_SKULL} -- the trophy block.</li>
 *   <li>{@link #CASKET} -- the Casket of Ancient Winters, first clear only ({@link CasketOfAncientWintersItem}).</li>
 *   <li>{@link #ICE_JAVELIN} -- never obtainable: the model the thrown javelins render with.</li>
 *   <li>Spawn eggs for the six units.</li>
 * </ul>
 */
public final class FrostGiantItems {
	public static Item RUNE_STONE;
	public static Item JOTUN_SKULL;
	public static Item RIME_SHARD;
	public static Item CASKET;
	public static Item ICE_JAVELIN;
	public static Item HOUND_SPAWN_EGG;
	public static Item RAIDER_SPAWN_EGG;
	public static Item SHAMAN_SPAWN_EGG;
	public static Item BRUTE_SPAWN_EGG;
	public static Item WARLORD_SPAWN_EGG;
	public static Item LAUFEY_SPAWN_EGG;

	private FrostGiantItems() {
	}

	public static void initialize() {
		RUNE_STONE = blockItem("jotunheim_rune_stone", new BlockItem(FrostGiantBlocks.RUNE_STONE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				JotunheimRuneStoneBlock.appendTooltip(stack, context, tooltip, flag);
			}
		});
		JOTUN_SKULL = blockItem("jotun_skull", new BlockItem(FrostGiantBlocks.JOTUN_SKULL, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.jotun_skull.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		RIME_SHARD = register("rime_shard", new Item(new Item.Properties().stacksTo(64).rarity(Rarity.RARE)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("item.projecthero.rime_shard.hint").withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.translatable("item.projecthero.rime_shard.hint2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		CASKET = register("casket_of_ancient_winters", new CasketOfAncientWintersItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
				.fireResistant()));
		ICE_JAVELIN = register("ice_javelin", new Item(new Item.Properties().stacksTo(1)));
		HOUND_SPAWN_EGG = register("rime_hound_spawn_egg", new SpawnEggItem(FrostGiantEntityTypes.HOUND, 0x3D6FA8, 0xB8ECFF, new Item.Properties()));
		RAIDER_SPAWN_EGG = register("jotun_raider_spawn_egg", new SpawnEggItem(FrostGiantEntityTypes.RAIDER, 0x3D6FA8, 0x8FC4EE, new Item.Properties()));
		SHAMAN_SPAWN_EGG = register("frost_shaman_spawn_egg", new SpawnEggItem(FrostGiantEntityTypes.SHAMAN, 0x2C3A6A, 0xB8ECFF, new Item.Properties()));
		BRUTE_SPAWN_EGG = register("ice_brute_spawn_egg", new SpawnEggItem(FrostGiantEntityTypes.BRUTE, 0x5A8AB8, 0xE8FBFF, new Item.Properties()));
		WARLORD_SPAWN_EGG = register("frost_giant_warlord_spawn_egg", new SpawnEggItem(FrostGiantEntityTypes.WARLORD, 0x2E4E7A, 0xD8B040,
				new Item.Properties()));
		LAUFEY_SPAWN_EGG = register("king_laufey_spawn_egg", new SpawnEggItem(FrostGiantEntityTypes.LAUFEY, 0x1E2C4A, 0xFF2018, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(RUNE_STONE);
		output.accept(RIME_SHARD);
		output.accept(JOTUN_SKULL);
		output.accept(CASKET);
		output.accept(HOUND_SPAWN_EGG);
		output.accept(RAIDER_SPAWN_EGG);
		output.accept(SHAMAN_SPAWN_EGG);
		output.accept(BRUTE_SPAWN_EGG);
		output.accept(WARLORD_SPAWN_EGG);
		output.accept(LAUFEY_SPAWN_EGG);
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
