package com.projecthero.mod.saiyan.kit;

import com.projecthero.mod.saiyan.SaiyanSounds;

import net.minecraft.sounds.SoundEvent;

/**
 * The move kit's sounds. v0.17.4: each move has its own event now (registered in {@link SaiyanSounds}, so they exist at mod init),
 * pointed at the user's Dragon Ball clips in test builds and at the old synthesised / vanilla stand-ins in a public build (see
 * {@link SaiyanSounds} and {@code docs/SAIYAN_DBZ_SOUND_MAP.md}).
 */
public final class SaiyanKitSounds {
	private SaiyanKitSounds() {
	}

	/** A combo / rush hit. */
	public static final SoundEvent PUNCH_LIGHT = SaiyanSounds.PUNCH;
	/** The axe kick, the smash, the kick that launches. */
	public static final SoundEvent PUNCH_HEAVY = SaiyanSounds.PUNCH_HEAVY;
	/** The combo's last hit and Dragon Rush's finishing blow. */
	public static final SoundEvent FINISHER = SaiyanSounds.FINISHER;
	/** A fist or a kick cutting the air (a whiff). */
	public static final SoundEvent SWING = SaiyanSounds.SWING;
	/** A dash, a leap, a burst of speed. */
	public static final SoundEvent WHOOSH = SaiyanSounds.KI_DASH;
	/** The Meteor Smash vanish, the Solar Flare. */
	public static final SoundEvent VANISH = SaiyanSounds.VANISH;
	/** Popping back in (the Meteor Smash arrival). */
	public static final SoundEvent APPEAR = SaiyanSounds.APPEAR;
	/** A Ki sphere leaving the hand (the volley). */
	public static final SoundEvent KI_SHOT = SaiyanSounds.KI_BLAST;
	/** A Ki Rain shot leaving the hand. */
	public static final SoundEvent RAIN_SHOT = SaiyanSounds.RAIN_SHOT;
	/** The Kamehameha's charge swell. */
	public static final SoundEvent BEAM_CHARGE = SaiyanSounds.KAME_CHARGE;
	/** The Kamehameha leaving the hands. */
	public static final SoundEvent BEAM_FIRE = SaiyanSounds.KAME_FIRE;
	/** The Kamehameha's roar while it fires (looped on the client). */
	public static final SoundEvent BEAM_LOOP = SaiyanSounds.KAME_LOOP;
	/** A small Ki blast bursting. */
	public static final SoundEvent BLAST = SaiyanSounds.KI_BLAST_HIT;
	/** A big burst: the beam's end, a crater. */
	public static final SoundEvent BOOM = SaiyanSounds.BOOM;
	/** The Explosive Wave. */
	public static final SoundEvent WAVE = SaiyanSounds.WAVE;
	/** Dragon Rush's flurry. */
	public static final SoundEvent FLURRY = SaiyanSounds.FLURRY;
}
