package com.projecthero.mod.titanshifter;

/**
 * The Titan Shifter's explicit state machine (v0.12.31) -- one enum rather than a pile of booleans.
 *
 * <pre>
 *   HUMAN -> TRANSFORMING -> TITAN -> REVERTING -> HUMAN
 *                              TITAN -> DEFEATED -> RECOVERING -> HUMAN
 * </pre>
 *
 * Any phase may also fall straight back to HUMAN / RECOVERING when the form is forcibly ended (logout,
 * death, dimension change, admin command); every other transition is refused by {@link #canGoTo}.
 */
public enum TitanPhase {
	HUMAN, TRANSFORMING, TITAN, REVERTING, DEFEATED, RECOVERING;

	public boolean canGoTo(TitanPhase next) {
		if (next == this) {
			return false;
		}
		return switch (this) {
			case HUMAN -> next == TRANSFORMING;
			case TRANSFORMING -> next == TITAN || next == HUMAN || next == RECOVERING;
			case TITAN -> next == REVERTING || next == DEFEATED || next == HUMAN || next == RECOVERING;
			case REVERTING -> next == HUMAN || next == RECOVERING;
			case DEFEATED -> next == RECOVERING || next == HUMAN;
			case RECOVERING -> next == HUMAN;
		};
	}

	/** The player is physically inside a Titan (the form entity exists and carries them). */
	public boolean insideForm() {
		return this == TRANSFORMING || this == TITAN || this == REVERTING || this == DEFEATED;
	}

	/** The form cannot move or act. */
	public boolean locked() {
		return this != TITAN;
	}

	public static TitanPhase byName(String name) {
		for (TitanPhase p : values()) {
			if (p.name().equalsIgnoreCase(name)) {
				return p;
			}
		}
		return HUMAN;
	}
}
