package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformMenu;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.PortableForm;
import com.projecthero.mod.ironman.suit.PortableForms;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.16.7 (user): the Suit Platform packs a racked Era II suit into its carried form (Mk VI briefcase, Mk VII backpack,
 * Mk VIII thruster pack, Mk IX heat case) and unpacks the packed item back onto the rack, keeping integrity and energy.
 * The Bracelets tab is gone; the Hulkbuster (mark_8) is left to its own Uplink path.
 */
public class IronManPlatformPackGameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final PortableForm[] FORMS = {
			PortableForms.MK6_BRIEFCASE, PortableForms.MK7_BACKPACK, PortableForms.MK8_THRUSTER_PACK, PortableForms.MK9_HEAT_CASE };

	private static ServerPlayer tony(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		net.minecraft.world.phys.Vec3 near = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 2.5));
		p.moveTo(near.x, near.y, near.z); // the packed item is handed to an owner within 24 blocks
		return p;
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer p, int x) {
		BlockPos pos = new BlockPos(x, 2, 2);
		h.setBlock(pos, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(pos);
		be.bindTo(p.getUUID());
		return be;
	}

	/** Rack all four pieces of {@code suit}, each stamped with {@code energy} / {@code integrity}. */
	private static void rack(IronManSuitPlatformBlockEntity be, String suit, float energy, float integrity) {
		for (ArmorItem.Type t : TYPES) {
			ItemStack piece = new ItemStack(IronManItems.armor(suit, t));
			IronManEnergy.stampStack(piece, energy, integrity);
			be.store(piece);
		}
	}

	private static ItemStack find(ServerPlayer p, PortableForm form) {
		for (ItemStack s : p.getInventory().items) {
			if (s.is(form.packedItem().get())) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.01f;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachEraTwoFormPacksFromThePlatform(GameTestHelper h) {
		ServerPlayer p = tony(h);
		int x = 0;
		for (PortableForm form : FORMS) {
			String suit = form.suitId();
			IronManSuitPlatformBlockEntity be = platform(h, p, x);
			x += 2;
			float integ = IronManEnergy.maxIntegrity(suit) * 0.6f;
			float energy = IronManEnergy.capacity(suit) * 0.5f;
			rack(be, suit, energy, integ);
			float wantEnergy = be.suitEnergy(); // what the rack reads (Era II energy is always full unless made real)
			h.assertTrue(near(be.suitIntegrity(), integ), suit + ": racked at 60% integrity");
			IronManSuitPlatformMenu menu = new IronManSuitPlatformMenu(x, p.getInventory(), be, new SimpleContainerData(3));
			h.assertTrue(menu.offersSuitcase() && menu.packForm() == form, suit + ": the pack tab offers its own form");
			h.assertFalse(menu.offersBracelets(), suit + ": no Bracelets tab");
			h.assertTrue(be.packSuitcase(p), suit + ": the fold starts");
			h.assertTrue(be.packing() && be.locked() && be.storedSuitId() == null, suit + ": locked while it folds");
			h.assertTrue(suit.equals(be.rackedSuitId()), suit + ": the renderer still sees which suit is folding");
			be.finishPack(h.getLevel());
			h.assertTrue(be.isEmptyPlatform() && !be.packing(), suit + ": the platform is empty afterwards");
			ItemStack packed = find(p, form);
			h.assertFalse(packed.isEmpty(), suit + ": its " + form.packedItem().get() + " is in the inventory");
			h.assertTrue(SuitcaseContents.nonEmpty(packed).size() == 4, suit + ": with all four pieces inside");
			h.assertTrue(near(IronManEnergy.stackIntegrity(packed, suit), integ), suit + ": integrity stamped on the packed item");
			h.assertTrue(near(IronManEnergy.stackEnergy(packed, suit), wantEnergy), suit + ": energy stamped on the packed item");
			for (ItemStack piece : SuitcaseContents.nonEmpty(packed)) {
				h.assertTrue(near(IronManEnergy.stackIntegrity(piece, suit), integ), suit + ": and on every piece inside");
			}
			if (form.rest() == PortableForm.Place.BACK) {
				h.assertFalse(p.getMainHandItem().is(form.packedItem().get()), suit + ": a backpack goes on the back, not the hand");
			}
		}
		h.getLevel().getServer().getPlayerList().remove(p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void packedItemsUnpackBackOntoThePlatform(GameTestHelper h) {
		ServerPlayer p = tony(h);
		int x = 0;
		for (PortableForm form : FORMS) {
			String suit = form.suitId();
			IronManSuitPlatformBlockEntity src = platform(h, p, x);
			IronManSuitPlatformBlockEntity dst = platform(h, p, x + 1);
			x += 2;
			float integ = IronManEnergy.maxIntegrity(suit) * 0.45f;
			rack(src, suit, IronManEnergy.capacity(suit) * 0.3f, integ);
			float wantEnergy = src.suitEnergy();
			src.packSuitcase(p);
			src.finishPack(h.getLevel());
			ItemStack packed = find(p, form);
			h.assertTrue(dst.storeSuitcase(p, packed), suit + ": the packed item docks on an empty platform");
			h.assertTrue(packed.isEmpty(), suit + ": and is used up");
			h.assertTrue(dst.pieceMask() == 15 && dst.unpacking() && dst.locked(), suit + ": all four racked, unfolding");
			dst.finishUnpack();
			h.assertTrue(suit.equals(dst.storedSuitId()) && !dst.locked(), suit + ": racked again once unfolded");
			h.assertTrue(near(dst.suitIntegrity(), integ), suit + ": integrity preserved through the round trip");
			h.assertTrue(near(dst.suitEnergy(), wantEnergy), suit + ": energy preserved through the round trip");
		}
		h.getLevel().getServer().getPlayerList().remove(p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dockingRules(GameTestHelper h) {
		ServerPlayer p = tony(h);
		// the packed item's own stamp wins over the pieces' (a deploy loads the suit from it)
		IronManSuitPlatformBlockEntity a = platform(h, p, 0);
		ItemStack heat = new ItemStack(IronManItems.MARK_9_HEAT_CASE);
		net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (ArmorItem.Type t : TYPES) {
			slots.set(SuitcaseContents.slotOf(t), new ItemStack(IronManItems.armor("mark_9", t)));
		}
		SuitcaseContents.write(heat, slots);
		float stamp = IronManEnergy.maxIntegrity("mark_9") * 0.25f;
		IronManSuitPlatformBlockEntity.stampPacked(heat, "mark_9", IronManEnergy.capacity("mark_9"), stamp);
		h.assertTrue(a.storeSuitcase(p, heat), "a heat case docks");
		a.finishUnpack();
		h.assertTrue(near(a.suitIntegrity(), stamp), "the racked Mk IX takes the case's integrity");
		// a full / other-suit platform refuses, and nothing is lost
		ItemStack pack = new ItemStack(IronManItems.CENTURION_BACKPACK); // never used = a fresh suit
		h.assertFalse(a.storeSuitcase(p, pack), "a platform holding another suit refuses the backpack");
		h.assertTrue(pack.getCount() == 1, "and the backpack is kept");
		IronManSuitPlatformBlockEntity b = platform(h, p, 2);
		h.assertTrue(b.storeSuitcase(p, pack), "a never-used backpack racks a fresh Mk VII");
		b.finishUnpack();
		h.assertTrue("mark_6".equals(b.storedSuitId()) && b.pieceMask() == 15, "all four Mk VII pieces");
		// an emptied pack (its suit is out) racks nothing
		ItemStack emptied = new ItemStack(IronManItems.SPACE_THRUSTER_PACK);
		SuitcaseContents.write(emptied, net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		IronManSuitPlatformBlockEntity c = platform(h, p, 4);
		h.assertFalse(c.storeSuitcase(p, emptied) || !c.isEmptyPlatform(), "an emptied thruster pack racks nothing");
		// shift-click in the platform screen docks it too (hotbar slot 0 = menu slot 31)
		ItemStack thr = new ItemStack(IronManItems.SPACE_THRUSTER_PACK);
		p.getInventory().items.set(0, thr);
		IronManSuitPlatformMenu menu = new IronManSuitPlatformMenu(9, p.getInventory(), c, new SimpleContainerData(3));
		menu.quickMoveStack(p, 31);
		h.assertTrue(c.pieceMask() == 15 && c.unpacking(), "shift-clicking the thruster pack racks the Mk VIII");
		h.assertTrue(p.getInventory().items.get(0).isEmpty(), "and uses it up");
		h.getLevel().getServer().getPlayerList().remove(p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noBraceletsAndHulkbusterLeftAlone(GameTestHelper h) {
		ServerPlayer p = tony(h);
		IronManSuitPlatformBlockEntity mk7 = platform(h, p, 0);
		rack(mk7, "mark_6", IronManEnergy.capacity("mark_6"), IronManEnergy.maxIntegrity("mark_6"));
		IronManSuitPlatformMenu menu = new IronManSuitPlatformMenu(1, p.getInventory(), mk7, new SimpleContainerData(3));
		h.assertFalse(menu.offersBracelets(), "no Bracelets tab for the Mk VII Silver Centurion");
		h.assertFalse(menu.clickMenuButton(p, IronManSuitPlatformMenu.BRACELETS_BUTTON), "the retired button does nothing");
		h.assertTrue(p.getInventory().countItem(IronManItems.COLANTOTTE_BRACELETS) == 0, "no bracelets handed out");
		IronManSuitPlatformBlockEntity mk8 = platform(h, p, 2);
		rack(mk8, "mark_vii", IronManEnergy.capacity("mark_vii"), IronManEnergy.maxIntegrity("mark_vii"));
		menu = new IronManSuitPlatformMenu(2, p.getInventory(), mk8, new SimpleContainerData(3));
		h.assertFalse(menu.offersBracelets(), "no Bracelets tab for the Mk VIII Space (the old Mark 7) either");
		h.assertTrue(menu.packForm() == PortableForms.MK8_THRUSTER_PACK, "it packs into its thruster pack instead");
		// the Hulkbuster has its own Uplink path -- the pack tab never offers it
		IronManSuitPlatformBlockEntity hb = platform(h, p, 4);
		rack(hb, "mark_8", IronManEnergy.capacity("mark_8"), IronManEnergy.maxIntegrity("mark_8"));
		menu = new IronManSuitPlatformMenu(3, p.getInventory(), hb, new SimpleContainerData(3));
		h.assertFalse(menu.offersSuitcase(), "no pack tab for the Hulkbuster");
		h.assertFalse(hb.packSuitcase(p), "and the platform's pack refuses it");
		IronManSuitPlatformBlockEntity empty = platform(h, p, 6);
		h.assertFalse(empty.storeSuitcase(p, new ItemStack(IronManItems.HULKBUSTER_UPLINK)), "nor does this dock an Uplink");
		h.getLevel().getServer().getPlayerList().remove(p);
		h.succeed();
	}

	/** Through the screen's button: the fold plays out on the platform tick and hands the heat case over. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void packButtonFoldFinishesOnItsOwn(GameTestHelper h) {
		ServerPlayer p = tony(h);
		IronManSuitPlatformBlockEntity be = platform(h, p, 2);
		rack(be, "mark_9", IronManEnergy.capacity("mark_9"), IronManEnergy.maxIntegrity("mark_9"));
		IronManSuitPlatformMenu menu = new IronManSuitPlatformMenu(1, p.getInventory(), be, new SimpleContainerData(3));
		h.assertTrue(menu.clickMenuButton(p, IronManSuitPlatformMenu.SUITCASE_BUTTON), "the pack button starts the fold");
		h.assertFalse(menu.offersSuitcase(), "the tab hides while it folds");
		h.runAfterDelay(IronManSuitPlatformBlockEntity.PACK_TICKS / 2, () -> h.assertTrue(be.packing() && be.pieceMask() == 15,
				"halfway: still folding, every piece still racked"));
		h.runAfterDelay(IronManSuitPlatformBlockEntity.PACK_TICKS + 5, () -> {
			h.assertTrue(!be.packing() && be.isEmptyPlatform(), "done: the rack is empty");
			h.assertTrue(p.getInventory().countItem(IronManItems.MARK_9_HEAT_CASE) == 1, "and the heat case is in the inventory");
			h.getLevel().getServer().getPlayerList().remove(p);
			h.succeed();
		});
	}
}
