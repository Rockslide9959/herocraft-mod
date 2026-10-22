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
 * v0.16.6: the One For All revamp -- Output charged by hits and decaying (v0.16.7: up to the safe limit; the limb strain rules
 * are OneForAllStrainGameTests), the five Shift moves' damage, Plus Ultra, Passing On (accept / refuse) and Lending (lender drawback, borrower cap,
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

	/** The Power Form at the Output where the damage multiplier is exactly x1.0 (~11%, inside the 20% limit). */
	private static void powerForm(ServerPlayer p) {
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		s.outputPct = (1f - AllMightConfig.OUTPUT_DAMAGE_BASE) / AllMightConfig.OUTPUT_DAMAGE_PER_100 * 100f;
		s.lastHitAt = p.level().getGameTime();
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
	public void outputChargesToTheLimitAndDecays(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AllMight.setOutput(p, AllMightConfig.OUTPUT_FLOOR);
		AllMight.onHitLanded(p, false);
		helper.assertTrue(Math.abs(AllMight.outputPct(p) - (AllMightConfig.OUTPUT_FLOOR + AllMightConfig.OUTPUT_PER_SMASH_HIT)) < 1e-3f,
				"a landed Smash charges +8, got " + AllMight.outputPct(p));
		for (int i = 0; i < 10; i++) {
			AllMight.onHitLanded(p, false);
		}
		helper.assertTrue(Math.abs(AllMight.outputPct(p) - AllMight.limit(p)) < 1e-3f, "capped at the 20% limit, got " + AllMight.outputPct(p));
		helper.assertTrue(Math.abs(AllMight.smashMultiplier(p) - AllMight.multiplierAt(20f)) < 1e-4f && AllMight.sizeScale(p) > 1.0,
				"20% hits harder and wider");
		helper.assertTrue(Math.abs(AllMight.multiplierAt(100f) - 1.8f) < 1e-4f, "100% is x1.8");
		// 8 s without a hit: it eases down
		edit(p, s -> s.lastHitAt = p.level().getGameTime() - AllMightConfig.OUTPUT_DECAY_TICKS);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(AllMight.outputPct(p) < 20f - 4f && AllMight.outputPct(p) >= AllMightConfig.OUTPUT_FLOOR,
					"decays toward the floor, got " + AllMight.outputPct(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fullCowlInsideTheLimitIsFree(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(60, () -> {
			AllMightState s = AllMight.state(p);
			helper.assertTrue(AllMight.isFullPower(p) && s.limb(AllMightState.Limb.RIGHT_ARM) == 0f && s.limb(AllMightState.Limb.LEGS) == 0f,
					"Full Cowl inside the limit is free");
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
	public void plusUltraGoesPastTheLimitThenDropsBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AbilityRouter.handleInput(p, 6, true); // C
		helper.assertTrue(AllMight.plusUltraActive(p) && AllMight.outputPct(p) == 100f && AllMight.overLimit(p), "straight to 100%, past the limit");
		helper.assertTrue(AllMight.state(p).plusUltraUntil - p.level().getGameTime() == AllMightConfig.PLUS_ULTRA_DURATION_TICKS, "22 s");
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(AllMight.state(p).limb(AllMightState.Limb.LEGS) > 0f, "Full Cowl past the limit strains every limb");
			AbilityRouter.handleInput(p, 6, false);
			AbilityRouter.handleInput(p, 6, true);
			helper.assertTrue(AllMight.plusUltraActive(p), "cannot be switched off early");
			edit(p, s -> s.plusUltraUntil = p.level().getGameTime());
			AllMight.tick(p);
			helper.assertFalse(AllMight.plusUltraActive(p), "ends by itself");
			helper.assertTrue(AllMight.outputPct(p) <= AllMight.limit(p) + 1e-3f, "Output drops back inside the limit");
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
		helper.assertTrue(AllMight.limit(borrower) == AllMightConfig.BORROWED_LIMIT && AllMight.outputPct(borrower) == AllMightConfig.BORROWED_LIMIT,
				"a fixed 15% limit, got " + AllMight.outputPercent(borrower));
		AllMight.setOutput(borrower, 100f);
		helper.assertTrue(AllMight.outputPct(borrower) == AllMightConfig.BORROWED_OUTPUT_CAP, "never past 45%, even when set directly");
		com.projecthero.mod.allmight.AllMightStrain.addLimbStrain(borrower, AllMightState.Limb.LEFT_ARM, 10f);
		helper.assertTrue(Math.abs(AllMight.state(borrower).limb(AllMightState.Limb.LEFT_ARM) - 20f) < 1e-3f, "limbs strain twice as fast");
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
		// a broken limb ends a loan early (50 x 2 = 100)
		com.projecthero.mod.allmight.AllMightStrain.addLimbStrain(borrower, AllMightState.Limb.RIGHT_ARM, 50f);
		helper.assertFalse(AllMight.hasPower(borrower), "the borrower's arm broke: the loan is over");
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
