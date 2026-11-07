package com.projecthero.mod.miraculous.ryuko;

import static com.projecthero.mod.miraculous.MiraculousSequence.ramp;
import static com.projecthero.mod.miraculous.MiraculousSequence.smooth;
import static com.projecthero.mod.miraculous.MiraculousSequence.sound;

import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.3 (user: "add a transformation scene please"): Ryuko's, played by "Longg, bring the storm!" in full (8.5 s, on
 * {@link MiraculousSequence}). The storm comes to her, one element at a time:
 * <ol>
 *   <li>{@link #CHOKER} -- Longg spirals into the Dragon Choker at her throat; a red flash.</li>
 *   <li>{@link #WIND} -- a cyclone wraps her from the feet as she turns once; the suit takes her boots and legs.</li>
 *   <li>{@link #WATER} -- a wave climbs her in a rising ring; the suit takes her body and arms.</li>
 *   <li>{@link #BOLT} -- lightning strikes her; the suit finishes.</li>
 *   <li>{@link #MASK} -- her hand sweeps across her eyes and the mask forms behind it.</li>
 *   <li>{@link #SWORD} -- the sword forms in her hand out of a last flicker; at {@link #SLASH} she cuts the air, and at
 *   {@link #POSE} she holds it high as a dragon of light circles her.</li>
 * </ol>
 */
public final class RyukoSequence {
	public static final int TOTAL = 170;
	public static final int CHOKER = 16;
	public static final int WIND = 24;
	public static final int WATER = 56;
	public static final int BOLT = 84;
	public static final int BOLT_END = 92;
	public static final int MASK = 98;
	public static final int MASK_END = 112;
	public static final int SWORD = 118;
	public static final int SLASH = 134;
	public static final int POSE = 146;

	/** How much of the suit each element leaves on: {@code RyukoRender.order} keeps the mask for the last of it. */
	public static final float AFTER_WIND = 0.42f;
	public static final float BODY = 0.87f;

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
			// once round inside the cyclone, ending where she began
			return 360f * ramp(t, WIND, WATER - WIND);
		}

		@Override
		public float cover(float t) {
			return RyukoSequence.cover(t);
		}

		@Override
		public void start(ServerPlayer p) {
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.9f);
		}

		@Override
		public void beats(ServerPlayer p, MiraculousState s, long age) {
			if (age == CHOKER) {
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 1.1f);
				sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.5f);
			} else if (age == WIND) {
				sound(p, SoundEvents.ELYTRA_FLYING, 0.7f, 1.3f);
				sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.4f);
			} else if (age == WATER) {
				sound(p, SoundEvents.GENERIC_SPLASH, 1.0f, 0.9f);
				sound(p, SoundEvents.WATER_AMBIENT, 1.0f, 1.0f);
			} else if (age == BOLT) {
				sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.3f);
				sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 1.4f);
			} else if (age == MASK + 3) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.8f);
			} else if (age == SWORD) {
				sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 1.5f);
			} else if (age == SLASH) {
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
			} else if (age == POSE) {
				sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 1.7f);
				sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.1f);
			}
		}
	};

	private RyukoSequence() {
	}

	/** The suit: boots to hips in the wind, the rest of her body in the water and the bolt, the mask last with her hand. */
	public static float cover(float t) {
		if (t < MASK) {
			return AFTER_WIND * ramp(t, WIND, WATER - WIND) + (BODY - AFTER_WIND) * ramp(t, WATER, BOLT_END - WATER);
		}
		return BODY + (1f - BODY) * ramp(t, MASK + 2, MASK_END - MASK - 4);
	}
}
