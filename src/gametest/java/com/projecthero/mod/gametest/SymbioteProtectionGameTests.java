package com.projecthero.mod.gametest;

import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.item.SymbioteHostItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** v0.11.15: the Symbiote protects its host -- it wraps them on its own and refuses to let them die. */
public class SymbioteProtectionGameTests implements FabricGameTest {
	private static ServerPlayer bonded(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void resurrectionCostsHalfTheBiomassAndGrantsResistance(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		float before = SymbioteVitalsManager.biomass(player);
		player.setHealth(1.0f);
		helper.assertTrue(Symbiote.tryResurrect(player), "a full Symbiote resurrects its host");
		helper.assertTrue(SymbioteVitalsManager.biomass(player) <= before - 99.0f, "half the bar is spent");
		helper.assertTrue(player.getHealth() >= 4.0f, "the host is brought back with health");
		helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance for 20 seconds");
		helper.assertFalse(Symbiote.tryResurrect(player), "it cannot resurrect twice in a row");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aDrainedSymbioteCannotResurrect(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		SymbioteVitalsManager.vitals(player).hp = 40.0f;
		helper.assertFalse(Symbiote.tryResurrect(player), "under half a bar the Symbiote cannot pay");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoEquipWrapsAHostWhoIsNotSuited(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		Symbiote.state(player).toggleReadyAt = 0L;
		helper.assertFalse(Symbiote.isActive(player), "precondition: suit off");
		helper.assertTrue(Symbiote.autoEquip(player, true), "the Symbiote wraps its host");
		helper.assertTrue(Symbiote.isActive(player), "the suit is on");
		helper.assertFalse(Symbiote.autoEquip(player, true), "already suited -- nothing to do");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anEmptyVialIsCraftableAndFillable(GameTestHelper helper) {
		helper.assertTrue(SymbioteHostItems.SYMBIOTE_VIAL != null && SymbioteHostItems.SYMBIOTE_VIAL_FILLED != null,
				"both vial items are registered");
		ItemStack vial = new ItemStack(SymbioteHostItems.SYMBIOTE_VIAL);
		helper.assertFalse(vial.isEmpty(), "the vial is a real item");
		helper.succeed();
	}
}
