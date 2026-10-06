package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gantry.StarkGantry;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.network.StarkGantryActionPayload;
import com.projecthero.mod.network.StarkGantryMenuPayload;

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
import net.minecraft.world.phys.AABB;

/**
 * v0.15.9: (1) Stark Gantry "Remove Suit / Swap Suit" -- a suited H offers both; Swap takes the worn suit off, sends it
 * home (its platform, else the pack) and puts the picked suit (racked in range or carried) on in one sequence, every
 * piece existing exactly once throughout. (2) A Mark 5 Suitcase docked on a Suit Platform unfolds into the armour on the
 * rack (the Suitcase tab's fold backwards): locked, hidden from calls / the gantry, saved, then a normal racked suit.
 */
public class IronManV0159GameTests implements FabricGameTest {
	private static final String OLD = "mark_iii";
	private static final String NEW = "mark_vii";
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final BlockPos CENTRE = new BlockPos(2, 1, 2);
	/** Where the worn suit goes home to (empty to start with). */
	private static final BlockPos HOME = new BlockPos(6, 2, 6);
	/** Where the suit swapped to is racked. */
	private static final BlockPos OTHER = new BlockPos(1, 2, 6);

	private static ServerPlayer stark(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getInventory().clearContent();
		TonyStark.grant(p);
		return p;
	}

