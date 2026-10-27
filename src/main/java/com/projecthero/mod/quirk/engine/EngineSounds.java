package com.projecthero.mod.quirk.engine;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1 Engine revamp: the Quirk's own sounds (synthesised by {@code scratchpad/synth_quirk_kits3_sounds.js}, mono ogg in
 * {@code sounds/quirk/engine_*.ogg}, events {@code quirk_engine_*} in {@code sounds.json}). Registered from {@link EngineMod#initialize}.
 */
public final class EngineSounds {
	/** A calf-engine rev (a two-stroke snarl and a rising whine); played pitched up a step per gear. */
	public static final SoundEvent REV = register("quirk_engine_rev");
	/** Recipro Burst: the overdrive ignition -- a supersonic crack into a jet roar. */
	public static final SoundEvent BURST = register("quirk_engine_burst");

	private EngineSounds() {
	}

	static void initialize() {
	}

	/** The rev's pitch in a gear (0..3+; Turbo / the Burst above). */
	public static float revPitch(float gear) {
		return 0.7f + 0.17f * Math.max(0f, gear);
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
