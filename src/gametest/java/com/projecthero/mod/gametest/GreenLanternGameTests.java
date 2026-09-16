package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternFlight;
import com.projecthero.mod.greenlantern.GreenLanternMastery;
import com.projecthero.mod.greenlantern.GreenLanternShield;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.maxsteel.MaxSteel;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Server-side coverage for Green Lantern: cross-tier exclusivity, the Ring Charge pool (clamping,
 * emergency reserve, cooldowns), construct slot-cap accounting, and lifecycle cleanup. Movement,
 * worldgen and the Will Trial's wave flow are gametest-unfriendly (real terrain / real time) and
 * belong to the manual test plan; what is checked here is every rule the server enforces around them.
 */
public class GreenLanternGameTests implements FabricGameTest {

	private static ServerPlayer bonded(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		GreenLantern.bond(player);
		return player;
	}

	// ---------------- usable unsuited ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void ringBoltWorksWithoutTheSuitOn(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		helper.assertFalse(GreenLantern.isSuited(player), "this test needs an unsuited player");
		float before = GreenLantern.state(player).ringCharge;
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		helper.assertTrue(GreenLantern.state(player).ringCharge == before - GreenLanternConfig.BOLT_COST,
				"Ring Bolt should spend its charge cost even while unsuited");
		helper.assertFalse(GreenLantern.abilityReady(player, "ring_bolt"),
				"Ring Bolt should set its cooldown even while unsuited");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightTogglesOnWithoutTheSuitOn(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		helper.assertFalse(GreenLantern.isSuited(player), "this test needs an unsuited player");
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_3, true);
		helper.assertTrue(GreenLanternFlight.isFlying(player), "Ring Flight should engage even while unsuited");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitingDownDoesNotInterruptFlightOrTheBarrier(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternFlight.onEnter(player);
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.GREEN_LANTERN_BARRIER_HP,
				GreenLanternConfig.SHIELD_HP);

		// Fast-forward a suit-down animation that's already in progress to its completion tick.
		GreenLanternState s = GreenLantern.state(player).copy();
		s.suited = true;
		s.suitAnimDir = GreenLanternState.SUIT_SUITING_DOWN;
		s.suitAnimStartTick = player.level().getGameTime() - GreenLanternConfig.SUIT_UP_TICKS;
		GreenLantern.save(player, s);
		com.projecthero.mod.greenlantern.GreenLanternSuit.tick(player);

