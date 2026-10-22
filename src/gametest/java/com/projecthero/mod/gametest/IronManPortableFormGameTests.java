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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** v0.16.7 (user) Iron Man Era II stage 2: the portable-form framework, proven on the Mk VI briefcase. */
public class IronManPortableFormGameTests implements FabricGameTest {
	private static final PortableForm MK6 = PortableForms.MK6_BRIEFCASE;
	private static final String SUIT = MK6.suitId();
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

	private static ItemStack packedCase(float integrity) {
		ItemStack c = new ItemStack(IronManItems.MARK_V_SUITCASE);
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int bit = 0; bit < 4; bit++) {
			slots.set(bit, new ItemStack(IronManItems.armor(SUIT, TYPES[bit])));
		}
		SuitcaseContents.write(c, slots);
		IronManEnergy.stampStack(c, 0f, integrity);
		return c;
	}

	private static void wear(ServerPlayer p) {
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(SUIT, t)));
		}
		TonyStark.setActiveSuit(p, SUIT);
	}

	private static int run(ServerPlayer p) {
		int n = 0;
		for (; n < 400 && IronManSuitUpManager.inTransition(p); n++) {
			IronManSuitUpManager.tick(p);
		}
		return n;
	}

	private static int count(ServerPlayer p) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(IronManItems.MARK_V_SUITCASE)) {
				n++;
			}
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void registryAndTimetable(GameTestHelper h) {
		h.assertTrue(PortableForms.byId(SUIT) == MK6 && PortableForms.byItem(IronManItems.MARK_V_SUITCASE) == MK6,
				"the Mk VI is registered by suit and by item");
		h.assertTrue(PortableForms.byId("mark_iii") == null, "an Era I suit has no portable form");
		int up = PortableForms.poseKind(MK6, true), down = PortableForms.poseKind(MK6, false);
		h.assertTrue(PortableForms.byPoseKind(up) == MK6 && PortableForms.byPoseKind(down) == MK6
				&& PortableForms.deployPose(up) && PortableForms.foldPose(down) && !PortableForms.deployPose(down),
				"the pose kinds carry the form and the direction");
		h.assertTrue(PortableForms.byPoseKind(IronManSuitFx.POSE_MK5_UP) == null, "old pose kinds are not portable");
		// boots first, then legs, then arms + chest, then the helmet, the faceplate last
		h.assertTrue(MK6.pieceOnAt(3) < MK6.pieceOnAt(2) && MK6.pieceOnAt(2) < MK6.pieceOnAt(1)
				&& MK6.pieceOnAt(1) < MK6.pieceOnAt(0), "piece order: boots, legs, chest, helmet");
		h.assertTrue(MK6.pieceOnAt(3) >= MK6.beats().dropTo(), "nothing goes on before the case has landed");
		h.assertTrue(MK6.appear(GantryTimeline.FACEPLATE, 1f) <= MK6.beats().sealAt()
				&& MK6.beats().sealAt() < MK6.beats().total(), "the faceplate is built before it seals, inside the clock");
		for (int st = 0; st < GantryTimeline.STAGES; st++) {
			h.assertTrue(MK6.uses(st), "the Mk VI uses every gantry stage (" + st + ")");
			h.assertTrue(MK6.pieceOnAt(PortableForm.bitOf(st)) <= MK6.appear(st, 0f), "slot before texels, stage " + st);
		}
		h.assertTrue(MK6.beats().total() >= 60 && MK6.beats().total() <= 80, "roughly 3 s");
		h.assertTrue(MK6.frame(false, 10f) == MK6.beats().total() - 10f && MK6.frame(true, 10f) == 10f, "the fold runs backwards");
		for (float f : new float[] { 3f, 14f, 30f, 45f, 58f, 68f }) {
			float[] a = MK6.pose(f);
			h.assertTrue(a != null && a.length == 13, "a pose at frame " + f);
		}
		h.assertTrue(MK6.pose(40f)[3] > 0.6f, "arms out while the plates climb them");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cDeploysTheBriefcaseBootsFirst(GameTestHelper h) {
		ServerPlayer p = player(h);
		p.getInventory().selected = 0;
		p.getInventory().items.set(0, packedCase(777f));
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true); // plain C, unsuited
		h.assertTrue(PortableDeploy.running(p) && IronManSuitUpManager.assembling(p), "C starts the deploy");
		h.assertTrue(IronManSuitUpManager.blockedWhileAssembling(p, false), "abilities locked while it deploys");
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		h.assertTrue(speed != null && speed.getValue() < 1e-6, "held still while it deploys");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.portable() && fx.poseKind() == PortableForms.poseKind(MK6, true), "the synced deploy clock");
		for (int i = 0; i < MK6.pieceOnAt(3); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.FEET, SUIT) && p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),
				"the boots first");
		h.assertTrue(SuitcaseContents.nonEmpty(p.getInventory().items.get(0)).size() == 3, "out of the case, not conjured");
		for (int i = MK6.pieceOnAt(3); i < MK6.pieceOnAt(0); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT) && p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false),
				"all four on, the helmet with its faceplate up");
		run(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the whole Mk VI is on after the timeline");
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false), "the faceplate sealed");
		h.assertTrue(count(p) == 0, "the case is used up");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, SUIT) - 777f) < 1f, "the case's integrity carried onto the suit");
		h.assertFalse(IronManSuitFx.of(p).portable(), "the pose clock ended");
		var speed2 = p.getAttribute(Attributes.MOVEMENT_SPEED);
		h.assertTrue(speed2 != null && speed2.getValue() > 0.01, "free to move again");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdShiftCFoldsBackIntoTheHand(GameTestHelper h) {
		ServerPlayer p = player(h);
		wear(p);
		IronManEnergy.setIntegrity(p, SUIT, 640f);
		p.getInventory().selected = 4;
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		long t0 = p.level().getGameTime();
		PortableDeploy.holdTick(p, t0 + PortableDeploy.FOLD_HOLD_TICKS - 1);
		h.assertFalse(IronManSuitUpManager.inTransition(p), "not before the hold has lasted");
		PortableDeploy.holdTick(p, t0 + PortableDeploy.FOLD_HOLD_TICKS);
		h.assertTrue(PortableDeploy.running(p) && !IronManSuitUpManager.assembling(p), "the fold runs");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == PortableForms.poseKind(MK6, false), "the synced fold clock");
		p.setShiftKeyDown(false);
		IronManSuitUpManager.tick(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the pieces stay on while it folds");
		run(p);
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "folded away");
		ItemStack c = p.getInventory().items.get(4);
		h.assertTrue(c.is(IronManItems.MARK_V_SUITCASE), "the case lands in the empty main hand");
		h.assertTrue(SuitcaseContents.nonEmpty(c).size() == 4, "holding all four pieces");
		h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(c, SUIT) - 640f) < 1f, "and the suit's integrity");
		for (ItemStack s : p.getInventory().items) {
			h.assertFalse(s.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem, "no loose pieces");
		}
		// and back on again: the same integrity
		IronManEnergy.setIntegrity(p, SUIT, 1f);
		h.assertTrue(PortableDeploy.deploy(p, SUIT), "deploys again");
		run(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT) && Math.abs(IronManEnergy.integrity(p, SUIT) - 640f) < 1f,
				"round trip keeps the integrity");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void busyHandGoesToTheInventory(GameTestHelper h) {
		ServerPlayer p = player(h);
		wear(p);
		p.getInventory().selected = 0;
		for (int i = 0; i < 9; i++) {
			p.getInventory().items.set(i, new ItemStack(Items.STONE));
		}
		h.assertTrue(PortableDeploy.fold(p, SUIT), "fold");
		run(p);
		h.assertTrue(p.getInventory().items.get(9).is(IronManItems.MARK_V_SUITCASE), "busy hotbar: the first inventory slot");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void refusesWhenItShould(GameTestHelper h) {
		ServerPlayer p = player(h);
		h.assertFalse(PortableDeploy.deployFromC(p), "no case, no deploy");
		ItemStack empty = new ItemStack(IronManItems.MARK_V_SUITCASE);
		SuitcaseContents.write(empty, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		p.getInventory().items.set(0, empty);
		h.assertFalse(PortableDeploy.deploy(p, SUIT), "an emptied case never conjures a suit");
		p.getInventory().items.set(0, packedCase(500f));
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor("mark_6", t)));
		}
		h.assertFalse(PortableDeploy.deploy(p, SUIT), "not while another suit is worn");
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), ItemStack.EMPTY);
		}
		h.assertFalse(PortableDeploy.fold(p, SUIT), "nothing worn, nothing to fold");
		ServerPlayer q = player(h);
		TonyStark.revoke(q);
		q.getInventory().items.set(0, packedCase(500f));
		h.assertFalse(PortableDeploy.deploy(q, SUIT), "no Tony Stark power, no deploy");
		h.assertTrue(PortableDeploy.deploy(p, SUIT), "the packed case deploys");
		h.assertFalse(PortableDeploy.deploy(p, SUIT), "not twice at once");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void logoutAndDeathMidSequenceLoseNothing(GameTestHelper h) {
		// logout mid-deploy: everything goes on at once, the case is used up
		ServerPlayer p = player(h);
		p.getInventory().items.set(0, packedCase(500f));
		PortableDeploy.deploy(p, SUIT);
		for (int i = 0; i < MK6.pieceOnAt(2) + 1; i++) {
			IronManSuitUpManager.tick(p);
		}
		PortableDeploy.completeNow(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT) && count(p) == 0 && !PortableDeploy.running(p),
				"saved fully suited");
		// logout mid-fold: everything goes into the case at once
		PortableDeploy.fold(p, SUIT);
		for (int i = 0; i < 20; i++) {
			IronManSuitUpManager.tick(p);
		}
		PortableDeploy.completeNow(p);
		h.assertTrue(!IronManArmor.wearingAnyIronMan(p) && count(p) == 1 && !PortableDeploy.running(p), "saved fully packed");
		// death mid-fold: it just stops -- the pieces are still worn (they drop as armour)
		p.getInventory().clearContent();
		wear(p);
		PortableDeploy.fold(p, SUIT);
		IronManSuitUpManager.tick(p);
		PortableDeploy.abort(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, SUIT) && count(p) == 0 && !PortableDeploy.running(p),
				"no case made, nothing lost");
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		h.assertTrue(speed != null && speed.getValue() > 0.01, "the movement lock is gone");
		h.succeed();
	}
}
