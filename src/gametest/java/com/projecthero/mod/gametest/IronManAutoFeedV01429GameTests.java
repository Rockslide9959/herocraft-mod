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
}
