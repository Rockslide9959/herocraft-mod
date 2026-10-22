package com.projecthero.mod.gametest;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.AllMightStrain;
import com.projecthero.mod.allmight.OneForAllKit;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.allmight.data.AllMightState.Limb;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.item.QuirkItems;
import com.projecthero.mod.quirk.item.QuirkSerumItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user: "come up with a better strain system for one for all ... and add it to the quirk framework"): the safe limit
 * and mastery, Smashes past the limit straining the limb that threw them, the strained / hurt / broken tiers (weaker moves, a
 * broken limb refusing its Smashes), healing (rest, food, Regeneration, mending, respawn), and One For All as the eighth Quirk.
 * Mock players are not ticked by the server, so each test drives the ticks itself. Design: docs/OFA_STRAIN.md.
 */
public class OneForAllStrainGameTests implements FabricGameTest {
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 5, 8))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		AllMight.grant(p);
		return p;
	}

	private static void powerForm(ServerPlayer p, float output) {
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		s.outputPct = output;
		s.lastHitAt = p.level().getGameTime();
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.reconcile(p);
		p.setHealth(p.getMaxHealth());
	}

	/** Presses and releases a key; the previous move's wind-up lock is cleared first so presses in a row each start. */
	private static void press(ServerPlayer p, int slot, boolean shift) {
		AllMightState s = AllMight.state(p).copy();
		s.busyUntil = 0L;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	private static float limb(ServerPlayer p, Limb l) {
		return AllMight.state(p).limb(l);
	}

	// ---------------------------------------------------------------- the safe limit

	@GameTest(template = EMPTY_STRUCTURE)
	public void insideTheLimitIsFreeAndTrainsTheLimit(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(AllMight.limit(p) == AllMightConfig.MASTERY_START, "a new holder starts at a 20% limit");
		powerForm(p, 20f);
		press(p, 1, false); // R: Detroit at 20% -- right on the limit
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "Detroit thrown");
		helper.assertTrue(limb(p, Limb.RIGHT_ARM) == 0f && p.getHealth() == p.getMaxHealth(), "at the limit: no strain, no HP");
		helper.assertTrue(Math.abs(AllMight.limit(p) - (20f + AllMightConfig.MASTERY_PER_SMASH)) < 1e-4f, "every Smash trains the limit a little");
		// the limit is kept when the power is lost and regained
		AllMightStrain.setMastery(p, 37f);
		AllMight.revoke(p);
		AllMight.grant(p);
		helper.assertTrue(AllMight.limit(p) == 37f, "the body remembers its limit, got " + AllMight.limit(p));
		AllMightStrain.setMastery(p, 1000f);
		helper.assertTrue(AllMight.limit(p) == AllMightConfig.MASTERY_MAX, "capped at 100%");
		helper.succeed();
	}

	// ---------------------------------------------------------------- over the limit

	@GameTest(template = EMPTY_STRUCTURE)
	public void overTheLimitStrainsTheLimbThatThrewIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 100f); // as in Plus Ultra: 80 points past a 20% limit
		float hp = p.getHealth();
		press(p, 1, false); // R: Detroit -- the right arm
		float expected = 1.0f * 80f * AllMightConfig.OVER_STRAIN_PER_POINT; // 60
		helper.assertTrue(Math.abs(limb(p, Limb.RIGHT_ARM) - expected) < 0.01f, "Detroit tore the right arm by 60, got " + limb(p, Limb.RIGHT_ARM));
		helper.assertTrue(limb(p, Limb.LEFT_ARM) == 0f && limb(p, Limb.LEGS) == 0f, "only the right arm");
		helper.assertTrue(p.getHealth() < hp && p.getHealth() >= 1f, "and it costs some health, " + hp + " -> " + p.getHealth());
		helper.assertTrue(AllMight.limit(p) > 20f + AllMightConfig.MASTERY_PER_SMASH, "past the limit trains it faster");
		press(p, 2, false); // G: Texas -- the left arm
		helper.assertTrue(limb(p, Limb.LEFT_ARM) > 50f, "Texas -> the left arm");
		press(p, 3, true); // Shift+X: Manchester -- the legs
		helper.assertTrue(limb(p, Limb.LEGS) > 50f, "Manchester -> the legs");
		helper.assertTrue(AllMightStrain.limbsOf(AllMightAbilities.CAROLINA).length == 2, "Carolina is both arms");
		helper.assertTrue(AllMightStrain.limbsOf(AllMightAbilities.LEAP).length == 0, "the Leap is no Smash");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strainedAndHurtLimbsWeakenTheirSmashes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 20f);
		AllMightStrain.setLimb(p, Limb.RIGHT_ARM, 45f);
		helper.assertTrue(AllMightStrain.tier(AllMight.state(p), Limb.RIGHT_ARM) == 1, "45 is Strained");
		float m = AllMightStrain.cast(p, AllMightAbilities.DETROIT, 20f);
		helper.assertTrue(Math.abs(m - AllMight.multiplierAt(20f) * AllMightConfig.LIMB_STRAINED_FACTOR) < 1e-4f, "a strained arm hits x0.85, got " + m);
		AllMightStrain.setLimb(p, Limb.RIGHT_ARM, 75f);
		m = AllMightStrain.cast(p, AllMightAbilities.DETROIT, 20f);
		helper.assertTrue(Math.abs(m - AllMight.multiplierAt(20f) * AllMightConfig.LIMB_HURT_FACTOR) < 1e-4f, "a hurt arm hits x0.65, got " + m);
		m = AllMightStrain.cast(p, AllMightAbilities.TEXAS, 20f);
		helper.assertTrue(Math.abs(m - AllMight.multiplierAt(20f)) < 1e-4f, "the healthy left arm is unaffected");
		m = AllMightStrain.cast(p, AllMightAbilities.CAROLINA, 20f);
		helper.assertTrue(Math.abs(m - AllMight.multiplierAt(20f) * AllMightConfig.LIMB_HURT_FACTOR) < 1e-4f, "a two-arm move takes the weaker arm");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aBrokenLimbRefusesItsSmashes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 20f);
		AllMightStrain.addLimbStrain(p, Limb.RIGHT_ARM, 150f);
		helper.assertTrue(AllMightStrain.broken(p, Limb.RIGHT_ARM) && limb(p, Limb.RIGHT_ARM) == 100f, "100 breaks the right arm");
		press(p, 1, false); // R: Detroit
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0, "a broken right arm cannot throw Detroit");
		press(p, 5, true); // Shift+V: Missouri (right arm)
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.MISSOURI) == 0, "nor Missouri");
		AbilityRouter.handleInput(p, 4, true); // Z: the United States of Smash
		helper.assertTrue(AllMight.state(p).chargeStart == 0L, "nor charge the United States of Smash");
		AbilityRouter.handleInput(p, 4, false);
		press(p, 5, false); // V: Carolina (both arms)
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.CAROLINA) == 0, "nor a two-arm Smash");
		press(p, 2, false); // G: Texas (left arm)
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.TEXAS) > 0, "the left arm still works");
		helper.assertTrue(new OneForAllKit().keyLocked(p, AbilitySlot.SLOT_1, false) && !new OneForAllKit().keyLocked(p, AbilitySlot.SLOT_2, false),
				"the HUD crosses out R, not G");
		AllMight.tick(p);
		helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "a broken arm weakens his punches");
		press(p, 3, false); // X: the Leap is no Smash
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.LEAP) > 0, "the Leap still works");
		helper.succeed();
	}

	// ---------------------------------------------------------------- healing

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void limbsHealWithRestFoodAndRegeneration(GameTestHelper helper) {
		ServerPlayer p = hero(helper); // the Base Form: 1 a second
		AllMightStrain.setLimb(p, Limb.LEFT_ARM, 30f);
		AllMightStrain.setLimb(p, Limb.LEGS, 100f);
		helper.assertTrue(AllMightStrain.broken(p, Limb.LEGS), "broken legs");
		p.getFoodData().setFoodLevel(10);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(41, () -> {
			float arm = limb(p, Limb.LEFT_ARM);
			helper.assertTrue(arm < 30f - 1.5f && arm > 30f - 2.5f, "~1 a second at rest, got " + arm);
			float legs = limb(p, Limb.LEGS);
			helper.assertTrue(legs < 100f && legs > 100f - 1.6f, "a broken limb heals slower, got " + legs);
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "broken legs slow him");
			// eating: 8 hunger points heal 20 at once
			p.getFoodData().setFoodLevel(18);
			AllMight.tick(p);
			helper.assertTrue(limb(p, Limb.LEGS) < legs - 19f, "a meal heals every limb, got " + limb(p, Limb.LEGS));
			// Regeneration speeds it up
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
			float before = limb(p, Limb.LEGS);
			helper.runAfterDelay(20, () -> {
				// broken legs: (1 + 2 x 1.5) x 0.6 = 2.4 a second, against 0.6 without it
				helper.assertTrue(limb(p, Limb.LEGS) < before - 1.8f, "Regeneration II heals much faster, " + before + " -> " + limb(p, Limb.LEGS));
				// mended: usable again at 50
				AllMightStrain.healLimb(p, Limb.LEGS, limb(p, Limb.LEGS) - 50f);
				helper.assertFalse(AllMightStrain.broken(p, Limb.LEGS), "mended at 50");
				// a respawn is a whole new body (the limit stays)
				AllMightStrain.setLimb(p, Limb.RIGHT_ARM, 100f);
				AllMightStrain.setMastery(p, 42f);
				AllMight.onPlayerRespawn(p);
				helper.assertTrue(AllMight.state(p).broken == 0 && limb(p, Limb.RIGHT_ARM) == 0f && AllMight.limit(p) == 42f,
						"respawn heals the limbs and keeps the limit");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nothingHealsPastTheLimit(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 100f);
		AllMightStrain.setLimb(p, Limb.LEFT_ARM, 30f);
		long now = p.level().getGameTime();
		for (int t = 0; t < 40; t++) {
			AllMightStrain.tick(p, now + t);
		}
		helper.assertTrue(limb(p, Limb.LEFT_ARM) > 30f, "Full Cowl past the limit strains instead of healing, got " + limb(p, Limb.LEFT_ARM));
		helper.succeed();
	}

	// ---------------------------------------------------------------- One For All in the Quirk framework

	@GameTest(template = EMPTY_STRUCTURE)
	public void theQuirkRegistryHoldsOneForAll(GameTestHelper helper) {
		helper.assertTrue(Quirk.byId("one_for_all") == Quirk.ONE_FOR_ALL && Quirk.byHeroKey("all_might") == Quirk.ONE_FOR_ALL,
				"One For All is a Quirk, by id and by its old hero key");
		helper.assertTrue(Quirk.ONE_FOR_ALL.heroKey().equals(AllMight.KEY) && Quirk.ONE_FOR_ALL.standalone, "it keeps the all_might key");
		helper.assertTrue(Quirk.values().length == 8 && Quirk.kitQuirks().length == 7, "eight Quirks, seven on QuirkState");
		helper.assertTrue(QuirkKits.of(Quirk.ONE_FOR_ALL) instanceof OneForAllKit, "its kit is registered");
		helper.assertTrue(QuirkKits.of(Quirk.ONE_FOR_ALL).idsOf(AbilitySlot.SLOT_1)[0].equals(AllMightAbilities.DETROIT)
				&& QuirkKits.of(Quirk.ONE_FOR_ALL).idsOf(AbilitySlot.SLOT_6)[1].equals(AllMightAbilities.ST_LOUIS), "R = Detroit ... Shift+C = St. Louis");
		helper.assertTrue(QuirkItems.serumOf(Quirk.ONE_FOR_ALL) != null, "it has a Quirk Serum");

		// granted the Quirk way
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(Quirks.grant(p, Quirk.ONE_FOR_ALL), "Quirks.grant gives One For All");
		helper.assertTrue(AllMight.hasPower(p) && Quirks.holds(p, Quirk.ONE_FOR_ALL) && Quirks.hasPower(p) && HeroTiers.heroCount(p) == 1,
				"held as All Might and as a Quirk, one hero");
		helper.assertFalse(Quirks.grant(p, Quirk.ONE_FOR_ALL), "twice refused");
		helper.assertTrue(Quirks.grant(p, Quirk.HELLFLAME) && !AllMight.hasPower(p) && Quirks.holds(p, Quirk.HELLFLAME), "another Quirk replaces it");
		helper.assertTrue(QuirkSerumItem.drink(p, Quirk.ONE_FOR_ALL, p.getRandom()) == Quirk.ONE_FOR_ALL && AllMight.hasPower(p)
				&& !Quirks.holds(p, Quirk.HELLFLAME), "its serum gives it back and replaces Hellflame");
		Quirks.revoke(p);
		helper.assertFalse(AllMight.hasPower(p) || Quirks.hasPower(p), "Quirks.revoke takes it");

		// an existing holder (a save from before One For All was a Quirk: only the All Might state) is a Quirk holder as is
		ServerPlayer old = helper.makeMockServerPlayerInLevel();
		AllMightState s = new AllMightState();
		s.hasPower = true;
		old.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		helper.assertTrue(Quirks.quirk(old) == Quirk.ONE_FOR_ALL, "no migration needed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theKeysGoThroughTheQuirkCore(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		// the Base Form: the Quirk core takes the keys and nothing happens
		press(p, 1, false);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0, "no Smashes in the Base Form");
		helper.assertTrue(new OneForAllKit().keyLocked(p, AbilitySlot.SLOT_1, false), "the HUD dims the keys in the Base Form");
		powerForm(p, 10f);
		AbilityRouter.handleInput(p, 1, true);
		AbilityRouter.handleInput(p, 1, true); // the OS key repeat: debounced by the Quirk core
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "Detroit in the Power Form");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.16.7: the One For All timer (back, at 20 minutes)

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTimerIsTwentyMinutes(GameTestHelper helper) {
		helper.assertTrue(AllMightConfig.timer().powerFormSeconds == 1200f && AllMight.timerMax() == 1200f, "20:00 of Power Form");
		helper.assertTrue(AllMightConfig.timer().drainPerSecond == 1f && AllMightConfig.timer().rechargePerSecond == 0.5f,
				"drains 1 s a second, refills 0.5 s a second (a full refill takes 40 min)");
		ServerPlayer p = hero(helper);
		helper.assertTrue(AllMight.timeLeft(p) == 1200f, "a new holder has a full timer");
		// the save: a new key, missing = full (old saves); a value round-trips
		AllMightState s = AllMight.state(p).copy();
		s.ofaTimer = 321.5f;
		var json = AllMightState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, s).getOrThrow();
		helper.assertTrue(AllMightState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow().ofaTimer == 321.5f, "saved");
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("has_power", true);
		old.addProperty("ofa", 0.0f); // the pre-0.16.6 key stays ignored
		AllMightState loaded = AllMightState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(AllMight.timeLeft(loaded) == 1200f, "an existing save loads with a full timer");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theTimerDrainsInThePowerFormAndRefillsInTheBaseForm(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 10f);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(50, () -> {
			float used = 1200f - AllMight.timeLeft(p);
			helper.assertTrue(used >= 2f && used <= 3f, "1 s a second in the Power Form, used " + used);
			AllMightState s = AllMight.state(p).copy();
			s.fullPower = false;
			p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
			AllMight.reconcile(p);
			AllMight.setTimer(p, 600f);
		});
		helper.runAfterDelay(100, () -> {
			float gained = AllMight.timeLeft(p) - 600f;
			helper.assertTrue(gained >= 1f && gained <= 1.5f, "0.5 s a second in the Base Form, gained " + gained);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void atZeroHeIsForcedBackToTheBaseForm(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 10f);
		AllMight.setTimer(p, 0f);
		AllMight.tick(p);
		helper.assertFalse(AllMight.isFullPower(p), "0:00 -- back to the Base Form");
		// H is refused until 4 s have come back
		AllMightState s = AllMight.state(p).copy();
		s.formChangedAt = p.level().getGameTime() - 100L;
		s.transformUntil = 0L;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.setTimer(p, 3f);
		AllMight.toggleForm(p);
		helper.assertFalse(AllMight.isFullPower(p), "3 s is not enough to transform");
		AllMight.setTimer(p, AllMightConfig.timer().minimumToTransformSeconds);
		AllMight.toggleForm(p);
		helper.assertTrue(AllMight.isFullPower(p), "4 s is");
		// a respawn refills it
		AllMight.setTimer(p, 10f);
		AllMight.onPlayerRespawn(p);
		helper.assertTrue(AllMight.timeLeft(p) == 1200f, "respawn refills the timer");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void plusUltraAndTheUnitedStatesOfSmashSpendTheTimer(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p, 10f);
		AllMight.setTimer(p, 150f);
		press(p, 6, false); // C: Plus Ultra needs 200 s (+1)
		helper.assertFalse(AllMight.plusUltraActive(p), "refused without 3:20 left");
		helper.assertTrue(AllMight.timeLeft(p) == 150f, "nothing spent");
		AllMight.setTimer(p, 1200f);
		press(p, 6, false);
		helper.assertTrue(AllMight.plusUltraActive(p) && AllMight.timeLeft(p) == 1000f, "Plus Ultra spends 200 s, left " + AllMight.timeLeft(p));
		// the United States of Smash: refused up front with less than 400 s
		AllMightState s = AllMight.state(p).copy();
		s.busyUntil = 0L;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.setTimer(p, 300f);
		AbilityRouter.handleInput(p, 4, true);
		helper.assertTrue(AllMight.state(p).chargeStart == 0L, "no hold with less than 6:40 left");
		AbilityRouter.handleInput(p, 4, false);
		AllMight.setTimer(p, 1200f);
		AbilityRouter.handleInput(p, 4, true);
		helper.assertTrue(AllMight.state(p).chargeStart > 0L && AllMight.timeLeft(p) == 1200f, "the hold starts; nothing is paid until the cast");
		AbilityRouter.handleInput(p, 4, false);
		helper.succeed();
	}
}