	private static ServerPlayer suited(GameTestHelper h) {
		ServerPlayer p = stark(h);
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), marked(OLD, t));
		}
		TonyStark.setActiveSuit(p, OLD);
		return p;
	}

	private static ItemStack marked(String suit, ArmorItem.Type t) {
		ItemStack s = new ItemStack(IronManItems.armor(suit, t));
		s.set(DataComponents.CUSTOM_NAME, Component.literal("swap-" + suit + "-" + t.getName()));
		return s;
	}

	private static boolean isMarked(ItemStack s, String suit, ArmorItem.Type t) {
		return s.getItem() instanceof IronManArmorItem a && a.getType() == t && a.suitId().equals(suit) && s.has(DataComponents.CUSTOM_NAME)
				&& s.get(DataComponents.CUSTOM_NAME).getString().equals("swap-" + suit + "-" + t.getName());
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer owner, BlockPos rel, String racked) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(owner.getUUID());
		if (racked != null) {
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(be.store(marked(racked, t)), "racked " + racked + " " + t.getName());
			}
		}
		return be;
	}

	private static StarkGantryFloorBlockEntity centre(GameTestHelper h) {
		return (StarkGantryFloorBlockEntity) h.getBlockEntity(CENTRE);
	}

	/** Copies of the marked piece anywhere: worn, carried, racked on {@code racks}, in the floor, dropped nearby. */
	private static int copies(GameTestHelper h, ServerPlayer p, String suit, ArmorItem.Type t, IronManSuitPlatformBlockEntity... racks) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			n += isMarked(p.getItemBySlot(slot), suit, t) ? 1 : 0;
		}
		for (ItemStack s : p.getInventory().items) {
			n += isMarked(s, suit, t) ? 1 : 0;
		}
		for (IronManSuitPlatformBlockEntity r : racks) {
			if (!r.isRemoved()) {
				for (int i = 0; i < 4; i++) {
					n += isMarked(r.getItem(i), suit, t) ? 1 : 0;
				}
			}
		}
		if (h.getBlockEntity(CENTRE) instanceof StarkGantryFloorBlockEntity g) {
			for (int i = 0; i < 4; i++) {
				n += isMarked(g.buffered(i), suit, t) ? 1 : 0;
			}
		}
		AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(8, 8, 8).inflate(0.5);
		for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			n += isMarked(e.getItem(), suit, t) ? 1 : 0;
		}
		return n;
	}

	private static void assertEachOnce(GameTestHelper h, ServerPlayer p, String when, IronManSuitPlatformBlockEntity... racks) {
		for (String suit : new String[] { OLD, NEW }) {
			for (ArmorItem.Type t : TYPES) {
				int c = copies(h, p, suit, t, racks);
				h.assertTrue(c == 1, suit + " " + t.getName() + " must exist exactly once " + when + ", found " + c);
			}
		}
	}

	private static int carried(ServerPlayer p, String suit) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			n += s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suit) ? 1 : 0;
		}
		return n;
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

	private static void send(ServerPlayer p, int action, BlockPos pos, String pack) {
		StarkGantryActionPayload.handleServer(p, new StarkGantryActionPayload(action, pos, pack));
	}

	// ------------------------------------------------------------------ swap from a platform

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1100)
	public void swapRacksTheOldSuitAndPutsThePickedOneOnInOneSequence(GameTestHelper h) {
		ServerPlayer p = suited(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity home = platform(h, p, HOME, null);
		IronManSuitPlatformBlockEntity other = platform(h, p, OTHER, NEW);
		StarkGantryMenuPayload menu = StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p);
		h.assertTrue(menu.wornSuit().equals(OLD) && menu.canRemove(), "suited: Remove Suit is offered");
		h.assertTrue(menu.entries().size() == 1 && menu.entries().get(0).suitId().equals(NEW) && !menu.entries().get(0).pack(),
				"and Swap Suit lists the other racked suit only (never the worn one): " + menu.entries());
		send(p, StarkGantryActionPayload.SWAP, h.absolutePos(OTHER), "");
		StarkGantryFloorBlockEntity g = centre(h);
		h.assertTrue(g.running() && g.mode() == StarkGantryFloorBlockEntity.MODE_UNEQUIP && NEW.equals(g.swapSuit()),
				"the swap starts by taking the worn suit off");
		h.assertTrue(other.isFull(), "the picked suit stays racked until the old one is off");
		int off = g.plan().total();
		int on = GantryTimeline.FULL.total();
		h.onEachTick(() -> assertEachOnce(h, p, "every tick", home, other));
		// the hand-off is seamless: the putting-on picks up at the end of the lead-in, the floor never shuts in between
		int handOff = off - GantryTimeline.LEAD;
		h.runAfterDelay(handOff - 1, () -> h.assertTrue(centre(h).mode() == StarkGantryFloorBlockEntity.MODE_UNEQUIP
				&& centre(h).getBlockState().getValue(com.projecthero.mod.ironman.gantry.StarkGantryFloorBlock.OPEN), "still taking off, floor open"));
		h.runAfterDelay(handOff + 1, () -> {
			StarkGantryFloorBlockEntity c = centre(h);
			h.assertTrue(c.mode() == StarkGantryFloorBlockEntity.MODE_EQUIP && c.getBlockState().getValue(com.projecthero.mod.ironman.gantry.StarkGantryFloorBlock.OPEN),
					"putting on straight away, the floor still open");
			float fr = c.frameAt(0f);
			h.assertTrue(fr >= GantryTimeline.LEAD && fr <= GantryTimeline.LEAD + 3, "starting at the end of its lead-in, got frame " + fr);
			h.assertTrue(GantryTimeline.hatch(fr, c.plan()) == 1f && GantryTimeline.lift(fr, c.plan()) == GantryTimeline.LIFT,
					"hatches open and lift up across the hand-off");
		});
		h.runAfterDelay(handOff + 2, () -> {
			h.assertTrue(centre(h).running() && centre(h).mode() == StarkGantryFloorBlockEntity.MODE_EQUIP,
					"straight on to putting the new suit on -- one continuous sequence");
			h.assertTrue(IronManSuitPlatformBlockEntity.isFrozen(p), "the player is held throughout");
			h.assertTrue(home.isFull() && OLD.equals(home.storedSuitId()), "the old suit is racked on its platform");
			h.assertTrue(other.isEmptyPlatform(), "the new suit left its platform for the floor");
			h.assertFalse(IronManArmor.wearingAnyIronMan(p), "nothing worn between the two halves");
		});
		h.runAfterDelay(off + on - 2 * GantryTimeline.LEAD + 8, () -> {
			assertReleased(h, p);
			h.assertTrue(IronManArmor.wearingFullSuit(p, NEW), "the new suit is on");
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(isMarked(p.getItemBySlot(IronManSuitUpManager.slotFor(t)), NEW, t), "the real " + t.getName() + " is worn");
			}
			h.assertTrue(TonyStark.activeSuitId(p).equals(NEW), "the active suit is the new one");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700)
	public void swapToASuitTakenMeanwhileEndsUnsuitedWithNothingLost(GameTestHelper h) {
		ServerPlayer p = suited(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity home = platform(h, p, HOME, null);
		IronManSuitPlatformBlockEntity other = platform(h, p, OTHER, NEW);
		h.assertTrue(StarkGantry.beginSwap(p, h.absolutePos(OTHER), ""), "the swap starts");
		int off = centre(h).plan().total();
		ItemStack[][] stolen = new ItemStack[1][];
		h.runAfterDelay(40, () -> stolen[0] = other.takeAllPieces()); // someone else's sequence took it meanwhile
		h.runAfterDelay(off + 6, () -> {
			assertReleased(h, p);
			h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the old suit came off and nothing went on");
			h.assertTrue(home.isFull() && OLD.equals(home.storedSuitId()), "the old suit went home");
			h.assertTrue(stolen[0] != null && stolen[0][1].getItem() instanceof IronManArmorItem, "the taken suit was never touched");
			h.assertTrue(TonyStark.activeSuitId(p).isEmpty(), "no suit active");
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ swap from / to the pack

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1100)
	public void swapWithNoRoomSendsTheOldSuitToThePackAndTakesTheNewOneFromIt(GameTestHelper h) {
		ServerPlayer p = suited(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(marked(NEW, t));
		}
		StarkGantryMenuPayload menu = StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p);
		h.assertFalse(menu.canRemove(), "no platform in range: Remove Suit is greyed");
		h.assertTrue(menu.entries().size() == 1 && menu.entries().get(0).pack() && menu.entries().get(0).mask() == 15,
				"but the suit in the pack can be swapped to: " + menu.entries());
		send(p, StarkGantryActionPayload.SWAP, BlockPos.ZERO, NEW);
		h.assertTrue(centre(h).running() && NEW.equals(centre(h).swapSuit()), "the swap starts");
		int off = centre(h).plan().total();
		h.onEachTick(() -> assertEachOnce(h, p, "every tick"));
		h.runAfterDelay(off + 2, () -> {
			h.assertTrue(carried(p, OLD) == 4, "the old suit went into the pack, got " + carried(p, OLD));
			h.assertTrue(carried(p, NEW) == 0, "the new one came out of it");
		});
		h.runAfterDelay(off + GantryTimeline.FULL.total() + 8, () -> {
			assertReleased(h, p);
			h.assertTrue(IronManArmor.wearingFullSuit(p, NEW), "the pack suit is on");
			h.assertTrue(carried(p, OLD) == 4, "the old one is in the pack");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void cancellingASuitUpFromThePackPutsItBackInThePack(GameTestHelper h) {
		ServerPlayer p = stark(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(marked(NEW, t));
		}
		IronManSuitPlatformBlockEntity empty = platform(h, p, HOME, null); // an empty rack must NOT take the pack suit
		StarkGantryMenuPayload menu = StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p);
		h.assertTrue(menu.wornSuit().isEmpty() && menu.entries().size() == 1 && menu.entries().get(0).pack(),
				"unsuited, the pack suit is listed too");
		h.assertTrue(StarkGantry.beginEquip(p, BlockPos.ZERO, NEW), "the gantry suits up out of the pack");
		h.assertTrue(carried(p, NEW) == 0, "the whole suit went into the floor");
		int at = GantryTimeline.FULL.equipTick(GantryTimeline.CHEST) + 3;
		h.runAfterDelay(at, () -> centre(h).abort(p));
		h.runAfterDelay(at + 2, () -> {
			assertReleased(h, p);
			h.assertTrue(carried(p, NEW) == 3 && p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem,
					"chestplate on, the rest back in the pack, got " + carried(p, NEW));
			h.assertTrue(empty.isEmptyPlatform(), "nothing was racked");
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ request validation

	@GameTest(template = EMPTY_STRUCTURE)
	public void swapAndRemoveRequestsAreValidated(GameTestHelper h) {
		ServerPlayer p = suited(h);
		ServerPlayer q = stark(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity home = platform(h, p, HOME, null);
		IronManSuitPlatformBlockEntity theirs = platform(h, q, OTHER, NEW); // another player's rack
		p.getInventory().add(new ItemStack(IronManItems.armor(OLD, ArmorItem.Type.HELMET))); // a spare of the worn mark
		StarkGantryFloorBlockEntity g = centre(h);
		send(p, 99, h.absolutePos(OTHER), "");
		send(p, -1, BlockPos.ZERO, "");
		h.assertFalse(g.running(), "an unknown action does nothing");
		send(p, StarkGantryActionPayload.SWAP, h.absolutePos(OTHER), "");
		h.assertFalse(g.running(), "another player's rack can't be swapped to");
		send(p, StarkGantryActionPayload.SWAP, h.absolutePos(HOME), "");
		h.assertFalse(g.running(), "an empty rack can't be swapped to");
		send(p, StarkGantryActionPayload.SWAP, h.absolutePos(CENTRE).above(40), "");
		h.assertFalse(g.running(), "nor a position with no platform");
		send(p, StarkGantryActionPayload.SWAP, BlockPos.ZERO, "not_a_suit");
		h.assertFalse(g.running(), "nor an unknown pack suit");
		send(p, StarkGantryActionPayload.SWAP, BlockPos.ZERO, NEW);
		h.assertFalse(g.running(), "nor a suit that is not in the pack");
		send(p, StarkGantryActionPayload.SWAP, BlockPos.ZERO, OLD);
		h.assertFalse(g.running(), "nor the suit already worn");
		send(p, StarkGantryActionPayload.EQUIP, h.absolutePos(OTHER), "");
		h.assertFalse(g.running(), "a plain suit-up is refused while suited");
		h.assertTrue(IronManArmor.wearingFullSuit(p, OLD) && theirs.isFull() && home.isEmptyPlatform(), "nothing moved");
		h.assertFalse(StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p).entries().stream()
				.anyMatch(e -> e.suitId().equals(OLD) || e.platform().equals(theirs.getBlockPos())),
				"the swap list never shows the worn mark or someone else's rack");
		// q is not suited: a swap request from them is refused too
		IronManGantryV0154GameTests.standOn(h, q, CENTRE.offset(-2, 0, -2));
		send(q, StarkGantryActionPayload.SWAP, h.absolutePos(OTHER), "");
		h.assertFalse(g.running(), "an unsuited player can't swap");
		// Remove Suit still works as before (no swap attached)
		send(p, StarkGantryActionPayload.UNEQUIP, BlockPos.ZERO, "");
		h.assertTrue(g.running() && g.mode() == StarkGantryFloorBlockEntity.MODE_UNEQUIP && g.swapSuit() == null,
				"Remove Suit takes the suit off and nothing else");
		g.abort(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, OLD), "an immediate abort leaves it on");
		leave(h, p);
		leave(h, q);
		h.succeed();
	}

	/** A Mark 6/7 helmet's highlight never lights a display armour stand (a racked suit used to render as a white blob). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theMark7HighlightNeverLightsARacksDisplayStand(GameTestHelper h) {
		ServerPlayer p = stark(h);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IronManItems.armor(NEW, ArmorItem.Type.HELMET)));
		net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),
				p.getX() + 2, p.getY(), p.getZ());
		h.assertTrue(com.projecthero.mod.ironman.IronManHighlight.decision(p, stand) == null
				&& !com.projecthero.mod.ironman.IronManHighlight.outlines(p, stand), "a display stand is never highlighted");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ Mark 5 suitcase unfolding onto the rack

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aDockedMark5SuitcaseUnfoldsOnTheRackLockedUntilDone(GameTestHelper h) {
		ServerPlayer p = stark(h);
		IronManGantryV0154GameTests.floor(h, CENTRE);
		IronManGantryV0154GameTests.standOn(h, p, CENTRE);
		IronManSuitPlatformBlockEntity be = platform(h, p, HOME, null);
		ItemStack caseStack = new ItemStack(IronManItems.MARK_V_SUITCASE);
		h.assertTrue(be.storeSuitcase(p, caseStack), "the case docks");
		h.assertTrue(be.unpacking() && be.locked() && be.pieceMask() == 15, "it unfolds on the rack, every piece already there");
		h.assertTrue(be.storedSuitId() == null, "hidden from calls / the gantry while it unfolds");
		h.assertTrue(be.removeItem(1, 1).isEmpty() && !be.canPlaceItem(0, new ItemStack(IronManItems.armor("mark_v", ArmorItem.Type.HELMET))),
				"the rack is locked");
		h.assertFalse(be.packSuitcase(p), "and can't be packed again mid-unfold");
		h.assertTrue(StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p).entries().isEmpty(), "the gantry doesn't list it yet");
		h.assertFalse(StarkGantry.beginEquip(p, h.absolutePos(HOME)), "nor put it on");
		// saved mid-unfold and loaded again: still unfolding (a reload finishes it cleanly)
		CompoundTag saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
		IronManSuitPlatformBlockEntity reloaded = new IronManSuitPlatformBlockEntity(be.getBlockPos(), be.getBlockState());
		reloaded.loadWithComponents(saved, h.getLevel().registryAccess());
		h.assertTrue(reloaded.unpacking() && reloaded.pieceMask() == 15, "the unfold is saved with the rack");
		h.runAfterDelay(IronManSuitPlatformBlockEntity.UNPACK_TICKS / 2, () -> h.assertTrue(be.unpacking(), "halfway: still unfolding"));
		h.runAfterDelay(IronManSuitPlatformBlockEntity.UNPACK_TICKS + 3, () -> {
			h.assertFalse(be.unpacking() || be.locked(), "done: unlocked");
			h.assertTrue("mark_v".equals(be.storedSuitId()) && be.isFull(), "a Mark 5 stands on the rack");
			h.assertTrue(StarkGantry.menuFor(h.getLevel(), StarkGantry.centreUnder(p), p).entries().stream()
					.anyMatch(e -> e.suitId().equals("mark_v")), "and the gantry lists it");
			leave(h, p);
			h.succeed();
		});
	}
}
