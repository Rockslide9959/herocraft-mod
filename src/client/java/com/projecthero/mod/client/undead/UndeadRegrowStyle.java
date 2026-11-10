package com.projecthero.mod.client.undead;

/**
 * v0.17.3: which look Undead's regrowth is drawn in. The user first made {@code stepped} the only look (the style command removed),
 * then asked to "swap back to the 3d style regeneration please": it is always the 3D look now ({@link UndeadRegrow}'s own drawing:
 * bone pushing out, muscle thickening from the bone outward, skin closing over). The stepped look ({@link UndeadRegrowStepped})
 * stays in the code, reachable only by the screenshot harness through {@link #forced}.
 */
public final class UndeadRegrowStyle {
	public enum Style {
		THREE_D, STEPPED
	}

	private UndeadRegrowStyle() {
	}

	/** Set only by the client screenshot harness: overrides the stepped default. */
	public static volatile Style forced;

	public static Style style() {
		return forced != null ? forced : Style.THREE_D; // v0.17.3 (user: "swap back to the 3d style regeneration please")
	}

	public static boolean stepped() {
		return style() == Style.STEPPED;
	}
}
