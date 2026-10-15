package com.projecthero.mod.gametest;

import com.projecthero.mod.spider.SpiderStruggle;
import com.projecthero.mod.spider.SpiderWrap;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): a cocooned player struggles free by mashing A / D alternately. Fast clean alternation breaks out in a few
 * seconds; slow presses, same-key spam and a macro burst never do; the bar drains when they stop.
 */
public class SpiderStruggleV0164GameTests implements FabricGameTest {

	/** A survival mock player in a wall-pinned cocoon (permanent, and no web is needed to hold it). */
	private static ServerPlayer cocooned(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setNoGravity(true);
		SpiderWrap.wrapOnWall(p);
		return p;
	}

	/** Alternate presses on the given ticks (the first press is key LEFT). */
	private static void schedule(GameTestHelper helper, ServerPlayer p, int every, int count, boolean alternate) {
		for (int i = 0; i < count; i++) {
			int key = alternate ? (i % 2 == 0 ? SpiderStruggle.LEFT : SpiderStruggle.RIGHT) : SpiderStruggle.LEFT;
			helper.runAfterDelay(1 + (long) i * every, () -> SpiderStruggle.press(p, key));
		}
	}

	private static void cleanUp(ServerPlayer p) {
		SpiderWrap.release(p, false);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void fastAlternationBreaksOutInAFewSeconds(GameTestHelper helper) {
		ServerPlayer p = cocooned(helper);
		schedule(helper, p, 2, 70, true); // 10 presses a second, A D A D ...
		helper.runAfterDelay(40, () -> helper.assertTrue(SpiderWrap.isWrapped(p),
				"not too easy: still cocooned after 2 s of mashing (progress " + SpiderStruggle.progress(p) + ")"));
		helper.runAfterDelay(121, () -> {
			helper.assertFalse(SpiderWrap.isWrapped(p), "10 alternate presses a second break out within 6 s");
			cleanUp(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 340)
	public void slowPressesNeverBreakOut(GameTestHelper helper) {
		ServerPlayer p = cocooned(helper);
		schedule(helper, p, 5, 64, true); // 4 a second for 16 s
		helper.runAfterDelay(321, () -> {
			helper.assertTrue(SpiderWrap.isWrapped(p), "4 presses a second never break out");
			helper.assertTrue(SpiderStruggle.progress(p) < 0.7f, "and the bar stays well short (" + SpiderStruggle.progress(p) + ")");
			cleanUp(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void sameKeySpamNeverBreaksOut(GameTestHelper helper) {
		ServerPlayer p = cocooned(helper);
		schedule(helper, p, 1, 200, false); // A A A A ... 20 a second
		helper.runAfterDelay(202, () -> {
			helper.assertTrue(SpiderWrap.isWrapped(p), "mashing one key never breaks out");
			helper.assertTrue(SpiderStruggle.progress(p) < 0.1f, "it fills nothing (" + SpiderStruggle.progress(p) + ")");
			cleanUp(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aMacroBurstIsRateLimited(GameTestHelper helper) {
		ServerPlayer p = cocooned(helper);
		helper.runAfterDelay(1, () -> {
			for (int i = 0; i < 60; i++) {
				SpiderStruggle.press(p, i % 2);
			}
			helper.assertTrue(SpiderWrap.isWrapped(p), "60 presses in one tick do not break out");
			helper.assertTrue(SpiderStruggle.progress(p) <= 0.16f, "only a short burst counts (" + SpiderStruggle.progress(p) + ")");
			cleanUp(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void theBarDrainsWhenIdle(GameTestHelper helper) {
		ServerPlayer p = cocooned(helper);
		schedule(helper, p, 2, 20, true); // 2 s at 10 a second, then nothing
		float[] peak = new float[1];
		helper.runAfterDelay(40, () -> {
			peak[0] = SpiderStruggle.progress(p);
			helper.assertTrue(peak[0] > 0.3f, "2 s of mashing fills a good part of the bar (" + peak[0] + ")");
		});
		helper.runAfterDelay(65, () -> helper.assertTrue(SpiderStruggle.progress(p) < peak[0] * 0.5f,
				"a second idle drains it fast (" + SpiderStruggle.progress(p) + " from " + peak[0] + ")"));
		helper.runAfterDelay(110, () -> {
			helper.assertTrue(SpiderStruggle.progress(p) == 0.0f, "and it empties (" + SpiderStruggle.progress(p) + ")");
			helper.assertTrue(SpiderWrap.isWrapped(p), "still cocooned");
			cleanUp(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void pressesDoNothingOutsideACocoon(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		for (int i = 0; i < 6; i++) {
			SpiderStruggle.press(p, i % 2);
		}
		helper.assertTrue(SpiderStruggle.progress(p) == 0.0f, "no cocoon, no struggle");
		helper.succeed();
	}
}
