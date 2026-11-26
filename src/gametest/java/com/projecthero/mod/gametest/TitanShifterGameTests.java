package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.titanshifter.TitanAbilities;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;
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
		// the test volume is walled in with barriers: open up room for a 15-block Titan (v0.16.7)
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-8, 0, -8), base.offset(8, 18, 8))) {
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
		helper.assertTrue(form.getMaxHealth() == 400.0f, "400 HP (v0.17.7 City tier), got " + form.getMaxHealth());
		helper.assertTrue(Math.abs(form.getBbHeight() - TitanShifterConfig.stats().heightBlocks) < 1e-3, "15 blocks tall");
		helper.assertFalse(TitanShifter.transform(p), "cannot transform twice");
		helper.runAfterDelay(SETTLE, () -> {
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.TITAN, "TITAN after the transformation, was " + TitanShifter.phase(p));
			helper.assertTrue(TitanShifter.formOf(p) == form && form.isAlive(), "same Titan, alive");
			double formY = form.getY();
			TitanShifter.requestToggle(p);
			// v0.16.7: the REVERTING phase is back -- ~3 s of steam while the shifter climbs out, the body untouchable
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.REVERTING, "REVERTING first, was " + TitanShifter.phase(p));
			helper.assertTrue(form.formState() == TitanFormEntity.FORM_REVERTING && form.formStateTicks() == TitanShifterConfig.transformation().revertTicks,
					"the form is synced as reverting, with its length");
			float hp0 = form.getHealth();
			form.hurt(helper.getLevel().damageSources().generic(), 50.0f);
			helper.assertTrue(form.getHealth() == hp0, "a reverting Titan cannot be hurt");
			helper.runAfterDelay(TitanShifterConfig.transformation().revertTicks + 2, () -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "HUMAN after the steam, was " + TitanShifter.phase(p));
				var corpses = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.titanshifter.entity.TitanCorpseEntity.class,
						new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(24.0));
				helper.assertTrue(corpses.size() == 1, "one Titan corpse was left behind, found " + corpses.size());
				helper.assertTrue(corpses.get(0).cause() == com.projecthero.mod.titanshifter.entity.TitanCorpseEntity.CAUSE_REVERT, "a reversion corpse");
				helper.assertTrue(p.getY() > formY + 5.0, "the shifter is out at neck height, y=" + p.getY() + " vs " + formY);
				helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "drifting down under Slow Falling");
				helper.assertTrue(p.getVehicle() == null, "no longer riding");
				helper.assertTrue(form.isRemoved(), "the Titan was removed");
				helper.assertTrue(TitanShifter.energy(p) >= 98f, "a full-HP Titan refunds the whole bar, was " + TitanShifter.energy(p));
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

	/** v0.13.10: a mob hovering above and ahead of the Titan -- out of the old punch volume -- is hit when aimed at. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void punchHitsWhereTheShifterAims(GameTestHelper helper) {
		aimedHitTest(helper, 1, 5.0, 2.0);
	}

	/** v0.13.10: Heavy Smash lands on the aimed mob up in the air, not on the ground in front of the Titan. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void heavySmashLandsWhereTheShifterAims(GameTestHelper helper) {
		aimedHitTest(helper, 2, 8.0, -4.0);
	}

	/** Floats a zombie {@code ahead} blocks in front and {@code aboveTop} blocks above the Titan's head, aims at it, fires the ability. */
	private static void aimedHitTest(GameTestHelper helper, int ability, double ahead, double aboveTop) {
		ServerPlayer p = shifter(helper);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(p.position()).offset(-12, 0, -12),
				BlockPos.containing(p.position()).offset(12, 18, 12))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null && TitanShifter.inTitan(p), "in the Titan");
			Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			double reachOut = form.getBbWidth() * 0.5 + ahead;
			z.moveTo(form.getX() + fwd.x * reachOut, form.getY() + form.getBbHeight() + aboveTop, form.getZ() + fwd.z * reachOut);
			z.setNoAi(true);
			z.setNoGravity(true);
			helper.getLevel().addFreshEntity(z);
			float before = z.getHealth();
			helper.onEachTick(() -> p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
					z.position().add(0, z.getBbHeight() * 0.5, 0)));
			helper.runAfterDelay(2, () -> {
				AbilityRouter.handleInput(p, ability, true);
				helper.runAfterDelay(TitanShifterConfig.abilities().smashChargeTicks + 10, () -> {
					helper.assertTrue(z.getHealth() < before || !z.isAlive(),
							"the aimed ability hurt the zombie at " + z.position() + " (Titan at " + form.position() + ")");
					helper.succeed();
				});
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
			helper.assertTrue(p.getHealth() == playerHp, "the player's own health was not touched yet");
			// no natural regen lifting the shifter back over 4 hearts while we wait (slow CI runners)
			p.getFoodData().setFoodLevel(6);
			p.getFoodData().setSaturation(0f);
			// poll instead of a fixed delay: a busy CI server can land the hand-off a few ticks late
			helper.succeedWhen(() -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.RECOVERING, "RECOVERING, was " + TitanShifter.phase(p));
				helper.assertTrue(p.getVehicle() == null && form.isRemoved(), "ejected, Titan gone");
				// v0.16.7: a defeat empties Steam, drops the shifter to 4 hearts and locks shifting for 60 s
				helper.assertTrue(TitanShifter.energy(p) < 1f, "Steam emptied, was " + TitanShifter.energy(p));
				helper.assertTrue(p.getHealth() <= 8.0f, "down to 4 hearts, was " + p.getHealth());
				helper.assertTrue(TitanShifter.transformCooldownRemaining(p) > 20 * 50, "shifting locked for about 60 s, "
						+ TitanShifter.transformCooldownRemaining(p));
				helper.assertFalse(TitanShifter.transform(p), "no immediate re-transformation");
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

	/** v0.17.5: a shift needs a Titan charge (not a full Steam bar any more) and spends exactly one. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void transformNeedsAndSpendsATitanCharge(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		helper.assertTrue(TitanShifter.energy(p) == (float) TitanShifterConfig.energy().max, "a fresh shifter has a full bar");
		helper.assertTrue(TitanShifter.charges(p) == 3, "a fresh Attack Titan has all 3 charges, had " + TitanShifter.charges(p));
		TitanShifter.setCharges(p, 0);
		helper.assertFalse(TitanShifter.transform(p), "no charge -> no shift, even with a full bar");
		TitanShifter.requestToggle(p);
		helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN && TitanShifter.formOf(p) == null, "still human");
		TitanShifter.setCharges(p, 1);
		setEnergy(p, 5.0f);
		helper.assertTrue(TitanShifter.transform(p), "one charge is enough, whatever the Steam");
		helper.assertTrue(TitanShifter.charges(p) == 0, "and it was spent, left " + TitanShifter.charges(p));
		helper.succeed();
	}

	// ---------------- v0.17.5: Titan charges ----------------

	/** The user's example: with charges left the shifter can change back and shift again at once; each shift costs one. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
	public void chargesLetTheShifterTransformTwiceInARow(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		helper.assertTrue(TitanShifter.transform(p), "first shift");
		helper.assertTrue(TitanShifter.charges(p) == 2, "3 -> 2");
		helper.runAfterDelay(SETTLE, () -> {
			helper.assertTrue(TitanShifter.inTitan(p), "in the Titan");
			TitanShifter.requestToggle(p);
			helper.runAfterDelay(TitanShifterConfig.transformation().revertTicks + 3, () -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "human again");
				TitanShifter.requestToggle(p); // H straight away: a second shift
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.TRANSFORMING, "shifting again at once, was " + TitanShifter.phase(p));
				helper.assertTrue(TitanShifter.charges(p) == 1, "2 -> 1, had " + TitanShifter.charges(p));
				helper.succeed();
			});
		});
	}

	/** One charge back per regenTicks, strictly one after the other: checked on the timer's own steps. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void chargesRegenerateOneAtATimeInSequence(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.titanshifter.TitanCharges.regenTicks() == 1200, "60 s a charge");
		TitanShifterState s = new TitanShifterState();
		s.unlocked = true;
		s.typeId = TitanType.GENERIC_TITAN.id;
		s.charges = 1; // used two of three
		s.chargeProgress = 0;
		int t = 0;
		int[] backAt = new int[3];
		int got = 0;
		while (t < 4000) {
			int before = s.charges;
			int gained = com.projecthero.mod.titanshifter.TitanCharges.advance(s, 20);
			t += 20;
			helper.assertTrue(gained <= 1 && s.charges - before == gained, "never more than one charge per step");
			if (gained > 0) {
				backAt[got++] = t;
			}
		}
		helper.assertTrue(got == 2, "exactly the two missing charges came back, got " + got);
		helper.assertTrue(backAt[0] == 1200, "the first after 1200 ticks, at " + backAt[0]);
		helper.assertTrue(backAt[1] - backAt[0] == 1200, "the second only 1200 ticks after the first, step " + (backAt[1] - backAt[0]));
		helper.assertTrue(s.charges == 3 && s.chargeProgress == 0, "full, and the timer stopped at 0");
		// one big jump still pays out one charge per 1200 ticks, never early
		s.charges = 0;
		s.chargeProgress = 0;
		helper.assertTrue(com.projecthero.mod.titanshifter.TitanCharges.advance(s, 1199) == 0, "1199 ticks: nothing yet");
		helper.assertTrue(com.projecthero.mod.titanshifter.TitanCharges.advance(s, 1) == 1 && s.chargeProgress == 0, "tick 1200: one, the timer restarts");
		helper.assertTrue(com.projecthero.mod.titanshifter.TitanCharges.advance(s, 2400) == 2 && s.charges == 3, "2400 more: the other two");
		// spending from full starts the timer from zero
		com.projecthero.mod.titanshifter.TitanCharges.spend(s);
		helper.assertTrue(s.charges == 2 && s.chargeProgress == 0, "spent one from full: 2, timer at 0");
		helper.succeed();
	}

	/** The real tick: the timer steps 20 ticks a second in human form, pays out on time, and pauses inside the Titan. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500)
	public void chargeTimerRunsInHumanFormAndPausesInTheTitan(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		TitanShifter.setCharges(p, 1);
		TitanShifter.setChargeTicksLeft(p, 60);
		int start = TitanShifter.state(p).chargeProgress;
		helper.assertTrue(start == 1140, "60 ticks left = progress 1140, was " + start);
		int[] last = { start };
		boolean[] badStep = { false };
		helper.onEachTick(() -> {
			TitanShifter.tick(p);
			TitanShifterState s = TitanShifter.state(p);
			if (s.charges == 1 && s.chargeProgress != last[0]) {
				if (s.chargeProgress - last[0] != 20) {
					badStep[0] = true; // every step is 20 ticks (one a second), however often the player is ticked
				}
				last[0] = s.chargeProgress;
			}
		});
		helper.runAfterDelay(90, () -> {
			TitanShifterState s = TitanShifter.state(p);
			helper.assertFalse(badStep[0], "the timer only moves in 20-tick steps");
			helper.assertTrue(s.charges == 2, "the charge came back on time, charges " + s.charges);
			helper.assertTrue(s.chargeProgress <= 40, "and the next one started over, progress " + s.chargeProgress);
			helper.assertTrue(TitanShifter.transform(p), "shift (2 -> 1)");
			helper.runAfterDelay(SETTLE, () -> {
				helper.assertTrue(TitanShifter.inTitan(p), "in the Titan");
				int frozen = TitanShifter.state(p).chargeProgress;
				helper.runAfterDelay(100, () -> {
					helper.assertTrue(TitanShifter.state(p).chargeProgress == frozen, "paused inside the Titan: "
							+ frozen + " -> " + TitanShifter.state(p).chargeProgress);
					helper.assertTrue(TitanShifter.charges(p) == 1, "and no charge came back meanwhile");
					helper.succeed();
				});
			});
		});
	}

	/** Each Titan's own count; a re-forge keeps the charges, cut to the new maximum. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void eachTitanHasItsOwnChargeCount(GameTestHelper helper) {
		int[] expect = { 3, 2, 4, 2 };
		TitanType[] types = { TitanType.GENERIC_TITAN, TitanType.ARMORED_TITAN, TitanType.FEMALE_TITAN, TitanType.BEAST_TITAN };
		for (int i = 0; i < types.length; i++) {
			helper.assertTrue(com.projecthero.mod.titanshifter.TitanCharges.max(types[i]) == expect[i],
					types[i].id + " stores " + expect[i] + ", got " + com.projecthero.mod.titanshifter.TitanCharges.max(types[i]));
			ServerPlayer p = helper.makeMockServerPlayerInLevel();
			TitanShifter.grant(p, types[i]);
			helper.assertTrue(TitanShifter.charges(p) == expect[i], types[i].id + " starts full");
		}
		TitanShifterState s = new TitanShifterState();
		s.typeId = TitanType.FEMALE_TITAN.id;
		s.charges = 4;
		s.typeId = TitanType.ARMORED_TITAN.id;
		com.projecthero.mod.titanshifter.TitanCharges.clamp(s);
		helper.assertTrue(s.charges == 2 && s.chargeProgress == 0, "Female 4 -> Armoured: clamped to 2");
		s.charges = 1;
		s.typeId = TitanType.FEMALE_TITAN.id;
		com.projecthero.mod.titanshifter.TitanCharges.clamp(s);
		helper.assertTrue(s.charges == 1, "a re-forge never refills");
		helper.succeed();
	}

	/** Charges and the timer survive a save / load (and old saves read as full) and a death. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void chargesPersistAcrossSavesAndDeath(GameTestHelper helper) {
		TitanShifterState s = new TitanShifterState();
		s.unlocked = true;
		s.typeId = TitanType.FEMALE_TITAN.id;
		s.charges = 1;
		s.chargeProgress = 600;
		s.emergencyUntil = 77L;
		s.regenOff = true;
		var nbt = TitanShifterState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s).getOrThrow();
		TitanShifterState back = TitanShifterState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt).getOrThrow();
		helper.assertTrue(back.charges == 1 && back.chargeProgress == 600, "charges and timer saved: " + back.charges + " / " + back.chargeProgress);
		helper.assertTrue(back.emergencyUntil == 77L && back.regenOff && "female_titan".equals(back.typeId), "the packed fields still round-trip");
		net.minecraft.nbt.CompoundTag legacy = (net.minecraft.nbt.CompoundTag) nbt.copy();
		legacy.remove("charges");
		legacy.remove("charge_progress");
		TitanShifterState old = TitanShifterState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, legacy).getOrThrow();
		helper.assertTrue(com.projecthero.mod.titanshifter.TitanCharges.current(old) == 4, "a save from before charges reads as full");

		ServerPlayer p = shifter(helper);
		TitanShifter.setCharges(p, 1);
		TitanShifter.setChargeTicksLeft(p, 300);
		TitanShifter.onPlayerRespawn(p);
		helper.assertTrue(TitanShifter.charges(p) == 1 && TitanShifter.state(p).chargeProgress == 900, "respawning keeps charges and timer");
		helper.succeed();
	}

	/** With no charges the Emergency Titan is the way out: it costs no charge but restarts the running timer. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theEmergencyTitanCostsNoChargeButRestartsTheTimer(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		TitanShifter.setCharges(p, 0);
		TitanShifter.setChargeTicksLeft(p, 100);
		setEnergy(p, 50.0f);
		helper.assertTrue(TitanShifter.transform(p, true), "emergency shift at 0 charges");
		helper.assertTrue(TitanShifter.charges(p) == 0, "no charge spent (none to spend)");
		helper.assertTrue(TitanShifter.state(p).chargeProgress == 0, "the timer started over");
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
	public void titanRegeneratesOutOfCombatBurningSteam(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		pump(helper, p);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(TitanShifter.inTitan(p) && form != null, "in the Titan");
			form.setHealth(form.getMaxHealth() - 200.0f);
			float hp0 = form.getHealth();
			float steam0 = TitanShifter.energy(p);
			helper.runAfterDelay(65, () -> {
				// v0.16.7 (user): ~2 HP a second out of combat, 0.1 Steam per HP
				float healed = form.getHealth() - hp0;
				helper.assertTrue(healed >= 4.0f && healed <= 8.5f, "about 6 HP over ~3 s, healed " + healed);
				float burned = steam0 - TitanShifter.energy(p);
				helper.assertTrue(Math.abs(burned - healed * 0.1f) < 0.11f, "Steam burned at 0.1 per HP: burned " + burned + " for " + healed);
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
			helper.assertTrue(Math.abs(form.getBbHeight() - 15.0f) < 1e-3, "15 blocks tall, got " + form.getBbHeight());
			helper.assertTrue(Math.abs(form.getBbWidth() - 5.0f) < 0.02f, "a player's proportions: 5 wide, got " + form.getBbWidth());
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, 3, true); // v0.16.7: Shift+X = the Attack Titan's signature, Rage
			helper.assertTrue(TitanShifter.cooldownRemaining(p, com.projecthero.mod.titanshifter.TitanMove.RAGE.id) > 0, "Shift+X rages");
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.LEAP) == 0, "and does not leap");
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.ROAR) == 0, "nor roar (V does)");
			// the rage bellow locks the Titan briefly, so let it finish before the next ability
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(form.hasMoveFlag(TitanFormEntity.FLAG_RAGING) && form.rage() == 1.0f, "raging (synced flag)");
				helper.assertTrue(Math.abs(form.outgoingDamageFactor() - 1.5f) < 1e-4f, "+50% damage at full rage");
				AbilityRouter.handleInput(p, 6, true); // Shift+C = Titan Hardening
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.HARDEN) > 0, "Shift+C hardens");
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.REGEN) == 0, "and does not regenerate");
				AbilityRouter.handleInput(p, 5, true); // Shift+V = Nape Guard
				helper.assertTrue(form.isNapeGuarded(), "Shift+V guards the nape");
				p.setShiftKeyDown(false);
				AbilityRouter.handleInput(p, 5, true); // V = Roar
				helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanAbilities.ROAR) > 0, "V roars");
				helper.succeed();
			});
		});
	}

	// ---------------- v0.12.39: the Emergency Titan ----------------

	private static void setEnergy(ServerPlayer p, float e) {
		TitanShifterState s = TitanShifter.state(p).copy();
		s.energy = e;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, s);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void holdingHForFiveSecondsAt30PercentMakesAWeakPaleSevenBlockTitan(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		setEnergy(p, 40.0f);
		TitanShifter.setCharges(p, 0); // v0.17.5: H only starts an emergency hold with no Titan charges left
		pump(helper, p);
		TitanShifter.requestToggle(p); // H pressed
		helper.runAfterDelay(60, () -> helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "still just holding after 3 s"));
		helper.runAfterDelay(110 + 2 * SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(TitanShifter.inTitan(p) && form != null, "the hold completed into a Titan");
			helper.assertTrue(form.isEmergency() && TitanShifter.state(p).inEmergency(), "it is the Emergency Titan");
			// v0.16.7: ~60% of the selected type's height (the Attack Titan's 15 -> 9)
			helper.assertTrue(Math.abs(form.getBbHeight() - 15.0f * 0.6f) < 0.05f, "9 blocks tall, got " + form.getBbHeight());
			helper.assertTrue(Math.abs(form.getMaxHealth() - 240.0f) < 0.5f, "40% less health (240), got " + form.getMaxHealth());
			helper.assertTrue(Math.abs(form.strengthFactor() - 0.6f) < 1e-4f, "40% weaker hits");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void releasingHOrBeingUnder30PercentNeverStartsAnEmergencyShift(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		setEnergy(p, 20.0f);
		TitanShifter.setCharges(p, 0); // v0.17.5: with a charge, H would simply shift
		pump(helper, p);
		TitanShifter.requestToggle(p);
		helper.runAfterDelay(130, () -> {
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "20% is not enough for an emergency shift");
			setEnergy(p, 50.0f);
			TitanShifter.requestToggle(p);
			helper.runAfterDelay(30, () -> {
				TitanShifter.cancelEmergencyHold(p); // H released early
				helper.runAfterDelay(100, () -> {
					helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN, "released early -> nothing happens");
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500)
	public void anEndedEmergencyTitanLeavesThePenalty(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		setEnergy(p, 50.0f);
		pump(helper, p);
		helper.assertTrue(TitanShifter.transform(p, true), "emergency transform");
		helper.runAfterDelay(2 * SETTLE, () -> {
			helper.assertTrue(TitanShifter.inTitan(p), "in the Titan");
			TitanShifter.revert(p);
			helper.runAfterDelay(TitanShifterConfig.transformation().revertTicks + 3, () -> afterEmergencyEnds(helper, p));
		});
	}

	private static void afterEmergencyEnds(GameTestHelper helper, ServerPlayer p) {
		{
			TitanShifterState s = TitanShifter.state(p);
			helper.assertTrue(s.emergencyPenalty && !s.inEmergency(), "the penalty is set when the form ends");
			setEnergy(p, 60.0f);
			s = TitanShifter.state(p);
			helper.assertFalse(TitanShifter.emergencyAllowed(s), "no second emergency shift until the bar is full");
			p.setHealth(10.0f);
			setEnergy(p, 40.0f);
			helper.assertFalse(TitanShifter.baseRegenActive(p, TitanShifter.state(p)), "no passive regeneration below 50% after an emergency");
			setEnergy(p, 60.0f);
			helper.assertTrue(TitanShifter.baseRegenActive(p, TitanShifter.state(p)), "passive regeneration is back at 50%");
			setEnergy(p, 0.0f);
			float before = TitanShifter.energy(p);
			float[] firstStep = { -1.0f };
			helper.onEachTick(() -> {
				float e = TitanShifter.energy(p);
				if (firstStep[0] < 0.0f && e > before) {
					firstStep[0] = e - before; // timing-independent: look at the size of the first refill step only
				}
			});
			helper.runAfterDelay(65, () -> {
				helper.assertTrue(firstStep[0] > 0.3f && firstStep[0] < 0.7f,
						"each refill step is a third of the normal 1% (0.33, or 0.67 if the mock player is ticked twice), got " + firstStep[0]);
				helper.succeed();
			});
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500)
	public void theEmergencyTitanIsForcedOutAfterTwoMinutes(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		setEnergy(p, 50.0f);
		pump(helper, p);
		helper.assertTrue(TitanShifter.transform(p, true), "emergency transform");
		helper.runAfterDelay(2 * SETTLE, () -> {
			TitanShifterState s = TitanShifter.state(p).copy();
			s.emergencyUntil = p.level().getGameTime() + 5; // fast-forward the two minutes
			p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, s);
			// v0.16.7: forced out through the 3 s REVERTING steam
			helper.runAfterDelay(20 + TitanShifterConfig.transformation().revertTicks, () -> {
				helper.assertTrue(TitanShifter.phase(p) == TitanPhase.HUMAN && TitanShifter.formOf(p) == null, "forced back out");
				helper.assertTrue(TitanShifter.state(p).emergencyPenalty, "and drained");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nTogglesThePassiveRegenerationInTheBaseForm(GameTestHelper helper) {
		ServerPlayer p = shifter(helper);
		p.setHealth(10.0f);
		helper.assertTrue(TitanShifter.baseRegenActive(p, TitanShifter.state(p)), "regeneration is on by default");
		TitanShifter.toggleRegen(p);
		helper.assertTrue(TitanShifter.state(p).regenOff, "N switched it off");
		helper.assertFalse(TitanShifter.baseRegenActive(p, TitanShifter.state(p)), "no passive healing while it is off");
		TitanShifter.toggleRegen(p);
		helper.assertTrue(TitanShifter.baseRegenActive(p, TitanShifter.state(p)), "and back on");
		helper.succeed();
	}
}
