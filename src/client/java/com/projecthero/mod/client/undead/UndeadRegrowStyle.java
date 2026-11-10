package com.projecthero.mod.client.undead;

/**
 * v0.17.3: which look Undead's regrowth is drawn in. User: "remove the command for changing your regen style and also make the
 * stepped regen style the default style for this ability please" -- it is always {@code stepped} now (bone-and-blood surface
 * revealed frame by frame, then raw flesh, then skin; {@link UndeadRegrowStepped}). The older 3D look ({@link UndeadRegrow}'s
 * own drawing) stays in the code, reachable only by the screenshot harness through {@link #forced}.
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
		return forced != null ? forced : Style.STEPPED;
	}

	public static boolean stepped() {
		return style() == Style.STEPPED;
	}
}
