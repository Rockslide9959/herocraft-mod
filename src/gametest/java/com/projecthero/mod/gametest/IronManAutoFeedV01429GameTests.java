package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManAutoFeed;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** v0.14.29: the suit auto-feed accepts any safe food, refuses risky food, hands containers back, and runs on every suit that has it. */
public class IronManAutoFeedV01429GameTests implements FabricGameTest {

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		for (ArmorItem.Type t : ArmorItem.Type.values()) {
			var item = IronManItems.armor(suitId, t);
			if (item != null) {
				p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(item));
			}
		}
		IronManEnergy.setEnergy(p, suitId, IronManEnergy.capacity(suitId));
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoFeedPicksSafeFoodOnly(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		p.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 4));
		p.getInventory().add(new ItemStack(Items.BEEF, 4));
		p.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 1));
		p.getInventory().add(new ItemStack(Items.SPIDER_EYE, 1));
		p.getFoodData().setFoodLevel(10);
		h.assertFalse(IronManAutoFeed.feedNow(p), "risky / raw food must never be auto-eaten");
		p.getInventory().add(new ItemStack(Items.APPLE, 2));
		p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 2));
		h.assertTrue(IronManAutoFeed.feedNow(p), "safe food should be eaten");
		h.assertTrue(p.getFoodData().getFoodLevel() == 18, "most filling fitting food (cooked beef, 8) eaten, got " + p.getFoodData().getFoodLevel());
		h.assertTrue(p.getInventory().countItem(Items.COOKED_BEEF) == 1, "one cooked beef consumed");
		h.assertTrue(p.getInventory().countItem(Items.GOLDEN_APPLE) == 1, "golden apple untouched");
		h.succeed();
	}

	/** v0.14.30: no waste rule -- at 19/20 the suit still feeds the best food it can find. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void autoFeedEatsTheBestFoodWheneverNotFull(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		p.getInventory().add(new ItemStack(Items.APPLE, 2));
		p.getInventory().add(new ItemStack(Items.BREAD, 2));
		p.getInventory().add(new ItemStack(Items.COOKED_PORKCHOP, 2));
		p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 2));
		p.getFoodData().setFoodLevel(19);
		h.assertTrue(IronManAutoFeed.feedNow(p), "19/20 is hungry enough to eat");
		h.assertTrue(p.getFoodData().getFoodLevel() == 20, "hunger topped up, got " + p.getFoodData().getFoodLevel());
		// cooked porkchop and steak tie on nutrition (8) and saturation, so exactly one of them went
		int meat = p.getInventory().countItem(Items.COOKED_PORKCHOP) + p.getInventory().countItem(Items.COOKED_BEEF);
		h.assertTrue(meat == 3, "the best food (an 8-hunger meat) was eaten, not the apple or bread");
		h.assertTrue(p.getInventory().countItem(Items.APPLE) == 2 && p.getInventory().countItem(Items.BREAD) == 2, "lesser food untouched");
		h.assertFalse(IronManAutoFeed.feedNow(p), "a full bar eats nothing");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoFeedReturnsBowls(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		p.getInventory().add(new ItemStack(Items.MUSHROOM_STEW));
		p.getFoodData().setFoodLevel(5);
		h.assertTrue(IronManAutoFeed.feedNow(p), "stew should be eaten");
		h.assertTrue(p.getInventory().countItem(Items.BOWL) == 1, "bowl handed back");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyAutoFeedSuitFeedsThroughTheTicker(GameTestHelper h) {
		for (var suit : IronManSuits.all()) {
			ServerPlayer p = suited(h, suit.id());
			p.getInventory().add(new ItemStack(Items.BREAD, 3));
			p.getFoodData().setFoodLevel(4);
			for (int i = 0; i < 41; i++) {
				p.tickCount++;
				IronManSuitTicker.tick(p);
			}
			boolean ate = p.getFoodData().getFoodLevel() > 4;
			h.assertTrue(ate == suit.autoFeed(), suit.id() + ": autoFeed=" + suit.autoFeed() + " but ate=" + ate);
			p.discard();
		}
		h.succeed();
	}

	/** v0.14.29: four loose Fabricator-made Mark 5 pieces pack into a suitcase on right-click (C never deploys the Mark 5). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void looseMarkFivePiecesPackIntoACase(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		TonyStark.grant(p);
		p.getInventory().clearContent();
		for (ArmorItem.Type t : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS}) {
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_v", t)));
		}
		h.assertFalse(com.projecthero.mod.ironman.item.IronManArmorItem.packLooseMarkV(p), "three pieces must not pack");
		p.getInventory().add(new ItemStack(IronManItems.armor("mark_v", ArmorItem.Type.BOOTS)));
		h.assertTrue(com.projecthero.mod.ironman.item.IronManArmorItem.packLooseMarkV(p), "four pieces pack");
		h.assertTrue(p.getInventory().countItem(IronManItems.MARK_V_SUITCASE) == 1, "one case made");
		for (ArmorItem.Type t : ArmorItem.Type.values()) {
			var it = IronManItems.armor("mark_v", t);
			if (it != null) {
				h.assertTrue(p.getInventory().countItem(it) == 0, "loose " + t + " moved into the case");
			}
		}
		ItemStack c = ItemStack.EMPTY;
		for (ItemStack s : p.getInventory().items) if (s.is(IronManItems.MARK_V_SUITCASE)) c = s;
		h.assertTrue(com.projecthero.mod.ironman.item.SuitcaseContents.nonEmpty(c).size() == 4, "case holds all four pieces");
		h.succeed();
	}
}
