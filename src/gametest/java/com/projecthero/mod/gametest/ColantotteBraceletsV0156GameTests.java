package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;
import com.projecthero.mod.ironman.entity.IronManSuitPartEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gear.ColantotteBracelets;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * v0.15.6: the Colantotte Bracelets call only the Mark 7 and are used up by that call; the Mark 7 pod stays beside its
 * owner and spits the pieces out one by one, leaving only once the whole suit is on; and any suit called while falling
 * races in -- the pieces fly and build on twice as fast (the speed-up synced per piece in {@link IronManSuitFx}).
 */
public class ColantotteBraceletsV0156GameTests implements FabricGameTest {
	private static final String M7 = "mark_vii";
	private static final String M3 = "mark_iii";
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = ColantotteBraceletsV0156GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	private static void wearBracelets(ServerPlayer p) {
		StarkGear.setGlasses(p, ColantotteBracelets.bind(new ItemStack(IronManItems.COLANTOTTE_BRACELETS), p.getUUID(), 0));
	}

	private static IronManSuitPlatformBlockEntity rack(GameTestHelper h, ServerPlayer p, String suitId, int x, int z) {
		BlockPos rel = new BlockPos(x, 1, z);
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

	private static boolean wearing(ServerPlayer p, String suitId) {
		for (EquipmentSlot s : SLOTS) {
			if (!(p.getItemBySlot(s).getItem() instanceof IronManArmorItem a) || !a.suitId().equals(suitId)) {
				return false;
			}
		}
		return true;
	}

	private static AABB area(GameTestHelper h) {
		return new AABB(h.absolutePos(BlockPos.ZERO)).inflate(48).expandTowards(0, 100, 0);
	}

	private static List<IronManDeliveryPodEntity> pods(GameTestHelper h, ServerPlayer p) {
		return h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, area(h), e -> p.getUUID().equals(e.ownerId()));
	}

