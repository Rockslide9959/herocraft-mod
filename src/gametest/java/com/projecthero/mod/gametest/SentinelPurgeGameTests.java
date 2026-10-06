package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.sentinel.SentinelConfig;
import com.projecthero.mod.sentinel.SentinelPurge;
import com.projecthero.mod.sentinel.SentinelPurgeEvent;
import com.projecthero.mod.sentinel.SentinelRewards;
import com.projecthero.mod.sentinel.SentinelSpawner;
import com.projecthero.mod.sentinel.SentinelTargets;
import com.projecthero.mod.sentinel.entity.MasterMoldEntity;
import com.projecthero.mod.sentinel.entity.SentinelDroneEntity;
import com.projecthero.mod.sentinel.entity.SentinelEntity;
import com.projecthero.mod.sentinel.entity.SentinelEntityTypes;
import com.projecthero.mod.sentinel.item.SentinelItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.1: the Sentinel Purge. The full timed purge is never run (its arrivals would land in the neighbouring tests):
 * each test drives one piece directly, synchronously, inside its own 8x8x8 space -- arrivals are pinned to 1.5 blocks
 * from the centre and 1 block up with the event's test overrides, and every purge is aborted (or completed) and removed
 * before the test ends. Tests that start a purge each get their own batch, since only one purge may run within 256
 * blocks of another. Monster subclasses need Normal difficulty; mock players always report creative, so the purge and
 * the robots check abilities instead and a survival mock player counts as a real fighter.
 */
public class SentinelPurgeGameTests implements FabricGameTest {

