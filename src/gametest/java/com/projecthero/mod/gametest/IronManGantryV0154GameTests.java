package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gantry.StarkGantry;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlock;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.network.StarkGantryMenuPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.4, explicit user request: the Stark Gantry -- a 5x5 piston floor whose robotic arms put a suit racked on a Suit
 * Platform within 20 blocks onto the player, and take it off again (replacing the Suit Platform's own robotic-arm
 * deploy / retrieve). Formation, the 20-block listing, the equip and unequip sequences, and every way a sequence can be
 * interrupted -- each checked for "every piece exists exactly once, every tick". Timings read {@link GantryTimeline}.
 */
public class IronManGantryV0154GameTests implements FabricGameTest {
	private static final String SUIT = "mark_iii";
	/** Rack-slot order (0 HEAD, 1 CHEST, 2 LEGS, 3 FEET), the Suit Platform's layout. */
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final BlockPos CENTRE = new BlockPos(2, 1, 2);
	/** Rack slot -> armour slot. */
	private static final EquipmentSlot[] SLOT_OF = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final BlockPos PLATFORM = new BlockPos(6, 2, 6);

	// ------------------------------------------------------------------ helpers (also used by HeroPackGameTests)

	/** Lay a complete 5x5 gantry floor centred on {@code centre} (relative). */
	public static void floor(GameTestHelper h, BlockPos centre) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				h.setBlock(centre.offset(dx, 0, dz), IronManBlocks.STARK_GANTRY_FLOOR);
			}
		}
	}

	/** Stand {@code p} on the middle of the tile at {@code tile} (relative), facing north. */
	public static void standOn(GameTestHelper h, ServerPlayer p, BlockPos tile) {
		Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(tile.above()));
		p.setPos(v.x, v.y, v.z);
		p.setYRot(180f);
		p.setYHeadRot(180f);
	}

	private static ServerPlayer stark(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		return p;
	}

	private static ServerPlayer suited(GameTestHelper h) {
		ServerPlayer p = stark(h);
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(IronManSuitUpManager.slotFor(TYPES[i]), marked(SUIT, TYPES[i]));
		}
		TonyStark.setActiveSuit(p, SUIT);
		return p;
	}

	/** A piece with a unique name, so the test can follow that exact stack around. */
	private static ItemStack marked(String suit, ArmorItem.Type t) {
		ItemStack s = new ItemStack(IronManItems.armor(suit, t));
		s.set(DataComponents.CUSTOM_NAME, Component.literal("gantry-test-" + t.getName()));
		return s;
	}

	private static boolean isMarked(ItemStack s, ArmorItem.Type t) {
		return s.getItem() instanceof IronManArmorItem a && a.getType() == t && s.has(DataComponents.CUSTOM_NAME)
				&& s.get(DataComponents.CUSTOM_NAME).getString().equals("gantry-test-" + t.getName());
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer owner, BlockPos rel, boolean racked) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		// bound to this test's player: an unowned rack is visible to every other test's suit calls (cross-test pollution)
		be.bindTo(owner.getUUID());
		if (racked) {
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(be.store(marked(SUIT, t)), "racked " + t.getName());
			}
		}
		return be;
	}

	private static StarkGantryFloorBlockEntity centre(GameTestHelper h) {
		return (StarkGantryFloorBlockEntity) h.getBlockEntity(CENTRE);
	}

	/** Copies of the marked {@code t} anywhere: worn, carried, racked on any of {@code racks}, in the floor, dropped. */
	private static int copies(GameTestHelper h, ServerPlayer p, ArmorItem.Type t, IronManSuitPlatformBlockEntity... racks) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			n += isMarked(p.getItemBySlot(slot), t) ? 1 : 0;
		}
		for (ItemStack s : p.getInventory().items) {
			n += isMarked(s, t) ? 1 : 0;
		}
		for (IronManSuitPlatformBlockEntity r : racks) {
			if (!r.isRemoved()) {
				for (int i = 0; i < 4; i++) {
					n += isMarked(r.getItem(i), t) ? 1 : 0;
				}
			}
		}
		if (h.getBlockEntity(CENTRE) instanceof StarkGantryFloorBlockEntity g) {
			for (int i = 0; i < 4; i++) {
				n += isMarked(g.buffered(i), t) ? 1 : 0;
			}
		}
		// only this test's own 8x8x8 space (neighbouring tests' drops must not be counted)
		AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(8, 8, 8).inflate(0.5);
		for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			n += isMarked(e.getItem(), t) ? 1 : 0;
		}
		return n;
	}

	private static void assertEachOnce(GameTestHelper h, ServerPlayer p, String when, IronManSuitPlatformBlockEntity... racks) {
		for (ArmorItem.Type t : TYPES) {
			int c = copies(h, p, t, racks);
			h.assertTrue(c == 1, t.getName() + " must exist exactly once " + when + ", found " + c);
		}
	}

	private static int worn(ServerPlayer p) {
		int n = 0;
		for (ArmorItem.Type t : TYPES) {
			n += p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		return n;
	}

	private static int racked(IronManSuitPlatformBlockEntity be) {
		int n = 0;
		for (int i = 0; i < 4; i++) {
			n += be.getItem(i).getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		return n;
	}

	private static boolean anyTileOpen(GameTestHelper h) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				var st = h.getBlockState(CENTRE.offset(dx, 0, dz));
				if (st.hasProperty(StarkGantryFloorBlock.OPEN) && st.getValue(StarkGantryFloorBlock.OPEN)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static void assertReleased(GameTestHelper h, ServerPlayer p) {
		h.assertFalse(centre(h).running(), "the gantry has stopped");
		h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "the player can move again");
		h.assertFalse(IronManSuitUpManager.inTransition(p), "the suit-up hold is handed back");
		h.assertTrue(centre(h).bufferEmpty(), "nothing is left in the floor");
	}

	// ------------------------------------------------------------------ formation

	@GameTest(template = EMPTY_STRUCTURE)
	public void aGantryIsACompleteFiveByFive(GameTestHelper h) {
		h.assertTrue(StarkGantry.findCentre(h.getLevel(), h.absolutePos(CENTRE)) == null, "no floor, no gantry");
		floor(h, CENTRE);
		BlockPos c = h.absolutePos(CENTRE);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos found = StarkGantry.findCentre(h.getLevel(), c.offset(dx, 0, dz));
				h.assertTrue(c.equals(found), "standing on tile " + dx + "," + dz + " finds the centre, got " + found);
			}
		}
		h.assertTrue(StarkGantry.complete(h.getLevel(), c), "complete");
		// one corner missing: no longer a gantry anywhere on it
		h.setBlock(CENTRE.offset(2, 0, -2), Blocks.STONE);
		h.assertFalse(StarkGantry.complete(h.getLevel(), c), "a missing corner breaks it");
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 2 && dz == -2) {
					continue;
				}
				h.assertTrue(StarkGantry.findCentre(h.getLevel(), c.offset(dx, 0, dz)) == null, "incomplete: no centre from "
						+ dx + "," + dz);
			}
		}
		// a 4x5 strip is not a gantry either
		h.setBlock(CENTRE.offset(2, 0, -2), IronManBlocks.STARK_GANTRY_FLOOR);
		for (int dz = -2; dz <= 2; dz++) {
			h.setBlock(CENTRE.offset(-2, 0, dz), Blocks.AIR);
		}
		h.assertTrue(StarkGantry.findCentre(h.getLevel(), c) == null, "4 x 5 is not enough");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hOffTheFloorOrIncompleteIsRefusedWithAReason(GameTestHelper h) {
		ServerPlayer p = stark(h);
		platform(h, p, PLATFORM, true);
		floor(h, CENTRE);
		h.setBlock(CENTRE.offset(-2, 0, -2), Blocks.STONE);
		standOn(h, p, CENTRE);
		h.assertTrue(StarkGantry.centreUnder(p) == null, "an incomplete floor is no gantry");
		h.assertFalse(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "refused on an incomplete floor");
		h.assertTrue(racked((IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM)) == 4, "nothing left the rack");
		h.setBlock(CENTRE.offset(-2, 0, -2), IronManBlocks.STARK_GANTRY_FLOOR);
		TonyStark.revoke(p);
		h.assertFalse(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "only Tony Stark can use it");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ the 20-block listing

	@GameTest(template = EMPTY_STRUCTURE, batch = "gantry_range")
	public void onlySuitPlatformsWithinTwentyBlocksAreListed(GameTestHelper h) {
		BlockPos c = h.absolutePos(CENTRE);
		// pure distance: measured from the floor's edge
		h.assertTrue(StarkGantry.inRange(c, c.offset(22, 0, 0)), "20 blocks past the edge is in range");
		h.assertFalse(StarkGantry.inRange(c, c.offset(23, 0, 0)), "21 past the edge is not");
		h.assertTrue(StarkGantry.inRange(c, c.offset(0, 20, 0)) && !StarkGantry.inRange(c, c.offset(0, 21, 0)), "straight up too");

		ServerPlayer p = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity near = platform(h, p, PLATFORM, true);
		// two more racks straight above the test (nothing else lives up there): 19 and 23 blocks from the floor
		BlockPos in = c.above(19);
		BlockPos out = c.above(23);
		for (BlockPos at : new BlockPos[] { in, out }) {
			h.getLevel().setBlockAndUpdate(at, IronManBlocks.IRON_MAN_SUIT_PLATFORM.defaultBlockState());
			IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getLevel().getBlockEntity(at);
			be.bindTo(p.getUUID());
			for (ArmorItem.Type t : TYPES) {
				be.store(new ItemStack(IronManItems.armor("mark_vii", t)));
			}
		}
		try {
			StarkGantryMenuPayload menu = StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p);
			List<BlockPos> listed = menu.entries().stream().map(StarkGantryMenuPayload.Entry::platform).toList();
			h.assertTrue(listed.contains(near.getBlockPos()), "the platform beside the floor is listed");
			h.assertTrue(listed.contains(in), "the one 19 blocks away is listed");
			h.assertFalse(listed.contains(out), "the one 23 blocks away is not");
			// v0.15.5: newest Mark first -- the Mark 7 19 blocks up outranks the Mark III beside the floor
			h.assertTrue(listed.get(0).equals(in) && listed.get(1).equals(near.getBlockPos()), "highest Mark first");
			StarkGantryMenuPayload.Entry e = menu.entries().get(1);
			h.assertTrue(e.suitId().equals(SUIT) && e.mask() == 15 && e.distance() <= 4, "with the suit, its pieces and distance");
			h.assertTrue(menu.wornSuit().isEmpty(), "unsuited: the pick list, not Remove armour");
			h.assertFalse(StarkGantry.beginEquip(p, out), "the out-of-range suit can't be picked even if asked for");
		} finally {
			h.getLevel().setBlockAndUpdate(in, Blocks.AIR.defaultBlockState());
			h.getLevel().setBlockAndUpdate(out, Blocks.AIR.defaultBlockState());
			for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(in).inflate(3).expandTowards(0, 5, 0))) {
				e.discard();
			}
		}
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ putting a suit on

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 560)
	public void equipFitsTheSuitPieceByPieceAndEmptiesThePlatform(GameTestHelper h) {
		ServerPlayer p = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE.east()); // anywhere on the floor: the gantry walks them onto the lift
		p.setYRot(97f);
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, true);
		h.assertTrue(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "the gantry starts");
		StarkGantryFloorBlockEntity g = centre(h);
		h.assertTrue(g.running() && g.mode() == StarkGantryFloorBlockEntity.MODE_EQUIP, "the centre tile runs it");
		h.assertTrue(rack.isEmptyPlatform(), "the whole suit left the platform at once");
		h.assertTrue(worn(p) == 0, "nothing is on yet");
		h.assertTrue(p.position().distanceTo(g.standAt()) < 0.05, "moved onto the lift");
		h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - 90f)) < 0.5f, "facing snapped to the grid (west), got " + p.getYRot());
		h.assertTrue(IronManSuitPlatformBlockEntity.isFrozen(p), "held still");
		h.assertTrue(anyTileOpen(h), "the floor opens");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_PLATFORM, "in the gantry pose");
		h.assertFalse(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "a second request is refused");
		GantryTimeline.Plan plan = g.plan();
		h.assertTrue(plan.mask() == 15 && plan.total() == GantryTimeline.FULL.total(), "a full suit plays the full plan");
		h.onEachTick(() -> assertEachOnce(h, p, "every tick", rack));
		// v0.15.5: the chest first (its top half is the first part on), then the boots, the leggings, the helmet
		int[] order = { 1, 3, 2, 0 };
		for (int i = 0; i < 4; i++) {
			final int k = i;
			final int idx = order[i];
			int at = plan.equipTick(idx);
			h.runAfterDelay(at - 2, () -> {
				h.assertTrue(p.getItemBySlot(SLOT_OF[idx]).isEmpty(), SLOT_OF[idx].getName() + " not on before tick " + at);
				h.assertTrue(worn(p) == k, "pieces go on one at a time, got " + worn(p));
				p.setYRot(-30f); // try to turn
			});
			h.runAfterDelay(at + 2, () -> {
				h.assertTrue(isMarked(p.getItemBySlot(SLOT_OF[idx]), TYPES[idx]), SLOT_OF[idx].getName() + " on by tick " + (at + 2));
				h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - 90f)) < 0.5f, "the heading is held");
				h.assertFalse(IronManFaceplate.isOpen(p), "the helmet goes on shut (the faceplate is its own part)");
			});
		}
		h.runAfterDelay(plan.total() / 2, () -> {
			h.assertTrue(IronManDamage.suitUpImmune(p), "immune while the suit goes on");
			h.assertFalse(IronManDamage.onAllowDamage(p, p.damageSources().generic(), 5f), "hits are cancelled");
		});
		h.runAfterDelay(plan.total() + 6, () -> {
			assertReleased(h, p);
			h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the full suit is on");
			h.assertTrue(rack.isEmptyPlatform(), "the platform is empty");
			h.assertFalse(anyTileOpen(h), "the floor is shut again");
			h.assertFalse(IronManFaceplate.isOpen(p), "the faceplate closed as it came online");
			h.assertFalse(IronManDamage.suitUpImmune(p), "no longer immune");
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ taking it off

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 560)
	public void unequipTakesTheSuitOffHelmetFirstAndRacksIt(GameTestHelper h) {
		ServerPlayer p = suited(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, false);
		StarkGantryMenuPayload menu = StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p);
		h.assertTrue(menu.wornSuit().equals(SUIT) && menu.canRemove(), "suited: the menu offers Remove armour");
		h.assertTrue(StarkGantry.beginUnequip(p), "the gantry starts");
		h.assertTrue(centre(h).mode() == StarkGantryFloorBlockEntity.MODE_UNEQUIP, "taking it off");
		h.assertTrue(worn(p) == 4, "still all on");
		h.assertFalse(IronManDamage.suitUpImmune(p), "taking a suit off gives no immunity");
		h.onEachTick(() -> assertEachOnce(h, p, "every tick", rack));
		GantryTimeline.Plan plan = centre(h).plan();
		// v0.15.5: the putting-on order backwards -- helmet, leggings, boots, and the chest last
		int[] order = { 0, 2, 3, 1 };
		for (int i = 0; i < 4; i++) {
			final int left = 3 - i;
			final int idx = order[i];
			int at = plan.removeTick(idx);
			h.runAfterDelay(at - 2, () -> h.assertTrue(p.getItemBySlot(SLOT_OF[idx]).getItem() instanceof IronManArmorItem,
					SLOT_OF[idx].getName() + " still on before tick " + at));
			h.runAfterDelay(at + 2, () -> {
				h.assertTrue(p.getItemBySlot(SLOT_OF[idx]).isEmpty(), SLOT_OF[idx].getName() + " off by tick " + (at + 2));
				h.assertTrue(worn(p) == left, "the pieces come off one at a time in reverse, got " + worn(p));
			});
		}
		h.runAfterDelay(plan.total() + 6, () -> {
			assertReleased(h, p);
			h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is off");
			h.assertTrue(rack.isFull() && SUIT.equals(rack.storedSuitId()), "and racked on the platform");
			for (int i = 0; i < 4; i++) {
				h.assertTrue(isMarked(rack.getItem(i), TYPES[i]), "the real " + TYPES[i].getName() + " is racked");
			}
			h.assertFalse(IronManFaceplate.isOpen(p), "no faceplate left open");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void removeArmourNeedsAPlatformWithRoom(GameTestHelper h) {
		ServerPlayer p = suited(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity full = platform(h, p, PLATFORM, false);
		full.store(new ItemStack(IronManItems.armor("mark_vii", ArmorItem.Type.HELMET))); // another mark: no room
		h.assertFalse(StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p).canRemove(), "no room anywhere");
		h.assertFalse(StarkGantry.beginUnequip(p), "so Remove armour is refused");
		h.assertTrue(worn(p) == 4, "and the suit stays on");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ interruptions: nothing lost, nothing duplicated

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 480)
	public void loggingOutMidEquipSendsTheRestBackToThePlatform(GameTestHelper h) {
		ServerPlayer p = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, true);
		h.assertTrue(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "the gantry starts");
		int at = GantryTimeline.FULL.equipTick(GantryTimeline.FEET) + 3; // chestplate + boots on
		h.runAfterDelay(at, () -> leave(h, p));
		h.runAfterDelay(at + 4, () -> {
			h.assertFalse(centre(h).running(), "the gantry stops when its wearer leaves");
			h.assertTrue(centre(h).bufferEmpty(), "nothing is left in the floor");
			h.assertTrue(worn(p) == 2, "chestplate + boots stay on the player, got " + worn(p));
			h.assertTrue(racked(rack) == 2, "leggings + helmet are back on the platform, got " + racked(rack));
			assertEachOnce(h, p, "after the logout", rack);
			h.assertFalse(anyTileOpen(h), "the floor shut");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 480)
	public void breakingAFloorTileMidEquipLosesNothing(GameTestHelper h) {
		ServerPlayer p = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, true);
		h.assertTrue(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "the gantry starts");
		h.onEachTick(() -> assertEachOnce(h, p, "every tick", rack));
		int at = GantryTimeline.FULL.equipTick(GantryTimeline.CHEST) + 3; // chestplate on
		h.runAfterDelay(at, () -> h.setBlock(CENTRE.offset(-2, 0, 2), Blocks.AIR));
		h.runAfterDelay(at + 3, () -> {
			assertReleased(h, p);
			h.assertTrue(worn(p) == 1 && racked(rack) == 3, "chestplate on, the rest racked again");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 480)
	public void sneakingCancelsMidUnequipWithNothingLost(GameTestHelper h) {
		ServerPlayer p = suited(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, false);
		h.assertTrue(StarkGantry.beginUnequip(p), "the gantry starts");
		h.onEachTick(() -> assertEachOnce(h, p, "every tick", rack));
		int at = GantryTimeline.FULL.removeTick(GantryTimeline.LEGS) + 3; // helmet + leggings off
		h.runAfterDelay(at, () -> p.setShiftKeyDown(true));
		h.runAfterDelay(at + 4, () -> {
			p.setShiftKeyDown(false);
			assertReleased(h, p);
			h.assertTrue(worn(p) == 2, "chestplate + boots stay on, got " + worn(p));
			h.assertTrue(racked(rack) == 2, "helmet + leggings are racked, got " + racked(rack));
			h.assertFalse(IronManFaceplate.isOpen(p), "no faceplate left open");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 560)
	public void breakingTheSourcePlatformMidEquipStillFinishes(GameTestHelper h) {
		ServerPlayer p = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, true);
		h.assertTrue(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "the gantry starts");
		h.runAfterDelay(30, () -> h.setBlock(PLATFORM, Blocks.AIR)); // the suit is in the floor already
		h.runAfterDelay(GantryTimeline.FULL.total() + 6, () -> {
			assertReleased(h, p);
			h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the suit still went on");
			assertEachOnce(h, p, "afterwards");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 560)
	public void breakingTheTargetPlatformMidUnequipHandsThePiecesBack(GameTestHelper h) {
		ServerPlayer p = suited(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		platform(h, p, PLATFORM, false);
		h.assertTrue(StarkGantry.beginUnequip(p), "the gantry starts");
		h.runAfterDelay(30, () -> h.setBlock(PLATFORM, Blocks.AIR));
		h.runAfterDelay(GantryTimeline.FULL.total() + 6, () -> {
			assertReleased(h, p);
			h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit came off");
			int carried = 0;
			for (ItemStack s : p.getInventory().items) {
				carried += s.getItem() instanceof IronManArmorItem ? 1 : 0;
			}
			h.assertTrue(carried == 4, "with no platform left in range the pieces go to the inventory, got " + carried);
			assertEachOnce(h, p, "afterwards");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 480)
	public void aSecondPlayerCantUseABusyGantry(GameTestHelper h) {
		ServerPlayer p = stark(h);
		ServerPlayer q = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		standOn(h, q, CENTRE.offset(-2, 0, -2));
		IronManSuitPlatformBlockEntity rack = platform(h, p, PLATFORM, true);
		IronManSuitPlatformBlockEntity other = platform(h, q, PLATFORM.west(5), false);
		other.store(new ItemStack(IronManItems.armor("mark_vii", ArmorItem.Type.HELMET)));
		h.assertTrue(StarkGantry.beginEquip(p, h.absolutePos(PLATFORM)), "the first player's sequence starts");
		h.assertFalse(StarkGantry.beginEquip(q, h.absolutePos(PLATFORM.west(5))), "the second is refused while it runs");
		h.assertTrue(other.getItem(0).getItem() instanceof IronManArmorItem, "and their suit stays racked");
		h.assertTrue(centre(h).playerId().equals(p.getUUID()), "the gantry still works on the first player");
		centre(h).abort(p);
		h.assertTrue(racked(rack) == 4, "an immediate abort puts everything back");
		leave(h, p);
		leave(h, q);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void timetableIsSymmetricAndOrdered(GameTestHelper h) {
		GantryTimeline.Plan full = GantryTimeline.FULL;
		h.assertTrue(full.count() == GantryTimeline.STAGES, "a full suit has every part");
		h.assertTrue(full.total() >= 380 && full.total() <= 440, "about 20 s, got " + full.total());
		int prev = -1;
		for (int i = 0; i < full.count(); i++) {
			h.assertTrue(full.stage(i) > prev, "the parts go on in the order asked for");
			prev = full.stage(i);
			h.assertTrue(full.begin(i) >= GantryTimeline.LEAD && full.begin(i) + full.length(i) <= full.total() - GantryTimeline.OUTRO,
					"part " + i + " inside the work");
			if (GantryTimeline.carried(full.stage(i))) {
				h.assertTrue(full.liftTick(i) < full.fitTick(i) && full.fitTick(i) < full.letGoTick(i), "carry " + i + " ordered");
			}
		}
		// the user's order: chest top -> bottom -> jacket, gauntlet -> arm -> sleeve (each side), boots, thigh top -> bottom
		// -> pant legs, helmet, faceplate
		int[] want = { GantryTimeline.TORSO_TOP, GantryTimeline.TORSO_BOTTOM, GantryTimeline.JACKET, GantryTimeline.R_GAUNTLET,
				GantryTimeline.R_ARM, GantryTimeline.R_SLEEVE, GantryTimeline.L_GAUNTLET, GantryTimeline.L_ARM, GantryTimeline.L_SLEEVE,
				GantryTimeline.BOOTS, GantryTimeline.THIGH_TOP, GantryTimeline.THIGH_BOTTOM, GantryTimeline.PANTS,
				GantryTimeline.HELMET, GantryTimeline.FACEPLATE };
		for (int i = 0; i < want.length; i++) {
			h.assertTrue(full.stage(i) == want[i], "stage " + i + " is " + want[i]);
		}
		h.assertTrue(full.appear(GantryTimeline.FACEPLATE, 0f) > full.appear(GantryTimeline.HELMET, 0f)
				&& full.appear(GantryTimeline.PANTS, 0f) > full.appear(GantryTimeline.THIGH_BOTTOM, 1f), "a layer after what it covers");
		h.assertTrue(full.appear(GantryTimeline.TORSO_BOTTOM, 1f) > full.appear(GantryTimeline.TORSO_BOTTOM, 0f),
				"a build grows along its part");
		for (int r = 0; r < 4; r++) {
			h.assertTrue(full.removeTick(r) == full.total() - full.equipTick(r), "taking off mirrors putting on");
		}
		// a suit missing its boots and helmet still builds the pant legs, and skips the rest
		GantryTimeline.Plan part = GantryTimeline.plan((1 << GantryTimeline.CHEST) | (1 << GantryTimeline.LEGS));
		h.assertTrue(part.indexOf(GantryTimeline.BOOTS) < 0 && part.indexOf(GantryTimeline.HELMET) < 0
				&& part.indexOf(GantryTimeline.PANTS) >= 0 && part.total() < full.total(), "missing pieces are left out");
		h.assertTrue(part.equipTick(GantryTimeline.HEAD) < 0, "no helmet tick for a suit with no helmet");
		int t = full.total();
		for (float f = 0; f <= t; f += 5) {
			h.assertTrue(Math.abs(GantryTimeline.lift(f, full) - GantryTimeline.lift(t - f, full)) < 1e-5f
					&& Math.abs(GantryTimeline.hatch(f, full) - GantryTimeline.hatch(t - f, full)) < 1e-5f,
					"the lead-in and the outro are mirror images at " + f);
		}
		h.assertTrue(GantryTimeline.lift(0, full) == 0f && GantryTimeline.lift(t / 2f, full) == GantryTimeline.LIFT, "the lift raises 0.5");
		h.assertTrue(GantryTimeline.hatch(0, full) == 0f && GantryTimeline.hatch(t / 2f, full) == 1f, "the hatches open and shut");
		h.assertTrue(GantryTimeline.pose(0f, full)[0] < 0.01f && GantryTimeline.pose(t, full)[0] < 0.01f
				&& GantryTimeline.pose(t / 2f, full)[0] > 0.99f, "the pose eases in and is gone by the end");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void menuListsTheNewestMarkFirst(GameTestHelper h) {
		ServerPlayer p = stark(h);
		floor(h, CENTRE);
		standOn(h, p, CENTRE);
		platform(h, p, PLATFORM, true); // Mark III, nearer
		IronManSuitPlatformBlockEntity newer = platform(h, p, PLATFORM.west(6), false);
		newer.store(new ItemStack(IronManItems.armor("mark_vii", ArmorItem.Type.HELMET)));
		IronManSuitPlatformBlockEntity older = platform(h, p, PLATFORM.north(5), false);
		older.store(new ItemStack(IronManItems.armor("mark_1", ArmorItem.Type.HELMET)));
		java.util.List<StarkGantryMenuPayload.Entry> e = StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p).entries();
		h.assertTrue(e.size() == 3, "three racked suits, got " + e.size());
		h.assertTrue(e.get(0).suitId().equals("mark_vii") && e.get(1).suitId().equals(SUIT) && e.get(2).suitId().equals("mark_1"),
				"newest armour on top: " + e.stream().map(StarkGantryMenuPayload.Entry::suitId).toList());
		leave(h, p);
		h.succeed();
	}
}
