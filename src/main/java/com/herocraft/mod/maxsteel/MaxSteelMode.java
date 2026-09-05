package com.herocraft.mod.maxsteel;

/**
 * The suit's current configuration. {@link #BASE} is the always-available fallback; the four
 * specialised modes are mutually exclusive and each drains T.U.R.B.O. Energy while held.
 * {@link #CANNON} is not a "mode" the player rests in -- it temporarily overrides whatever mode was
 * active for the charge/launch and the suit returns to {@link #BASE} on impact -- but it has its own
 * form model so it is tracked here too.
 *
 * <p>Persisted and synced as an ordinal, so the order of these constants must not be reordered.
 */
public enum MaxSteelMode {
	BASE,
	STRENGTH,
	SPEED,
	FLIGHT,
	STEALTH,
	CANNON;

	public static MaxSteelMode byOrdinal(int i) {
		MaxSteelMode[] v = values();
		return i >= 0 && i < v.length ? v[i] : BASE;
	}

	/** One of the four rest-in specialised modes (not BASE, not the transient CANNON). */
	public boolean isSpecialised() {
		return this == STRENGTH || this == SPEED || this == FLIGHT || this == STEALTH;
	}

	public String lower() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
