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
 * decays and a death empties it. The numbers are read from {@link BlackPantherConfig} so the user's own values drop in. Targets are
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
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5 }) {
			press(p, slot, true);
		}
		helper.assertTrue(BlackPanther.kinetic(p) == 10f, "nothing spent, got " + BlackPanther.kinetic(p));
		helper.assertTrue(s.getHealth() == 200f, "nothing fired");
		helper.assertTrue(p.position().distanceToSqr(at) < 1.0e-4, "no dash");
		helper.assertTrue(!BlackPantherAbilities.shrouded(p), "no shroud");
		for (String id : new String[] { BlackPantherAbilities.KINETIC_COMBO, BlackPantherAbilities.KINETIC_POUNCE, BlackPantherAbilities.DASH,
				BlackPantherAbilities.OVERLOAD, BlackPantherAbilities.SHROUD }) {
			helper.assertTrue(BlackPanther.cooldownRemaining(p, id) == 0, id + ": no cooldown on a refusal");
		}
		helper.assertTrue(BlackPantherAbilities.kineticDenied(p), "the HUD's Kinetic bar flashes");
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

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void kineticNeverDecaysAndADeathEmptiesIt(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		BlackPanther.setKinetic(p, 60f);
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(BlackPanther.kinetic(p) == 60f, "15 s idle in the suit: still 60, got " + BlackPanther.kinetic(p));
			BlackPantherGameTests.takeOffSuit(p);
			for (int i = 0; i < 40; i++) {
				BlackPanther.tick(p);
			}
			helper.assertTrue(BlackPanther.kinetic(p) == 60f, "the suit put away: still 60");
			BlackPanther.onDeath(p);
			helper.assertTrue(BlackPanther.kinetic(p) == 0f, "a death empties it");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shroudHalvesHitsAndThornsTheAttacker(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.suited(helper);
		Vindicator attacker = dummy(helper, new BlockPos(2, 2, 4));
		// the soaked share of a hit is stored as Kinetic: the stored amount measures what the hit was, before armour
		BlackPanther.setKinetic(p, 0f);
		p.hurt(p.damageSources().mobAttack(attacker), 10f);
		float plain = BlackPanther.kinetic(p);
		helper.assertTrue(attacker.getHealth() == 200f, "no thorns without the Shroud");
		BlackPanther.setKinetic(p, 50f);
		press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(BlackPantherAbilities.shrouded(p), "Shift+V: the Shroud is up");
		helper.assertTrue(near(BlackPanther.kinetic(p), 50f - BlackPantherConfig.SHROUD_COST), "the cost");
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
		helper.succeed();
	}
}
