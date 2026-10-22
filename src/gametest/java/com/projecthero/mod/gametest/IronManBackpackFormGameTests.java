package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.PortableDeploy;
import com.projecthero.mod.ironman.suit.PortableForm;
import com.projecthero.mod.ironman.suit.PortableForms;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the Mk VII Silver Centurion backpack and the Mk VIII Space thruster pack --
 * worn on the back while carried, C deploys chest first, hold Shift+C folds back into the pack on the back, the Mk VIII's
 * O2 tanks stay on the suit, plain C keeps each suit's old store.
 */
public class IronManBackpackFormGameTests implements FabricGameTest {
	private static final PortableForm MK7 = PortableForms.MK7_BACKPACK;
	private static final PortableForm MK8 = PortableForms.MK8_THRUSTER_PACK;
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.getInventory().clearContent();
		return p;
	}

	private static ItemStack pack(PortableForm form, float integrity) {
		ItemStack c = new ItemStack(form.packedItem().get());
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int bit = 0; bit < 4; bit++) {
			slots.set(bit, new ItemStack(IronManItems.armor(form.suitId(), TYPES[bit])));
		}
		SuitcaseContents.write(c, slots);
		IronManEnergy.stampStack(c, 0f, integrity);
		return c;
	}

	private static void wear(ServerPlayer p, String suit) {
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(suit, t)));
		}
		TonyStark.setActiveSuit(p, suit);
	}

	private static int run(ServerPlayer p) {
		int n = 0;
		for (; n < 400 && IronManSuitUpManager.inTransition(p); n++) {
			IronManSuitUpManager.tick(p);
			PortableDeploy.backTick(p);
		}
		return n;
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(item)) {
				n++;
			}
		}
		return n;
	}

	private static String back(ServerPlayer p) {
		PortableDeploy.backTick(p);
		return p.getAttachedOrElse(ModAttachments.IRON_MAN_BACK_PACK, "");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void registryAndChestFirstOrder(GameTestHelper h) {
		for (PortableForm f : new PortableForm[] { MK7, MK8 }) {
			h.assertTrue(PortableForms.byId(f.suitId()) == f && PortableForms.byItem(f.packedItem().get()) == f,
					"registered by suit and by item: " + f.suitId());
			h.assertTrue(f.rest() == PortableForm.Place.BACK && f.during() == PortableForm.Place.BACK, "worn on the back");
			int up = PortableForms.poseKind(f, true);
			h.assertTrue(PortableForms.byPoseKind(up) == f && PortableForms.deployPose(up)
					&& PortableForms.foldPose(PortableForms.poseKind(f, false)), "own pose kinds");
			// chest first, then the legs, the boots, the helmet last
			h.assertTrue(f.pieceOnAt(1) < f.pieceOnAt(2) && f.pieceOnAt(2) < f.pieceOnAt(3) && f.pieceOnAt(3) < f.pieceOnAt(0),
					"piece order: chest, legs, boots, helmet");
			h.assertTrue(f.pieceOnAt(1) >= f.beats().openTo(), "nothing goes on before the shell is open");
			h.assertTrue(f.appear(GantryTimeline.FACEPLATE, 1f) <= f.beats().sealAt() && f.beats().sealAt() < f.beats().total(),
					"the faceplate is built before it seals");
			for (int st = 0; st < GantryTimeline.STAGES; st++) {
				h.assertTrue(f.uses(st), "every gantry stage is used (" + st + ")");
				h.assertTrue(f.pieceOnAt(PortableForm.bitOf(st)) <= f.appear(st, 0f), "slot before texels, stage " + st);
			}
			h.assertTrue(f.direction() == PortableForm.Direction.DROP, "it wraps down the body");
			h.assertTrue(f.pose(24f)[3] > 0.9f && f.pose(24f)[6] < -0.9f, "arms out while the plates wrap them");
			h.assertTrue(f.pose(f.beats().total() - 1f)[0] < 0.5f, "settled at the end");
		}
		h.assertTrue(PortableForms.keepsTanks(MK8) && !PortableForms.keepsTanks(MK7), "only the Mk VIII keeps its tanks");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carriedPackIsWornOnTheBack(GameTestHelper h) {
		ServerPlayer p = player(h);
		h.assertTrue(back(p).isEmpty(), "nothing on the back");
		p.getInventory().items.set(20, pack(MK7, 900f));
		h.assertTrue("mark_6".equals(back(p)), "a pack anywhere in the inventory is on the back");
		p.getInventory().items.set(20, ItemStack.EMPTY);
		p.getInventory().items.set(3, pack(MK8, 900f));
		h.assertTrue("mark_vii".equals(back(p)), "the thruster pack too");
		ItemStack empty = new ItemStack(IronManItems.CENTURION_BACKPACK);
		SuitcaseContents.write(empty, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		p.getInventory().items.set(3, empty);
		h.assertTrue(back(p).isEmpty(), "an emptied pack is not drawn");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cDeploysTheMk7ChestFirst(GameTestHelper h) {
		ServerPlayer p = player(h);
		String suit = MK7.suitId();
		p.getInventory().selected = 0;
		p.getInventory().items.set(14, pack(MK7, 1500f)); // carried in the inventory = on the back
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // plain C, unsuited
		h.assertTrue(PortableDeploy.running(p) && IronManSuitUpManager.assembling(p), "C starts the deploy");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.portable() && fx.poseKind() == PortableForms.poseKind(MK7, true), "the synced deploy clock");
		for (int i = 0; i < MK7.pieceOnAt(1); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.CHEST, suit) && p.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
				&& p.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "the chest first");
		h.assertTrue(SuitcaseContents.nonEmpty(p.getInventory().items.get(14)).size() == 3, "out of the pack, not conjured");
		run(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, suit), "the whole Mk VII is on");
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false), "the faceplate sealed");
		h.assertTrue(count(p, IronManItems.CENTURION_BACKPACK) == 0, "the pack is used up");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, suit) - 1500f) < 1f, "the pack's integrity carried onto the suit");
		h.assertTrue(back(p).isEmpty(), "nothing left on the back");
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_PACK_TANKS, false), "the Mk VII has no tanks");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdShiftCFoldsIntoThePackOnTheBack(GameTestHelper h) {
		ServerPlayer p = player(h);
		String suit = MK7.suitId();
		wear(p, suit);
		IronManEnergy.setIntegrity(p, suit, 1234f);
		p.getInventory().selected = 0;
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		long t0 = p.level().getGameTime();
		PortableDeploy.holdTick(p, t0 + PortableDeploy.FOLD_HOLD_TICKS - 1);
		h.assertFalse(IronManSuitUpManager.inTransition(p), "not before the hold has lasted");
		PortableDeploy.holdTick(p, t0 + PortableDeploy.FOLD_HOLD_TICKS);
		h.assertTrue(PortableDeploy.running(p) && IronManSuitFx.of(p).poseKind() == PortableForms.poseKind(MK7, false),
				"the fold runs on its synced clock");
		p.setShiftKeyDown(false);
		run(p);
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "folded away");
		h.assertTrue(p.getInventory().items.get(0).isEmpty(), "not into the hand: a pack goes on the back");
		ItemStack c = p.getInventory().items.get(9);
		h.assertTrue(c.is(IronManItems.CENTURION_BACKPACK) && SuitcaseContents.nonEmpty(c).size() == 4,
				"the pack (the first inventory slot) holds all four pieces");
		h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(c, suit) - 1234f) < 1f, "and the suit's integrity");
		h.assertTrue("mark_6".equals(back(p)), "worn on the back");
		for (ItemStack s : p.getInventory().items) {
			h.assertFalse(s.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem, "no loose pieces");
		}
		IronManEnergy.setIntegrity(p, suit, 1f);
		h.assertTrue(PortableDeploy.deployFromC(p), "C deploys it again from the back");
		run(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, suit) && Math.abs(IronManEnergy.integrity(p, suit) - 1234f) < 1f,
				"round trip keeps the integrity");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk8TanksStayOnTheSuit(GameTestHelper h) {
		ServerPlayer p = player(h);
		String suit = MK8.suitId();
		p.getInventory().items.set(30, pack(MK8, 2000f));
		h.assertTrue(PortableDeploy.deploy(p, suit), "the thruster pack deploys");
		IronManSuitUpManager.tick(p);
		PortableDeploy.backTick(p);
		h.assertTrue(p.getAttachedOrElse(ModAttachments.IRON_MAN_PACK_TANKS, false), "the tanks are kept from the start");
		run(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, suit) && count(p, IronManItems.SPACE_THRUSTER_PACK) == 0, "Mk VIII on");
		PortableDeploy.backTick(p);
		h.assertTrue(p.getAttachedOrElse(ModAttachments.IRON_MAN_PACK_TANKS, false), "the O2 tanks stay on the suit");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, suit) - 2000f) < 1f, "integrity carried");
		h.assertTrue(PortableDeploy.fold(p, suit), "folds");
		run(p);
		PortableDeploy.backTick(p);
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_PACK_TANKS, false), "the tanks went back into the pack");
		h.assertTrue(count(p, IronManItems.SPACE_THRUSTER_PACK) == 1 && "mark_vii".equals(back(p)), "packed on the back");
		// a Mk VIII put on any other way has no tanks
		p.getInventory().clearContent();
		wear(p, suit);
		PortableDeploy.backTick(p);
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_PACK_TANKS, false), "no tanks without the pack");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void refusalsAndTheOldStoreStays(GameTestHelper h) {
		ServerPlayer p = player(h);
		h.assertFalse(PortableDeploy.deployFromC(p), "no pack, no deploy");
		ItemStack empty = new ItemStack(IronManItems.SPACE_THRUSTER_PACK);
		SuitcaseContents.write(empty, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		p.getInventory().items.set(10, empty);
		h.assertFalse(PortableDeploy.deploy(p, MK8.suitId()), "an emptied pack never conjures a suit");
		p.getInventory().items.set(10, pack(MK8, 500f));
		wear(p, MK7.suitId());
		h.assertFalse(PortableDeploy.deploy(p, MK8.suitId()), "not while another suit is worn");
		// plain C on the worn Mk VII keeps its old store -- it never packs it into a backpack
		p.getInventory().items.set(10, ItemStack.EMPTY);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		h.assertFalse(PortableDeploy.running(p), "plain C is not the fold");
		run(p);
		h.assertTrue(count(p, IronManItems.CENTURION_BACKPACK) == 0, "and makes no pack");
		ServerPlayer q = player(h);
		TonyStark.revoke(q);
		q.getInventory().items.set(10, pack(MK7, 500f));
		h.assertFalse(PortableDeploy.deploy(q, MK7.suitId()), "no Tony Stark power, no deploy");
		// a crafted / creative pack (never held a suit) stands for a fresh suit
		ServerPlayer r = player(h);
		r.getInventory().items.set(10, new ItemStack(IronManItems.CENTURION_BACKPACK));
		h.assertTrue(PortableDeploy.deploy(r, MK7.suitId()), "a fresh pack deploys");
		h.assertFalse(PortableDeploy.deploy(r, MK7.suitId()), "not twice at once");
		run(r);
		h.assertTrue(IronManArmor.wearingFullSuit(r, MK7.suitId()), "a whole Mk VII");
		h.succeed();
	}
}
