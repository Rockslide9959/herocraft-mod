package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gear.ColantotteBracelets;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitRemoval;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.15.15 (Iron Man suit-ups, menus, energy): the C removals (Mark 1 by hand, Marks 2-7 retracting into the reactor), the
 * Stark Glasses-only call picker listing Mark 8+, the gantry faceplate timing the HUD / highlight follow, and the Mark 7's
 * 10% call cost.
 */
public class IronManV01515GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static ServerPlayer tony(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	/** Put {@code suit} straight on (no sequence). */
	private static void wear(ServerPlayer p, String suit) {
		for (int bit = 0; bit < 4; bit++) {
			p.setItemSlot(SLOTS[bit], new ItemStack(IronManItems.armor(suit, TYPES[bit])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setEnergy(p, suit, IronManSuits.byId(suit).energyCapacity() * 0.5f);
	}

	private static int count(ServerPlayer p, String suit) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suit)) {
				n += s.getCount();
			}
		}
		for (EquipmentSlot s : SLOTS) {
			if (p.getItemBySlot(s).getItem() instanceof IronManArmorItem a && a.suitId().equals(suit)) {
				n++;
			}
		}
		return n;
	}

	private static boolean frozen(ServerPlayer p) {
		var inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
		return inst != null && inst.hasModifier(IronManSuitPlatformBlockEntity.FREEZE_ID);
	}

	/** Runs a removal to the end; returns the tick each piece (bit) left the body. */
	private static int[] runRemoval(GameTestHelper h, ServerPlayer p, String suit) {
		int[] off = { -1, -1, -1, -1 };
		int ticks = 0;
		while (IronManSuitUpManager.inTransition(p) && ticks < 2000) {
			IronManSuitUpManager.tick(p);
			ticks++;
			h.assertTrue(count(p, suit) == 4, "no piece lost or duplicated at tick " + ticks);
			for (int bit = 0; bit < 4; bit++) {
				if (off[bit] < 0 && p.getItemBySlot(SLOTS[bit]).isEmpty()) {
					off[bit] = ticks;
				}
			}
			if (IronManSuitUpManager.inTransition(p)) {
				h.assertTrue(frozen(p), "held still (FOV held client-side) at tick " + ticks);
			}
		}
		return off;
	}

	// ------------------------------------------------------------------ C removals

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void markOneComesOffByHandPieceByPiece(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_1");
		h.assertTrue(IronManSuitRemoval.kindFor("mark_1") == IronManSuitRemoval.KIND_MK1, "the Mark 1 is pulled off by hand");
		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_1"), "C starts the removal");
		h.assertTrue(IronManSuitRemoval.running(p) && !IronManManualSuitUp.running(p), "a removal, not a hand build");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.poseKind() == IronManSuitFx.POSE_MK1_OFF && fx.style() == IronManSuitFx.STYLE_MK1_OFF,
				"the Mark 1 removal pose + style (70) are synced");
		var sch = IronManSuitRemoval.schedule(IronManSuitRemoval.KIND_MK1, 0b1111);
		h.assertTrue(sch.offAt(0) < sch.offAt(1) && sch.offAt(1) < sch.offAt(2) && sch.offAt(2) < sch.offAt(3),
				"helmet, chestplate, greaves, boots -- one after another");
		for (int bit = 0; bit < 4; bit++) {
			h.assertTrue(sch.dropAt(bit) > sch.offAt(bit), "piece " + bit + " is held a moment, then dropped");
		}
		int[] off = runRemoval(h, p, "mark_1");
		for (int bit = 0; bit < 4; bit++) {
			h.assertTrue(off[bit] == sch.offAt(bit), "piece " + bit + " leaves at its pull tick (" + off[bit] + " vs "
					+ sch.offAt(bit) + ")");
		}
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is off");
		h.assertFalse(frozen(p), "free to move again");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_NONE, "the pose clock is cleared");
		for (int i = 0; i < 9; i++) {
			h.assertFalse(p.getInventory().items.get(i).getItem() instanceof IronManArmorItem, "nothing lands in the hotbar");
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void marksTwoToSevenRetractIntoTheReactor(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_iii");
		for (String id : new String[] { "mark_2", "mark_iii", "mark_4", "mark_6", "mark_vii" }) {
			h.assertTrue(IronManSuitRemoval.kindFor(id) == IronManSuitRemoval.KIND_SLEEK, id + " retracts");
		}
		h.assertTrue(IronManSuitRemoval.kindFor("mark_v") < 0 && IronManSuitRemoval.kindFor("mark_8") < 0,
				"the Mark 5 keeps its suitcase fold, the Mark 8 its reverse build");
		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_iii"), "C starts the removal");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.poseKind() == IronManSuitFx.POSE_SLEEK_OFF && fx.style() == IronManSuitFx.STYLE_SLEEK_OFF,
				"the retract pose + style (71) are synced");
		int[] off = runRemoval(h, p, "mark_iii");
		h.assertTrue(off[3] < off[2] && off[2] <= off[0] && off[0] < off[1],
				"boots first, then legs, helmet, and the chestplate last into the reactor (" + off[0] + ", " + off[1] + ", "
						+ off[2] + ", " + off[3] + ")");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is off");
		h.assertTrue(IronManEnergy.stackEnergy(findInPack(p, "mark_iii", ArmorItem.Type.CHESTPLATE), "mark_iii")
				> IronManSuits.byId("mark_iii").energyCapacity() * 0.45f, "its charge is stamped on the stored pieces");
		// the wave itself: far from the reactor first, the torso last
		h.assertTrue(IronManSuitRemoval.vanishAt(IronManSuitRemoval.R_BOOTS, 2f, 1f, 0f, 0.5f)
				< IronManSuitRemoval.vanishAt(IronManSuitRemoval.R_TORSO, 0f, 20f, -2f, 0.5f), "boot panels before the reactor");
		h.assertTrue(IronManSuitRemoval.vanishAt(IronManSuitRemoval.R_ARMS, 6f, 12f, 0f, 0.5f)
				< IronManSuitRemoval.vanishAt(IronManSuitRemoval.R_ARMS, 6f, 23f, 0f, 0.5f), "hands before shoulders");
		h.assertTrue(IronManSuitRemoval.regionGone(IronManSuitRemoval.R_LEGS, off[2])
				&& !IronManSuitRemoval.regionGone(IronManSuitRemoval.R_TORSO, off[2]), "legs bare while the torso is still on");
		h.succeed();
	}

	private static ItemStack findInPack(ServerPlayer p, String suit, ArmorItem.Type type) {
		for (ItemStack s : p.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suit) && a.getType() == type) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void removalEndsSafelyOnLogoutAndDeath(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_4");
		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_4"), "C starts the removal");
		for (int i = 0; i < 20; i++) {
			IronManSuitUpManager.tick(p);
		}
		IronManManualSuitUp.onDisconnect(p);
		h.assertFalse(IronManSuitUpManager.inTransition(p) || IronManArmor.wearingAnyIronMan(p), "logout finishes it at once");
		h.assertTrue(count(p, "mark_4") == 4 && !frozen(p), "every piece in the pack, no movement lock saved");

		ServerPlayer q = tony(h);
		wear(q, "mark_1");
		h.assertTrue(IronManSuitUpManager.beginSuitDown(q, "mark_1"), "C starts the Mark 1 removal");
		for (int i = 0; i < 60; i++) {
			IronManSuitUpManager.tick(q);
		}
		IronManManualSuitUp.abort(q);
		h.assertFalse(IronManSuitUpManager.inTransition(q) || frozen(q), "death stops it where it is");
		h.assertTrue(count(q, "mark_1") == 4, "nothing lost or duplicated");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightKeepsTheReverseBuild(GameTestHelper h) {
		ServerPlayer p = tony(h);
		wear(p, "mark_8");
		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_8"), "C starts the suit-down");
		h.assertTrue(IronManSuitFx.of(p).style() == IronManSuitFx.STYLE_UNBUILD && !IronManSuitRemoval.running(p),
				"the Mark 8 un-builds as before");
		h.succeed();
	}

	// ------------------------------------------------------------------ the Stark Glasses picker / C

	@GameTest(template = EMPTY_STRUCTURE)
	public void glassesPickerListsOnlyMarkEightAndUp(GameTestHelper h) {
		ServerPlayer p = tony(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_iii", t)));
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_8", t)));
		}
		h.assertTrue(IronManSuitCall.glassesOptions(p).isEmpty(), "no glasses: no picker at all");
		StarkGear.setGlasses(p, new ItemStack(IronManItems.STARK_GLASSES));
		List<IronManSuitListPayload.Option> opts = IronManSuitCall.glassesOptions(p);
		h.assertTrue(!opts.isEmpty() && opts.stream().allMatch(o -> IronManSuits.byId(o.suitId()).markNumber() >= StarkGear.GLASSES_MIN_MARK),
				"only Mark 8 and later are listed: " + opts);
		h.assertTrue(opts.stream().anyMatch(o -> o.suitId().equals("mark_8")), "the Mark 8 is there");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void plainCWithoutGlassesStillPutsOnACarriedSuitButOpensNothing(GameTestHelper h) {
		ServerPlayer p = tony(h);
		IronManSuitCall.unsuitedC(p);
		h.assertFalse(IronManSuitUpManager.inTransition(p), "no gear, no suit: nothing happens (silently)");
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_iii", t)));
		}
		IronManSuitCall.unsuitedC(p);
		h.assertTrue(IronManSuitUpManager.inTransition(p), "plain C still puts on the suit in the pack");
		h.succeed();
	}

	// ------------------------------------------------------------------ gantry faceplate

	@GameTest(template = EMPTY_STRUCTURE)
	public void gantryFaceplateTimesTheHudAndHighlight(GameTestHelper h) {
		GantryTimeline.Plan full = GantryTimeline.FULL;
		float at = full.appear(GantryTimeline.FACEPLATE, 0f);
		float helmet = full.appear(GantryTimeline.HELMET, 0f);
		h.assertTrue(!Float.isNaN(at) && at > helmet, "the faceplate goes on after the helmet");
		h.assertFalse(GantryTimeline.faceplateOn(full, at - 1f), "before it is fitted (and once lifted off) the visor is dark");
		h.assertTrue(GantryTimeline.faceplateOn(full, at), "fitted: the HUD / highlight come on");
		h.assertFalse(GantryTimeline.faceplateOn(full, (at + helmet) * 0.5f),
				"between the helmet and the faceplate the visor is dark -- taking the suit off it goes with the faceplate");
		h.assertTrue(GantryTimeline.faceplateOn(GantryTimeline.plan(0b1110), 0f), "no helmet in the plan: never dark");
		h.succeed();
	}

	// ------------------------------------------------------------------ Mark 7 call cost

	private static IronManSuitPlatformBlockEntity rack(GameTestHelper h, ServerPlayer p, String suitId, float energyFrac) {
		BlockPos rel = new BlockPos(1, 1, 5);
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(p.getUUID());
		float cap = IronManSuits.byId(suitId).energyCapacity();
		for (ArmorItem.Type t : TYPES) {
			ItemStack s = new ItemStack(IronManItems.armor(suitId, t));
			IronManEnergy.stampStack(s, cap * energyFrac, IronManEnergy.maxIntegrity(suitId));
			be.store(s);
		}
		return be;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void callingTheMarkSevenCostsTenPercent(GameTestHelper h) {
		ServerPlayer p = tony(h);
		p.setOnGround(true);
		StarkGear.setGlasses(p, ColantotteBracelets.bind(new ItemStack(IronManItems.COLANTOTTE_BRACELETS), p.getUUID(), 0));
		IronManSuitPlatformBlockEntity be = rack(h, p, "mark_vii", 0.5f);
		h.assertTrue(IronManSuitCall.callCosts(IronManSuits.byId("mark_vii")) && !IronManSuitCall.callCosts(IronManSuits.byId("mark_iii")),
				"only the Mark 7's call costs power here (the Mark 8 is the Mark 8 builder's)");
		h.assertTrue(IronManSuitCall.braceletCall(p), "the bracelets call the Mark 7");
		h.assertTrue(be.isEmptyPlatform(), "it leaves its platform");
		float cap = IronManSuits.byId("mark_vii").energyCapacity();
		float e = IronManEnergy.energy(p, "mark_vii");
		h.assertTrue(Math.abs(e - cap * 0.4f) < cap * 0.001f, "50% on the rack -> 40% after the call, got " + e / cap);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aMarkSevenUnderTenPercentStaysPut(GameTestHelper h) {
		ServerPlayer p = tony(h);
		p.setOnGround(true);
		StarkGear.setGlasses(p, ColantotteBracelets.bind(new ItemStack(IronManItems.COLANTOTTE_BRACELETS), p.getUUID(), 0));
		IronManSuitPlatformBlockEntity be = rack(h, p, "mark_vii", 0.05f);
		IronManSuitCall.braceletCall(p);
		h.assertFalse(be.isEmptyPlatform(), "5% power: the Mark 7 is not called");
		h.assertFalse(IronManSuitUpManager.inTransition(p), "nothing inbound");
		h.assertTrue(StarkGear.hasBracelets(p), "and the bracelets are not used up");
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false), "(nothing else touched)");
		h.succeed();
	}
}
