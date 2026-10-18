package com.projecthero.mod.gametest;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** v0.16.7: the Quirk core -- grant / revoke, one Primary, the meter (the core agent adds the rest). */
public class QuirkCoreGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	@GameTest(template = EMPTY_STRUCTURE)
	public void grantSwapRevoke(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(Quirks.grant(p, Quirk.HELLFLAME), "granted");
		helper.assertTrue(Quirks.holds(p, Quirk.HELLFLAME), "holds Hellflame");
		helper.assertTrue(!Quirks.grant(p, Quirk.HELLFLAME), "twice refused");
		helper.assertTrue(Quirks.grant(p, Quirk.ENGINE), "swap");
		helper.assertTrue(Quirks.holds(p, Quirk.ENGINE) && !Quirks.holds(p, Quirk.HELLFLAME), "one quirk at a time");
		Quirks.setMeter(p, 140f);
		helper.assertTrue(Quirks.meter(p) == 100f, "meter clamps");
		Quirks.revoke(p);
		helper.assertTrue(!Quirks.hasPower(p), "revoked");
		helper.succeed();
	}
}
