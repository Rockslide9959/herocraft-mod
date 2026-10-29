package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAbilityManager;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "when detransforming the watch should first beep 5 times like how it does in the mp3 file and then
 * detransform"): V while transformed and the 3-minute timeout start the power-down sequence synced to the user's
 * omnitrix-power-down sound -- the beeps on their measured ticks, the revert {@link Omnitrix#POWER_DOWN_REVERT_TICK} ticks
 * in; a second V during the beeps changes nothing; death, logout and a world change mid-sequence revert at once and leave no
 * power-down state behind. Mock players are not ticked by the server, so each test drives {@link Omnitrix#tick} itself.
 */
public class OmnitrixPowerDownGameTests implements FabricGameTest {
	/** The measured onsets in the shipped OGG (scratchpad/detect_omnitrix_beeps.js), seconds. */
	private static final double[] BEEP_SECONDS = { 0.050, 0.469, 0.958, 1.487, 2.015 };
	private static final double POWER_DOWN_ONSET = 2.724, POWER_DOWN_LOUDEST = 2.829;

	private static ServerPlayer alien(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.transform(p), "transformed");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBeepsAreOnTheMeasuredTicks(GameTestHelper helper) {
		int[] beeps = Omnitrix.POWER_DOWN_BEEPS;
		helper.assertTrue(beeps.length == 5, "five beeps");
		for (int i = 0; i < beeps.length; i++) {
			helper.assertTrue(beeps[i] == (int) Math.round(BEEP_SECONDS[i] * 20), "beep " + (i + 1) + " on its measured tick");
			helper.assertTrue(i == 0 || beeps[i] > beeps[i - 1], "in order");
		}
		int rev = Omnitrix.POWER_DOWN_REVERT_TICK;
		helper.assertTrue(rev > beeps[4] && rev >= Math.round(POWER_DOWN_ONSET * 20) && rev <= Math.round(POWER_DOWN_LOUDEST * 20),
				"the revert lands between the power-down's onset and its loudest part");
		// the client-side reading of a sequence that began at T
		ServerPlayer p = alien(helper);
		long t0 = helper.getLevel().getGameTime();
		OmnitrixState s = Omnitrix.state(p).copy();
		s.timers.put(Omnitrix.POWER_DOWN_TIMER, t0);
		for (int i = 0; i < beeps.length; i++) {
			helper.assertTrue(Omnitrix.beepsDone(s, t0 + beeps[i] - 1) == i, "before beep " + (i + 1));
			helper.assertTrue(Omnitrix.beepsDone(s, t0 + beeps[i]) == i + 1, "on beep " + (i + 1));
			helper.assertTrue(Omnitrix.beepBlink(s, t0 + beeps[i], 0f) > 0.99f, "the face blinks red on beep " + (i + 1));
		}
		helper.assertTrue(Omnitrix.beepBlink(s, t0 + 5, 0f) == 0f, "and is dark between beeps");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void vPowersDownForTheRightTicksAndASecondVDoesNothing(GameTestHelper helper) {
		ServerPlayer p = alien(helper);
		pump(helper, p);
		OmnitrixAbilityManager.pressV(p);
		long start = Omnitrix.state(p).readyAt(Omnitrix.POWER_DOWN_TIMER);
		helper.assertTrue(Omnitrix.poweringDown(p) && Omnitrix.transformed(p), "V starts the beeps; still the alien");
		int left = Omnitrix.timeLeft(p);
		helper.runAfterDelay(20, () -> {
			// a second V (and Shift+V) mid-beeps: ignored, the sequence keeps its start
			OmnitrixAbilityManager.pressV(p);
			helper.assertFalse(Omnitrix.revertEarly(p), "a second V is refused");
			helper.assertTrue(Omnitrix.state(p).readyAt(Omnitrix.POWER_DOWN_TIMER) == start, "the sequence is not restarted");
			helper.assertTrue(Omnitrix.transformed(p), "still the alien during the beeps");
			helper.assertTrue(Omnitrix.timeLeft(p) == left, "the time limit is stopped");
		});
		helper.runAfterDelay(Omnitrix.POWER_DOWN_REVERT_TICK - 4, () -> helper.assertTrue(Omnitrix.transformed(p), "not yet"));
		helper.runAfterDelay(Omnitrix.POWER_DOWN_REVERT_TICK + 4, () -> {
			OmnitrixState s = Omnitrix.state(p);
			helper.assertFalse(s.transformed(), "turned back");
			helper.assertTrue(s.animId == Omnitrix.ANIM_REVERT, "with the red revert flash");
			helper.assertTrue(s.animStart - start == Omnitrix.POWER_DOWN_REVERT_TICK, "exactly POWER_DOWN_REVERT_TICK after V: " + (s.animStart - start));
			helper.assertFalse(s.timers.containsKey(Omnitrix.POWER_DOWN_TIMER) || s.timers.containsKey(Omnitrix.POWER_DOWN_TIMEOUT), "no state left");
			helper.assertTrue(Math.abs(Omnitrix.rechargeLeft(p) - OmnitrixConfig.EARLY_RECHARGE_TICKS) <= 5, "the short recharge, once");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void theTimeoutPowersDownThenTimesOut(GameTestHelper helper) {
		ServerPlayer p = alien(helper);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.endsAt = helper.getLevel().getGameTime() + 1;
		Omnitrix.save(p, n);
		pump(helper, p);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(Omnitrix.poweringDown(p) && Omnitrix.transformed(p), "the timeout starts the beeps");
			helper.assertTrue(Omnitrix.state(p).readyAt(Omnitrix.POWER_DOWN_TIMEOUT) == 1L, "marked as the timeout");
			helper.assertTrue(Omnitrix.timeLeft(p) == 0, "the timer sits at 0");
		});
		helper.runAfterDelay(Omnitrix.POWER_DOWN_REVERT_TICK + 6, () -> {
			helper.assertFalse(Omnitrix.transformed(p), "timed out");
			helper.assertTrue(Omnitrix.state(p).animId == Omnitrix.ANIM_TIMEOUT, "with the timeout flash");
			helper.assertTrue(Math.abs(Omnitrix.rechargeLeft(p) - OmnitrixConfig.RECHARGE_TICKS) <= 6, "then the 90 s recharge");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void deathMidBeepsRevertsCleanly(GameTestHelper helper) {
		ServerPlayer p = alien(helper);
		pump(helper, p);
		Omnitrix.revertEarly(p);
		helper.runAfterDelay(15, () -> {
			p.kill();
			if (Omnitrix.transformed(p)) {
				Omnitrix.onDeath(p); // the event, should the mock's death not fire it
			}
			OmnitrixState s = Omnitrix.state(p);
			helper.assertFalse(s.transformed(), "dead: human at once");
			helper.assertFalse(Omnitrix.poweringDown(p) || s.timers.containsKey(Omnitrix.POWER_DOWN_TIMER), "no power-down left");
			helper.assertTrue(Omnitrix.hasPower(p), "the watch stays");
		});
		helper.runAfterDelay(Omnitrix.POWER_DOWN_REVERT_TICK + 5, () -> {
			helper.assertFalse(Omnitrix.state(p).transformed(), "nothing happens later");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void logoutOrAWorldChangeMidBeepsRevertsCleanly(GameTestHelper helper) {
		ServerPlayer p = alien(helper);
		Omnitrix.revertEarly(p);
		Omnitrix.onLogout(p);
		helper.assertFalse(Omnitrix.state(p).transformed() || Omnitrix.state(p).timers.containsKey(Omnitrix.POWER_DOWN_TIMER), "logout: human, clean");
		ServerPlayer q = alien(helper);
		Omnitrix.revertEarly(q);
		Omnitrix.onDimensionChange(q);
		OmnitrixState s = Omnitrix.state(q);
		helper.assertFalse(s.transformed() || s.timers.containsKey(Omnitrix.POWER_DOWN_TIMER), "world change: human, clean");
		helper.assertTrue(Omnitrix.rechargeLeft(q) > 0, "with the recharge it was heading for");
		helper.succeed();
	}
}
