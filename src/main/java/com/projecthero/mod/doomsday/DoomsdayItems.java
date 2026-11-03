package com.projecthero.mod.doomsday;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.block.DoomsdayBlocks;
import com.projecthero.mod.doomsday.entity.DoomsdayEntityTypes;
import com.projecthero.mod.doomsday.item.BoneGauntletItem;
import com.projecthero.mod.doomsday.item.PhantomZoneProjectorItem;

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
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.2: Doomsday's items.
 * <ul>
 *   <li>{@link #CALATONIAN_CASKET} -- starts the event where it is placed and opened (an expensive late-game recipe).</li>
 *   <li>{@link #PHANTOM_SHARD} -- a Phantom Zone crystal shard; one falls from each of his first three deaths.</li>
 *   <li>{@link #PROJECTOR} -- the Phantom Zone Projector (three shards): banishes his corpse after the third death.</li>
 *   <li>{@link #BONE_GAUNTLET} -- first-clear weapon that adapts to what it kills ({@link BoneGauntletItem}).</li>
 *   <li>{@link #SKULL} -- Doomsday's Skull, the trophy.</li>
 *   <li>A spawn egg.</li>
 * </ul>
 */
public final class DoomsdayItems {
	public static Item CALATONIAN_CASKET;
	public static Item SKULL;
	public static Item PHANTOM_SHARD;
	public static Item PROJECTOR;
	public static Item BONE_GAUNTLET;
	public static Item SPAWN_EGG;

	private DoomsdayItems() {
	}

	public static void initialize() {
		CALATONIAN_CASKET = blockItem("calatonian_casket", new BlockItem(DoomsdayBlocks.CALATONIAN_CASKET,
				new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.calatonian_casket.hint").withStyle(ChatFormatting.DARK_RED));
				tooltip.add(Component.translatable("block.projecthero.calatonian_casket.hint2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		SKULL = blockItem("doomsday_skull", new BlockItem(DoomsdayBlocks.SKULL, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.doomsday_skull.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		PHANTOM_SHARD = register("phantom_zone_shard", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("item.projecthero.phantom_zone_shard.hint").withStyle(ChatFormatting.AQUA));
			}

			@Override
			public boolean isFoil(ItemStack stack) {
				return true;
			}
		});
		PROJECTOR = register("phantom_zone_projector", new PhantomZoneProjectorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
				.fireResistant()));
		BONE_GAUNTLET = register("doomsday_bone_gauntlet", new BoneGauntletItem(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC)
				.fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.6f))));
		SPAWN_EGG = register("doomsday_spawn_egg", new SpawnEggItem(DoomsdayEntityTypes.DOOMSDAY, 0x6E7178, 0xC7BFA8, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(CALATONIAN_CASKET);
		output.accept(PHANTOM_SHARD);
		output.accept(PROJECTOR);
		output.accept(BONE_GAUNTLET);
		output.accept(SKULL);
		output.accept(SPAWN_EGG);
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
