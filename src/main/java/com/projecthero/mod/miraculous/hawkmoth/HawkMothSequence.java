package com.projecthero.mod.miraculous.hawkmoth;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.1 (user: the other Miraculous get the long transformation too, "look like how they look in the show"): Hawk
 * Moth's, played by "Nooroo, dark wings rise!" in full (about 8.8 s, on {@link MiraculousSequence}). He doesn't twirl:
 * he stands still and the change comes up over him. The show's beats:
 * <ol>
 *   <li>{@link #BROOCH} -- Nooroo is drawn into the Brooch at his throat; it flashes violet.</li>
 *   <li>{@link #RISE} -- a purple wave of light RISES from his feet, the suit forming under it (the long change runs
 *   the suit from the boots up, not out from the Brooch), while a swirl of purple butterflies circles him and
 *   dissolves into the suit.</li>
 *   <li>{@link #MASK} -- the silver mask forms over his face.</li>
 *   <li>{@link #CANE} -- the Cane materialises in his hand.</li>
 *   <li>{@link #POSE} -- arms flung wide, under a burst of white and purple butterflies.</li>
 * </ol>
 */
public final class HawkMothSequence {
	public static final int TOTAL = 176;
	public static final int BROOCH = 18;
	public static final int RISE = 28;
	public static final int RISE_END = 90;
	public static final int SWARM = 38;
	public static final int SWARM_END = 100;
	public static final int MASK = 102;
	public static final int MASK_END = 122;
	public static final int CANE = 126;
	public static final int POSE = 150;
	/** The suit is done to the neck at this much of the change (the mask is the rest) -- {@code HawkMothSuitRender}'s split. */
	public static final float BODY_END = 0.72f;

	public static final MiraculousSequence.Timeline TIMELINE = new MiraculousSequence.Timeline() {
		@Override
		public int total() {
			return TOTAL;
		}

		@Override
		public double lift(float t) {
			// he rises slowly with the wave and sinks back at the end
			return 0.7 * smooth((t - 10) / 50f) - 0.7 * smooth((t - (POSE + 8)) / 16f);
		}

		@Override
		public float spin(float t) {
			return 0f;
		}

		@Override
		public float cover(float t) {
			return HawkMothSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.7f);
			sound(p, SoundEvents.SCULK_CLICKING, 0.8f, 1.2f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == BROOCH) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
				sound(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7f, 1.2f);
			} else if (age == RISE) {
				sound(p, SoundEvents.BEACON_ACTIVATE, 0.9f, 1.3f);
				sound(p, SoundEvents.PORTAL_TRIGGER, 0.25f, 1.8f);
			} else if (age > SWARM && age < SWARM_END && age % 6 == 0) {
				sound(p, SoundEvents.PHANTOM_FLAP, 0.35f, 1.9f);
			} else if (age == MASK) {
				sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 1.3f);
				sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.0f);
			} else if (age == CANE) {
				sound(p, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.7f, 1.4f);
				sound(p, SoundEvents.CHAIN_PLACE, 1.0f, 0.9f);
			} else if (age == POSE) {
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 0.9f);
				sound(p, SoundEvents.ELYTRA_FLYING, 0.6f, 1.7f);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
			}
		}
	};

	private HawkMothSequence() {
	}

	/** The wave climbs him from the boots to the neck (to {@link #BODY_END}); the mask is the rest. */
	public static float cover(float t) {
		if (t < RISE + 2) {
			return 0f;
		}
		if (t < MASK) {
			return BODY_END * ramp(t, RISE + 2, RISE_END - RISE - 2);
		}
		return BODY_END + (1f - BODY_END) * ramp(t, MASK, MASK_END - MASK - 2);
	}

	/** How high the rising wave is, 0..1 of his height (it leads the suit a little). */
	public static float wave(float t) {
		return Math.min(1f, ramp(t, RISE, RISE_END - RISE - 6) * 1.08f);
	}
}
