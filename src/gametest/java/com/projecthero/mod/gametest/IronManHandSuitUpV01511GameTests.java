package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.15.11: C with a suit in the pack puts it on <b>by hand</b> -- the Mark 1 cave build (600 ticks, sitting for the boots
 * and greaves, hammer in hand) and the Marks 2-7 workshop build (500 ticks, standing, faceplate last). The player is held
 * still, abilities / flight are locked, every piece ends up worn, and death / logout / dimension change mid-build never
 * lose or duplicate a piece.
 */
public class IronManHandSuitUpV01511GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static ServerPlayer tony(GameTestHelper h, String suit, int plan) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (int bit = 0; bit < 4; bit++) {
			if ((plan & (1 << bit)) != 0) {
				p.getInventory().add(new ItemStack(IronManItems.armor(suit, TYPES[bit])));
			}
		}
		return p;
	}

	/** Pieces of {@code suit} anywhere on the player: pack + armour slots. */
	private static int pieces(ServerPlayer p, String suit) {
		int n = inPack(p, suit);
		for (EquipmentSlot s : SLOTS) {
			if (p.getItemBySlot(s).getItem() instanceof IronManArmorItem a && a.suitId().equals(suit)) {
				n += p.getItemBySlot(s).getCount();
			}
		}
		return n;
	}

	/** Pieces of {@code suit} in the pack only (main inventory + offhand, NOT the armour slots). */
	private static int inPack(ServerPlayer p, String suit) {
		int n = 0;
		var inv = p.getInventory();
		for (ItemStack st : inv.items) {
			if (st.getItem() instanceof IronManArmorItem a && a.suitId().equals(suit)) {
				n += st.getCount();
			}
		}
		for (ItemStack st : inv.offhand) {
			if (st.getItem() instanceof IronManArmorItem a && a.suitId().equals(suit)) {
				n += st.getCount();
			}
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void timelinesAreThirtyAndTwentyFiveSeconds(GameTestHelper h) {
		var mk1 = IronManManualSuitUp.schedule(IronManManualSuitUp.KIND_MK1, 0b1111);
		var ws = IronManManualSuitUp.schedule(IronManManualSuitUp.KIND_WORKSHOP, 0b1111);
		h.assertTrue(mk1.total() == 600 && IronManManualSuitUp.MK1_TICKS == 600, "the Mark 1 hand build is 30 s, got " + mk1.total());
		h.assertTrue(ws.total() == 500 && IronManManualSuitUp.WORKSHOP_TICKS == 500, "the workshop build is 25 s, got " + ws.total());
		for (var sch : new IronManManualSuitUp.Schedule[] { mk1, ws }) {
			h.assertTrue(sch.pieceAt(3) < sch.pieceAt(2) && sch.pieceAt(2) < sch.pieceAt(1) && sch.pieceAt(1) < sch.pieceAt(0),
					"boots, greaves, chestplate, helmet in that order");
			for (int bit = 0; bit < 4; bit++) {
				h.assertTrue(sch.pieceDone(bit) <= sch.total() && sch.pieceDone(bit) > sch.pieceAt(bit),
						"piece " + bit + " is built inside the sequence");
			}
			h.assertTrue(sch.faceplateAt() > sch.pieceDone(0) && sch.faceplateAt() < sch.total(),
					"the faceplate comes down last, once the helmet is built");
			h.assertTrue((sch.props(sch.pieceAt(1) - 20) & IronManManualSuitUp.PROP_PIECE) != 0,
					"the chestplate is carried in the hand before it is fitted");
		}
		// the Mark 1 sits on the floor for the boots / greaves and holds the hammer all the way through
		float[] boots = mk1.keyAt(mk1.pieceAt(3) + 40);
		float[] chest = mk1.keyAt(mk1.pieceAt(1) + 30);
		h.assertTrue(boots[IronManManualSuitUp.SIT] > 0.99f && boots[IronManManualSuitUp.RLX] < -1.3f,
				"sitting, legs out in front, for the boots");
		h.assertTrue(chest[IronManManualSuitUp.SIT] < 0.01f, "standing for the chestplate");
		for (int t = 1; t < 600; t += 7) {
			h.assertTrue((mk1.props(t) & IronManManualSuitUp.PROP_HAMMER) != 0, "the Mark 1 hammer is in hand at " + t);
		}
		// the workshop never sits, strikes less often and uses the wrench
		int mkStrikes = 0;
		int wsStrikes = 0;
		int ratchets = 0;
		for (var e : mk1.events()) {
			mkStrikes += e.type() == IronManManualSuitUp.EV_STRIKE ? 1 : 0;
		}
		for (var e : ws.events()) {
			wsStrikes += e.type() == IronManManualSuitUp.EV_STRIKE ? 1 : 0;
			ratchets += e.type() == IronManManualSuitUp.EV_RATCHET ? 1 : 0;
		}
		for (int t = 0; t <= 500; t += 5) {
			h.assertTrue(ws.keyAt(t)[IronManManualSuitUp.SIT] == 0f, "the workshop build never sits down");
		}
		h.assertTrue(wsStrikes > 0 && wsStrikes < mkStrikes, "fewer hammer strikes in the workshop (" + wsStrikes + " vs " + mkStrikes + ")");
		h.assertTrue(ratchets >= 6, "the workshop build ratchets bolts, got " + ratchets);
		// a partial set skips the missing pieces' work
		var chestOnly = IronManManualSuitUp.schedule(IronManManualSuitUp.KIND_MK1, 0b0010);
		h.assertTrue(chestOnly.total() < 200 && chestOnly.pieceAt(3) < 0 && chestOnly.keyAt(30)[IronManManualSuitUp.SIT] == 0f,
				"a chestplate alone: no sitting, much shorter");
		// the build-on progress runs 0 -> 1 monotonically inside each window
		for (int kind = 0; kind < 2; kind++) {
			for (int bit = 0; bit < 4; bit++) {
				int win = IronManManualSuitUp.window(kind, bit);
				float last = 0f;
				for (int a = 0; a <= win; a++) {
					float p = IronManManualSuitUp.progress(kind, bit, a);
					h.assertTrue(p >= last - 1e-5f, "progress never runs backwards");
					last = p;
				}
				h.assertTrue(IronManManualSuitUp.progress(kind, bit, 0) == 0f && last == 1f, "0 at the fit, 1 at the end");
			}
		}
		h.assertTrue(IronManManualSuitUp.kindFor("mark_1") == IronManManualSuitUp.KIND_MK1
				&& IronManManualSuitUp.kindFor("mark_vii") == IronManManualSuitUp.KIND_WORKSHOP
				&& IronManManualSuitUp.kindFor("mark_v") < 0 && IronManManualSuitUp.kindFor("mark_8") < 0,
				"Mark 1 = cave, Marks 2-7 = workshop (not the case-only Mark 5), Mark 8 keeps its own");
		h.succeed();
	}

	private static void runHandBuild(GameTestHelper h, String suit, int expectTicks) {
		ServerPlayer p = tony(h, suit, 0b1111);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "plain C puts the carried " + suit + " on");
		h.assertTrue(IronManManualSuitUp.running(p), "by hand");
		IronManSuitFx fx = IronManSuitFx.of(p);
		h.assertTrue(fx.manual() && IronManManualSuitUp.planOf(fx.poseVariant()) == 0b1111
				&& suit.equals(IronManManualSuitUp.suitOf(fx.poseVariant())), "the synced pose carries the suit and the plan");
		ByteBuf buf = Unpooled.buffer();
		IronManSuitFx.STREAM_CODEC.encode(buf, fx);
		h.assertTrue(IronManSuitFx.STREAM_CODEC.decode(buf).equals(fx), "and syncs unchanged");
		h.assertTrue(inPack(p, suit) == 4, "pieces are only reserved at the start");
		int ticks = 0;
		boolean sawOpenFaceplate = false;
		while (IronManSuitUpManager.inTransition(p) && ticks < 2000) {
			IronManSuitUpManager.tick(p);
			ticks++;
			if (IronManSuitUpManager.inTransition(p)) {
				h.assertTrue(IronManSuitPlatformBlockEntity.isFrozen(p), "held still at tick " + ticks);
				h.assertTrue(IronManSuitUpManager.blockedWhileAssembling(p, false), "abilities locked at tick " + ticks);
				h.assertTrue(pieces(p, suit) == 4, "never more or fewer than four pieces (tick " + ticks + ")");
				if (IronManArmor.isPieceWorn(p, EquipmentSlot.HEAD, suit)
						&& p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false)) {
					sawOpenFaceplate = true;
				}
			}
			if (ticks == 100) {
				IronManFlight.toggle(p);
				h.assertFalse(IronManFlight.isFlying(p), "no take-off mid build");
				var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
				h.assertTrue(speed != null && speed.getValue() <= 1e-6, "walking speed is zero");
			}
		}
		h.assertTrue(ticks == expectTicks, suit + " took exactly " + expectTicks + " ticks, got " + ticks);
		h.assertTrue(IronManArmor.wearingFullSuit(p, suit), "the whole suit is on");
		h.assertTrue(inPack(p, suit) == 0 && pieces(p, suit) == 4, "every piece moved from the pack to the body, none copied");
		h.assertTrue(sawOpenFaceplate, "the helmet goes on with its faceplate up");
		h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false), "and it is pulled down at the end");
		h.assertTrue(IronManSuitFx.of(p).faceplateAt() > 0L, "with the seal");
		h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "free to move again");
		h.assertFalse(IronManSuitUpManager.assembling(p) || IronManManualSuitUp.running(p), "online");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_NONE, "the pose clock is dropped");
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void markOneBuildsByHandInSixHundredTicks(GameTestHelper h) {
		runHandBuild(h, "mark_1", 600);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void marksTwoToSevenGoOnByHandInFiveHundredTicks(GameTestHelper h) {
		runHandBuild(h, "mark_iii", 500);
		runHandBuild(h, "mark_vii", 500);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void piecesLandInTheirSlotsOnSchedule(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1", 0b1111);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_1", true), "by hand");
		var sch = IronManManualSuitUp.schedule(IronManManualSuitUp.KIND_MK1, 0b1111);
		for (int t = 1; t <= sch.pieceAt(1); t++) {
			IronManSuitUpManager.tick(p);
			if (t == sch.pieceAt(3) - 1) {
				h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "the boots are still being carried");
			}
			if (t == sch.pieceAt(3)) {
				h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.FEET, "mark_1"), "the boots land when fitted");
				h.assertTrue(IronManSuitFx.of(p).building(3, p.level().getGameTime()), "and start building on");
			}
		}
		h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.CHEST, "mark_1")
				&& p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "chestplate on, helmet still in the pack");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathMidBuildStopsWithNoLostOrCopiedPiece(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_iii", 0b1111);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii", true), "by hand");
		for (int i = 0; i < 250; i++) {
			IronManSuitUpManager.tick(p);
		}
		int worn = 0;
		for (EquipmentSlot s : SLOTS) {
			worn += IronManArmor.isPieceWorn(p, s, "mark_iii") ? 1 : 0;
		}
		h.assertTrue(worn >= 2 && worn < 4, "partway: some pieces on, some in the pack (" + worn + ")");
		IronManManualSuitUp.onDeath(p);
		h.assertFalse(IronManSuitUpManager.inTransition(p) || IronManManualSuitUp.running(p), "the build stops");
		h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "the movement lock is gone");
		h.assertTrue(pieces(p, "mark_iii") == 4, "all four pieces still exist exactly once");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_NONE, "no pose left running");
		for (int i = 0; i < 40; i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(pieces(p, "mark_iii") == 4, "and nothing moves afterwards");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void logoutOrDimensionChangeMidBuildFinishesTheSuit(GameTestHelper h) {
		for (int which = 0; which < 2; which++) {
			ServerPlayer p = tony(h, "mark_1", 0b1111);
			h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_1", true), "by hand");
			for (int i = 0; i < 200; i++) {
				IronManSuitUpManager.tick(p);
			}
			h.assertTrue(inPack(p, "mark_1") > 0, "some pieces are still in the pack");
			if (which == 0) {
				IronManManualSuitUp.onDisconnect(p);
			} else {
				IronManManualSuitUp.onChangeWorld(p);
			}
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_1"), "the rest goes straight on");
			h.assertTrue(inPack(p, "mark_1") == 0 && pieces(p, "mark_1") == 4, "exactly four pieces, all worn");
			h.assertFalse(IronManSuitUpManager.inTransition(p), "the build is over");
			h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "no movement lock saved with the player");
			h.assertFalse(p.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false), "faceplate shut");
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void partialSetAndPieceDroppedMidBuild(GameTestHelper h) {
		// only boots + chestplate carried: a shorter build, both end up on
		ServerPlayer p = tony(h, "mark_1", 0b1010);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "C with a partial set");
		int expect = IronManManualSuitUp.schedule(IronManManualSuitUp.KIND_MK1, 0b1010).total();
		int ticks = 0;
		while (IronManSuitUpManager.inTransition(p) && ticks < 1000) {
			IronManSuitUpManager.tick(p);
			ticks++;
		}
		h.assertTrue(ticks == expect && expect < 600, "shorter: " + ticks);
		h.assertTrue(IronManArmor.isPieceWorn(p, EquipmentSlot.FEET, "mark_1")
				&& IronManArmor.isPieceWorn(p, EquipmentSlot.CHEST, "mark_1"), "both carried pieces are on");
		// the helmet thrown away mid-build is simply skipped (never conjured)
		ServerPlayer q = tony(h, "mark_iii", 0b1111);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(q, "mark_iii", true), "by hand");
		for (int i = 0; i < q.getInventory().items.size(); i++) {
			if (q.getInventory().items.get(i).getItem() instanceof IronManArmorItem a && a.getType() == ArmorItem.Type.HELMET) {
				q.getInventory().items.set(i, ItemStack.EMPTY);
			}
		}
		ticks = 0;
		while (IronManSuitUpManager.inTransition(q) && ticks < 1000) {
			IronManSuitUpManager.tick(q);
			ticks++;
		}
		h.assertTrue(q.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && pieces(q, "mark_iii") == 3,
				"no helmet appears out of nowhere");
		h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(q), "and the build still ends cleanly");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyPlainCarriedSuitUpsGoOnByHand(GameTestHelper h) {
		// the old entry point (Protocol Phoenix, commands, the pod) keeps the self-building suit-up
		ServerPlayer p = tony(h, "mark_iii", 0b1111);
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
		h.assertFalse(IronManManualSuitUp.running(p), "not by hand");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_SUIT_UP, "the ordinary build pose");
		// the Mark 8 keeps its own suit-up even on C
		ServerPlayer q = tony(h, "mark_8", 0b1111);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(q), "C puts the Mark 8 on");
		h.assertFalse(IronManManualSuitUp.running(q), "the Mark 8 builds itself on");
		// the reserved ids
		h.assertTrue(IronManSuitFx.STYLE_MK1_BUILD == 40 && IronManSuitFx.STYLE_MANUAL == 41
				&& IronManSuitFx.POSE_MK1_BUILD == 40 && IronManSuitFx.POSE_MANUAL_UP == 41, "ids 40/41");
		h.succeed();
	}
}
