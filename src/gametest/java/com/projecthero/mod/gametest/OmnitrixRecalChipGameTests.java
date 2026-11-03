package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixRecal;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.item.OmnitrixItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "the player can right click the chip and then an option to choose to recalibrate or not will pop up ... The omnitrix
 * then enters recalibration mode for 3 minutes, if the player dies it wont halt anything, the watch should stay recalibrating no matter
 * what"): the Recalibration Chip's own recalibration ({@link OmnitrixRecal}) -- its refusals, Yes starting the insert and the chip
 * being taken on the insert beat (not before), the 3 minutes kept through a death and finishing into the Recalibrated Omnitrix, and
 * the watch refusing to be used meanwhile.
 */
public final class OmnitrixRecalChipGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	/** A prototype wearer broken in with {@code transforms} transformations, holding a chip. */
	private static ServerPlayer wearer(GameTestHelper helper, double x, int transforms) {
		ServerPlayer p = mock(helper, x);
		helper.assertTrue(Omnitrix.grant(p), "the prototype goes on");
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(Omnitrix.TRANSFORMS, (long) transforms);
		n.animId = 0; // the grant's slam-on pose
		Omnitrix.save(p, n);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OmnitrixItems.RECALIBRATION_CHIP));
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theChipRefusesWhenItCannotRecalibrate(GameTestHelper helper) {
		// too few transformations: says how many
		ServerPlayer green = wearer(helper, 1.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS - 3);
		helper.assertTrue(OmnitrixRecal.refusal(green) != null, "24 and under: refused");
		var args = ((net.minecraft.network.chat.contents.TranslatableContents) OmnitrixRecal.refusal(green).getContents()).getArgs();
		helper.assertTrue(args.length == 3 && Integer.valueOf(22).equals(args[0]) && Integer.valueOf(3).equals(args[2]),
				"the message says how many (22 of 25, 3 more): " + java.util.Arrays.toString(args));
		helper.assertFalse(OmnitrixRecal.request(green), "Yes is refused too");
		green.getMainHandItem().use(helper.getLevel(), green, InteractionHand.MAIN_HAND);
		helper.assertFalse(OmnitrixRecal.busy(green), "nothing started");
		helper.assertTrue(green.getMainHandItem().getCount() == 1, "the chip is kept");
		// transformed
		ServerPlayer alien = wearer(helper, 3.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		Omnitrix.select(alien, Aliens.HEATBLAST.id());
		helper.assertTrue(Omnitrix.transform(alien), "transformed");
		helper.assertTrue(OmnitrixRecal.refusal(alien) != null && !OmnitrixRecal.request(alien), "an alien cannot recalibrate");
		Omnitrix.revert(alien, Omnitrix.Reason.REVOKE);
		// already recalibrated
		ServerPlayer done = mock(helper, 5.5);
		Omnitrix.grant(done, true);
		done.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OmnitrixItems.RECALIBRATION_CHIP));
		helper.assertTrue(OmnitrixRecal.refusal(done) != null && !OmnitrixRecal.request(done), "already recalibrated");
		// no watch at all
		ServerPlayer none = mock(helper, 7.5);
		helper.assertTrue(OmnitrixRecal.refusal(none) != null, "no watch");
		// broken in: allowed
		ServerPlayer ready = wearer(helper, 9.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		helper.assertTrue(OmnitrixRecal.refusal(ready) == null, "25: the screen may open");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void yesStartsTheInsertAndTheChipGoesInOnItsBeat(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 1.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		helper.assertTrue(OmnitrixRecal.request(p), "Yes starts it");
		helper.assertTrue(OmnitrixRecal.busy(p) && Omnitrix.state(p).timers.containsKey(OmnitrixRecal.INSERT), "the insert is playing");
		helper.assertFalse(Omnitrix.activate(p), "the watch cannot be raised meanwhile");
		helper.onEachTick(() -> {
			Omnitrix.tick(p);
			Omnitrix.tick(p); // a mock can tick twice: every beat must still fire once
		});
		helper.runAfterDelay(OmnitrixRecal.CHIP_IN - 6, () -> {
			helper.assertTrue(p.getMainHandItem().getCount() == 1, "the chip is still in the hand before the insert beat");
			helper.assertFalse(OmnitrixRecal.chipIn(Omnitrix.state(p)), "not in yet");
		});
		helper.runAfterDelay(OmnitrixRecal.CHIP_IN + 4, () -> {
			helper.assertTrue(p.getMainHandItem().isEmpty(), "the chip went into the slot");
			helper.assertTrue(OmnitrixRecal.chipIn(Omnitrix.state(p)), "the mode's end is fixed");
			long end = Omnitrix.state(p).readyAt(OmnitrixRecal.END);
			long start = end - OmnitrixRecal.INSERT_TOTAL - OmnitrixConfig.RECAL_MODE_TICKS;
			helper.assertTrue(Math.abs(helper.getLevel().getGameTime() - start - (OmnitrixRecal.CHIP_IN + 4)) <= 2,
					"three minutes from the end of the insert");
		});
		helper.runAfterDelay(OmnitrixRecal.INSERT_TOTAL + 6, () -> {
			OmnitrixState s = Omnitrix.state(p);
			helper.assertFalse(s.timers.containsKey(OmnitrixRecal.INSERT), "the insert is over");
			helper.assertTrue(OmnitrixRecal.busy(s) && !Omnitrix.recalibrated(p), "recalibration mode, still the prototype");
			int left = OmnitrixRecal.modeLeft(s, helper.getLevel().getGameTime());
			helper.assertTrue(left > OmnitrixConfig.RECAL_MODE_TICKS - 20 && left <= OmnitrixConfig.RECAL_MODE_TICKS, "about 3:00 left, got " + left);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aChipTakenAwayBeforeTheBeatCallsItOff(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 1.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		helper.assertTrue(OmnitrixRecal.request(p), "started");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		OmnitrixRecal.skip(p, OmnitrixRecal.CHIP_IN);
		Omnitrix.tick(p);
		helper.assertFalse(OmnitrixRecal.busy(p), "no chip at the slot: nothing happens");
		helper.assertTrue(Omnitrix.activate(p), "the watch works again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theThreeMinutesSurviveADeathAndFinishIntoTheRecalibratedOmnitrix(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 1.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		helper.assertTrue(OmnitrixRecal.request(p), "started");
		OmnitrixRecal.skip(p, OmnitrixRecal.CHIP_IN);
		Omnitrix.tick(p);
		helper.assertTrue(OmnitrixRecal.chipIn(Omnitrix.state(p)), "the chip is in");
		long end = Omnitrix.state(p).readyAt(OmnitrixRecal.END);
		// he dies mid-insert: the sequence on screen stops, the recalibration does not
		helper.assertTrue(OmnitrixAttachments.STATE.copyOnDeath(), "the state is copied to the respawned player");
		Omnitrix.onDeath(p);
		OmnitrixState kept = Omnitrix.state(p);
		helper.assertFalse(kept.timers.containsKey(OmnitrixRecal.INSERT), "the insert's animation stopped");
		helper.assertTrue(OmnitrixRecal.busy(kept) && kept.readyAt(OmnitrixRecal.END) == end, "the end time is unchanged");
		helper.assertFalse(Omnitrix.failsafe(p, helper.getLevel().damageSources().generic()), "no failsafe while recalibrating");
		// the time runs out while he is dead: nothing happens to a dead body
		OmnitrixRecal.skipMode(p);
		p.setHealth(0f);
		Omnitrix.tick(p);
		helper.assertFalse(Omnitrix.state(p).timers.containsKey(OmnitrixRecal.FINISH), "no finish on a dead body");
		// the respawned player (copyOnDeath): alive again -> the finish plays, then the Recalibrated Omnitrix
		ServerPlayer q = mock(helper, 3.5);
		q.setAttached(OmnitrixAttachments.STATE, Omnitrix.state(p).copy());
		helper.assertTrue(OmnitrixRecal.busy(q), "still recalibrating after the respawn");
		Omnitrix.tick(q);
		Omnitrix.tick(q);
		helper.assertTrue(Omnitrix.state(q).timers.containsKey(OmnitrixRecal.FINISH), "alive and the time is up: the finish starts");
		helper.assertFalse(Omnitrix.recalibrated(q), "not until it has reformed");
		OmnitrixRecal.skip(q, OmnitrixRecal.FINISH_TOTAL);
		Omnitrix.tick(q);
		helper.assertTrue(Omnitrix.recalibrated(q), "the Recalibrated Omnitrix");
		helper.assertFalse(OmnitrixRecal.busy(q), "recalibration mode is over");
		helper.assertTrue(Aliens.byId(Omnitrix.state(q).selected).recalibrated(), "the dial is on an Alien Force alien");
		helper.assertTrue(Omnitrix.activate(q), "and the watch works again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theWatchIsRefusedWhileItRecalibrates(GameTestHelper helper) {
		ServerPlayer p = wearer(helper, 1.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		helper.assertTrue(OmnitrixRecal.request(p), "started");
		OmnitrixRecal.skip(p, OmnitrixRecal.INSERT_TOTAL);
		Omnitrix.tick(p);
		OmnitrixState s = Omnitrix.state(p);
		helper.assertTrue(OmnitrixRecal.busy(s) && !s.timers.containsKey(OmnitrixRecal.INSERT), "in recalibration mode");
		// v0.17.2 (user: "while the omnitrix is recalibrating make it so that the player can still lift the omnitrix by pressing R"):
		// it comes up to be looked at, but its dial never pops
		helper.assertTrue(Omnitrix.activate(p), "R raises it to look at");
		helper.assertTrue(Omnitrix.popAt(Omnitrix.state(p)) > p.level().getGameTime() + 100000L, "the dial stays down");
		Omnitrix.deactivate(p);
		Omnitrix.select(p, Aliens.HEATBLAST.id());
		helper.assertFalse(Omnitrix.transform(p), "no transforming either");
		helper.assertFalse(Omnitrix.transformed(p), "still human");
		ServerPlayer q = wearer(helper, 3.5, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		helper.assertTrue(Omnitrix.activate(q), "a watch that is not recalibrating still raises");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theVideosFourSoundsAreRegistered(GameTestHelper helper) {
		for (var e : OmnitrixSounds.RECAL_CHIP) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(e.getLocation()), e.getLocation() + " registered");
		}
		helper.assertTrue(OmnitrixSounds.RECAL_CHIP.length == 4, "click, press, pulse, reform");
		helper.succeed();
	}
}
