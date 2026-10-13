package com.projecthero.mod.spider;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.16.1: Spider-Man's own sounds, synthesised for the mod ({@code scratchpad/synth_spiderman_sounds.js} writes
 * {@code assets/projecthero/sounds/spiderman/*.ogg}): the thwip of a web shooter, the whoosh of a long zip line, the
 * wet splat of a web landing, and the Spider-Sense tingle (the web alarm and the Pulse).
 */
public final class SpiderSounds {
	public static final SoundEvent THWIP = register("spider_thwip");
	public static final SoundEvent ZIP = register("spider_zip");
	public static final SoundEvent SPLAT = register("spider_splat");
	public static final SoundEvent SENSE = register("spider_sense");

	private SpiderSounds() {
	}

	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
