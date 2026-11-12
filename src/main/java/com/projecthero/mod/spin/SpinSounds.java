package com.projecthero.mod.spin;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3 the Spin (the Spin): every sound the power plays. CONTRACT shared by the two build agents: the ART agent supplies
 * the OGGs ({@code assets/projecthero/sounds/spin/}) and the {@code sounds.json} entries + subtitles, cut from the user's clip
 * {@code docs/reference/spin/spin_sfx_source.mp4}; the POWER agent plays them. Keep the ids; add more only if needed.
 * {@code *_LOOP} events are seamless loops.
 */
public final class SpinSounds {
	/** R / Shift+R: a ball leaves the hand. */
	public static final SoundEvent BALL_THROW = register("spin_ball_throw");
	/** The whirr of a spinning ball (in flight, on the ground for Scan, orbiting for Spin Shield). Loop. */
	public static final SoundEvent BALL_SPIN_LOOP = register("spin_ball_spin_loop");
	/** A ball strikes a creature or block. */
	public static final SoundEvent BALL_HIT = register("spin_ball_hit");
	/** A ball slaps back into the hand. */
	public static final SoundEvent BALL_CATCH = register("spin_ball_catch");
	/** A target's body spinning (skin twisting, G Spin Touch, the 3 s spin after a hit). */
	public static final SoundEvent BODY_SPIN = register("spin_body_spin");
	/** Shift+G / Shift+Z held: the Golden Rectangle charging. Loop. */
	public static final SoundEvent GOLDEN_CHARGE_LOOP = register("spin_golden_charge_loop");
	/** Golden Spin released (louder, brighter on a Perfect Spin -- the client may pitch it). */
	public static final SoundEvent GOLDEN_RELEASE = register("spin_golden_release");
	/** Ball Breaker bursting out and striking. */
	public static final SoundEvent BALL_BREAKER = register("spin_ball_breaker");
	/** Z: the ball humming on the ground as it scans. */
	public static final SoundEvent SCAN = register("spin_scan");
	/** C: the ball hammering into the ground. */
	public static final SoundEvent QUAKE = register("spin_quake");
	/** V: Healing Spin. */
	public static final SoundEvent HEAL = register("spin_heal");
	/** X: Spin Leap. */
	public static final SoundEvent LEAP = register("spin_leap");
	/** Shift+X: whistling for Valkyrie. */
	public static final SoundEvent WHISTLE = register("spin_whistle");

	private SpinSounds() {
	}

	/** Forces the registrations above (called from {@link ProjectHeroMod#onInitialize}). */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
