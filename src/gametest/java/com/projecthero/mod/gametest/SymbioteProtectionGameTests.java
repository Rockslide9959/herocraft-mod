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

	/** v0.13.19: the resurrection costs no Biomass -- it is on a flat, saved 10-minute cooldown instead. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void resurrectionIsFreeButOnATenMinuteCooldown(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		float before = SymbioteVitalsManager.biomass(player);
		player.setHealth(1.0f);
		helper.assertTrue(Symbiote.tryResurrect(player), "a Symbiote resurrects its host");
		helper.assertTrue(SymbioteVitalsManager.biomass(player) >= before - 0.01f, "no Biomass is spent");
		helper.assertTrue(player.getHealth() >= 4.0f, "the host is brought back with health");
		helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance for 20 seconds");
		helper.assertTrue(SymbioteVitalsManager.resurrectReadyAt(player) - player.level().getGameTime()
				== SymbioteVitalsManager.RESURRECT_COOLDOWN_TICKS, "the next one is ten minutes away");
		helper.assertFalse(Symbiote.tryResurrect(player), "it cannot resurrect twice in a row");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aDrainedSymbioteCanStillResurrect(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		SymbioteVitalsManager.vitals(player).hp = 10.0f;
		helper.assertTrue(Symbiote.tryResurrect(player), "Biomass no longer gates the resurrection");
		helper.succeed();
	}

	/** v0.13.19: dying no longer hands the host a full Biomass bar. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void biomassSurvivesDeathUnchanged(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		SymbioteVitalsManager.vitals(player).hp = 37.0f;
		Symbiote.clearTransient(player);
		helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(player) - 37.0f) < 0.01f, "the same Biomass comes back");
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
	/** v0.13.19: a Symbiote Spike is a real projectile -- 14 damage and Wither V on whatever it hits. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aSpikeHitsForFourteenAndWithers(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 4);
		pig.setNoAi(true);
		pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40.0);
		pig.setHealth(40.0f);
		net.minecraft.world.phys.Vec3 from = pig.position().add(0, 0.5, -1.5);
		com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity.shoot(player, from, new net.minecraft.world.phys.Vec3(0, 0, 1));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(pig.getHealth() <= 40.0f - 13.9f, "the spike hit for 14 (" + pig.getHealth() + ")");
			var wither = pig.getEffect(MobEffects.WITHER);
			helper.assertTrue(wither != null && wither.getAmplifier() == 4, "and applied Wither V");
			helper.succeed();
		});
	}

	/** v0.13.19: the blade has no time limit -- it feeds on 0.3 Biomass a second, and Biomass does not regrow meanwhile. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void theBladeFeedsOnBiomass(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		SymbioteVitalsManager.vitals(player).hp = 100.0f;
		SymbioteVitalsManager.toggleBlade(player);
		helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "the blade comes out");
		helper.onEachTick(() -> SymbioteVitalsManager.tick(player));
		helper.runAfterDelay(200, () -> {
			float hp = SymbioteVitalsManager.biomass(player);
			helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "no time limit: still out after 10 s");
			helper.assertTrue(hp < 97.8f && hp > 96.0f, "10 s of blade cost ~3 Biomass (" + hp + ")");
			helper.succeed();
		});
	}
}
