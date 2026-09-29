package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityActivation;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerCategory;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.ComboMoves;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** v0.14.1: the mutation revamp's shared framework -- H / N slots, synced visuals, meters and combo moves. */
public class MutationFrameworkGameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void utilitySlotsAreSevenAndEight(GameTestHelper helper) {
		helper.assertTrue(AbilitySlot.byNumber(7) == AbilitySlot.SLOT_7 && AbilitySlot.SLOT_7.isUtility(), "slot 7");
		helper.assertTrue(AbilitySlot.byNumber(8) == AbilitySlot.SLOT_8 && AbilitySlot.SLOT_8.isUtility(), "slot 8");
		helper.assertFalse(AbilitySlot.SLOT_6.isUtility(), "slot 6 is a core slot");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerNeedsBothUtilitySlotsOrNeither(GameTestHelper helper) {
		Power.Builder b = Power.Builder.of(com.projecthero.mod.ProjectHeroMod.id("test_power"), PowerCategory.PHYSICAL);
		for (AbilitySlot s : AbilitySlot.values()) {
			if (s != AbilitySlot.SLOT_8) {
				b.ability(Ability.of("a" + s.number(), s, "k", AbilityActivation.INSTANT, 0));
			}
		}
		boolean threw = false;
		try {
			b.build();
		} catch (IllegalStateException e) {
			threw = true;
		}
		helper.assertTrue(threw, "a power with H but no N must be rejected");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void utilityInputNeverCrashes(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		for (Power pw : Powers.all()) {
			ExperimentalPowers.forget(p, pw);
		}
		Power strength = Powers.byKey("power_01_super_strength");
		ExperimentalPowers.grant(p, strength);
		AbilityRouter.handleInput(p, 7, true);
		AbilityRouter.handleInput(p, 7, false);
		AbilityRouter.handleInput(p, 8, true);
		AbilityRouter.handleInput(p, 8, false);
		AbilityRouter.handleInput(p, 9, true); // out of range: ignored
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void visualsPlayAndStop(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		MutationVisuals.play(p, "punch_right");
		helper.assertTrue("punch_right".equals(MutationVisuals.anim(p)), "anim should be playing");
		MutationVisuals.stopIf(p, "slam_two_hand");
		helper.assertTrue("punch_right".equals(MutationVisuals.anim(p)), "stopIf must not cut a different anim");
		MutationVisuals.stopIf(p, "punch_right");
		helper.assertTrue(MutationVisuals.anim(p).isEmpty(), "anim should have stopped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void overlayFlagsAreEvaluated(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		MutationVisuals.registerFlag("test.always_on", sp -> sp == p);
		for (int i = 0; i < 4; i++) {
			p.tickCount++;
			MutationVisuals.tick(p);
		}
		helper.assertTrue(MutationVisuals.hasFlag(p, "test.always_on"), "a true flag should be synced on");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void meterRegistryLooksUp(GameTestHelper helper) {
		MutationMeters.register("power_test", "fuel", MutationMeters.Kind.RESERVE, "Fuel", 100f, 0xFFFFFFFF, true);
		helper.assertTrue(MutationMeters.get("power_test", "fuel") != null, "registered meter should be found");
		helper.assertTrue(MutationMeters.get("power_test", "other") == null, "unregistered meter must be null");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void comboNeedsAPairAndStartsCooldown(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		helper.assertTrue(ComboMoves.available(p) == null, "no mutations, no combo");
		ExperimentalPowers.grant(p, Powers.byKey("power_08_pyrokinesis"));
		ExperimentalPowers.grant(p, Powers.byKey("power_03_flight"));
		helper.assertTrue("comet_dash".equals(ComboMoves.available(p)), "Pyro + Flight should unlock Comet Dash");
		ComboMoves.tryFire(p);
		helper.assertTrue(ExperimentalPowers.state(p).abilityReadyAt.containsKey("combo/comet_dash"), "cooldown recorded");
		helper.succeed();
	}
}
