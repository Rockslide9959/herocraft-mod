package com.projecthero.mod.miraculous.renarouge;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user: the other Miraculous get the long transformation too, "look like how they look in the show"): Rena
 * Rouge's, played by "Trixx, let's pounce!" in full (8.5 s, on {@link MiraculousSequence}). The show's beats:
 * <ol>
 *   <li>{@link #NECKLACE} -- Trixx dives into the Necklace; it flashes orange.</li>
 *   <li>{@link #SWIRL} -- an orange fox-fire swirl spirals round her from the neck down as she turns; the suit forms
 *   under it (out from the Necklace, up and down).</li>
 *   <li>{@link #TAIL} -- the fox tail flicks out and swishes, the ears form (the suit's fox parts grow in).</li>
 *   <li>{@link #FLUTE} -- the Flute is spun up in the air and caught.</li>
 *   <li>{@link #POSE} -- a sly pose, the Flute behind her back, an orange shimmer fading off her like an illusion.</li>
 * </ol>
 */
public final class RenaRougeSequence {
	public static final int TOTAL = 170;
	public static final int NECKLACE = 16;
	public static final int SWIRL = 26;
	public static final int SWIRL_END = 92;
	public static final int TAIL = 96;
	public static final int TAIL_END = 118;
	public static final int FLUTE = 120;
	/** The Flute leaves her hand spinning at FLUTE + 4 and drops back into it here. */
	public static final int CATCH = 138;
	public static final int POSE = 148;

	public static final MiraculousSequence.Timeline TIMELINE = new MiraculousSequence.Timeline() {
		@Override
		public int total() {
			return TOTAL;
		}

		@Override
		public double lift(float t) {
			return 1.0 * (smooth(t / 20f) - smooth((t - (POSE + 4)) / 16f));
		}

		@Override
		public float spin(float t) {
			return RenaRougeSequence.spin(t);
		}

		@Override
		public float cover(float t) {
			return RenaRougeSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 1.1f);
			sound(p, SoundEvents.FOX_AMBIENT, 0.6f, 1.5f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == NECKLACE) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.3f);
				sound(p, SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 1.4f);
			} else if (age == SWIRL) {
				sound(p, SoundEvents.FIRECHARGE_USE, 0.6f, 1.5f);
				sound(p, SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.3f);
			} else if (age > SWIRL && age < SWIRL_END && age % 8 == 0) {
				sound(p, SoundEvents.FIRE_AMBIENT, 0.6f, 1.6f);
			} else if (age == TAIL) {
				sound(p, SoundEvents.FOX_SNIFF, 1.0f, 1.2f);
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
			} else if (age == TAIL + 10) {
				sound(p, SoundEvents.FOX_AMBIENT, 0.8f, 1.3f);
			} else if (age == FLUTE + 4) {
				sound(p, SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 1.0f);
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f, 1.9f);
			} else if (age == CATCH) {
				sound(p, SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.0f, 1.6f);
				sound(p, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.6f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
				sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.7f);
			}
		}
	};

	private RenaRougeSequence() {
	}

	/** A turn and a half in the swirl, a quick look over the shoulder for the tail, and a half turn back for the pose. */
	public static float spin(float t) {
		float swirl = 540f * ramp(t, SWIRL + 4, SWIRL_END - SWIRL - 4);
		float tail = 40f * Mth.sin(Mth.PI * Mth.clamp((t - TAIL) / (float) (TAIL_END - TAIL), 0f, 1f));
		float pose = -30f * ramp(t, POSE, 8f);
		return swirl + tail + pose;
	}

	/** The swirl leaves the suit at 0.7 (no fox parts yet); the tail and ears grow in with the rest (0.7..1). */
	public static float cover(float t) {
		if (t < SWIRL + 6) {
			return 0f;
		}
		if (t < TAIL) {
			return 0.7f * ramp(t, SWIRL + 6, SWIRL_END - SWIRL - 6);
		}
		return 0.7f + 0.3f * ramp(t, TAIL, TAIL_END - TAIL);
	}

	/** The Flute up in the air between leaving her hand and the catch: 0..1 of the flight, or -1. */
	public static float fluteFlight(float t) {
		if (t < FLUTE + 4 || t >= CATCH) {
			return -1f;
		}
		return (t - (FLUTE + 4)) / (CATCH - (FLUTE + 4));
	}
}
