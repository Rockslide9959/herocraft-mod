package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixLongTransform;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.sequence.ShowSequence;
import com.projecthero.mod.omnitrix.sequence.ShowSequences;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "make this long transformation sequence play when the player presses right click on the dial instead of left
 * click"): the right-click starts the long Four Arms transformation -- rooted, keys ignored, the body changing on its beat, the clock
 * starting at its end, sneak skipping it -- while the left-click (and any other alien) stays the quick slap. Mock players are not
 * ticked by the server and game time does not move inside a test's tick, so the sequence clock is wound on by hand.
 */
public class OmnitrixLongTransformGameTests implements FabricGameTest {
	/** v0.17.3: Four Arms' sequence, on the per-alien framework. */
	private static ShowSequence fourArms() {
		return ShowSequences.get(Aliens.FOUR_ARMS.id());
	}

	private static ServerPlayer raised(GameTestHelper helper, String alien) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setOnGround(true);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Omnitrix.noGlitch(p);
		helper.assertTrue(Omnitrix.activate(p), "the watch comes up");
		Omnitrix.finishRaise(p);
		helper.assertTrue(Omnitrix.select(p, alien), "the dial turns to " + alien);
		return p;
	}

	/** Winds the sequence's clock {@code ticks} further on and runs a watch tick. */
	private static void wind(ServerPlayer p, long ticks) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(OmnitrixLongTransform.TIMER, n.timers.get(OmnitrixLongTransform.TIMER) - ticks);
		Omnitrix.save(p, n);
		Omnitrix.tick(p);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rightClickPlaysTheLongFourArmsTransformation(GameTestHelper helper) {
		ServerPlayer p = raised(helper, Aliens.FOUR_ARMS.id());
		helper.assertTrue(Omnitrix.slap(p, true), "the right-click slaps the dial");
		helper.assertTrue(OmnitrixLongTransform.playing(p), "and starts the long transformation");
		helper.assertTrue(OmnitrixLongTransform.rooted(p), "rooted to the spot");
		helper.assertFalse(Omnitrix.state(p).transformed(), "still human at the slap");
		// a key mid-sequence does nothing (R would have lowered the watch)
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_1.number(), true);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_1.number(), false);
		helper.assertTrue(Omnitrix.state(p).selecting, "keys are ignored while it plays");
		wind(p, fourArms().transformAt() - 10);
		helper.assertFalse(Omnitrix.state(p).transformed(), "still human through the swelling");
		wind(p, 11);
		OmnitrixState s = Omnitrix.state(p);
		helper.assertTrue(s.transformed() && Aliens.FOUR_ARMS.id().equals(s.active), "Four Arms under the golden flash");
		helper.assertTrue(OmnitrixLongTransform.playing(p), "the sequence plays on after the change");
		long left = s.endsAt - helper.getLevel().getGameTime();
		helper.assertTrue(left > OmnitrixConfig.TRANSFORM_TICKS, "the 3 minutes start at the end of the sequence (" + left + ")");
		wind(p, fourArms().total());
		helper.assertFalse(OmnitrixLongTransform.playing(p), "over");
		helper.assertFalse(OmnitrixLongTransform.rooted(p), "free to move");
		helper.assertTrue(Omnitrix.state(p).transformed(), "and still Four Arms");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void sneakingSkipsTheLongTransformation(GameTestHelper helper) {
		ServerPlayer p = raised(helper, Aliens.FOUR_ARMS.id());
		helper.assertTrue(Omnitrix.slap(p, true), "the right-click starts it");
		wind(p, OmnitrixLongTransform.SKIP_FROM + 5);
		p.setShiftKeyDown(true);
		Omnitrix.tick(p);
		helper.assertFalse(OmnitrixLongTransform.playing(p), "sneaking skips it");
		helper.assertFalse(OmnitrixLongTransform.rooted(p), "no longer rooted");
		helper.assertTrue(Omnitrix.state(p).transformed(), "straight into Four Arms");
		p.setShiftKeyDown(false);
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void leftClickAndOtherAliensStayQuick(GameTestHelper helper) {
		ServerPlayer p = raised(helper, Aliens.FOUR_ARMS.id());
		helper.assertTrue(Omnitrix.slap(p, false), "the left-click slaps");
		helper.assertFalse(OmnitrixLongTransform.playing(p), "the left-click is the quick transformation");
		// v0.17.3: an alien with no show sequence gets the quick one on a right-click too (the first on the dial without one, if any)
		String quick = null;
		for (var a : Aliens.playable(p)) {
			if (!OmnitrixLongTransform.has(a.id())) {
				quick = a.id();
				break;
			}
		}
		if (quick != null) {
			ServerPlayer q = raised(helper, quick);
			helper.assertTrue(Omnitrix.slap(q, true), "a right-click on " + quick + " slaps");
			helper.assertFalse(OmnitrixLongTransform.playing(q), quick + " has no long transformation: the quick one");
		}
		helper.assertFalse(OmnitrixLongTransform.has(Aliens.DITTO.id()), "Ditto has no show sequence");
		helper.succeed();
	}

	/**
	 * v0.17.3: every show sequence registered on the framework is well formed -- its alien exists, the change comes after the skip
	 * point and before the end, and its soundtrack is registered (the per-alien agents' safety net).
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void everyShowSequenceIsWellFormed(GameTestHelper helper) {
		helper.assertTrue(ShowSequences.has(Aliens.FOUR_ARMS.id()), "Four Arms' sequence is registered");
		for (ShowSequence s : ShowSequences.all()) {
			String id = s.alienId();
			helper.assertTrue(Aliens.byId(id) != null, id + ": a declared alien");
			helper.assertTrue(s.skipFrom() >= 0 && s.skipFrom() < s.transformAt(), id + ": skippable before the change");
			helper.assertTrue(s.transformAt() < s.total(), id + ": the change comes before the end");
			helper.assertTrue(s.total() > 20 && s.total() <= 30 * 20, id + ": between 1 and 30 seconds (" + s.total() + ")");
			helper.assertTrue(s.sound() != null && s.cue() != null, id + ": a soundtrack (OmnitrixSounds.SEQUENCE_ALIENS)");
			helper.assertTrue(OmnitrixSounds.SEQUENCE_ALIENS.contains(id) || s.sound() != null, id + ": sound registered");
		}
		helper.succeed();
	}
}
