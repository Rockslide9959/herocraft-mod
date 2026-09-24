package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * v0.11.14: every power is Primary or Secondary. Gaining a Primary power replaces the old one; the
 * Symbiote (the only Secondary) survives only a Spider-Man; and Mjolnir only lifts for a Hero of the
 * Village, who is then bound and loses the effect.
 */
public class PrimaryPowerGameTests implements FabricGameTest {
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gainingAPrimaryPowerReplacesTheOldOne(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		helper.assertTrue(TonyStark.grant(player), "Tony Stark should grant");
		helper.assertTrue(Punisher.grant(player), "the Punisher should now grant instead of being refused");
		helper.assertFalse(TonyStark.hasPower(player), "Tony Stark must be replaced");
		helper.assertTrue(Punisher.hasPower(player), "the Punisher should be held");
		helper.assertTrue(GreenLantern.bond(player), "Green Lantern should bond over the Punisher");
		helper.assertFalse(Punisher.hasPower(player), "the Punisher must be replaced");
		helper.assertTrue(GreenLantern.hasPower(player), "Green Lantern should be held");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aPrimaryPowerReplacesMutations(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Power adhesion = Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY);
		ExperimentalPowers.grant(player, adhesion);
		helper.assertTrue(GreenLantern.bond(player), "Green Lantern should bond over a mutation");
		helper.assertTrue(ExperimentalPowers.state(player).ownedPowers.isEmpty(), "mutations must be replaced");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void symbioteIsReplacedByAnyPrimaryButSpiderMan(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Symbiote.grant(player);
		helper.assertTrue(Symbiote.hasSymbiote(player), "precondition: bonded");
		Punisher.grant(player);
		helper.assertFalse(Symbiote.hasSymbiote(player), "a non-Spider-Man Primary power removes the Symbiote");

		ServerPlayer spider = survivalPlayer(helper);
		Symbiote.grant(spider);
		ExperimentalPowers.grant(spider, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		helper.assertTrue(SpiderMan.evolveFromAdhesion(spider), "Spider-Man should evolve");
		helper.assertTrue(Symbiote.hasSymbiote(spider), "the Symbiote survives becoming Spider-Man");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heroOfTheVillageAscendsAndConsumesTheEffect(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		TonyStark.grant(player);
		helper.assertFalse(Worthiness.wouldAscend(player), "without the effect there is no ascension");
		player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
		helper.assertTrue(Worthiness.wouldAscend(player), "an unworthy Hero of the Village would ascend");
		Worthiness.ascend(player, new net.minecraft.world.item.ItemStack(com.projecthero.mod.item.ModItems.MJOLNIR));
		helper.assertTrue(Worthiness.isWorthy(player), "the player becomes Thor");
		helper.assertFalse(player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "the effect is consumed");
		helper.assertFalse(TonyStark.hasPower(player), "Thor replaces the previous Primary power");
		helper.assertFalse(Worthiness.wouldAscend(player), "an existing Thor does not ascend again");
		helper.succeed();
	}
}
