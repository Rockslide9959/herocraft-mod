package com.projecthero.mod.shazam;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3 Shazam: the power's own sound events. They reuse vanilla thunder and crackle files ({@code sounds.json}), so they need
 * no OGGs: what they add is the subtitle ("SHAZAM!" for the word) and a volume the power keeps low.
 */
public final class ShazamSounds {
	/** The word's magic thunder (subtitle "SHAZAM!"). */
	public static final SoundEvent WORD = register("shazam_word");
	/** A lightning move's crackle (Lightning Hand, Zeus Storm, the dash trail). */
	public static final SoundEvent CRACKLE = register("shazam_crackle");

	private ShazamSounds() {
	}

	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
