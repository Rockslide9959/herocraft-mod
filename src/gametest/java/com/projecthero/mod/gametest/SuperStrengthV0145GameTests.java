package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.14.5 Super Strength rework. */
public class SuperStrengthV0145GameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(SuperStrengthHandlers.KEY) != null, "Super Strength is registered");
		helper.succeed();
	}
}
