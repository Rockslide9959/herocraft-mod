package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.14.21 Iron Man UI redesign. The screens themselves are client-only, so these check the pure layout + format
 * rules they all draw with ({@link IronManUiLayout}) against the smallest supported scaled screen, 320 x 240 (GUI
 * scale 4), using the real English strings from {@code en_us.json} and a pessimistic copy of the vanilla font's
 * glyph widths -- so a string that passes here also fits on screen.
 */
public class IronManUiV01421GameTests implements FabricGameTest {
	private static final int W = IronManUiLayout.MIN_W;
	private static final int H = IronManUiLayout.MIN_H;
	/** The scaled sizes we care about: GUI scale 4 at 1280x960, scale 3 at 1280x720, scale 2 at 1920x1080. */
	private static final int[][] SCREENS = { { 320, 240 }, { 426, 240 }, { 480, 270 }, { 960, 540 } };

	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManUiV01421GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	private static String f(String key, Object... args) {
		return String.format(java.util.Locale.ROOT, t(key), args);
	}

	private static int w(String s) {
		return IronManUiLayout.approxWidth(s);
	}

	private static List<IronManSuit> suits() {
		return new ArrayList<>(IronManSuits.all());
	}

	// ------------------------------------------------------------------ formats

	@GameTest(template = EMPTY_STRUCTURE)
	public void hudNumberFormatsKeepCommaDecimals(GameTestHelper helper) {
		helper.assertTrue("76,67%".equals(IronManUiLayout.pct(0.7667f)), "pct uses two comma decimals: " + IronManUiLayout.pct(0.7667f));
		helper.assertTrue("100,00%".equals(IronManUiLayout.pct(1.5f)), "pct clamps to 100");
		helper.assertTrue("(7667 / 10000)".equals(IronManUiLayout.amount(7667.4f, 10000f)), "amount = (cur / max)");
		helper.assertTrue("4,5s".equals(IronManUiLayout.secs(90)), "secs one comma decimal: " + IronManUiLayout.secs(90));
		helper.assertTrue("02:11".equals(IronManUiLayout.mmss(2601)), "mm:ss rounds up: " + IronManUiLayout.mmss(2601));
		helper.assertTrue(IronManUiLayout.ceilSecs(21) == 2 && IronManUiLayout.ceilSecs(20) == 1 && IronManUiLayout.ceilSecs(0) == 0,
				"cooldown seconds round up");
		helper.assertTrue(Math.abs(IronManUiLayout.cooldownFraction(200, 100, 150) - 0.5f) < 1e-6f, "sweep half way");
		helper.assertTrue(IronManUiLayout.cooldownFraction(200, 100, 200) == 0f, "sweep done at ready-at");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ellipsizeAlwaysFits(GameTestHelper helper) {
		String s = t("hud.projecthero.ironman.offline");
		for (int max : new int[] { 10, 40, 90, 150, 400 }) {
			String e = IronManUiLayout.ellipsize(s, max, IronManUiLayout::approxWidth);
			helper.assertTrue(w(e) <= max, "ellipsized text fits " + max + ": '" + e + "'");
		}
		helper.assertTrue(s.equals(IronManUiLayout.ellipsize(s, 1000, IronManUiLayout::approxWidth)), "a fitting string is untouched");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helmet HUD

	@GameTest(template = EMPTY_STRUCTURE)
	public void hudMeterLinesFitTheBlock(GameTestHelper helper) {
		int bw = IronManUiLayout.HUD_W;
		helper.assertTrue(IronManUiLayout.HUD_X + bw <= W / 2, "the HUD block stays in the left half at 320 wide");
		for (IronManSuit suit : suits()) {
			float cap = suit.energyCapacity();
			float maxI = IronManEnergy.maxIntegrity(suit.id());
			String e = IronManUiLayout.pct(1f) + " " + IronManUiLayout.amount(cap, cap);
			String i = IronManUiLayout.pct(1f) + " " + IronManUiLayout.amount(maxI, maxI);
			helper.assertTrue(w(t("hud.projecthero.ironman.energy_short")) + 6 + w(e) <= bw,
					"ENERGY line fits for " + suit.id() + ": " + e);
			helper.assertTrue(w(t("hud.projecthero.ironman.integrity_short")) + 6 + w(i) <= bw,
					"INTEGRITY line fits for " + suit.id() + ": " + i);
			float heat = IronManAbilities.flamethrowerMaxHeat(suit);
			String h = IronManUiLayout.pct(1f) + " " + IronManUiLayout.amount(heat, heat);
			helper.assertTrue(w(t("hud.projecthero.ironman.heat")) + 6 + w(h) <= bw, "HEAT line fits: " + h);
			helper.assertTrue(IronManUiLayout.approxWidth(t(suit.nameKey()), true) + 8 + w(f("hud.projecthero.ironman.alt_spd", 320, 99)) <= bw,
					"name + ALT / SPD fit on the header for " + suit.id());
		}
		helper.assertTrue(w(t("hud.projecthero.ironman.overloaded")) + 6 + w(IronManUiLayout.secs(600)) <= bw, "OVERLOAD line fits");
		helper.assertTrue(w(t("hud.projecthero.ironman.ability.wrist_laser")) + 6 + w(IronManUiLayout.secs(80)) <= bw, "laser line fits");
		helper.assertTrue(w(t("hud.projecthero.ironman.missiles_short")) + 6
				+ w(f("hud.projecthero.ironman.reload", IronManUiLayout.secs(400))) <= bw, "missile reload line fits");
		helper.assertTrue(w(t("hud.projecthero.ironman.critical_short")) + 6 <= bw, "the critical banner fits without wrapping");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hudChipsWrapInsideTheBlock(GameTestHelper helper) {
		int bw = IronManUiLayout.HUD_W;
		String[] chips = {
				t("hud.projecthero.ironman.chip.highlight_on") + " " + IronManUiLayout.secs(400),
				f("hud.projecthero.ironman.chip.scan", 48),
				f("hud.projecthero.ironman.chip.wheel", t("hud.projecthero.ironman.ability.supersonic_flight")),
				t("hud.projecthero.ironman.chip.blades"),
				f("hud.projecthero.ironman.missiles", 12),
				t("hud.projecthero.ironman.chip.visor_open"),
				f("hud.projecthero.ironman.phoenix_cooldown", "59:59"),
				f("hud.projecthero.ironman.call_armor", "C") };
		int[] widths = new int[chips.length];
		for (int i = 0; i < chips.length; i++) {
			widths[i] = Math.min(bw, w(chips[i]) + 7);
			helper.assertTrue(w(chips[i]) + 7 <= bw, "chip '" + chips[i] + "' fits the block unclipped");
		}
		List<Rect> rects = IronManUiLayout.flowChips(widths, IronManUiLayout.HUD_X, 100, bw, 2, 11);
		for (Rect r : rects) {
			helper.assertTrue(r.x() >= IronManUiLayout.HUD_X && r.right() <= IronManUiLayout.HUD_X + bw, "chip inside block: " + r);
		}
		for (int a = 0; a < rects.size(); a++) {
			for (int b = a + 1; b < rects.size(); b++) {
				helper.assertFalse(rects.get(a).overlaps(rects.get(b)), "chips never overlap");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void abilityStripSitsAboveTheHotbar(GameTestHelper helper) {
		for (int[] s : SCREENS) {
			Rect[] slots = IronManUiLayout.abilityStrip(s[0], s[1]);
			helper.assertTrue(slots.length == 6, "six slots");
			int hotbarL = s[0] / 2 - 91;
			int hotbarR = s[0] / 2 + 91;
			for (int i = 0; i < 6; i++) {
				Rect r = slots[i];
				helper.assertTrue(r.insideScreen(s[0], s[1]), "slot on screen at " + s[0] + "x" + s[1]);
				helper.assertTrue(r.x() >= hotbarL && r.right() <= hotbarR, "strip no wider than the hotbar");
				// hotbar (22) + XP bar + hearts + armour row end 49 px above the bottom
				helper.assertTrue(r.bottom() <= s[1] - 49, "strip clears the hotbar, hearts and armour row");
				if (i > 0) {
					helper.assertTrue(r.x() >= slots[i - 1].right(), "slots in order, no overlap");
				}
			}
			// the HUD block's worst case (header + 2 gauges + clock + 6 context meters + 2 chip rows) stays above it
			int blockBottom = IronManUiLayout.HUD_Y + 12 + 2 * IronManUiLayout.GAUGE_ROW_H + 11 + 3 * IronManUiLayout.GAUGE_ROW_H + 26;
			helper.assertTrue(blockBottom <= slots[0].y() || IronManUiLayout.HUD_X + IronManUiLayout.HUD_W <= slots[0].x(),
					"a busy HUD block does not run into the ability strip at " + s[0] + "x" + s[1]);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ call armour grid

	@GameTest(template = EMPTY_STRUCTURE)
	public void callGridFitsAndScrolls(GameTestHelper helper) {
		int count = suits().size();
		for (int[] s : SCREENS) {
			int cols = IronManUiLayout.gridColumns(s[0]);
			helper.assertTrue(cols >= 1, "at least one column");
			helper.assertTrue(IronManUiLayout.gridViewportHeight(s[1]) >= IronManUiLayout.CARD_H, "a whole card is always visible");
			for (int i = 0; i < count; i++) {
				Rect r = IronManUiLayout.card(i, s[0], 0);
				helper.assertTrue(r.x() >= 0 && r.right() <= s[0], "card " + i + " inside the width at " + s[0]);
				int scroll = IronManUiLayout.scrollToShow(i, s[0], s[1], 0, count);
				Rect shown = IronManUiLayout.card(i, s[0], scroll);
				helper.assertTrue(shown.y() >= IronManUiLayout.GRID_TOP
						&& shown.bottom() <= IronManUiLayout.GRID_TOP + IronManUiLayout.gridViewportHeight(s[1]),
						"keyboard focus scrolls card " + i + " fully into view at " + s[0] + "x" + s[1]);
				helper.assertTrue(scroll <= IronManUiLayout.gridMaxScroll(count, s[0], s[1]), "scroll within range");
			}
			helper.assertTrue(IronManUiLayout.GRID_TOP + IronManUiLayout.gridViewportHeight(s[1]) <= s[1] - 36,
					"the viewport leaves room for the hint line and the Cancel button");
		}
		helper.assertTrue(IronManUiLayout.gridColumns(W) == 2, "two columns at 320 wide");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void callCardTextFits(GameTestHelper helper) {
		int iw = IronManUiLayout.CARD_INFO_W;
		helper.assertTrue(IronManUiLayout.CARD_INFO_X + iw <= IronManUiLayout.CARD_W, "info column inside the card");
		for (IronManSuit suit : suits()) {
			helper.assertTrue(w(t(suit.nameKey())) + 1 <= iw, "suit name fits a card: " + suit.id());
			float cap = suit.energyCapacity();
			float maxI = IronManEnergy.maxIntegrity(suit.id());
			helper.assertTrue(w(IronManUiLayout.exact(cap, cap)) <= iw - 6, "exact charge fits inside the Slab");
			helper.assertTrue(w(IronManUiLayout.exact(maxI, maxI)) <= iw - 6, "exact integrity fits inside the Slab");
		}
		helper.assertTrue(w(t("screen.projecthero.suit_call.charge")) + 6 + w("100,00%") <= iw, "CHARGE + % fit");
		helper.assertTrue(w(t("screen.projecthero.suit_call.integrity")) + 6 + w("100,00%") <= iw, "INTEGRITY + % fit");
		helper.assertTrue(w(f("screen.projecthero.suit_call.on_platform", 999)) <= iw - 6, "platform distance line fits");
		helper.assertTrue(w(f("screen.projecthero.suit_call.send_back", 999)) <= iw - 6, "send-back line fits (v0.14.27)");
		for (String k : new String[] { "screen.projecthero.suit_call.in_inventory", "screen.projecthero.suit_call.pending",
				"screen.projecthero.suit_call.unreachable" }) {
			helper.assertTrue(w(t(k)) <= iw - 6, "location line fits: " + k);
		}
		helper.assertTrue("screen.projecthero.suit_call.pending".equals(IronManUiLayout.locationKey(7)),
				"an unknown (newer) source reads as on its way");
		helper.assertTrue(w(t("screen.projecthero.suit_call.hint")) <= W - 12, "hint fits at 320");
		helper.succeed();
	}

	// ------------------------------------------------------------------ blueprint track

	@GameTest(template = EMPTY_STRUCTURE)
	public void blueprintTrackFits(GameTestHelper helper) {
		List<IronManSuit> marks = suits();
		int n = marks.size();
		for (int[] s : SCREENS) {
			Rect[] nodes = IronManUiLayout.trackNodes(n, s[0], s[1] / 2);
			int panelL = s[0] / 2 - (Math.min(s[0], 420) - 16) / 2;
			int panelR = panelL + Math.min(s[0], 420) - 16;
			int labelMax = IronManUiLayout.trackLabelMaxWidth(n, s[0]);
			for (int i = 0; i < n; i++) {
				helper.assertTrue(nodes[i].x() >= panelL + 4 && nodes[i].right() <= panelR - 4, "node inside the panel at " + s[0]);
				if (i > 0) {
					helper.assertTrue(nodes[i].x() - nodes[i - 1].right() >= 12, "room for the connecting line");
				}
				int lw = Math.min(labelMax, w(t(marks.get(i).nameKey())));
				int cx = nodes[i].x() + IronManUiLayout.NODE / 2;
				helper.assertTrue(cx - lw / 2 >= 0 && cx + lw / 2 <= s[0], "label on screen");
				if (i >= 2) {
					int pcx = nodes[i - 2].x() + IronManUiLayout.NODE / 2;
					int plw = Math.min(labelMax, w(t(marks.get(i - 2).nameKey())));
					helper.assertTrue(pcx + plw / 2 + 2 <= cx - lw / 2, "same-side labels never overlap");
				}
				helper.assertTrue(w(t(marks.get(i).nameKey())) <= labelMax, "mark name never needs ellipsizing at " + s[0]);
			}
		}
		helper.assertTrue(w(f("screen.projecthero.blank_blueprint.locked_short", "Mark VII")) <= W - 32, "locked line fits");
		helper.assertTrue(w(f("screen.projecthero.blank_blueprint.make", "Mark VII")) <= W - 32, "stamp line fits");
		helper.assertTrue(w(t("screen.projecthero.blank_blueprint.subtitle")) <= W - 28, "subtitle fits");
		helper.succeed();
	}

	// ------------------------------------------------------------------ weapon wheel

	@GameTest(template = EMPTY_STRUCTURE)
	public void weaponWheelHitTestMatchesTheDrawnWedges(GameTestHelper helper) {
		int n = IronManAbilities.WEAPON_WHEEL_SECTORS.length;
		double r = 70;
		for (int i = 0; i < n; i++) {
			double c = IronManUiLayout.sectorCentre(i, n);
			helper.assertTrue(IronManUiLayout.wheelSector(Math.cos(c) * r, Math.sin(c) * r, n, 20) == i,
					"pointing at wedge " + i + "'s centre selects it");
			double half = Math.PI / n;
			for (double off : new double[] { -half + 0.02, half - 0.02 }) {
				helper.assertTrue(IronManUiLayout.wheelSector(Math.cos(c + off) * r, Math.sin(c + off) * r, n, 20) == i,
						"anywhere inside wedge " + i + " selects it (half-sector offset)");
			}
			helper.assertTrue(IronManUiLayout.wheelSector(Math.cos(c + half + 0.02) * r, Math.sin(c + half + 0.02) * r, n, 20) == (i + 1) % n,
					"just past the edge is the next wedge");
		}
		helper.assertTrue(IronManUiLayout.wheelSector(0, -70, n, 20) == 0, "straight up is sector 0");
		helper.assertTrue(IronManUiLayout.wheelSector(5, 5, n, 20) == -1, "the centre is a dead zone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void weaponWheelFitsAndLabelsFit(GameTestHelper helper) {
		for (int[] s : SCREENS) {
			int[] radii = IronManUiLayout.wheelRadii(s[0], s[1]);
			int cy = s[1] / 2;
			helper.assertTrue(cy - radii[1] - 18 >= 0, "title above the wheel stays on screen at " + s[0] + "x" + s[1]);
			helper.assertTrue(cy + radii[1] + 10 + 9 <= s[1], "hint below the wheel stays on screen");
			helper.assertTrue(radii[1] - radii[0] >= 26, "the ring holds an icon plus a label line");
			int tw = IronManUiLayout.wheelTextWidth(radii[0]);
			for (String sector : IronManAbilities.WEAPON_WHEEL_SECTORS) {
				helper.assertTrue(w(t("hud.projecthero.ironman.ability." + sector)) <= tw,
						"name fits the centre disc: " + sector + " (" + tw + " px)");
				helper.assertTrue(IronManUiLayout.wrapLines(t("screen.projecthero.weapon_wheel.desc." + sector), tw,
						IronManUiLayout::approxWidth) <= 2, "description wraps to at most two lines: " + sector);
			}
		}
		helper.assertTrue(w(t("screen.projecthero.weapon_wheel.hint")) <= W - 12, "hint fits at 320");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Stark Fabricator + Suit Platform

	@GameTest(template = EMPTY_STRUCTURE)
	public void fabricatorChecklistCountsLikeTheMachine(GameTestHelper helper) {
		List<ItemStack> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			grid.add(ItemStack.EMPTY);
		}
		grid.set(0, new ItemStack(Items.IRON_INGOT, 3));
		grid.set(4, new ItemStack(Items.IRON_INGOT, 5));
		grid.set(8, new ItemStack(Items.GOLD_INGOT, 2));
		helper.assertTrue(IronManUiLayout.have(grid, Items.IRON_INGOT) == 8, "have sums every input slot");
		helper.assertTrue(IronManUiLayout.have(grid, Items.DIAMOND) == 0, "missing = 0");
		List<ItemStack> withOutput = new ArrayList<>(grid);
		withOutput.add(new ItemStack(Items.IRON_INGOT, 64)); // slot 9+ (blueprint / output) is not an input
		helper.assertTrue(IronManUiLayout.have(withOutput, Items.IRON_INGOT) == 8, "only the nine inputs count");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fabricatorLayoutFits(GameTestHelper helper) {
		int fw = IronManUiLayout.FAB_W;
		helper.assertTrue(IronManUiLayout.FAB_H <= H, "the fabricator panel fits 240 high");
		for (int i = 0; i < 4; i++) {
			Rect tab = IronManUiLayout.fabTab(i);
			helper.assertTrue(tab.right() <= fw - 6 && tab.h() >= 16 && tab.w() >= 40, "tab " + i + " is big and inside the panel");
			for (int[] sl : IronManUiLayout.FAB_SLOTS) {
				helper.assertFalse(tab.overlaps(new Rect(sl[0] - 1, sl[1] - 1, 18, 18)), "tab clear of every slot");
			}
			String piece = t("screen.projecthero.stark_fabricator.piece." + new String[] { "helmet", "chestplate", "leggings", "boots" }[i]);
			helper.assertTrue(w(f("screen.projecthero.stark_fabricator.needs_short", piece)) <= IronManUiLayout.FAB_BOX_W - 8 - IronManUiLayout.FAB_MORE_W - 6,
					"the checklist header names the piece (tabs are icon-only): " + piece);
		}
		Rect box = new Rect(IronManUiLayout.FAB_BOX_X, IronManUiLayout.FAB_BOX_Y, IronManUiLayout.FAB_BOX_W, IronManUiLayout.FAB_BOX_H);
		helper.assertTrue(box.bottom() < IronManUiLayout.FAB_INV_Y - 1, "checklist ends above the inventory");
		helper.assertTrue(box.y() > IronManUiLayout.fabTab(0).bottom(), "checklist below the tabs");
		int maxInputs = 0;
		for (FabricationRecipe r : FabricatorRecipes.all()) {
			if (r.result().getItem() instanceof IronManArmorItem) {
				maxInputs = Math.max(maxInputs, r.inputs().size());
				for (FabricationRecipe.Input in : r.inputs()) {
					helper.assertTrue(w(in.count() + "/" + in.count()) <= IronManUiLayout.FAB_CHECK_CELL_W - 12,
							"have/need text fits a checklist cell");
				}
			}
		}
		helper.assertTrue(maxInputs > 0 && maxInputs <= 12, "every armour recipe fits the 4 x 3 checklist (max " + maxInputs + ")");
		for (int i = 0; i < maxInputs; i++) {
			Rect c = IronManUiLayout.checklistCell(i, box.x() + 4, IronManUiLayout.FAB_CHECK_TOP, IronManUiLayout.FAB_CHECK_PER_ROW,
					IronManUiLayout.FAB_CHECK_CELL_W, IronManUiLayout.FAB_CHECK_ROW_H);
			helper.assertTrue(c.right() <= box.right() && c.y() + 8 <= box.bottom(), "checklist cell " + i + " inside the box");
		}
		helper.assertTrue(w(t("screen.projecthero.stark_fabricator.view_more")) <= IronManUiLayout.FAB_MORE_W - 4, "View more fits");
		helper.assertFalse(t("screen.projecthero.stark_fabricator.pick_piece").contains("→"), "no misleading arrow");
		for (String k : new String[] { "ready", "low_energy", "missing", "idle" }) {
			helper.assertTrue(w(t("screen.projecthero.stark_fabricator.status." + k)) + 6 + w("50000 / 50000") <= fw - 20,
					"status '" + k + "' and the energy readout share one line");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fabricatorAndPlatformMenusMatchTheirScreens(GameTestHelper helper) {
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var fab = new com.projecthero.mod.ironman.fabricator.StarkFabricatorMenu(1, player.getInventory(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
		for (int i = 0; i < IronManUiLayout.FAB_SLOTS.length; i++) {
			var sl = fab.slots.get(i);
			helper.assertTrue(sl.x == IronManUiLayout.FAB_SLOTS[i][0] && sl.y == IronManUiLayout.FAB_SLOTS[i][1],
					"fabricator slot " + i + " where the texture's well is (" + sl.x + "," + sl.y + ")");
		}
		for (int i = IronManUiLayout.FAB_SLOTS.length; i < fab.slots.size(); i++) {
			var sl = fab.slots.get(i);
			helper.assertTrue(sl.y >= IronManUiLayout.FAB_INV_Y && sl.y + 17 <= IronManUiLayout.FAB_H - 4 && sl.x + 17 <= IronManUiLayout.FAB_W,
					"player slot " + i + " inside the 200 x 236 panel");
			helper.assertTrue(sl.y >= IronManUiLayout.FAB_BOX_Y + IronManUiLayout.FAB_BOX_H, "player slots below the checklist");
		}
		var plat = new com.projecthero.mod.ironman.fabricator.IronManSuitPlatformMenu(2, player.getInventory(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
		for (int i = 0; i < 4; i++) {
			var sl = plat.slots.get(i);
			helper.assertTrue(sl.x == 53 + i * 20 && sl.y == 32, "platform armour slot " + i + " unchanged");
		}
		for (int i = 4; i < plat.slots.size(); i++) {
			helper.assertTrue(plat.slots.get(i).y + 17 <= IronManUiLayout.PLAT_H - 2, "platform player slot inside the panel");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void platformMetersFit(GameTestHelper helper) {
		int mw = IronManUiLayout.PLAT_METER_W;
		helper.assertTrue(IronManUiLayout.PLAT_METER_X + mw <= IronManUiLayout.PLAT_W - 5, "meters inside the panel");
		helper.assertTrue(IronManUiLayout.PLAT_H <= H, "the platform panel fits 240 high");
		for (IronManSuit suit : suits()) {
			float cap = suit.energyCapacity();
			float maxI = IronManEnergy.maxIntegrity(suit.id());
			helper.assertTrue(w(IronManUiLayout.exact(cap, cap)) <= mw - 6, "exact charge fits the Slab");
			helper.assertTrue(w(IronManUiLayout.exact(maxI, maxI)) <= mw - 6, "exact integrity fits the Slab");
			helper.assertTrue(w(t(suit.nameKey())) <= 80, "stored mark name fits over the slots");
		}
		helper.assertTrue(w(t("screen.projecthero.suit_platform.empty")) <= 80, "'no suit' fits over the slots");
		helper.assertTrue(w("+10/s") <= 30 && w(t("screen.projecthero.suit_platform.regen_short")) <= 30, "regen plate text fits");
		for (String k : new String[] { "screen.projecthero.suit_platform.deploy", "screen.projecthero.suit_platform.retrieve" }) {
			helper.assertTrue(w(t(k)) <= 57 - 6, "button label fits: " + k);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void guiTexturesAreSized(GameTestHelper helper) {
		for (Map.Entry<String, int[]> e : Map.of("stark_fabricator", new int[] { 256, 256 },
				"iron_man_suit_platform", new int[] { 256, 256 }).entrySet()) {
			try (InputStream in = IronManUiV01421GameTests.class.getResourceAsStream(
					"/assets/projecthero/textures/gui/" + e.getKey() + ".png")) {
				helper.assertTrue(in != null, "texture present: " + e.getKey());
				byte[] head = in.readNBytes(24);
				int pw = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
				int ph = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16) | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
				helper.assertTrue(pw == e.getValue()[0] && ph == e.getValue()[1], e.getKey() + " is " + pw + "x" + ph);
			} catch (java.io.IOException ex) {
				throw new IllegalStateException(ex);
			}
		}
		helper.succeed();
	}
}
