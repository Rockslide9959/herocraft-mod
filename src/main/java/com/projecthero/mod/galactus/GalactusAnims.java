package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.16.1: <b>the model contract</b> for Galactus: The Devourer -- every animation clip, how long the server holds it, and
 * the server tick its strike / damage / release lands on. The placeholder generator
 * ({@code scratchpad/gen_galactus_placeholders.js}) reads this file and pins each strike keyframe to it; the real models
 * (designed separately, exported to the same paths) must do the same, so a move lands on screen on the tick the damage
 * does.
 *
 * <h2>Assets</h2>
 * {@code geo/galactus/<name>.geo.json}, {@code textures/entity/galactus/<name>.png} + {@code <name>_glowmask.png},
 * {@code animations/galactus/<name>.animation.json} for every name in {@link #MODELS}. The Silver Surfer carries his board
 * as a {@code board} bone. The bones {@code neck} / {@code head} (look) and {@code flinch} (hurt) are posed by code every
 * frame and must never be keyed by any clip.
 *
 * <h2>Timing convention (the Titan's / FrostGiantAnims')</h2>
 * The server starts a move by syncing its clip; the client's GeckoLib "main" controller spends {@link #TRANSITION} ticks
 * blending into it before the clip's own clock starts. So:
 * <ul>
 *   <li>a clip with {@code hit = H} keys its strike at clip time {@code (H - TRANSITION) / 20} seconds
 *       ({@link Clip#strikeSeconds()}) -- the generator fails if no keyframe sits there;</li>
 *   <li>a play-once clip's {@code animation_length} is {@code (length - TRANSITION) / 20} seconds, so it finishes before
 *       the server drops it after {@code length} ticks; held clips ({@code hold}) hold their last frame;</li>
 *   <li>loops ({@code loop}) are {@code length / 20} seconds long and never carry a hit.</li>
 * </ul>
 * Edit only the numbers in the {@code clip(...)} / {@code hold(...)} / {@code loop(...)} lines -- the generators parse
 * those lines with a regex.
 */
public final class GalactusAnims {
	/** Ticks the main controller blends into a new clip before its clock starts. */
	public static final int TRANSITION = 4;

	/** Bones the code drives every frame; no clip may key them. */
	public static final String[] CODE_DRIVEN_BONES = { "neck", "head", "flinch" };

	/** Asset base names. */
	public static final String GALACTUS = "galactus";
	public static final String SURFER = "silver_surfer";
	public static final String PUNISHER = "punisher";
	public static final String CONVERTER = "element_converter";
	public static final String WORLDSHIP = "worldship";
	public static final String HAND = "galactus_hand";
	public static final String BOARD = "surfboard_mount";
	public static final String NULLIFIER = "nullifier";

	public static final String[] MODELS = { GALACTUS, SURFER, PUNISHER, CONVERTER, WORLDSHIP, HAND, BOARD, NULLIFIER };

	/**
	 * One clip.
	 *
	 * @param model  asset base name
	 * @param name   clip name ({@code animation.<model>.<name>})
	 * @param length ticks the server plays it for (a loop's cycle length)
	 * @param hit    server tick (from the start of the move) of the strike / damage / release, or -1
	 * @param kind   0 = play once, 1 = hold the last frame, 2 = loop
	 */
	public record Clip(String model, String name, int length, int hit, int kind) {
		public String id() {
			return "animation." + model + "." + name;
		}

		public boolean loops() {
			return kind == 2;
		}

		public boolean holds() {
			return kind == 1;
		}

		/** Clip time (seconds) of the strike keyframe, or -1 when it has none. */
		public double strikeSeconds() {
			return hit < TRANSITION ? -1.0 : (hit - TRANSITION) / 20.0;
		}

		/** The clip's own animation_length (seconds). */
		public double clipSeconds() {
			return loops() ? length / 20.0 : Math.max(1, length - TRANSITION) / 20.0;
		}
	}

	private static final List<Clip> ALL_MUTABLE = new ArrayList<>();
	public static final List<Clip> ALL = Collections.unmodifiableList(ALL_MUTABLE);

	// ---------------------------------------------------------------- Galactus (the ~60-block colossus, stationary)
	public static final Clip GALACTUS_IDLE = loop(GALACTUS, "idle", 100);
	/** 2 s: the eyes gather Power Cosmic while the line is drawn on the ground; the sweep runs from {@code hit} to the end. */
	public static final Clip GALACTUS_EYE_BEAM = clip(GALACTUS, "eye_beam", 90, 44);
	/** The hand comes down over a 1.5 s shadow; it closes on {@code hit}. */
	public static final Clip GALACTUS_REACH = clip(GALACTUS, "reach", 40, 34);
	/** The fist rises to his face (held until the fling or the grip breaks). */
	public static final Clip GALACTUS_GRAB_LIFT = hold(GALACTUS, "grab_lift", 50, -1);
	/** The victim is thrown on {@code hit}. */
	public static final Clip GALACTUS_FLING = clip(GALACTUS, "fling", 30, 16);
	/** A palm to the ground; the marked patches turn on {@code hit}. */
	public static final Clip GALACTUS_TRANSMUTE = clip(GALACTUS, "transmute", 60, 40);
	/** Both fists down; the ring leaves his feet on {@code hit}. */
	public static final Clip GALACTUS_DISINTEGRATE = clip(GALACTUS, "disintegrate", 50, 30);
	/** An arm to the Worldship; the Punishers drop on {@code hit}. */
	public static final Clip GALACTUS_SUMMON = clip(GALACTUS, "summon", 50, 30);
	public static final Clip GALACTUS_WEAKPOINT_HURT = clip(GALACTUS, "weakpoint_hurt", 30, -1);
	/** The Nullifier: he raises a hand on {@code hit} (and speaks), then is gone in a burst of Power Cosmic at the end. */
	public static final Clip GALACTUS_RETREAT = hold(GALACTUS, "retreat", 160, 60);

	// ---------------------------------------------------------------- the Silver Surfer (player-sized, on his board)
	public static final Clip SURFER_IDLE = loop(SURFER, "idle", 40);
	public static final Clip SURFER_FLY = loop(SURFER, "fly", 20);
	/** A bolt of Power Cosmic leaves the hand on {@code hit}. */
	public static final Clip SURFER_BLAST = clip(SURFER, "blast", 24, 14);
	/** 1 s telegraph (the line is drawn), the dash itself starts on {@code hit}. */
	public static final Clip SURFER_DASH = clip(SURFER, "dash", 34, 24);
	/** The reflective shield is up from {@code hit} to the end. */
	public static final Clip SURFER_SHIELD = clip(SURFER, "shield", 70, 8);
	/** The victim leaves the ground on {@code hit}. */
	public static final Clip SURFER_LIFT = clip(SURFER, "lift", 40, 20);
	/** 6 s: he stops, lowers the board and turns. Held. */
	public static final Clip SURFER_TURN_ALLY = hold(SURFER, "turn_ally", 120, -1);
	public static final Clip SURFER_ALLY_IDLE = loop(SURFER, "ally_idle", 40);

	// ---------------------------------------------------------------- the Punisher robots (~8 blocks)
	public static final Clip PUNISHER_IDLE = loop(PUNISHER, "idle", 60);
	public static final Clip PUNISHER_WALK = loop(PUNISHER, "walk", 30);
	/** The gauntlet fires on {@code hit}. */
	public static final Clip PUNISHER_BLAST = clip(PUNISHER, "blast", 30, 18);
	/** 1.2 s raised fists (the ring is drawn), the slam lands on {@code hit}. */
	public static final Clip PUNISHER_SLAM = clip(PUNISHER, "slam", 40, 28);
	public static final Clip PUNISHER_DEATH = hold(PUNISHER, "death", 40, -1);

	// ---------------------------------------------------------------- the Element Converters
	public static final Clip CONVERTER_IDLE = loop(CONVERTER, "idle", 40);
	public static final Clip CONVERTER_CONSUME = loop(CONVERTER, "consume", 40);
	public static final Clip CONVERTER_SHIELDED = loop(CONVERTER, "shielded", 40);
	public static final Clip CONVERTER_DESTROYED = hold(CONVERTER, "destroyed", 40, -1);

	// ---------------------------------------------------------------- the Worldship (render-only)
	public static final Clip WORLDSHIP_IDLE = loop(WORLDSHIP, "idle", 200);
	public static final Clip WORLDSHIP_LEAVE = hold(WORLDSHIP, "leave", 100, -1);

	// ---------------------------------------------------------------- the grabbing hand, the board, the Nullifier
	public static final Clip HAND_IDLE = loop(HAND, "idle", 40);
	public static final Clip HAND_GRIP = loop(HAND, "grip", 20);
	public static final Clip BOARD_IDLE = loop(BOARD, "idle", 40);
	public static final Clip NULLIFIER_IDLE = loop(NULLIFIER, "idle", 40);

	private GalactusAnims() {
	}

	private static Clip clip(String model, String name, int length, int hit) {
		return add(new Clip(model, name, length, hit, 0));
	}

	private static Clip hold(String model, String name, int length, int hit) {
		return add(new Clip(model, name, length, hit, 1));
	}

	private static Clip loop(String model, String name, int length) {
		return add(new Clip(model, name, length, -1, 2));
	}

	private static Clip add(Clip c) {
		ALL_MUTABLE.add(c);
		return c;
	}

	/** Index of {@code clip} in {@link #ALL} (what gets synced), or -1. */
	public static int indexOf(Clip clip) {
		return clip == null ? -1 : ALL.indexOf(clip);
	}

	public static Clip byIndex(int i) {
		return i >= 0 && i < ALL.size() ? ALL.get(i) : null;
	}

	/** Every clip of one model. */
	public static List<Clip> of(String model) {
		List<Clip> out = new ArrayList<>();
		for (Clip c : ALL) {
			if (c.model().equals(model)) {
				out.add(c);
			}
		}
		return out;
	}

	public static Clip find(String model, String name) {
		for (Clip c : ALL) {
			if (c.model().equals(model) && c.name().equals(name)) {
				return c;
			}
		}
		return null;
	}
}
