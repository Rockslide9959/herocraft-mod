package com.projecthero.mod.gyro;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3 Gyro Zeppeli (the Spin): every sound the power plays. CONTRACT shared by the two build agents: the ART agent supplies
 * the OGGs ({@code assets/projecthero/sounds/gyro/}) and the {@code sounds.json} entries + subtitles, cut from the user's clip
 * {@code docs/reference/gyro/spin_sfx_source.mp4}; the POWER agent plays them. Keep the ids; add more only if needed.
 * {@code *_LOOP} events are seamless loops.
 */
public final class GyroSounds {
	/** R / Shift+R: a ball leaves the hand. */
	public static final SoundEvent BALL_THROW = register("gyro_ball_throw");
	/** The whirr of a spinning ball (in flight, on the ground for Scan, orbiting for Spin Shield). Loop. */
	public static final SoundEvent BALL_SPIN_LOOP = register("gyro_ball_spin_loop");
	/** A ball strikes a creature or block. */
	public static final SoundEvent BALL_HIT = register("gyro_ball_hit");
	/** A ball slaps back into the hand. */
	public static final SoundEvent BALL_CATCH = register("gyro_ball_catch");
	/** A target's body spinning (skin twisting, G Spin Touch, the 3 s spin after a hit). */
	public static final SoundEvent BODY_SPIN = register("gyro_body_spin");
	/** Shift+G / Shift+Z held: the Golden Rectangle charging. Loop. */
	public static final SoundEvent GOLDEN_CHARGE_LOOP = register("gyro_golden_charge_loop");
	/** Golden Spin released (louder, brighter on a Perfect Spin -- the client may pitch it). */
	public static final SoundEvent GOLDEN_RELEASE = register("gyro_golden_release");
	/** Ball Breaker bursting out and striking. */
	public static final SoundEvent BALL_BREAKER = register("gyro_ball_breaker");
	/** Z: the ball humming on the ground as it scans. */
	public static final SoundEvent SCAN = register("gyro_scan");
	/** C: the ball hammering into the ground. */
	public static final SoundEvent QUAKE = register("gyro_quake");
	/** V: Healing Spin. */
	public static final SoundEvent HEAL = register("gyro_heal");
	/** X: Spin Leap. */
	public static final SoundEvent LEAP = register("gyro_leap");
	/** Shift+X: whistling for Valkyrie. */
	public static final SoundEvent WHISTLE = register("gyro_whistle");

	private GyroSounds() {
	}

	/** Forces the registrations above (called from {@link ProjectHeroMod#onInitialize}). */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
