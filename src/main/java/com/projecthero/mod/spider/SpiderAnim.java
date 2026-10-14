package com.projecthero.mod.spider;

/**
 * v0.16.1: Spider-Man's animation ids, shared by the server (which starts them through
 * {@link SpiderMoves#anim}) and {@code client.spider.SpiderPose} (which plays them on every client).
 */
public final class SpiderAnim {
	public static final int NONE = 0;
	/** The thwip: one wrist flicked forward, two fingers down. */
	public static final int THWIP_RIGHT = 1;
	public static final int THWIP_LEFT = 2;
	/** Both wrists thrust out together (Web Bomb, Web Cocoon, Web Wall). */
	public static final int THWIP_BOTH = 3;
	/** The charged Impact Web leaving the hand: a hard recoil. */
	public static final int IMPACT_RELEASE = 4;
	/** Point Zip / the zip boost: arm up and forward, body pulled after it. */
	public static final int ZIP = 5;
	/** Web Yank: a two-handed haul back over the shoulder. */
	public static final int YANK = 6;
	/** Zip Kick: hauled in on one arm, both legs tucked, then the flying kick. */
	public static final int ZIP_KICK = 7;
	/** Web Strike: the three-punch combo (right, left, right finisher). */
	public static final int STRIKE_COMBO = 8;
	/** The uppercut: crouch and drive a fist straight up. */
	public static final int UPPERCUT = 9;
	/** The counter: a back-flip over the attack, then a snap kick. */
	public static final int COUNTER_FLIP = 10;
	/** Web Whip: arm flung out, body spinning a full turn. */
	public static final int WHIP = 11;
	/** Grab & Hurl: both hands haul in, then an overhead throw. */
	public static final int HURL = 12;
	/** Web Swing-Throw release: the whirling arm comes over and lets go. */
	public static final int SWING_THROW_RELEASE = 13;
	/** A tightrope anchor shot (both anchors). */
	public static final int TIGHTROPE_SHOT = 14;
	/** Web Wall: both palms pushed out, fingers spread. */
	public static final int WALL_CAST = 15;
	/** Weave Mode finished: both arms sweep out wide. */
	public static final int WEAVE_FINISH = 16;
	/** Spider-Sense Pulse: a hand to the temple, head snaps up. */
	public static final int PULSE = 17;
	/** Web Cocoon: both wrists circling the target. */
	public static final int COCOON = 18;
	/** Web Blossom burst: arms and legs flung out like a star. */
	public static final int BLOSSOM = 19;
	/** Letting go of a fast swing: a forward somersault. */
	public static final int RELEASE_FLIP = 20;
	/** The superhero landing: one knee down, one fist on the ground, the other arm out. */
	public static final int LANDING = 21;
	/** Weave Mode: one radial strand shot from the hand. */
	public static final int WEAVE_SHOT = 22;
	/** v0.16.2: the Inverted Takedown -- hanging upside down, he drops down the line, snatches, and hauls back up. */
	public static final int TAKEDOWN = 31;

	private SpiderAnim() {
	}

	/** Length of each animation in ticks (the client poses use the same numbers). */
	public static int length(int id) {
		return switch (id) {
			case THWIP_RIGHT, THWIP_LEFT, WEAVE_SHOT -> 8;
			case THWIP_BOTH, TIGHTROPE_SHOT -> 10;
			case IMPACT_RELEASE -> 10;
			case ZIP -> 12;
			case YANK -> 12;
			case ZIP_KICK -> 30;
			case STRIKE_COMBO -> 22;
			case UPPERCUT -> 12;
			case COUNTER_FLIP -> 14;
			case WHIP -> 12;
			case HURL -> 16;
			case SWING_THROW_RELEASE -> 10;
			case WALL_CAST -> 12;
			case WEAVE_FINISH -> 16;
			case PULSE -> 14;
			case COCOON -> 16;
			case BLOSSOM -> 16;
			case RELEASE_FLIP -> 12;
			case LANDING -> 18;
			case TAKEDOWN -> 16;
			default -> 0;
		};
	}
}
