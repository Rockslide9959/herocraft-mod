package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.blackpanther.plane.AncestralPlane;
import com.projecthero.mod.blackpanther.plane.BastBlessing;
import com.projecthero.mod.blackpanther.plane.ParkourCourse;
import com.projecthero.mod.blackpanther.plane.PantherShadeEntity;
import com.projecthero.mod.blackpanther.plane.PlaneGeometry;
import com.projecthero.mod.blackpanther.plane.PlaneScene;
import com.projecthero.mod.blackpanther.plane.ShadeTrial;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther Ancestral Plane: the soup gives Bast's Blessing for 60 s and no power; blessed, a bed lets you sleep by
 * day; falling asleep blessed opens the plane (the blessing is spent, the bed remembered); the whole dream stage by stage --
 * walking up to the ancestors, the greeting, the procession, touching the shrine, the trial, the spirit path, the finale --
 * ends in bed with the power; waking early (or logging out) grants nothing and sends them back; the trial's and the course's
 * rules as pure functions, and their live checkpoint reset and shade strike.
 *
 * <p>The GameTest server never loads datapack dimensions, so the plane is built in its overworld fallback ({@code PlaneRealm},
 * high in the sky far away). Plane tests share slots and statics, so they run in one batch.
 */
public class BlackPantherPlaneV0177GameTests implements FabricGameTest {
	private static final String BATCH = "bp_plane_v0177";

