package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.ProtocolPhoenix;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.gear.StarkGearLayout;
import com.projecthero.mod.ironman.gear.StarkGearMenu;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.1: the Stark Glasses and the Stark Gear slot -- equip / unequip (API, right-click, the menu's shift-click with no
 * duplication), persistence, Night Vision on / off, suit calling only with the glasses, Protocol Phoenix only with the
 * glasses and only for a Mark 7+, the death drop, and the Stark Gear screen's text fitting.
 */
public class StarkGlassesV0151GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
		p.setPos(at.x, at.y, at.z);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		return p;
	}

	private static ItemStack glasses() {
		return new ItemStack(IronManItems.STARK_GLASSES);
	}

	private static int glassesCount(ServerPlayer p) {
		return p.getInventory().countItem(IronManItems.STARK_GLASSES) + (StarkGear.hasGlasses(p) ? 1 : 0);
	}

	private static void packSuit(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(suitId, t)));
		}
	}

	private static IronManSuitPlatformBlockEntity platformWith(GameTestHelper h, ServerPlayer owner, BlockPos rel, String suitId) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(owner.getUUID());
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor(suitId, t)));
		}
		return be;
	}

	// ------------------------------------------------------------------ the slot

	@GameTest(template = EMPTY_STRUCTURE)
	public void glassesGoOnAndOffThroughTheServerApi(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		h.assertFalse(StarkGear.hasGlasses(p), "nothing in the Stark Gear slot at first");
		h.assertTrue(StarkGear.equip(p, glasses()).isEmpty(), "putting the first pair on returns nothing");
		h.assertTrue(StarkGear.hasGlasses(p), "the glasses are on");
		h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "and the helmet slot is untouched");
		ItemStack old = StarkGear.equip(p, glasses());
		h.assertTrue(old.is(IronManItems.STARK_GLASSES), "a second pair swaps out the first");
		ItemStack dirt = new ItemStack(net.minecraft.world.item.Items.DIRT);
		h.assertTrue(StarkGear.equip(p, dirt) == dirt && StarkGear.hasGlasses(p), "anything else is refused (handed back untouched)");
		ItemStack off = StarkGear.unequip(p);
		h.assertTrue(off.is(IronManItems.STARK_GLASSES) && off.getCount() == 1, "taking them off hands back exactly one pair");
		h.assertFalse(StarkGear.hasGlasses(p), "and the slot is empty");

		// right-click puts them on
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, glasses());
		p.getMainHandItem().use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
		h.assertTrue(StarkGear.hasGlasses(p), "right-clicking the glasses puts them on");
		h.assertTrue(glassesCount(p) == 1, "one pair in total, no copy left in hand");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theStarkGearMenuMovesTheGlassesWithoutDuplicating(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		p.getInventory().add(glasses());
		StarkGearMenu menu = new StarkGearMenu(1, p.getInventory());
		int invSlot = -1;
		for (int i = 1; i < menu.slots.size(); i++) {
			if (menu.slots.get(i).getItem().is(IronManItems.STARK_GLASSES)) {
				invSlot = i;
			}
		}
		h.assertTrue(invSlot > 0, "the glasses show in the menu's inventory");
		h.assertFalse(menu.slots.get(StarkGearMenu.GEAR_SLOT).mayPlace(new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET)),
				"the gear slot takes only Stark Glasses");
		menu.quickMoveStack(p, invSlot);
		h.assertTrue(StarkGear.hasGlasses(p), "shift-click puts them on");
		h.assertTrue(glassesCount(p) == 1, "exactly one pair after putting them on");
		menu.quickMoveStack(p, StarkGearMenu.GEAR_SLOT);
		h.assertFalse(StarkGear.hasGlasses(p), "shift-click on the gear slot takes them off");
		h.assertTrue(glassesCount(p) == 1, "exactly one pair after taking them off");
		// a full inventory refuses to take them off rather than losing them
		StarkGear.equip(p, p.getInventory().removeItem(p.getInventory().findSlotMatchingItem(glasses()), 1));
		for (int i = 0; i < 36; i++) {
			p.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
		}
		menu.quickMoveStack(p, StarkGearMenu.GEAR_SLOT);
		h.assertTrue(StarkGear.hasGlasses(p), "with no room the glasses stay on");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theGearSlotSurvivesASaveAndLoad(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		ItemStack named = glasses();
		named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("EDITH"));
		StarkGear.equip(p, named);
		CompoundTag tag = p.saveWithoutId(new CompoundTag());
		ServerPlayer fresh = h.makeMockServerPlayerInLevel();
		h.assertFalse(StarkGear.hasGlasses(fresh), "a fresh player has no glasses");
		fresh.load(tag);
		h.assertTrue(StarkGear.hasGlasses(fresh), "the glasses come back after a save / load (relog)");
		h.assertTrue("EDITH".equals(StarkGear.glasses(fresh).getHoverName().getString()), "with their components intact");
		h.succeed();
	}

	// ------------------------------------------------------------------ night vision

	@GameTest(template = EMPTY_STRUCTURE)
	public void glassesGiveNightVisionThatClearsTheMomentTheyComeOff(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		IronManSuitTicker.tick(p);
		h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "no night vision without the glasses");
		StarkGear.equip(p, glasses());
		IronManSuitTicker.tick(p);
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		h.assertTrue(nv != null && StarkGear.isOptic(nv), "glasses on: hidden, ambient night vision");
		IronManSuitTicker.tick(p);
		h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) != null, "and it stays on tick after tick (the helmet clear spares it)");
		StarkGear.unequip(p);
		IronManSuitTicker.tick(p);
		h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null, "glasses off: cleared the same tick");

		// a potion's night vision is never touched
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0));
		StarkGear.equip(p, glasses());
		IronManSuitTicker.tick(p);
		StarkGear.unequip(p);
		IronManSuitTicker.tick(p);
		MobEffectInstance potion = p.getEffect(MobEffects.NIGHT_VISION);
		h.assertTrue(potion != null && potion.isVisible() && potion.getDuration() > 3000, "a potion's night vision survives");

		// a player without the Tony Stark power still sees in the dark with the glasses on
		ServerPlayer plain = h.makeMockServerPlayerInLevel();
		StarkGear.equip(plain, glasses());
		IronManSuitTicker.tick(plain);
		h.assertTrue(plain.getEffect(MobEffects.NIGHT_VISION) != null, "night vision does not need the power");
		StarkGear.unequip(plain);
		IronManSuitTicker.tick(plain);
		h.assertTrue(plain.getEffect(MobEffects.NIGHT_VISION) == null, "and clears for them too");
		h.succeed();
	}

	// ------------------------------------------------------------------ suit calling

	@GameTest(template = EMPTY_STRUCTURE)
	public void callingASuitNeedsTheGlasses(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(6, 1, 6));
		IronManSuitPlatformBlockEntity be = platformWith(h, p, new BlockPos(2, 2, 2), "mark_iii");

		IronManSuitCall.execute(p, "mark_iii", IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(be.isFull(), "without the glasses the platform call is refused -- the suit stays put");
		h.assertFalse(IronManSuitUpManager.inTransition(p), "and nothing is inbound");
		h.assertFalse(IronManSuitCall.callBest(p), "the quick call is refused");
		h.assertFalse(IronManSuitCall.commandCall(p, "mark_iii"), "/ironman suit is refused");
		h.assertFalse(IronManSuitCall.callPiece(p, "mark_iii", ArmorItem.Type.HELMET), "/ironman part is refused");
		h.assertTrue(com.projecthero.mod.ironman.drone.IronManDrones.deploy(p, "mark_iii", IronManSuitListPayload.SOURCE_PLATFORM) == null,
				"Remote Pilot off a platform is refused");
		h.assertTrue(be.isFull(), "the platform still holds the whole suit");

		StarkGear.equip(p, glasses());
		IronManSuitCall.execute(p, "mark_iii", IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(be.isEmptyPlatform(), "with the glasses on the suit is called off the platform");
		h.assertTrue(IronManSuitUpManager.inTransition(p), "and is inbound");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void puttingOnACarriedSuitIsNotACall(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		packSuit(p, "mark_iii");
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "plain C still puts on a suit you carry, no glasses needed");
		h.assertTrue(IronManSuitUpManager.inTransition(p), "the staged suit-up runs");

		// the Mark 7 pod drop is a call: without the glasses an airborne Mark 7 in the pack just suits up normally
		ServerPlayer q = tony(h, new BlockPos(4, 1, 4));
		packSuit(q, "mark_vii");
		h.assertFalse(IronManSuitCall.orbitalDrop(q, com.projecthero.mod.ironman.suit.IronManSuits.MARK_VII),
				"no orbital drop without the glasses");
		h.succeed();
	}

	// ------------------------------------------------------------------ Protocol Phoenix

	@GameTest(template = EMPTY_STRUCTURE)
	public void phoenixNeedsTheGlassesAndAMarkSevenOrLater(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		try {
			packSuit(p, "mark_iii");
			StarkGear.equip(p, glasses());
			h.assertFalse(ProtocolPhoenix.tryActivate(p, p.damageSources().generic()), "a Mark III is never an emergency suit");
			h.assertTrue(TonyStark.phoenixReady(p), "and the cooldown is not spent");

			p.getInventory().clearContent();
			packSuit(p, "mark_vii");
			StarkGear.unequip(p);
			h.assertFalse(ProtocolPhoenix.tryActivate(p, p.damageSources().generic()), "no glasses: Phoenix stays disarmed");
			h.assertTrue(TonyStark.phoenixReady(p), "cooldown untouched");

			StarkGear.equip(p, glasses());
			h.assertTrue(ProtocolPhoenix.tryActivate(p, p.damageSources().generic()), "glasses + a Mark 7: Phoenix takes over");
			h.assertTrue("mark_vii".equals(TonyStark.state(p).phoenixSuitId), "and recalls the Mark 7");
			h.assertFalse(TonyStark.phoenixReady(p), "the cooldown starts");
		} finally {
			ProtocolPhoenix.clearEmergency(p);
			TonyStark.setPhoenixReadyAt(p, 0L);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ death

	@GameTest(template = EMPTY_STRUCTURE)
	public void theGlassesDropOnDeathUnlessKeepInventory(GameTestHelper h) {
		GameRules.BooleanValue keep = h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		boolean was = keep.get();
		try {
			ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
			keep.set(true, h.getLevel().getServer());
			StarkGear.equip(p, glasses());
			StarkGear.onDeath(p);
			h.assertTrue(StarkGear.hasGlasses(p), "keepInventory: the glasses stay on");

			keep.set(false, h.getLevel().getServer());
			StarkGear.onDeath(p);
			h.assertFalse(StarkGear.hasGlasses(p), "no keepInventory: they leave the slot");
			long drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3),
					e -> e.getItem().is(IronManItems.STARK_GLASSES)).size();
			h.assertTrue(drops == 1, "and exactly one pair drops where the player died, got " + drops);
			StarkGear.onDeath(p);
			drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3),
					e -> e.getItem().is(IronManItems.STARK_GLASSES)).size();
			h.assertTrue(drops == 1, "a second death with nothing worn drops nothing more");
		} finally {
			keep.set(was, h.getLevel().getServer());
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ the screen's layout

	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = StarkGlassesV0151GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void theStarkGearScreenFitsAndItsTextWraps(GameTestHelper h) {
		// GUI scale 4 (320x240) and scale 3 at 720p (426x240)
		for (int[] s : new int[][] { { 320, 240 }, { 426, 240 } }) {
			h.assertTrue(StarkGearLayout.W <= s[0] && StarkGearLayout.H <= s[1], "the screen fits " + s[0] + "x" + s[1]);
		}
		h.assertTrue(StarkGearLayout.PANEL_X >= StarkGearLayout.PREVIEW_X + StarkGearLayout.PREVIEW_W + 2, "panel clear of the preview");
		h.assertTrue(StarkGearLayout.PANEL_X + StarkGearLayout.PANEL_W <= StarkGearLayout.W - 6, "panel inside the screen");
		h.assertTrue(StarkGearLayout.PANEL_Y + StarkGearLayout.PANEL_H <= StarkGearLayout.INV_Y - 2, "panel above the inventory");
		h.assertTrue(StarkGearLayout.ROW_Y + 3 * StarkGearLayout.ROW_H <= StarkGearLayout.PANEL_Y + StarkGearLayout.PANEL_H,
				"three status rows fit the panel");
		h.assertTrue(13 + StarkGearLayout.HINT_LINES * StarkGearLayout.LINE_H <= StarkGearLayout.ROW_H, "two hint lines fit a row");
		h.assertTrue(StarkGearLayout.SLOT_Y + 18 <= StarkGearLayout.INV_Y, "the gear slot is above the inventory");
		h.assertTrue(StarkGearLayout.SLOT_Y >= StarkGearLayout.PREVIEW_Y + StarkGearLayout.PREVIEW_H, "and below the preview");
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.stark_gear.slot")) <= StarkGearLayout.SLOT_LABEL_W, "slot label fits");
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.stark_gear.title")) <= StarkGearLayout.W - 16, "title fits");

		int tw = StarkGearLayout.TEXT_W;
		for (String v : StarkGearLayout.allValueKeys()) {
			String text = String.format(t(v), "59:59");
			int cw = IronManUiLayout.approxWidth(text) + StarkGearLayout.CHIP_PAD;
			h.assertTrue(cw <= tw / 2, "value chip fits: " + v);
		}
		// every row the screen can build: its label fits beside its own value chip (glasses on / off, ready / cooling down)
		for (boolean g : new boolean[] { true, false }) {
			for (long readyAt : new long[] { 0L, 72_000L }) {
				for (StarkGearLayout.Row row : StarkGearLayout.rows(g, readyAt, 0L)) {
					String value = String.format(t(row.valueKey()), row.valueArgs());
					int cw = IronManUiLayout.approxWidth(value) + StarkGearLayout.CHIP_PAD;
					h.assertTrue(IronManUiLayout.approxWidth(t(row.labelKey())) + StarkGearLayout.LABEL_GAP + cw <= tw,
							"label fits beside its chip: " + row.labelKey() + " / " + value);
				}
			}
		}
		for (String k : StarkGearLayout.allHintKeys()) {
			h.assertTrue(IronManUiLayout.wrapLines(t(k), tw, IronManUiLayout::approxWidth) <= StarkGearLayout.HINT_LINES,
					"hint wraps in two lines: " + k);
		}
		// the rows the screen builds use only keys that exist and fit
		for (boolean g : new boolean[] { true, false }) {
			for (StarkGearLayout.Row row : StarkGearLayout.rows(g, 24_000L, 0L)) {
				t(row.labelKey());
				t(row.valueKey());
				t(row.hintKey());
			}
		}
		h.assertTrue(StarkGearLayout.rows(true, 0L, 10L)[2].tone() == StarkGearLayout.Tone.GOOD, "glasses + ready = ARMED");
		h.assertTrue(StarkGearLayout.rows(false, 0L, 10L)[0].tone() == StarkGearLayout.Tone.BAD, "no glasses = calling OFFLINE");

		// HUD chips and the picker hint
		for (String k : new String[] { "hud.projecthero.ironman.calling_offline", "hud.projecthero.ironman.phoenix_armed" }) {
			h.assertTrue(IronManUiLayout.approxWidth(t(k)) + 7 <= IronManUiLayout.HUD_W, "HUD chip fits: " + k);
		}
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_call.needs_glasses_hint")) <= 320 - 12,
				"picker hint fits 320 wide");
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_call.needs_glasses")) <= IronManUiLayout.CARD_INFO_W - 6,
				"card location line fits");
		t("message.projecthero.ironman.glasses_needed");
		t("item.projecthero.stark_glasses");
		t("projecthero.guide.iron_man.glasses.body");
		t("projecthero.guide.iron_man.phoenix.body");
		h.succeed();
	}

	/** Shared by the older call / Phoenix tests: give a test Tony the glasses so a call is allowed. */
	public static void wearGlasses(ServerPlayer p) {
		StarkGear.setGlasses(p, glasses());
	}
}
