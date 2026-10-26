package com.projecthero.mod.miraculous.multimouse;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user: the other Miraculous get the long transformation too, "look like how they look in the show"):
 * Multimouse's, played by "Mullo, multiply!" in full (8.5 s, on {@link MiraculousSequence}). The beats:
 * <ol>
 *   <li>{@link #PENDANT} -- Mullo zips into the Mouse Pendant; a pink flash glints on it.</li>
 *   <li>{@link #SPLIT} -- she splits for a moment into a ring of small pink-lit copies of herself spinning round her (a
 *   nod to Multitude); from {@link #MERGE} they converge back into her one by one, the suit forming out from the
 *   pendant a step with each one that merges ({@link #merged}).</li>
 *   <li>{@link #HOOD} -- the hood rises up over her head, and at {@link #EARS} the round mouse ears pop up with a
 *   squeak-sparkle.</li>
 *   <li>{@link #ROPE} -- the jump rope unspools from her belt, she skips it once round her body ({@link #SKIP}), then
 *   winds it back to her belt ({@link #WIND}) so the rope tail swings out.</li>
 *   <li>{@link #POSE} -- a peace sign and a hand on her hip, with a pink star-glint.</li>
 * </ol>
 */
public final class MultimouseSequence {
	public static final int TOTAL = 170;
	public static final int PENDANT = 16;
	public static final int SPLIT = 22;
	public static final int MERGE = 62;
	public static final int COPIES = 8;
	/** Ticks between two copies merging back. */
	public static final int MERGE_STEP = 3;
	public static final int MERGE_END = MERGE + COPIES * MERGE_STEP; // 86
	public static final int HOOD = 92;
	public static final int HOOD_END = 104;
	public static final int EARS = 106;
	public static final int ROPE = 116;
	public static final int SKIP = 122;
	public static final int WIND = 134;
	public static final int WIND_END = 142;
	public static final int POSE = 148;

	/** How much of the suit is on when the last copy has merged (the head is the hood's beat). */
	private static final float BODY = 0.9f;

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
			// she turns once with the ring of copies, then a little back for the pose
			return 360f * ramp(t, SPLIT + 6, MERGE_END - SPLIT - 6) - 20f * ramp(t, POSE, 8f);
		}

		@Override
		public float cover(float t) {
			return MultimouseSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
			sound(p, SoundEvents.BAT_AMBIENT, 0.25f, 2.0f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == PENDANT) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.7f);
				sound(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.6f);
			} else if (age == SPLIT) {
				sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9f, 1.5f);
				sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
			} else if (age > SPLIT && age < MERGE && age % 8 == 0) {
				sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 1.4f + 0.012f * (age - SPLIT));
			} else if (age >= MERGE && age < MERGE_END && (age - MERGE) % MERGE_STEP == 0) {
				// a pop for each copy that merges back, climbing the scale
				sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 0.9f + 0.12f * ((age - MERGE) / MERGE_STEP));
			} else if (age == HOOD) {
				sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 1.3f);
			} else if (age == EARS) {
				sound(p, SoundEvents.BAT_AMBIENT, 0.6f, 2.0f);
				sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.8f);
			} else if (age == ROPE) {
				sound(p, SoundEvents.LEASH_KNOT_BREAK, 0.9f, 1.4f);
			} else if (age == SKIP + 6) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
			} else if (age == WIND) {
				sound(p, SoundEvents.LEASH_KNOT_PLACE, 0.9f, 1.5f);
			} else if (age == WIND_END) {
				sound(p, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8f, 1.5f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.9f);
				sound(p, SoundEvents.BAT_AMBIENT, 0.4f, 2.0f);
			}
		}
	};

	private MultimouseSequence() {
	}

	/** How many of the copies have merged back by {@code t} (fractional while one is merging), 0..{@link #COPIES}. */
	public static float merged(float t) {
		return Mth.clamp((t - MERGE) / MERGE_STEP, 0f, COPIES);
	}

	/**
	 * The suit: a step out from the pendant with each copy that merges (to {@link #BODY}), the hood and the mask with the
	 * hood's beat, the last of it as the rope winds to her belt.
	 */
	public static float cover(float t) {
		if (t < MERGE) {
			return 0f;
		}
		if (t < HOOD) {
			float m = merged(t);
			int whole = (int) m;
			// each step eases in as its copy arrives
			float step = whole + smooth(m - whole);
			return BODY * Math.min(1f, step / COPIES);
		}
		if (t < WIND) {
			return BODY + 0.07f * ramp(t, HOOD, HOOD_END - HOOD);
		}
		return 0.97f + 0.03f * ramp(t, WIND, WIND_END - WIND);
	}

	/** The hood: 0 down the back of her neck, 1 up over her head (outside the sequence it is just on). */
	public static float hood(float t) {
		return ramp(t, HOOD, HOOD_END - HOOD);
	}
}
