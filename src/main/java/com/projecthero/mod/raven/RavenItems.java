package com.projecthero.mod.raven;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.3 Raven: her items.
 * <ul>
 *   <li>{@link #BOOK_OF_AZARATH} ({@code projecthero:book_of_azarath}): read it at night to become Raven ({@link RavenLearning}). Rare in
 *   ancient city and stronghold library chests.</li>
 * </ul>
 * (The craftable Raven's Cloak was removed at the user's request on 2026-11-12; the old {@code projecthero:raven_cloak} id loads as
 * air in saved worlds. Trigon's Wrath still draws its own black hood and cape, {@code client.raven.RavenCloakLayer}.)
 */
public final class RavenItems {
	public static Item BOOK_OF_AZARATH;

	private RavenItems() {
	}

	public static void initialize() {
		if (BOOK_OF_AZARATH != null) {
			return;
		}
		BOOK_OF_AZARATH = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("book_of_azarath"),
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
					@Override
					public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
						tooltip.add(Component.translatable("item.projecthero.book_of_azarath.tooltip").withStyle(ChatFormatting.DARK_PURPLE));
					}

					@Override
					public boolean isFoil(ItemStack stack) {
						return true;
					}
				});
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(BOOK_OF_AZARATH);
	}
}
