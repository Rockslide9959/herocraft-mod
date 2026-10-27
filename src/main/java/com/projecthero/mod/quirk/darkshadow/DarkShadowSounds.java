package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1 Dark Shadow revamp: the Quirk's own sounds (synthesised by {@code scratchpad/synth_quirk_kits3_sounds.js}, mono ogg in
 * {@code sounds/quirk/dark_shadow_*.ogg}, events {@code quirk_dark_shadow_*} in {@code sounds.json}). Registered from
 * {@link DarkShadowMod#initialize}.
 */
public final class DarkShadowSounds {
	/** Its low, throaty growl (2 takes). */
	public static final SoundEvent GROWL = register("quirk_dark_shadow_growl");
	/** A heavy shadow whoosh: a lunge, a claw sweep, the wings (2 takes). */
	public static final SoundEvent WHOOSH = register("quirk_dark_shadow_whoosh");
	/** The berserk roar (and Abyssal Black Body's merge). */
	public static final SoundEvent ROAR = register("quirk_dark_shadow_roar");

	private DarkShadowSounds() {
	}

	static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
