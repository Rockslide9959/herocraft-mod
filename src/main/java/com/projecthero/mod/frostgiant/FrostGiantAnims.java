package com.projecthero.mod.frostgiant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.16.1: <b>the model contract</b> -- every Frost Giant Assault animation clip, how long the server holds it, and the
 * server tick its strike / damage / release lands on. The model generator ({@code scratchpad/gen_frost_giant_models.js},
 * and the placeholder one, {@code gen_frost_giant_placeholders.js}) reads this file and pins each strike keyframe to it,
 * so the swing lands on screen on the tick the damage does.
 *
 * <h2>Timing convention (the Titan's)</h2>
 * The server starts a move by syncing its clip ({@code FrostGiantEntity#setAnim}); the client's GeckoLib "main"
 * controller spends {@link #TRANSITION} ticks blending into it before the clip's own clock starts. So:
 * <ul>
 *   <li>a clip with {@code hit = H} keys its strike at clip time {@code (H - TRANSITION) / 20} seconds
 *       ({@link Clip#strikeSeconds()}) -- the generator fails if no keyframe sits there;</li>
 *   <li>a play-once clip's {@code animation_length} is {@code (length - TRANSITION) / 20} seconds, so it finishes before
 *       the server drops it after {@code length} ticks; held clips ({@code hold}) hold their last frame;</li>
 *   <li>loops ({@code loop}) are {@code length / 20} seconds long and never carry a hit.</li>
 * </ul>
 * The bones {@code neck} and {@code flinch} are posed by code every frame (head look, hurt flinch) and must never be
 * keyed by any clip.
 *
 * <p>Edit only the numbers in the {@code clip(...)} / {@code hold(...)} / {@code loop(...)} lines -- the generators
 * parse those lines with a regex.
 */
public final class FrostGiantAnims {
	/** Ticks the main controller blends into a new clip before its clock starts. */
	public static final int TRANSITION = 4;

	/** Bones the code drives every frame; no clip may key them. */
	public static final String[] CODE_DRIVEN_BONES = { "neck", "flinch" };

	/** Asset base names (geo/frost_giant/<name>.geo.json, textures/entity/frost_giant/<name>.png, ...). */
	public static final String HOUND = "rime_hound";
	public static final String RAIDER = "jotun_raider";
	public static final String SHAMAN = "frost_shaman";
	public static final String BRUTE = "ice_brute";
	public static final String WARLORD = "frost_giant_warlord";
	public static final String LAUFEY = "king_laufey";
	public static final String YMIR = "ymir_arm";
	public static final String CASKET = "ice_casket";

	public static final String[] MODELS = { HOUND, RAIDER, SHAMAN, BRUTE, WARLORD, LAUFEY, YMIR, CASKET };

	/**
	 * One clip.
	 *
	 * @param model  asset base name
	 * @param name   clip name ({@code animation.<model>.<name>})
	 * @param length ticks the server plays it for (a loop's cycle length)
	 * @param hit    server tick (from the start of the move) of the strike / damage / release, or -1
	 * @param kind   0 = play once (back to locomotion after {@code length}), 1 = hold the last frame, 2 = loop
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

	// ---------------------------------------------------------------- Rime Hound
	public static final Clip HOUND_IDLE = loop(HOUND, "idle", 40);
	public static final Clip HOUND_WALK = loop(HOUND, "walk", 16);
	public static final Clip HOUND_RUN = loop(HOUND, "run", 10);
	public static final Clip HOUND_WAR_CRY = clip(HOUND, "war_cry", 30, 10);
	public static final Clip HOUND_HURT = clip(HOUND, "hurt", 10, -1);
	public static final Clip HOUND_DEATH = hold(HOUND, "death", 30, -1);
	public static final Clip HOUND_BITE = clip(HOUND, "bite", 14, 8);
	public static final Clip HOUND_HOWL = clip(HOUND, "howl", 40, 12);
	public static final Clip HOUND_POUNCE = clip(HOUND, "pounce", 24, 12);

	// ---------------------------------------------------------------- Jotun Raider
	public static final Clip RAIDER_IDLE = loop(RAIDER, "idle", 60);
	public static final Clip RAIDER_WALK = loop(RAIDER, "walk", 30);
	public static final Clip RAIDER_RUN = loop(RAIDER, "run", 18);
	public static final Clip RAIDER_WAR_CRY = clip(RAIDER, "war_cry", 30, 10);
	public static final Clip RAIDER_HURT = clip(RAIDER, "hurt", 10, -1);
	public static final Clip RAIDER_DEATH = hold(RAIDER, "death", 50, -1);
	public static final Clip RAIDER_SPEAR_THRUST = clip(RAIDER, "spear_thrust", 20, 12);
	public static final Clip RAIDER_JAVELIN_WINDUP = hold(RAIDER, "javelin_windup", 20, -1);
	public static final Clip RAIDER_JAVELIN_THROW = clip(RAIDER, "javelin_throw", 16, 6);

	// ---------------------------------------------------------------- Frost Shaman
	public static final Clip SHAMAN_IDLE = loop(SHAMAN, "idle", 60);
	public static final Clip SHAMAN_WALK = loop(SHAMAN, "walk", 34);
	public static final Clip SHAMAN_RUN = loop(SHAMAN, "run", 20);
	public static final Clip SHAMAN_WAR_CRY = clip(SHAMAN, "war_cry", 30, 10);
	public static final Clip SHAMAN_HURT = clip(SHAMAN, "hurt", 10, -1);
	public static final Clip SHAMAN_DEATH = hold(SHAMAN, "death", 50, -1);
	public static final Clip SHAMAN_ICE_WALL = clip(SHAMAN, "ice_wall", 28, 16);
	public static final Clip SHAMAN_RIME_WARD = clip(SHAMAN, "rime_ward", 30, 18);
	public static final Clip SHAMAN_FROST_NOVA = clip(SHAMAN, "frost_nova", 22, 12);

	// ---------------------------------------------------------------- Ice Brute
	public static final Clip BRUTE_IDLE = loop(BRUTE, "idle", 60);
	public static final Clip BRUTE_WALK = loop(BRUTE, "walk", 36);
	public static final Clip BRUTE_RUN = loop(BRUTE, "run", 20);
	public static final Clip BRUTE_WAR_CRY = clip(BRUTE, "war_cry", 34, 12);
	public static final Clip BRUTE_HURT = clip(BRUTE, "hurt", 10, -1);
	public static final Clip BRUTE_DEATH = hold(BRUTE, "death", 56, -1);
	/** 1.2 s paw-the-ground telegraph; the rush starts on tick {@code length}. */
	public static final Clip BRUTE_CHARGE_WINDUP = hold(BRUTE, "charge_windup", 24, -1);
	public static final Clip BRUTE_CHARGE = loop(BRUTE, "charge", 16);
	/** Fists slam on {@code hit}; the cracking-frost line was telegraphed for the second before it. */
	public static final Clip BRUTE_GLACIER_SPIKES = clip(BRUTE, "glacier_spikes", 40, 24);
	public static final Clip BRUTE_BRAZIER_SMASH = clip(BRUTE, "brazier_smash", 26, 16);

	// ---------------------------------------------------------------- Frost Giant Warlord
	public static final Clip WARLORD_IDLE = loop(WARLORD, "idle", 70);
	public static final Clip WARLORD_WALK = loop(WARLORD, "walk", 40);
	public static final Clip WARLORD_RUN = loop(WARLORD, "run", 24);
	public static final Clip WARLORD_WAR_CRY = clip(WARLORD, "war_cry", 34, 12);
	public static final Clip WARLORD_HURT = clip(WARLORD, "hurt", 10, -1);
	public static final Clip WARLORD_DEATH = hold(WARLORD, "death", 60, -1);
	/** A wide arc, telegraphed for a second. */
	public static final Clip WARLORD_CLEAVE = clip(WARLORD, "cleave", 34, 24);
	/** The sword goes in on {@code hit}; the spike ring runs outward from there. */
	public static final Clip WARLORD_SWORD_PLANT = clip(WARLORD, "sword_plant", 40, 24);
	public static final Clip WARLORD_RALLY = clip(WARLORD, "rally", 36, 14);
	public static final Clip WARLORD_KNEEL = hold(WARLORD, "kneel", 60, -1);

	// ---------------------------------------------------------------- King Laufey
	public static final Clip LAUFEY_IDLE = loop(LAUFEY, "idle", 80);
	public static final Clip LAUFEY_WALK = loop(LAUFEY, "walk", 48);
	public static final Clip LAUFEY_RUN = loop(LAUFEY, "run", 30);
	public static final Clip LAUFEY_WAR_CRY = clip(LAUFEY, "war_cry", 40, 14);
	public static final Clip LAUFEY_HURT = clip(LAUFEY, "hurt", 10, -1);
	/** The 4 s collapse: kneel, the crown cracks, ice creeps up from the feet, he shatters at the end. */
	public static final Clip LAUFEY_DEATH = hold(LAUFEY, "death", 80, -1);
	/** Three spears form above his hand one by one (ticks 8 / 16 / 24), thrown together on {@code hit}. */
	public static final Clip LAUFEY_SPEAR_FAN = clip(LAUFEY, "spear_fan", 40, 30);
	public static final Clip LAUFEY_SUMMON = clip(LAUFEY, "summon", 36, 20);
	/** Points; the ring under the victim grows until {@code hit}, when the prison closes. */
	public static final Clip LAUFEY_FROZEN_GRASP = clip(LAUFEY, "frozen_grasp", 40, 30);
	public static final Clip LAUFEY_CASKET_OPEN = clip(LAUFEY, "casket_open", 50, 30);
	/** 2 s of frost gathering in the lid, then the beam from {@code hit} to the end (3 s). */
	public static final Clip LAUFEY_WINTERS_BREATH = clip(LAUFEY, "winters_breath", 100, 40);
	public static final Clip LAUFEY_ICE_PILLARS = clip(LAUFEY, "ice_pillars", 40, 28);
	public static final Clip LAUFEY_AVALANCHE_SLAM = clip(LAUFEY, "avalanche_slam", 36, 24);
	public static final Clip LAUFEY_STAGGER = hold(LAUFEY, "stagger", 100, -1);
	public static final Clip LAUFEY_KNEEL_HOLD = hold(LAUFEY, "kneel_hold", 20, -1);

	// ---------------------------------------------------------------- Ymir's arm (render-only)
	public static final Clip YMIR_IDLE = loop(YMIR, "idle", 60);
	public static final Clip YMIR_REACH_IN = hold(YMIR, "reach_in", 60, -1);
	/** The shadow grows for 2 s; the palm lands on {@code hit}. */
	public static final Clip YMIR_PALM_SLAM = clip(YMIR, "palm_slam", 50, 40);
	/** 3 s warning, then the drag across the arena from {@code hit} to the end. */
	public static final Clip YMIR_GLACIAL_SWEEP = clip(YMIR, "glacial_sweep", 80, 60);
	public static final Clip YMIR_RETRACT = hold(YMIR, "retract", 50, -1);

	// ---------------------------------------------------------------- the Casket (phase-3 hit-box)
	public static final Clip CASKET_IDLE = loop(CASKET, "idle", 40);

	/** Laufey's spears appear above his hand on these clip ticks (server ticks from the move's start). */
	public static final int[] LAUFEY_SPEAR_FORM_TICKS = { 8, 16, 24 };

	private FrostGiantAnims() {
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

	/** The clip named {@code name} of {@code model}, or null. */
	public static Clip find(String model, String name) {
		for (Clip c : ALL) {
			if (c.model().equals(model) && c.name().equals(name)) {
				return c;
			}
		}
		return null;
	}
}
