package com.projecthero.mod.sentry.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.sentry.block.SentryBlocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.17.5 Sentry's items: the Sentry Serum (only from the O.X.E. vat in The Vault, never crafted, never in other loot), and (v0.17.5
 * rework) the craftable Sentry Suit ({@link SentrySuitItem}: top + trousers).
 */
public final class SentryItems {
	public static Item SENTRY_SERUM;

	private SentryItems() {
	}

	public static void initialize() {
		SENTRY_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("sentry_serum"),
				new SentrySerumItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
		SentrySuitItem.register();
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(SENTRY_SERUM);
		output.accept(SentryBlocks.SERUM_VAT_ITEM);
		output.accept(SentrySuitItem.TOP);
		output.accept(SentrySuitItem.TROUSERS);
	}
}
