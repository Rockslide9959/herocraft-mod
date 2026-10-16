package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p05.GeoRockEntity;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;
import com.projecthero.mod.hero.power.p05.GroundType;
import com.projecthero.mod.hero.power.p06.CrystalNodeEntity;
import com.projecthero.mod.hero.power.p09.FrostStacks;
import com.projecthero.mod.hero.power.p09.IceBladeItem;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.revamp.BatchBScheduler;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 mutation revamp, batch B: regression coverage for the reworked Geokinesis, Crystalkinesis, Pyrokinesis,
 * Cryokinesis and Water Manipulation kits -- each power's signature mechanic, its H / N abilities and the entities,
 * items and temporary blocks they create. Mock players are not reliably ticked, so tests drive
 * {@link ExperimentalPowers#serverTick} themselves where a per-tick effect matters; world-level effects
 * (the batch scheduler, crystal nodes) tick with the server.
 */
public class RevampBatchBGameTests implements FabricGameTest {
	private static final String GEO = "power_05_geokinesis";
	private static final String CRYSTAL = "power_06_crystalkinesis";
	private static final String CRYO = "power_09_cryokinesis";
	private static final String WATER = "power_25_water_manipulation";

	/** A survival mock player at (2.5, 2, 2.5) facing +Z, in a cleared room on a floor of {@code floor}. */
	private static ServerPlayer hero(GameTestHelper helper, String key, BlockState floor) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0.0f);
		p.setXRot(0.0f);
		BlockPos base = BlockPos.containing(at);
		ServerLevel level = helper.getLevel();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 4, 8))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, -1, 8))) {
			level.setBlock(pos, floor, 2);
		}
		Power power = Powers.byKey(key);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		return p;
	}

	private static ServerPlayer hero(GameTestHelper helper, String key) {
		return hero(helper, key, Blocks.STONE.defaultBlockState());
	}

	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, int dist) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2 + dist));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		return z;
	}

	private static float res(ServerPlayer p, String key, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(key), name);
	}

	// ---------------- kit shape ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 26, "expected 26 powers (Super Durability removed in v0.14.5)");
		for (String key : new String[] { GEO, CRYSTAL, CRYO, WATER } /* 08 Pyrokinesis: reworked in v0.16.5 (six keys) */) {
		for (String key : new String[] { GEO, CRYSTAL, PYRO, WATER }) { // v0.16.5: Cryokinesis reworked (six keys)
		for (String key : new String[] { CRYSTAL, PYRO, CRYO, WATER }) { // v0.16.5: Geokinesis has six keys now
		for (String key : new String[] { GEO, CRYSTAL, PYRO, CRYO }) { // v0.16.5: Water reworked (WaterV0165GameTests)
			Power p = Powers.byKey(key);
			helper.assertTrue(p.abilities().size() == 8, key + " must define 8 abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, key + " slot " + slot + " mismapped");
				helper.assertTrue(AbilityHandlers.has(p, a), key + "/" + a.id() + " has no handler");
			}
		}
		helper.succeed();
	}

	// 05 Geokinesis: reworked in v0.16.5 -- see GeokinesisV0165GameTests

	// ---------------- 06 Crystalkinesis ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void crystalShardPlantsANodeWhereItLands(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		BlockPos base = p.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, 5), base.offset(2, 3, 5))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		AbilityRouter.handleInput(p, 1, true);
		helper.succeedWhen(() -> helper.assertTrue(!CrystalNodeEntity.owned(p, false).isEmpty(),
				"a crystal node grows where the shard struck"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalShatterDetonatesEveryNode(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		Zombie z = zombieAhead(helper, p, 4);
		ServerLevel level = helper.getLevel();
		CrystalNodeEntity.spawn(level, p, z.position().add(0.8, 0, 0), Direction.UP, false);
		CrystalNodeEntity.spawn(level, p, z.position().add(-0.8, 0, 0), Direction.UP, false);
		helper.assertTrue(CrystalNodeEntity.owned(p, true).size() == 2, "two nodes planted");
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 2, true);
		helper.assertTrue(CrystalNodeEntity.owned(p, true).isEmpty(), "Shatter detonates every node");
		helper.assertTrue(z.getHealth() < before, "the burst hurts what stands by the nodes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalNodeCapCracksTheOldest(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < CrystalNodeEntity.MAX_NODES + 3; i++) {
			CrystalNodeEntity.spawn(level, p, p.position().add(0, 0, 2 + i * 0.3), Direction.UP, false);
		}
		helper.assertTrue(CrystalNodeEntity.owned(p, false).size() == CrystalNodeEntity.MAX_NODES,
				"never more than " + CrystalNodeEntity.MAX_NODES + " nodes, got " + CrystalNodeEntity.owned(p, false).size());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalRefractSplitsANodeIntoThree(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		ServerLevel level = helper.getLevel();
		Vec3 at = p.position().add(0, 0, 4);
		CrystalNodeEntity.spawn(level, p, at, Direction.UP, false);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.add(0, 0.4, 0));
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(CrystalNodeEntity.owned(p, false).size() == 3,
				"the struck node splits into three, got " + CrystalNodeEntity.owned(p, false).size());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalSpireIsASpireNode(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		p.setXRot(40.0f); // aim at the floor ahead
		AbilityRouter.handleInput(p, 7, true);
		var all = CrystalNodeEntity.owned(p, true);
		helper.assertTrue(all.size() == 1 && all.get(0).isSpire(), "Resonance Spire plants a spire");
		helper.assertTrue(CrystalNodeEntity.owned(p, false).isEmpty(), "a spire does not count against the node cap");
		helper.succeed();
	}

	// 08 Pyrokinesis: since the v0.16.5 rework in PyrokinesisV0165GameTests

	// 09 Cryokinesis: reworked in v0.16.5 -- see CryokinesisV0165GameTests

	// ---------------- 25 Water Manipulation (v0.16.5: reworked -- WaterV0165GameTests; the shared scheduler stays here) ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void temporaryWaterDrainsOnItsOwnClock(GameTestHelper helper) {
		ServerPlayer p = hero(helper, WATER);
		BlockPos cell = p.blockPosition().offset(0, 1, 3);
		helper.assertTrue(BatchBScheduler.placeTemporarily(helper.getLevel(), cell, Blocks.WATER.defaultBlockState(), 5),
				"water goes into an air cell");
		helper.assertTrue(helper.getLevel().getBlockState(cell).is(Blocks.WATER), "and is there");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(cell).isAir(), "and drains when its time is up");
			helper.succeed();
		});
	}
}
