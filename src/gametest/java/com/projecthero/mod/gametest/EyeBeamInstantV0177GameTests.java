package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.EyeBeamHits;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "you need to hold the laser vision on the enemy for like a second for it to deal damage ... it should do damage
 * instantly"): held eye beams hit a creature the first tick they touch it ({@link EyeBeamHits}), then at their usual rate.
 */
public class EyeBeamInstantV0177GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aTargetIsHitAtOnceThenAtTheInterval(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Husk a = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 2));
		Husk b = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 2, 2));
		helper.assertTrue(EyeBeamHits.due(p, "test_beam", a, 5), "the first tick on a creature hits");
		helper.assertFalse(EyeBeamHits.due(p, "test_beam", a, 5), "not again in the same tick");
		helper.assertTrue(EyeBeamHits.due(p, "test_beam", b, 5), "a second creature has its own clock and is hit at once too");
		helper.assertTrue(EyeBeamHits.due(p, "other_beam", a, 5), "each beam keeps its own clock");
		helper.runAfterDelay(3, () -> helper.assertFalse(EyeBeamHits.due(p, "test_beam", a, 5), "3 ticks later: still waiting"));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(EyeBeamHits.due(p, "test_beam", a, 5), "5 ticks later: the next hit (rate unchanged)");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void laserVisionHitsTheFirstTickItTouches(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 3, 5))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(1, -1, 5))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		p.setOnGround(true);
		Power power = Powers.byKey(LaserVisionHandlers.KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		Husk target = helper.spawnWithNoFreeWill(EntityType.HUSK, base.offset(0, 0, 3).subtract(helper.absolutePos(BlockPos.ZERO)));
		float full = target.getHealth();
		// off the old 10-tick clock on purpose: the old beam waited for tickCount % 10 == 0
		p.tickCount = 13;
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(LaserVisionHandlers.firing(p), "holding R fires the beam");
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(target.getHealth() < full, "the very first tick on the husk hurts it, got " + target.getHealth() + " of " + full);
		float afterFirst = target.getHealth();
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(target.getHealth() == afterFirst, "and the next tick waits for the interval (rate unchanged)");
		AbilityRouter.handleInput(p, 1, false);
		helper.succeed();
	}
}
