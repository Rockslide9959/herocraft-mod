package com.projecthero.mod.saiyan;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3 Saiyan revamp: the Saiyan core's sounds, all synthesised for the mod by {@code scratchpad/synth_saiyan.js} (no show,
 * film or game audio). The OGGs live in {@code assets/projecthero/sounds/saiyan/}; events are listed in {@code sounds.json} with
 * subtitles. The {@code *_LOOP} events are seamless loops (the client plays them as looping instances while the state lasts);
 * everything else is a one-shot. The kit's moves register their own.
 */
public final class SaiyanSounds {
	/** V: the power-up burst (one-shot, ~1.6 s, from the user's clip), then {@link #KI_CHARGE_LOOP} while it lasts. */
	public static final SoundEvent KI_CHARGE_START = register("saiyan_ki_charge_start");
	public static final SoundEvent KI_CHARGE_LOOP = register("saiyan_ki_charge_loop");
	/** Quiet aura hums: gold (Super Saiyan), red (Kaioken). Loops. */
	public static final SoundEvent AURA_LOOP_GOLD = register("saiyan_aura_loop_gold");
	public static final SoundEvent AURA_LOOP_RED = register("saiyan_aura_loop_red");
	/** Transforming (and Kaioken flaring up): energy blast + shockwave. */
	public static final SoundEvent TRANSFORM_BURST = register("saiyan_transform_burst");
	/** Super Saiyan or Kaioken ending. */
	public static final SoundEvent POWER_DOWN = register("saiyan_power_down");
	/** Taking off into flight. */
	public static final SoundEvent KI_DASH = register("saiyan_ki_dash");
	/** Super Saiyan's 3 minutes are up: the aura fizzles and he gasps. */
	public static final SoundEvent EXHAUSTED = register("saiyan_exhausted");

	private SaiyanSounds() {
	}

	/** Forces the registrations above (called from {@link ProjectHeroMod#onInitialize}). */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
