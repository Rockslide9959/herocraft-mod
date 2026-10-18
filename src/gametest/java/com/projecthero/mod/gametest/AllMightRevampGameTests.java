package com.projecthero.mod.gametest;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightAbilityManager;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.AllMightPassOn;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the One For All revamp -- Output (20/45/100%) charged by hits and decaying, Strain and the recoil that locks the
 * Smashes, the five Shift moves' damage, Plus Ultra, Passing On (accept / refuse) and Lending (lender drawback, borrower cap,
 * return on time and on the borrower's death), and that One For All is never duplicated. Mock players are not ticked by the
 * server, so each test drives the ticks itself.
 */
public class AllMightRevampGameTests implements FabricGameTest {
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

	private static ServerPlayer plain(GameTestHelper helper, ServerPlayer near, double dx) {
		ServerPlayer o = helper.makeMockServerPlayerInLevel();
		o.setGameMode(GameType.SURVIVAL);
		o.moveTo(near.getX() + dx, near.getY(), near.getZ(), 0f, 0f);
		return o;
	}

	private static void powerForm(ServerPlayer p) {
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.reconcile(p);
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<AllMightState> fn) {
		AllMightState s = AllMight.state(p).copy();
		fn.accept(s);
		p.setAttached(ModAttachments.ALL_MIGHT_STATE, s);
	}

