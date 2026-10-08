package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManAssemblyPlan;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitPoses;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.14.27 Iron Man suit-up rework: every piece builds itself on over exactly 3 s (60 ticks) -- base halves, then the
 * shell one texel at a time -- one after another (boots, leggings, chestplate, helmet); the suit-up only completes
 * (online message, abilities / flight unlocked) once every piece is fully built; one of four body poses is picked per
 * suit-up and synced.
 */
public class IronManV01427SuitUpGameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static ServerPlayer suitedUpReady(GameTestHelper h) {
		return suitedUpReady(h, "mark_iii");
	}

	private static ServerPlayer suitedUpReady(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(suitId, t)));
		}
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachPieceTakesSixtyTicksOneAfterAnother(GameTestHelper h) {
		h.assertTrue(IronManSuitFx.BUILD_TICKS == 60, "a piece builds on over exactly 3 s");
		int plan = 0b1111;
		h.assertTrue(IronManSuitUpManager.buildStageTick(3, plan) == 1, "boots first");
		h.assertTrue(IronManSuitUpManager.buildStageTick(2, plan) == 61, "leggings 60 ticks later");
		h.assertTrue(IronManSuitUpManager.buildStageTick(1, plan) == 121, "chestplate 60 ticks after that");
		h.assertTrue(IronManSuitUpManager.buildStageTick(0, plan) == 181, "helmet last");
		h.assertTrue(IronManSuitUpManager.buildSequenceTicks(4) == 241, "four pieces: the whole suit-up is 4 x 60 (+1) ticks");
		// a partial suit (only helmet + chestplate carried) builds them back to back, no gaps
		h.assertTrue(IronManSuitUpManager.buildStageTick(1, 0b0011) == 1 && IronManSuitUpManager.buildStageTick(0, 0b0011) == 61
				&& IronManSuitUpManager.buildStageTick(3, 0b0011) == -1, "a partial plan has no gaps");
		// the synced clock: an ordinary piece is 0 at its start, half at 30 ticks, whole at 60
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPiece(1, 1000L, true);
		h.assertTrue(fx.lockTicks() == 60, "STYLE_PLATES builds over BUILD_TICKS");
		h.assertTrue(fx.pieceProgress(EquipmentSlot.CHEST, 1000L, 0f) == 0f
				&& Math.abs(fx.pieceProgress(EquipmentSlot.CHEST, 1030L, 0f) - 0.5f) < 1e-4f
				&& fx.pieceProgress(EquipmentSlot.CHEST, 1060L, 0f) == 1f, "piece progress runs over 60 ticks");
		h.assertTrue(fx.building(1, 1059L) && !fx.building(1, 1060L), "building until exactly 60 ticks in");
		// the Mark V case build keeps its quick lock-on
		IronManSuitFx cased = fx.withPose(IronManSuitFx.POSE_CASE_UP, 1000L, 100, IronManSuitFx.STYLE_CASE);
		h.assertTrue(cased.lockTicks() == IronManSuitFx.LOCK_TICKS, "the suitcase path is unchanged");
		// the build timetable: base halves first, then the shell texel by texel, all inside the window
		h.assertTrue(IronManAssemblyPlan.baseTexelTime(1f) == 0f && IronManAssemblyPlan.baseTexelTime(0f) < IronManAssemblyPlan.BUILD_BASE_END,
				"the base halves grow from their far edges and meet on the seam before the shell starts");
		h.assertTrue(IronManAssemblyPlan.shellTexelTime(0, 500) == IronManAssemblyPlan.BUILD_BASE_END
				&& IronManAssemblyPlan.shellTexelTime(499, 500) == IronManAssemblyPlan.BUILD_SHELL_END
				&& IronManAssemblyPlan.shellTexelTime(250, 500) > IronManAssemblyPlan.shellTexelTime(249, 500),
				"shell texels flip on one after another, finishing inside the window");
		h.assertTrue(IronManAssemblyPlan.isBaseCube("base_head") && !IronManAssemblyPlan.isBaseCube("helmet_shell"),
				"base cubes are the geo's base_* cubes");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitUpCompletesOnlyAfterEveryPieceIsBuilt(GameTestHelper h) {
		ServerPlayer p = suitedUpReady(h);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
		int total = IronManSuitUpManager.buildSequenceTicks(4);
		int ticks = 0;
		boolean sawFullSuitStillAssembling = false;
		while (IronManSuitUpManager.inTransition(p) && ticks < 1000) {
			IronManSuitUpManager.tick(p);
			ticks++;
			if (ticks == 2) {
				h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem
						&& p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "the boots go on first, alone");
			}
			if (ticks == 120) {
				h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the chestplate waits for the leggings to finish");
			}
			if (IronManArmor.wearingFullSuit(p, "mark_iii") && IronManSuitUpManager.inTransition(p)) {
				sawFullSuitStillAssembling = true;
				h.assertTrue(IronManSuitUpManager.assembling(p), "while the helmet builds the suit is still assembling");
				h.assertTrue(IronManSuitFx.of(p).faceplateAt() == 0L, "and not online yet");
			}
		}
		h.assertTrue(ticks == total, "the suit-up ran exactly 4 x 60 (+1) ticks, got " + ticks);
		h.assertTrue(sawFullSuitStillAssembling, "the last piece is worn for its whole 3 s build before completion");
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii") && !IronManSuitUpManager.assembling(p),
				"complete: the whole suit is on and the lock is released");
		h.assertTrue(IronManSuitFx.of(p).faceplateAt() > 0L, "completion ends with the faceplate seal");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void abilitiesAndFlightStayLockedWhileAssembling(GameTestHelper h) {
		ServerPlayer p = suitedUpReady(h);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
		for (int i = 0; i < 70; i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.wearingAnyIronMan(p), "the boots are on");
		h.assertTrue(IronManSuitUpManager.blockedWhileAssembling(p, false), "abilities are locked mid suit-up");
		com.projecthero.mod.ironman.IronManFlight.toggle(p);
		h.assertFalse(com.projecthero.mod.ironman.IronManFlight.isFlying(p), "no take-off mid suit-up");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitUpPoseIsAPureFunctionOfThePieceNoRandomVariant(GameTestHelper h) {
		for (int run = 0; run < 6; run++) {
			ServerPlayer p = suitedUpReady(h);
			h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
			IronManSuitFx fx = IronManSuitFx.of(p);
			h.assertTrue(fx.poseKind() == IronManSuitFx.POSE_SUIT_UP, "the suit-up pose runs");
			h.assertTrue(fx.poseVariant() == 0, "v0.14.28: no random pose variant any more");
			ByteBuf buf = Unpooled.buffer();
			IronManSuitFx.STREAM_CODEC.encode(buf, fx);
			h.assertTrue(IronManSuitFx.STREAM_CODEC.decode(buf).equals(fx), "the clocks sync to every viewer unchanged");
		}
		// a full 4-piece suit-up clock, boots at 1001, leggings 1061, chest 1121, helmet 1181
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPose(IronManSuitFx.POSE_SUIT_UP, 1000L, 251, IronManSuitFx.STYLE_PLATES)
				.withPiece(3, 1001L, true).withPiece(2, 1061L, true).withPiece(1, 1121L, true).withPiece(0, 1181L, true);
		// pure: same input, same output -- and the key at a given moment is exactly the building piece's own key
		for (long t = 1001L; t < 1241L; t += 7) {
			IronManSuitPoses.Sample a = IronManSuitPoses.sample(fx, t, 0.3f);
			IronManSuitPoses.Sample b = IronManSuitPoses.sample(fx, t, 0.3f);
			h.assertTrue(a != null && b != null && a.weight() == b.weight() && java.util.Arrays.equals(a.key(), b.key()),
					"the pose is a pure function of the clocks at t=" + t);
		}
		float[] boots = IronManSuitPoses.sample(fx, 1031L, 0f).key();
		float[] legs = IronManSuitPoses.sample(fx, 1091L, 0f).key();
		float[] chest = IronManSuitPoses.sample(fx, 1151L, 0f).key();
		float[] helmet = IronManSuitPoses.sample(fx, 1211L, 0f).key();
		h.assertTrue(java.util.Arrays.equals(boots, IronManSuitPoses.key(3, 0.5f)), "mid-boots = the boots key");
		h.assertTrue(java.util.Arrays.equals(chest, IronManSuitPoses.key(1, 0.5f)), "mid-chest = the chest key");
		h.assertTrue(boots[IronManSuitPoses.HX] > 0.4f, "boots: looking down at the feet");
		h.assertTrue(legs[IronManSuitPoses.RAZ] > 0.6f && legs[IronManSuitPoses.RLZ] > 0.15f, "leggings: arms out, wide stance");
		h.assertTrue(chest[IronManSuitPoses.RAZ] > 1.1f && chest[IronManSuitPoses.HX] < 0f, "chestplate: arms spread wide, head up");
		h.assertTrue(helmet[IronManSuitPoses.HX] > 0.3f && IronManSuitPoses.key(0, 1f)[IronManSuitPoses.HX] < 0f,
				"helmet: head bowed, then up at the end");
		// no shake: within every piece each angle moves one way only (no tremor / oscillation)
		for (int bit = 0; bit < 4; bit++) {
			float[] prev = IronManSuitPoses.key(bit, 0f);
			int[] dir = new int[IronManSuitPoses.SIZE];
			for (int i = 1; i <= 240; i++) {
				float[] k = IronManSuitPoses.key(bit, i / 240f);
				for (int a = 0; a < IronManSuitPoses.SIZE; a++) {
					float d = k[a] - prev[a];
					int sgn = d > 1e-6f ? 1 : d < -1e-6f ? -1 : 0;
					h.assertTrue(sgn == 0 || dir[a] == 0 || sgn == dir[a], "piece " + bit + " angle " + a + " never reverses (no jitter)");
					if (sgn != 0) {
						dir[a] = sgn;
					}
				}
				prev = k;
			}
		}
		// crossing from one piece to the next is continuous (a cross-fade, no snap)
		float[] before = IronManSuitPoses.sample(fx, 1060L, 0.95f).key();
		float[] after = IronManSuitPoses.sample(fx, 1061L, 0.05f).key();
		for (int a = 0; a < IronManSuitPoses.SIZE; a++) {
			h.assertTrue(Math.abs(before[a] - after[a]) < 0.05f, "piece hand-over is smooth for angle " + a);
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitUpPoseIsGoneMomentsAfterTheLastPiece(GameTestHelper h) {
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPose(IronManSuitFx.POSE_SUIT_UP, 1000L, 251, IronManSuitFx.STYLE_PLATES)
				.withPiece(3, 1001L, true).withPiece(2, 1061L, true).withPiece(1, 1121L, true).withPiece(0, 1181L, true);
		long done = 1181L + IronManSuitFx.BUILD_TICKS; // the helmet finishes
		h.assertTrue(IronManSuitPoses.weight(fx, done - 1, 0f) == 1f, "full pose while the helmet builds");
		h.assertTrue(IronManSuitPoses.weight(fx, done + 6, 0f) == 0f && IronManSuitPoses.sample(fx, done + 6, 0f) == null,
				"the pose is gone 0.3 s after the last piece finishes (no lingering)");
		h.assertTrue(IronManSuitPoses.weight(fx, done + 2, 0f) < 1f, "and it is already easing out right after");
		// and on the server the pose clock is dropped the moment the suit-up completes
		ServerPlayer p = suitedUpReady(h);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
		for (int i = 0; i < 400 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_NONE, "suit-up complete: the pose clock is cleared");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitDownTakesSixtyTicksPerPieceInReverse(GameTestHelper h) {
		ServerPlayer p = suitedUpReady(h, "mark_8"); // v0.15.15: Marks 1-7 have their own C removals now -- the Mark 8 keeps the reverse build
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_8"), "suit-up starts");
		for (int i = 0; i < 400 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_8"), "suited up");
		h.assertTrue(IronManSuitUpManager.unbuildStageTick(0, 0b1111) == 1 && IronManSuitUpManager.unbuildStageTick(1, 0b1111) == 61
				&& IronManSuitUpManager.unbuildStageTick(2, 0b1111) == 121 && IronManSuitUpManager.unbuildStageTick(3, 0b1111) == 181,
				"helmet, chestplate, leggings, boots -- 60 ticks apart");
		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_8"), "suit-down starts");
		var st = TonyStark.state(p);
		h.assertTrue(st.transitionTotal == 241 && st.transitionTicks == 241 && st.transitionPlan == 0b1111,
				"suit-down timeline: total " + st.transitionTotal + " ticks " + st.transitionTicks + " plan " + st.transitionPlan
						+ " up " + st.transitionUp);
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.style() == IronManSuitFx.STYLE_UNBUILD && fx.releaseTicks() == IronManSuitFx.BUILD_TICKS,
				"each piece un-builds over the same 60 ticks it took to build");
		int ticks = 0;
		int helmetOffAt = -1;
		int chestOffAt = -1;
		int bootsOffAt = -1;
		boolean posedMidway = false;
		while (IronManSuitUpManager.inTransition(p) && ticks < 1000) {
			IronManSuitUpManager.tick(p);
			ticks++;
			if (helmetOffAt < 0 && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				helmetOffAt = ticks;
			}
			if (chestOffAt < 0 && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
				chestOffAt = ticks;
			}
			if (bootsOffAt < 0 && p.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
				bootsOffAt = ticks;
			}
			if (ticks == 90) {
				IronManSuitFx now = IronManSuitFx.of(p);
				h.assertTrue(!now.assembling(1) && now.start(1) > 0L, "the chestplate is un-building at 90 ticks");
				posedMidway = IronManSuitPoses.weight(now, now.start(1) + 29, 0f) == 1f;
			}
		}
		h.assertTrue(ticks == IronManSuitUpManager.unbuildSequenceTicks(4), "the suit-down ran 4 x 60 (+1) ticks, got " + ticks
				+ " (off at " + helmetOffAt + ", " + chestOffAt + ", " + bootsOffAt + ")");
		h.assertTrue(helmetOffAt == 61 && chestOffAt == 121 && bootsOffAt == 241,
				"each piece leaves the body after its full 60-tick un-build (" + helmetOffAt + ", " + chestOffAt + ", " + bootsOffAt + ")");
		h.assertTrue(posedMidway, "the per-piece pose plays during the suit-down too");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is off");
		for (ArmorItem.Type t : TYPES) {
			h.assertTrue(p.getInventory().countItem(IronManItems.armor("mark_8", t)) == 1, t.getName() + " stored exactly once");
		}
		h.succeed();
	}
}
