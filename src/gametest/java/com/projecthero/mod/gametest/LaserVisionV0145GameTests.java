package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.14.5 Laser Vision rework. */
public class LaserVisionV0145GameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(LaserVisionHandlers.KEY) != null, "Laser Vision is registered");
		helper.succeed();
	}
}
