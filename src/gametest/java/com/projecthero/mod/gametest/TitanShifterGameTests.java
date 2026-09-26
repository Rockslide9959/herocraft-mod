package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.titanshifter.TitanAbilities;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for the Titan Shifter (v0.12.31): the unlock, the phase state machine, a real
 * transformation with a real form entity, ability cooldowns / damage through the ability router, defeat,
 * manual reversion and the forced-end cleanup. Mock players are not in the player list, so each test ticks
 * {@link TitanShifter#tick} itself, exactly as the server tick would.
 */
public class TitanShifterGameTests implements FabricGameTest {
	private static final int SETTLE = 80; // > transformTicks

	private static ServerPlayer shifter(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// the test volume is walled in with barriers: open up room for an 11-block Titan
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-8, 0, -8), base.offset(8, 14, 8))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		TitanShifter.grant(p);
		return p;
	}

	/** Mock players are not reliably ticked by the server: run the Titan tick ourselves, exactly as the server tick would. */
	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> TitanShifter.tick(p));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void serumUnlocksOnce(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(TitanShifter.isShifter(p), "not a shifter yet");
		helper.assertTrue(TitanShifter.grant(p), "first grant unlocks");
		helper.assertTrue(TitanShifter.isShifter(p), "unlocked");
		helper.assertTrue(HeroTiers.holdsHero(p, TitanShifter.KEY), "registered as a Hero-Tier Primary power");
		helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "the serum does not transform by itself");
		helper.assertFalse(TitanShifter.grant(p), "second grant refused");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void phaseTableRefusesIllegalTransitions(GameTestHelper helper) {
		helper.assertTrue(TitanPhase.HUMAN.canGoTo(TitanPhase.TRANSFORMING), "HUMAN -> TRANSFORMING");
		helper.assertTrue(TitanPhase.TRANSFORMING.canGoTo(TitanPhase.TITAN), "TRANSFORMING -> TITAN");
		helper.assertTrue(TitanPhase.TITAN.canGoTo(TitanPhase.REVERTING), "TITAN -> REVERTING");
		helper.assertTrue(TitanPhase.REVERTING.canGoTo(TitanPhase.HUMAN), "REVERTING -> HUMAN");
		helper.assertTrue(TitanPhase.TITAN.canGoTo(TitanPhase.DEFEATED), "TITAN -> DEFEATED");
		helper.assertTrue(TitanPhase.DEFEATED.canGoTo(TitanPhase.RECOVERING), "DEFEATED -> RECOVERING");
		helper.assertTrue(TitanPhase.RECOVERING.canGoTo(TitanPhase.HUMAN), "RECOVERING -> HUMAN");
		helper.assertFalse(TitanPhase.HUMAN.canGoTo(TitanPhase.TITAN), "cannot skip the transformation");
		helper.assertFalse(TitanPhase.HUMAN.canGoTo(TitanPhase.DEFEATED), "a human cannot be defeated");
		helper.assertFalse(TitanPhase.REVERTING.canGoTo(TitanPhase.TITAN), "cannot un-revert");
		helper.assertFalse(TitanPhase.RECOVERING.canGoTo(TitanPhase.TITAN), "cannot transform while recovering");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void transformCreatesARealTitanThenReverts(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		helper.assertTrue(TitanShifter.transform(p), "transform starts");
		helper.assertTrue(TitanShifter.phase(p) == TitanPhase.TRANSFORMING, "TRANSFORMING first");
		TitanFormEntity form = TitanShifter.formOf(p);
		helper.assertTrue(form != null, "the player rides a Titan form entity");
		helper.assertTrue(form.getMaxHealth() == 500.0f, "500 HP, got " + form.getMaxHealth());
		helper.assertTrue(Math.abs(form.getBbHeight() - TitanShifterConfig.stats().heightBlocks) < 1e-3, "11 blocks tall");
		helper.assertFalse(TitanShifter.transform(p), "cannot transform twice");
		helper.runAfterDelay(SETTLE, () -> {
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.TITAN, "TITAN after the transformation, was " + TitanShifter.phase(p));
			helper.assertTrue(TitanShifter.formOf(p) == form && form.isAlive(), "same Titan, alive");
			TitanShifter.requestToggle(p);
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.REVERTING, "REVERTING");
			helper.runAfterDelay(50, () -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "back to HUMAN, was " + TitanShifter.phase(p));
				helper.assertTrue(p.getVehicle() == null, "no longer riding");
				helper.assertTrue(form.isRemoved(), "the Titan was removed");
				helper.assertTrue(TitanShifter.energy(p) < 5f, "reverting empties the Titan Energy bar (it has only refilled a point or two since), was " + TitanShifter.energy(p));
				helper.assertFalse(TitanShifter.transform(p), "cannot shift again with an empty bar");
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "still human after the refused shift");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void abilitiesGoThroughTheRouterWithCooldowns(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null && TitanShifter.inTitan(p), "in the Titan");
			Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			z.moveTo(form.getX() + fwd.x * 4.5, form.getY(), form.getZ() + fwd.z * 4.5);
			z.setNoAi(true);
			helper.getLevel().addFreshEntity(z);
			float before = z.getHealth();

			AbilityRouter.handleInput(p, 1, true); // Ability 1 = Titan Punch
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.PUNCH) > 0, "punch is on cooldown");
			helper.runAfterDelay(15, () -> {
				helper.assertTrue(z.getHealth() < before || !z.isAlive(), "the punch hurt the zombie");
				AbilityRouter.handleInput(p, 3, true); // Ability 3 (X) = Titan Leap
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.LEAP) > 0, "leap is on cooldown");
				AbilityRouter.handleInput(p, 4, true); // Ability 4 (Z) = Titan Stomp
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.STOMP) > 0, "stomp is on cooldown");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
	public void defeatEjectsTheShifterAndAppliesRecovery(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			float playerHp = p.getHealth();
			form.hurt(helper.getLevel().damageSources().generic(), 100000.0f);
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.DEFEATED, "DEFEATED, was " + TitanShifter.phase(p));
			helper.assertTrue(p.getHealth() == playerHp, "the player's own health was not touched");
			helper.runAfterDelay(TitanShifterConfig.transformation().defeatTicks + 10, () -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.RECOVERING, "RECOVERING, was " + TitanShifter.phase(p));
				helper.assertTrue(p.getVehicle() == null && form.isRemoved(), "ejected, Titan gone");
				helper.assertFalse(TitanShifter.transform(p), "no immediate re-transformation");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void forceEndAndRevokeCleanUp(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		helper.assertTrue(TitanShifter.transform(p), "transform");
		TitanFormEntity form = TitanShifter.formOf(p);
		TitanShifter.forceEnd(p, true);
		helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "HUMAN after a forced end");
		helper.assertTrue(p.getVehicle() == null && form.isRemoved(), "no rider, no Titan");
		TitanShifter.revoke(p);
		helper.assertFalse(TitanShifter.isShifter(p), "revoked");
		helper.assertFalse(HeroTiers.holdsHero(p, TitanShifter.KEY), "no longer a Hero-Tier holder");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void transformIsRefusedWithoutRoomAndWithoutTheUnlock(GameTestHelper helper) {
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(TitanShifter.transform(plain), "not a shifter -> refused");
		ServerPlayer p = shifter(helper);
		// a solid ceiling two blocks up leaves no room for an 11-block Titan
		BlockPos ceiling = helper.absolutePos(new BlockPos(2, 5, 2));
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				helper.getLevel().setBlockAndUpdate(ceiling.offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			}
		}
		helper.assertFalse(TitanShifter.transform(p), "no room -> refused");
		helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "still human");
		helper.assertTrue(p.getVehicle() == null, "not riding anything");
		helper.succeed();
	}

