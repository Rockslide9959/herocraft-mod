package com.projecthero.mod.miraculous.carapace;

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
 * Carapace's, played by "Wayzz, shell on!" in full (8.5 s, on {@link MiraculousSequence}). The show's beats:
 * <ol>
 *   <li>{@link #BRACELET} -- Wayzz enters the Bracelet on the left wrist; it flashes green.</li>
 *   <li>{@link #BUBBLE} -- a green hexagon shell-bubble closes round him from the feet up.</li>
 *   <li>{@link #PLATES} -- inside it the suit forms panel by panel, like plates of a shell clicking on
 *   ({@link #PANELS} steps).</li>
 *   <li>{@link #SHELL} -- the bubble breaks; the Shield forms on his back and spins.</li>
 *   <li>{@link #POSE} -- a braced stance, the Shield raised, a hexagon flash.</li>
 * </ol>
 */
public final class CarapaceSequence {
	public static final int TOTAL = 170;
	public static final int BRACELET = 16;
	public static final int BUBBLE = 22;
	public static final int BUBBLE_SHUT = 50;
	public static final int PLATES = 52;
	public static final int PLATES_END = 106;
	public static final int PANELS = 9;
	public static final int SHELL = 110;
	public static final int POSE = 146;

	public static final MiraculousSequence.Timeline TIMELINE = new MiraculousSequence.Timeline() {
		@Override
		public int total() {
			return TOTAL;
		}

		@Override
		public double lift(float t) {
			// a little float inside the bubble; down to a planted stance for the pose
			return 0.6 * (smooth((t - BUBBLE) / 26f) - smooth((t - (SHELL - 4)) / 16f));
		}

		@Override
		public float spin(float t) {
			// a slow half turn in the bubble, and round to face front again as the Shield spins on his back
			return 180f * ramp(t, PLATES, PLATES_END - PLATES) + 180f * ramp(t, SHELL + 2, POSE - SHELL - 6);
		}

		@Override
		public float cover(float t) {
			return CarapaceSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f);
			sound(p, SoundEvents.TURTLE_AMBIENT_LAND, 0.8f, 1.3f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == BRACELET) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.0f);
				sound(p, SoundEvents.CONDUIT_ACTIVATE, 0.6f, 1.6f);
			} else if (age == BUBBLE) {
				sound(p, SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.2f);
				sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 1.0f);
			} else if (age == BUBBLE_SHUT) {
				sound(p, SoundEvents.SHIELD_BLOCK, 0.7f, 1.4f);
			} else if (age >= PLATES && age < PLATES_END && plateClick(age)) {
				sound(p, SoundEvents.TURTLE_EGG_CRACK, 0.6f, 1.5f + 0.05f * plate(age));
				sound(p, SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.5f, 1.3f);
			} else if (age == SHELL) {
				sound(p, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
				sound(p, SoundEvents.TURTLE_SHAMBLE, 1.0f, 1.0f);
			} else if (age > SHELL + 4 && age < POSE && age % 5 == 0) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.4f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.1f);
				sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.9f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);
			}
		}
	};

	private CarapaceSequence() {
	}

	/** Which plate is clicking on at {@code t} (0..PANELS-1). */
	public static int plate(float t) {
		return Mth.clamp((int) ((t - PLATES) / ((PLATES_END - PLATES) / (float) PANELS)), 0, PANELS - 1);
	}

	/** The first tick of each plate. */
	public static boolean plateClick(long age) {
		float step = (PLATES_END - PLATES) / (float) PANELS;
		return (int) ((age - PLATES) / step) != (int) ((age - 1 - PLATES) / step);
	}

	/** Panel by panel: each step of the suit snaps on over 3 ticks, the last plate lands at PLATES_END. */
	public static float cover(float t) {
		if (t < PLATES) {
			return 0f;
		}
		if (t >= PLATES_END) {
			return 1f;
		}
		float step = (PLATES_END - PLATES) / (float) PANELS;
		float k = (t - PLATES) / step;
		int done = (int) k;
		float snap = smooth((k - done) * step / 3f);
		return Math.min(1f, (done + snap) / PANELS);
	}

	/** How far the bubble has closed over him, 0..1 (feet to head); it breaks at {@link #SHELL}. */
	public static float bubble(float t) {
		return t < BUBBLE || t >= SHELL ? 0f : ramp(t, BUBBLE, BUBBLE_SHUT - BUBBLE);
	}
}
