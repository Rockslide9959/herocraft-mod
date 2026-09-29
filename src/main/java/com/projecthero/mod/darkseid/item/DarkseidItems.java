package com.projecthero.mod.darkseid.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;

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
 * The Darkseid Raid's items -- its own registration class, like {@code SupervillainRaidItems}.
 * <ul>
 *   <li>{@link #BOOM_TUBE_BEACON} -- starts the raid. Crafted from Supervillain Tokens and a Nether Star (or, for a
 *       rematch, Omega Shards); never found, never spawns on its own.</li>
 *   <li>{@link #OMEGA_CORE} -- the guaranteed raid reward; crafting material (the Mother Box recipe).</li>
 *   <li>{@link #OMEGA_SHARD} -- the common-ish reward; crafting material (Mother Box, a new beacon).</li>
 *   <li>{@link #MOTHER_BOX} -- very rare; a personal Boom Tube home.</li>
 *   <li>{@link #OMEGA_RELIC} -- extremely rare; a limited-charge Omega Beam.</li>
 * </ul>
 */
public final class DarkseidItems {
	public static Item BOOM_TUBE_BEACON;
	public static Item OMEGA_CORE;
	public static Item OMEGA_SHARD;
	public static Item MOTHER_BOX;
	public static Item OMEGA_RELIC;
	public static Item DARKSEID_SPAWN_EGG;
	public static Item PARADEMON_SPAWN_EGG;

	private DarkseidItems() {
	}

	public static void initialize() {
		BOOM_TUBE_BEACON = register("boom_tube_beacon", new BoomTubeBeaconItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		OMEGA_CORE = register("omega_core", material(Rarity.EPIC, 16, "item.projecthero.omega_core.hint"));
		OMEGA_SHARD = register("omega_shard", material(Rarity.RARE, 64, "item.projecthero.omega_shard.hint"));
		MOTHER_BOX = register("mother_box", new MotherBoxItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		OMEGA_RELIC = register("omega_relic", new OmegaRelicItem(new Item.Properties().durability(OmegaRelicItem.CHARGES)
				.rarity(Rarity.EPIC).fireResistant()));
		DARKSEID_SPAWN_EGG = register("darkseid_spawn_egg", new SpawnEggItem(DarkseidEntityTypes.DARKSEID, 0x3A3A44, 0xC01010, new Item.Properties()));
		PARADEMON_SPAWN_EGG = register("parademon_spawn_egg", new SpawnEggItem(DarkseidEntityTypes.PARADEMON, 0x4A5A3A, 0xD08020, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(BOOM_TUBE_BEACON);
		output.accept(OMEGA_CORE);
		output.accept(OMEGA_SHARD);
		output.accept(MOTHER_BOX);
		output.accept(OMEGA_RELIC);
		output.accept(DARKSEID_SPAWN_EGG);
		output.accept(PARADEMON_SPAWN_EGG);
	}

	private static Item material(Rarity rarity, int stack, String hint) {
		return new Item(new Item.Properties().stacksTo(stack).rarity(rarity).fireResistant()) {
			@Override
			public boolean isFoil(ItemStack s) {
				return true;
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
