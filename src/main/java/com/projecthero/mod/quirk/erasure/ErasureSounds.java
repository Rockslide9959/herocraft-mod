package com.projecthero.mod.quirk.erasure;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirks revamp: Eraser Head's own sounds, synthesised for the mod ({@code scratchpad/synth_quirk_erasure_zerogravity_sounds.js}
 * writes {@code assets/projecthero/sounds/quirk/erasure_*.ogg}; the events are {@code quirk_erasure_*} in {@code sounds.json}).
 * Registered here, from the kit's own init, so the shared {@code QuirkSounds} file stays the framework's.
 */
public final class ErasureSounds {
	/** The capture scarf whipped out: a cloth whoosh into a crack (2 takes). */
	public static final SoundEvent SCARF_WHIP = register("quirk_erasure_scarf_whip");
	/** The scarf snapping taut round something: a short cloth snap (2 takes). */
	public static final SoundEvent SCARF_SNAP = register("quirk_erasure_scarf_snap");
	/** The stare catching hold: a low electric hum with a red "glare" ring. */
	public static final SoundEvent GLARE = register("quirk_erasure_glare");
	/** The scarf winding tight round a body (a rustle and a cinch). */
	public static final SoundEvent BIND = register("quirk_erasure_bind");
	/** The goggles snapping down (a plastic click). */
	public static final SoundEvent GOGGLES = register("quirk_erasure_goggles");

	private ErasureSounds() {
	}

	static void initialize() {
	}

	public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