	private static List<IronManSuitPartEntity> couriers(GameTestHelper h, ServerPlayer p) {
		return h.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, area(h), e -> e.ownerEntityId() == p.getId());
	}

	/** Every piece of {@code suitId} that exists for {@code p}: worn, in the pack, on the rack, in a courier or in a pod. */
	private static int pieces(GameTestHelper h, ServerPlayer p, String suitId, IronManSuitPlatformBlockEntity be) {
		int n = 0;
		for (EquipmentSlot s : SLOTS) {
			n += p.getItemBySlot(s).getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId) ? 1 : 0;
		}
		for (ItemStack s : p.getInventory().items) {
			n += s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId) ? s.getCount() : 0;
		}
		if (be != null) {
			for (ItemStack s : be.pieces()) {
				n += s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId) ? s.getCount() : 0;
			}
		}
		for (IronManSuitPartEntity c : couriers(h, p)) {
			n += c.piece().getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId) ? 1 : 0;
		}
		for (IronManDeliveryPodEntity pod : pods(h, p)) {
			n += pod.cargo().size();
		}
		return n;
	}

	// ------------------------------------------------------------------ used up by the Mark 7 call

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void braceletsAreUsedUpByTheMarkSevenCall(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		p.setOnGround(true);
		wearBracelets(p);
		IronManSuitPlatformBlockEntity be = rack(h, p, M7, 1, 6);
		IronManSuitCall.execute(p, M7, IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(be.storedSuitId() == null, "the Mark 7 leaves its platform");
		h.assertFalse(StarkGear.hasBracelets(p), "the bracelets are gone from the Stark Gear slot");
		h.assertTrue(ColantotteBracelets.count(p) == 0, "used up for good: none worn or carried");
		h.assertTrue(StarkGear.glasses(p).isEmpty(), "nothing put back in the slot");
		List<IronManDeliveryPodEntity> list = pods(h, p);
		h.assertTrue(list.size() == 1 && list.get(0).fast(), "the bracelet call still comes by the fast pod");
		// a spent pair does not block the next one: a platform holding a Mark 7 issues a new pair
		IronManSuitPlatformBlockEntity other = rack(h, p, M7, 6, 1);
		h.assertFalse(ColantotteBracelets.hasPair(p), "no working pair left");
		h.assertTrue(ColantotteBracelets.claim(p, other) == ColantotteBracelets.Claim.GIVEN, "so a new pair can be claimed");
		h.assertTrue(ColantotteBracelets.count(p) == 1, "exactly one new pair");
		h.succeedWhen(() -> {
			h.assertTrue(wearing(p, M7) && !IronManSuitUpManager.inTransition(p), "the Mark 7 is on");
			h.assertTrue(pods(h, p).isEmpty(), "and the pod has gone");
			h.assertTrue(pieces(h, p, M7, be) == 4, "no piece lost or duplicated");
		});
	}

	// ------------------------------------------------------------------ Mark 7 only

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletsRefuseEveryOtherSuit(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		p.setOnGround(true);
		wearBracelets(p);
		h.assertTrue(StarkGear.canCall(p), "the bracelets still arm calling (HUD, Phoenix)");
		h.assertTrue(StarkGear.canCall(p, M7), "they call the Mark 7");
		h.assertFalse(StarkGear.canCall(p, M3), "but not the Mark 3");
		h.assertFalse(StarkGear.canCall(p, "mark_v"), "nor any other mark");
		IronManSuitPlatformBlockEntity be = rack(h, p, M3, 1, 6);
		IronManSuitCall.execute(p, M3, IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(M3.equals(be.storedSuitId()) && be.isFull(), "the Mark 3 stays on its platform");
		h.assertTrue(couriers(h, p).isEmpty() && pods(h, p).isEmpty(), "nothing is launched");
		h.assertFalse(IronManSuitUpManager.inTransition(p), "no suit-up starts");
		h.assertTrue(StarkGear.hasBracelets(p), "a refused call does not use the bracelets up");
		h.assertFalse(IronManSuitCall.callBest(p), "the quick call finds nothing it may call");
		h.assertFalse(IronManSuitCall.commandCall(p, M3), "nor does /ironman suit");
		h.assertFalse(IronManSuitCall.callPiece(p, M3, ArmorItem.Type.HELMET), "nor a single piece");
		h.assertTrue(be.isFull() && StarkGear.hasBracelets(p), "still all on the rack, bracelets still on");
		// the glasses call anything
		StarkGlassesV0151GameTests.wearGlasses(p);
		h.assertTrue(StarkGear.canCall(p, M3) && StarkGear.canCall(p, M7), "the Stark Glasses call any suit");
		// text
		for (String k : new String[] { "message.projecthero.ironman.bracelets_mk7_only", "message.projecthero.ironman.bracelets_used" }) {
			h.assertTrue(IronManUiLayout.approxWidth(t(k)) <= 320, "action-bar message fits: " + k);
		}
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_call.bracelets_mk7_only")) <= 120,
				"the greyed card's line fits");
		t("screen.projecthero.suit_call.bracelets_mk7_only_hint");
		for (String k : new String[] { "item.projecthero.colantotte_bracelets.tooltip2", "item.projecthero.colantotte_bracelets.tooltip3" }) {
			h.assertTrue(IronManUiLayout.approxWidth(t(k)) <= 220, "short tooltip: " + k);
		}
		h.assertTrue(t("projecthero.guide.iron_man.bracelets.body").contains("ONLY the Mark 7")
				&& t("projecthero.guide.iron_man.bracelets.body").contains("uses the bracelets up"), "the guide explains the rules");
		h.succeed();
	}

	// ------------------------------------------------------------------ the pod stays until the suit is on

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void braceletPodStaysAndSpitsThePiecesOutOneByOne(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		p.setOnGround(true);
		wearBracelets(p);
		IronManSuitPlatformBlockEntity be = rack(h, p, M7, 1, 6);
		IronManSuitCall.execute(p, M7, IronManSuitListPayload.SOURCE_PLATFORM);
		IronManDeliveryPodEntity pod = pods(h, p).get(0);
		int[] clock = { 0 };
		int[] wornAt = { -1, -1, -1, -1 };
		int[] closedAt = { -1 };
		int[] doneAt = { -1 };
		boolean[] braceletCouriers = { false };
		boolean[] braceletStyle = { false };
		boolean[] earlyLeave = { false };
		boolean[] dupe = { false };
		h.onEachTick(() -> {
			clock[0]++;
			for (int bit = 0; bit < 4; bit++) {
				if (wornAt[bit] < 0 && IronManArmor.isPieceWorn(p, SLOTS[bit], M7)) {
					wornAt[bit] = clock[0];
				}
			}
			for (IronManSuitPartEntity c : couriers(h, p)) {
				braceletCouriers[0] |= c.braceletWrap();
			}
			braceletStyle[0] |= IronManSuitFx.of(p).bracelet() && IronManArmor.wearingAnyIronMan(p);
			boolean podLeaving = pod.isRemoved() || pod.phase() >= IronManDeliveryPodEntity.CLOSE;
			if (closedAt[0] < 0 && podLeaving) {
				closedAt[0] = clock[0];
				// the moment it closes, the whole suit must be on and nothing still building
				earlyLeave[0] = !wearing(p, M7) || IronManSuitFx.of(p).anyBuilding(h.getLevel().getGameTime());
			}
			if (doneAt[0] < 0 && wearing(p, M7) && !IronManSuitFx.of(p).anyBuilding(h.getLevel().getGameTime())) {
				doneAt[0] = clock[0];
			}
			dupe[0] |= pieces(h, p, M7, be) != 4;
		});
		h.succeedWhen(() -> {
			h.assertTrue(wearing(p, M7) && !IronManSuitUpManager.inTransition(p), "the full Mark 7 ends up on");
			h.assertTrue(pods(h, p).isEmpty(), "and then the pod flies off");
			for (int a = 0; a < 4; a++) {
				for (int b = a + 1; b < 4; b++) {
					h.assertTrue(wornAt[a] != wornAt[b], "the pieces go on one by one (" + java.util.Arrays.toString(wornAt) + ")");
				}
			}
			h.assertTrue(braceletCouriers[0], "the pod spits the pieces out as couriers that wrap on bracelet-style");
			h.assertTrue(braceletStyle[0], "every viewer gets the bracelet wrap-on look per piece");
			h.assertFalse(earlyLeave[0], "the pod did not close before the suit was fully on (closed " + closedAt[0]
					+ ", done " + doneAt[0] + ")");
			h.assertTrue(closedAt[0] >= doneAt[0] && doneAt[0] > 0, "it closes once the suit is done");
			h.assertFalse(dupe[0], "never a piece lost or duplicated on the way");
			h.assertFalse(IronManFaceplate.isOpen(p), "the faceplate closes at the end");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void catchModePodWaitsForTheWholeSuit(GameTestHelper h) {
		// the ordinary (glasses) airborne call: the pod clamps the pieces on mid-air and must ride along until they are built
		ServerPlayer p = tony(h, 1, 1);
		StarkGlassesV0151GameTests.wearGlasses(p);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(M7, t)));
		}
		p.setNoGravity(true);
		p.teleportTo(p.getX(), p.getY() + 6.0, p.getZ());
		p.setOnGround(false);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "C while airborne calls the Mark 7 by pod");
		IronManDeliveryPodEntity pod = pods(h, p).get(0);
		boolean[] caught = { false };
		boolean[] earlyLeave = { false };
		boolean[] sawLeave = { false };
		boolean[] dupe = { false };
		h.onEachTick(() -> {
			caught[0] |= pod.catching();
			if (!sawLeave[0] && (pod.isRemoved() || pod.phase() >= IronManDeliveryPodEntity.CLOSE)) {
				sawLeave[0] = true;
				earlyLeave[0] = !wearing(p, M7) || IronManSuitFx.of(p).anyBuilding(h.getLevel().getGameTime());
			}
			dupe[0] |= pieces(h, p, M7, null) != 4;
		});
		h.succeedWhen(() -> {
			h.assertTrue(caught[0], "caught mid-air");
			h.assertTrue(wearing(p, M7) && pods(h, p).isEmpty(), "full suit on, pod gone");
			h.assertFalse(earlyLeave[0], "the pod stayed alongside until the whole suit had built on");
			h.assertFalse(dupe[0], "no piece lost or duplicated");
		});
	}

	// ------------------------------------------------------------------ falling: twice as fast

	@GameTest(template = EMPTY_STRUCTURE)
	public void fallingDetectionAndPodSpeed(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		p.setOnGround(true);
		p.fallDistance = 10f;
		h.assertFalse(IronManSuitCall.falling(p), "standing is not falling");
		p.setOnGround(false);
		p.fallDistance = 0.5f;
		h.assertFalse(IronManSuitCall.falling(p), "a hop is not falling");
		p.fallDistance = 10f;
		h.assertTrue(IronManSuitCall.falling(p), "airborne and well into a drop is falling");
		p.getAbilities().flying = true;
		h.assertFalse(IronManSuitCall.falling(p), "flying is not falling");
		p.getAbilities().flying = false;
		for (int t = 0; t < 40; t++) {
			h.assertTrue(Math.abs(IronManDeliveryPodEntity.speedAt(t, false, true) - IronManDeliveryPodEntity.speedAt(t, true)) < 1.0e-9,
					"a falling owner's pod runs at double time");
		}
		// the speed-up is synced per piece: the fast bit halves that piece's build window for every viewer
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPiece(1, 100L, true, true).withPiece(3, 100L, true, false);
		h.assertTrue(fx.fast(1) && !fx.fast(3) && fx.assembling(1) && fx.assembling(3), "fast bit per piece");
		h.assertTrue(fx.lockTicks(1) * 2 == IronManSuitFx.BUILD_TICKS && fx.lockTicks(3) == IronManSuitFx.BUILD_TICKS,
				"half the build window");
		h.assertTrue(fx.building(1, 100L + IronManSuitFx.BUILD_TICKS / 2 - 1) && !fx.building(1, 100L + IronManSuitFx.BUILD_TICKS / 2),
				"the fast piece is built in half the time");
		var buf = io.netty.buffer.Unpooled.buffer();
		IronManSuitFx.STREAM_CODEC.encode(buf, fx);
		IronManSuitFx back = IronManSuitFx.STREAM_CODEC.decode(buf);
		h.assertTrue(back.fast(1) && !back.fast(3), "the fast flag reaches the clients");
		h.assertFalse(fx.withPiece(1, 200L, true).fast(1), "a fresh ordinary clock clears it");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void fallingCallArrivesAndBuildsTwiceAsFast(GameTestHelper h) {
		// two glasses Tonys calling a Mark 3 off their own platforms: one falling (well into a drop), one standing
		ServerPlayer fallP = tony(h, 1, 1);
		ServerPlayer standP = tony(h, 6, 1);
		IronManSuitPlatformBlockEntity fallRack = rack(h, fallP, M3, 1, 6);
		IronManSuitPlatformBlockEntity standRack = rack(h, standP, M3, 6, 6);
		StarkGlassesV0151GameTests.wearGlasses(fallP);
		StarkGlassesV0151GameTests.wearGlasses(standP);
		standP.setOnGround(true);
		fallP.setNoGravity(true);
		fallP.teleportTo(fallP.getX(), fallP.getY() + 4.0, fallP.getZ());
		fallP.setOnGround(false);
		fallP.fallDistance = 12f;
		h.assertTrue(IronManSuitCall.falling(fallP) && !IronManSuitCall.falling(standP), "one falling, one standing");
		IronManSuitCall.execute(fallP, M3, IronManSuitListPayload.SOURCE_PLATFORM);
		IronManSuitCall.execute(standP, M3, IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(couriers(h, fallP).stream().allMatch(IronManSuitPartEntity::falling) && !couriers(h, fallP).isEmpty(),
				"the falling Tony's couriers race in");
		h.assertTrue(couriers(h, standP).stream().noneMatch(IronManSuitPartEntity::falling), "the standing Tony's do not");
		int[] clock = { 0 };
		int[] first = { -1, -1 };
		int[] done = { -1, -1 };
		boolean[] fastBits = { false };
		boolean[] slowBits = { false };
		boolean[] dupe = { false };
		h.onEachTick(() -> {
			clock[0]++;
			fallP.fallDistance = Math.max(fallP.fallDistance, 12f); // still dropping
			ServerPlayer[] ps = { fallP, standP };
			for (int i = 0; i < 2; i++) {
				ServerPlayer q = ps[i];
				if (first[i] < 0 && IronManArmor.wearingAnyIronMan(q)) {
					first[i] = clock[0];
				}
				if (done[i] < 0 && wearing(q, M3) && !IronManSuitFx.of(q).anyBuilding(h.getLevel().getGameTime())) {
					done[i] = clock[0];
				}
			}
			IronManSuitFx ff = IronManSuitFx.of(fallP);
			IronManSuitFx sf = IronManSuitFx.of(standP);
			for (int bit = 0; bit < 4; bit++) {
				fastBits[0] |= ff.assembling(bit) && ff.fast(bit);
				slowBits[0] |= sf.fast(bit);
			}
			dupe[0] |= pieces(h, fallP, M3, fallRack) != 4 || pieces(h, standP, M3, standRack) != 4;
		});
		h.succeedWhen(() -> {
			h.assertTrue(done[0] > 0 && done[1] > 0, "both suits end up fully on (" + done[0] + ", " + done[1] + ")");
			h.assertTrue(first[0] < first[1], "the falling Tony's first piece arrives earlier (" + first[0] + " vs " + first[1] + ")");
			h.assertTrue(done[0] * 1.0 <= done[1] * 0.7,
					"the falling Tony is fully suited in about half the time (" + done[0] + " vs " + done[1] + " ticks)");
			h.assertTrue(fastBits[0], "the falling Tony's pieces carry the synced fast flag");
			h.assertFalse(slowBits[0], "the standing Tony's never do");
			h.assertFalse(dupe[0], "no piece lost or duplicated");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fallingMarkSevenCallFlagsThePod(GameTestHelper h) {
		ServerPlayer p = tony(h, 1, 1);
		StarkGlassesV0151GameTests.wearGlasses(p);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(M7, t)));
		}
		p.setNoGravity(true);
		p.teleportTo(p.getX(), p.getY() + 6.0, p.getZ());
		p.setOnGround(false);
		p.fallDistance = 15f;
		h.assertTrue(IronManSuitCall.orbitalDrop(p, IronManSuits.MARK_VII), "the falling Tony calls the Mark 7");
		List<IronManDeliveryPodEntity> list = pods(h, p);
		h.assertTrue(list.size() == 1 && list.get(0).falling(), "its pod is on the double-speed falling schedule");
		h.assertTrue(M7.equals(list.get(0).suitId()), "and knows which suit it waits for");
		h.succeed();
	}
}
