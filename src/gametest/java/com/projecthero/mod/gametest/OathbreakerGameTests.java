package com.projecthero.mod.gametest;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.oathbreaker.OathbreakerTuning;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntityTypes;
import com.projecthero.mod.oathbreaker.entity.OathbreakerThreat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19 Oathbreaker coverage: the threat/target-switching rule, the 50-block follow range surviving a reload,
 * and the two new attacks (Oathbound Whirlwind, Grave Geysers) running every step without throwing.
 *
 * <p>Mock GameTest players report {@code isCreative() == true}, which the boss never targets or hits, so these use
 * husks (NoAI, no daylight burning) as attackers/victims. Every test that spawns the boss gets its own batch: his
 * attacks reach several blocks and must not land in a neighbouring test's area.
 */
public class OathbreakerGameTests implements FabricGameTest {

	private static OathbreakerEntity boss(GameTestHelper helper, Vec3 rel) {
		OathbreakerEntity boss = OathbreakerEntityTypes.OATHBREAKER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		boss.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(boss);
		return boss;
	}

	private static Husk dummy(GameTestHelper helper, Vec3 rel) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		h.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		h.setHealth(500.0f);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	/** A landed hit from {@code attacker} (i-frames cleared first so every call counts in full). */
	private static void hitBy(OathbreakerEntity boss, net.minecraft.world.entity.LivingEntity attacker, float amount) {
		boss.invulnerableTime = 0;
		boss.hurt(boss.damageSources().mobAttack(attacker), amount);
	}

	// ---------------------------------------------------------------- threat rule (pure)

	@GameTest(template = EMPTY_STRUCTURE)
	public void threatRuleSwitchesOnMarginIdleAndRespectsLockout(GameTestHelper helper) {
		Pig a = EntityType.PIG.create(helper.getLevel());
		Pig b = EntityType.PIG.create(helper.getLevel());
		int now = 1000;

		OathbreakerThreat t = new OathbreakerThreat();
		helper.assertTrue(t.shouldSwitch(null, b, now, 0, 25), "no target at all -> anyone");
		helper.assertFalse(t.shouldSwitch(a, b, now, 25, 25), "someone who never hit him is no reason to switch");
		t.addHit(b, 5.0f, now);
		helper.assertTrue(t.shouldSwitch(a, b, now, 25, 400), "a target who never hit him loses him to anyone who does (the lure case)");

		t = new OathbreakerThreat();
		t.addHit(a, 20.0f, now);
		t.addHit(b, 22.0f, now);
		helper.assertFalse(t.shouldSwitch(a, b, now, 4, 4), "22 vs 20 is inside the 1.25x margin -- no ping-pong");
		t.addHit(b, 6.0f, now);
		helper.assertTrue(t.shouldSwitch(a, b, now, 4, 4), "28 vs 20 beats the margin");
		t.noteSwitched(now);
		t.addHit(a, 200.0f, now + 1);
		helper.assertFalse(t.shouldSwitch(b, a, now + 1, 4, 4), "switch lockout");
		helper.assertTrue(t.shouldSwitch(b, a, now + OathbreakerTuning.THREAT_SWITCH_LOCKOUT_TICKS, 4, 4), "after the lockout");

		t = new OathbreakerThreat();
		t.addHit(a, 20.0f, now);
		int later = now + OathbreakerTuning.THREAT_IDLE_TICKS + 10;
		t.addHit(b, 2.0f, later);
		helper.assertTrue(t.shouldSwitch(a, b, later, 100, 9), "target idle 4s+, the attacker hit recently and is closer");
		helper.assertFalse(t.shouldSwitch(a, b, later, 9, 100), "...but not if the attacker is further away");

		float half = t.threatOf(a, now + OathbreakerTuning.THREAT_HALF_LIFE_TICKS);
		helper.assertTrue(Math.abs(half - 10.0f) < 0.01f, "threat halves every half-life, got " + half);
		helper.succeed();
	}