	private static ServerPlayer player(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static void leave(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			AncestralPlane.onDisconnect(p);
			p.removeAttached(AncestralPlane.RETURN);
			if (BlackPanther.hasPower(p)) {
				BlackPanther.revoke(p);
			}
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	/** Runs steps one after another, each once its condition holds; succeeds after the last. */
	private static final class Script {
		private final GameTestHelper helper;
		private final List<BooleanSupplier> when = new ArrayList<>();
		private final List<Runnable> then = new ArrayList<>();
		private int at;

		Script(GameTestHelper helper) {
			this.helper = helper;
		}

		Script step(BooleanSupplier condition, Runnable action) {
			when.add(condition);
			then.add(action);
			return this;
		}

		void run(Runnable cleanup) {
			helper.onEachTick(() -> {
				if (at >= when.size()) {
					return;
				}
				if (when.get(at).getAsBoolean()) {
					try {
						then.get(at).run();
					} catch (RuntimeException e) {
						cleanup.run();
						throw e;
					}
					at++;
					if (at >= when.size()) {
						cleanup.run();
						helper.succeed();
					}
				}
			});
		}
	}

	// ---------------------------------------------------------------- the soup and the blessing

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void soupGivesBastsBlessingForAMinuteAndNoPower(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		try {
			ItemStack soup = new ItemStack(BlackPantherItems.SOUP);
			p.setItemInHand(InteractionHand.MAIN_HAND, soup);
			helper.assertTrue(BlackPanther.canDrink(p), "a fresh player can drink");
			soup.finishUsingItem(p.level(), p);
			MobEffectInstance e = p.getEffect(BastBlessing.EFFECT);
			helper.assertTrue(e != null, "Bast's Blessing");
			helper.assertTrue(Math.abs(e.getDuration() - 1200) <= 2, "for 60 s: " + e.getDuration());
			helper.assertTrue(e.isAmbient(), "soft (ambient) particles");
			helper.assertFalse(BlackPanther.hasPower(p), "no power yet");
			helper.assertFalse(BlackPanther.inVision(p), "no 10 s vision any more");
			helper.assertFalse(AncestralPlane.inPlane(p), "awake: no plane yet");
			helper.assertFalse(BlackPanther.canDrink(p), "a second soup is refused while blessed");
			helper.succeed();
		} finally {
			leave(helper, p);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void blessedPlayersMaySleepByDay(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		try {
			BlockPos bed = helper.absolutePos(new BlockPos(3, 2, 3));
			InteractionResult before = EntitySleepEvents.ALLOW_SLEEP_TIME.invoker().allowSleepTime(p, bed, false);
			helper.assertTrue(before != InteractionResult.SUCCESS, "unblessed: the day refuses as usual");
			BastBlessing.give(p);
			InteractionResult after = EntitySleepEvents.ALLOW_SLEEP_TIME.invoker().allowSleepTime(p, bed, false);
			helper.assertTrue(after == InteractionResult.SUCCESS, "blessed: the bed lets you sleep by day");
			helper.succeed();
		} finally {
			leave(helper, p);
		}
	}

	// ---------------------------------------------------------------- falling asleep

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void fallingAsleepBlessedOpensThePlaneAndWakingEarlyGrantsNothing(GameTestHelper helper) {
		// a real bed on the floor of the test box
		BlockPos foot = new BlockPos(3, 1, 3), head = foot.relative(Direction.NORTH);
		BlockState footState = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
		helper.setBlock(foot, footState);
		helper.setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD));
		ServerPlayer p = player(helper, new Vec3(3.5, 2, 4.5));
		BlockPos bedAbs = helper.absolutePos(head);
		helper.assertFalse(AncestralPlane.fellAsleep(p), "no blessing: no plane");
		BastBlessing.give(p);
		p.startSleeping(bedAbs);
		Vec3 inBed = p.position();
		helper.assertTrue(AncestralPlane.fellAsleep(p), "asleep and blessed: the plane opens");
		new Script(helper)
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.ARRIVAL, () -> {
					helper.assertFalse(BastBlessing.has(p), "the blessing is spent");
					var back = p.getAttachedOrElse(AncestralPlane.RETURN, null);
					helper.assertTrue(back != null && back.bed().isPresent() && back.bed().get().equals(bedAbs), "the bed is remembered");
					helper.assertTrue(Math.abs(back.x() - inBed.x) < 1.0e-6 && Math.abs(back.z() - inBed.z) < 1.0e-6, "and where they lay");
					BlockPos o = AncestralPlane.origin(p);
					helper.assertTrue(p.position().distanceTo(PlaneGeometry.spawn(o)) < 0.8, "on the savanna at the spawn: " + p.position());
					double toEldest = PlaneGeometry.flat(p.position(), PlaneGeometry.ancestor(o, 0));
					helper.assertTrue(toEldest >= 60 && toEldest <= 80, "a long walk from the ancestors: " + toEldest);
					helper.assertFalse(p.level().getBlockState(BlockPos.containing(p.position()).below()).isAir(), "standing on ground");
				})
				// hold Sneak for 5 s in a free stage: wakes early, back in bed, no power
				.step(() -> true, () -> AncestralPlane.fastForward(p, PlaneScene.ARRIVAL.length))
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.WANDER, () -> p.setShiftKeyDown(true))
				.step(() -> !AncestralPlane.inPlane(p), () -> {
					p.setShiftKeyDown(false);
					helper.assertFalse(BlackPanther.hasPower(p), "woke early: no power");
					helper.assertTrue(p.getAttachedOrElse(AncestralPlane.RETURN, null) == null, "the return point is used up");
					helper.assertTrue(p.position().distanceTo(inBed) < 1.5, "back at the bed: " + p.position() + " vs " + inBed);
					helper.assertTrue(p.isSleeping(), "lying in bed a moment");
				})
				.step(() -> !p.isSleeping(), () -> helper.assertFalse(BlackPanther.hasPower(p), "awake, still no power"))
				.run(() -> leave(helper, p));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void leavingEarlyReturnsThePlayerWithNoPower(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		Vec3 start = p.position();
		try {
			helper.assertTrue(AncestralPlane.beginHere(p), "into the plane");
			helper.assertTrue(p.position().distanceTo(start) > 1000, "far away in the plane");
			helper.assertTrue(AncestralPlane.leave(p), "leave");
			helper.assertFalse(AncestralPlane.inPlane(p), "out of it");
			helper.assertTrue(p.position().distanceTo(start) < 0.01, "back where they were: " + p.position());
			helper.assertFalse(BlackPanther.hasPower(p), "no power");
			helper.assertTrue(p.getAttachedOrElse(AncestralPlane.RETURN, null) == null, "nothing left saved");
			helper.succeed();
		} finally {
			leave(helper, p);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void loggingOutEndsTheDreamAndTheNextCheckSendsThemBack(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		Vec3 start = p.position();
		try {
			helper.assertTrue(AncestralPlane.jumpTo(p, AncestralPlane.Stage.TRIAL), "a dream, mid-trial");
			helper.assertTrue(AncestralPlane.inPlane(p), "dreaming");
			AncestralPlane.onDisconnect(p);
			helper.assertFalse(AncestralPlane.inPlane(p), "the dream ends on logout");
			helper.assertTrue(p.getAttachedOrElse(AncestralPlane.RETURN, null) != null, "the return point stays for the next login");
			AncestralPlane.check(p);
			helper.assertTrue(p.position().distanceTo(start) < 0.01, "the check sends them back: " + p.position());
			helper.assertFalse(BlackPanther.hasPower(p), "with no power");
			helper.assertTrue(p.getAttachedOrElse(AncestralPlane.RETURN, null) == null, "and nothing saved");
			helper.succeed();
		} finally {
			leave(helper, p);
		}
	}

	// ---------------------------------------------------------------- the whole dream

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 1600)
	public void theWholeDreamStageByStageEndsInBedWithThePower(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		Vec3 start = p.position();
		helper.assertTrue(AncestralPlane.beginHere(p), "into the plane");
		new Script(helper)
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.ARRIVAL, () -> AncestralPlane.fastForward(p, PlaneScene.ARRIVAL.length))
				// walk up to the ancestors (once they stand)
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.WANDER && AncestralPlane.eldest(p) != null, () -> {
					Vec3 near = PlaneGeometry.ancestor(AncestralPlane.origin(p), 0).add(-6, 0, 0);
					p.teleportTo(near.x, near.y, near.z);
				})
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.GREETING, () -> {
					helper.assertTrue(p.position().distanceTo(PlaneGeometry.meetSpot(AncestralPlane.origin(p))) < 0.6, "placed before the eldest");
					AncestralPlane.fastForward(p, PlaneScene.GREETING.length);
				})
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.PROCESSION, () -> AncestralPlane.fastForward(p, PlaneScene.PROCESSION.length))
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.SHRINE && AncestralPlane.eldest(p) != null, () -> {
					BlockPos o = AncestralPlane.origin(p);
					helper.assertTrue(AncestralPlane.eldest(p).position().distanceTo(PlaneGeometry.ancestorAtShrine(o, 0)) < 0.6, "they led the way to the shrine");
					int stone = 0;
					for (int y = 4; y < 18; y++) {
						BlockPos q = o.offset(PlaneGeometry.SHRINE_X + PlaneGeometry.PEDESTAL_DX - 1, y, 0);
						stone += p.level().getBlockState(q).is(Blocks.POLISHED_BLACKSTONE) || p.level().getBlockState(q).is(Blocks.BLACK_CONCRETE) ? 1 : 0;
					}
					helper.assertTrue(stone >= 6, "the statue stands: " + stone);
					helper.assertTrue(AncestralPlane.touchShrineForTest(p), "a hand on the shrine");
				})
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.TRIAL_INTRO, () -> AncestralPlane.fastForward(p, PlaneScene.TRIAL_INTRO.length))
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.TRIAL, () -> {
					BlockPos o = AncestralPlane.origin(p);
					helper.assertTrue(p.level().getBlockState(com.projecthero.mod.blackpanther.plane.PlaneRealm.eyePos(o, 1)).is(Blocks.CRYING_OBSIDIAN),
							"the statue's eyes are awake");
					AncestralPlane.setTrialRound(p, ShadeTrial.ROUNDS); // the shades are beaten
				})
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.PATH, () -> AncestralPlane.fastForward(p, PlaneScene.PATH.length))
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.PARKOUR, () -> {
					BlockPos o = AncestralPlane.origin(p);
					helper.assertTrue(AncestralPlane.checkpoint(p) == 0, "on the first checkpoint");
					ParkourCourse.Platform f = ParkourCourse.finish();
					helper.assertFalse(p.level().getBlockState(o.offset(PlaneGeometry.PATH_X + f.x0(), PlaneGeometry.PATH_BASE + f.y(), f.z0())).isAir(),
							"the whole path stands");
					Vec3 top = PlaneGeometry.at(o, PlaneGeometry.PATH_X + f.centreX(), PlaneGeometry.PATH_BASE + f.y() + 1, f.centreZ());
					p.teleportTo(top.x, top.y, top.z);
				})
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.FINALE, () -> AncestralPlane.fastForward(p, PlaneScene.FINALE_HOME))
				.step(() -> !AncestralPlane.inPlane(p), () -> {
					helper.assertTrue(BlackPanther.hasPower(p), "woke as the Black Panther");
					helper.assertTrue(p.position().distanceTo(start) < 0.01, "back where they fell asleep: " + p.position());
					helper.assertTrue(p.getAttachedOrElse(AncestralPlane.RETURN, null) == null, "nothing left saved");
					int seen = p.getAttachedOrElse(AncestralPlane.SEEN, 0);
					helper.assertTrue((seen & PlaneScene.FINALE.bit()) != 0 && (seen & PlaneScene.GREETING.bit()) != 0, "the scenes are remembered as seen");
				})
				.run(() -> leave(helper, p));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
	public void theFinaleGrantsThePowerExactlyOnce(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		helper.assertTrue(AncestralPlane.jumpTo(p, AncestralPlane.Stage.FINALE), "straight to the end of the path");
		new Script(helper)
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.FINALE, () -> {
					helper.assertFalse(BlackPanther.hasPower(p), "not before waking");
					AncestralPlane.fastForward(p, PlaneScene.FINALE_HOME);
				})
				.step(() -> !AncestralPlane.inPlane(p), () -> {
					helper.assertTrue(BlackPanther.hasPower(p), "the power on waking");
					helper.assertFalse(BlackPanther.canDrink(p), "a soup afterwards is refused");
				})
				.run(() -> leave(helper, p));
	}

	// ---------------------------------------------------------------- the trial

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void shadeTrialRules(GameTestHelper helper) {
		ShadeTrial t = new ShadeTrial();
		helper.assertTrue(ShadeTrial.shadesIn(0) == 4 && ShadeTrial.shadesIn(1) == 6 && ShadeTrial.shadesIn(2) == 8, "4, 6, 8 shades");
		helper.assertTrue(ShadeTrial.concurrent(0) == 1 && ShadeTrial.concurrent(2) == 2, "one at a time, then two");
		helper.assertTrue(ShadeTrial.windup(0) >= 16, "a readable tell (at least 0.8 s): " + ShadeTrial.windup(0));
		helper.assertTrue(t.canSpawn(0) && !t.canSpawn(1), "round 1: one on the floor at a time");
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(t.kill() == ShadeTrial.Outcome.NONE, "kill " + (i + 1));
		}
		helper.assertTrue(t.wound() == ShadeTrial.Outcome.NONE && t.wounds() == 1, "a wound");
		helper.assertTrue(t.kill() == ShadeTrial.Outcome.ROUND_WON && t.round() == 1, "the 4th kill wins round 1");
		helper.assertTrue(t.wounds() == 0 && t.killed() == 0, "wounds reset each round");
		helper.assertTrue(t.wound() == ShadeTrial.Outcome.NONE && t.wound() == ShadeTrial.Outcome.NONE, "two wounds");
		t.kill();
		helper.assertTrue(t.wound() == ShadeTrial.Outcome.ROUND_LOST, "the third wound loses the round");
		helper.assertTrue(t.round() == 1 && t.killed() == 0 && t.wounds() == 0 && t.spawned() == 0, "round 2 starts over; round 1 stays won");
		for (int i = 0; i < 6; i++) {
			t.kill();
		}
		helper.assertTrue(t.round() == 2, "round 2 won");
		helper.assertTrue(t.canSpawn(1) && !t.canSpawn(2), "round 3: two at a time");
		ShadeTrial.Outcome last = ShadeTrial.Outcome.NONE;
		for (int i = 0; i < 8; i++) {
			last = t.kill();
		}
		helper.assertTrue(last == ShadeTrial.Outcome.TRIAL_WON && t.won(), "8 more: the trial is passed");
		helper.assertTrue(ShadeTrial.pounceHits(1.0) && !ShadeTrial.pounceHits(2.5), "a pounce hits within reach, misses a sidestep");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 300)
	public void shadesComeAndOneStrikeDissolvesThem(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		helper.assertTrue(AncestralPlane.jumpTo(p, AncestralPlane.Stage.TRIAL), "the trial");
		AncestralPlane.fastForward(p, 40);
		new Script(helper)
				.step(() -> !AncestralPlane.shades(p).isEmpty(), () -> {
					PantherShadeEntity e = AncestralPlane.shades(p).get(0);
					BlockPos o = AncestralPlane.origin(p);
					helper.assertTrue(PlaneGeometry.flat(e.position(), PlaneGeometry.arena(o)) > 6.0, "it appears on the rim");
					helper.assertTrue(AncestralPlane.strikeForTest(p, e), "struck");
					helper.assertTrue(e.isRemoved(), "it dissolves");
					helper.assertTrue(AncestralPlane.trial(p).killed() == 1, "counted");
					helper.assertFalse(p.hurt(p.damageSources().generic(), 5f), "nothing hurts the dreamer");
				})
				.run(() -> leave(helper, p));
	}

	// ---------------------------------------------------------------- the spirit path

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void parkourCourseRules(GameTestHelper helper) {
		helper.assertTrue(ParkourCourse.length() >= 100, "at least 100 blocks long: " + ParkourCourse.length());
		helper.assertTrue(ParkourCourse.checkpointCount() == 5, "five checkpoints");
		for (int i = 1; i < ParkourCourse.checkpointCount(); i++) {
			int gap = ParkourCourse.checkpoint(i).x0() - ParkourCourse.checkpoint(i - 1).x0();
			helper.assertTrue(gap >= 18 && gap <= 25, "a checkpoint every ~20 blocks: " + gap);
		}
		// no jump wider than 2 blocks (edge to edge), no rise over 1
		List<ParkourCourse.Platform> all = ParkourCourse.platforms();
		for (int i = 1; i < all.size(); i++) {
			ParkourCourse.Platform a = all.get(i - 1), b = all.get(i);
			int gx = Math.max(0, b.x0() - (a.x0() + a.sx()));
			int gz = Math.max(0, Math.max(b.z0() - (a.z0() + a.sz()), a.z0() - (b.z0() + b.sz())));
			helper.assertTrue(gx <= 2 && gz <= 2 && Math.hypot(gx, gz) <= 2.9, "jump " + i + " is fair: " + gx + "," + gz);
			helper.assertTrue(b.y() - a.y() <= 1, "rise " + i + " is at most 1");
		}
		ParkourCourse.Platform cp2 = ParkourCourse.checkpoint(2);
		helper.assertTrue(ParkourCourse.reachedCheckpoint(cp2.centreX(), cp2.y() + 1, cp2.centreZ()) == 2, "standing on checkpoint 3 reaches it");
		helper.assertTrue(ParkourCourse.reachedCheckpoint(cp2.centreX(), cp2.y() + 3, cp2.centreZ()) == -1, "jumping over it does not");
		helper.assertTrue(ParkourCourse.advance(3, 1) == 3, "checkpoints never go back");
		helper.assertFalse(ParkourCourse.fell(cp2.y() + 1, -PlaneGeometry.PATH_BASE, 2), "on it: no fall");
		helper.assertTrue(ParkourCourse.fell(cp2.y() + 1 - ParkourCourse.FALL_DROP - 0.1, -PlaneGeometry.PATH_BASE, 2), "far below it: a fall");
		helper.assertTrue(ParkourCourse.fell(-PlaneGeometry.PATH_BASE + 0.2, -PlaneGeometry.PATH_BASE, 0), "down on the grass: a fall");
		double[] r = ParkourCourse.respawn(2);
		helper.assertTrue(cp2.over(r[0], r[2], 0) && r[1] == cp2.y() + 1, "the reset lands on the checkpoint");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
	public void fallingOffThePathPutsYouBackOnTheLastCheckpoint(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		helper.assertTrue(AncestralPlane.jumpTo(p, AncestralPlane.Stage.PARKOUR), "on the path");
		ParkourCourse.Platform cp2 = ParkourCourse.checkpoint(2);
		new Script(helper)
				.step(() -> AncestralPlane.stage(p) == AncestralPlane.Stage.PARKOUR, () -> {
					BlockPos o = AncestralPlane.origin(p);
					Vec3 on = PlaneGeometry.at(o, PlaneGeometry.PATH_X + cp2.centreX(), PlaneGeometry.PATH_BASE + cp2.y() + 1, cp2.centreZ());
					p.teleportTo(on.x, on.y, on.z);
				})
				.step(() -> AncestralPlane.checkpoint(p) == 2, () -> {
					BlockPos o = AncestralPlane.origin(p);
					// down onto the grass under the path
					Vec3 below = PlaneGeometry.at(o, PlaneGeometry.PATH_X + cp2.centreX() + 6, 0.05, cp2.centreZ() + 6);
					p.teleportTo(below.x, below.y, below.z);
				})
				.step(() -> p.getY() > AncestralPlane.origin(p).getY() + PlaneGeometry.PATH_BASE + cp2.y(), () -> {
					BlockPos o = AncestralPlane.origin(p);
					Vec3 on = PlaneGeometry.at(o, PlaneGeometry.PATH_X + cp2.centreX(), PlaneGeometry.PATH_BASE + cp2.y() + 1, cp2.centreZ());
					helper.assertTrue(p.position().distanceTo(on) < 0.6, "caught and put back on checkpoint 3: " + p.position() + " vs " + on);
					helper.assertTrue(AncestralPlane.checkpoint(p) == 2, "still checkpoint 3");
					helper.assertTrue(p.getHealth() == p.getMaxHealth(), "no fall damage");
				})
				.run(() -> leave(helper, p));
	}
}