	/** A no-AI, armourless 200-HP zombie {@code distance} blocks along {@code yaw} (so the damage dealt reads exactly). */
	private static Zombie dummy(GameTestHelper helper, ServerPlayer p, double distance, float yaw) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false)); // a flung dummy takes no fall damage to blur the count
		z.setHealth(200.0f);
		Vec3 fwd = Vec3.directionFromRotation(0, yaw);
		z.moveTo(p.getX() + fwd.x * distance, p.getY(), p.getZ() + fwd.z * distance);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static void assertDealt(GameTestHelper helper, Zombie z, float expected, String what) {
		float dealt = 200.0f - z.getHealth();
		// +1.2 slack: a flung dummy can scrape 1 more off the test cage walls
		helper.assertTrue(dealt > expected - 0.6f && dealt < expected + 1.2f, what + ": expected " + expected + " damage, dealt " + dealt);
	}

	// ---------------------------------------------------------------- Output

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void outputLevelsUpOnHitsAndDecays(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		helper.assertTrue(AllMight.output(p) == AllMightState.OUTPUT_20, "starts at 20%");
		for (int i = 0; i < AllMightConfig.OUTPUT_HITS_TO_45; i++) {
			AllMight.onHitLanded(p, false);
		}
		helper.assertTrue(AllMight.output(p) == AllMightState.OUTPUT_45, "4 hits -> 45%, got " + AllMight.outputPercent(p));
		helper.assertTrue(Math.abs(AllMight.smashMultiplier(p) - 1.25f) < 1e-4f && AllMight.sizeScale(p) > 1.1, "45% is +25% and wider");
		AllMight.onHitLanded(p, false);
		helper.assertTrue(AllMight.state(p).outputProgress > 0.1f, "progress toward 100% shows");
		for (int i = 1; i < AllMightConfig.OUTPUT_HITS_TO_100; i++) {
			AllMight.onHitLanded(p, false);
		}
		helper.assertTrue(AllMight.output(p) == AllMightState.OUTPUT_100, "6 more -> 100%");
		helper.assertTrue(Math.abs(AllMight.smashMultiplier(p) - 1.6f) < 1e-4f, "100% is +60%");
		// 8 s without a hit: one level down
		edit(p, s -> s.lastHitAt = p.level().getGameTime() - AllMightConfig.OUTPUT_DECAY_TICKS);
		AllMight.tick(p);
		helper.assertTrue(AllMight.output(p) == AllMightState.OUTPUT_45, "decays one level after 8 s, got " + AllMight.outputPercent(p));
		// a real Smash landing charges once
		edit(p, s -> {
			s.output = AllMightState.OUTPUT_20;
			s.outputProgress = 0f;
		});
		Zombie z = dummy(helper, p, 3.0, 0f);
		AbilityRouter.handleInput(p, 1, true); // Detroit
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(AllMightConfig.DETROIT_WINDUP + 4, () -> {
			helper.assertTrue(z.getHealth() < 200f, "Detroit landed");
			helper.assertTrue(Math.abs(AllMight.state(p).outputProgress - 1f / AllMightConfig.OUTPUT_HITS_TO_45) < 1e-3f,
					"one Smash = one charge, progress " + AllMight.state(p).outputProgress);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Strain

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void strainBuildsAtHighOutputAndTheRecoilLocksSmashes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AllMight.setOutput(p, AllMightState.OUTPUT_100);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(40, () -> {
			// keep it at 100% (the decay clock), and check Strain grew at ~5/s
			float st = AllMight.strain(p);
			helper.assertTrue(st >= 5.0f && AllMight.output(p) == AllMightState.OUTPUT_100, "100% builds Strain, strain=" + st);
			AllMight.addStrain(p, AllMightConfig.STRAIN_MAX);
			helper.assertTrue(AllMight.inRecoil(p), "full Strain -> his arms give out");
			helper.assertTrue(AllMight.output(p) == AllMightState.OUTPUT_20, "forced back to 20%");
			helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS) && p.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1, "Weakness II");
			helper.assertTrue(p.hasEffect(MobEffects.DIG_SLOWDOWN), "Mining Fatigue");
			AbilityRouter.handleInput(p, 1, true); // Detroit
			AbilityRouter.handleInput(p, 2, true); // Texas
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0 && AllMight.cooldownRemaining(p, AllMightAbilities.TEXAS) == 0,
					"Smashes are locked in the recoil");
			AbilityRouter.handleInput(p, 6, true);
			helper.assertFalse(AllMight.plusUltraActive(p), "so is Plus Ultra");
			for (int i = 0; i < 10; i++) {
				AllMight.onHitLanded(p, false);
			}
			helper.assertTrue(AllMight.output(p) == AllMightState.OUTPUT_20, "hits do not charge in the recoil");
			AbilityRouter.handleInput(p, 3, true); // Leap is not a Smash
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.LEAP) > 0, "the Leap still works");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void strainRecoversAtTwentyPercentAndInTheBaseForm(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		edit(p, s -> s.strain = 50f);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(41, () -> {
			float st = AllMight.strain(p);
			helper.assertTrue(st < 50f - 5f, "the Base Form recovers ~5/s, strain=" + st);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void thePowerFormHasNoTimer(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(AllMight.isFullPower(p) && AllMight.strain(p) == 0f, "20% in the Power Form costs nothing and builds no Strain");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Shift moves

	private static void shift(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		p.setShiftKeyDown(false);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void delawareSmashFiresThreeTenDamageBullets(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		Zombie z = dummy(helper, p, 5.0, 0f);
		helper.onEachTick(() -> AllMight.tick(p));
		shift(p, 2);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DELAWARE) > 0, "Shift+G = Delaware");
		helper.runAfterDelay(AllMightConfig.DELAWARE_WINDUP + 2 * AllMightConfig.DELAWARE_FLICK_GAP + 6, () -> {
			assertDealt(helper, z, 3 * AllMightConfig.DELAWARE_DAMAGE, "three air bullets");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void manchesterSmashCraterHitsFor22(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		p.setOnGround(true);
		Zombie z = dummy(helper, p, 3.0, 90f);
		helper.onEachTick(() -> AllMight.tick(p));
		shift(p, 3);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.MANCHESTER) > 0, "Shift+X = Manchester");
		helper.runAfterDelay(AllMightConfig.MANCHESTER_WINDUP + AllMightConfig.MANCHESTER_FLIP_TICKS + 6, () -> {
			assertDealt(helper, z, AllMightConfig.MANCHESTER_DAMAGE, "the axe-kick crater");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void oklahomaSmashTornadoHitsAllRoundFor16(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		Zombie side = dummy(helper, p, 3.0, 90f);
		Zombie front = dummy(helper, p, 4.0, 0f);
		helper.onEachTick(() -> AllMight.tick(p));
		shift(p, 4);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.OKLAHOMA) > 0 && AllMight.state(p).chargeStart == 0L,
				"Shift+Z = Oklahoma, not the United States of Smash charge");
		helper.runAfterDelay(AllMightConfig.OKLAHOMA_WINDUP + AllMightConfig.OKLAHOMA_SPIN_TICKS + 4, () -> {
			assertDealt(helper, side, AllMightConfig.OKLAHOMA_DAMAGE, "the tornado (beside him)");
			assertDealt(helper, front, AllMightConfig.OKLAHOMA_DAMAGE, "the tornado (in front), once only");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void missouriSmashCutsForTwenty(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		Zombie z = dummy(helper, p, 6.0, 0f);
		helper.onEachTick(() -> AllMight.tick(p));
		shift(p, 5);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.MISSOURI) > 0, "Shift+V = Missouri");
		helper.runAfterDelay(AllMightConfig.MISSOURI_WINDUP + 6, () -> {
			assertDealt(helper, z, AllMightConfig.MISSOURI_DAMAGE, "the cut of air");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void stLouisSmashCrescentHitsFor18(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		Zombie z = dummy(helper, p, 7.0, 0f);
		helper.onEachTick(() -> AllMight.tick(p));
		shift(p, 6);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.ST_LOUIS) > 0 && !AllMight.plusUltraActive(p), "Shift+C = St. Louis, not Plus Ultra");
		helper.runAfterDelay(AllMightConfig.ST_LOUIS_WINDUP + 10, () -> {
			assertDealt(helper, z, AllMightConfig.ST_LOUIS_DAMAGE, "the crescent");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Plus Ultra

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void plusUltraIsOneHundredPercentWithNoStrainThenSeventyFive(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		edit(p, s -> s.strain = 10f);
		AbilityRouter.handleInput(p, 6, true); // C
		helper.assertTrue(AllMight.plusUltraActive(p) && AllMight.output(p) == AllMightState.OUTPUT_100, "straight to 100%");
		helper.assertTrue(AllMight.state(p).plusUltraUntil - p.level().getGameTime() == AllMightConfig.PLUS_ULTRA_DURATION_TICKS, "22 s");
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(Math.abs(AllMight.strain(p) - 10f) < 1e-3f, "no Strain builds during Plus Ultra, strain=" + AllMight.strain(p));
			AbilityRouter.handleInput(p, 6, true);
			helper.assertTrue(AllMight.plusUltraActive(p), "cannot be switched off early");
			edit(p, s -> s.plusUltraUntil = p.level().getGameTime());
			AllMight.tick(p);
			helper.assertFalse(AllMight.plusUltraActive(p), "ends by itself");
			helper.assertTrue(AllMight.strain(p) >= AllMightConfig.PLUS_ULTRA_AFTER_STRAIN - 0.01f, "then Strain is set to 75");
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.PLUS_ULTRA) > 40 * 20, "45 s cooldown");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Pass On

	@GameTest(template = EMPTY_STRUCTURE)
	public void passOnRefuseChangesNothingAcceptMovesThePower(GameTestHelper helper) {
		ServerPlayer giver = hero(helper);
		ServerPlayer other = plain(helper, giver, 2.0);
		helper.assertTrue(AllMightPassOn.offer(giver, other, AllMightPassOn.Kind.PASS_ON), "offer sent");
		helper.assertTrue(AllMightPassOn.hasOffer(other), "waiting for an answer");
		helper.assertTrue(AllMight.hasPower(giver) && !AllMight.hasPower(other), "nothing changes before an answer");
		AllMightPassOn.refuse(other);
		helper.assertTrue(AllMight.hasPower(giver) && !AllMight.hasPower(other) && !AllMightPassOn.hasOffer(other), "refuse changes nothing");
		helper.assertFalse(AllMightPassOn.accept(other), "nothing left to accept");
		AllMightPassOn.offer(giver, other, AllMightPassOn.Kind.PASS_ON);
		helper.assertTrue(AllMightPassOn.accept(other), "accepted");
		helper.assertFalse(AllMight.hasPower(giver), "the giver lost it for good");
		helper.assertFalse(HeroTiers.holdsHero(giver, AllMight.KEY), "a normal player now");
		helper.assertTrue(AllMight.hasPower(other) && HeroTiers.holdsHero(other, AllMight.KEY), "the receiver is the holder");
		helper.assertFalse(AllMight.isBorrower(other), "for good, not a loan");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void offersAreRefusedWhenTheyCannotWork(GameTestHelper helper) {
		ServerPlayer giver = hero(helper);
		ServerPlayer far = plain(helper, giver, 30.0);
		helper.assertFalse(AllMightPassOn.offer(giver, far, AllMightPassOn.Kind.PASS_ON), "too far away");
		ServerPlayer holder = plain(helper, giver, 2.0);
		com.projecthero.mod.titanshifter.TitanShifter.grant(holder);
		helper.assertFalse(AllMightPassOn.offer(giver, holder, AllMightPassOn.Kind.LEND), "a loan needs someone with no power");
		helper.assertTrue(AllMight.hasPower(giver) && !AllMight.hasPower(holder), "nothing changed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Lend

	private static ServerPlayer[] lent(GameTestHelper helper) {
		ServerPlayer lender = hero(helper);
		powerForm(lender);
		ServerPlayer borrower = plain(helper, lender, 2.0);
		helper.assertTrue(AllMightPassOn.offer(lender, borrower, AllMightPassOn.Kind.LEND), "lend offer sent");
		helper.assertTrue(AllMightPassOn.accept(borrower), "loan accepted");
		return new ServerPlayer[] { lender, borrower };
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lendingLeavesTheLenderExhaustedAndCapsTheBorrower(GameTestHelper helper) {
		ServerPlayer[] pair = lent(helper);
		ServerPlayer lender = pair[0];
		ServerPlayer borrower = pair[1];
		helper.assertFalse(AllMight.hasPower(lender), "the lender has no One For All for the loan");
		helper.assertTrue(AllMight.isLender(lender) && !AllMight.isFullPower(lender), "lending, out of the Power Form");
		helper.assertTrue(lender.hasEffect(MobEffects.WEAKNESS) && lender.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "exhausted: Weakness + Slowness");
		helper.assertTrue(AllMight.hasPower(borrower) && AllMight.isBorrower(borrower), "the borrower holds it");
		powerForm(borrower);
		for (int i = 0; i < 20; i++) {
			AllMight.onHitLanded(borrower, false);
		}
		helper.assertTrue(AllMight.output(borrower) == AllMightState.OUTPUT_45, "capped at 45%, got " + AllMight.outputPercent(borrower));
		AllMight.setOutput(borrower, AllMightState.OUTPUT_100);
		helper.assertTrue(AllMight.output(borrower) == AllMightState.OUTPUT_45, "even when set directly");
		AllMight.addStrain(borrower, 10f);
		helper.assertTrue(Math.abs(AllMight.strain(borrower) - 20f) < 1e-3f, "Strain builds twice as fast");
		ServerPlayer third = plain(helper, borrower, 2.0);
		helper.assertFalse(AllMightPassOn.offer(borrower, third, AllMightPassOn.Kind.PASS_ON), "cannot pass on a borrowed power");
		helper.assertFalse(AllMightPassOn.offer(borrower, third, AllMightPassOn.Kind.LEND), "nor re-lend it");
		helper.assertFalse(AllMight.grant(lender), "a lender cannot be granted a second One For All");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theLoanReturnsWhenTheTimeRunsOut(GameTestHelper helper) {
		ServerPlayer[] pair = lent(helper);
		ServerPlayer lender = pair[0];
		ServerPlayer borrower = pair[1];
		long now = lender.level().getGameTime();
		edit(lender, s -> s.loanUntil = now);
		edit(borrower, s -> s.loanUntil = now);
		helper.onEachTick(() -> {
			AllMightAbilityManager.serverTick(lender);
			AllMightAbilityManager.serverTick(borrower);
		});
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(AllMight.hasPower(lender) && !AllMight.isLender(lender), "back to the lender");
			helper.assertFalse(AllMight.hasPower(borrower), "gone from the borrower");
			helper.assertFalse(lender.hasEffect(MobEffects.WEAKNESS), "no longer exhausted");
			helper.assertTrue(AllMight.state(lender).lendReadyAt > lender.level().getGameTime() + 50 * 20, "60 s before lending again");
			ServerPlayer next = plain(helper, lender, 2.0);
			helper.assertFalse(AllMightPassOn.offer(lender, next, AllMightPassOn.Kind.LEND), "the lend cooldown holds");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theLoanReturnsWhenTheBorrowerDies(GameTestHelper helper) {
		ServerPlayer[] pair = lent(helper);
		ServerPlayer lender = pair[0];
		ServerPlayer borrower = pair[1];
		borrower.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(AllMight.hasPower(borrower), "a dead borrower loses it");
			helper.assertTrue(AllMight.hasPower(lender) && !AllMight.isLender(lender), "and it goes home to the lender");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void recallAndFullStrainEndTheLoanAndNothingIsDuplicated(GameTestHelper helper) {
		ServerPlayer[] pair = lent(helper);
		ServerPlayer lender = pair[0];
		ServerPlayer borrower = pair[1];
		helper.assertTrue(AllMight.hasPower(lender) ^ AllMight.hasPower(borrower), "exactly one of them holds it");
		// full Strain ends a loan early
		AllMight.addStrain(borrower, AllMightConfig.STRAIN_MAX);
		helper.assertFalse(AllMight.hasPower(borrower), "the borrower's arms gave out: the loan is over");
		helper.assertTrue(AllMight.hasPower(lender), "back with the lender");
		// lend again later, then recall it (the lender sneak-right-clicks: the same path)
		edit(lender, s -> s.lendReadyAt = 0L);
		AllMightPassOn.offer(lender, borrower, AllMightPassOn.Kind.LEND);
		AllMightPassOn.accept(borrower);
		helper.assertTrue(AllMight.isBorrower(borrower), "lent again");
		AllMightPassOn.endLoanFromBorrower(borrower, AllMightPassOn.EndReason.RECALL, lender);
		helper.assertTrue(AllMight.hasPower(lender) && !AllMight.hasPower(borrower), "recalled");
		// a borrower revoked some other way (an admin, a new Primary power) still sends it home
		edit(lender, s -> s.lendReadyAt = 0L);
		AllMightPassOn.offer(lender, borrower, AllMightPassOn.Kind.LEND);
		AllMightPassOn.accept(borrower);
		HeroTiers.wipeAll(borrower);
		helper.assertTrue(AllMight.hasPower(lender) && !AllMight.hasPower(borrower), "revoking the borrower returns it, never loses it");
		// a stale loan on join (the server stopped mid-loan) resolves without a second copy
		edit(lender, s -> s.lendReadyAt = 0L);
		AllMightPassOn.offer(lender, borrower, AllMightPassOn.Kind.LEND);
		AllMightPassOn.accept(borrower);
		AllMightPassOn.onJoin(borrower);
		helper.assertTrue(AllMight.hasPower(lender) && !AllMight.hasPower(borrower), "a borrower rejoining ends the loan cleanly");
		helper.succeed();
	}
}
