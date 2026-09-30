package com.projecthero.mod.gametest;

import java.util.Random;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.item.RandomPowerSerumItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** v0.13.18: the Prismatic / Mutagenic / Heroic serums grant a random power the drinker does not already have. */
public class RandomPowerSerumGameTests implements FabricGameTest {
	private static ServerPlayer fresh(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		HeroTiers.wipeAll(p);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mutagenicSerumGrantsANewExperimentalPower(GameTestHelper helper) {
		ServerPlayer p = fresh(helper);
		int before = PowerGrants.missingExperimental(p).size();
		Component got = RandomPowerSerumItem.grantRandom(p, RandomPowerSerumItem.Pool.EXPERIMENTAL, new Random(1));
		helper.assertTrue(got != null, "a mutation was granted");
		helper.assertTrue(PowerGrants.missingExperimental(p).size() == before - 1, "exactly one new Experimental power");
		helper.assertFalse(HeroTiers.hasHeroTier(p), "and no Hero-Tier power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mutagenicSerumStopsWhenThereIsNoRoom(GameTestHelper helper) {
		ServerPlayer p = fresh(helper);
		int capacity = ExperimentalPowers.capacity();
		for (int i = 0; i < capacity; i++) {
			helper.assertTrue(RandomPowerSerumItem.grantRandom(p, RandomPowerSerumItem.Pool.EXPERIMENTAL, new Random(i)) != null,
					"serum " + (i + 1) + " of " + capacity + " grants a power");
		}
		helper.assertTrue(RandomPowerSerumItem.grantRandom(p, RandomPowerSerumItem.Pool.EXPERIMENTAL, new Random(99)) == null,
				"with every mutation slot full nothing is granted (so the serum is kept)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heroicSerumGrantsAHeroTierPower(GameTestHelper helper) {
		ServerPlayer p = fresh(helper);
		int missingBefore = PowerGrants.missingHeroTiers(p).size();
		Component got = RandomPowerSerumItem.grantRandom(p, RandomPowerSerumItem.Pool.HERO, new Random(7));
		helper.assertTrue(got != null, "a Hero-Tier power was granted");
		helper.assertTrue(PowerGrants.missingHeroTiers(p).size() < missingBefore, "the player now holds one they did not before");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void prismaticSerumGrantsSomethingNew(GameTestHelper helper) {
		ServerPlayer p = fresh(helper);
		int before = PowerGrants.missingExperimental(p).size() + PowerGrants.missingHeroTiers(p).size();
		Component got = RandomPowerSerumItem.grantRandom(p, RandomPowerSerumItem.Pool.ANY, new Random(3));
		helper.assertTrue(got != null, "some power was granted");
		int after = PowerGrants.missingExperimental(p).size() + PowerGrants.missingHeroTiers(p).size();
		// Thor is the one grant that is not "held" yet: it makes you worthy, and you become Thor when you lift Mjolnir
		boolean thor = got.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
				&& "projecthero.hero_tier.thor".equals(tc.getKey());
		helper.assertTrue(after < before || thor, "the pool of powers they lack shrank (" + before + " -> " + after + ", got "
				+ got.getString() + ")");
		helper.succeed();
	}
}
