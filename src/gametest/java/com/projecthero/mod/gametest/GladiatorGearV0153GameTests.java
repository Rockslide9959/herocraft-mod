package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.gladiator.GladiatorGear;
import com.projecthero.mod.hulk.gladiator.GladiatorGearLayout;
import com.projecthero.mod.hulk.gladiator.GladiatorGearMenu;
import com.projecthero.mod.hulk.gladiator.GladiatorItems;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3: Gladiator Hulk's gear -- the seven items and their recipes, the slots (each takes only its piece, no
 * duplication through shift-click), the full-kit / Gladiator Hulk rule, the 10% damage cut, the screen refused to
 * non-Gamma players and to the Hulk (gear locked while he is out), the gear surviving death and relogs, the thrown-weapon
 * flags clearing, and the screen's text fitting.
 */
public class GladiatorGearV0153GameTests implements FabricGameTest {
	private static final String[] IDS = { "gladiator_helmet", "gladiator_pauldron", "gladiator_harness", "gladiator_bracers",
			"gladiator_kilt", "gladiator_hammer", "gladiator_axe" };

	private static ServerPlayer plain(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = h.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.getInventory().clearContent();
		return p;
	}

	/** A Gamma player as Banner, in a cleared room (the Hulk grows to 3.2 blocks). */
	private static ServerPlayer banner(GameTestHelper h) {
		ServerPlayer p = plain(h);
		BlockPos base = BlockPos.containing(p.position());
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 5, 2))) {
			h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		h.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		return p;
	}

	private static void hulkOut(GameTestHelper h, ServerPlayer p) {
		Hulk.setRage(p, 100.0f);
		Hulk.tryTransform(p);
		h.assertTrue(Hulk.isHulk(p), "precondition: he is the Hulk");
	}

	private static void fullKit(ServerPlayer p) {
		for (int i = 0; i < GladiatorGear.SLOTS; i++) {
			GladiatorGear.set(p, i, new ItemStack(GladiatorItems.forSlot(i)));
		}
	}

	// ------------------------------------------------------------------ items + recipes

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSevenPiecesAreCraftable(GameTestHelper h) {
		for (int i = 0; i < IDS.length; i++) {
			Item item = BuiltInRegistries.ITEM.get(ProjectHeroMod.id(IDS[i]));
			h.assertTrue(item == GladiatorItems.forSlot(i), "registered: " + IDS[i]);
			h.assertTrue(new ItemStack(item).getMaxStackSize() == 1, "stack size 1: " + IDS[i]);
			var holder = h.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(IDS[i]));
			h.assertTrue(holder.isPresent(), "a recipe exists: " + IDS[i]);
			ItemStack result = holder.get().value().getResultItem(h.getLevel().registryAccess());
			h.assertTrue(result.is(item), "and it makes the piece: " + IDS[i]);
		}
		h.assertTrue(GladiatorItems.forSlot(GladiatorGear.HAMMER) == GladiatorItems.HAMMER
				&& GladiatorItems.forSlot(GladiatorGear.AXE) == GladiatorItems.AXE, "slot order");
		h.succeed();
	}

	// ------------------------------------------------------------------ slots

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachSlotTakesOnlyItsOwnPiece(GameTestHelper h) {
		ServerPlayer p = banner(h);
		for (int slot = 0; slot < GladiatorGear.SLOTS; slot++) {
			for (int piece = 0; piece < GladiatorGear.SLOTS; piece++) {
				boolean ok = GladiatorGear.accepts(slot, new ItemStack(GladiatorItems.forSlot(piece)));
				h.assertTrue(ok == (slot == piece), "slot " + slot + " vs piece " + piece);
			}
			h.assertFalse(GladiatorGear.accepts(slot, new ItemStack(Items.IRON_HELMET)), "vanilla gear refused");
		}
		GladiatorGear.set(p, GladiatorGear.HELMET, new ItemStack(GladiatorItems.AXE));
		h.assertTrue(GladiatorGear.equippedCount(p) == 0, "the API refuses a piece in the wrong slot too");

		GladiatorGearMenu menu = new GladiatorGearMenu(1, p.getInventory());
		h.assertFalse(menu.slots.get(GladiatorGear.HELMET).mayPlace(new ItemStack(GladiatorItems.HAMMER)), "menu slot refuses the wrong piece");
		h.assertTrue(menu.slots.get(GladiatorGear.HAMMER).mayPlace(new ItemStack(GladiatorItems.HAMMER)), "and takes its own");
		// shift-click from the inventory goes to the matching slot, without duplicating
		p.getInventory().add(new ItemStack(GladiatorItems.KILT));
		// menu slots: 0-6 gear, 7-33 the main inventory (inventory 9-35), 34-42 the hotbar (inventory 0-8)
		int inv = p.getInventory().findSlotMatchingItem(new ItemStack(GladiatorItems.KILT));
		int menuSlot = inv < 9 ? GladiatorGear.SLOTS + 27 + inv : GladiatorGear.SLOTS + inv - 9;
		h.assertTrue(menu.slots.get(menuSlot).getItem().is(GladiatorItems.KILT), "found the kilt's menu slot");
		menu.quickMoveStack(p, menuSlot);
		h.assertTrue(GladiatorGear.get(p, GladiatorGear.KILT).is(GladiatorItems.KILT), "shift-click puts the kilt in its slot");
		h.assertTrue(p.getInventory().countItem(GladiatorItems.KILT) == 0, "and out of the inventory");
		menu.quickMoveStack(p, GladiatorGear.KILT);
		h.assertTrue(GladiatorGear.get(p, GladiatorGear.KILT).isEmpty(), "shift-click on the slot takes it off");
		h.assertTrue(p.getInventory().countItem(GladiatorItems.KILT) == 1, "back in the inventory, exactly one");
		h.succeed();
	}

	// ------------------------------------------------------------------ the full-kit rule

	@GameTest(template = EMPTY_STRUCTURE)
	public void gladiatorHulkNeedsAllSevenAndTheHulk(GameTestHelper h) {
		ServerPlayer p = banner(h);
		for (int i = 0; i < GladiatorGear.SLOTS - 1; i++) {
			GladiatorGear.set(p, i, new ItemStack(GladiatorItems.forSlot(i)));
		}
		h.assertTrue(GladiatorGear.equippedCount(p) == 6, "six on");
		h.assertFalse(GladiatorGear.hasFullKit(p), "six is not the full kit");
		GladiatorGear.set(p, GladiatorGear.AXE, new ItemStack(GladiatorItems.AXE));
		h.assertTrue(GladiatorGear.hasFullKit(p) && GladiatorGear.equippedCount(p) == 7, "seven is");
		h.assertFalse(GladiatorGear.isGladiator(p), "but Banner is not Gladiator Hulk");
		hulkOut(h, p);
		h.assertTrue(GladiatorGear.isGladiator(p), "the Hulk with the full kit is");
		ServerPlayer other = banner(h);
		hulkOut(h, other);
		h.assertFalse(GladiatorGear.isGladiator(other), "a Hulk without it is not");
		h.succeed();
	}

	// ------------------------------------------------------------------ damage

	@GameTest(template = EMPTY_STRUCTURE)
	public void gladiatorHulkTakesTenPercentLess(GameTestHelper h) {
		ServerPlayer glad = banner(h);
		fullKit(glad);
		h.assertTrue(GladiatorGear.reduce(glad, 10.0f) == 10.0f, "Banner: no cut");
		hulkOut(h, glad);
		h.assertTrue(Math.abs(GladiatorGear.reduce(glad, 10.0f) - 9.0f) < 1e-4f, "Gladiator Hulk: 10 -> 9");
		h.assertTrue(GladiatorGear.DAMAGE_FACTOR == 0.9f, "the factor is 0.9");
		// the hook: ordinary damage is vetoed and re-applied smaller (the Hulk's reduce-and-re-hurt pattern)
		h.assertFalse(HulkDamage.allowDamage(glad, glad.damageSources().generic(), 10.0f), "the hit is cut (re-applied at 90%)");
		// still immune to what the Hulk ignores
		h.assertFalse(HulkDamage.allowDamage(glad, glad.damageSources().fall(), 10.0f), "falls still do nothing");

		ServerPlayer hulk = banner(h);
		hulkOut(h, hulk);
		h.assertTrue(GladiatorGear.reduce(hulk, 10.0f) == 10.0f, "a plain Hulk: no cut");
		h.assertTrue(HulkDamage.allowDamage(hulk, hulk.damageSources().generic(), 10.0f), "a plain Hulk takes ordinary hits in full");
		h.succeed();
	}

	// ------------------------------------------------------------------ the screen

	@GameTest(template = EMPTY_STRUCTURE)
	public void theScreenIsBannersOnly(GameTestHelper h) {
		ServerPlayer nobody = plain(h);
		h.assertTrue("message.projecthero.hulk.gladiator.no_power".equals(GladiatorGear.refusal(nobody)), "not a Gamma player: refused");
		h.assertFalse(GladiatorGear.openMenu(nobody), "and it does not open");

		ServerPlayer p = banner(h);
		h.assertTrue(GladiatorGear.refusal(p) == null, "Banner may open it");
		h.assertTrue(GladiatorGear.openMenu(p), "it opens for Banner");
		h.assertTrue(p.containerMenu instanceof GladiatorGearMenu, "the Gladiator Gear menu is open");
		p.closeContainer();

		GladiatorGearMenu menu = new GladiatorGearMenu(2, p.getInventory());
		fullKit(p);
		h.assertTrue(menu.stillValid(p) && !menu.locked(), "open and unlocked as Banner");
		hulkOut(h, p);
		h.assertTrue("message.projecthero.hulk.gladiator.locked".equals(GladiatorGear.refusal(p)), "the Hulk: locked");
		h.assertFalse(GladiatorGear.openMenu(p), "it does not open for the Hulk");
		h.assertFalse(p.containerMenu instanceof GladiatorGearMenu, "no Gladiator Gear menu");
		h.assertTrue(menu.locked() && !menu.stillValid(p), "a menu left open is locked and closes when he changes");
		h.assertFalse(menu.slots.get(GladiatorGear.HELMET).mayPickup(p), "nothing comes off while he is the Hulk");
		h.assertTrue(menu.quickMoveStack(p, GladiatorGear.HAMMER).isEmpty() && GladiatorGear.hasFullKit(p), "not even by shift-click");
		h.succeed();
	}

	// ------------------------------------------------------------------ death + relog + thrown weapons

	@GameTest(template = EMPTY_STRUCTURE)
	public void theGearIsKeptOnDeathAndRelog(GameTestHelper h) {
		h.assertTrue(ModAttachments.GLADIATOR_GEAR.isPersistent() && ModAttachments.GLADIATOR_GEAR.copyOnDeath(),
				"the gear attachment is persistent and copied on death");
		h.assertFalse(ModAttachments.GLADIATOR_WEAPONS_AWAY.isPersistent() || ModAttachments.GLADIATOR_WEAPONS_AWAY.copyOnDeath(),
				"the thrown-weapon flags are neither");
		ServerPlayer p = banner(h);
		fullKit(p);
		// (Fabric copies copyOnDeath attachments in PlayerList.respawn, which a mock player can't go through; the flag
		// above is what it reads.) Nothing on death takes the gear off:
		h.assertTrue(GladiatorGear.hasFullKit(p), "the kit is on");
		CompoundTag tag = p.saveWithoutId(new CompoundTag());
		ServerPlayer fresh = plain(h);
		fresh.load(tag);
		h.assertTrue(GladiatorGear.hasFullKit(fresh), "and a save / load (relog)");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thrownWeaponFlagsClearOutOfHulkForm(GameTestHelper h) {
		ServerPlayer p = banner(h);
		fullKit(p);
		hulkOut(h, p);
		GladiatorGear.setWeaponAway(p, false, true);
		h.assertTrue(GladiatorGear.weaponAway(p, false) && !GladiatorGear.weaponAway(p, true), "hammer away, axe in hand");
		GladiatorGear.setWeaponAway(p, true, true);
		h.assertTrue(GladiatorGear.weaponAway(p, true), "both away");
		GladiatorGear.setWeaponAway(p, false, false);
		h.assertTrue(!GladiatorGear.weaponAway(p, false) && GladiatorGear.weaponAway(p, true), "hammer back");
		GladiatorGear.tick(p);
		h.assertTrue(GladiatorGear.weaponAway(p, true), "still the Hulk: the axe stays thrown");
		Hulk.revert(p, false);
		GladiatorGear.tick(p);
		h.assertFalse(GladiatorGear.weaponAway(p, true) || GladiatorGear.weaponAway(p, false), "back to Banner: both in hand again");
		GladiatorGear.setWeaponAway(p, true, true);
		GladiatorGear.clearWeapons(p); // what death and login do
		h.assertFalse(GladiatorGear.weaponAway(p, true), "cleared");
		h.succeed();
	}

	// ------------------------------------------------------------------ the screen's text

	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = GladiatorGearV0153GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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
	public void theGladiatorGearScreenFits(GameTestHelper h) {
		for (int[] s : new int[][] { { 320, 240 }, { 426, 240 } }) {
			h.assertTrue(GladiatorGearLayout.W <= s[0] && GladiatorGearLayout.H <= s[1], "the screen fits " + s[0] + "x" + s[1]);
		}
		h.assertTrue(GladiatorGearLayout.LEFT_X + GladiatorGearLayout.LEFT_W < GladiatorGearLayout.RIGHT_X, "panels don't overlap");
		h.assertTrue(GladiatorGearLayout.RIGHT_X + GladiatorGearLayout.RIGHT_W <= GladiatorGearLayout.W - 6, "right panel inside");
		h.assertTrue(GladiatorGearLayout.PANEL_Y + GladiatorGearLayout.PANEL_H <= GladiatorGearLayout.INV_Y - 2, "panels above the inventory");
		for (int i = 0; i < GladiatorGear.SLOTS; i++) {
			int x = GladiatorGearLayout.slotX(i);
			int y = GladiatorGearLayout.slotY(i);
			boolean weapon = i >= GladiatorGear.HAMMER;
			int px = weapon ? GladiatorGearLayout.RIGHT_X : GladiatorGearLayout.LEFT_X;
			int pw = weapon ? GladiatorGearLayout.RIGHT_W : GladiatorGearLayout.LEFT_W;
			h.assertTrue(x - 2 >= px && y - 2 >= GladiatorGearLayout.PANEL_Y && y + 18 <= GladiatorGearLayout.PANEL_Y + GladiatorGearLayout.PANEL_H
					&& GladiatorGearLayout.labelX(i) + GladiatorGearLayout.labelW(i) <= px + pw, "slot " + i + " inside its panel");
			h.assertTrue(IronManUiLayout.approxWidth(t(GladiatorGearLayout.labelKey(i))) <= GladiatorGearLayout.labelW(i),
					"label fits: " + GladiatorGearLayout.labelKey(i));
		}
		h.assertTrue(GladiatorGearLayout.slotY(GladiatorGear.AXE) + 18 <= GladiatorGearLayout.STATUS_Y, "status below the weapons");
		String status = String.format(t(GladiatorGearLayout.EQUIPPED_KEY), "7");
		h.assertTrue(IronManUiLayout.approxWidth(status) + 7 <= GladiatorGearLayout.STATUS_W, "status chip fits: " + status);
		h.assertTrue(GladiatorGearLayout.HINT_Y + GladiatorGearLayout.HINT_LINES * GladiatorGearLayout.LINE_H
				<= GladiatorGearLayout.PANEL_Y + GladiatorGearLayout.PANEL_H, "hint lines fit the panel");
		for (String k : GladiatorGearLayout.allHintKeys()) {
			h.assertTrue(IronManUiLayout.wrapLines(t(k), GladiatorGearLayout.STATUS_W, IronManUiLayout::approxWidth) <= GladiatorGearLayout.HINT_LINES,
					"hint wraps in " + GladiatorGearLayout.HINT_LINES + " lines: " + k);
		}
		h.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.gladiator_gear.title")) <= GladiatorGearLayout.W - 16, "title fits");
		for (String k : new String[] { "message.projecthero.hulk.gladiator.locked", "message.projecthero.hulk.gladiator.no_power",
				"item.projecthero.gladiator_gear.tooltip", "item.projecthero.gladiator_gear.tooltip2",
				"screen.projecthero.gladiator_gear.slot_tip", "projecthero.guide.hulk.gladiator", "projecthero.guide.hulk.gladiator.body" }) {
			t(k);
		}
		for (String id : IDS) {
			t("item.projecthero." + id);
		}
		h.succeed();
	}
}
