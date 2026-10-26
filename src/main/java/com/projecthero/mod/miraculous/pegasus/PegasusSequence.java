package com.projecthero.mod.miraculous.pegasus;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user: the other Miraculous get the long transformation too, "look like how they look in the show"): Pegasus's,
 * played by "Kaalki, full gallop!" in full (8.5 s, on {@link MiraculousSequence}). The show's beats:
 * <ol>
 *   <li>{@link #GLASSES} -- Kaalki enters the Horseshoe Glasses; they flash white-gold.</li>
 *   <li>{@link #PORTAL} -- a portal ring of white-gold light opens over his head and he passes through it as it sinks
 *   down him; the suit forms where it has passed (his order of the change runs from the glasses down to the boots).</li>
 *   <li>{@link #MANE} -- a mane-like flourish of light sweeps back over his head.</li>
 *   <li>{@link #SHOE} -- the Horseshoe spins in his hand.</li>
 *   <li>{@link #POSE} -- the final pose beside a portal that opens for a moment and closes again.</li>
 * </ol>
 */
public final class PegasusSequence {
	public static final int TOTAL = 170;
	public static final int GLASSES = 16;
	public static final int PORTAL = 22;
	/** The portal is open over his head and starts down him here, and has passed his feet by PORTAL_END. */
	public static final int PORTAL_DOWN = 36;
	public static final int PORTAL_END = 90;
	public static final int MANE = 94;
	public static final int MANE_END = 116;
	public static final int SHOE = 118;
	public static final int POSE = 146;

	public static final MiraculousSequence.Timeline TIMELINE = new MiraculousSequence.Timeline() {
		@Override
		public int total() {
			return TOTAL;
		}

		@Override
		public double lift(float t) {
			return 1.0 * (smooth(t / 20f) - smooth((t - (POSE + 2)) / 16f));
		}

		@Override
		public float spin(float t) {
			// a gallop-turn as the portal passes, the head tossed with the mane, a half turn with the Horseshoe and back
			float pass = 360f * ramp(t, PORTAL_DOWN, PORTAL_END - PORTAL_DOWN);
			float shoe = 90f * Mth.sin(Mth.PI * Mth.clamp((t - SHOE) / (float) (POSE - SHOE), 0f, 1f));
			return pass + shoe;
		}

		@Override
		public float cover(float t) {
			return PegasusSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 1.2f);
			sound(p, SoundEvents.HORSE_AMBIENT, 0.5f, 1.4f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == GLASSES) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.5f);
				sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 1.4f);
			} else if (age == PORTAL) {
				sound(p, SoundEvents.PORTAL_TRIGGER, 0.35f, 1.9f);
				sound(p, SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.4f);
			} else if (age == PORTAL_DOWN) {
				sound(p, SoundEvents.ENDER_EYE_DEATH, 0.8f, 1.2f);
			} else if (age > PORTAL_DOWN && age < PORTAL_END && age % 9 == 0) {
				sound(p, SoundEvents.HORSE_STEP, 0.6f, 1.3f);
			} else if (age == MANE) {
				sound(p, SoundEvents.HORSE_GALLOP, 0.9f, 1.2f);
				sound(p, SoundEvents.ELYTRA_FLYING, 0.4f, 1.9f);
			} else if (age == SHOE) {
				sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0f, 1.3f);
			} else if (age > SHOE && age < POSE && age % 4 == 0) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.8f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
				sound(p, SoundEvents.HORSE_AMBIENT, 0.8f, 1.2f);
				sound(p, SoundEvents.PORTAL_TRIGGER, 0.3f, 2.0f);
			}
		}
	};

	private PegasusSequence() {
	}

	/** The suit forms where the portal has passed (the glasses down to the boots), the mane hood last. */
	public static float cover(float t) {
		if (t < PORTAL_DOWN) {
			return 0f;
		}
		if (t < MANE) {
			return 0.9f * ramp(t, PORTAL_DOWN, PORTAL_END - PORTAL_DOWN);
		}
		return 0.9f + 0.1f * ramp(t, MANE, MANE_END - MANE);
	}

	/**
	 * Where the passing portal is: {open 0..1, height 0..1 of his height from the feet}. It opens over his head
	 * ({@link #PORTAL}), sinks down him and closes under his feet.
	 */
	public static float[] passPortal(float t) {
		if (t < PORTAL || t >= PORTAL_END + 10) {
			return null;
		}
		float open = Math.min(ramp(t, PORTAL, 10f), 1f - ramp(t, PORTAL_END, 10f));
		float h = 1.25f - 1.35f * ramp(t, PORTAL_DOWN, PORTAL_END - PORTAL_DOWN);
		return new float[] { open, h };
	}

	/** The pose's portal beside him: 0..1 open (opens fast, holds, closes), or 0. */
	public static float posePortal(float t) {
		if (t < POSE - 4) {
			return 0f;
		}
		return Math.min(ramp(t, POSE - 4, 8f), 1f - ramp(t, TOTAL - 12, 12f));
	}
}
