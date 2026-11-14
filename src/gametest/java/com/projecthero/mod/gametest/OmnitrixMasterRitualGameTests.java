package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixRitual;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 (user: "i want master control to be something that can be activated at any point if the player knows how to do it,
 * make it kinda complicated"): the Master Control ritual ({@link OmnitrixRitual}) -- look up, three crouches while looking up,
 * a full round of the dial left, a full round right, then the slap on the third beep. Each failure quietly resets (or, for a
 * slap off the beat, mistransforms), and Shift+N held 3 s only turns Master Control off. Mock players are not ticked by the
 * server, so every test drives {@link Omnitrix#tick} itself; all timing is in ticks after the step before (step sizes).
 */
public class OmnitrixMasterRitualGameTests implements FabricGameTest {
	private static ServerPlayer wearer(GameTestHelper helper, boolean recal) {
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
		helper.assertTrue(Omnitrix.activate(p), "the watch up");
		Omnitrix.finishRaise(p);
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		return p;
	}

	private static int stage(ServerPlayer p) {
		return OmnitrixRitual.stage(Omnitrix.state(p));
	}

	private static void turn(ServerPlayer p, int dir, int steps) {
		for (int i = 0; i < steps; i++) {
			Omnitrix.clearDialCooldown(p);
			Omnitrix.cycle(p, dir);
		}
	}

	/** Steps 1-2: the look up (held), then three crouches with the eyes still up. */
	private static GameTestSequence lookAndCrouch(GameTestHelper helper, ServerPlayer p) {
		GameTestSequence seq = helper.startSequence()
				.thenExecute(() -> p.setXRot(-90f))
				.thenIdle(OmnitrixConfig.RITUAL_LOOK_TICKS + 3)
				.thenExecute(() -> helper.assertTrue(stage(p) == OmnitrixRitual.LOOKED, "looking straight up, held: step 1, got " + stage(p)));
		for (int i = 0; i < OmnitrixConfig.RITUAL_CROUCHES; i++) {
			seq = seq.thenExecute(() -> p.setShiftKeyDown(true)).thenIdle(2).thenExecute(() -> p.setShiftKeyDown(false)).thenIdle(2);
		}
		return seq.thenExecute(() -> helper.assertTrue(stage(p) == OmnitrixRitual.CROUCHED, "three crouches looking up: step 2, got " + stage(p)));
	}

	/** Steps 3-4: a full round left, then a full round right -- the beeps begin. */
	private static GameTestSequence spin(GameTestHelper helper, ServerPlayer p, GameTestSequence seq) {
		return seq.thenExecute(() -> {
			p.setXRot(0f); // the eyes may come down from here on
			String start = Omnitrix.state(p).selected;
			int n = Aliens.playable(p).size();
			turn(p, -1, n);
			helper.assertTrue(stage(p) == OmnitrixRitual.LEFT, "a full round left: step 3, got " + stage(p));
			helper.assertTrue(start.equals(Omnitrix.state(p).selected), "back where it started");
			turn(p, 1, n);
			helper.assertTrue(stage(p) == OmnitrixRitual.BEEPS, "a full round right: step 4, the beeps, got " + stage(p));
		});
	}

	private static void succeedsThrough(GameTestHelper helper, boolean recal) {
		ServerPlayer p = wearer(helper, recal);
		long[] at = { 0L };
		spin(helper, p, lookAndCrouch(helper, p))
				.thenExecute(() -> at[0] = Omnitrix.state(p).readyAt(OmnitrixRitual.AT))
				.thenWaitUntil(() -> helper.assertTrue(p.level().getGameTime() >= OmnitrixRitual.beepAt(at[0], OmnitrixConfig.RITUAL_BEEPS),
						"waiting for the third beep"))
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.state(p).readyAt(OmnitrixRitual.COUNT) == OmnitrixConfig.RITUAL_BEEPS, "three beeps played");
					helper.assertTrue(Omnitrix.slap(p), "the slap on the third beep");
					helper.assertTrue(Omnitrix.masterSequence(p), "the Master Control dial sequence plays");
					helper.assertFalse(Omnitrix.transformed(p), "no transformation");
				})
				.thenIdle((Aliens.playable(p).size() + 2) * OmnitrixConfig.MASTER_STEP_TICKS)
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.masterControl(p), "Master Control is on");
					helper.assertTrue(Omnitrix.masterKnown(p), "and remembered (the Guidebook's hidden page)");
					helper.assertFalse(Omnitrix.selecting(p), "the watch is down again");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theFullRitualWakesMasterControl(GameTestHelper helper) {
		succeedsThrough(helper, false);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theRitualWorksOnTheRecalibratedWatchToo(GameTestHelper helper) {
		succeedsThrough(helper, true);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aWrongWayTurnStartsItOver(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false);
		lookAndCrouch(helper, p)
				.thenExecute(() -> {
					turn(p, 1, 1); // right first: the wrong way
					helper.assertTrue(stage(p) == 0, "a right turn before the left round resets it, got " + stage(p));
				})
				.thenExecute(() -> p.setXRot(-90f))
				.thenIdle(OmnitrixConfig.RITUAL_LOOK_TICKS + 3)
				.thenExecute(() -> {
					helper.assertTrue(stage(p) == OmnitrixRitual.LOOKED, "it can be started again");
					Omnitrix.deactivate(p);
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertTrue(stage(p) == 0 && !Omnitrix.masterControl(p), "the watch down resets it"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void dawdlingStartsItOver(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false);
		lookAndCrouch(helper, p)
				.thenExecute(() -> p.setXRot(0f))
				.thenIdle(OmnitrixConfig.RITUAL_STEP_TICKS + 5)
				.thenExecute(() -> {
					helper.assertTrue(stage(p) == 0, "more than 6 s before the dial: reset, got " + stage(p));
					turn(p, -1, Aliens.playable(p).size());
					helper.assertTrue(stage(p) == 0, "and the left round now counts for nothing");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void lookingDownBeforeTheCrouchesStartsItOver(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false);
		helper.startSequence()
				.thenExecute(() -> p.setXRot(-90f))
				.thenIdle(OmnitrixConfig.RITUAL_LOOK_TICKS + 3)
				.thenExecute(() -> {
					helper.assertTrue(stage(p) == OmnitrixRitual.LOOKED, "step 1");
					p.setShiftKeyDown(true);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					p.setShiftKeyDown(false);
					p.setXRot(-30f);
				})
				.thenIdle(2)
				.thenExecute(() -> helper.assertTrue(stage(p) == 0, "eyes down mid-crouches: reset, got " + stage(p)))
				.thenSucceed();
	}

	/** A slap off the beat: {@code ticksAfterBeeps} after the right round finished. */
	private static void missesAt(GameTestHelper helper, int ticksAfterBeeps, String what) {
		ServerPlayer p = wearer(helper, false);
		long[] at = { 0L };
		String[] dialled = { "" };
		spin(helper, p, lookAndCrouch(helper, p))
				.thenExecute(() -> {
					at[0] = Omnitrix.state(p).readyAt(OmnitrixRitual.AT);
					dialled[0] = Omnitrix.state(p).selected;
				})
				.thenWaitUntil(() -> helper.assertTrue(p.level().getGameTime() >= at[0] + ticksAfterBeeps, "waiting"))
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.slap(p), what + ": the slap still goes down");
					helper.assertFalse(Omnitrix.masterSequence(p), what + ": no Master Control");
					helper.assertTrue(stage(p) == 0, what + ": the ritual is over");
				})
				.thenIdle(Omnitrix.SLAP_TICKS + 3)
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.transformed(p), what + ": a transformation");
					helper.assertFalse(dialled[0].equals(Omnitrix.active(p)), what + ": into the wrong alien, got " + Omnitrix.active(p));
					helper.assertTrue(dialled[0].equals(Omnitrix.state(p).selected), what + ": the dial stays where it was");
					helper.assertFalse(Omnitrix.masterControl(p), what + ": Master Control stays off");
					Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void aSlapOnTheSecondBeepMistransforms(GameTestHelper helper) {
		missesAt(helper, 2 * OmnitrixConfig.RITUAL_BEEP_TICKS, "on beep 2");
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void aSlapAfterTheWindowMistransforms(GameTestHelper helper) {
		missesAt(helper, OmnitrixConfig.RITUAL_BEEPS * OmnitrixConfig.RITUAL_BEEP_TICKS + OmnitrixConfig.RITUAL_WINDOW_TICKS + 4, "late");
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void shiftNHeldThreeSecondsOnlyTurnsItOff(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, false);
		helper.startSequence()
				.thenExecute(() -> {
					// without Master Control the hold does nothing any more
					p.setShiftKeyDown(true);
					AbilityRouter.handleInput(p, AbilitySlot.SLOT_8.number(), true);
				})
				.thenIdle(OmnitrixConfig.MASTER_HOLD_TICKS + 10)
				.thenExecute(() -> {
					helper.assertFalse(Omnitrix.masterSequence(p) || Omnitrix.masterControl(p), "the old Shift+N activation is gone");
					AbilityRouter.handleInput(p, AbilitySlot.SLOT_8.number(), false);
					p.setShiftKeyDown(false);
					Omnitrix.setMasterControl(p, true);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					p.setShiftKeyDown(true);
					AbilityRouter.handleInput(p, AbilitySlot.SLOT_8.number(), true);
				})
				.thenIdle(OmnitrixConfig.MASTER_HOLD_TICKS - 10)
				.thenExecute(() -> helper.assertFalse(Omnitrix.masterSequence(p), "not before 3 s"))
				.thenIdle(15)
				.thenExecute(() -> {
					helper.assertTrue(Omnitrix.masterSequence(p), "3 s of Shift+N: the dial sequence turns it off");
					AbilityRouter.handleInput(p, AbilitySlot.SLOT_8.number(), false);
					p.setShiftKeyDown(false);
				})
				.thenIdle((Aliens.playable(p).size() + 2) * OmnitrixConfig.MASTER_STEP_TICKS)
				.thenExecute(() -> helper.assertFalse(Omnitrix.masterControl(p), "Master Control is off"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theGuidebookPageStaysHiddenUntilWoken(GameTestHelper helper) {
		var chapters = HeroPackGuide.chapters();
		int page = -1;
		for (int i = 0; i < chapters.size(); i++) {
			if (chapters.get(i).title().getContents() instanceof TranslatableContents t && t.getKey().equals("projecthero.guide.omnitrix.master")) {
				page = i;
			}
		}
		helper.assertTrue(page >= 0, "the Master Control page exists");
		final int mc = page;
		var before = HeroPackGuide.masterControlRevealed;
		try {
			HeroPackGuide.masterControlRevealed = () -> false;
			helper.assertTrue(HeroPackGuide.hidden(mc), "hidden before");
			helper.assertFalse(HeroPackGuide.categoryEntries(HeroPackGuide.Category.OTHER).stream().anyMatch(e -> e.chapterIndex() == mc),
					"not listed before Master Control is woken");
			helper.assertTrue(HeroPackGuide.search("heartbeat").stream().noneMatch(h -> h.chapter() == mc), "not found by search either");
			HeroPackGuide.masterControlRevealed = () -> true;
			helper.assertTrue(HeroPackGuide.categoryEntries(HeroPackGuide.Category.OTHER).stream().anyMatch(e -> e.chapterIndex() == mc),
					"listed once woken");
		} finally {
			HeroPackGuide.masterControlRevealed = before;
		}
		var lang = net.minecraft.locale.Language.getInstance();
		for (String k : new String[] { "note", "step1", "step2", "step3", "step4", "step5", "step6", "warning", "sleep", "sign" }) {
			helper.assertTrue(lang.has("projecthero.guide.omnitrix.master." + k), "lang " + k);
		}
		helper.assertTrue(lang.has("projecthero.omnitrix.info.rule.master"), "the vague hint");
		helper.succeed();
	}
}
