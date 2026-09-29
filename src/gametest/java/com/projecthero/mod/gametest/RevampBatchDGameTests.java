package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Powers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.13.22 mutation revamp, batch D: regression coverage for the reworked kits. */
public class RevampBatchDGameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 27, "expected 27 powers");
		helper.succeed();
	}
}
