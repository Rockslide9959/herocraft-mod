package com.projecthero.mod.flight.landing;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1: the superhero landing's sounds, synthesised for the mod ({@code scratchpad/synth_hero_landing_sounds.js} writes
 * {@code assets/projecthero/sounds/hero_landing/*.ogg}, mono): the heavy impact thud every landing plays, and the element
 * layers on top of it.
 */
public final class HeroLandingSounds {
	/** The heavy impact thud (every landing). */
	public static final SoundEvent IMPACT = register("hero_landing_impact");
	/** Green Lantern / Max Steel: a hard-light hum swelling off the impact. */
	public static final SoundEvent HARD_LIGHT = register("hero_landing_hardlight");
	/** Thor: a thunder crack. */
	public static final SoundEvent THUNDER = register("hero_landing_thunder");
	/** Nova / the Silver Surfer / the Flight power: a cosmic whoosh. */
	public static final SoundEvent COSMIC = register("hero_landing_cosmic");

	private HeroLandingSounds() {
	}

	/** Class-load hook. */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
