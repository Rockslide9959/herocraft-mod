package com.projecthero.mod.gametest;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilities;
import com.projecthero.mod.blackpanther.BlackPantherAbilityManager;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.entity.VibraniumDaggerEntity;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther suited kit (user-approved): plain keys are free and add Kinetic when they land, Shift keys spend it; short
 * on Kinetic a Shift move does nothing and spends nothing; Kinetic Overload (Shift+Z) needs a full bar and empties it; the bar never
 * decays in the suit, leaks slowly with the suit in the necklace, and a death empties it. Later v0.17.7 round: Pounce / Kinetic Pounce
 * always fire with a launch-only aim assist, Panther Leap follows the aim, suited Shift+V is Wakanda Forever (+ the Shroud for 30). The numbers are read from {@link BlackPantherConfig} so the user's own values drop in. Targets are
 * Vindicators (no natural armour) with 200 health. The player stands at 2.5, 2, 2.5 facing south (+z). Mock players are not
 * ticked by the server, so moves over time are stepped with {@link BlackPanther#tick}.
 */
public class BlackPantherSuitedV0177GameTests implements FabricGameTest {
	private static Vindicator dummy(GameTestHelper helper, BlockPos pos) {
		Vindicator s = helper.spawn(EntityType.VINDICATOR, pos);
		s.setNoAi(true);
		s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		s.setHealth(200f);
		return s;
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		BlackPantherAbilityManager.handle(p, slot, true);
		BlackPantherAbilityManager.handle(p, slot, false);
		p.setShiftKeyDown(false);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vibraniumComboHitsAndFillsKinetic(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		BlackPanther.setKinetic(p, 0f);
		press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.VIBRANIUM_COMBO) > 0, "suited R: the Vibranium Combo");
		float first = 200f - s.getHealth();
		helper.assertTrue(near(first, BlackPantherConfig.VIBRANIUM_COMBO_HIT_1), "strike 1 on the press, dealt " + first);
		helper.assertTrue(near(BlackPanther.kinetic(p), BlackPantherConfig.VIBRANIUM_COMBO_KINETIC_PER_HIT), "a landed strike adds Kinetic");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(BlackPantherConfig.VIBRANIUM_COMBO_STEP * 2 + 3, () -> {
			float total = 200f - s.getHealth();
			float expected = BlackPantherConfig.VIBRANIUM_COMBO_HIT_1 + BlackPantherConfig.VIBRANIUM_COMBO_HIT_2 + BlackPantherConfig.VIBRANIUM_COMBO_HIT_3;
			helper.assertTrue(near(total, expected), expected + " over the three strikes, dealt " + total);
			helper.assertTrue(near(BlackPanther.kinetic(p), 3 * BlackPantherConfig.VIBRANIUM_COMBO_KINETIC_PER_HIT), "Kinetic for each strike, got "
					+ BlackPanther.kinetic(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void comboWhiffAddsNoKinetic(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		BlackPanther.setKinetic(p, 0f);
		press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(BlackPanther.kinetic(p) == 0f, "nothing hit: nothing gained");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kineticComboCostsAndItsShockwaveHits(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_1, true);
		float first = 200f - s.getHealth();
		helper.assertTrue(near(first, BlackPantherConfig.KINETIC_COMBO_DAMAGE), "charged slash 1, dealt " + first);
		helper.assertTrue(near(BlackPanther.kinetic(p), 50f - BlackPantherConfig.KINETIC_COMBO_COST), "the cost, and no gain from its hits");
		// look up: slashes 2 and 3 cut the air above it, so only the finisher's flat forward cone shockwave reaches it
		p.setXRot(-60f);
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(BlackPantherConfig.KINETIC_COMBO_STEP * 2 + 3, () -> {
			float total = 200f - s.getHealth();
			float expected = BlackPantherConfig.KINETIC_COMBO_DAMAGE + BlackPantherConfig.KINETIC_COMBO_WAVE_DAMAGE;
			helper.assertTrue(near(total, expected), "slash 1 + the shockwave = " + expected + ", dealt " + total);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftMovesShortOnKineticSpendNothing(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 5));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, s.getEyePosition().add(0, -0.4, 0));
		BlackPanther.setKinetic(p, 10f);
		Vec3 at = p.position();
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4 }) {
			press(p, slot, true);
		}
		helper.assertTrue(BlackPanther.kinetic(p) == 10f, "nothing spent, got " + BlackPanther.kinetic(p));
		helper.assertTrue(s.getHealth() == 200f, "nothing fired");
		helper.assertTrue(p.position().distanceToSqr(at) < 1.0e-4, "no dash");
		helper.assertTrue(BlackPantherAbilities.pounceDirection(p) == null, "no Kinetic Pounce leap");
		for (String id : new String[] { BlackPantherAbilities.KINETIC_COMBO, BlackPantherAbilities.KINETIC_POUNCE, BlackPantherAbilities.DASH,
				BlackPantherAbilities.OVERLOAD }) {
			helper.assertTrue(BlackPanther.cooldownRemaining(p, id) == 0, id + ": no cooldown on a refusal");
		}
		helper.assertTrue(BlackPantherAbilities.kineticDenied(p), "the HUD's Kinetic bar flashes");
		// v0.17.7: Shift+V (Wakanda Forever) is never refused -- short of 30 it is the buffs alone, nothing spent, no Shroud
		press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(BlackPanther.kinetic(p) == 10f && !BlackPantherAbilities.shrouded(p), "Wakanda Forever under 30: nothing spent, no Shroud");
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.WAKANDA) > 0, "but it fired");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pounceLandsPinsAndFillsKinetic(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 6));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, s.getEyePosition().add(0, -0.4, 0));
		BlackPanther.setKinetic(p, 0f);
		press(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.POUNCE) > 0, "G: Pounce");
		helper.assertTrue(BlackPanther.kinetic(p) == 0f, "the plain pounce costs nothing");
		// the server launches him with velocity (mock players do not move): put him where the leap ends
		p.teleportTo(s.getX(), s.getY(), s.getZ() - 1.2);
		BlackPanther.tick(p);
		float dealt = 200f - s.getHealth();
		helper.assertTrue(near(dealt, BlackPantherConfig.POUNCE_LAND_DAMAGE), "landing, dealt " + dealt);
		helper.assertTrue(near(BlackPanther.kinetic(p), BlackPantherConfig.POUNCE_KINETIC_GAIN), "landing adds Kinetic, got " + BlackPanther.kinetic(p));
		var slow = s.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getDuration() <= BlackPantherConfig.POUNCE_PIN_TICKS, "pinned for the pin time");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kineticPounceBurstsRoundTheLanding(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 6));
		Vindicator other = dummy(helper, new BlockPos(4, 2, 6));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, s.getEyePosition().add(0, -0.4, 0));
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(near(BlackPanther.kinetic(p), 50f - BlackPantherConfig.KINETIC_POUNCE_COST), "the cost");
		p.teleportTo(s.getX(), s.getY(), s.getZ() - 1.2);
		BlackPanther.tick(p);
		helper.assertTrue(near(200f - s.getHealth(), BlackPantherConfig.KINETIC_POUNCE_DAMAGE), "the target takes the burst, " + (200f - s.getHealth()));
		helper.assertTrue(near(200f - other.getHealth(), BlackPantherConfig.KINETIC_POUNCE_DAMAGE), "so does one beside it, " + (200f - other.getHealth()));
		helper.assertTrue(near(BlackPanther.kinetic(p), 50f - BlackPantherConfig.KINETIC_POUNCE_COST), "a Shift move adds nothing back");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kineticDashHitsWhatItPassesAndIsUntouchable(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		Vindicator attacker = dummy(helper, new BlockPos(5, 2, 2));
		BlackPanther.setKinetic(p, 50f);
		Vec3 start = p.position();
		press(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(near(BlackPanther.kinetic(p), 50f - BlackPantherConfig.DASH_COST), "the cost");
		Vec3 step = p.position().subtract(start);
		double per = BlackPantherConfig.DASH_DISTANCE / BlackPantherConfig.DASH_TICKS;
		helper.assertTrue(Math.abs(step.z - per) < 0.3 && Math.abs(step.x) < 0.01 && Math.abs(step.y) < 0.01,
				"the first step goes " + per + " straight ahead, went " + step);
		helper.assertTrue(near(200f - s.getHealth(), BlackPantherConfig.DASH_DAMAGE), "passed through: " + (200f - s.getHealth()));
		helper.assertTrue(BlackPantherAbilities.dashInvulnerable(p), "untouchable during the dash");
		float hp = p.getHealth();
		float k = BlackPanther.kinetic(p);
		p.hurt(p.damageSources().mobAttack(attacker), 10f);
		helper.assertTrue(p.getHealth() == hp && BlackPanther.kinetic(p) == k, "a hit during the dash does nothing");
		// it runs on into the test's barrier wall and stops flush against it, never inside
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(BlackPantherConfig.DASH_TICKS + BlackPantherConfig.DASH_IFRAME_EXTRA_TICKS + 3, () -> {
			helper.assertTrue(p.level().noCollision(p, p.getBoundingBox()), "stopped clear of the wall");
			helper.assertTrue(!BlackPantherAbilities.dashInvulnerable(p), "the i-frames end");
			helper.assertTrue(near(200f - s.getHealth(), BlackPantherConfig.DASH_DAMAGE), "each creature is struck once");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kineticDashFollowsThePitch(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		BlackPanther.setKinetic(p, 50f);
		p.setXRot(-45f); // up and forward
		p.setYRot(0f);
		Vec3 start = p.position();
		press(p, AbilitySlot.SLOT_3, true);
		Vec3 step = p.position().subtract(start);
		double per = BlackPantherConfig.DASH_DISTANCE / BlackPantherConfig.DASH_TICKS;
		double expect = per * Math.sqrt(0.5);
		helper.assertTrue(Math.abs(step.y - expect) < 0.3 && Math.abs(step.z - expect) < 0.3, "45 degrees up: " + expect + " up and on, went " + step);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void overloadNeedsAFullBarAndEmptiesIt(GameTestHelper helper) {
		helper.assertTrue(near(BlackPantherAbilities.overloadDamage(0), BlackPantherConfig.OVERLOAD_DAMAGE)
				&& near(BlackPantherAbilities.overloadDamage(BlackPantherConfig.OVERLOAD_RADIUS), BlackPantherConfig.OVERLOAD_EDGE_DAMAGE),
				"centre to edge falloff");
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 5));
		BlackPanther.setKinetic(p, BlackPantherConfig.KINETIC_MAX - 1f);
		press(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(BlackPanther.kinetic(p) == BlackPantherConfig.KINETIC_MAX - 1f && s.getHealth() == 200f, "refused one short of full");
		helper.assertTrue(BlackPantherAbilities.kineticDenied(p), "the bar flashes");
		BlackPanther.setKinetic(p, BlackPantherConfig.KINETIC_MAX);
		press(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(BlackPanther.kinetic(p) == 0f, "the whole bar goes");
		float dealt = 200f - s.getHealth();
		helper.assertTrue(dealt >= BlackPantherConfig.OVERLOAD_EDGE_DAMAGE - 0.05f && dealt <= BlackPantherConfig.OVERLOAD_DAMAGE + 0.05f,
				"between the edge and centre values, dealt " + dealt);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
	public void kineticHoldsInTheSuitLeaksInTheNecklaceAndADeathEmptiesIt(GameTestHelper helper) {
		// user: "make kinetic energy dissipate slowly when suit is in necklace mode please" -- suited no decay; unsuited every change is
		// exactly one drain step, only on a drain tick (step sizes, not elapsed totals: CI ticks mock players at its own rate)
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		BlackPanther.setKinetic(p, 60f);
		float[] last = { 60f };
		int[] steps = { 0 };
		boolean[] off = { false, false }; // [0] the suit is put away, [1] the test is done (the death below empties it)
		helper.onEachTick(() -> {
			if (off[1]) {
				return;
			}
			BlackPanther.tick(p);
			float k = BlackPanther.kinetic(p);
			if (k != last[0]) {
				helper.assertTrue(off[0], "no change while suited, " + last[0] + " -> " + k);
				helper.assertTrue(Math.abs(last[0] - k - BlackPantherConfig.KINETIC_UNSUITED_DRAIN) < 1.0e-4, "one drain step, " + last[0] + " -> " + k);
				helper.assertTrue(BlackPanther.kineticDrains(p.level().getGameTime()), "only on a drain tick");
				steps[0]++;
				last[0] = k;
			}
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(BlackPanther.kinetic(p) == 60f, "10 s idle in the suit: still 60, got " + BlackPanther.kinetic(p));
			BlackPantherGameTests.takeOffSuit(p);
			off[0] = true;
		});
		helper.runAfterDelay(200 + 4 * BlackPantherConfig.KINETIC_UNSUITED_DRAIN_EVERY + 2, () -> {
			helper.assertTrue(steps[0] >= 3, "the suit in the necklace: it leaks, " + steps[0] + " steps");
			helper.assertTrue(BlackPantherConfig.KINETIC_UNSUITED_DRAIN == 1f && BlackPantherConfig.KINETIC_UNSUITED_DRAIN_EVERY == 40, "1 every 2 s");
			off[1] = true;
			BlackPanther.onDeath(p);
			helper.assertTrue(BlackPanther.kinetic(p) == 0f, "a death empties it");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void suitedWakandaForeverWithThirtyAddsTheShroud(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator attacker = dummy(helper, new BlockPos(2, 2, 4));
		// the soaked share of a hit is stored as Kinetic: the stored amount measures what the hit was, before armour
		BlackPanther.setKinetic(p, 0f);
		p.hurt(p.damageSources().mobAttack(attacker), 10f);
		float plain = BlackPanther.kinetic(p);
		helper.assertTrue(attacker.getHealth() == 200f, "no thorns without the Shroud");
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(BlackPantherAbilities.shrouded(p), "Shift+V with 30+: Wakanda Forever and the Shroud");
		helper.assertTrue(near(BlackPanther.kinetic(p), 50f - BlackPantherConfig.SHROUD_COST), "30 spent");
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.SHROUD_ACTIVE) == BlackPantherConfig.RESOLVE_TICKS,
				"the Shroud lasts as long as Wakanda Forever (50 s)");
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.WAKANDA) == BlackPantherConfig.RESOLVE_COOLDOWN, "100 s cooldown");
		var strength = p.getEffect(MobEffects.DAMAGE_BOOST);
		helper.assertTrue(strength != null && strength.getAmplifier() == BlackPantherConfig.RESOLVE_STRENGTH, "the Wakanda Forever buffs");
		BlackPanther.setKinetic(p, 0f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(attacker), 10f);
		float shrouded = BlackPanther.kinetic(p);
		helper.assertTrue(plain > 0f && Math.abs(shrouded - plain * (1f - BlackPantherConfig.SHROUD_REDUCTION)) < 0.05f,
				"the hit is cut: " + plain + " -> " + shrouded);
		helper.assertTrue(near(200f - attacker.getHealth(), BlackPantherConfig.SHROUD_THORNS), "thorns, dealt " + (200f - attacker.getHealth()));
		BlackPantherGameTests.takeOffSuit(p);
		helper.assertTrue(!BlackPantherAbilities.shrouded(p), "the suit going ends it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void daggerHitFillsKinetic(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 5));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, s.getEyePosition().add(0, -0.4, 0));
		BlackPanther.setKinetic(p, 0f);
		VibraniumDaggerEntity.shoot(p);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(s.getHealth() < 200f, "the dagger hit");
			helper.assertTrue(near(BlackPanther.kinetic(p), BlackPantherConfig.DAGGER_KINETIC_GAIN), "one hit's Kinetic, got " + BlackPanther.kinetic(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRemovedMovesAreGoneFromTheSuitedKit(GameTestHelper helper) {
		for (AbilitySlot slot : AbilitySlot.values()) {
			for (String id : BlackPantherAbilityManager.idsOf(slot, true)) {
				helper.assertTrue(!id.equals("kimoyo_flash") && !id.equals("panther_senses") && !id.equals("kinetic_pulse"), "removed: " + id);
			}
		}
		helper.assertTrue(BlackPantherAbilityManager.idsOf(AbilitySlot.SLOT_4, true)[1].equals(BlackPantherAbilities.OVERLOAD), "Shift+Z: Overload");
		helper.assertTrue(BlackPantherAbilityManager.idsOf(AbilitySlot.SLOT_5, true)[1].equals(BlackPantherAbilities.WAKANDA)
				&& BlackPantherAbilities.WAKANDA.equals(com.projecthero.mod.blackpanther.BlackPantherUnsuited.RESOLVE),
				"Shift+V in the suit: Wakanda Forever (the Kinetic Shroud is no separate move)");
		helper.succeed();
	}

	// ================================================================ v0.17.7 round: the user's tuning requests

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theUsersNumbers(GameTestHelper helper) {
		helper.assertTrue(BlackPantherConfig.POUNCE_LAND_DAMAGE == 12f, "Pounce 12");
		helper.assertTrue(BlackPantherConfig.KINETIC_POUNCE_DAMAGE == 25f, "Kinetic Pounce 25");
		helper.assertTrue(BlackPantherConfig.DASH_DAMAGE == 18f, "Kinetic Dash 18");
		helper.assertTrue(BlackPantherConfig.KNIFE_COOLDOWN == 40 && BlackPantherConfig.DAGGER_COOLDOWN == 60, "knife 2 s, dagger 3 s");
		helper.assertTrue(BlackPantherConfig.SHROUD_TICKS == BlackPantherConfig.RESOLVE_TICKS, "the Shroud lasts as long as Wakanda Forever");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void daggerAndKnifeCooldownsAreShorter(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		press(p, AbilitySlot.SLOT_4, false);
		int knife = BlackPanther.cooldownRemaining(p, com.projecthero.mod.blackpanther.BlackPantherUnsuited.KNIFE);
		helper.assertTrue(knife == BlackPantherConfig.KNIFE_COOLDOWN, "unsuited Z: Throwing Knife 2 s, got " + knife);
		BlackPantherGameTests.wearSuit(p);
		BlackPantherGameTests.finishChange(p);
		press(p, AbilitySlot.SLOT_4, false);
		int dagger = BlackPanther.cooldownRemaining(p, BlackPantherAbilities.DAGGER);
		helper.assertTrue(dagger == BlackPantherConfig.DAGGER_COOLDOWN, "suited Z: Vibranium Dagger 3 s, got " + dagger);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pantherLeapFollowsTheAim(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		p.setYRot(0f);
		p.setXRot(-30f); // up and ahead
		press(p, AbilitySlot.SLOT_3, false);
		Vec3 v = p.getDeltaMovement();
		Vec3 want = p.getLookAngle().scale(BlackPantherConfig.LEAP_SPEED);
		helper.assertTrue(v.distanceTo(want) < 1.0e-3, "launched along the look, pitch included: want " + want + ", got " + v);
		helper.assertTrue(v.z > 1.0 && v.y > 0.7, "forward as much as up at 30 degrees, " + v);
		// straight up is still the old ~12-block leap; a level aim off the ground still leaves it; mid-air a downward aim dives
		helper.assertTrue(Math.abs(BlackPantherAbilities.leapVelocity(new Vec3(0, 1, 0), true).y - BlackPantherConfig.LEAP_SPEED) < 1.0e-6, "up");
		Vec3 level = BlackPantherAbilities.leapVelocity(new Vec3(0, 0, 1), true);
		helper.assertTrue(Math.abs(level.z - BlackPantherConfig.LEAP_SPEED) < 1.0e-6 && level.y == BlackPantherConfig.LEAP_MIN_LIFT, "level: " + level);
		helper.assertTrue(BlackPantherAbilities.leapVelocity(new Vec3(0, -1, 0), false).y < -1.0, "a mid-air dive");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pounceAndKineticPounceFireWithNoTarget(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		p.setYRot(0f);
		p.setXRot(-20f);
		BlackPanther.setKinetic(p, 0f);
		press(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.POUNCE) > 0, "G with nothing to aim at still fires");
		Vec3 dir = BlackPantherAbilities.pounceDirection(p);
		helper.assertTrue(dir != null && BlackPantherAbilities.pounceTarget(p) == -1, "a leap along the aim, no target");
		helper.assertTrue(dir.dot(p.getLookAngle()) > 0.98, "along the look (pitch included), " + dir);
		ServerPlayer q = BlackPantherGameTests.suited(helper);
		q.setYRot(0f);
		BlackPanther.setKinetic(q, 50f);
		press(q, AbilitySlot.SLOT_2, true);
		helper.assertTrue(BlackPanther.cooldownRemaining(q, BlackPantherAbilities.KINETIC_POUNCE) > 0, "Shift+G with nothing to aim at fires too");
		helper.assertTrue(near(BlackPanther.kinetic(q), 50f - BlackPantherConfig.KINETIC_POUNCE_COST), "and costs its 30");
		helper.assertTrue(BlackPantherAbilities.pounceDirection(q) != null, "leaping");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pounceAssistOnlyInsideTheConeAndNeverSteers(GameTestHelper helper) {
		Vec3 eye = Vec3.ZERO, look = new Vec3(0, 0, 1);
		double r = BlackPantherConfig.POUNCE_RANGE;
		double a5 = Math.toRadians(5), a12 = Math.toRadians(12);
		helper.assertTrue(BlackPantherAbilities.assistAngle(eye, look, new Vec3(Math.sin(a5) * 5, 0, Math.cos(a5) * 5), r) < 6.0, "5 degrees off: picked");
		helper.assertTrue(BlackPantherAbilities.assistAngle(eye, look, new Vec3(Math.sin(a12) * 5, 0, Math.cos(a12) * 5), r) == Double.MAX_VALUE,
				"12 degrees off: outside the 8-degree cone");
		helper.assertTrue(BlackPantherAbilities.assistAngle(eye, look, new Vec3(0, 0, r + 1), r) == Double.MAX_VALUE, "out of range");
		// in the world: a creature well off the crosshair is not picked...
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator s = dummy(helper, new BlockPos(5, 2, 5));
		p.setYRot(0f);
		p.setXRot(0f);
		helper.assertTrue(BlackPantherAbilities.assistTarget(p) == null, "about 45 degrees off: no assist");
		// ...one just off it is, and the leap is aimed at it once, at launch
		p.lookAt(EntityAnchorArgument.Anchor.EYES, s.getEyePosition().add(0.3, -0.4, 0));
		helper.assertTrue(BlackPantherAbilities.assistTarget(p) == s, "a few degrees off: assisted");
		press(p, AbilitySlot.SLOT_2, false);
		Vec3 dir = BlackPantherAbilities.pounceDirection(p);
		helper.assertTrue(dir != null && BlackPantherAbilities.pounceTarget(p) == s.getId(), "aimed at the assisted creature");
		// it moves away sideways: the leap does not follow
		s.teleportTo(s.getX() - 3.0, s.getY(), s.getZ());
		BlackPanther.tick(p);
		Vec3 after = BlackPantherAbilities.pounceDirection(p);
		helper.assertTrue(after != null && after.distanceTo(dir) < 1.0e-9, "the direction is fixed after launch: " + dir + " -> " + after);
		helper.assertTrue(p.getDeltaMovement().normalize().distanceTo(dir) < 1.0e-3, "and he is still driven along it, " + p.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wakandaForeverCooldownIsSharedAcrossTheSuit(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		press(p, AbilitySlot.SLOT_6, true); // unsuited Shift+C
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.WAKANDA) > 0, "unsuited Wakanda Forever");
		BlackPantherGameTests.wearSuit(p);
		BlackPantherGameTests.finishChange(p);
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_5, true); // suited Shift+V
		helper.assertTrue(BlackPanther.kinetic(p) == 50f && !BlackPantherAbilities.shrouded(p), "suiting up gives no second cast (nothing spent, no Shroud)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void suitedWakandaForeverKeepsItsRegenerationWhenTheSuitGoes(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		BlackPanther.setKinetic(p, 0f);
		press(p, AbilitySlot.SLOT_5, true);
		BlackPantherGameTests.takeOffSuit(p);
		BlackPanther.reconcile(p);
		var regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == BlackPantherConfig.RESOLVE_REGEN && !regen.isInfiniteDuration()
				&& regen.getDuration() > BlackPantherConfig.RESOLVE_TICKS - 20, "Regeneration II carries on after the suit's own II goes, " + regen);
		var res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == BlackPantherConfig.RESOLVE_RESISTANCE, "Resistance II too, " + res);
		helper.succeed();
	}
}
