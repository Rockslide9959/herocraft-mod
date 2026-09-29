package com.projecthero.mod.hero;

/**
 * The six universal ability slots. What a slot <em>does</em> depends on the active power. The
 * {@code role} / default key is the design-intent guideline (R = Ability 1 / primary, G = Ability 2 /
 * secondary, X = slot 3 / movement, Z = slot 4 / ultimate, V = slot 5 / utility-control, C = slot 6 /
 * special mode) and is only used for documentation/HUD hints. Since v0.11.17 the controls screen names the keys R, G, Z, X, C, V
 * "Ability 1" .. "Ability 6" (so slot 4 / Z is "Ability 3", slot 3 / X is "Ability 4", slot 6 / C is "Ability 5", slot 5 / V is "Ability 6");
 * the moves themselves never moved. The dedicated power-select key is H (see {@code ModKeyBindings}).
 */
public enum AbilitySlot {
	SLOT_1('R', "Primary"),
	SLOT_2('G', "Secondary"),
	SLOT_3('X', "Movement"),
	SLOT_4('Z', "Ultimate"),
	SLOT_5('V', "Utility / Control"),
	SLOT_6('C', "Special Mode"),
	/** v0.13.22: Utility 1 (H) -- experimental mutations only; the Hero-Tier powers keep their own H behaviour. */
	SLOT_7('H', "Utility 1"),
	/** v0.13.22: Utility 2 (N) -- experimental mutations only. */
	SLOT_8('N', "Utility 2");

	/** The six universal slots every power has; mutations additionally define {@link #SLOT_7} and {@link #SLOT_8}. */
	public static final int CORE_COUNT = 6;

	private final char defaultKey;
	private final String role;

	AbilitySlot(char defaultKey, String role) {
		this.defaultKey = defaultKey;
		this.role = role;
	}

	/** 0-based index; slot 1 -> 0. */
	public int index() {
		return ordinal();
	}

	/** Human "slot number", 1..8. */
	public int number() {
		return ordinal() + 1;
	}

	public char defaultKey() {
		return defaultKey;
	}

	public String role() {
		return role;
	}

	/** The controls-screen translation key (rendered "Ability 1" ... "Ability 6"). */
	public String keyBindingTranslationKey() {
		if (this == SLOT_7) {
			return "key.projecthero.power_select"; // H: Utility 1
		}
		if (this == SLOT_8) {
			return "key.projecthero.max_steel_transform"; // N: Utility 2
		}
		return "key.projecthero.ability_" + number();
	}

	/** Whether this is one of the two mutation-only utility slots (H / N). */
	public boolean isUtility() {
		return this == SLOT_7 || this == SLOT_8;
	}

	public static AbilitySlot byNumber(int number) {
		if (number < 1 || number > 8) {
			throw new IllegalArgumentException("ability slot out of range: " + number);
		}
		return values()[number - 1];
	}
}
