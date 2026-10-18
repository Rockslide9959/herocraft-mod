package com.projecthero.mod.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.16.7: the Overhaul Quirk (STUB -- the kit agent adds one test per move). */
public class QuirkOverhaulGameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void overhaulKitRegistered(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.quirk.QuirkKits.of(com.projecthero.mod.quirk.Quirk.OVERHAUL) != null, "kit");
		helper.succeed();
	}
}
