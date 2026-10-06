package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.15.4 (explicit user spec): the Mark 7 runs the Mark 6's modern kit ({@link IronManMark6} -- G wheel weapon / Shift
 * Sonic Clap, X Flares or a supersonic boost in the air / Shift JARVIS scan, V wheel / Shift energy shield, C store)
 * with one difference on Z:
 * <pre>
 *   Z        RED LASER -- a solid red beam from the wrist for as long as Z is held: 22 per damage tick (the Unibeam is
 *            28), 70 energy/s, 10 s cooldown after it stops
 *   Shift+Z  Unibeam (hold): 28 per damage tick, 100 energy/s, 15 s cooldown after
 * </pre>
 * Both run on {@link IronManHeldBeam} (the laser with {@link IronManHeldBeam#RED_LASER_BEAM} and its precise, lock-first
 * hit model). Energy figures are base costs (x0.6 on this suit). The Mark 7's orbital drop / delivery pod calling lives
 * in {@code IronManSuitCall} / {@code IronManDeliveryPodEntity} and is untouched by the kit.
 */
public final class IronManMark7 {
	public static final String SUIT_ID = "mark_vii";

	/** Z: red laser (hold); Shift+Z: Unibeam (hold). Also the red laser's cooldown key. */
	public static final String LASER = "mk7_laser";
	/** The Shift+Z Unibeam's cooldown key. */
	public static final String UNIBEAM = "mk7_unibeam";

	// ---- tuning ----
	public static final float UNIBEAM_DAMAGE = 28f;
	public static final float UNIBEAM_ENERGY_PER_TICK = 100f / 20f; // 100 energy/s
	public static final int UNIBEAM_COOLDOWN = 15 * 20;
	public static final double UNIBEAM_RANGE = 32.0;
	public static final IronManHeldBeam.Spec BEAM = new IronManHeldBeam.Spec(SUIT_ID, UNIBEAM, UNIBEAM_DAMAGE,
			UNIBEAM_ENERGY_PER_TICK, UNIBEAM_COOLDOWN, UNIBEAM_RANGE);

	public static final float RED_LASER_DAMAGE = 22f;
	public static final float RED_LASER_ENERGY_PER_TICK = 70f / 20f; // 70 energy/s
	public static final int RED_LASER_COOLDOWN = 10 * 20;
	public static final double RED_LASER_RANGE = 40.0;
	public static final IronManHeldBeam.Spec RED_LASER = new IronManHeldBeam.Spec(SUIT_ID, LASER, RED_LASER_DAMAGE,
			RED_LASER_ENERGY_PER_TICK, RED_LASER_COOLDOWN, RED_LASER_RANGE, IronManHeldBeam.RED_LASER_BEAM, true);

	private IronManMark7() {
	}

	/** Nothing of its own to clear any more (the kit's state lives in {@link IronManMark6}). */
	public static void clearSessionState() {
	}

	/** Called from {@link IronManAbilities#trigger} for the Mark 7's Z slot. */
	public static void trigger(ServerPlayer player, IronManSuit suit, String ability, boolean pressed) {
		if (!LASER.equals(ability) || !SUIT_ID.equals(suit.id())) {
			return;
		}
		if (pressed) {
			IronManHeldBeam.start(player, suit, player.isShiftKeyDown() ? BEAM : RED_LASER);
		} else {
			IronManHeldBeam.stop(player, true);
		}
	}

	/** True while the held red laser (not the Unibeam) is firing. */
	public static boolean redLaserFiring(ServerPlayer player) {
		IronManHeldBeam.Spec spec = IronManHeldBeam.current(player);
		return spec != null && spec.laser();
	}
}
