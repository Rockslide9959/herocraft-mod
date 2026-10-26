package com.projecthero.mod.miraculous.viperion;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.1 (user: the other Miraculous get the long transformation too, "look like how they look in the show"):
 * Viperion's, played by "Sass, scales slither!" in full (8.5 s, on {@link MiraculousSequence}). The show's beats:
 * <ol>
 *   <li>{@link #BRACELET} -- Sass coils into the Snake Bracelet on the right wrist; it flashes teal.</li>
 *   <li>{@link #COIL} -- a translucent snake of teal light coils up the arm and round the body, the suit forming in its
 *   wake (his order of the change runs out from the bracelet).</li>
 *   <li>{@link #SCALES} -- scale patterns ripple up over the suit.</li>
 *   <li>{@link #LYRE} -- the Lyre forms in his hand.</li>
 *   <li>{@link #VISOR} -- the visor slides down over his eyes.</li>
 *   <li>{@link #POSE} -- the final pose with the Lyre, and a snake-eye glint.</li>
 * </ol>
 */
public final class ViperionSequence {
	public static final int TOTAL = 170;
	public static final int BRACELET = 16;
	public static final int COIL = 22;
	public static final int COIL_END = 90;
	public static final int SCALES = 92;
	public static final int SCALES_END = 112;
	public static final int LYRE = 114;
	public static final int VISOR = 130;
	public static final int VISOR_END = 144;
	public static final int POSE = 148;

	public static final MiraculousSequence.Timeline TIMELINE = new MiraculousSequence.Timeline() {
		@Override
		public int total() {
			return TOTAL;
		}

		@Override
		public double lift(float t) {
			return 0.9 * (smooth(t / 20f) - smooth((t - (POSE + 4)) / 16f));
		}

		@Override
		public float spin(float t) {
			// he turns with the snake as it winds round him, then back a little for the pose
			return 540f * ramp(t, COIL + 10, COIL_END - COIL - 10) - 25f * ramp(t, POSE, 8f);
		}

		@Override
		public float cover(float t) {
			return ViperionSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
			sound(p, SoundEvents.CAT_HISS, 0.35f, 2.0f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == BRACELET) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.4f);
				sound(p, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, 1.2f);
			} else if (age == COIL) {
				sound(p, SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.5f);
			} else if (age > COIL && age < COIL_END && age % 7 == 0) {
				sound(p, SoundEvents.SILVERFISH_STEP, 0.6f, 1.2f + 0.01f * (age - COIL));
			} else if (age == SCALES) {
				sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.6f);
				sound(p, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.9f, 1.3f);
			} else if (age == LYRE) {
				sound(p, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, 0.9f);
				sound(p, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, 1.2f);
			} else if (age == LYRE + 4) {
				sound(p, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, 1.5f);
			} else if (age == VISOR) {
				sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.5f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
				sound(p, SoundEvents.CAT_HISS, 0.5f, 1.7f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
			}
		}
	};

	private ViperionSequence() {
	}

	/** The coil to 0.9 (the head held back for the visor), 0.9..0.96 in the scale ripple, the visor the rest. */
	public static float cover(float t) {
		if (t < COIL + 6) {
			return 0f;
		}
		if (t < SCALES) {
			return 0.9f * ramp(t, COIL + 6, COIL_END - COIL - 6);
		}
		if (t < VISOR) {
			return 0.9f + 0.06f * ramp(t, SCALES, SCALES_END - SCALES);
		}
		return 0.96f + 0.04f * ramp(t, VISOR, VISOR_END - VISOR);
	}

	/** How far the snake of light has wound up him, 0..1 (its head leads the suit). */
	public static float coil(float t) {
		return ramp(t, COIL, COIL_END - COIL);
	}
}
