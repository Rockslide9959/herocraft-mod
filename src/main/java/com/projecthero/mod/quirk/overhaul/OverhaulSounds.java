package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1 Overhaul revamp: the Quirk's own sounds (synthesised by {@code scratchpad/synth_quirk_kits3_sounds.js}, mono ogg in
 * {@code sounds/quirk/overhaul_*.ogg}, events {@code quirk_overhaul_*} in {@code sounds.json}). Vanilla blends stay layered
 * under them where a move wants a familiar texture. Registered from {@link OverhaulMod#initialize}.
 */
public final class OverhaulSounds {
	/** The crackling disassembly: a body / item / the ground coming apart (2 takes). */
	public static final SoundEvent DISASSEMBLE = register("quirk_overhaul_disassemble");
	/** A stone spike crunching up out of the ground (2 takes). */
	public static final SoundEvent SPIKE = register("quirk_overhaul_spike");
	/** The reassemble "rewind": scattered matter snapping back together. */
	public static final SoundEvent REASSEMBLE = register("quirk_overhaul_reassemble");

	private OverhaulSounds() {
	}

	static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
