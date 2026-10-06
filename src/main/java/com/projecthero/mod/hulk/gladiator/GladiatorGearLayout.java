package com.projecthero.mod.hulk.gladiator;

/**
 * v0.15.3: the pure (no client classes) layout of the Gladiator Gear screen, kept in the common source set so a gametest
 * can check every label and hint fits. The client screen draws with these same numbers and keys.
 *
 * <pre>
 *   y 5           title
 *   x 7..84       y 16..117   ARMOUR panel: five labelled slots (helmet, pauldron, harness, bracers, kilt)
 *   x 87..169     y 16..117   WEAPONS panel: hammer + axe slots, then "N/7 equipped" and a wrapped hint (max 4 lines)
 *   y 124 / 182   player inventory / hotbar
 * </pre>
 */
public final class GladiatorGearLayout {
	public static final int W = 176;
	public static final int H = 206;

	public static final int LEFT_X = 7;
	public static final int LEFT_W = 78;
	public static final int RIGHT_X = 87;
	public static final int RIGHT_W = 83;
	public static final int PANEL_Y = 16;
	public static final int PANEL_H = 102;

	/** Armour slots: x, then y of the first + row step. */
	public static final int ARMOUR_SLOT_X = 12;
	public static final int SLOT_Y0 = 21;
	public static final int SLOT_STEP = 19;
	public static final int ARMOUR_LABEL_X = ARMOUR_SLOT_X + 20;
	public static final int ARMOUR_LABEL_W = LEFT_X + LEFT_W - 3 - ARMOUR_LABEL_X;

	public static final int WEAPON_SLOT_X = 92;
	public static final int WEAPON_LABEL_X = WEAPON_SLOT_X + 20;
	public static final int WEAPON_LABEL_W = RIGHT_X + RIGHT_W - 3 - WEAPON_LABEL_X;

	public static final int STATUS_X = RIGHT_X + 4;
	public static final int STATUS_Y = SLOT_Y0 + 2 * SLOT_STEP + 4;
	public static final int STATUS_W = RIGHT_W - 8;
	public static final int HINT_Y = STATUS_Y + 13;
	public static final int LINE_H = 9;
	public static final int HINT_LINES = 4;

	public static final int INV_Y = 124;
	public static final int HOTBAR_Y = 182;

	private GladiatorGearLayout() {
	}

	/** Screen x of a gear slot. */
	public static int slotX(int slot) {
		return slot >= GladiatorGear.HAMMER ? WEAPON_SLOT_X : ARMOUR_SLOT_X;
	}

	/** Screen y of a gear slot. */
	public static int slotY(int slot) {
		int row = slot >= GladiatorGear.HAMMER ? slot - GladiatorGear.HAMMER : slot;
		return SLOT_Y0 + row * SLOT_STEP;
	}

	/** The x and width a slot's label is drawn in. */
	public static int labelX(int slot) {
		return slot >= GladiatorGear.HAMMER ? WEAPON_LABEL_X : ARMOUR_LABEL_X;
	}

	public static int labelW(int slot) {
		return slot >= GladiatorGear.HAMMER ? WEAPON_LABEL_W : ARMOUR_LABEL_W;
	}

	public static String labelKey(int slot) {
		return "screen.projecthero.gladiator_gear.slot." + GladiatorGear.SLOT_NAMES[slot];
	}

	/** The status line ("%s/7 equipped"). */
	public static final String EQUIPPED_KEY = "screen.projecthero.gladiator_gear.equipped";

	/** The wrapped hint under the status line for {@code count} pieces equipped. */
	public static String hintKey(int count) {
		return count >= GladiatorGear.SLOTS ? "screen.projecthero.gladiator_gear.hint_full"
				: "screen.projecthero.gladiator_gear.hint_partial";
	}

	public static String[] allHintKeys() {
		return new String[] { hintKey(0), hintKey(GladiatorGear.SLOTS) };
	}
}
