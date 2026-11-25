package com.projecthero.mod.shazam.journey;

/**
 * v0.17.7 Shazam acquisition: the journey's cutscenes, their lengths and beats (ticks). The server runs the story on these
 * beats ({@link ShazamJourney}); the client shoots the camera, fades, letterbox and subtitles from the same numbers
 * ({@code client.shazam.journey.JourneyClient}).
 */
public enum JourneyScene {
	/** At the illusion: the screen darkens, the camera looks down the stairs, a gust, a horn far off; then the subway. */
	DESCENT(0, 74),
	/** On the platform: fade in, the train's lights in the tunnel, the horn, the wind, the brakes; it stops and the doors open. */
	TRAIN(1, 152),
	/** The ride: doors close, the tunnel lamps streak by, glyphs, a lightning flash, raw rock, into the cave. */
	RIDE(2, JourneyGeometry.RIDE_TICKS),
	/** Before the throne: he rises and speaks. */
	MEETING(3, 206),
	/** "Say my name." -- held until the player answers. */
	PROMPT(4, Integer.MAX_VALUE),
	/** SHAZAM! The bolt, the first transformation, his last words, the white flash home. */
	TRANSFORM(5, 150),
	/** Back where it began: the white fades. */
	HOME(6, 40);

	public final int id;
	public final int length;

	JourneyScene(int id, int length) {
		this.id = id;
		this.length = length;
	}

	public static JourneyScene byId(int id) {
		for (JourneyScene s : values()) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	// ---- DESCENT
	public static final int DESCENT_HORN = 22;
	/** v2: the light dims gradually from 20 and is gone here (no hard cut). */
	public static final int DESCENT_BLACK = 58;
	/** The distant horn and the rumble, in the beat of darkness before the platform. */
	public static final int DESCENT_HORN_DARK = 55;
	/** The player is taken to the subway here (or as soon as the station is built after it). */
	public static final int DESCENT_GO = 62;

	// ---- TRAIN: the train's arrival starts at TRAIN_ARRIVE (its {@link JourneyGeometry#ARRIVE_TICKS} braking run)
	public static final int TRAIN_ARRIVE = 24;

	// ---- MEETING
	public static final int MEET_RISE = 12;
	public static final int MEET_LINE1 = 58;
	public static final int MEET_LINE2 = 108;
	public static final int MEET_LINE3 = 152;

	// ---- TRANSFORM
	public static final int TRANSFORM_SHOUT = 2;
	public static final int TRANSFORM_STRIKE = 14;
	public static final int TRANSFORM_LINE1 = 62;
	public static final int TRANSFORM_LINE2 = 92;
	public static final int TRANSFORM_WHITE = 118;
	public static final int TRANSFORM_HOME = 134;
}
