package com.projecthero.mod.blackpanther.plane;

/**
 * v0.17.7 Black Panther Ancestral Plane: the cutscenes, their lengths and beats (ticks). The server runs the story on these
 * beats ({@link AncestralPlane}); the client shoots the camera, letterbox, fades and subtitles from the same numbers
 * ({@code client.blackpanther.plane.AncestralPlaneClient}). Every scene but {@link #WAKE} can be skipped once it has been seen
 * all the way through before (Space).
 */
public enum PlaneScene {
	/** Falling asleep into the plane: black, then the savanna at sunset fades in from high above, down to the player. */
	ARRIVAL(0, 170, true),
	/** The ancestors greet the player under the great acacia. */
	GREETING(1, 330, true),
	/** They lead the player across the grass to the Panther Shrine. */
	PROCESSION(2, 250, true),
	/** The statue's eyes ignite; Bast speaks and the shades gather. */
	TRIAL_INTRO(3, 210, true),
	/** The spirit path forms behind the shrine, platform by platform, rising toward the sun. */
	PATH(4, 170, true),
	/** Bast's spirit, the ancestors' farewell, the gold light -- and waking. */
	FINALE(5, 320, true),
	/** Back in bed in the real world: the dark lifts. */
	WAKE(6, 70, false);

	public final int id;
	public final int length;
	public final boolean skippable;

	PlaneScene(int id, int length, boolean skippable) {
		this.id = id;
		this.length = length;
		this.skippable = skippable;
	}

	public static PlaneScene byId(int id) {
		for (PlaneScene s : values()) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	/** This scene's bit in the player's "seen" mask. */
	public int bit() {
		return 1 << id;
	}

	// ---- ARRIVAL
	public static final int ARRIVAL_DARK = 24;
	public static final int ARRIVAL_CLEAR = 70;
	public static final int ARRIVAL_CAPTION = 62;

	// ---- GREETING
	public static final int GREET_LINE1 = 30;
	public static final int GREET_LINE2 = 100;
	public static final int GREET_LINE3 = 178;
	public static final int GREET_LINE4 = 258;

	// ---- PROCESSION
	public static final int PROC_LINE1 = 24;
	public static final int PROC_LINE2 = 130;
	/** The ancestors walk from here to {@link #PROC_ARRIVE}. */
	public static final int PROC_WALK = 6;
	public static final int PROC_ARRIVE = 196;

	// ---- TRIAL_INTRO
	public static final int INTRO_EYES = 22;
	public static final int INTRO_LINE1 = 48;
	public static final int INTRO_LINE2 = 108;
	public static final int INTRO_LINE3 = 164;

	// ---- PATH
	public static final int PATH_LINE = 8;
	public static final int PATH_BUILD_FROM = 22;
	public static final int PATH_BUILD_TO = 118;

	// ---- FINALE
	public static final int FINALE_SPIRIT = 20;
	public static final int FINALE_LINE1 = 52;
	public static final int FINALE_LINE2 = 128;
	public static final int FINALE_LINE3 = 204;
	public static final int FINALE_GOLD = 252;
	public static final int FINALE_BLACK = 272;
	public static final int FINALE_HOME = 292;
}
