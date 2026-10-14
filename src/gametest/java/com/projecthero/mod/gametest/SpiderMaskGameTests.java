package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.SpiderMask;
import com.projecthero.mod.spider.item.SpiderItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** v0.16.2: the Spider-Man mask's take-off / put-on animation -- synced state, timing, ignored presses, the peel curve. */
public class SpiderMaskGameTests implements FabricGameTest {

	private static ServerPlayer masked(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(SpiderItems.SUIT_HELMET));
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void maskTogglePlaysOneAnimationAndIgnoresPressesDuringIt(GameTestHelper helper) {
		ServerPlayer p = masked(helper);
		helper.assertTrue(!SpiderMask.isOpen(p) && !SpiderMask.animating(p), "starts on, still");
		long t0 = p.level().getGameTime();
		SpiderMask.toggle(p);
		helper.assertTrue(SpiderMask.isOpen(p), "H: coming off");
		helper.assertTrue(SpiderMask.animStart(p) == t0, "the start tick is recorded (synced to every viewer)");
		helper.assertTrue(SpiderMask.animating(p), "and the animation is playing");
		helper.assertTrue(SpiderMask.coverage(p, t0) == 1f, "the mask is still fully on as his hand reaches up");
		helper.runAfterDelay(5, () -> {
			SpiderMask.toggle(p);
			helper.assertTrue(SpiderMask.isOpen(p) && SpiderMask.animStart(p) == t0, "a press mid-animation is ignored");
		});
		helper.runAfterDelay(SpiderMask.OFF_TICKS + 1, () -> {
			helper.assertTrue(!SpiderMask.animating(p), "about a second later it is done");
			helper.assertTrue(SpiderMask.coverage(p, p.level().getGameTime()) == 0f, "and the mask is off");
			long t1 = p.level().getGameTime();
			SpiderMask.toggle(p);
			helper.assertTrue(!SpiderMask.isOpen(p) && SpiderMask.animStart(p) == t1, "the next press puts it back on");
			helper.assertTrue(SpiderMask.coverage(p, t1) == 0f, "starting from off");
			helper.runAfterDelay(SpiderMask.ON_TICKS + 1, () -> {
				helper.assertTrue(SpiderMask.coverage(p, p.level().getGameTime()) == 1f, "on again");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void maskPeelsUpOffAndPullsDownOnInStep(GameTestHelper helper) {
		// taking it off: full until the hand has hold, then strictly less and less, gone at the end of the peel
		helper.assertTrue(SpiderMask.coverage(true, 0f) == 1f && SpiderMask.coverage(true, SpiderMask.OFF_PEEL_START) == 1f,
				"untouched while he reaches up");
		float prev = 1f;
		for (int i = 1; i <= 10; i++) {
			float p = SpiderMask.OFF_PEEL_START + (SpiderMask.OFF_PEEL_END - SpiderMask.OFF_PEEL_START) * i / 10f;
			float c = SpiderMask.coverage(true, p);
			helper.assertTrue(c < prev, "peels steadily (" + c + " at " + p + ")");
			prev = c;
		}
		helper.assertTrue(SpiderMask.coverage(true, SpiderMask.OFF_PEEL_END) == 0f && SpiderMask.coverage(true, 1f) == 0f,
				"off over the top, then in his hand");
		// putting it on: nothing on the head while it is lifted, then more and more, all of it after the pull
		helper.assertTrue(SpiderMask.coverage(false, SpiderMask.ON_PULL_START) == 0f, "lifted over the head first");
		float half = SpiderMask.coverage(false, (SpiderMask.ON_PULL_START + SpiderMask.ON_PULL_END) * 0.5f);
		helper.assertTrue(half > 0.3f && half < 0.7f, "half way down half way through the pull (" + half + ")");
		helper.assertTrue(SpiderMask.coverage(false, SpiderMask.ON_PULL_END) == 1f, "all the way on");
		helper.assertTrue(SpiderMask.OFF_TICKS >= 16 && SpiderMask.OFF_TICKS <= 20 && SpiderMask.ON_TICKS >= 16
				&& SpiderMask.ON_TICKS <= 20, "each about 0.8-1.0 s");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takingTheHoodOffMidAnimationResetsTheMask(GameTestHelper helper) {
		ServerPlayer p = masked(helper);
		SpiderMask.toggle(p);
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		SpiderMask.reconcile(p);
		helper.assertTrue(!SpiderMask.isOpen(p), "no hood: no mask off");
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.SPIDER_MAN_MASK_ANIM_START, 0L) == SpiderMask.NO_ANIM,
				"and no animation left over for when it goes back on");
		SpiderMask.toggle(p);
		helper.assertTrue(!SpiderMask.isOpen(p), "and H does nothing without the hood");
		helper.succeed();
	}
}
