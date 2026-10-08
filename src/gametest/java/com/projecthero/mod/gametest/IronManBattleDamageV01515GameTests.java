package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManDamageTiers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** v0.15.15: the nine Iron Man battle-damage tiers (45 / 40 / ... / 5 %), each strictly worse than the last. */
public class IronManBattleDamageV01515GameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void damageTiersStepEveryFivePercent(GameTestHelper h) {
		h.assertTrue(IronManDamageTiers.tier(1f) == 0, "a full suit is clean");
		h.assertTrue(IronManDamageTiers.tier(0.46f) == 0, "46% is still clean");
		float[] at = { 0.45f, 0.40f, 0.35f, 0.30f, 0.25f, 0.20f, 0.15f, 0.10f, 0.05f };
		for (int i = 0; i < at.length; i++) {
			int want = i + 1;
			h.assertTrue(IronManDamageTiers.tier(at[i]) == want, (int) (at[i] * 100) + "% is tier " + want
					+ ", got " + IronManDamageTiers.tier(at[i]));
			h.assertTrue(IronManDamageTiers.tier(at[i] + 0.01f) == want - 1, "just above " + (int) (at[i] * 100) + "% is tier " + (want - 1));
		}
		h.assertTrue(IronManDamageTiers.tier(0f) == IronManDamageTiers.MAX_TIER, "a wrecked suit is the last tier");
		int last = -1;
		for (int p = 100; p >= 0; p--) {
			int t = IronManDamageTiers.tier(p / 100f);
			h.assertTrue(t >= last, "tiers never get better as integrity drops (" + p + "%)");
			last = t;
		}
		h.assertTrue(IronManDamageTiers.SPARK_TIER == IronManDamageTiers.tier(0.25f), "sparks start at 25%");
		h.assertTrue(IronManDamageTiers.SMOKE_TIER == IronManDamageTiers.tier(0.15f), "smoke starts at 15%");
		h.assertTrue(IronManDamageTiers.MAX_TIER == IronManDamageTiers.tier(0.05f), "the wrecked effects start at 5%");
		h.succeed();
	}
}
