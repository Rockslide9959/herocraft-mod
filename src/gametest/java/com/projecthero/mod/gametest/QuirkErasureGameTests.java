package com.projecthero.mod.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.16.7: the Erasure Quirk (STUB -- the kit agent adds one test per move). */
public class QuirkErasureGameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void erasureKitRegistered(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.quirk.QuirkKits.of(com.projecthero.mod.quirk.Quirk.ERASURE) != null, "kit");
		helper.succeed();
	}
}
