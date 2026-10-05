package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManAssemblyPlan;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
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
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_iii", t)));
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
	public void poseVariantIsOneOfFourAndSynced(GameTestHelper h) {
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int run = 0; run < 12; run++) {
			ServerPlayer p = suitedUpReady(h);
			h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
			IronManSuitFx fx = IronManSuitFx.of(p);
			h.assertTrue(fx.poseKind() == IronManSuitFx.POSE_SUIT_UP, "the suit-up pose runs");
			h.assertTrue(fx.poseVariant() >= 0 && fx.poseVariant() < IronManSuitFx.POSE_VARIANTS && IronManSuitFx.POSE_VARIANTS == 4,
					"the pose variant is 0..3, got " + fx.poseVariant());
			h.assertTrue(fx.poseTicks() >= IronManSuitUpManager.buildSequenceTicks(4), "the pose covers the whole build");
			seen.add(fx.poseVariant());
			ByteBuf buf = Unpooled.buffer();
			IronManSuitFx.STREAM_CODEC.encode(buf, fx);
			h.assertTrue(IronManSuitFx.STREAM_CODEC.decode(buf).poseVariant() == fx.poseVariant(), "the variant syncs to every viewer");
		}
		h.assertTrue(seen.size() >= 2, "the variant is picked at random per suit-up, saw " + seen);
		h.assertTrue(IronManSuitFx.EMPTY.withPose(IronManSuitFx.POSE_SUIT_UP, 1L, 10, IronManSuitFx.STYLE_PLATES, 7).poseVariant() == 3,
				"out-of-range variants wrap into 0..3");
		h.succeed();
	}
}
