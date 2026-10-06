package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformMenu;
import com.projecthero.mod.ironman.gear.ColantotteBracelets;
import com.projecthero.mod.ironman.gear.ColantotteBraceletsItem;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.gear.StarkGearLayout;
import com.projecthero.mod.ironman.gear.StarkGearMenu;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManBraceletSuitUp;
import com.projecthero.mod.ironman.suit.IronManChunkTickets;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.suit.SuitUpType;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * v0.15.4: the Colantotte Bracelets -- handed out by a Suit Platform holding a Mark 7 (never without one, never a second
 * working pair), worn in the Stark Gear slot (calling works, no night vision), and the Mark 7 with them: a pod that flies
 * twice as fast and the 4 s bracelet wrap-on (body pieces first, then the helmet, the faceplate closing last). Without
 * the bracelets nothing changes.
 */
public class ColantotteBraceletsV0154GameTests implements FabricGameTest {
	private static final String M7 = "mark_vii";
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = ColantotteBraceletsV0154GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	private static ServerPlayer tony(GameTestHelper h, int x, int z) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(x, 1, z));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f);
		p.setXRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static ItemStack bracelets() {
		return new ItemStack(IronManItems.COLANTOTTE_BRACELETS);
	}

	private static void wearBracelets(ServerPlayer p) {
		StarkGear.setGlasses(p, bracelets());
	}

	private static void packMark7(ServerPlayer p) {
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(M7, t)));
		}
	}

	private static boolean wearing(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			if (!(p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem a) || !a.suitId().equals(suitId)) {
				return false;
			}
		}
		return true;
	}

	private static int slotOf(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).getItem() instanceof ColantotteBraceletsItem) {
				return i;
			}
		}
		return -1;
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer p, String suitId) {
		return platform(h, p, suitId, 3);
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer p, String suitId, int x) {
		BlockPos rel = new BlockPos(x, 1, 3);
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(p.getUUID());
		if (suitId != null) {
			for (ArmorItem.Type t : TYPES) {
				be.store(new ItemStack(IronManItems.armor(suitId, t)));
			}
		}
		return be;
	}

	// ------------------------------------------------------------------ the platform button

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletsOnlyFromAPlatformHoldingAMarkSeven(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		IronManSuitPlatformBlockEntity be = platform(h, p, "mark_iii");
		IronManSuitPlatformMenu menu = new IronManSuitPlatformMenu(1, p.getInventory(), be, new SimpleContainerData(3));
		h.assertFalse(menu.offersBracelets(), "no Bracelets button with a Mark 3 stored");
		h.assertFalse(menu.clickMenuButton(p, IronManSuitPlatformMenu.BRACELETS_BUTTON), "pressing it anyway does nothing");
		h.assertTrue(ColantotteBracelets.count(p) == 0, "and hands out no bracelets");

		IronManSuitPlatformBlockEntity empty = platform(h, p, null, 5);
		h.assertTrue(ColantotteBracelets.claim(p, empty) == ColantotteBracelets.Claim.NO_MARK_7, "an empty platform has none either");

		IronManSuitPlatformBlockEntity mk7 = platform(h, p, M7, 7);
		menu = new IronManSuitPlatformMenu(2, p.getInventory(), mk7, new SimpleContainerData(3));
		h.assertTrue(menu.offersBracelets(), "the Bracelets button shows with a Mark 7 stored");
		h.assertTrue(menu.clickMenuButton(p, IronManSuitPlatformMenu.BRACELETS_BUTTON), "pressing it hands out a pair");
		h.assertTrue(ColantotteBracelets.count(p) == 1, "exactly one pair");
		h.assertTrue(p.getInventory().countItem(IronManItems.COLANTOTTE_BRACELETS) == 1, "in the pack");
		h.assertTrue(p.getUUID().equals(ColantotteBracelets.owner(p.getInventory().getItem(slotOf(p)))), "bound to the player");

		// no spam: pressing again, with the pair in the pack or worn, gives nothing more
		for (int i = 0; i < 5; i++) {
			h.assertFalse(menu.clickMenuButton(p, IronManSuitPlatformMenu.BRACELETS_BUTTON), "no second pair while you have one");
		}
		h.assertTrue(ColantotteBracelets.count(p) == 1, "still exactly one pair after spamming the button");
		ItemStack pair = p.getInventory().getItem(slotOf(p));
		StarkGear.equip(p, pair);
		pair.shrink(1);
		h.assertTrue(StarkGear.hasBracelets(p), "worn in the Stark Gear slot");
		h.assertTrue(ColantotteBracelets.claim(p, mk7) == ColantotteBracelets.Claim.ALREADY_HAVE, "wearing them counts too");
		h.assertTrue(ColantotteBracelets.count(p) == 1, "still one pair");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aReplacementPairRetiresTheOldOne(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		IronManSuitPlatformBlockEntity be = platform(h, p, M7);
		h.assertTrue(ColantotteBracelets.claim(p, be) == ColantotteBracelets.Claim.GIVEN, "first pair");
		ItemStack old = p.getInventory().removeItem(slotOf(p), 1); // "left in a chest" / lost
		h.assertFalse(ColantotteBracelets.hasPair(p), "without it on you, you have no pair");
		h.assertFalse(ColantotteBracelets.retired(old, h.getLevel().getServer()), "the first pair works until replaced");
		h.assertTrue(ColantotteBracelets.claim(p, be) == ColantotteBracelets.Claim.GIVEN, "so the platform issues a new one");
		h.assertTrue(ColantotteBracelets.retired(old, h.getLevel().getServer()), "which retires the old pair");
		ItemStack fresh = p.getInventory().getItem(slotOf(p)).copy();
		h.assertFalse(ColantotteBracelets.retired(fresh, h.getLevel().getServer()), "the new pair works");
		h.assertTrue(ColantotteBracelets.serial(fresh) == ColantotteBracelets.serial(old) + 1, "with the next issue number");
		// the retired pair crumbles in a pack (checked once a second)
		p.getInventory().add(old);
		h.assertTrue(p.getInventory().countItem(IronManItems.COLANTOTTE_BRACELETS) == 2, "both in the pack for a moment");
		h.onEachTick(() -> {
			for (ItemStack s : p.getInventory().items) {
				if (s.getItem() instanceof ColantotteBraceletsItem) {
					s.getItem().inventoryTick(s, h.getLevel(), p, 0, false);
				}
			}
		});
		h.succeedWhen(() -> h.assertTrue(p.getInventory().countItem(IronManItems.COLANTOTTE_BRACELETS) == 1,
				"the retired pair crumbles, the new one stays"));
	}

	// ------------------------------------------------------------------ the Stark Gear slot

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletsFitTheStarkGearSlotAndAllowCalling(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		h.assertFalse(StarkGear.canCall(p), "nothing worn: no calling");
		ItemStack old = StarkGear.equip(p, bracelets());
		h.assertTrue(old.isEmpty() && StarkGear.hasBracelets(p) && !StarkGear.hasGlasses(p), "the bracelets go in the slot");
		h.assertTrue(StarkGear.canCall(p), "and allow suit calling, like the glasses");
		StarkGearMenu menu = new StarkGearMenu(1, p.getInventory());
		h.assertTrue(menu.slots.get(StarkGearMenu.GEAR_SLOT).mayPlace(bracelets()), "the gear slot takes the bracelets");
		h.assertTrue(menu.slots.get(StarkGearMenu.GEAR_SLOT).mayPlace(new ItemStack(IronManItems.STARK_GLASSES)), "and the glasses");
		h.assertFalse(menu.slots.get(StarkGearMenu.GEAR_SLOT).mayPlace(new ItemStack(IronManItems.ARC_REACTOR)), "nothing else");
		StarkGear.tick(p);
		h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "the bracelets give no night vision");
		ItemStack swapped = StarkGear.equip(p, new ItemStack(IronManItems.STARK_GLASSES));
		h.assertTrue(swapped.getItem() instanceof ColantotteBraceletsItem && StarkGear.hasGlasses(p), "one or the other: glasses swap them out");
		StarkGear.equip(p, swapped);
		// a quick call with them on is not refused (the Mark 7 in the pack, on the ground: the wrap-on)
		packMark7(p);
		p.setOnGround(true);
		h.assertTrue(IronManSuitCall.callBest(p), "a quick call works with only the bracelets on");
		// the screen rows: calling ONLINE, night vision OFF
		StarkGearLayout.Row[] rows = StarkGearLayout.rows(true, false, 0L, 0L);
		h.assertTrue(rows[0].tone() == StarkGearLayout.Tone.GOOD && rows[1].tone() == StarkGearLayout.Tone.OFF,
				"Stark Gear screen: calling online, night vision off");
		h.succeed();
	}

	// ------------------------------------------------------------------ the bracelet wrap-on

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void braceletSuitUpIsShortAndClosesTheFaceplateLast(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		wearBracelets(p);
		packMark7(p);
		h.assertTrue(IronManSuitUpManager.effectiveSuitUpType(p, IronManSuits.MARK_VII) == SuitUpType.BRACELET_QUICK,
				"the Mark 7 with bracelets uses the bracelet suit-up type");
		h.assertTrue(IronManSuitUpManager.effectiveSuitUpType(p, IronManSuits.MARK_III) != SuitUpType.BRACELET_QUICK,
				"only the Mark 7");
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, M7), "C puts the Mark 7 on");
		TonyStarkState s = TonyStark.state(p);
		h.assertTrue(s.transitionBracelet, "as the bracelet wrap-on");
		h.assertTrue(IronManSuitFx.of(p).style() == IronManSuitFx.STYLE_BRACELET
				&& IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_BRACELET_UP, "every viewer gets the bracelet style + pose");
		int total = s.transitionTotal;
		h.assertTrue(total == IronManBraceletSuitUp.UP_TICKS && total <= 85,
				"about 4 s (" + total + " ticks)");
		h.assertTrue(total * 2 < IronManSuitUpManager.buildSequenceTicks(4), "well under half the ordinary build");

		int[] on = { -1, -1, -1, -1 };
		int faceplateOpenedAt = -1;
		int faceplateClosedAt = -1;
		boolean wasOpen = false;
		boolean immuneThroughout = true;
		int tick = 0;
		while (IronManSuitUpManager.inTransition(p) && tick < 200) {
			IronManSuitUpManager.tick(p);
			tick++;
			for (int bit = 0; bit < 4; bit++) {
				EquipmentSlot slot = new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
						EquipmentSlot.FEET }[bit];
				if (on[bit] < 0 && IronManArmor.isPieceWorn(p, slot, M7)) {
					on[bit] = tick;
				}
			}
			boolean open = IronManFaceplate.isOpen(p);
			if (open && !wasOpen) {
				faceplateOpenedAt = tick;
			}
			if (!open && wasOpen) {
				faceplateClosedAt = tick;
			}
			wasOpen = open;
			if (IronManSuitUpManager.inTransition(p)) {
				immuneThroughout &= IronManDamage.suitUpImmune(p) && IronManSuitUpManager.blockedWhileAssembling(p, false);
			}
		}
		h.assertTrue(tick <= IronManBraceletSuitUp.UP_TICKS + 2, "done in about 4 s (" + tick + " ticks)");
		h.assertTrue(on[1] > 0 && on[1] < on[2] && on[2] < on[3] && on[3] < on[0],
				"chestplate, then leggings, then boots, then the helmet (" + on[1] + "," + on[2] + "," + on[3] + "," + on[0] + ")");
		h.assertTrue(faceplateOpenedAt == on[0], "the helmet arrives without its faceplate down (raised)");
		h.assertTrue(faceplateClosedAt > on[0] + IronManBraceletSuitUp.upWindow(0) - 1,
				"the faceplate closes last, after the helmet is home (" + faceplateClosedAt + ")");
		h.assertTrue(immuneThroughout, "immune, abilities and flight locked for the whole wrap-on");
		h.assertTrue(wearing(p, M7) && IronManArmor.wearingFullSuit(p, M7), "the full Mark 7 is on");
		h.assertFalse(IronManFaceplate.isOpen(p), "faceplate closed");
		h.assertFalse(TonyStark.state(p).transitionBracelet, "the wrap-on flag is cleared");
		int strays = 0;
		for (ItemStack st : p.getInventory().items) {
			strays += st.getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		h.assertTrue(strays == 0, "every piece left the pack (no duplicates)");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletWrapOnMathIsSmooth(GameTestHelper h) {
		// the halves start open, never re-open, and end shut; the helmet swings home; the faceplate shows only near the end
		float prev = Float.MAX_VALUE;
		for (int i = 0; i <= 100; i++) {
			float p = i / 100f;
			float a = IronManBraceletSuitUp.openAngle(p);
			h.assertTrue(a <= prev + 1.0e-4f && a >= 0f && a <= IronManBraceletSuitUp.OPEN_DEG, "the halves only ever close (" + p + ")");
			prev = a;
			h.assertTrue(IronManBraceletSuitUp.scale(p) > 0.5f && IronManBraceletSuitUp.scale(p) < 1.1f, "scale stays sane");
		}
		h.assertTrue(IronManBraceletSuitUp.openAngle(0.01f) >= 90f && IronManBraceletSuitUp.openAngle(0.9f) == 0f,
				"opens wide, ends shut");
		h.assertTrue(IronManBraceletSuitUp.helmetAngle(0.01f) > 120f && IronManBraceletSuitUp.helmetAngle(1f) == 0f,
				"the helmet starts folded down the back and ends home");
		h.assertTrue(IronManBraceletSuitUp.faceplateAngle(0.3f) < 0f, "no faceplate on the helmet while it comes out of the back");
		h.assertTrue(Math.abs(IronManBraceletSuitUp.faceplateAngle(1f) - 90f) < 0.01f,
				"the faceplate ends raised, where the H swing takes over to close it");
		for (float age = 0f; age <= IronManBraceletSuitUp.UP_TICKS; age += 0.5f) {
			float[] k = IronManBraceletSuitUp.pose(age);
			h.assertTrue(k != null && k[0] >= 0f && k[0] <= 1f, "pose weight in range at " + age);
		}
		h.assertTrue(IronManBraceletSuitUp.pose(-1f) == null && IronManBraceletSuitUp.pose(IronManBraceletSuitUp.UP_TICKS + 1) == null,
				"no pose outside the wrap-on");
		h.assertTrue(IronManBraceletSuitUp.piecesDoneAt() < IronManBraceletSuitUp.FACEPLATE_CLOSE_AT
				&& IronManBraceletSuitUp.FACEPLATE_CLOSE_AT + 10 <= IronManBraceletSuitUp.UP_TICKS,
				"the faceplate swing fits after the pieces and before the end");
		h.succeed();
	}

	// ------------------------------------------------------------------ the call: twice as fast

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletPodFlightTakesHalfAsLong(GameTestHelper h) {
		for (double dist : new double[] { 26.0, 40.0, 70.0, 100.0 }) {
			int slow = ticksToCover(dist, false);
			int fast = ticksToCover(dist, true);
			h.assertTrue(fast <= Math.ceil(slow / 2.0) + 1 && fast >= slow / 2 - 1,
					"the bracelet pod covers " + dist + " blocks in half the time (" + fast + " vs " + slow + ")");
		}
		h.assertTrue(IronManDeliveryPodEntity.speedAt(5, false) == Math.min(IronManDeliveryPodEntity.MAX_SPEED,
				IronManDeliveryPodEntity.START_SPEED + 5 * IronManDeliveryPodEntity.ACCELERATION), "without bracelets: unchanged");
		h.succeed();
	}

	private static int ticksToCover(double dist, boolean fast) {
		double d = 0;
		int t = 0;
		while (d < dist && t < 1000) {
			t++;
			d += IronManDeliveryPodEntity.speedAt(t, fast);
		}
		return t;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void markSevenCallWithBraceletsArrivesTwiceAsFast(GameTestHelper h) {
		// two Tonys hovering in the air, each with a Mark 7 in the pack: one wears the bracelets, one the glasses
		ServerPlayer fastP = tony(h, 1, 1);
		ServerPlayer slowP = tony(h, 6, 6);
		wearBracelets(fastP);
		StarkGlassesV0151GameTests.wearGlasses(slowP);
		for (ServerPlayer p : List.of(fastP, slowP)) {
			packMark7(p);
			p.setNoGravity(true);
			p.teleportTo(p.getX(), p.getY() + 6.0, p.getZ());
			p.setOnGround(false);
		}
		h.assertTrue(IronManSuitCall.fastCall(fastP, IronManSuits.MARK_VII), "a bracelet call");
		h.assertFalse(IronManSuitCall.fastCall(slowP, IronManSuits.MARK_VII), "a glasses call is the ordinary one");
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(fastP), "the bracelet Tony calls the Mark 7");
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(slowP), "so does the glasses Tony");
		// v0.15.6: the call uses the bracelets up; the glasses stay on
		h.assertFalse(StarkGear.hasBracelets(fastP) || ColantotteBracelets.count(fastP) > 0, "the bracelets are spent on the call");
		h.assertTrue(StarkGear.hasGlasses(slowP), "the glasses are not");
		AABB sky = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40).expandTowards(0, 100, 0);
		List<IronManDeliveryPodEntity> fastPods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky,
				e -> fastP.getUUID().equals(e.ownerId()));
		List<IronManDeliveryPodEntity> slowPods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky,
				e -> slowP.getUUID().equals(e.ownerId()));
		h.assertTrue(fastPods.size() == 1 && fastPods.get(0).fast(), "the bracelet call comes by a fast pod");
		h.assertTrue(slowPods.size() == 1 && !slowPods.get(0).fast(), "the glasses call by an ordinary one");
		IronManDeliveryPodEntity fastPod = fastPods.get(0);
		IronManDeliveryPodEntity slowPod = slowPods.get(0);
		int[] arrived = { -1, -1 };
		int[] clock = { 0 };
		boolean[] wrapOn = { false };
		h.onEachTick(() -> {
			clock[0]++;
			if (arrived[0] < 0 && (fastPod.isRemoved() || fastPod.phase() != IronManDeliveryPodEntity.DESCEND)) {
				arrived[0] = clock[0];
			}
			if (arrived[1] < 0 && (slowPod.isRemoved() || slowPod.phase() != IronManDeliveryPodEntity.DESCEND)) {
				arrived[1] = clock[0];
			}
			// v0.15.6: no more hand-off -- the pod clamps each piece on with the bracelet wrap-on look
			wrapOn[0] |= IronManSuitFx.of(fastP).bracelet() && IronManArmor.wearingAnyIronMan(fastP);
		});
		h.succeedWhen(() -> {
			h.assertTrue(arrived[0] > 0 && arrived[1] > 0, "both pods arrive");
			h.assertTrue(arrived[0] * 1.0 <= arrived[1] * 0.65,
					"the bracelet pod gets there about twice as fast (" + arrived[0] + " vs " + arrived[1] + " ticks)");
			h.assertTrue(wearing(fastP, M7) && wearing(slowP, M7), "both end up in the full Mark 7");
			h.assertTrue(wrapOn[0], "the bracelet Tony's Mark 7 pieces went on with the bracelet wrap-on look");
			h.assertFalse(IronManSuitUpManager.inTransition(fastP), "and is online");
			h.assertFalse(IronManFaceplate.isOpen(fastP), "faceplate closed");
		});
	}

	/**
	 * The user's rule: with the bracelets the Mark 7 is still CALLED off the Suit Platform -- it leaves the rack in its pod
	 * and physically travels to the player (half the time it takes without bracelets); only when it arrives does the
	 * wrap-on start. Two Tonys, each 20 blocks from their own platform holding a Mark 7.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void braceletCallStillTravelsFromThePlatform(GameTestHelper h) {
		ServerPlayer fastP = tony(h, 1, 1);
		ServerPlayer slowP = tony(h, 6, 1);
		wearBracelets(fastP);
		StarkGlassesV0151GameTests.wearGlasses(slowP);
		int dz = 11; // inside the test area: a rack in a frozen neighbour chunk would make the pod jump straight to its owner
		IronManSuitPlatformBlockEntity fastRack = rack(h, fastP, 1, dz);
		IronManSuitPlatformBlockEntity slowRack = rack(h, slowP, 6, dz);
		// v0.15.9: the racks sit outside the 8x8 test area; depending on where the test lands in the batch grid their chunk
		// may not be entity-ticking, and then the pod (correctly) comes in from the sky instead of off the rack. Force-load
		// both racks' chunks and call only once they tick (CALL_AT ticks in).
		// Only chunks that were not already forced (the GameTest framework force-loads test areas; un-forcing one of those
		// at the end would freeze a neighbouring test).
		java.util.Set<ChunkPos> forced = new java.util.HashSet<>();
		for (ChunkPos cp : List.of(new ChunkPos(fastRack.getBlockPos()), new ChunkPos(slowRack.getBlockPos()))) {
			if (!h.getLevel().getForcedChunks().contains(cp.toLong()) && h.getLevel().setChunkForced(cp.x, cp.z, true)) {
				forced.add(cp);
			}
		}
		// v0.15.9: call only once both racks really tick entities (a forced chunk takes a variable number of ticks to
		// get there) -- a fixed tick made this test depend on where it landed in the batch grid.
		int[] callAtBox = { -1 };
		IronManDeliveryPodEntity[] pods = new IronManDeliveryPodEntity[2];
		int[] arrived = { -1, -1 };
		int[] clock = { 0 };
		boolean[] earlyWorn = { false };
		boolean[] enRoute = { false };
		net.minecraft.world.phys.Vec3 rackAt = net.minecraft.world.phys.Vec3.atCenterOf(fastRack.getBlockPos());
		h.onEachTick(() -> {
			clock[0]++;
			if (callAtBox[0] < 0) {
				if (clock[0] >= 5 && IronManChunkTickets.entityTicking(h.getLevel(), fastRack.getBlockPos())
						&& IronManChunkTickets.entityTicking(h.getLevel(), slowRack.getBlockPos())) {
					callAtBox[0] = clock[0];
				} else {
					h.assertTrue(clock[0] < 150, "the racks chunks never started ticking entities");
					return;
				}
			}
			final int callAt = callAtBox[0];
			if (clock[0] == callAt) {
				for (ServerPlayer p : List.of(fastP, slowP)) {
					p.setOnGround(true);
				}
				IronManSuitCall.execute(fastP, M7, com.projecthero.mod.network.IronManSuitListPayload.SOURCE_PLATFORM);
				IronManSuitCall.execute(slowP, M7, com.projecthero.mod.network.IronManSuitListPayload.SOURCE_PLATFORM);
				h.assertTrue(fastRack.storedSuitId() == null && slowRack.storedSuitId() == null, "both suits leave their platforms");
				h.assertFalse(IronManArmor.wearingAnyIronMan(fastP), "nothing appears on the bracelet Tony at once");
				AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(48).expandTowards(0, 100, 0);
				pods[0] = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, area,
						e -> fastP.getUUID().equals(e.ownerId())).get(0);
				pods[1] = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, area,
						e -> slowP.getUUID().equals(e.ownerId())).get(0);
				double fromRack = pods[0].startPos().distanceTo(rackAt);
				h.assertTrue(fromRack < 2.5, "the bracelet pod sets off from the platform itself (" + fromRack + " blocks from it)");
				h.assertTrue(pods[0].startPos().distanceTo(fastP.position()) > dz - 3, "...about " + dz + " blocks from the player");
				h.assertTrue(pods[0].fast() && !pods[1].fast(), "the bracelet pod is the fast one");
				return;
			}
			int t = clock[0] - callAt;
			IronManDeliveryPodEntity fastPod = pods[0];
			IronManDeliveryPodEntity slowPod = pods[1];
			boolean fastHere = fastPod.isRemoved() || fastPod.phase() != IronManDeliveryPodEntity.DESCEND;
			if (!fastHere && fastPod.position().distanceTo(rackAt) > 1.5
					&& fastPod.position().distanceTo(fastP.position()) > 2.5) {
				enRoute[0] = true; // seen in flight between the platform and the player
			}
			if (arrived[0] < 0 && fastHere) {
				arrived[0] = t;
			}
			if (!fastHere && IronManArmor.wearingAnyIronMan(fastP)) {
				earlyWorn[0] = true; // a piece on the body before the pod got there
			}
			if (arrived[1] < 0 && (slowPod.isRemoved() || slowPod.phase() != IronManDeliveryPodEntity.DESCEND)) {
				arrived[1] = t;
			}
		});
		h.succeedWhen(() -> {
			h.assertTrue(arrived[0] > 0 && arrived[1] > 0, "both pods arrive");
			h.assertTrue(arrived[0] >= 2, "the bracelet suit takes real travel time (" + arrived[0] + " ticks), not instant");
			h.assertTrue(arrived[1] >= 4, "the ordinary pod takes its usual time (" + arrived[1] + " ticks)");
			h.assertTrue(arrived[0] <= arrived[1] * 0.7,
					"about half the travel time without bracelets (" + arrived[0] + " vs " + arrived[1] + " ticks)");
			h.assertFalse(earlyWorn[0], "nothing goes on until the suit has arrived");
			h.assertTrue(enRoute[0], "the pod was seen in flight between the platform and the player");
			h.assertTrue(wearing(fastP, M7) && !IronManSuitUpManager.inTransition(fastP), "then the wrap-on puts it on");
			h.assertTrue(wearing(slowP, M7), "the glasses Tony gets the ordinary delivery");
			for (ChunkPos cp : forced) {
				h.getLevel().setChunkForced(cp.x, cp.z, false);
			}
		});
	}

	private static IronManSuitPlatformBlockEntity rack(GameTestHelper h, ServerPlayer p, int x, int z) {
		BlockPos rel = new BlockPos(x, 1, z);
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor(M7, t)));
		}
		return be;
	}

	// ------------------------------------------------------------------ without the bracelets: unchanged

	@GameTest(template = EMPTY_STRUCTURE)
	public void withoutBraceletsTheMarkSevenIsUnchanged(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		StarkGlassesV0151GameTests.wearGlasses(p);
		packMark7(p);
		h.assertTrue(IronManSuitUpManager.effectiveSuitUpType(p, IronManSuits.MARK_VII) == SuitUpType.REMOTE_AUTOMATED,
				"its own suit-up type");
		h.assertFalse(IronManSuitCall.fastCall(p, IronManSuits.MARK_VII), "an ordinary call");
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, M7), "C puts it on");
		TonyStarkState s = TonyStark.state(p);
		h.assertFalse(s.transitionBracelet, "not the wrap-on");
		h.assertTrue(s.transitionPlan != 0 && s.transitionTotal == IronManSuitUpManager.buildSequenceTicks(4),
				"the ordinary 3 s-per-piece build");
		h.assertTrue(IronManSuitFx.of(p).style() == IronManSuitFx.STYLE_PLATES, "with the ordinary build-on style");
		// bracelets put on mid-build change nothing about the build already running
		wearBracelets(p);
		h.assertFalse(TonyStark.state(p).transitionBracelet, "a running build keeps its style");
		h.succeed();
	}

	// ------------------------------------------------------------------ text

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletTextExistsAndFits(GameTestHelper h) {
		h.assertTrue("Colantotte Bracelets".equals(t("item.projecthero.colantotte_bracelets")), "the item name");
		for (String k : new String[] { "item.projecthero.colantotte_bracelets.tooltip",
				"item.projecthero.colantotte_bracelets.tooltip2", "item.projecthero.colantotte_bracelets.tooltip3" }) {
			h.assertTrue(IronManUiLayout.approxWidth(t(k)) <= 220, "tooltip line fits without wrapping: " + k);
		}
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_platform.bracelets")) <= 60 - 6,
				"the platform's Bracelets tab label fits");
		for (String k : new String[] { "message.projecthero.ironman.bracelets_on", "message.projecthero.ironman.bracelets_given",
				"message.projecthero.ironman.bracelets_already", "message.projecthero.ironman.bracelets_need_mk7",
				"message.projecthero.ironman.bracelets_retired", "message.projecthero.ironman.glasses_needed" }) {
			h.assertTrue(IronManUiLayout.approxWidth(t(k)) <= 320, "action-bar message fits: " + k);
		}
		t("screen.projecthero.suit_platform.bracelets_tip");
		t("projecthero.guide.iron_man.bracelets");
		t("projecthero.guide.iron_man.bracelets.body");
		h.assertTrue(ModAttachments.COLANTOTTE_SERIAL != null, "the issue counter is registered");
		h.succeed();
	}
}