	// ---------------------------------------------------------------- threat rule (on the real entity)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "oathbreaker_threat")
	public void anotherAttackerTakesHisAttention(GameTestHelper helper) {
		OathbreakerEntity boss = boss(helper, new Vec3(2.5, 2, 2.5));
		boss.debugHoldAttacks(100000); // nothing of his own gets in the way (no Oath Guard parries)
		Husk lure = dummy(helper, new Vec3(6.5, 2, 2.5));
		Husk hitter = dummy(helper, new Vec3(2.5, 2, 6.5));
		boss.setTarget(lure);

		hitBy(boss, hitter, 10.0f);
		helper.assertTrue(boss.getTarget() == hitter, "the lure never hit him: the first hit from someone else turns him");
		hitBy(boss, lure, 50.0f);
		helper.assertTrue(boss.getTarget() == hitter, "but not straight back -- the switch lockout");

		helper.runAfterDelay(OathbreakerTuning.THREAT_SWITCH_LOCKOUT_TICKS + 2, () -> {
			hitBy(boss, lure, 5.0f);
			helper.assertTrue(boss.getTarget() == lure, "after the lockout the heavier attacker takes over");
			hitBy(boss, hitter, 12.0f);
			helper.assertTrue(boss.getTarget() == lure, "a lighter attacker inside the margin doesn't");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void followRangeIsFiftyAndSurvivesAReload(GameTestHelper helper) {
		helper.assertTrue(OathbreakerTuning.FOLLOW_RANGE >= 50.0, "tracks from 50 blocks");
		helper.assertTrue(OathbreakerTuning.BOSS_BAR_RADIUS >= OathbreakerTuning.FOLLOW_RANGE, "boss bar reaches as far as he tracks");
		OathbreakerEntity old = OathbreakerEntityTypes.OATHBREAKER.create(helper.getLevel());
		helper.assertTrue(old.getAttributeBaseValue(Attributes.FOLLOW_RANGE) == OathbreakerTuning.FOLLOW_RANGE, "fresh boss");
		old.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0); // a boss saved by an older version
		CompoundTag tag = old.saveWithoutId(new CompoundTag());
		OathbreakerEntity loaded = OathbreakerEntityTypes.OATHBREAKER.create(helper.getLevel());
		loaded.load(tag);
		helper.assertTrue(loaded.getAttributeBaseValue(Attributes.FOLLOW_RANGE) == OathbreakerTuning.FOLLOW_RANGE,
				"the saved 48 must be replaced on load, got " + loaded.getAttributeBaseValue(Attributes.FOLLOW_RANGE));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the two new attacks

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140, batch = "oathbreaker_whirlwind")
	public void whirlwindRunsEveryStepAndHitsAllRound(GameTestHelper helper) {
		helper.assertTrue(OathbreakerTuning.WHIRLWIND_SPIN_TICKS + OathbreakerTuning.WHIRLWIND_RECOVER_TICKS
				== OathbreakerTuning.WHIRLWIND_STRIKE_TICKS, "spins + recovery == the strike clip");
		OathbreakerEntity boss = boss(helper, new Vec3(4.5, 2, 4.5));
		Husk front = dummy(helper, new Vec3(4.5, 2, 7.5));
		Husk behind = dummy(helper, new Vec3(4.5, 2, 1.5));
		boss.setTarget(front);
		boss.debugBeginAttack("WHIRLWIND");
		helper.assertTrue("WHIRLWIND".equals(boss.debugActiveAttack()), "the whirlwind starts");

		Set<Integer> steps = new HashSet<>();
		boolean[] hyperDuringSpin = { true };
		boolean[] openDuringRecovery = { true };
		boolean[] done = { false };
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}
			if ("WHIRLWIND".equals(boss.debugActiveAttack())) {
				int step = boss.debugAttackStep();
				steps.add(step);
				double kr = boss.getAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE);
				if (step == 1 && kr != OathbreakerTuning.KNOCKBACK_RESISTANCE_HYPER_ARMOR) {
					hyperDuringSpin[0] = false;
				}
				if (step == 2 && kr != OathbreakerTuning.KNOCKBACK_RESISTANCE_BASE) {
					openDuringRecovery[0] = false;
				}
			} else if (!steps.isEmpty()) {
				done[0] = true;
				boss.debugHoldAttacks(100000);
			}
		});
		helper.runAfterDelay(OathbreakerTuning.WHIRLWIND_WINDUP_TICKS + OathbreakerTuning.WHIRLWIND_STRIKE_TICKS + 10, () -> {
			helper.assertTrue(done[0], "the whirlwind finished");
			helper.assertTrue(steps.contains(0) && steps.contains(1) && steps.contains(2), "wind-up, spins and recovery all ran: " + steps);
			helper.assertTrue(hyperDuringSpin[0], "hyper armor through the spins");
			helper.assertTrue(openDuringRecovery[0], "no hyper armor in the dizzy recovery (punish window)");
			helper.assertTrue(front.getHealth() < front.getMaxHealth(), "the husk in front is cut");
			helper.assertTrue(behind.getHealth() < behind.getMaxHealth(), "and so is the one behind him");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "oathbreaker_geysers")
	public void graveGeysersRunEveryStepAndEruptUnderTheTarget(GameTestHelper helper) {
		OathbreakerEntity boss = boss(helper, new Vec3(2.5, 2, 2.5));
		Husk far = dummy(helper, new Vec3(2.5, 2, 12.5)); // 10 blocks: geyser range, out of every melee reach
		Husk bystander = dummy(helper, new Vec3(9.5, 2, 2.5)); // not a player, not his target: no geyser
		boss.setTarget(far);
		boss.debugBeginAttack("GRAVE_GEYSERS");
		helper.assertTrue("GRAVE_GEYSERS".equals(boss.debugActiveAttack()), "the geysers start");

		Set<Integer> steps = new HashSet<>();
		boolean[] burned = { false };
		boolean[] done = { false };
		helper.onEachTick(() -> {
			if (far.getRemainingFireTicks() > 0) {
				burned[0] = true;
			}
			if (done[0]) {
				return;
			}
			if ("GRAVE_GEYSERS".equals(boss.debugActiveAttack())) {
				steps.add(boss.debugAttackStep());
			} else if (!steps.isEmpty()) {
				done[0] = true;
				boss.debugHoldAttacks(100000);
			}
		});
		int total = OathbreakerTuning.GEYSER_WINDUP_TICKS + OathbreakerTuning.GEYSER_PLUNGE_TICKS + OathbreakerTuning.GEYSER_BOWED_TICKS;
		helper.runAfterDelay(total + 10, () -> {
			helper.assertTrue(done[0], "the geysers finished");
			helper.assertTrue(steps.contains(0) && steps.contains(1) && steps.contains(2), "raise, plunge and bow all ran: " + steps);
			helper.assertTrue(far.getHealth() < far.getMaxHealth(), "the column erupted under his target 10 blocks away");
			helper.assertTrue(burned[0], "and set it alight");
			helper.assertTrue(bystander.getHealth() == bystander.getMaxHealth(), "nothing erupted under the bystander");
			helper.succeed();
		});
	}
}
