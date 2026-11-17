package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixCodeEntry;
import com.projecthero.mod.omnitrix.OmnitrixCodes;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixFunction;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixSecrets;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "after priming the omnitrix the player can shift+right click to go into function mode of the omnitrix ... change
 * how master control is gained, it should be a random 6 number combination, add a way for the player to slowly discover random
 * numbers for the master control"): the watch's function mode ({@link OmnitrixFunction}), the code entry ({@link OmnitrixCodeEntry}),
 * the codes and their discovery ({@link OmnitrixCodes}). Mock players are not ticked by the server, so each test drives
 * {@link Omnitrix#tick} itself; timings are asserted as step sizes from the step before.
 */
public class OmnitrixFunctionModeGameTests implements FabricGameTest {
	private static final String CODE = "246813";

	private static ServerPlayer wearer(GameTestHelper helper, boolean recal, boolean raise) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.assertTrue(Omnitrix.grant(p, recal), "the Omnitrix goes on");
		Omnitrix.noGlitch(p);
		OmnitrixCodes.setMasterCode(p, CODE);
		if (raise) {
			helper.assertTrue(Omnitrix.activate(p), "the watch up");
			Omnitrix.finishRaise(p);
		}
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		return p;
	}

	private static void inFunction(GameTestHelper helper, ServerPlayer p) {
		helper.assertTrue(OmnitrixFunction.enter(p), "Shift + right-click with the watch primed: function mode");
		helper.assertTrue(OmnitrixFunction.active(Omnitrix.state(p)), "in function mode");
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void functionModeOnlyOnAPrimedWatch(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, false);
		helper.assertFalse(OmnitrixFunction.enter(p), "the watch down: no function mode");
		helper.assertTrue(Omnitrix.activate(p), "the watch up");
		helper.assertFalse(OmnitrixFunction.enter(p), "the core still coming up: no function mode yet");
		Omnitrix.finishRaise(p);
		inFunction(helper, p);
		String dialled = Omnitrix.state(p).selected;
		Omnitrix.clearDialCooldown(p);
		helper.assertFalse(Omnitrix.cycle(p, 1), "the scroll wheel does not turn the dial in function mode");
		helper.assertTrue(dialled.equals(Omnitrix.state(p).selected), "the dial stays put");
		helper.assertFalse(Omnitrix.slap(p), "no slap in function mode");
		helper.assertTrue(OmnitrixFunction.toggle(p) && !OmnitrixFunction.active(Omnitrix.state(p)), "Shift + right-click again leaves it");
		helper.assertTrue(Omnitrix.selecting(p), "the watch is still up (primed again)");
		inFunction(helper, p);
		helper.assertTrue(Omnitrix.deactivate(p), "R lowers the watch");
		helper.assertFalse(OmnitrixFunction.active(Omnitrix.state(p)), "lowering the watch ends function mode");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theEntryScrollsWrapsAndAdvances(GameTestHelper helper) {
		OmnitrixCodeEntry e = new OmnitrixCodeEntry();
		helper.assertTrue("000000".equals(e.code()) && e.slot() == 0, "starts on 000000, slot 1");
		e.scroll(1);
		helper.assertTrue(e.digit(0) == 1, "scroll up: +1");
		e.scroll(-1);
		e.scroll(-1);
		helper.assertTrue(e.digit(0) == 9, "scroll down past 0 wraps to 9");
		e.scroll(1);
		helper.assertTrue(e.digit(0) == 0, "and up past 9 wraps to 0");
		helper.assertTrue(e.advance() == null && e.slot() == 1, "middle click: the next slot");
		e.scroll(1);
		e.scroll(1);
		helper.assertTrue(e.digit(1) == 2 && e.digit(0) == 0, "only the current slot changes");
		e.back();
		helper.assertTrue(e.slot() == 0, "Shift + middle click: back a slot");
		e.back();
		helper.assertTrue(e.slot() == 0, "it stops at the first");
		for (int i = 0; i < OmnitrixCodeEntry.LENGTH - 1; i++) {
			helper.assertTrue(e.advance() == null, "slots 1-5 just move on");
		}
		helper.assertTrue(e.slot() == OmnitrixCodeEntry.LENGTH - 1, "on the sixth slot");
		String code = e.advance();
		helper.assertTrue("020000".equals(code), "the sixth middle click submits the code, got " + code);
		helper.assertTrue(e.slot() == 0, "and the cursor goes back to slot 1");
		helper.assertTrue(OmnitrixCodeEntry.valid("123456") && !OmnitrixCodeEntry.valid("12345") && !OmnitrixCodeEntry.valid("12a456"),
				"only six digits are a code");
		helper.succeed();
	}

	private static void codeTogglesMasterControl(GameTestHelper helper, boolean recal) {
		ServerPlayer p = wearer(helper, recal, true);
		int steps = OmnitrixConfig.MASTER_FLASH_TICKS + (Aliens.playable(p).size() + 2) * OmnitrixConfig.MASTER_STEP_TICKS; // v0.17.5 round 2: the flash first
		helper.startSequence()
				.thenExecute(() -> {
					inFunction(helper, p);
					helper.assertTrue(OmnitrixFunction.submit(p, CODE) == OmnitrixFunction.Outcome.MASTER, "his own code is accepted");
					helper.assertTrue(Omnitrix.masterSequence(p), "the Master Control dial sequence plays");
					helper.assertFalse(OmnitrixFunction.active(Omnitrix.state(p)), "function mode is left for it");
					helper.assertTrue(OmnitrixCodes.knownCount(p) == OmnitrixConfig.CODE_LENGTH, "typing it means he knows all six");
				})
				.thenIdle(steps)
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.masterControl(p), "Master Control is on");
					helper.assertTrue(Omnitrix.masterKnown(p), "and remembered");
					helper.assertFalse(Omnitrix.selecting(p), "the watch is down again");
					helper.assertTrue(Omnitrix.activate(p), "raised again");
					Omnitrix.finishRaise(p);
					inFunction(helper, p);
					helper.assertTrue(OmnitrixFunction.submit(p, CODE) == OmnitrixFunction.Outcome.MASTER, "the same code again");
				})
				.thenIdle(steps)
				.thenExecute(() -> helper.assertFalse(Omnitrix.masterControl(p), "the same code turns Master Control off"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theRightCodeWakesMasterControlAndPutsItToSleep(GameTestHelper helper) {
		codeTogglesMasterControl(helper, false);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theCodeWorksOnTheRecalibratedWatchToo(GameTestHelper helper) {
		codeTogglesMasterControl(helper, true);
	}

	/**
	 * v0.17.5 round 2 (user: "When I entered the master control code nothing happened"): the test client's log showed Master Control
	 * already on (the tune command) when the code went in, so the code turned it OFF -- and R during the sequence threw it away
	 * without a word. Now: the code opens with the 2 s white / black flash, R cannot cut the flash or the dial run short, and the
	 * sequence knows which way it goes (on from off, off from on).
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theCodeFlashesFirstAndRCannotCutItShort(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, true);
		int dial = (Aliens.playable(p).size() + 2) * OmnitrixConfig.MASTER_STEP_TICKS;
		String[] first = { "" };
		helper.startSequence()
				.thenExecute(() -> {
					inFunction(helper, p);
					first[0] = Omnitrix.state(p).selected;
					helper.assertTrue(OmnitrixFunction.submit(p, CODE) == OmnitrixFunction.Outcome.MASTER, "his own code is accepted");
					OmnitrixState s = Omnitrix.state(p);
					helper.assertTrue(Omnitrix.masterFlashAge(s, p.level().getGameTime(), 0f) >= 0f, "the flash starts at once");
					helper.assertTrue(Omnitrix.masterTurningOn(s), "it is going ON (it was off)");
					com.projecthero.mod.omnitrix.OmnitrixAbilityManager.pressR(p);
					helper.assertTrue(Omnitrix.selecting(p) && Omnitrix.masterSequence(p), "R does not lower the watch mid-sequence");
				})
				.thenIdle(OmnitrixConfig.MASTER_FLASH_TICKS / 2)
				.thenExecute(() -> {
					helper.assertTrue(first[0].equals(Omnitrix.state(p).selected), "the dial waits while it flashes");
					com.projecthero.mod.omnitrix.OmnitrixAbilityManager.pressR(p);
					helper.assertTrue(Omnitrix.masterSequence(p), "still running after a second R");
				})
				.thenIdle(OmnitrixConfig.MASTER_FLASH_TICKS / 2 + OmnitrixConfig.MASTER_STEP_TICKS * 2 + 1)
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.masterFlashAge(Omnitrix.state(p), p.level().getGameTime(), 0f) < 0f, "the flash is over");
					helper.assertFalse(first[0].equals(Omnitrix.state(p).selected), "and the dial is turning");
				})
				.thenIdle(dial)
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.masterControl(p), "Master Control is on");
					helper.assertFalse(Omnitrix.selecting(p), "the watch is down");
					// already on (as the tune command left the tester's watch): the same code turns it off, and says so
					helper.assertTrue(Omnitrix.activate(p), "raised again");
					Omnitrix.finishRaise(p);
					inFunction(helper, p);
					helper.assertTrue(OmnitrixFunction.submit(p, CODE) == OmnitrixFunction.Outcome.MASTER, "the code again");
					helper.assertFalse(Omnitrix.masterTurningOn(Omnitrix.state(p)), "this time it is going OFF");
				})
				.thenIdle(OmnitrixConfig.MASTER_FLASH_TICKS + dial)
				.thenExecute(() -> helper.assertFalse(Omnitrix.masterControl(p), "Master Control is off"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void aWrongCodeHintsAndLocksForTenSeconds(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, true);
		long[] at = { 0L };
		helper.startSequence()
				.thenExecute(() -> {
					inFunction(helper, p);
					// 2 4 . . . 3 right in place against 246813
					helper.assertTrue(OmnitrixFunction.submit(p, "249993") == OmnitrixFunction.Outcome.WRONG, "a wrong code");
					OmnitrixState s = Omnitrix.state(p);
					helper.assertTrue(OmnitrixFunction.hint(s) == 0b100011, "the right-place digits light (Mastermind), got "
							+ Integer.toBinaryString(OmnitrixFunction.hint(s)));
					at[0] = p.level().getGameTime();
					helper.assertTrue(OmnitrixFunction.lockLeft(s, at[0]) == OmnitrixConfig.CODE_LOCK_TICKS, "locked for 10 s");
					helper.assertFalse(Omnitrix.masterControl(p), "no Master Control");
					helper.assertTrue(OmnitrixFunction.active(s), "still in function mode, locked");
					helper.assertTrue(OmnitrixFunction.submit(p, CODE) == OmnitrixFunction.Outcome.LOCKED, "even the right code is refused while locked");
				})
				.thenIdle(OmnitrixConfig.CODE_LOCK_TICKS / 2)
				.thenExecute(() -> helper.assertTrue(OmnitrixFunction.locked(Omnitrix.state(p), p.level().getGameTime()), "half way: still locked"))
				.thenWaitUntil(() -> helper.assertTrue(p.level().getGameTime() >= at[0] + OmnitrixConfig.CODE_LOCK_TICKS, "the lock running out"))
				.thenExecute(() -> {
					helper.assertFalse(OmnitrixFunction.locked(Omnitrix.state(p), p.level().getGameTime()), "the lock is over");
					helper.assertTrue(OmnitrixFunction.submit(p, "000000") == OmnitrixFunction.Outcome.WRONG, "it listens again");
					helper.assertTrue(OmnitrixFunction.hint(Omnitrix.state(p)) == 0, "no digit right: no hint");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aHiddenAlienCodeUnlocksIt(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, true);
		Aliens.Alien target = null;
		for (Aliens.Alien a : OmnitrixCodes.codeable()) {
			if (!Aliens.found(p, a.id())) {
				target = a;
				break;
			}
		}
		if (target == null) {
			helper.succeed(); // nothing hidden registered (all held back)
			return;
		}
		String code = OmnitrixCodes.secrets(p).aliens().get(target.id());
		helper.assertTrue(OmnitrixCodeEntry.valid(code) && !code.equals(CODE) && !code.equals(OmnitrixConfig.SELF_DESTRUCT_CODE),
				"every hidden alien has its own code");
		inFunction(helper, p);
		helper.assertTrue(OmnitrixFunction.submit(p, code) == OmnitrixFunction.Outcome.ALIEN, "its code is accepted");
		helper.assertTrue(Aliens.found(p, target.id()), target.id() + " is on his dial now");
		helper.assertTrue(code.equals(OmnitrixCodes.knownAlienCodes(p).get(target.id())), "and its code is listed as found");
		helper.assertTrue(target.id().equals(Omnitrix.state(p).selected), "the dial jumps to it");
		helper.assertFalse(Omnitrix.masterControl(p), "it is not Master Control");
		Aliens.discover(p, target.id(), false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theSelfDestructCountsDownCancelsAndFizzlesHarmlessly(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, true);
		BlockPos under = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.startSequence()
				.thenExecute(() -> {
					inFunction(helper, p);
					helper.assertTrue(OmnitrixFunction.submit(p, OmnitrixConfig.SELF_DESTRUCT_CODE) == OmnitrixFunction.Outcome.SELF_DESTRUCT_ARMED,
							"the self-destruct code starts the countdown");
					long now = p.level().getGameTime();
					helper.assertTrue(OmnitrixFunction.selfDestructLeft(Omnitrix.state(p), now) == OmnitrixConfig.SELF_DESTRUCT_TICKS, "15 s on the clock");
					OmnitrixFunction.clearLock(p);
					helper.assertTrue(OmnitrixFunction.submit(p, OmnitrixConfig.SELF_DESTRUCT_CODE) == OmnitrixFunction.Outcome.SELF_DESTRUCT_CANCELLED,
							"the same code again cancels it");
					helper.assertFalse(OmnitrixFunction.selfDestructing(Omnitrix.state(p)), "no countdown");
					OmnitrixFunction.clearLock(p);
					OmnitrixFunction.submit(p, OmnitrixConfig.SELF_DESTRUCT_CODE);
					OmnitrixFunction.toggle(p); // out of function mode
					helper.assertFalse(Omnitrix.slap(p), "the watch will not transform during the countdown");
					OmnitrixFunction.setSelfDestructElapsed(p, OmnitrixConfig.SELF_DESTRUCT_TICKS);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					OmnitrixState s = Omnitrix.state(p);
					long now = p.level().getGameTime();
					helper.assertFalse(OmnitrixFunction.selfDestructing(s), "the countdown reached zero");
					helper.assertTrue(OmnitrixFunction.lastOutcome(s) == OmnitrixFunction.Outcome.FIZZLED, "and fizzled");
					helper.assertTrue(s.hasPower, "the watch is still on");
					helper.assertFalse(s.selecting, "it dropped");
					helper.assertTrue(s.rechargeUntil - now > OmnitrixConfig.SELF_DESTRUCT_FIZZLE_TICKS - 10, "overheated: a lock-out");
					helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 0.01f, "nobody was hurt");
					helper.assertTrue(helper.getLevel().getBlockState(under).is(Blocks.STONE), "nothing was destroyed");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theWatchFlickerAndDataFragmentsRevealDigits(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, false);
		OmnitrixCodes.forgetAll(p);
		helper.assertTrue(OmnitrixCodes.knownCount(p) == 0, "nothing known");
		OmnitrixCodes.Reveal r = OmnitrixCodes.flicker(p, 1f);
		helper.assertTrue(r.kind() == OmnitrixCodes.Kind.DIGIT, "the flicker shows a digit");
		helper.assertTrue(OmnitrixCodes.knownCount(p) == 1, "one digit known");
		helper.assertTrue(r.value() == CODE.charAt(r.index()) - '0', "the right digit in its right place");
		helper.assertTrue(OmnitrixCodes.flicker(p, 1f).kind() == OmnitrixCodes.Kind.NONE, "never two flickers inside the cooldown");
		OmnitrixCodes.clearFlickerCooldown(p);
		helper.assertTrue(OmnitrixCodes.flicker(p, 1f).kind() == OmnitrixCodes.Kind.DIGIT, "after the cooldown: another");
		helper.assertTrue(OmnitrixCodes.knownCount(p) == 2, "two digits known, both different places");
		int before = OmnitrixCodes.knownCount(p) + OmnitrixCodes.knownAlienCodes(p).size();
		OmnitrixCodes.FragmentResult f = OmnitrixCodes.useFragment(p);
		helper.assertTrue(f == OmnitrixCodes.FragmentResult.DIGIT || f == OmnitrixCodes.FragmentResult.ALIEN, "a Data Fragment shows something, got " + f);
		helper.assertTrue(OmnitrixCodes.knownCount(p) + OmnitrixCodes.knownAlienCodes(p).size() == before + 1, "exactly one new thing");
		ServerPlayer bare = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(OmnitrixCodes.useFragment(bare) == OmnitrixCodes.FragmentResult.NO_WATCH, "without a watch nothing happens");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void codesBelongToThePlayerAndOutliveTheWatch(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false, false);
		ServerPlayer q = helper.makeMockServerPlayerInLevel();
		String mine = OmnitrixCodes.secrets(p).master();
		helper.assertTrue(mine.equals(OmnitrixCodes.secrets(p).master()), "the code is stable");
		helper.assertFalse(OmnitrixCodes.secrets(q).master().equals(mine), "another player has his own code");
		OmnitrixCodes.forgetAll(p);
		OmnitrixCodes.learnDigit(p, 4);
		Omnitrix.revoke(p);
		helper.assertFalse(Omnitrix.hasPower(p), "the watch is off");
		helper.assertTrue(OmnitrixCodes.knownDigit(p, 4) == CODE.charAt(4) - '0', "the found digit is still his");
		helper.assertTrue(CODE.equals(OmnitrixCodes.secrets(p).master()), "and so is the code");
		helper.assertTrue(Omnitrix.grant(p), "the watch back on");
		helper.assertTrue(OmnitrixCodes.knownCount(p) == 1, "the found digit comes back with it");
		// a relog: both attachments through their save codecs
		OmnitrixSecrets saved = OmnitrixSecrets.CODEC.parse(JsonOps.INSTANCE,
				OmnitrixSecrets.CODEC.encodeStart(JsonOps.INSTANCE, p.getAttached(OmnitrixAttachments.SECRETS)).getOrThrow()).getOrThrow();
		helper.assertTrue(saved.equals(p.getAttached(OmnitrixAttachments.SECRETS)), "the secrets survive a save and load");
		OmnitrixState state = OmnitrixState.CODEC.parse(JsonOps.INSTANCE,
				OmnitrixState.CODEC.encodeStart(JsonOps.INSTANCE, Omnitrix.state(p)).getOrThrow()).getOrThrow();
		helper.assertTrue(state.readyAt(OmnitrixCodes.digitKey(4)) == CODE.charAt(4) - '0', "the found digit survives a save and load");
		helper.succeed();
	}
}
