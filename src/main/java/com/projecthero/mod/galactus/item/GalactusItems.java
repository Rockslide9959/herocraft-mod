package com.projecthero.mod.galactus.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.galactus.block.GalactusBlocks;
import com.projecthero.mod.galactus.block.HeraldsBeaconBlock;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;

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
 * v0.16.1: Galactus: The Devourer's items.
 * <ul>
 *   <li>{@link #HERALDS_BEACON} -- starts it (an expensive late-game recipe).</li>
 *   <li>{@link #NULLIFIER_PART} -- what a destroyed Element Converter drops; the Surfer assembles them.</li>
 *   <li>{@link #ULTIMATE_NULLIFIER} -- the one weapon he fears ({@link UltimateNullifierItem}).</li>
 *   <li>{@link #SURFBOARD} -- the Silver Surfer's Board (first clear; {@link SurfboardItem}).</li>
 *   <li>{@link #POWER_COSMIC} -- the Power Cosmic artifact (first clear; {@link PowerCosmicItem}).</li>
 *   <li>{@link #HELMET_CREST} -- Galactus' Helmet Crest trophy block.</li>
 *   <li>Spawn eggs for the Silver Surfer and a Punisher.</li>
 * </ul>
 */
public final class GalactusItems {
	public static Item HERALDS_BEACON;
	public static Item HELMET_CREST;
	public static Item NULLIFIER_PART;
	public static Item ULTIMATE_NULLIFIER;
	public static Item SURFBOARD;
	public static Item POWER_COSMIC;
	public static Item SURFER_SPAWN_EGG;
	public static Item PUNISHER_SPAWN_EGG;

	private GalactusItems() {
	}

	public static void initialize() {
		HERALDS_BEACON = blockItem("heralds_beacon", new BlockItem(GalactusBlocks.HERALDS_BEACON, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				HeraldsBeaconBlock.appendTooltip(stack, context, tooltip, flag);
			}
		});
		HELMET_CREST = blockItem("galactus_helmet_crest", new BlockItem(GalactusBlocks.HELMET_CREST, new Item.Properties().stacksTo(16)
				.rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.galactus_helmet_crest.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		NULLIFIER_PART = register("ultimate_nullifier_part", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("item.projecthero.ultimate_nullifier_part.hint").withStyle(ChatFormatting.LIGHT_PURPLE));
			}

			@Override
			public boolean isFoil(ItemStack stack) {
				return true;
			}
		});
		ULTIMATE_NULLIFIER = register("ultimate_nullifier", new UltimateNullifierItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
				.fireResistant()));
		SURFBOARD = register("silver_surfer_board", new SurfboardItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		POWER_COSMIC = register("power_cosmic", new PowerCosmicItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		SURFER_SPAWN_EGG = register("silver_surfer_spawn_egg", new SpawnEggItem(GalactusEntityTypes.SILVER_SURFER, 0xD8DEE8, 0x7A5CFF,
				new Item.Properties()));
		PUNISHER_SPAWN_EGG = register("punisher_spawn_egg", new SpawnEggItem(GalactusEntityTypes.PUNISHER, 0x4A2E6A, 0x9A7FD0, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(HERALDS_BEACON);
		output.accept(NULLIFIER_PART);
		output.accept(ULTIMATE_NULLIFIER);
		output.accept(SURFBOARD);
		output.accept(POWER_COSMIC);
		output.accept(HELMET_CREST);
		output.accept(SURFER_SPAWN_EGG);
		output.accept(PUNISHER_SPAWN_EGG);
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
