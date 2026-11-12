package com.projecthero.mod.beastboy;

import java.util.List;

import net.minecraft.world.entity.EntityDimensions;

/**
 * v0.17.3 Beast Boy: what each {@link BeastForm} does as a body -- stamina drain, how it moves, which moves its R / G / C are.
 * Kept beside the contract enum so the enum stays the shared, art-facing list. Client-safe.
 */
public final class BeastBoyForms {
	private BeastBoyForms() {
	}

	/** The V wheel's sustained forms, clockwise from the top (Human sits in the middle). */
	public static final List<BeastForm> WHEEL = List.of(BeastForm.TREX, BeastForm.GORILLA, BeastForm.RHINO, BeastForm.ELEPHANT,
			BeastForm.WOLF, BeastForm.BEAR, BeastForm.PTERODACTYL, BeastForm.HAWK, BeastForm.BAT, BeastForm.SHARK, BeastForm.OCTOPUS,
			BeastForm.WHALE, BeastForm.MOUSE);

	/** Morph Stamina per second in the form (big forms drain it, small ones are nearly free). */
	public static float drainPerSecond(BeastForm f) {
		return switch (f) {
			case HUMAN, SNAKE, TIGER -> 0f;
			case FLY, MOUSE -> 0.3f;
			case BAT -> 0.5f;
			case HAWK, CHEETAH, DOLPHIN -> 0.8f;
			case WOLF -> 1f;
			case SHARK, OCTOPUS -> 2f;
			case GORILLA, RHINO, BEAR, PTERODACTYL -> 2.5f;
			case WHALE -> 3f;
			case ELEPHANT -> 3.5f;
			case TREX -> 4f;
			case BEAST -> 0f; // paid up front, timed
		};
	}

	public static float drainPerTick(BeastForm f) {
		return drainPerSecond(f) / 20f;
	}

	/** Fly through the air (directional flight while in the air; double-tap Space to take off). */
	public static boolean flies(BeastForm f) {
		return f == BeastForm.HAWK || f == BeastForm.PTERODACTYL || f == BeastForm.BAT || f == BeastForm.FLY;
	}

	/** Swims fast and breathes water. */
	public static boolean aquatic(BeastForm f) {
		return f == BeastForm.DOLPHIN || f == BeastForm.SHARK || f == BeastForm.OCTOPUS || f == BeastForm.WHALE;
	}

	/** Mobs ignore it (and it fits through 1-block gaps). */
	public static boolean tiny(BeastForm f) {
		return f == BeastForm.MOUSE || f == BeastForm.FLY;
	}

	/** Can't attack at all (the fly, the mouse). */
	public static boolean harmless(BeastForm f) {
		return tiny(f);
	}

	/** Breaks soft blocks it runs into (needs the mobGriefing rule). */
	public static boolean smashes(BeastForm f) {
		return f == BeastForm.BEAST || f == BeastForm.RHINO;
	}

	/** Walking speed multiplier on top of the base (ADD_MULTIPLIED_TOTAL). */
	public static double speedBonus(BeastForm f) {
		return switch (f) {
			case CHEETAH -> 1.6;
			case WOLF -> 0.45;
			case RHINO -> 0.3;
			case BEAST -> 0.35;
			case GORILLA, BEAR -> 0.15;
			case ELEPHANT -> -0.1;
			case MOUSE -> -0.15;
			case WHALE, SHARK, DOLPHIN, OCTOPUS -> -0.5; // on land
			default -> 0.0;
		};
	}

	/** Extra melee damage on a left click in the form. */
	public static double meleeBonus(BeastForm f) {
		return switch (f) {
			case TREX -> 8.0;
			case BEAST -> 10.0;
			case GORILLA, BEAR, RHINO, ELEPHANT, WHALE -> 5.0;
			case WOLF, SHARK, CHEETAH, PTERODACTYL -> 2.0;
			default -> 0.0;
		};
	}

	/** Step up a full block (the big bodies). */
	public static boolean tallStep(BeastForm f) {
		return f == BeastForm.TREX || f == BeastForm.ELEPHANT || f == BeastForm.BEAST || f == BeastForm.WHALE || f == BeastForm.GORILLA
				|| f == BeastForm.BEAR || f == BeastForm.RHINO;
	}

	/** Flight speed (blocks a second) cruising / sprinting. */
	public static double flightBps(BeastForm f, boolean sprint) {
		return switch (f) {
			case HAWK -> sprint ? 30.0 : 17.0;
			case PTERODACTYL -> sprint ? 26.0 : 15.0;
			case BAT -> sprint ? 16.0 : 10.0;
			case FLY -> sprint ? 9.0 : 5.0;
			default -> 0.0;
		};
	}

	/** The player's body box in the form (eyes near the top, a little lower for the long low animals). */
	public static EntityDimensions dimensions(BeastForm f) {
		float eye = f.height * (f.height < 1.0f ? 0.8f : 0.88f);
		return EntityDimensions.scalable(f.width, f.height).withEyeHeight(eye);
	}

	/** Players this form carries (right-click it to climb on). */
	public static int seats(BeastForm f) {
		return switch (f) {
			case WHALE -> BeastBoyConfig.WHALE_SEATS;
			case PTERODACTYL -> 1;
			default -> 0;
		};
	}

	/** The move ids on R / G / C in this form (null = nothing on that key). Human form's are the quick morphs. */
	public static String[] moves(BeastForm f) {
		return switch (f) {
			case TREX -> new String[] { "trex_bite", "trex_tail", "trex_roar" };
			case GORILLA -> new String[] { "gorilla_punch", "gorilla_throw", "gorilla_beat" };
			case RHINO -> new String[] { "rhino_charge", null, null };
			case ELEPHANT -> new String[] { "elephant_trunk", "elephant_stomp", "elephant_spray" };
			case WOLF -> new String[] { "wolf_bite", "wolf_howl", null };
			case BEAR -> new String[] { "bear_maul", "bear_roar", null };
			case PTERODACTYL -> new String[] { "ptero_dive", null, null };
			case HAWK -> new String[] { "hawk_talon", "hawk_screech", null };
			case BAT -> new String[] { "bat_pulse", null, null };
			case SHARK -> new String[] { "shark_bite", null, null };
			case OCTOPUS -> new String[] { "octopus_ink", "octopus_grab_form", null };
			case WHALE -> new String[] { "whale_slam", null, null };
			case BEAST -> new String[] { "beast_swipe", "beast_leap", "beast_roar" };
			default -> new String[] { null, null, null };
		};
	}
}
