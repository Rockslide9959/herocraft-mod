package com.projecthero.mod.city;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;

import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

/**
 * v0.16.2 City part B: trades of the two City professions. The Repair Worker sells building blocks (and buys
 * cobblestone); the City Cop sells patrol gear and, at Master, the Police Scanner that starts a Syndicate Bust.
 */
public final class CityTrades {
	private CityTrades() {
	}

	private static VillagerTrades.ItemListing sell(ItemLike item, int count, int emeralds, int uses, int xp) {
		return (trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), uses, xp, 0.05f);
	}

	private static VillagerTrades.ItemListing buy(ItemLike item, int count, int emeralds, int uses, int xp) {
		return (trader, random) -> new MerchantOffer(new ItemCost(item.asItem(), count), new ItemStack(Items.EMERALD, emeralds), uses, xp, 0.05f);
	}

	static void initialize() {
		TradeOfferHelper.registerVillagerOffers(CityBlocks.REPAIR_WORKER, 1, f -> {
			f.add(buy(Items.COBBLESTONE, 16, 1, 16, 2));
			f.add(sell(Items.STONE_BRICKS, 16, 1, 16, 1));
			f.add(sell(Items.OAK_PLANKS, 16, 1, 16, 1));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.REPAIR_WORKER, 2, f -> {
			f.add(sell(Items.GLASS, 12, 1, 16, 5));
			f.add(sell(Items.BRICKS, 16, 2, 16, 5));
			f.add(sell(Items.SCAFFOLDING, 16, 1, 12, 5));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.REPAIR_WORKER, 3, f -> {
			f.add(sell(Items.POLISHED_ANDESITE, 16, 2, 12, 10));
			f.add(sell(Items.LANTERN, 4, 1, 12, 10));
			f.add(buy(Items.IRON_INGOT, 4, 1, 12, 10));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.REPAIR_WORKER, 4, f -> {
			f.add(sell(Items.SMOOTH_QUARTZ, 12, 3, 12, 15));
			f.add(sell(Items.CUT_COPPER, 8, 3, 12, 15));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.REPAIR_WORKER, 5, f -> {
			f.add(sell(Items.DEEPSLATE_TILES, 16, 3, 12, 30));
			f.add(sell(Items.TUFF_BRICKS, 16, 3, 12, 30));
		});

		TradeOfferHelper.registerVillagerOffers(CityBlocks.CITY_COP, 1, f -> {
			f.add(buy(Items.ROTTEN_FLESH, 24, 1, 16, 2));
			f.add(sell(Items.COOKED_BEEF, 6, 1, 16, 1));
			f.add(sell(Items.BREAD, 6, 1, 16, 1));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.CITY_COP, 2, f -> {
			f.add(buy(Items.STRING, 14, 1, 16, 5));
			f.add(sell(Items.SHIELD, 1, 4, 8, 5));
			f.add(sell(Items.LEAD, 2, 1, 12, 5));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.CITY_COP, 3, f -> {
			f.add(sell(Items.IRON_SWORD, 1, 3, 8, 10));
			f.add(sell(Items.CHAINMAIL_CHESTPLATE, 1, 6, 8, 10));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.CITY_COP, 4, f -> {
			f.add(sell(Items.CROSSBOW, 1, 5, 8, 15));
			f.add(sell(Items.SPYGLASS, 1, 4, 8, 15));
		});
		TradeOfferHelper.registerVillagerOffers(CityBlocks.CITY_COP, 5, f -> {
			f.add((trader, random) -> {
				Item scanner = com.projecthero.mod.syndicate.SyndicateItems.POLICE_SCANNER;
				return scanner == null ? null : new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(scanner), 3, 30, 0.05f);
			});
			f.add(sell(CityBlocks.DAILY_BULLETIN, 1, 1, 16, 30));
		});
	}
}