		helper.assertFalse(GreenLantern.state(player).suited, "the suit should have finished retracting");
		helper.assertTrue(GreenLanternFlight.isFlying(player),
				"suiting down must not interrupt an in-progress unsuited-capable flight");
		helper.assertTrue(player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f)
				== GreenLanternConfig.SHIELD_HP, "suiting down must not dismiss an active barrier");
		helper.succeed();
	}

	// ---------------- cross-tier exclusivity ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void bondingGrantsThePower(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		helper.assertTrue(GreenLantern.hasPower(player), "bond() should grant the power");
		helper.assertTrue(GreenLantern.state(player).ringCharge == GreenLanternConfig.MAX_RING_CHARGE,
				"a freshly bonded ring should start at full charge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bondingRefusesASecondTime(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		boolean second = GreenLantern.bond(player);
		helper.assertFalse(second, "bonding an already-bonded player must be a no-op");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wipeAllStripsGreenLanternWhenGrantingAnotherTier(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		HeroTiers.wipeAll(player);
		MaxSteel.bond(player);
		helper.assertFalse(GreenLantern.hasPower(player), "wipeAll() should have stripped Green Lantern");
		helper.assertTrue(MaxSteel.hasPower(player), "Max Steel should now be the sole power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hasHeroTierReportsGreenLantern(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		helper.assertTrue(HeroTiers.hasHeroTier(player), "HeroTiers.hasHeroTier must see a bonded Green Lantern");
		helper.succeed();
	}

	// ---------------- Ring Charge ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void spendClampsAtZeroAndNeverGoesNegative(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 100f;
		GreenLantern.save(player, s);
		boolean ok = GreenLanternEnergy.spend(player, 5000f);
		helper.assertFalse(ok, "spend() must refuse a cost greater than the spendable pool");
		helper.assertTrue(GreenLantern.state(player).ringCharge == 100f, "a refused spend must not touch the pool");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spendCannotDipIntoTheEmergencyReserve(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.EMERGENCY_RESERVE; // exactly the reserve, nothing spendable
		GreenLantern.save(player, s);
		helper.assertFalse(GreenLanternEnergy.canSpend(player, 1f),
				"only the emergency reserve remains -- canSpend must refuse even 1 point");
		boolean ok = GreenLanternEnergy.spend(player, 1f);
		helper.assertFalse(ok, "spend() must refuse to touch the emergency reserve");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spendEmergencyCanDrainBelowTheReserve(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.EMERGENCY_RESERVE;
		GreenLantern.save(player, s);
		boolean ok = GreenLanternEnergy.spendEmergency(player, GreenLanternConfig.EMERGENCY_RESERVE);
		helper.assertTrue(ok, "spendEmergency() should be able to spend the full reserve");
		helper.assertTrue(GreenLantern.state(player).ringCharge == 0f, "the reserve should now be fully spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void addChargeNeverExceedsTheMax(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternEnergy.addCharge(player, 999999f);
		helper.assertTrue(GreenLantern.state(player).ringCharge == GreenLanternConfig.MAX_RING_CHARGE,
				"addCharge() must clamp at the pool maximum");
		helper.succeed();
	}

	// ---------------- cooldowns ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void triggeredCooldownBlocksImmediateReuse(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLantern.triggerCooldown(player, "ring_bolt", 200);
		helper.assertFalse(GreenLantern.abilityReady(player, "ring_bolt"), "a fresh cooldown must not be ready");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cooldownClearsOnceItsAbsoluteTimeHasPassed(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.abilityReadyAt.put("ring_bolt", player.level().getGameTime() - 5L); // already in the past
		GreenLantern.save(player, s);
		helper.assertTrue(GreenLantern.abilityReady(player, "ring_bolt"), "a past-due cooldown must report ready");
		helper.succeed();
	}

	// ---------------- constructs ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void constructCapRefusesOverweightDeploy(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.masteryLevel = GreenLanternState.MASTERY_IV; // unlock everything for this test
		GreenLantern.save(player, s);

		// Energy Blade (slot weight 1, no block placement) deploys deterministically regardless of
		// what the mock player is looking at -- six of them fill the cap exactly.
		for (int i = 0; i < 6; i++) {
			GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE);
		}
		int weightAtCap = GreenLanternConstructs.activeWeight(player.getUUID());
		helper.assertTrue(weightAtCap == GreenLanternConfig.CONSTRUCT_MAX_SLOTS,
				"six weight-1 constructs should fill the 6-slot cap exactly, was " + weightAtCap);

		float chargeBefore = GreenLantern.state(player).ringCharge;
		GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE); // a 7th -- should overflow
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == GreenLanternConfig.CONSTRUCT_MAX_SLOTS,
				"a refused deploy must not push the weight past the cap");
		helper.assertTrue(GreenLantern.state(player).ringCharge == chargeBefore,
				"a refused deploy must not spend any charge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dismissAllClearsEveryOwnedConstruct(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternConstructs.deploy(player, ConstructType.LANTERN_LIGHT);
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) > 0, "the construct should be tracked");
		GreenLanternConstructs.dismissAll(player.getUUID());
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == 0,
				"dismissAll() must clear every owned construct");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mastersLockedConstructIsRefused(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.masteryLevel = GreenLanternState.MASTERY_BONDED; // Sentry Turret needs Mastery IV
		GreenLantern.save(player, s);
		GreenLanternConstructs.deploy(player, ConstructType.SENTRY_TURRET);
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == 0,
				"a construct above the player's Mastery level must be refused");
		helper.assertTrue(GreenLantern.state(player).ringCharge == GreenLanternConfig.MAX_RING_CHARGE,
				"a Mastery-refused deploy must not spend charge");
		helper.succeed();
	}

	// ---------------- shield / mastery ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void barrierReturnsOverflowInsteadOfNoSellingIt(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, 1f);
		float overflow = GreenLanternShield.absorb(player, 200f);
		helper.assertTrue(overflow == 199f, "a 1-HP shield must only absorb 1 of a 200-damage hit, got overflow " + overflow);
		helper.assertFalse(GreenLanternShield.isActive(player), "the barrier should have broken");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void barrierAbsorbsFullyWhenItHasEnoughHp(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, GreenLanternConfig.SHIELD_HP);
		float overflow = GreenLanternShield.absorb(player, 30f);
		helper.assertTrue(overflow == 0f, "a full-HP shield should fully absorb a 30-damage hit, got overflow " + overflow);
		helper.assertTrue(GreenLanternShield.isActive(player), "the barrier should still be up");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void defeatingABossAdvancesToMasteryIV(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.masteryLevel = GreenLanternState.MASTERY_III;
		s.totalEnergySpent = GreenLanternConfig.MASTERY_IV_ENERGY;
		GreenLantern.save(player, s);
		GreenLanternMastery.onBossDefeated(player);
		helper.assertTrue(GreenLantern.state(player).masteryLevel == GreenLanternState.MASTERY_IV,
				"meeting both the energy and boss-defeat requirements should advance to Mastery IV");
		helper.succeed();
	}

	// ---------------- lifecycle ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void clearTransientStopsFlightAndDismissesConstructs(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.suited = true;
		GreenLantern.save(player, s);
		GreenLanternFlight.onEnter(player);
		GreenLanternConstructs.deploy(player, ConstructType.LANTERN_LIGHT);

		GreenLantern.clearTransient(player);

		helper.assertFalse(GreenLanternFlight.isFlying(player), "clearTransient() must stop Ring Flight");
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == 0,
				"clearTransient() must dismiss every owned construct");
		helper.assertFalse(GreenLantern.state(player).suited, "clearTransient() must retract the suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void barrierIsClearedButRingChargeSurvivesRevoke(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 4242f;
		GreenLantern.save(player, s);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, GreenLanternConfig.SHIELD_HP);
		GreenLanternShield.dismissAll(player);
		helper.assertFalse(GreenLanternShield.isActive(player), "dismissAll() must end the active barrier");
		helper.assertTrue(GreenLantern.state(player).ringCharge == 4242f,
				"dismissing the barrier must not touch Ring Charge");
		helper.succeed();
	}
}
