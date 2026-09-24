package com.projecthero.mod.hero;

/**
 * The six universal ability slots. What a slot <em>does</em> depends on the active power. The
 * {@code role} / default key is the design-intent guideline (R = Ability 1 / primary, G = Ability 2 /
 * secondary, Z = Ability 3 / movement, X = Ability 4 / ultimate, C = Ability 5 / utility-control, V = Ability 6 /
 * special mode) and is only used for documentation/HUD hints. Since v0.11.15 the controls screen names the
 * keys "Ability 1" .. "Ability 6" and slots 3-6 default to Z, X, C, V. The dedicated power-select key is H (see {@code ModKeyBindings}).
 */
public enum AbilitySlot {
	SLOT_1('R', "Primary"),
	SLOT_2('G', "Secondary"),
	SLOT_3('Z', "Movement"),
	SLOT_4('X', "Ultimate"),
	SLOT_5('C', "Utility / Control"),
	SLOT_6('V', "Special Mode");

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

	/** Human "slot number", 1..6. */
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
		return "key.projecthero.ability_" + number();
	}

	public static AbilitySlot byNumber(int number) {
		if (number < 1 || number > 6) {
			throw new IllegalArgumentException("ability slot out of range: " + number);
		}
		return values()[number - 1];
	}
}
