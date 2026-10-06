package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.fabricator.PlatformDeployTimeline;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.1: the Suit Platform's 8-second robotic-arm deploy -- the timetable, the piece order, the stance / movement lock,
 * abilities locked until the suit comes online, and the exactly-once guarantee through every interruption (sneak to
 * cancel, logging out, the platform being broken). Asserts on the shared timetable's tick numbers, never wall time.
 */
public class IronManPlatformArmsV0151GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final BlockPos PLATFORM = new BlockPos(2, 2, 2);

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 2)));
		p.setPos(v.x, v.y, v.z);
		return p;
	}

	/** A north-facing platform with a clear, floored spot in front of it (where the deploy stands its wearer). */
	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h) {
		h.setBlock(PLATFORM, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		h.setBlock(PLATFORM.north().below(), Blocks.STONE);
		h.setBlock(PLATFORM.north(), Blocks.AIR);
		h.setBlock(PLATFORM.north().above(), Blocks.AIR);
		return (IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM);
	}

	private static String nameFor(GameTestHelper h, ArmorItem.Type type) {
		return "Arms test " + type.getName() + " @" + h.absolutePos(BlockPos.ZERO).toShortString();
	}

	private static ItemStack marked(GameTestHelper h, ArmorItem.Type type) {
		ItemStack s = new ItemStack(IronManItems.armor("mark_iii", type));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(nameFor(h, type)));
		return s;
	}

	private static boolean isMarked(GameTestHelper h, ItemStack s, ArmorItem.Type type) {
		return s.getItem() instanceof IronManArmorItem a && a.suitId().equals("mark_iii") && a.getType() == type
				&& nameFor(h, type).equals(s.getHoverName().getString());
	}

	private static void stock(GameTestHelper h, IronManSuitPlatformBlockEntity be) {
		for (ArmorItem.Type t : TYPES) {
			h.assertTrue(be.store(marked(h, t)), "the rack takes the " + t.getName());
		}
	}

	/** Copies of the named piece anywhere: worn, in the pack, on the rack, or loose on the ground. */
	private static int copiesOf(GameTestHelper h, ServerPlayer p, IronManSuitPlatformBlockEntity be, ArmorItem.Type type) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			n += isMarked(h, p.getItemBySlot(slot), type) ? 1 : 0;
		}
		for (ItemStack s : p.getInventory().items) {
			n += isMarked(h, s, type) ? 1 : 0;
		}
		if (be != null && !be.isRemoved()) {
			for (int i = 0; i < be.getContainerSize(); i++) {
				n += isMarked(h, be.getItem(i), type) ? 1 : 0;
			}
		}
		for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))) {
			n += isMarked(h, e.getItem(), type) ? 1 : 0;
		}
		return n;
	}

	private static void assertEachOnce(GameTestHelper h, ServerPlayer p, IronManSuitPlatformBlockEntity be, String when) {
		for (ArmorItem.Type t : TYPES) {
			int c = copiesOf(h, p, be, t);
			h.assertTrue(c == 1, t.getName() + " must exist exactly once " + when + ", got " + c);
		}
	}

	private static int wornCount(ServerPlayer p) {
		int worn = 0;
		for (ArmorItem.Type t : TYPES) {
			worn += p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		return worn;
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	// ------------------------------------------------------------------ the timetable

	@GameTest(template = EMPTY_STRUCTURE)
	public void deployTimetableIsEightSecondsWithOrderedPieces(GameTestHelper h) {
		h.assertTrue(PlatformDeployTimeline.TOTAL == 160, "the robotic-arm suit-up is 8 s (160 ticks)");
		for (int n = 1; n <= 4; n++) {
			h.assertTrue(IronManSuitPlatformBlockEntity.sequenceLength(IronManSuitPlatformBlockEntity.SEQ_DEPLOY, n) == 160,
					"a " + n + "-piece deploy still takes 160 ticks");
			int prevEquip = -1;
			for (int i = 0; i < n; i++) {
				int start = PlatformDeployTimeline.pieceStart(i, n);
				int lift = PlatformDeployTimeline.liftTick(i, n);
				int equip = PlatformDeployTimeline.equipTick(i, n);
				int letGo = PlatformDeployTimeline.letGoTick(i, n);
				h.assertTrue(start >= PlatformDeployTimeline.LEAD && start < lift && lift < equip && equip < letGo
						&& letGo < PlatformDeployTimeline.pieceStart(i + 1, n) + 1,
						"piece " + i + "/" + n + ": reach < grip < fit < let go, inside its window");
				h.assertTrue(equip > prevEquip, "pieces go on one after another");
				h.assertTrue(PlatformDeployTimeline.pieceAt(start, n) == i && PlatformDeployTimeline.pieceAt(equip, n) == i,
						"the window lookup agrees with the timetable");
				prevEquip = equip;
			}
			h.assertTrue(prevEquip < PlatformDeployTimeline.foldTick(), "the last piece is on before the arms fold away");
		}
		// four pieces: 30-tick windows, on the body at 43 / 73 / 103 / 133, alternating arms
		h.assertTrue(PlatformDeployTimeline.window(4) == 30, "four pieces get 30 ticks each");
		h.assertTrue(PlatformDeployTimeline.equipTick(0, 4) == 43 && PlatformDeployTimeline.equipTick(3, 4) == 133,
				"boots on at 43, helmet at 133");
		h.assertTrue(PlatformDeployTimeline.rightArmCarries(0) && !PlatformDeployTimeline.rightArmCarries(1),
				"the arms take turns carrying");
		h.assertTrue(PlatformDeployTimeline.pose(0f, 4)[0] < 0.01f && PlatformDeployTimeline.pose(160f, 4)[0] < 0.01f
				&& PlatformDeployTimeline.pose(80f, 4)[0] > 0.99f, "the arms-out pose eases in and is gone by the end");
		h.succeed();
	}

	// ------------------------------------------------------------------ the live sequence

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void deployFitsBootsLegsChestHelmetAndComesOnlineAt160(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h);
		stock(h, be);
		h.assertTrue(be.deployTo(p), "deploy starts");
		int[] order = be.seqSlots();
		h.assertTrue(order.length == 4 && order[0] == 3 && order[1] == 2 && order[2] == 1 && order[3] == 0,
				"the arms fit boots, leggings, chestplate, helmet in that order");
		Vec3 stance = be.deployStance();
		h.assertTrue(p.position().distanceTo(stance) < 0.05, "the wearer is stood in front of the platform, got " + p.position()
				+ " vs " + stance);
		h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - 180f)) < 1f, "facing out (north), got " + p.getYRot());
		h.assertTrue(IronManSuitPlatformBlockEntity.isFrozen(p), "and held still for the suit-up");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_PLATFORM, "in the platform pose");
		h.onEachTick(() -> {
			if (!p.isRemoved()) {
				assertEachOnce(h, p, be, "every tick");
			}
		});
		EquipmentSlot[] slots = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
		for (int i = 0; i < 4; i++) {
			final int k = i;
			int equip = IronManSuitPlatformBlockEntity.deployEquipTick(i, 4);
			h.runAfterDelay(equip - 2, () -> {
				h.assertTrue(p.getItemBySlot(slots[k]).isEmpty(), slots[k].getName() + " is still in the clamp at tick " + (equip - 2));
				h.assertTrue(wornCount(p) == k, "only the earlier pieces are on, got " + wornCount(p));
				h.assertTrue(IronManSuitUpManager.blockedWhileAssembling(p, false), "abilities stay locked");
			});
			h.runAfterDelay(equip + 2, () -> {
				h.assertTrue(isMarked(h, p.getItemBySlot(slots[k]), TYPES[3 - k]), slots[k].getName() + " is on by tick " + (equip + 2));
				h.assertTrue(wornCount(p) == k + 1, "exactly " + (k + 1) + " pieces on, got " + wornCount(p));
			});
		}
		h.runAfterDelay(IronManSuitPlatformBlockEntity.deployEquipTick(1, 4) + 3, () -> {
			IronManFlight.toggle(p);
			h.assertFalse(IronManFlight.isFlying(p), "no take-off while the arms are still working");
		});
		h.runAfterDelay(PlatformDeployTimeline.TOTAL - 5, () -> {
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "every piece is on before the end");
			h.assertTrue(be.sequenceRunning() && IronManSuitUpManager.assembling(p), "but the suit is not online yet");
			h.assertTrue(IronManFaceplate.isOpen(p), "the faceplate is still up -- it closes last");
			h.assertTrue(IronManSuitPlatformBlockEntity.isFrozen(p), "still held still");
		});
		h.runAfterDelay(PlatformDeployTimeline.TOTAL + 5, () -> {
			h.assertFalse(be.sequenceRunning(), "the sequence is over");
			h.assertFalse(IronManSuitUpManager.inTransition(p), "the suit is online");
			h.assertFalse(IronManFaceplate.isOpen(p), "with the faceplate shut");
			h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "and the wearer free to move");
			h.assertTrue(be.isEmptyPlatform(), "the rack is empty");
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(isMarked(h, p.getItemBySlot(IronManSuitUpManager.slotFor(t)), t), "the worn " + t.getName() + " is the real stack");
			}
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void sneakingCancelsMidDeployWithNothingLost(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h);
		stock(h, be);
		h.assertTrue(be.deployTo(p), "deploy starts");
		int cancelAt = IronManSuitPlatformBlockEntity.deployEquipTick(1, 4) + 3; // boots + leggings on
		h.runAfterDelay(cancelAt, () -> p.setShiftKeyDown(true));
		h.runAfterDelay(cancelAt + 6, () -> {
			p.setShiftKeyDown(false);
			h.assertFalse(be.sequenceRunning(), "sneaking stopped the sequence");
			h.assertFalse(IronManSuitUpManager.inTransition(p), "and handed the suit-up state back");
			h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "and the wearer can move again");
			h.assertTrue(wornCount(p) == 2, "only what had already been fitted is worn, got " + wornCount(p));
			assertEachOnce(h, p, be, "after the cancel");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void loggingOutMidDeployLosesNothing(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h);
		stock(h, be);
		h.assertTrue(be.deployTo(p), "deploy starts");
		int at = IronManSuitPlatformBlockEntity.deployEquipTick(2, 4) + 3; // boots, leggings, chestplate on
		h.runAfterDelay(at, () -> leave(h, p));
		h.runAfterDelay(at + 6, () -> {
			h.assertFalse(be.sequenceRunning(), "the platform stops when its wearer logs out");
			h.assertTrue(wornCount(p) == 3, "the logged-out player keeps what was fitted, got " + wornCount(p));
			assertEachOnce(h, p, be, "after the logout");
			h.assertTrue(isMarked(h, be.getItem(0), ArmorItem.Type.HELMET), "the helmet is still on the rack");
			CompoundTag saved = p.saveWithoutId(new CompoundTag());
			h.assertFalse(saved.toString().contains(IronManSuitPlatformBlockEntity.FREEZE_ID.toString()),
					"the movement lock is never written into the player's save");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void breakingThePlatformMidDeployLosesNothing(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h);
		stock(h, be);
		h.assertTrue(be.deployTo(p), "deploy starts");
		int at = IronManSuitPlatformBlockEntity.deployEquipTick(0, 4) + 3; // boots on
		h.runAfterDelay(at, () -> h.setBlock(PLATFORM, Blocks.AIR));
		h.runAfterDelay(at + 4, () -> {
			h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "a broken platform lets its wearer go at once");
			h.assertTrue(wornCount(p) == 1, "the boots stay on, got " + wornCount(p));
			assertEachOnce(h, p, null, "after the platform broke (the rest dropped)");
			leave(h, p);
			h.succeed();
		});
	}
}
