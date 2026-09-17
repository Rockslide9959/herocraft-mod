package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.GreenLanternCombat;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternFlight;
import com.projecthero.mod.greenlantern.GreenLanternMastery;
import com.projecthero.mod.greenlantern.GreenLanternShield;
import com.projecthero.mod.greenlantern.GreenLanternSuit;
import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.maxsteel.MaxSteel;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;

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

	// ---------------- flight (v0.11.2: speed/animation parity with the Flight power) ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void ringFlightCruiseSpeedMatchesTheFlightPower(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternFlight.onEnter(player);
		GreenLanternFlight.tick(player, false);
		helper.assertTrue(player.getAbilities().getFlyingSpeed()
				== com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED,
				"Ring Flight's cruise speed should exactly match the Flight power's HERO_FLYING_SPEED, was "
						+ player.getAbilities().getFlyingSpeed());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ringFlightBoostIsFasterThanCruise(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternFlight.onEnter(player);
		GreenLanternFlight.tick(player, true);
		helper.assertTrue(player.getAbilities().getFlyingSpeed()
				> com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED,
				"Boost should still fly faster than plain cruise after the speed rework");
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
		// what the mock player is looking at -- CONSTRUCT_MAX_SLOTS of them fill the cap exactly.
		for (int i = 0; i < GreenLanternConfig.CONSTRUCT_MAX_SLOTS; i++) {
			GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE);
		}
		int weightAtCap = GreenLanternConstructs.activeWeight(player.getUUID());
		helper.assertTrue(weightAtCap == GreenLanternConfig.CONSTRUCT_MAX_SLOTS,
				"CONSTRUCT_MAX_SLOTS weight-1 constructs should fill the cap exactly, was " + weightAtCap);

		float chargeBefore = GreenLantern.state(player).ringCharge;
		GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE); // one more -- should overflow
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
		s.masteryLevel = GreenLanternState.MASTERY_I; // ENERGY_BLADE's unlock requirement
		GreenLantern.save(player, s);
		GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE); // raycast-free marker construct
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

	// ---------------- construct wheel (v0.11.2: hold C to select) ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void selectConstructAppliesAValidUnlockedChoice(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.masteryLevel = GreenLanternState.MASTERY_IV; // unlock everything
		GreenLantern.save(player, s);
		GreenLanternAbilityManager.selectConstruct(player, ConstructType.SENTRY_TURRET.ordinal());
		helper.assertTrue(GreenLantern.state(player).selectedConstruct == ConstructType.SENTRY_TURRET.ordinal(),
				"a valid, unlocked ordinal from the wheel should become the selected construct");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void selectConstructRefusesALockedType(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.masteryLevel = GreenLanternState.MASTERY_BONDED; // Sentry Turret needs Mastery IV
		s.selectedConstruct = ConstructType.LANTERN_LIGHT.ordinal();
		GreenLantern.save(player, s);
		GreenLanternAbilityManager.selectConstruct(player, ConstructType.SENTRY_TURRET.ordinal());
		helper.assertTrue(GreenLantern.state(player).selectedConstruct == ConstructType.LANTERN_LIGHT.ordinal(),
				"the wheel payload must not be trusted just because the client only shows unlocked wedges -- "
						+ "a locked ordinal must leave the previous selection untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void selectConstructRefusesAnOutOfRangeOrdinal(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.masteryLevel = GreenLanternState.MASTERY_IV;
		s.selectedConstruct = ConstructType.LANTERN_LIGHT.ordinal();
		GreenLantern.save(player, s);
		GreenLanternAbilityManager.selectConstruct(player, -1);
		GreenLanternAbilityManager.selectConstruct(player, ConstructType.values().length + 5);
		helper.assertTrue(GreenLantern.state(player).selectedConstruct == ConstructType.LANTERN_LIGHT.ordinal(),
				"a negative or past-the-end ordinal must be ignored rather than throwing or corrupting state");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shortTapOfAbilitySixStillDeploysDirectly(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.masteryLevel = GreenLanternState.MASTERY_IV;
		// ENERGY_BLADE rather than a block-placing construct -- it's a pure attribute-modifier "marker"
		// construct with no raycast/placement step, so this test (which is really about the tap/hold
		// gesture timing, not placement) can't spuriously fail from a raycast reaching a neighbouring
		// gametest structure when many tests are packed into the same batch.
		s.selectedConstruct = ConstructType.ENERGY_BLADE.ordinal();
		GreenLantern.save(player, s);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, false); // released same tick -- a tap
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) > 0,
				"a quick tap of C must still deploy the selected construct, unchanged by the wheel gesture");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void longHoldOfAbilitySixNoLongerCyclesTheSelection(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.masteryLevel = GreenLanternState.MASTERY_IV;
		s.selectedConstruct = ConstructType.ENERGY_BLADE.ordinal(); // raycast-free marker construct, see the sibling test's comment
		GreenLantern.save(player, s);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, true);
		// v0.11.2: a hold past the wheel threshold used to cycle the selection server-side as a
		// no-screen fallback; that responsibility now belongs entirely to the client construct wheel
		// (GreenLanternConstructSelectPayload), so a long hold-then-release must be a pure no-op here --
		// no deploy (it was not a quick tap) and no selection change (cycling was removed). This exact
		// press/wait/release sequence is really only exercised at the server-method level now: the real
		// client (ProjectHeroModClient#handleGreenLanternConstructWheelHold) deliberately withholds the
		// release payload entirely once it opens the wheel (see the screen-cleanup special case for slot
		// 6), so a live client never actually sends a release this late for a genuine hold -- this test
		// exists to pin the server method's own contract, not to reproduce live client behaviour.
		helper.runAfterDelay(GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS, () -> {
			GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, false);
			helper.assertTrue(GreenLantern.state(player).selectedConstruct == ConstructType.ENERGY_BLADE.ordinal(),
					"a long hold-then-release must not silently cycle the selection any more");
			helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == 0,
					"a long hold-then-release must not deploy anything either -- it is not a tap");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void justUnderTheWheelThresholdStillDeploysAsATap(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.masteryLevel = GreenLanternState.MASTERY_IV;
		s.selectedConstruct = ConstructType.ENERGY_BLADE.ordinal(); // raycast-free marker construct, see the sibling test's comment
		GreenLantern.save(player, s);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, true);
		// One tick short of the wheel threshold is still a "tap" by the server's own definition
		// (held < CONSTRUCT_WHEEL_HOLD_TICKS) -- pins the exact boundary the deploy-vs-wheel decision
		// sits on, since GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS - 1 was the elapsed time the old
		// client/server race in this method's sibling test used to mis-fire a deploy at.
		helper.runAfterDelay(GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS - 1, () -> {
			GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, false);
			helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) > 0,
					"a release just under the wheel threshold is a tap and should still deploy");
			helper.succeed();
		});
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
	public void shiftZDismissesAnActiveDomeVoluntarily(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		player.setShiftKeyDown(true);

		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, true); // Shift+Z: deploy the dome
		helper.assertTrue(GreenLanternShield.isActive(player) && GreenLanternShield.isDome(player),
				"the dome should be up before testing its dismiss");

		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, true); // Shift+Z again: voluntary dismiss
		helper.assertFalse(GreenLanternShield.isActive(player), "a second Shift+Z should dismiss an active dome");
		helper.assertFalse(GreenLantern.abilityReady(player, "protective_dome"),
				"a voluntary dismiss should still cost a (shorter) cooldown, or a full-HP dome could be "
						+ "soaked and instantly redeployed for free");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void voluntaryDomeDismissCooldownIsShorterThanABreak(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, true); // deploy
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, true); // voluntary dismiss
		int voluntaryCooldown = GreenLantern.cooldownRemaining(player, "protective_dome");
		helper.assertTrue(voluntaryCooldown > 0 && voluntaryCooldown <= GreenLanternConfig.DOME_COOLDOWN_TICKS / 2,
				"a voluntary dismiss's cooldown should be a fraction of the full break cooldown, was " + voluntaryCooldown);
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
		s.masteryLevel = GreenLanternState.MASTERY_I; // ENERGY_BLADE's unlock requirement
		GreenLantern.save(player, s);
		GreenLanternFlight.onEnter(player);
		GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE); // raycast-free marker construct

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

	// ================ v0.11.4: no passive regen / Oath recharge / rebalance / diamond suit / Tool Kit ================

	private static BlockPos placeBatteryNextTo(GameTestHelper helper, ServerPlayer player) {
		BlockPos pos = player.blockPosition().north();
		helper.getLevel().setBlock(pos, GreenLanternBlocks.POWER_BATTERY.defaultBlockState(), Block.UPDATE_ALL);
		return pos;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noPassiveRegenWithoutABattery(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 100f;
		GreenLantern.save(player, s);
		for (int i = 0; i < 200; i++) {
			GreenLanternAbilityManager.serverTick(player);
		}
		helper.assertTrue(GreenLantern.state(player).ringCharge == 100f,
				"with passive regen removed, charge must never increase on its own, was "
						+ GreenLantern.state(player).ringCharge);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oathRefusedWhenAlreadyAtMaxCharge(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		BlockPos batteryPos = placeBatteryNextTo(helper, player);
		GreenLanternBattery.beginOath(player, batteryPos);
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player),
				"starting an oath at full charge should be refused outright");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void oathCompletionFillsChargeExactlyToMax(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 500f;
		GreenLantern.save(player, s);
		BlockPos batteryPos = placeBatteryNextTo(helper, player);

		GreenLanternBattery.beginOath(player, batteryPos);
		helper.assertTrue(GreenLanternBattery.isRecitingOath(player), "beginOath() should start the recitation");

		helper.runAfterDelay(GreenLanternConfig.OATH_LINE_TICKS * 4L + 1, () -> {
			GreenLanternBattery.tick(player);
			helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "the oath should have finished by now");
			helper.assertTrue(GreenLantern.state(player).ringCharge == GreenLanternConfig.MAX_RING_CHARGE,
					"a completed oath should fill the ring to exactly max, was " + GreenLantern.state(player).ringCharge);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void movingDuringTheOathCancelsItAndSpendsNothing(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 500f;
		GreenLantern.save(player, s);
		BlockPos batteryPos = placeBatteryNextTo(helper, player);

		GreenLanternBattery.beginOath(player, batteryPos);
		player.teleportTo(player.getX() + 1.0, player.getY(), player.getZ());
		GreenLanternBattery.tick(player);

		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "moving during the oath must cancel it");
		helper.assertTrue(GreenLantern.state(player).ringCharge == 500f,
				"a cancelled oath must not change Ring Charge at all");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void usingAnAbilityDuringTheOathCancelsIt(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 500f;
		GreenLantern.save(player, s);
		BlockPos batteryPos = placeBatteryNextTo(helper, player);

		GreenLanternBattery.beginOath(player, batteryPos);
		GreenLanternCombat.ringBolt(player); // any ability firing calls GreenLanternBattery.onAbilityUsed
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "using Ring Bolt during the oath must cancel it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takingDamageDuringTheOathCancelsIt(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 500f;
		GreenLantern.save(player, s);
		BlockPos batteryPos = placeBatteryNextTo(helper, player);

		GreenLanternBattery.beginOath(player, batteryPos);
		GreenLanternBattery.onDamaged(player);
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "taking damage during the oath must cancel it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitingUpCancelsAnInProgressOath(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = 500f;
		GreenLantern.save(player, s);
		BlockPos batteryPos = placeBatteryNextTo(helper, player);

		GreenLanternBattery.beginOath(player, batteryPos);
		GreenLanternSuit.toggle(player); // suit-up previously did not cancel the old channel -- gap closed in v0.11.4
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "suiting up during the oath must cancel it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ringBoltUsesTheV0114DamageAndCost(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		float before = GreenLantern.state(player).ringCharge;
		GreenLanternCombat.ringBolt(player);
		helper.assertTrue(GreenLantern.state(player).ringCharge == before - GreenLanternConfig.BOLT_COST,
				"Ring Bolt should spend the v0.11.4 cost of " + GreenLanternConfig.BOLT_COST);
		helper.assertTrue(GreenLanternConfig.BOLT_DAMAGE == 13f, "Ring Bolt damage should now be 13");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void constructFistAndWarHammerUseTheV0114Numbers(GameTestHelper helper) {
		helper.assertTrue(GreenLanternConfig.FIST_DAMAGE == 16f, "Construct Fist damage should now be 16");
		helper.assertTrue(GreenLanternConfig.FIST_COST == 30f, "Construct Fist cost should now be 30");
		helper.assertTrue(GreenLanternConfig.FIST_COOLDOWN_TICKS == 40, "Construct Fist cooldown should now be 2s (40 ticks)");
		helper.assertTrue(GreenLanternConfig.HAMMER_CENTER_DAMAGE == 17f && GreenLanternConfig.HAMMER_OUTER_DAMAGE == 17f,
				"War Hammer Slam damage should now be a flat 17");
		helper.assertTrue(GreenLanternConfig.HAMMER_COST == 40f, "War Hammer Slam cost should now be 40");
		helper.assertTrue(GreenLanternConfig.HAMMER_COOLDOWN_TICKS == 100, "War Hammer Slam cooldown should stay 5s (100 ticks)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void warHammerSlamDealsTheSameFlatDamageAtBothRadii(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		Vec3 center = player.position().add(player.getLookAngle().scale(2.0)); // matches warHammerSlam's own centre

		Zombie near = EntityType.ZOMBIE.create(helper.getLevel());
		near.moveTo(center.x, center.y, center.z, 0f, 0f); // inside the old 1.5-block "center" ring
		helper.getLevel().addFreshEntity(near);
		Zombie outer = EntityType.ZOMBIE.create(helper.getLevel());
		outer.moveTo(center.x + GreenLanternConfig.HAMMER_RADIUS - 0.5, center.y, center.z, 0f, 0f); // near the outer edge
		helper.getLevel().addFreshEntity(outer);
		float nearBefore = near.getHealth();
		float outerBefore = outer.getHealth();

		GreenLanternCombat.warHammerSlam(player);

		// Not compared against the raw HAMMER_CENTER_DAMAGE/HAMMER_OUTER_DAMAGE constants directly --
		// AbilityHelpers.hurt() runs the real damage pipeline, and a vanilla Zombie's own base armour
		// mitigates a small fraction of it (17 lands as ~16.7), so the config value isn't what actually
		// reaches health. What v0.11.4 actually changed is that center and outer used to differ (14 vs 9)
		// and now must not -- so the real assertion is that both identical zombies took identical damage.
		float nearDamage = nearBefore - near.getHealth();
		float outerDamage = outerBefore - outer.getHealth();
		// A loose floor (not an exact 17) tolerates armour mitigation while still pinning the "flat 17"
		// magnitude -- a regression that shrank both constants to, say, 2 would still pass the pure
		// near==outer equality check below without this.
		helper.assertTrue(nearDamage > 10f, "the center-radius target should take roughly the flattened 17 damage, took " + nearDamage);
		helper.assertTrue(Math.abs(nearDamage - outerDamage) < 0.01f,
				"center and outer radius should now deal identical damage (the old 14/9 split is gone), got "
						+ nearDamage + " vs " + outerDamage);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void continuousBeamDrainsTenEnergyPerSecondForEightDps(GameTestHelper helper) {
		helper.assertTrue(GreenLanternConfig.BEAM_DAMAGE_PER_TICK == 4f && GreenLanternConfig.BEAM_TICK_INTERVAL == 10,
				"Continuous Beam should now deal 4 damage every 10 ticks (0.5s)");
		float drainPerSecond = GreenLanternConfig.BEAM_COST_PER_TICK * (20f / GreenLanternConfig.BEAM_TICK_INTERVAL);
		helper.assertTrue(drainPerSecond == 10f, "Continuous Beam should drain 10 energy/sec, computed " + drainPerSecond);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitMaterialIsDiamondLevel(GameTestHelper helper) {
		// Checked directly against the ArmorMaterial record rather than a live player's getArmorValue():
		// a mock GameTest player doesn't run the ordinary equipment-attribute-recompute tick cycle
		// (LivingEntity#collectEquipmentChanges) a real player does, so getArmorValue() reads 0 right
		// after setItemSlot() regardless of the material -- this checks the actual thing v0.11.4 changed.
		net.minecraft.world.item.ArmorMaterial material = com.projecthero.mod.item.ModArmorMaterials.GREEN_LANTERN.value();
		int total = material.defense().values().stream().mapToInt(Integer::intValue).sum();
		helper.assertTrue(total == 20, "the suit's total defence should be diamond-level 20, was " + total);
		helper.assertTrue(material.toughness() == 2.0f, "the suit's toughness should be diamond-level 2.0, was " + material.toughness());
		helper.assertTrue(material.knockbackResistance() == 0.0f,
				"the suit should carry no bonus knockback resistance from the material itself now, was "
						+ material.knockbackResistance());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitingUpGrantsTheMeleeBonusAndStrippingRemovesIt(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		double baseAttack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

		GreenLanternSuitArmor.equip(player);
		helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == baseAttack + GreenLanternConfig.SUIT_MELEE_BONUS,
				"the suit should add a flat +8 melee bonus while worn");

		GreenLanternSuitArmor.strip(player);
		helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == baseAttack,
				"stripping the suit should remove the melee bonus again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boostDrainsMoreThanBeforeForTheSprintTrail(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternFlight.onEnter(player);
		float drain = GreenLanternFlight.tick(player, true); // boosting
		float expected = (GreenLanternConfig.BOOST_COST_PER_SEC + GreenLanternConfig.FLIGHT_TRAIL_COST_PER_SEC) / 20f;
		helper.assertTrue(drain == expected,
				"boosting should now drain Boost's cost plus the trail's own cost, got " + drain + " expected " + expected);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hardLightWallIsUnlockedFromBonded(GameTestHelper helper) {
		helper.assertTrue(ConstructType.HARD_LIGHT_WALL.requiredMastery() == GreenLanternState.MASTERY_BONDED,
				"Hard-Light Wall should now be available from the moment a Green Lantern bonds");
		// Checked via unlockedFor() rather than an actual deploy -- Wall places real blocks at a 24-block
		// raycast projection (Kind.WALL), and this test's actual subject is the Mastery gate, not
		// placement, so it shouldn't share the raycast-into-a-neighbouring-structure flake risk that
		// LANTERN_LIGHT-based tests had (see the wheel-timing tests' fix, same root cause).
		ServerPlayer player = bonded(helper);
		helper.assertTrue(ConstructType.HARD_LIGHT_WALL.unlockedFor(GreenLantern.state(player)),
				"a freshly bonded (Mastery Bonded) player should have Hard-Light Wall unlocked");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void constructCostsAreCutFromTheirOriginalValues(GameTestHelper helper) {
		helper.assertTrue(GreenLanternConfig.WALL_COST == 100f && GreenLanternConfig.WALL_UPKEEP_PER_SEC == 8f,
				"Hard-Light Wall cost/upkeep should be cut to 100/8 (was 500/40)");
		helper.assertTrue(GreenLanternConfig.PLATFORM_COST == 60f && GreenLanternConfig.PLATFORM_UPKEEP_PER_SEC == 4f,
				"Platform cost/upkeep should be cut to 60/4 (was 300/20)");
		helper.assertTrue(GreenLanternConfig.TURRET_COST == 170f && GreenLanternConfig.TURRET_UPKEEP_PER_SEC == 9f,
				"Sentry Turret cost/upkeep should be cut to 170/9 (was 850/45)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void toolKitGrantsThreeTaggedToolsWithoutTouchingAPreExistingOne(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		// The player already owns an ordinary diamond pickaxe -- it must not be counted as (or replaced
		// by) a hard-light one; only the CustomData-tagged pieces the deploy grants should count.
		player.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));
		float before = GreenLantern.state(player).ringCharge;

		GreenLanternConstructs.deploy(player, ConstructType.HARD_LIGHT_TOOLS);
		helper.assertTrue(GreenLantern.state(player).ringCharge == before - GreenLanternConfig.TOOL_KIT_COST,
				"deploying the Tool Kit should spend its configured cost");
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == GreenLanternConfig.TOOL_KIT_SLOT_WEIGHT,
				"the Tool Kit should occupy its configured slot weight");

		long ordinaryPickaxes = player.getInventory().items.stream().filter(st -> st.getItem() == Items.DIAMOND_PICKAXE
				&& !st.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)).count();
		helper.assertTrue(ordinaryPickaxes == 1, "the player's own diamond pickaxe must be untouched, found " + ordinaryPickaxes);
		long tagged = countHardLightTools(player);
		helper.assertTrue(tagged == 3, "deploying the Tool Kit should grant exactly 3 tagged tools, got " + tagged);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void droppingAToolEndsTheKitAndThePickedUpPieceIsDeletedNotDuplicated(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternConstructs.deploy(player, ConstructType.HARD_LIGHT_TOOLS);

		// Simulates a drop (Q) by copying the stack out then clearing the slot, rather than calling
		// player.drop() -- what matters for this test is the piece leaving the inventory and later
		// coming back with no live kit behind it; the bug this guards against is that becoming a free
		// PERMANENT tool once picked back up.
		ItemStack pickaxe = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			if (isHardLightTool(player.getInventory().items.get(i))) {
				pickaxe = player.getInventory().items.get(i).copy();
				player.getInventory().items.set(i, ItemStack.EMPTY);
				break;
			}
		}
		helper.assertFalse(pickaxe.isEmpty(), "test setup: should have found a tagged tool to simulate dropping");
		GreenLanternConstructs.tick(helper.getLevel().getServer());

		helper.assertTrue(countHardLightTools(player) == 0,
				"dropping one tool should dismiss the whole kit and strip the remaining pieces too");
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == 0,
				"the Tool Kit construct should no longer be tracked once dismissed");

		// Now simulate picking the dropped piece back up -- with no live kit behind it any more, the
		// periodic all-players sweep (ProjectHeroMod -> GreenLanternConstructs.sweepLooseToolKitPieces,
		// which runs for every online player, not just this one, so a traded/gifted piece is caught too)
		// must delete it rather than let it become a permanent item.
		player.getInventory().add(pickaxe);
		helper.assertTrue(countHardLightTools(player) == 1, "test setup: the picked-up piece should be back in the inventory");
		GreenLanternConstructs.sweepLooseToolKitPieces(helper.getLevel().getServer());
		helper.assertTrue(countHardLightTools(player) == 0,
				"picking up a tool with no live Tool Kit behind it must delete it, not hand back a free permanent tool");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void movingAToolOntoTheContainerCursorDoesNotFalselyEndTheKit(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternConstructs.deploy(player, ConstructType.HARD_LIGHT_TOOLS);

		// Rearranging the hotbar picks a stack up onto the container-menu cursor for a moment -- it is
		// not in Inventory.items during that window, but it must still count towards the kit's 3 pieces.
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			if (isHardLightTool(player.getInventory().items.get(i))) {
				ItemStack carried = player.getInventory().items.get(i).copy();
				player.getInventory().items.set(i, ItemStack.EMPTY);
				player.containerMenu.setCarried(carried);
				break;
			}
		}
		GreenLanternConstructs.tick(helper.getLevel().getServer());

		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == GreenLanternConfig.TOOL_KIT_SLOT_WEIGHT,
				"a tool held on the container cursor must still count -- the kit must not end just from a hotbar rearrange");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void secondToolKitDeployIsRefused(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		GreenLanternConstructs.deploy(player, ConstructType.HARD_LIGHT_TOOLS);
		float afterFirst = GreenLantern.state(player).ringCharge;

		GreenLanternConstructs.deploy(player, ConstructType.HARD_LIGHT_TOOLS); // second attempt
		helper.assertTrue(GreenLantern.state(player).ringCharge == afterFirst,
				"a second Tool Kit deploy must be refused outright and must not spend any charge");
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == GreenLanternConfig.TOOL_KIT_SLOT_WEIGHT,
				"only one Tool Kit's worth of slot weight should be tracked, not two");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void toolKitDeployWithoutRoomIsRefundedNotDropped(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(player, s);
		// Fill every inventory slot but two -- not enough room for all three tools.
		var inv = player.getInventory();
		for (int i = 0; i < inv.items.size() - 2; i++) {
			inv.items.set(i, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
		}
		float before = GreenLantern.state(player).ringCharge;

		GreenLanternConstructs.deploy(player, ConstructType.HARD_LIGHT_TOOLS);

		helper.assertTrue(GreenLantern.state(player).ringCharge == before,
				"a Tool Kit deploy that can't fit must be fully refunded, not partially granted");
		helper.assertTrue(GreenLanternConstructs.activeWeight(player.getUUID()) == 0,
				"a refused-for-space Tool Kit must not be tracked as an active construct");
		helper.assertTrue(countHardLightTools(player) == 0, "no tools should have been granted at all");
		long droppedItems = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				player.getBoundingBox().inflate(8)).stream().filter(e -> isHardLightTool(e.getItem())).count();
		helper.assertTrue(droppedItems == 0, "a refused deploy must not leave any tool dropped on the ground either");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fractionalUpkeepStillAccumulatesMasteryProgress(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.totalEnergySpent = 0L;
		GreenLantern.save(player, s);
		// Not calling GreenLanternEnergy.clearSessionState() here: it's a global, shared-across-the-batch
		// map, and GameTest runs many tests concurrently -- wiping it mid-batch could zero out another
		// concurrently-running test's own in-progress carry. Unnecessary anyway: mock players get a fresh
		// random UUID each test, so MASTERY_XP_CARRY has no pre-existing entry for this one regardless.

		// 0.25 is exactly representable in float (no drift) and, individually, is exactly the kind of
		// sub-1 per-tick amount v0.11.4's cheaper upkeep produces -- Math.round(0.25) alone is 0, so
		// before the fix 8 calls would have contributed nothing at all to Mastery progress.
		for (int i = 0; i < 8; i++) {
			GreenLanternEnergy.drainTick(player, 0.25f);
		}
		long spent = GreenLantern.state(player).totalEnergySpent;
		helper.assertTrue(spent == 2L,
				"8 ticks of a 0.25/tick drain (2.0 total) should accumulate via the carried remainder instead of "
						+ "truncating to zero every call, was " + spent);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void codecStillDecodesASaveThatHasTheRemovedLastAbilityUseTickField(GameTestHelper helper) {
		// A save written before v0.11.4 still has "last_ability_use_tick" in its NBT -- the codec must
		// keep decoding it (RecordCodecBuilder simply ignores map entries it doesn't declare a field
		// for) rather than failing to load an existing player's Green Lantern state.
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putBoolean("has_power", true);
		tag.putFloat("ring_charge", 4242f);
		tag.putLong("last_ability_use_tick", 999L);
		tag.putInt("mastery_level", GreenLanternState.MASTERY_III);
		tag.putLong("total_energy_spent", 12345L);

		com.mojang.serialization.DataResult<GreenLanternState> result =
				GreenLanternState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag);
		helper.assertTrue(result.result().isPresent(),
				"a save with the removed last_ability_use_tick field should still decode: " + result.error());
		GreenLanternState decoded = result.result().get();
		helper.assertTrue(decoded.hasPower, "has_power should round-trip");
		helper.assertTrue(decoded.ringCharge == 4242f, "ring_charge should round-trip, was " + decoded.ringCharge);
		helper.assertTrue(decoded.masteryLevel == GreenLanternState.MASTERY_III, "mastery_level should round-trip");
		helper.assertTrue(decoded.totalEnergySpent == 12345L, "total_energy_spent should round-trip");
		helper.succeed();
	}

	private static long countHardLightTools(ServerPlayer player) {
		return player.getInventory().items.stream().filter(GreenLanternGameTests::isHardLightTool).count();
	}

	private static boolean isHardLightTool(ItemStack stack) {
		return (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.DIAMOND_AXE
				|| stack.getItem() == Items.DIAMOND_SHOVEL)
				&& stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
	}
}
