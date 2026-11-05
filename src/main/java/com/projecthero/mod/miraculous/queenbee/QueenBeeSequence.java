package com.projecthero.mod.miraculous.queenbee;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.2 (user: "add the transformation sequence to queen bee when she transforms while saying the kwami name like how
 * it works with all the other miraculous"): Queen Bee's, played by "Pollen, buzz on!" in full (8.5 s, on
 * {@link MiraculousSequence}). The beats:
 * <ol>
 *   <li>{@link #COMB} -- Pollen spirals into the Bee Comb at the back of her head; a gold flash glints on it.</li>
 *   <li>{@link #BANDS} -- {@link #BAND_COUNT} bands of gold and black coil down her from the comb one after another as she
 *   turns once, the suit running down her body a step with each ({@link #BANDS_END}).</li>
 *   <li>{@link #MASK} -- her right hand sweeps across her eyes and the mask forms behind it.</li>
 *   <li>{@link #TOP} -- the spinning top forms in her hand; at {@link #THROW} it flies out on its string and circles her,
 *   and from {@link #CATCH} it snaps back to its place at her hip ({@link #HIP}).</li>
 *   <li>{@link #POSE} -- a hand on her hip, the other pointing high, a wall of honeycomb flashing behind her.</li>
 * </ol>
 */
public final class QueenBeeSequence {
	public static final int TOTAL = 170;
	public static final int COMB = 16;
	public static final int BANDS = 24;
	public static final int BAND_COUNT = 5;
	/** Ticks between two bands starting. */
	public static final int BAND_STEP = 10;
	/** How long one band takes to coil down her. */
	public static final int BAND_SWEEP = 14;
	public static final int BANDS_END = BANDS + (BAND_COUNT - 1) * BAND_STEP + BAND_SWEEP; // 78
	public static final int MASK = 86;
	public static final int MASK_END = 100;
	public static final int TOP = 106;
	public static final int THROW = 114;
	public static final int CATCH = 136;
	public static final int HIP = 142;
	public static final int POSE = 148;

	/** How much of the suit is on when the last band lands: everything from the collar down ({@code QueenBeeRender.order} keeps the mask for last). */
	public static final float BODY = 0.87f;

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
			// she turns once inside the bands, and ends facing the way she began
			return 360f * ramp(t, BANDS, BANDS_END - BANDS);
		}

		@Override
		public float cover(float t) {
			return QueenBeeSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
			sound(p, SoundEvents.BEE_LOOP, 0.8f, 1.5f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == COMB) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.4f);
				sound(p, SoundEvents.BEEHIVE_ENTER, 1.0f, 1.3f);
			} else if (age >= BANDS && age < BANDS_END - BAND_SWEEP + BAND_STEP && (age - BANDS) % BAND_STEP == 0) {
				// a rising note and a buzz for each band that starts down her
				int band = (int) ((age - BANDS) / BAND_STEP);
				sound(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 0.9f + 0.14f * band);
				sound(p, SoundEvents.BEE_LOOP, 0.5f, 1.2f + 0.1f * band);
			} else if (age == BANDS_END) {
				sound(p, SoundEvents.HONEYCOMB_WAX_ON, 1.0f, 1.2f);
			} else if (age == MASK + 3) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.8f);
				sound(p, SoundEvents.HONEYCOMB_WAX_ON, 1.0f, 1.6f);
			} else if (age == TOP) {
				sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.5f);
			} else if (age == THROW) {
				sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 1.2f);
				sound(p, SoundEvents.BEE_LOOP_AGGRESSIVE, 0.7f, 1.6f);
			} else if (age == HIP) {
				sound(p, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8f, 1.5f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
				sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
				sound(p, SoundEvents.BEE_POLLINATE, 1.0f, 1.2f);
			}
		}
	};

	private QueenBeeSequence() {
	}

	/** When band {@code i} starts down her. */
	public static int bandStart(int i) {
		return BANDS + i * BAND_STEP;
	}

	/** The suit: a step down her body with each band (to {@link #BODY}), the last of it the mask, with her hand. */
	public static float cover(float t) {
		if (t < MASK) {
			float on = 0f;
			for (int i = 0; i < BAND_COUNT; i++) {
				on += ramp(t, bandStart(i), BAND_SWEEP);
			}
			return BODY * on / BAND_COUNT;
		}
		return BODY + (1f - BODY) * ramp(t, MASK + 2, MASK_END - MASK - 4);
	}
}
