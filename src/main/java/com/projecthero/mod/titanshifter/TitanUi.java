package com.projecthero.mod.titanshifter;

/**
 * v0.16.7 (user, Titan revamp): the layout numbers the client's Titan picker and HUD draw with, kept in common code so the
 * UI gametest can check every string against them (the "tooltips must wrap" rule).
 */
public final class TitanUi {
	private TitanUi() {
	}

	/** The picker: the most description lines a card shows, and the widest the two-column panel gets. */
	public static final int PICKER_TEXT_LINES = 6;
	public static final int PICKER_MAX_PANEL_W = 340;
	public static final int PICKER_GAP = 6;
	public static final int PICKER_PAD = 5;

	/** A picker card's width on a screen this wide. */
	public static int pickerCardWidth(int screenW) {
		int panelW = Math.min(screenW - 16, PICKER_MAX_PANEL_W);
		return (panelW - PICKER_GAP) / 2;
	}

	/** The text width inside a picker card. */
	public static int pickerTextWidth(int screenW) {
		return pickerCardWidth(screenW) - PICKER_PAD * 2;
	}

	/** The HUD block: six 20-px key boxes with 2-px gaps. Every HUD line must fit this width. */
	public static final int HUD_BOX = 20;
	public static final int HUD_GAP = 2;
	public static final int HUD_W = HUD_BOX * 6 + HUD_GAP * 5;
}
