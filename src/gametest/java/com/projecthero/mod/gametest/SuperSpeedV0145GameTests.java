package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.14.5 Super Speed rework. */
public class SuperSpeedV0145GameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(SuperSpeedHandlers.KEY) != null, "Super Speed is registered");
		helper.succeed();
	}
}
