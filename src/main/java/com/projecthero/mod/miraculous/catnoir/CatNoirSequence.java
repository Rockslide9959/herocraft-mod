package com.projecthero.mod.miraculous.catnoir;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user: the other Miraculous get the long transformation too, "look like how they look in the show"): Cat
 * Noir's, played by "Plagg, claws out!" in full (8.5 s, on {@link MiraculousSequence}). The show's beats:
 * <ol>
 *   <li>{@link #RING} -- Plagg is sucked, spinning, into the Ring; it turns black and a green paw print lights on it.</li>
 *   <li>{@link #SWEEP} -- he sweeps the ringed hand across, and a ripple of green-black energy runs from the Ring up the
 *   arm and over the body: the suit forms in that wave (his own order of the change, out from the Ring).</li>
 *   <li>{@link #MASK} -- the cat ears pop up and the mask forms with a slide of the hand across the eyes.</li>
 *   <li>{@link #BATON} -- the belt-tail whips; the Baton extends and he twirls it.</li>
 *   <li>{@link #POSE} -- he drops into a crouched cat pose, a flash of green slit eyes.</li>
 * </ol>
 */
public final class CatNoirSequence {
	public static final int TOTAL = 170;
	public static final int RING = 18;
	public static final int SWEEP = 30;
	public static final int WAVE = 36;
	public static final int WAVE_END = 96;
	public static final int MASK = 100;
	public static final int MASK_END = 118;
	public static final int TAIL = 120;
	public static final int BATON = 124;
	public static final int POSE = 148;

	public static final MiraculousSequence.Timeline TIMELINE = new MiraculousSequence.Timeline() {
		@Override
		public int total() {
			return TOTAL;
		}

		@Override
		public double lift(float t) {
			return CatNoirSequence.lift(t);
		}

		@Override
		public float spin(float t) {
			return CatNoirSequence.spin(t);
		}

		@Override
		public float cover(float t) {
			return CatNoirSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.9f);
			sound(p, SoundEvents.CAT_AMBIENT, 0.6f, 1.4f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == RING) {
				sound(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8f, 1.6f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.9f);
			} else if (age == SWEEP) {
				sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 0.9f, 1.2f);
			} else if (age == WAVE) {
				sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.1f);
			} else if (age > WAVE && age < WAVE_END && age % 12 == 0) {
				sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.8f + 0.01f * (age - WAVE));
			} else if (age == MASK) {
				sound(p, SoundEvents.CAT_PURREOW, 0.7f, 1.3f);
				sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 1.2f);
			} else if (age == TAIL) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.6f);
			} else if (age == BATON) {
				sound(p, SoundEvents.CHAIN_PLACE, 1.0f, 1.6f);
				sound(p, SoundEvents.PISTON_EXTEND, 0.6f, 1.8f);
			} else if (age > BATON + 4 && age < POSE && age % 4 == 0) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.9f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f);
				sound(p, SoundEvents.CAT_HISS, 0.5f, 1.4f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
			}
		}
	};

	private CatNoirSequence() {
	}

	/** Up over the first second; he drops down into the crouch at the end. */
	public static double lift(float t) {
		return 0.9 * (smooth(t / 20f) - smooth((t - (POSE - 2)) / 10f));
	}

	/** A full turn in the wave, and a half turn to the side and back with the Baton twirl. */
	public static float spin(float t) {
		float wave = 360f * ramp(t, WAVE + 4, WAVE_END - WAVE - 4);
		float twirl = 120f * Mth.sin(Mth.PI * Mth.clamp((t - BATON) / (float) (POSE - BATON), 0f, 1f));
		return wave + twirl;
	}

	/** The wave from the Ring out over the body to 0.88; the head (and the ears, at 0.9) with the slide across the eyes. */
	public static float cover(float t) {
		if (t < WAVE) {
			return 0f;
		}
		if (t < MASK + 2) {
			return 0.88f * ramp(t, WAVE, WAVE_END - WAVE);
		}
		return 0.88f + 0.12f * ramp(t, MASK + 2, MASK_END - MASK - 4);
	}

	/** The Baton in his hand: how far it is out, 0..1 (0 = not in the hand). */
	public static float baton(float t) {
		if (t < BATON || t >= POSE + 4) {
			return 0f;
		}
		return Math.min(ramp(t, BATON, 6f), 1f - ramp(t, POSE, 4f));
	}
}