	private static ServerPlayer survivor(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static SentinelPurgeEvent startPinned(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		SentinelPurgeEvent purge = SentinelPurge.start(helper.getLevel(), at, null);
		helper.assertTrue(purge != null, "the purge starts");
		purge.arrivalRadiusOverride = 1.5;
		purge.arrivalHeightOverride = 1.0;
		return purge;
	}

	private static void end(ServerLevel level, SentinelPurgeEvent purge) {
		if (purge != null) {
			purge.abort(level);
			EventSavedData.get(level).remove(purge.id());
		}
	}

	// ---------------------------------------------------------------- registration and stats

	@GameTest(template = EMPTY_STRUCTURE)
	public void sentinelPurgeIsARegisteredEventType(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(SentinelPurgeEvent.TYPE_ID), "sentinel_purge is registered with the event framework");
		helper.assertTrue(EventTypes.create(SentinelPurgeEvent.TYPE_ID, UUID.randomUUID()) instanceof SentinelPurgeEvent,
				"a saved purge loads back as a SentinelPurgeEvent");
		for (String r : new String[] { "trask_signal", "trask_signal_from_circuitry" }) {
			helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(r)).isPresent(), "recipe " + r);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void robotsHaveTheirConfiguredStatsAndSizes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SentinelDroneEntity drone = SentinelEntityTypes.SENTINEL_DRONE.create(level);
		SentinelEntity sentinel = SentinelEntityTypes.SENTINEL.create(level);
		MasterMoldEntity mold = SentinelEntityTypes.MASTER_MOLD.create(level);
		helper.assertTrue(drone.getMaxHealth() == (float) SentinelConfig.drone().health, "drone health " + drone.getMaxHealth());
		helper.assertTrue(sentinel.getMaxHealth() == (float) SentinelConfig.sentinel().health, "sentinel health " + sentinel.getMaxHealth());
		helper.assertTrue(sentinel.getAttributeValue(Attributes.ARMOR) == SentinelConfig.sentinel().armor, "sentinel armour");
		helper.assertTrue(sentinel.getAttributeValue(Attributes.SCALE) == SentinelEntity.SCALE, "the Sentinel is scaled up");
		helper.assertTrue(Math.abs(sentinel.getBbHeight() - 3.5f) < 0.05f, "a Sentinel stands ~3.5 blocks, got " + sentinel.getBbHeight());
		helper.assertTrue(drone.getBbHeight() < 1.0f, "a drone is small, got " + drone.getBbHeight());
		helper.assertTrue(Math.abs(mold.getBbHeight() - 11.0f) < 0.1f, "Master Mold stands ~11 blocks, got " + mold.getBbHeight());
		mold.configure(1);
		helper.assertTrue(mold.getMaxHealth() == (float) SentinelConfig.masterMold().health && mold.getHealth() == mold.getMaxHealth(),
				"Master Mold solo health " + mold.getMaxHealth());
		mold.configure(3);
		helper.assertTrue(Math.abs(mold.getMaxHealth() - (SentinelConfig.masterMold().health + 2 * SentinelConfig.masterMold().healthPerExtraFighter)) < 0.5,
				"more fighters, more health: " + mold.getMaxHealth());
		mold.configure(50);
		helper.assertTrue(mold.getMaxHealth() <= SentinelConfig.masterMold().maxHealth, "capped, got " + mold.getMaxHealth());
		helper.assertTrue(SentinelConfig.masterMold().health >= 1000 && SentinelConfig.masterMold().health <= 3000,
				"in the horde / Darkseid boss bracket");
		helper.assertTrue(sentinel.isAlliedTo(mold) && drone.isAlliedTo(sentinel), "the robots are on one side");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wavesStartWithDronesAndGrow(GameTestHelper helper) {
		helper.assertTrue(SentinelPurgeEvent.waveCount() == 4, "four waves by default, got " + SentinelPurgeEvent.waveCount());
		int[] w1 = SentinelPurgeEvent.composition(1);
		helper.assertTrue(w1[0] > 0 && w1[1] == 0, "wave 1 is all drones");
		int prevSentinels = 0;
		for (int n = 2; n <= SentinelPurgeEvent.waveCount(); n++) {
			int[] w = SentinelPurgeEvent.composition(n);
			helper.assertTrue(w[1] > prevSentinels, "wave " + n + " brings more Sentinels (" + w[1] + ")");
			prevSentinels = w[1];
		}
		helper.assertTrue(SentinelPurgeEvent.scaled(4, 1) == 4 && SentinelPurgeEvent.scaled(4, 3) == 8, "half again per extra fighter");
		helper.assertTrue(SentinelPurgeEvent.scaled(0, 4) == 0, "an empty entry stays empty");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the event

	@GameTest(template = EMPTY_STRUCTURE, batch = "sentinel_purge_waves", timeoutTicks = 40)
	public void purgeStartsAndItsWavesSpawnTheRightRobots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SentinelPurgeEvent purge = startPinned(helper);
		try {
			helper.assertTrue(purge.state() == EventState.RUNNING, "running at once");
			helper.assertTrue(purge.phase() == SentinelPurgeEvent.Phase.DETECTION, "it opens with the detection countdown, got " + purge.phase());
			helper.assertTrue(SentinelPurge.start(level, helper.absolutePos(new BlockPos(5, 1, 5)), null) == null,
					"a second purge right beside it is refused");
			helper.assertTrue(SentinelPurgeEvent.find(level, purge.id()) == purge, "it can be found by id");

			purge.startWave(level, 1);
			int f = purge.fighterCount(); // leftover survival mock players from earlier batches may count
			int[] live = purge.liveCounts(level);
			int[] w1 = SentinelPurgeEvent.composition(1);
			helper.assertTrue(purge.phase() == SentinelPurgeEvent.Phase.WAVE && purge.wave() == 1, "wave 1 is on");
			helper.assertTrue(live[0] + purge.owed() == SentinelPurgeEvent.scaled(w1[0], f) && live[0] > 0 && live[1] == 0 && live[2] == 0,
					"wave 1: drones only, got " + java.util.Arrays.toString(live) + " for " + f + " fighter(s)");

			purge.debugAdvance(level); // wave 1 cleared
			helper.assertTrue(purge.phase() == SentinelPurgeEvent.Phase.BREATHER, "a breather after a cleared wave");
			helper.assertTrue(purge.liveCounts(level)[0] == 0, "the cleared wave's drones are gone");

			purge.startWave(level, 2);
			int[] w2 = SentinelPurgeEvent.composition(2);
			live = purge.liveCounts(level);
			helper.assertTrue(live[0] + live[1] + purge.owed() == SentinelPurgeEvent.scaled(w2[0], f) + SentinelPurgeEvent.scaled(w2[1], f)
					&& live[1] > 0 && live[0] > 0, "wave 2: drones and Sentinels " + java.util.Arrays.toString(w2) + ", got " + java.util.Arrays.toString(live));
			int arriving = 0;
			for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12))) {
				if (m instanceof SentinelEntity s && purge.ownsEntity(s)) {
					helper.assertTrue(purge.id().equals(s.purgeId()), "each Sentinel carries the purge's id");
					if (s.isArriving() && s.isFlying()) {
						arriving++;
					}
				}
			}
			helper.assertTrue(arriving == live[1], "every Sentinel comes down on its thrusters, got " + arriving + " of " + live[1]);
		} finally {
			List<Mob> spawned = new ArrayList<>(level.getEntitiesOfClass(Mob.class,
					new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12), m -> purge.ownsEntity(m)));
			end(level, purge);
			for (Mob m : spawned) {
				helper.assertTrue(m.isRemoved(), "an aborted purge takes its robots with it");
			}
		}
		helper.assertTrue(SentinelPurgeEvent.find(level, purge.id()) == null, "nothing of it remains");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "sentinel_purge_boss", timeoutTicks = 40)
	public void masterMoldComesLastAndBeatingItPaysTheFighters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer fighter = survivor(helper, new Vec3(1.5, 1, 1.5));
		SentinelPurgeEvent purge = startPinned(helper);
		MasterMoldEntity boss = null;
		try {
			helper.assertTrue(purge.isFighter(fighter.getUUID()), "a survival player nearby is locked in as a fighter");
			purge.startBoss(level);
			boss = purge.boss(level);
			helper.assertTrue(purge.phase() == SentinelPurgeEvent.Phase.BOSS, "the boss phase");
			helper.assertTrue(boss != null && boss.isAlive(), "Master Mold is down here");
			helper.assertTrue(boss.isArriving(), "it descends out of the sky");
			int f = purge.fighterCount();
			helper.assertTrue(boss.getMaxHealth() == (float) SentinelConfig.masterMoldHealthFor(f), "boss health for " + f + " fighter(s), got " + boss.getMaxHealth());
			int[] live = purge.liveCounts(level);
			helper.assertTrue(live[2] == 1 && live[1] == Math.min(SentinelPurgeEvent.scaled(SentinelConfig.waves().bossEscorts, f), SentinelConfig.waves().enemyCap - 1),
					"Master Mold plus its escort, got " + java.util.Arrays.toString(live));

			boss.kill();
			purge.tick(level); // the framework's tick: the boss is dead -> victory
			helper.assertTrue(purge.state() == EventState.COMPLETED, "beating Master Mold wins the purge, got " + purge.state());
			helper.assertTrue(purge.liveCounts(level)[1] == 0, "the escorts are cleaned up");
			int cores = 0;
			int circuitry = 0;
			for (ItemStack s : fighter.getInventory().items) {
				if (s.is(SentinelItems.MASTER_MOLD_CORE)) cores += s.getCount();
				if (s.is(SentinelItems.SENTINEL_CIRCUITRY)) circuitry += s.getCount();
			}
			helper.assertTrue(cores == SentinelConfig.rewards().coresPerFighter, "the fighter got a Master Mold Core, got " + cores);
			helper.assertTrue(circuitry >= SentinelConfig.rewards().circuitryMin, "and Sentinel Circuitry, got " + circuitry);
		} finally {
			if (boss != null && !boss.isRemoved()) {
				boss.discard();
			}
			end(level, purge);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rewardsAlwaysHoldACoreAndCircuitry(GameTestHelper helper) {
		for (int i = 0; i < 20; i++) {
			List<ItemStack> loot = SentinelRewards.roll(helper.getLevel().random, helper.getLevel().registryAccess(), 1);
			int cores = 0, circuitry = 0, iron = 0;
			for (ItemStack s : loot) {
				if (s.is(SentinelItems.MASTER_MOLD_CORE)) cores += s.getCount();
				if (s.is(SentinelItems.SENTINEL_CIRCUITRY)) circuitry += s.getCount();
				if (s.is(Items.IRON_INGOT)) iron += s.getCount();
			}
			helper.assertTrue(cores == 1, "one core, got " + cores);
			helper.assertTrue(circuitry >= 2 && circuitry <= 5, "2-5 circuitry, got " + circuitry);
			helper.assertTrue(iron >= 24, "a pile of iron salvage, got " + iron);
			helper.assertTrue(loot.size() <= 27, "fits an inventory's worth of stacks");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- who the Sentinels hunt

	@GameTest(template = EMPTY_STRUCTURE)
	public void theNaturalTriggerOnlyHuntsPlayersWithPowers(GameTestHelper helper) {
		ServerPlayer p = survivor(helper, new Vec3(2, 1, 2));
		helper.assertTrue(SentinelTargets.classify(p) == SentinelTargets.Threat.HUMAN, "an ordinary player is human");
		helper.assertTrue(SentinelSpawner.chanceFor(p) == 0.0, "the Sentinels never come for an ordinary player");
		helper.assertTrue(PowerGrants.grantExperimental(p, Powers.byKey("power_01_super_strength")), "a mutation");
		helper.assertTrue(SentinelTargets.classify(p) == SentinelTargets.Threat.MUTANT, "a mutation makes you a mutant");
		helper.assertTrue(SentinelSpawner.chanceFor(p) == SentinelConfig.trigger().mutantChancePerMinute, "the mutant chance");
		helper.assertTrue(SentinelConfig.trigger().mutantChancePerMinute > SentinelConfig.trigger().superhumanChancePerMinute
				&& SentinelConfig.trigger().superhumanChancePerMinute > 0, "mutants are hunted hardest, other superhumans less");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sentinelsGoForTheMutantFirst(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer human = survivor(helper, new Vec3(2.5, 1, 3.5));
		ServerPlayer mutant = survivor(helper, new Vec3(6.5, 1, 6.5));
		PowerGrants.grantExperimental(mutant, Powers.byKey("power_02_laser_vision"));
		SentinelEntity s = SentinelEntityTypes.SENTINEL.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		s.moveTo(at.x, at.y, at.z, 0, 0);
		Player pick = SentinelTargets.pickTarget(helper.getLevel(), s, 48);
		helper.assertTrue(pick == mutant, "the farther mutant beats the human beside it, got " + (pick == null ? "nobody" : pick.getName().getString()));
		helper.assertTrue(SentinelTargets.canTarget(human), "a survival player is fair game");
		ServerPlayer creative = survivor(helper, new Vec3(3.5, 1, 2.5));
		creative.setGameMode(GameType.CREATIVE);
		helper.assertFalse(SentinelTargets.canTarget(creative), "a creative player is left alone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void robotsWhosePurgeIsGoneRemoveThemselves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		UUID ghost = UUID.randomUUID();
		SentinelEntity s = SentinelEntityTypes.SENTINEL.create(level);
		SentinelDroneEntity d = SentinelEntityTypes.SENTINEL_DRONE.create(level);
		Vec3 a = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(5.5, 2, 5.5));
		s.moveTo(a.x, a.y, a.z, 0, 0);
		d.moveTo(b.x, b.y, b.z, 0, 0);
		s.bindToPurge(ghost);
		d.bindToPurge(ghost);
		level.addFreshEntity(s);
		level.addFreshEntity(d);
		helper.runAfterDelay(85, () -> {
			helper.assertTrue(s.isRemoved(), "the orphaned Sentinel removed itself");
			helper.assertTrue(d.isRemoved(), "the orphaned drone removed itself");
			helper.succeed();
		});
	}
}
