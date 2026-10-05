package com.projecthero.mod.ironman.sorter;

import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

/**
 * v0.14.29: the pure (no client classes) panel layout of the Stark Sorting Station screen, shared by
 * {@link SortingStationMenu} (slot positions) and the client screen (everything it draws), so a gametest can check it
 * fits the smallest supported scaled screen -- {@link com.projecthero.mod.ironman.ui.IronManUiLayout#MIN_W} x
 * {@link com.projecthero.mod.ironman.ui.IronManUiLayout#MIN_H} (GUI scale 3 at 1280 x 720 is 426 x 240, GUI scale 4 at
 * 1920 x 1080 is 480 x 270) -- the same way {@code IronManUiLayout} does for the Iron Man screens.
 *
 * <p>The old panel was 262 px tall (the status band sat between the store and the inventory) and clipped at GUI scale 3
 * on a 720p window. Now the left column is just the 6x9 store over the player inventory, and the right column holds
 * everything else: the supply slots, what the room still needs, and the status band (job status, progress bar, Sort and
 * Tidy side by side). All coordinates are panel-relative; slot coordinates are the item's top-left.
 */
public final class SortingStationLayout {
	/** Left column (store + player inventory) and right column widths, and the whole panel. */
	public static final int MAIN_W = 176;
	public static final int SIDE_W = 96;
	public static final int W = MAIN_W + SIDE_W;

	public static final int TITLE_X = 8;
	public static final int TITLE_Y = 6;
	public static final int STORE_X = 8;
	public static final int STORE_Y = 18;
	public static final int ROWS = 6;
	/** First player-inventory row, and the hotbar below it. */
	public static final int INV_Y = STORE_Y + ROWS * 18 + 14;
	public static final int HOTBAR_Y = INV_Y + 58;
	public static final int INV_LABEL_Y = INV_Y - 11;
	public static final int H = HOTBAR_Y + 24;

	/** Right column: inner left edge for text, its usable width. */
	public static final int SIDE_X = MAIN_W + 7;
	public static final int SIDE_TEXT_W = SIDE_W - 14;
	/** Supply slots: x of the first of three, and the sign row / chest row (each with a 10 px label above). */
	public static final int SUPPLY_X = MAIN_W + 8;
	public static final int SIGN_Y = 28;
	public static final int CHEST_Y = 60;
	/** "What the room still needs" lines: from here down to {@link #NEEDS_BOTTOM}, 10 px a line. */
	public static final int NEEDS_Y = CHEST_Y + 22;

	/** The status band at the foot of the right column. */
	public static final int BAND_X = MAIN_W + 4;
	public static final int BAND_W = SIDE_W - 8;
	public static final int BAND_H = 64;
	/** Bottom-aligned with the hotbar row. */
	public static final int BAND_Y = HOTBAR_Y + 17 - BAND_H;
	public static final int NEEDS_BOTTOM = BAND_Y - 3;
	/** Status text: up to {@link #STATUS_LINES} wrapped lines at the band's top. */
	public static final int STATUS_Y = BAND_Y + 5;
	public static final int STATUS_LINES = 3;
	public static final int BAR_X = SIDE_X;
	public static final int BAR_Y = BAND_Y + 37;
	public static final int BAR_W = SIDE_TEXT_W;
	/** Sort and Tidy, side by side under the bar. */
	public static final int BTN_W = 39;
	public static final int BTN_H = 15;
	public static final int BTN_GAP = 4;
	public static final int BTN_Y = BAND_Y + 45;

	private SortingStationLayout() {
	}

	public static Rect panel() {
		return new Rect(0, 0, W, H);
	}

	public static Rect band() {
		return new Rect(BAND_X, BAND_Y, BAND_W, BAND_H);
	}

	public static Rect sortButton() {
		return new Rect(SIDE_X, BTN_Y, BTN_W, BTN_H);
	}

	public static Rect tidyButton() {
		return new Rect(SIDE_X + BTN_W + BTN_GAP, BTN_Y, BTN_W, BTN_H);
	}

	public static Rect bar() {
		return new Rect(BAR_X, BAR_Y, BAR_W, 4);
	}

	/** The needs-text area (also the hover zone for its detail tooltip). */
	public static Rect needs() {
		return new Rect(MAIN_W + 1, NEEDS_Y - 2, SIDE_W - 1, NEEDS_BOTTOM - (NEEDS_Y - 2));
	}

	/** Store slot {@code i} (0..53), row-major. */
	public static int[] storeSlot(int i) {
		return new int[] { STORE_X + (i % 9) * 18, STORE_Y + (i / 9) * 18 };
	}

	/** Supply slot {@code i}: 0..2 signs, 3..5 chests. */
	public static int[] supplySlot(int i) {
		return new int[] { SUPPLY_X + (i % 3) * 18, i < 3 ? SIGN_Y : CHEST_Y };
	}

	/** Player inventory slot {@code i}: 0..26 the three rows, 27..35 the hotbar. */
	public static int[] playerSlot(int i) {
		return i < 27 ? new int[] { STORE_X + (i % 9) * 18, INV_Y + (i / 9) * 18 }
				: new int[] { STORE_X + (i - 27) * 18, HOTBAR_Y };
	}

	/** Top-left x where a centred panel of width {@link #W} lands on a {@code screenW}-wide screen. */
	public static int left(int screenW) {
		return (screenW - W) / 2;
	}

	public static int top(int screenH) {
		return (screenH - H) / 2;
	}
}