// ---------------- v0.12.32: Titan Energy, the new hit-box, the new key layout ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void transformNeedsAFullEnergyBar(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		helper.assertTrue(TitanShifter.energy(p) == (float) TitanShifterConfig.energy().max, "a fresh shifter has a full bar");
		TitanShifterState low = TitanShifter.state(p).copy();
		low.energy = 99.0f;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, low);
		helper.assertFalse(TitanShifter.transform(p), "99% is not enough");
		helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "still human");
		TitanShifterState ok = TitanShifter.state(p).copy();
		ok.energy = 100.0f;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, ok);
		helper.assertTrue(TitanShifter.transform(p), "100% is enough");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void humanEnergyRefillsOnePercentPerSecond(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		TitanShifterState s = TitanShifter.state(p).copy();
		s.energy = 0f;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, s);
		pump(helper, p);
		helper.runAfterDelay(65, () -> {
			float e = TitanShifter.energy(p);
			// ~3% after ~3 s (the mock player may also be ticked by the server itself, so allow up to double)
			helper.assertTrue(e >= 2.0f && e <= 8.0f, "about 3% after ~3 s, got " + e);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void titanHasNoPassiveRegeneration(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(TitanShifter.inTitan(p) && form != null, "in the Titan");
			form.setHealth(form.getMaxHealth() - 200.0f);
			float hp0 = form.getHealth();
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(form.getHealth() <= hp0 + 0.01f, "the Titan does not regenerate on its own, was " + hp0 + " now " + form.getHealth());
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void ordinaryMobsStillHurtTheTitan(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null, "in the Titan");
			net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
			z.setNoAi(true);
			helper.getLevel().addFreshEntity(z);
			float hp0 = form.getHealth();
			form.hurt(helper.getLevel().damageSources().mobAttack(z), 3.0f);
			helper.assertTrue(hp0 - form.getHealth() >= 2.9f, "a zombie's 3 damage goes straight through the armour, lost " + (hp0 - form.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void shiftVariantsAndTheNewHitBox(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null, "in the Titan");
			helper.assertTrue(Math.abs(form.getBbHeight() - 11.0f) < 1e-3, "11 blocks tall, got " + form.getBbHeight());
			helper.assertTrue(Math.abs(form.getBbWidth() - 3.67f) < 0.02f, "a player's proportions: 3.67 wide, got " + form.getBbWidth());
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, 3, true); // Shift+X = Titan Roar
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.ROAR) > 0, "Shift+X roars");
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.LEAP) == 0, "and does not leap");
			// the roar locks the Titan for a second, so let it finish before the next ability
			helper.runAfterDelay(40, () -> {
				AbilityRouter.handleInput(p, 6, true); // Shift+C = Titan Hardening
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.HARDEN) > 0, "Shift+C hardens");
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.REGEN) == 0, "and does not regenerate");
				helper.succeed();
			});
		});
	}
}
