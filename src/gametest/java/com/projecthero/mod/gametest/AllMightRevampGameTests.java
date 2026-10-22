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
 * v0.16.6: the One For All revamp -- the Smashes' full damage (v0.16.7: Output and limb strain are gone; x1.3 in Plus Ultra), the
 * five Shift moves' damage, Plus Ultra, Passing On (accept / refuse) and Lending (lender drawback, the borrower on the lender's
 * timer, return on time and on the borrower's death), and that One For All is never duplicated. The timer itself is in
 * OneForAllTimerGameTests. Mock players are not ticked by the
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

	/** The Power Form (every Smash at x1.0, its designed damage). */
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

	// ---------------------------------------------------------------- full damage (v0.16.7: no Output)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void detroitSmashLandsItsFullEighteen(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		Zombie z = dummy(helper, p, 4.0, 0f);
		helper.onEachTick(() -> AllMight.tick(p));
		AbilityRouter.handleInput(p, 1, true); // R
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "R = Detroit");
		helper.runAfterDelay(AllMightConfig.DETROIT_WINDUP + 4, () -> {
			assertDealt(helper, z, AllMightConfig.DETROIT_DAMAGE, "Detroit at full power");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void plusUltraDetroitHitsThirtyPercentHarder(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		Zombie z = dummy(helper, p, 4.0, 0f);
		helper.onEachTick(() -> AllMight.tick(p));
		AbilityRouter.handleInput(p, 6, true); // C
		AbilityRouter.handleInput(p, 6, false);
		edit(p, s -> s.busyUntil = 0L);
		AbilityRouter.handleInput(p, 1, true); // R
		helper.assertTrue(AllMight.plusUltraActive(p) && AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "Plus Ultra, then Detroit");
		helper.runAfterDelay(AllMightConfig.DETROIT_WINDUP + 4, () -> {
			assertDealt(helper, z, AllMightConfig.DETROIT_DAMAGE * AllMightConfig.PLUS_ULTRA_MULTIPLIER, "Detroit in Plus Ultra (18 x 1.3)");
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
	public void plusUltraLastsTwentyTwoSecondsThenCoolsDown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AbilityRouter.handleInput(p, 6, true); // C
		helper.assertTrue(AllMight.plusUltraActive(p) && AllMight.smashMultiplier(p) == AllMightConfig.PLUS_ULTRA_MULTIPLIER, "Plus Ultra: x1.3");
		helper.assertTrue(AllMight.state(p).plusUltraUntil - p.level().getGameTime() == AllMightConfig.PLUS_ULTRA_DURATION_TICKS, "22 s");
		helper.onEachTick(() -> AllMight.tick(p));
		helper.runAfterDelay(30, () -> {
			AbilityRouter.handleInput(p, 6, false);
			AbilityRouter.handleInput(p, 6, true);
			helper.assertTrue(AllMight.plusUltraActive(p), "cannot be switched off early");
			edit(p, s -> s.plusUltraUntil = p.level().getGameTime());
			AllMight.tick(p);
			helper.assertFalse(AllMight.plusUltraActive(p), "ends by itself");
			helper.assertTrue(AllMight.smashMultiplier(p) == 1.0f, "the Smashes are back to x1");
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.PLUS_ULTRA) > 15 * 20, "20 s cooldown");
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
		AllMight.setTimer(lender, 700f); // the loan carries whatever is left on the lender's timer
		ServerPlayer borrower = plain(helper, lender, 2.0);
		helper.assertTrue(AllMightPassOn.offer(lender, borrower, AllMightPassOn.Kind.LEND), "lend offer sent");
		helper.assertTrue(AllMightPassOn.accept(borrower), "loan accepted");
		return new ServerPlayer[] { lender, borrower };
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lendingLeavesTheLenderExhaustedAndTheBorrowerOnTheirTimer(GameTestHelper helper) {
		ServerPlayer[] pair = lent(helper);
		ServerPlayer lender = pair[0];
		ServerPlayer borrower = pair[1];
		helper.assertFalse(AllMight.hasPower(lender), "the lender has no One For All for the loan");
		helper.assertTrue(AllMight.isLender(lender) && !AllMight.isFullPower(lender), "lending, out of the Power Form");
		helper.assertTrue(lender.hasEffect(MobEffects.WEAKNESS) && lender.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "exhausted: Weakness + Slowness");
		helper.assertTrue(AllMight.hasPower(borrower) && AllMight.isBorrower(borrower), "the borrower holds it");
		helper.assertTrue(Math.abs(AllMight.timeLeft(borrower) - 700f) < 1e-3f, "the borrower runs on the lender's timer, got " + AllMight.timeLeft(borrower));
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
	public void recallEndsTheLoanAndNothingIsDuplicated(GameTestHelper helper) {
		ServerPlayer[] pair = lent(helper);
		ServerPlayer lender = pair[0];
		ServerPlayer borrower = pair[1];
		helper.assertTrue(AllMight.hasPower(lender) ^ AllMight.hasPower(borrower), "exactly one of them holds it");
		helper.assertTrue(AllMight.isBorrower(borrower), "lent");
		// recall it (the lender sneak-right-clicks: the same path)
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
