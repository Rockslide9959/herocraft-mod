package com.projecthero.mod.ironman.gear;

import com.projecthero.mod.ironman.ui.IronManUiLayout;

/**
 * v0.15.1: the pure (no client classes) layout + status rules of the Stark Gear screen, kept in the common source set so
 * gametests can check every line fits without a client (see {@link IronManUiLayout}). The client screen draws with these
 * same numbers and keys.
 *
 * <pre>
 *   y 5          title
 *   x 7..56      y 17..92    3D preview of the player (wearing the glasses)
 *   x 8, y 97    the Stark Gear slot + "Eyes" label
 *   x 61..168    y 17..115   status panel: three rows (label + value chip, then a wrapped hint of at most 2 lines)
 *   y 124 / 182  player inventory / hotbar
 * </pre>
 */
public final class StarkGearLayout {
	public static final int W = 176;
	public static final int H = 202;
	public static final int PREVIEW_X = 7;
	public static final int PREVIEW_Y = 17;
	public static final int PREVIEW_W = 50;
	public static final int PREVIEW_H = 76;
	public static final int SLOT_X = 8;
	public static final int SLOT_Y = 97;
	public static final int SLOT_LABEL_X = 28;
	public static final int SLOT_LABEL_W = 61 - 2 - SLOT_LABEL_X; // up to the status panel
	public static final int PANEL_X = 61;
	public static final int PANEL_Y = 17;
	public static final int PANEL_W = 108;
	public static final int PANEL_H = 99;
	public static final int PAD = 4;
	public static final int TEXT_X = PANEL_X + PAD;
	public static final int TEXT_W = PANEL_W - 2 * PAD;
	public static final int ROW_Y = PANEL_Y + PAD;
	public static final int ROW_H = 31;
	public static final int LINE_H = 9;
	public static final int HINT_LINES = 2;
	/** Gap between a row's label and its value chip. */
	public static final int LABEL_GAP = 4;
	/** A value chip is its text plus this much padding. */
	public static final int CHIP_PAD = 7;
	public static final int INV_Y = 124;
	public static final int HOTBAR_Y = 182;

	private StarkGearLayout() {
	}

	/** One status row: what it says and in which colour family. */
	public enum Tone {
		GOOD, BAD, WARN, OFF
	}

	public record Row(String labelKey, String valueKey, Object[] valueArgs, Tone tone, String hintKey) {
	}

	/** The three status rows for a player with/without the glasses, Phoenix ready at {@code phoenixReadyAt}, now = {@code now}. */
	public static Row[] rows(boolean glasses, long phoenixReadyAt, long now) {
		String p = "screen.projecthero.stark_gear.";
		Row calling = glasses
				? new Row(p + "calling", p + "online", new Object[0], Tone.GOOD, p + "calling.hint_on")
				: new Row(p + "calling", p + "offline", new Object[0], Tone.BAD, p + "calling.hint_off");
		Row vision = glasses
				? new Row(p + "night_vision", p + "on", new Object[0], Tone.GOOD, p + "night_vision.hint_on")
				: new Row(p + "night_vision", p + "off", new Object[0], Tone.OFF, p + "night_vision.hint_off");
		Row phoenix;
		if (!glasses) {
			phoenix = new Row(p + "phoenix", p + "disarmed", new Object[0], Tone.BAD, p + "phoenix.hint_off");
		} else if (phoenixReadyAt > now) {
			phoenix = new Row(p + "phoenix", p + "cooldown", new Object[] { IronManUiLayout.mmss(phoenixReadyAt - now) },
					Tone.WARN, p + "phoenix.hint_on");
		} else {
			phoenix = new Row(p + "phoenix", p + "armed", new Object[0], Tone.GOOD, p + "phoenix.hint_on");
		}
		return new Row[] { calling, vision, phoenix };
	}

	/** Every key the screen can show, for the layout-fit gametest. */
	public static String[] allLabelKeys() {
		String p = "screen.projecthero.stark_gear.";
		return new String[] { p + "calling", p + "night_vision", p + "phoenix" };
	}

	public static String[] allValueKeys() {
		String p = "screen.projecthero.stark_gear.";
		return new String[] { p + "online", p + "offline", p + "on", p + "off", p + "armed", p + "disarmed", p + "cooldown" };
	}

	public static String[] allHintKeys() {
		String p = "screen.projecthero.stark_gear.";
		return new String[] { p + "calling.hint_on", p + "calling.hint_off", p + "night_vision.hint_on",
				p + "night_vision.hint_off", p + "phoenix.hint_on", p + "phoenix.hint_off" };
	}
}
