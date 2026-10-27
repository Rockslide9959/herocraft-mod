package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1 (Quirks revamp): Endeavor's own sounds ({@code quirk_hellflame_*} in {@code sounds.json}), synthesised by
 * {@code scratchpad/synth_quirk_explosion_hellflame_sounds.js}. Vanilla fire sounds stay as quiet layers where they add
 * texture.
 */
public final class HellflameSounds {
	/** A roaring flame burst (fists, the Flame Wave, the Pillar). */
	public static final SoundEvent ROAR = register("quirk_hellflame_roar");
	/** A jet burn: the ignition thump and the rush (Jet Burst, Jet Burn, take-off). */
	public static final SoundEvent JET = register("quirk_hellflame_jet");
	/** Heat Vent: a whoomp of flame and a long steam hiss. */
	public static final SoundEvent VENT = register("quirk_hellflame_vent");
	/** Hellblast: the beam's ignition and roar. */
	public static final SoundEvent BEAM = register("quirk_hellflame_beam");
	/** Prominence Burn: the comet's scream into the detonation. */
	public static final SoundEvent COMET = register("quirk_hellflame_comet");
	/** Hell Spider: the strings whipping out. */
	public static final SoundEvent WHIP = register("quirk_hellflame_whip");

	private HellflameSounds() {
	}

	static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
