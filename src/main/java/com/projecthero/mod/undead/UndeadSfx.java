package com.projecthero.mod.undead;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Undead's server-side sounds -- everything a server event makes that every nearby player must hear (a part tearing
 * off, the bullets, a launch, the punch). Each call is a single {@code level.playSound(null, ...)} on
 * {@link SoundSource#PLAYERS} with a small random pitch spread, so it reaches every client in range (the attenuation
 * distance is in sounds.json). Called on a client level it does nothing (the client side is {@code client.undead.UndeadSounds}).
 * Mixed against vanilla: nothing is louder than a zombie hurt at a block away.
 */
public final class UndeadSfx {
	private UndeadSfx() {
	}

	private static void play(Level level, Vec3 at, SoundEvent ev, float volume, float pitch) {
		if (level == null || level.isClientSide || at == null) {
			return;
		}
		float spread = 0.92f + level.random.nextFloat() * 0.16f;
		level.playSound(null, at.x, at.y, at.z, ev, SoundSource.PLAYERS, volume, Mth.clamp(pitch * spread, 0.5f, 2.0f));
	}

	/** A part tears off (burst): the wet tear and crunch, with a spurt of blood. */
	public static void partBreak(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.PART_BREAK, 1.0f, 1.0f);
		play(level, at, UndeadSoundEvents.BLOOD_SPRAY, 0.55f, 1.0f);
	}

	/** Undead: the whole body bursts at once -- a big splash under a deep, slow tear. */
	public static void shatter(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_BURST, 1.0f, 0.8f);
		play(level, at, UndeadSoundEvents.PART_BREAK, 1.0f, 0.7f);
	}

	/**
	 * Reform (tap V): the whole regrowth in one beat -- v0.17.3 (user: the regeneration sounds are the steam fizz now): a fizz
	 * a little stronger than a part finishing ({@code parts}: something was actually closed; without, a soft one as the wounds knit).
	 */
	public static void reform(Level level, Vec3 at, boolean parts) {
		play(level, at, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, parts ? 0.4f : 0.2f, parts ? 0.95f : 1.3f);
	}

	/** A big splash of blood; {@code size} 0..1 (bigger is louder and lower). */
	public static void bloodBurst(Level level, Vec3 at, float size) {
		float s = Mth.clamp(size, 0f, 1f);
		play(level, at, UndeadSoundEvents.BLOOD_BURST, 0.55f + 0.45f * s, 1.15f - 0.3f * s);
	}

	public static void spray(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_SPRAY, 0.6f, 1.0f);
	}

	public static void drip(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_DRIP, 0.35f, 1.0f);
	}

	public static void splat(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_SPLAT, 0.5f, 1.0f);
	}

	/** Parts Bullet leaves the hand: a flesh pellet popping off. */
	public static void partsBullet(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BULLET_PARTS, 0.75f, 1.0f);
	}

	/** Crimson Bullet fires: the heavier wet shot. */
	public static void crimsonBullet(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BULLET_CRIMSON, 1.0f, 1.0f);
	}

	/** A Crimson Bullet lands on something: a heavy splash. */
	public static void crimsonImpact(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_BURST, 0.8f, 1.1f);
	}

	/** A body part (or the head) launched: the wet flap and whoosh. */
	public static void launch(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.LAUNCH_WHOOSH, 0.85f, 1.0f);
	}

	/** The head (or a part) thuds into something / rolls along: {@code speed} 0..1 drives the loudness. */
	public static void headRoll(Level level, Vec3 at, float speed) {
		float s = Mth.clamp(speed, 0.1f, 1f);
		play(level, at, UndeadSoundEvents.HEAD_ROLL, 0.35f + 0.45f * s, 0.9f + 0.2f * s);
	}

	/** The stretched punch let go. */
	public static void punchFire(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.PUNCH_FIRE, 0.9f, 1.0f);
	}

	/** One-shot sinew stretch (the server's version of the wind-up, for a move without a client loop). */
	public static void punchStretch(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.PUNCH_STRETCH, 0.6f, 1.0f);
	}

	/** A Bone Sword swing: a soft whoosh ({@code size} 0..1, bigger is louder and lower). */
	public static void swordSwing(Level level, Vec3 at, float size) {
		float s = Mth.clamp(size, 0f, 1f);
		play(level, at, UndeadSoundEvents.LAUNCH_WHOOSH, 0.3f + 0.3f * s, 1.5f - 0.3f * s);
	}

	/** A Bone Sword move cuts into something: a wet slash. */
	public static void swordHit(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_SPLAT, 0.7f, 0.85f);
		play(level, at, UndeadSoundEvents.BLOOD_SPRAY, 0.35f, 1.2f);
	}

	public static void jetStart(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_JET_START, 0.8f, 1.0f);
	}

	public static void jetStop(Level level, Vec3 at) {
		play(level, at, UndeadSoundEvents.BLOOD_JET_STOP, 0.7f, 1.0f);
	}
}
