package com.herocraft.mod.gametest;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.EventInstance;
import com.herocraft.mod.event.EventTypes;
import com.herocraft.mod.event.boss.BossPowers;
import com.herocraft.mod.event.entity.EmpoweredZombie;
import com.herocraft.mod.event.entity.RaidEntityTypes;
import com.herocraft.mod.event.entity.SupervillainVariant;
import com.herocraft.mod.event.raid.SupervillainRaid;
import com.herocraft.mod.event.raid.SupervillainRaidWaves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * Regression coverage for the Supervillain Village Raid: the event type is registered and round-trips,
 * the six-wave table is well formed, model selection is distributed and independent of power, and the
 * boss configures with a Supervillain variant + a boss-capable power.
 */
public class SupervillainRaidGameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void eventTypeRegisteredAndRoundTrips(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(SupervillainRaid.TYPE_ID),
				"supervillain_raid event type must be registered");
		EventInstance instance = EventTypes.create(SupervillainRaid.TYPE_ID, UUID.randomUUID());
		helper.assertTrue(instance instanceof SupervillainRaid, "factory must build a SupervillainRaid");
		helper.assertTrue(instance.typeId().equals(SupervillainRaid.TYPE_ID), "typeId must match");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveTableIsWellFormed(GameTestHelper helper) {
		helper.assertTrue(SupervillainRaidWaves.count() == 6, "must be 6 waves, got " + SupervillainRaidWaves.count());
		for (int w = 1; w <= 5; w++) {
			helper.assertTrue(!SupervillainRaidWaves.get(w).spawns().isEmpty(),
					"raider wave " + w + " must spawn something");
			helper.assertFalse(SupervillainRaidWaves.get(w).bossWave(), "wave " + w + " is not the boss wave");
		}
		helper.assertTrue(SupervillainRaidWaves.get(6).bossWave(), "wave 6 is the boss wave");
		helper.assertTrue(SupervillainRaidWaves.get(6).spawns().isEmpty(),
				"wave 6 spawns no raiders from the table (the raid spawns the villain itself)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void modelSelectionIsDistributedAndPowerIndependent(GameTestHelper helper) {
		RandomSource random = RandomSource.create(1234L);
		Map<SupervillainVariant, Integer> counts = new EnumMap<>(SupervillainVariant.class);
		for (SupervillainVariant v : SupervillainVariant.values()) {
			counts.put(v, 0);
		}
		int trials = 3000;
		for (int i = 0; i < trials; i++) {
			SupervillainVariant v = SupervillainVariant.random(random);
			counts.merge(v, 1, Integer::sum);
			// The power roll must never depend on the variant -- it is a separate registry call.
			String power = BossPowers.randomSupervillainKey(random);
			helper.assertTrue(BossPowers.isEligible(power), "rolled power must be boss-capable: " + power);
		}
		for (SupervillainVariant v : SupervillainVariant.values()) {
			int c = counts.get(v);
			helper.assertTrue(c > trials / 6 && c < trials / 2,
					v + " should be roughly a third of picks, got " + c + "/" + trials);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossConfiguresAsSupervillain(GameTestHelper helper) {
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(boss != null, "boss entity must create");
		BlockPos p = helper.absolutePos(new BlockPos(2, 2, 2));
		boss.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
		boss.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(p),
				net.minecraft.world.entity.MobSpawnType.EVENT, null);

		String power = BossPowers.randomSupervillainKey(helper.getLevel().random);
		boss.configureAsSupervillain(SupervillainVariant.ARSENAL, power, 3);

		helper.assertTrue(boss.variant() == SupervillainVariant.ARSENAL, "variant stored");
		helper.assertTrue(boss.powerKey().equals(power), "power stored");
		helper.assertTrue(boss.isFinalBoss(), "supervillain runs with final-boss behaviour");

		EventConfig.SupervillainRaid cfg = EventConfig.supervillain();
		double expected = cfg.bossBaseHealth + cfg.bossHealthPerAdditionalPlayer * 2;
		helper.assertTrue(Math.abs(boss.getMaxHealth() - expected) < 1.0,
				"3-player health should be " + expected + ", got " + boss.getMaxHealth());
		helper.assertTrue(Math.abs(boss.abilityDamageScale() - cfg.bossAbilityDamageScale) < 0.01f,
				"boss ability damage scale comes from config");
		helper.assertTrue(Math.abs(boss.getAttribute(
				net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getBaseValue()
				- cfg.bossKnockbackResistance) < 0.01, "knockback resistance from config");
		boss.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pillagerSpyIsNotPersistentAndKnowsVillages(GameTestHelper helper) {
		var spy = RaidEntityTypes.PILLAGER_SPY.create(helper.getLevel());
		helper.assertTrue(spy != null, "spy entity must create");
		helper.assertTrue(spy.removeWhenFarAway(0.0), "the spy is a non-persistent rare spawn");
		helper.assertFalse(com.herocraft.mod.event.entity.PillagerSpy.insideVillage(helper.getLevel(),
				helper.absolutePos(BlockPos.ZERO)), "empty test level is not a village");
		spy.discard();
		helper.succeed();
	}
}
