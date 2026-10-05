package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.sorter.SortingStationBlockEntity;
import com.projecthero.mod.ironman.sorter.SortingStationLayout;
import com.projecthero.mod.ironman.sorter.SortingStationMenu;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

/**
 * v0.14.29: the Stark Sorting Station screen used to be 262 px tall and clipped at GUI scale 3 on a 1280 x 720 window
 * (426 x 240 scaled). These check {@link SortingStationLayout} -- the numbers both the menu and the client screen use --
 * against the smallest supported scaled screen (320 x 240), the slots against the real menu, and the screen's text
 * against the pessimistic {@link IronManUiLayout#approxWidth} glyph widths, like {@code IronManUiV01421GameTests}.
 */
public class SortingStationLayoutGameTests implements FabricGameTest {
	private static final String K = "screen.projecthero.stark_sorting_station.";
	/** GUI scale 4 at 1280x960, scale 3 at 1280x720, scale 4 at 1920x1080, scale 3 at 1920x1080. */
	private static final int[][] SCREENS = { { 320, 240 }, { 426, 240 }, { 480, 270 }, { 640, 360 } };

	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = SortingStationLayoutGameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	private static int lines(String s, int maxW) {
		return IronManUiLayout.wrapLines(s, maxW, IronManUiLayout::approxWidth);
	}

	private static Rect slotRect(int[] xy) {
		return new Rect(xy[0] - 1, xy[1] - 1, 18, 18);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sortingStationPanelFitsEveryScreen(GameTestHelper h) {
		Rect panel = SortingStationLayout.panel();
		for (int[] s : SCREENS) {
			Rect placed = new Rect(SortingStationLayout.left(s[0]), SortingStationLayout.top(s[1]), panel.w(), panel.h());
			h.assertTrue(placed.insideScreen(s[0], s[1]), "panel " + panel.w() + "x" + panel.h() + " fits " + s[0] + "x" + s[1]);
		}
		h.assertTrue(panel.h() <= IronManUiLayout.MIN_H && panel.w() <= IronManUiLayout.MIN_W, "panel fits 320x240 outright");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sortingStationSlotsMatchTheMenuAndDoNotOverlap(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		SortingStationMenu menu = new SortingStationMenu(0, p.getInventory(), pos);
		int store = SortingStationBlockEntity.SIZE;
		int supply = SortingStationBlockEntity.SUPPLY_SLOTS;
		h.assertTrue(menu.slots.size() == store + supply + 36, "menu slot count " + menu.slots.size());
		List<Rect> cells = new ArrayList<>();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot sl = menu.slots.get(i);
			int[] want = i < store ? SortingStationLayout.storeSlot(i)
					: i < store + supply ? SortingStationLayout.supplySlot(i - store)
					: SortingStationLayout.playerSlot(i - store - supply);
			h.assertTrue(sl.x == want[0] && sl.y == want[1], "slot " + i + " at " + sl.x + "," + sl.y + " = layout " + want[0] + "," + want[1]);
			Rect r = slotRect(want);
			h.assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= SortingStationLayout.W && r.bottom() <= SortingStationLayout.H - 2,
					"slot " + i + " inside the panel: " + r);
			cells.add(r);
		}
		for (int i = 0; i < cells.size(); i++) {
			for (int j = i + 1; j < cells.size(); j++) {
				h.assertTrue(!cells.get(i).overlaps(cells.get(j)), "slots " + i + " and " + j + " overlap");
			}
		}
		Rect band = SortingStationLayout.band();
		Rect needs = SortingStationLayout.needs();
		for (int i = 0; i < cells.size(); i++) {
			h.assertTrue(!cells.get(i).overlaps(band), "slot " + i + " clear of the status band");
			h.assertTrue(!cells.get(i).overlaps(needs), "slot " + i + " clear of the needs text");
		}
		// the store's last row ends above the inventory label, which ends above the first inventory row
		int storeBottom = SortingStationLayout.STORE_Y + (SortingStationLayout.ROWS - 1) * 18 + 17;
		h.assertTrue(storeBottom < SortingStationLayout.INV_LABEL_Y, "store ends above the inventory label");
		h.assertTrue(SortingStationLayout.INV_LABEL_Y + 9 < SortingStationLayout.INV_Y - 1, "label ends above the inventory");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sortingStationRightColumnFits(GameTestHelper h) {
		Rect band = SortingStationLayout.band();
		Rect sort = SortingStationLayout.sortButton();
		Rect tidy = SortingStationLayout.tidyButton();
		Rect bar = SortingStationLayout.bar();
		Rect side = new Rect(SortingStationLayout.MAIN_W, 0, SortingStationLayout.SIDE_W, SortingStationLayout.H);
		for (Rect r : new Rect[] { sort, tidy, bar }) {
			h.assertTrue(r.x() >= band.x() + 1 && r.right() <= band.right() - 1 && r.y() >= band.y() + 1 && r.bottom() <= band.bottom() - 1,
					r + " inside the band " + band);
		}
		h.assertTrue(band.x() >= side.x() && band.right() <= side.right() && band.bottom() <= side.bottom() - 2, "band inside the column");
		h.assertTrue(!sort.overlaps(tidy) && !sort.overlaps(bar) && !tidy.overlaps(bar), "buttons and bar apart");
		int statusBottom = SortingStationLayout.STATUS_Y + SortingStationLayout.STATUS_LINES * 10 - 2;
		h.assertTrue(statusBottom < bar.y() - 1, "status lines end above the bar");
		h.assertTrue(SortingStationLayout.needs().bottom() <= band.y(), "needs text ends above the band");

		// text: every status wraps into the band's lines, button labels fit their buttons, labels fit the column
		int sw = SortingStationLayout.SIDE_TEXT_W;
		for (String s : new String[] { t(K + "idle"), f(K + "sorting", 999, 999), f(K + "finished", 999, 999),
				f(K + "tidying", 999, 999), f(K + "tidied", 999, 999) }) {
			h.assertTrue(lines(s, sw) <= SortingStationLayout.STATUS_LINES, "status '" + s + "' wraps to <= 3 lines at " + sw);
		}
		for (String k : new String[] { "sort", "tidy", "busy" }) {
			h.assertTrue(w(t(K + k)) <= SortingStationLayout.BTN_W - 4, "button label '" + t(K + k) + "' fits");
		}
		h.assertTrue(w(t(K + "supplies")) <= sw && w(t(K + "signs")) <= sw && w(t(K + "chests")) <= sw, "column headings fit");
		// the worst needs list: signs + chests + no-sign-face, each wrapped, 2 px between entries
		int needH = 0;
		for (String s : new String[] { f(K + "add_signs", 99), f(K + "add_chests", 99), f(K + "no_sign_face", 99) }) {
			needH += lines(s, sw) * 10 + 2;
		}
		Rect needs = SortingStationLayout.needs();
		h.assertTrue(SortingStationLayout.NEEDS_Y + needH - 4 <= needs.bottom(), "the worst needs list fits (" + needH + " px)");
		h.assertTrue(SortingStationLayout.TITLE_X + w(t("container.projecthero.stark_sorting_station")) <= SortingStationLayout.MAIN_W - 4,
				"the title fits the left column");
		h.succeed();
	}
}
