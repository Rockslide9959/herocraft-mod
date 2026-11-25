package com.projecthero.mod.shazam;

/**
 * v0.17.7 Shazam kit: the ids of the synced body clips ({@code ShazamState.anim}) and hold moves ({@code ShazamState.hold}). Client-safe;
 * the client's {@code ShazamPose} plays the clips, {@code ShazamBoltFx} draws the held effects (the Haymaker's charging fist, Zeus's
 * Wrath's stream, Mercury Rush's crackle, the creature held overhead).
 */
public final class ShazamAnim {
	private ShazamAnim() {
	}

	public static final int NONE = 0;
	public static final int COMBO_1 = 1;
	public static final int COMBO_2 = 2;
	public static final int COMBO_3 = 3;
	public static final int UPPERCUT = 4;
	public static final int HAYMAKER = 5;
	public static final int ZEUS_CALL = 6;
	public static final int SLAM = 7;
	public static final int BLITZ = 8;
	public static final int CLAP = 9;
	public static final int RUSH_A = 10;
	public static final int RUSH_B = 11;
	public static final int QUAKE = 12;
	public static final int HURL = 13;
	public static final int ULT = 14;
	public static final int LEAP = 15;
	public static final int COURAGE = 16;
	public static final int WISDOM = 17;
	public static final int DASH = 18;
	public static final int SHOVE = 19;

	/** No hold move running. */
	public static final int HOLD_NONE = 0;
	/** Hold R: the Haymaker charging. */
	public static final int HOLD_HAYMAKER = 1;
	/** Hold G: Zeus's Wrath streaming. */
	public static final int HOLD_WRATH = 2;
	/** Hold C: Mercury Rush hopping. */
	public static final int HOLD_RUSH = 3;
	/** Hold Z: Titan's Grip, a creature held overhead. */
	public static final int HOLD_GRIP = 4;
}
