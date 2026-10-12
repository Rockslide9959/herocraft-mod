package com.projecthero.mod.wolverine;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.16.1: Wolverine's own sounds. The Sniff (N): quick nasal inhales, synthesised for the mod
 * ({@code scratchpad/synth_wolverine_sniff.js} writes {@code assets/projecthero/sounds/wolverine/sniff1..3.ogg}),
 * replacing the borrowed Sniffer mob noises.
 */
public final class WolverineSounds {
	public static final SoundEvent SNIFF = register("wolverine_sniff");

	private WolverineSounds() {
	}

	/** Forces the registrations above (called from {@link Wolverine#initialize}). */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
