package com.projecthero.mod.quirk.zerogravity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirks revamp: Uravity's own sounds, synthesised for the mod ({@code scratchpad/synth_quirk_erasure_zerogravity_sounds.js}
 * writes {@code assets/projecthero/sounds/quirk/zerogravity_*.ogg}; the events are {@code quirk_zerogravity_*} in
 * {@code sounds.json}). Registered from the kit's own init so the framework's {@code QuirkSounds} stays shared.
 */
public final class ZeroGravitySounds {
	/** The five-finger touch: a soft "pip" (2 takes). */
	public static final SoundEvent TOUCH = register("quirk_zerogravity_touch");
	/** Something floats: a soft rising chime. */
	public static final SoundEvent FLOAT = register("quirk_zerogravity_float");
	/** "Release!": the fingertips clap and a heavy thud follows. */
	public static final SoundEvent RELEASE = register("quirk_zerogravity_release");
	/** The Meteor Shower / Gravity Bomb rubble tearing up and coming down: a deep rumble. */
	public static final SoundEvent METEOR = register("quirk_zerogravity_meteor");
	/** The Weightless Push / the leap: an airy push. */
	public static final SoundEvent PUSH = register("quirk_zerogravity_push");

	private ZeroGravitySounds() {
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
