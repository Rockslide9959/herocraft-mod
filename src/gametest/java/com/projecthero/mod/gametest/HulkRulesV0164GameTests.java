package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkControl;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.HulkRoar;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.hulk.entity.MirrorHulkEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user, docs/HULK_V0164_REVAMP_CONTRACT.md): the Hulk's new rules. Only a death (as Banner, every time) or holding H
 * changes him; the 3-minute lock on changing back; the 1-minute hold-H cooldown after a willing Hulk changes back (a death
 * still forces the change); rage -> size (3.5 to 6 blocks) and rage -> damage (+0 to +50%); the minute-by-minute rampage
 * warning (typed in time, or a rampage); the ring streak (a miss resets it, three in a row end the rampage); the Hulk-form
 * death (the fall, the slow change back, then a real death the base-form change never catches); the N roar; and the mirror
 * Hulk looking the player in the eye. Mock players are not reliably ticked by the server, so each test drives
 * {@link Hulk#tick} itself.
 */
public class HulkRulesV0164GameTests implements FabricGameTest {
	private static ServerPlayer gamma(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().invulnerable = false;
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// room for a 6-block Hulk
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 8, 3))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		return p;
	}

	/** Out as the Hulk, the change already over (tryTransform skips it). */
	private static ServerPlayer hulk(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p) && !Hulk.changing(p), "precondition: the Hulk, ready");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<HulkState> change) {
		HulkState n = Hulk.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.HULK_STATE, n);
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	// ---------------------------------------------------------------- the change

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void onlyHoldingHChangesHimByChoice(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.onHurt(p, 60.0f);
		helper.assertTrue(Hulk.rage(p) == 0.0f, "Banner has no rage, got " + Hulk.rage(p));
		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		helper.assertFalse(Hulk.isHulk(p), "no rage ever changes him");
		Hulk.pressH(p);
		helper.assertFalse(Hulk.isHulk(p), "a press only starts the hold");
		helper.assertTrue(Hulk.state(p).combat.strainStart != 0L, "the hold has started");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkConfig.HOLD_TO_CHANGE_TICKS - 10, () -> {
			helper.assertFalse(Hulk.isHulk(p), "not before the hold is long enough");
			helper.assertTrue(Hulk.holdProgress(p) > 0.5f, "the hold is under way, got " + Hulk.holdProgress(p));
		});
		helper.runAfterDelay(HulkConfig.HOLD_TO_CHANGE_TICKS + 3, () -> {
			HulkState s = Hulk.state(p);
			helper.assertTrue(Hulk.isHulk(p), "holding H changes him");
			helper.assertFalse(s.combat.unwilling, "the willing change");
			helper.assertTrue(s.combat.transformStyle >= HulkState.TRANSFORM_QUICK && s.combat.transformStyle <= HulkState.TRANSFORM_STAGGER,
					"one of the six transformations, got " + s.combat.transformStyle);
			helper.assertTrue(Hulk.changing(p), "and the change is playing");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void lettingGoOfHEarlyCallsTheHoldOff(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.pressH(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(10, () -> {
			Hulk.releaseH(p);
			helper.assertTrue(Hulk.state(p).combat.strainStart == 0L, "releasing H ends the hold");
		});
		helper.runAfterDelay(HulkConfig.HOLD_TO_CHANGE_TICKS + 10, () -> {
			helper.assertFalse(Hulk.isHulk(p), "so he never changes");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bannersDeathChangesHimEveryTime(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		helper.assertTrue(Hulk.deathSaveReady(p), "Banner is protected");
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "a fatal hit is refused");
		helper.assertTrue(Hulk.isHulk(p) && Hulk.state(p).combat.unwilling, "and the Hulk comes out (not by choice)");
		helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 0.01f, "at full health");
		Hulk.revert(p, false);
		// no cooldown -- and even the hold-H cooldown does not stop it
		edit(p, n -> n.rules.holdCooldownUntil = now(p) + HulkConfig.WILLING_COOLDOWN_TICKS);
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "every time, even on the hold-H cooldown");
		helper.assertTrue(Hulk.isHulk(p), "the Hulk again");
		Hulk.revert(p, false);
		helper.assertFalse(Hulk.tryDeathSave(p, p.damageSources().genericKill()), "/kill always works");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heCantChangeBackForThreeMinutesAndAWillingHulkLeavesACooldown(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.pressH(p);
		helper.assertTrue(Hulk.isHulk(p), "locked: no changing back in the first 3 minutes");
		helper.assertTrue(Hulk.revertLock(p) > 0, "and the lock is counting down");
		edit(p, n -> n.formChangedAt = now(p) - HulkConfig.DETRANSFORM_LOCK_TICKS - 1);
		Hulk.pressH(p);
		helper.assertFalse(Hulk.isHulk(p), "after 3 minutes H changes him back");
		helper.assertTrue(Hulk.holdCooldown(p) > HulkConfig.WILLING_COOLDOWN_TICKS - 5, "a willing Hulk leaves a 1-minute cooldown");
		edit(p, n -> n.formChangedAt = now(p) - 100L); // past the H debounce
		Hulk.pressH(p);
		helper.assertTrue(Hulk.state(p).combat.strainStart == 0L, "holding H is refused on the cooldown");
		Hulk.tryTransform(p);
		helper.assertFalse(Hulk.isHulk(p), "no willing change on the cooldown");
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "but dying still forces the change");
		helper.assertTrue(Hulk.isHulk(p), "the Hulk is out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aHulkWhoCameOutByDyingLeavesNoCooldown(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.tryDeathSave(p, p.damageSources().generic());
		edit(p, n -> n.formChangedAt = now(p) - HulkConfig.DETRANSFORM_LOCK_TICKS - 1);
		Hulk.pressH(p);
		helper.assertFalse(Hulk.isHulk(p), "changed back");
		helper.assertTrue(Hulk.holdCooldown(p) == 0, "no hold-H cooldown after an unwilling Hulk");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroRageNeverChangesHimBack(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		edit(p, n -> {
			n.rage = 0.0f;
			n.lastCombatAt = -10_000L;
		});
		for (int i = 0; i < 40; i++) {
			Hulk.tick(p);
		}
		helper.assertTrue(Hulk.isHulk(p), "0 rage: still the Hulk");
		helper.succeed();
	}

	// ---------------------------------------------------------------- rage -> size, rage -> damage

	@GameTest(template = EMPTY_STRUCTURE)
	public void rageSetsHisHeightAndHisDamage(GameTestHelper helper) {
		helper.assertTrue(Math.abs(HulkConfig.heightForRage(0.0f) - 3.5) < 1.0e-6, "0 rage: 3.5 blocks");
		helper.assertTrue(Math.abs(HulkConfig.heightForRage(100.0f) - 6.0) < 1.0e-6, "100 rage: 6 blocks");
		helper.assertTrue(Math.abs(HulkConfig.heightForRage(50.0f) - 4.75) < 1.0e-6, "in between, smoothly");
		helper.assertTrue(Math.abs(HulkConfig.scaleForRage(0.0f) * 1.8 - 3.5) < 1.0e-6, "scale = height / 1.8");
		helper.assertTrue(Math.abs(HulkConfig.damageMultiplier(0.0f) - 1.0f) < 1.0e-6, "0 rage: normal damage");
		helper.assertTrue(Math.abs(HulkConfig.damageMultiplier(50.0f) - 1.25f) < 1.0e-6, "50 rage: +25%");
		helper.assertTrue(Math.abs(HulkConfig.damageMultiplier(100.0f) - 1.5f) < 1.0e-6, "100 rage: +50%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240, batch = "hulk_rules_size")
	public void theHulkGrowsWithHisRage(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		helper.onEachTick(() -> {
			Hulk.setRage(p, 100.0f);
			Hulk.tick(p);
		});
		helper.runAfterDelay(180, () -> {
			double scale = p.getAttributeValue(Attributes.SCALE);
			helper.assertTrue(Math.abs(scale - 6.0 / 1.8) < 0.02, "100 rage: 6 blocks tall (scale 3.33), got " + scale);
			helper.assertTrue(Math.abs(p.getBbHeight() - 6.0) < 0.05, "the hitbox follows, got " + p.getBbHeight());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_rules_damage")
	public void rageAddsUpToHalfAgainToEveryHit(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var husk = EntityType.HUSK.create(helper.getLevel());
		husk.moveTo(p.getX() + 2.0, p.getY(), p.getZ(), 0.0f, 0.0f);
		husk.setNoAi(true);
		helper.getLevel().addFreshEntity(husk);
		Hulk.setRage(p, 0.0f);
		helper.assertTrue(HulkDamage.allowDamage(husk, p.damageSources().playerAttack(p), 10.0f), "0 rage: the hit lands as it is");
		Hulk.setRage(p, 100.0f);
		float before = husk.getHealth();
		helper.assertFalse(HulkDamage.allowDamage(husk, p.damageSources().playerAttack(p), 10.0f), "100 rage: the hit is re-applied bigger");
		float lost = before - husk.getHealth();
		helper.assertTrue(lost > 14.0f && lost <= 15.01f, "10 becomes 15 (less the husk's 2 armour), got " + lost);
		husk.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the rampage

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRampageClockStartsWhenHeIsOut(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.transform(p, false);
		HulkState s = Hulk.state(p);
		long due = s.formChangedAt + Hulk.changeTicks(s) + HulkConfig.control().rampageEveryTicks;
		helper.assertTrue(s.rules.nextRampageAt == due, "a minute after the change, got " + s.rules.nextRampageAt + " want " + due);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void typingTheArrowsInTimeHoldsTheRampageBack(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		edit(p, n -> n.rules.nextRampageAt = now(p));
		Hulk.tick(p);
		HulkState s = Hulk.state(p);
		helper.assertTrue(s.rules.warnLen >= 6 && s.rules.warnLen <= 8, "the minute is up: a warning of 6-8 arrows, got " + s.rules.warnLen);
		helper.assertTrue(HulkControl.warning(s, now(p)), "and it is showing");
		// a right arrow, then a wrong one: back to the start
		int first = HulkControl.arrowAt(s.rules.warnSeq, 0);
		HulkControl.answer(p, first);
		helper.assertTrue(Hulk.state(p).rules.warnPos == 1, "the right arrow counts");
		int second = HulkControl.arrowAt(s.rules.warnSeq, 1);
		HulkControl.answer(p, second % 4 + 1);
		helper.assertTrue(Hulk.state(p).rules.warnPos == 0, "a wrong arrow starts the string over");
		for (int i = 0; i < s.rules.warnLen; i++) {
			HulkControl.answer(p, HulkControl.arrowAt(s.rules.warnSeq, i));
		}
		HulkState after = Hulk.state(p);
		helper.assertTrue(after.rules.warnLen == 0, "typed in full: the warning is gone");
		helper.assertFalse(HulkControl.rampaging(p), "no rampage");
		helper.assertTrue(after.rules.nextRampageAt >= now(p) + HulkConfig.control().rampageEveryTicks - 1, "the next minute starts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tooSlowAndHeRampages(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		edit(p, n -> n.rules.nextRampageAt = now(p));
		Hulk.tick(p);
		helper.assertTrue(Hulk.state(p).rules.warnLen > 0, "the warning is up");
		edit(p, n -> n.rules.warnUntil = now(p) - 1);
		Hulk.tick(p);
		helper.assertTrue(HulkControl.rampaging(p), "time ran out: he rampages");
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_RAMPAGE, "with the rampage roar");
		Hulk.pressH(p);
		helper.assertTrue(Hulk.isHulk(p), "and H does nothing while he rampages");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void aMissResetsTheRingStreakAndFiveInARowEndTheRampage(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		HulkControl.startRampage(p);
		helper.assertTrue(HulkControl.rampaging(p), "rampaging");
		int gap = HulkConfig.control().ringMinGapTicks + 2;
		HulkControl.ringResult(p, true);
		HulkControl.ringResult(p, true); // same tick: not a real press
		helper.assertTrue(Hulk.state(p).rules.ringStreak == 1, "one hit (a double report is ignored), got " + Hulk.state(p).rules.ringStreak);
		helper.runAfterDelay(gap, () -> {
			HulkControl.ringResult(p, true);
			helper.assertTrue(Hulk.state(p).rules.ringStreak == 2, "two in a row");
		});
		helper.runAfterDelay(gap * 2, () -> {
			HulkControl.ringResult(p, false);
			helper.assertTrue(Hulk.state(p).rules.ringStreak == 0, "a miss resets the streak");
			helper.assertTrue(HulkControl.rampaging(p), "still rampaging");
		});
		// v0.16.4 (user): "Increase the rampage contol minigame to 5 times instead of 3 times"
		int need = HulkConfig.control().ringHitsNeeded;
		helper.assertTrue(need == 5, "the ring needs 5 in a row, got " + need);
		for (int i = 1; i < need; i++) {
			int hits = i;
			helper.runAfterDelay(gap * (2 + i), () -> {
				HulkControl.ringResult(p, true);
				helper.assertTrue(HulkControl.rampaging(p), hits + " in a row is not enough");
			});
		}
		helper.runAfterDelay(gap * (2 + need), () -> {
			HulkControl.ringResult(p, true);
			helper.assertFalse(HulkControl.rampaging(p), "five in a row: control is back");
			helper.assertTrue(Hulk.state(p).rules.nextRampageAt > now(p), "and the next minute starts");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Hulk's death

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void theHulksDeathIsAFallTheChangeBackThenARealDeath(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "the killing blow is caught");
		helper.assertTrue(Hulk.isHulk(p) && Hulk.dying(p), "he lies there, still the Hulk");
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_DEATH_FALL, "flat on his back");
		helper.assertTrue(Hulk.changing(p), "he can't move or act");
		helper.assertFalse(HulkDamage.allowDamage(p, p.damageSources().generic(), 4.0f), "and nothing more hurts him");
		helper.onEachTick(() -> {
			if (p.isAlive()) {
				Hulk.tick(p);
			}
		});
		helper.runAfterDelay(HulkConfig.DEATH_LIE_TICKS + 5, () -> {
			helper.assertFalse(Hulk.isHulk(p), "then he changes back...");
			helper.assertTrue(Hulk.dying(p) && p.isAlive(), "...slowly, still alive");
		});
		helper.runAfterDelay(HulkConfig.DEATH_LIE_TICKS + HulkConfig.DEATH_REVERT_TICKS + HulkConfig.DEATH_FINAL_DELAY_TICKS + 8, () -> {
			helper.assertTrue(p.isDeadOrDying(), "and dies as the regular player");
			helper.assertFalse(Hulk.isHulk(p), "the base-form change did not catch it");
			helper.assertFalse(Hulk.dying(p), "the sequence is over");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- N: the roar

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_rules_roar")
	public void theRoarScaresOffTheSmallFry(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var pig = EntityType.PIG.create(helper.getLevel());
		pig.moveTo(p.getX() + 3.0, p.getY(), p.getZ(), 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(pig);
		var golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		golem.moveTo(p.getX() - 3.0, p.getY(), p.getZ(), 0.0f, 0.0f);
		golem.setNoAi(true);
		helper.getLevel().addFreshEntity(golem);
		HulkRoar.press(p);
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_ROAR, "the roar animation plays");
		helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, HulkRoar.ROAR) > 0, "and goes on cooldown");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkRoar.ROAR_IMPACT_TICKS + 4, () -> {
			helper.assertTrue(HulkRoar.fleeing(pig), "a pig (10 health) runs");
			helper.assertFalse(HulkRoar.fleeing(golem), "an iron golem (100 health) does not");
			pig.discard();
			golem.discard();
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the mirror

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMirrorHulkLooksHimInTheEye(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		double mirrorZ = p.getZ() - 6.0;
		p.moveTo(p.getX(), p.getY(), p.getZ(), 180.0f, -40.0f); // facing the mirror (-Z), looking up
		Vec3 feet = MirrorHulkEntity.reflect(p.position(), mirrorZ);
		float body = MirrorHulkEntity.reflectYaw(180.0f);
		float[] look = MirrorHulkEntity.headLook(p, feet, body, body, p.getXRot(), p.getEyePosition(), 3.0, mirrorZ);
		helper.assertTrue(look[1] > 0.0f, "looking up does not make him look up: the taller Hulk looks DOWN at his eyes, got " + look[1]);
		helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(look[0])) < 1.0f, "straight at him, got " + look[0]);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0.0f, -40.0f); // his back to the mirror
		float[] away = MirrorHulkEntity.headLook(p, feet, body, 123.0f, p.getXRot(), p.getEyePosition(), 3.0, mirrorZ);
		helper.assertTrue(away[0] == 123.0f && away[1] == -40.0f, "turned away: just the reflection");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRulesSurviveSaveAndLoad(GameTestHelper helper) {
		HulkState s = new HulkState(true, true, 42.5f, 10L, 20L, 30L);
		s.rules.nextRampageAt = 1234L;
		s.rules.warnSeq = 0b10_01_11;
		s.rules.warnLen = 3;
		s.rules.warnPos = 1;
		s.rules.warnUntil = 99L;
		s.rules.ringStreak = 2;
		s.rules.holdCooldownUntil = 555L;
		s.rules.deathFallAt = 777L;
		s.combat.transformStyle = HulkState.TRANSFORM_ARCH;
		var json = HulkState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		HulkState back = HulkState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.rules.nextRampageAt == 1234L && back.rules.warnSeq == 0b10_01_11 && back.rules.warnLen == 3
				&& back.rules.warnPos == 1 && back.rules.warnUntil == 99L && back.rules.ringStreak == 2 && back.rules.holdCooldownUntil == 555L
				&& back.rules.deathFallAt == 777L, "the rules round-trip");
		helper.assertTrue(back.combat.transformStyle == HulkState.TRANSFORM_ARCH, "and the transformation style");
		// an old save without the "rules" group still loads
		var old = json.getAsJsonObject().deepCopy();
		old.remove("rules");
		helper.assertTrue(HulkState.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().rules.deathFallAt == 0L, "an old save loads with fresh rules");
		helper.succeed();
	}
}
