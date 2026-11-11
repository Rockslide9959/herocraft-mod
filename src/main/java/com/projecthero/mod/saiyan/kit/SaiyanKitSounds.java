package com.projecthero.mod.saiyan.kit;

import com.projecthero.mod.saiyan.SaiyanSounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.3 Saiyan revamp: the move kit's sounds. The core keeps only a few synthesised Saiyan sounds (Ki charge, dash,
 * transform), so the kit reuses those where they fit and quiet vanilla sounds for the rest. Nothing to register.
 */
public final class SaiyanKitSounds {
	private SaiyanKitSounds() {
	}

	/** A combo / rush hit. */
	public static final SoundEvent PUNCH_LIGHT = SoundEvents.PLAYER_ATTACK_STRONG;
	/** A finisher, the axe kick, the smash. */
	public static final SoundEvent PUNCH_HEAVY = SoundEvents.PLAYER_ATTACK_KNOCKBACK;
	/** A whiff, a dash, a Ki sphere leaving the hand. */
	public static final SoundEvent WHOOSH = SaiyanSounds.KI_DASH;
	/** The Meteor Smash vanish, the Solar Flare. */
	public static final SoundEvent VANISH = SoundEvents.ENDERMAN_TELEPORT;
	/** The Kamehameha's charge swell. */
	public static final SoundEvent BEAM_CHARGE = SaiyanSounds.KI_CHARGE_START;
	/** The Kamehameha's roar while it fires (looped on the client). */
	public static final SoundEvent BEAM_LOOP = SaiyanSounds.KI_CHARGE_LOOP;
	/** A small Ki blast bursting. */
	public static final SoundEvent BLAST = SoundEvents.FIREWORK_ROCKET_BLAST;
	/** A big burst: the beam's end, the wave, a crater. */
	public static final SoundEvent BOOM = SoundEvents.GENERIC_EXPLODE.value();
	/** Dragon Rush's flurry. */
	public static final SoundEvent FLURRY = SoundEvents.PLAYER_ATTACK_SWEEP;
}
