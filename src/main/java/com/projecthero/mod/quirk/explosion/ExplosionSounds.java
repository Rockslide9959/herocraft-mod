package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1 (Quirks revamp): Bakugo's own sounds ({@code quirk_explosion_*} in {@code sounds.json}), synthesised by
 * {@code scratchpad/synth_quirk_explosion_hellflame_sounds.js} -- no more {@code GENERIC_EXPLODE}. Kept in the kit's own
 * package (the framework's {@code QuirkSounds} pattern: a constant, a {@code sounds.json} entry, a subtitle).
 */
public final class ExplosionSounds {
	/** The sweat igniting on his palms: a fizz of sparks and a pop. */
	public static final SoundEvent CRACKLE = register("quirk_explosion_crackle");
	/** A sharp palm blast (AP Shot, the Auto-Cannon, small pops). */
	public static final SoundEvent BLAST_SMALL = register("quirk_explosion_blast_small");
	/** A full blast with a sub-bass thump. */
	public static final SoundEvent BLAST = register("quirk_explosion_blast");
	/** Howitzer Impact: the corkscrew whine into the deepest boom. */
	public static final SoundEvent HOWITZER = register("quirk_explosion_howitzer");
	/** The Stun Grenade's flashbang. */
	public static final SoundEvent STUN = register("quirk_explosion_stun");
	/** AP Shot: Pierce -- a compressed whine snapping into a tearing beam. */
	public static final SoundEvent PIERCE = register("quirk_explosion_pierce");

	private ExplosionSounds() {
	}

	/** Class-load hook (from {@code ExplosionMod.initialize}). */
	static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
