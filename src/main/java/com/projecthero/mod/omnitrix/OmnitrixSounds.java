package com.projecthero.mod.omnitrix;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix's own sounds, synthesised for the mod ({@code scratchpad/synth_omnitrix_sounds.js} writes
 * {@code assets/projecthero/sounds/omnitrix/*.ogg}, mono). An alien kit adds its own the same way (a constant in its own class,
 * one {@code sounds.json} block, a subtitle line) -- Heatblast's and Four Arms' are here only because they shipped with the
 * framework.
 */
public final class OmnitrixSounds {
	public static final SoundEvent SLAP = register("omnitrix_slap");
	public static final SoundEvent TRANSFORM = register("omnitrix_transform");
	public static final SoundEvent REVERT = register("omnitrix_revert");
	public static final SoundEvent TIMEOUT = register("omnitrix_timeout");
	public static final SoundEvent WARNING = register("omnitrix_warning");
	public static final SoundEvent DIAL = register("omnitrix_dial");
	/** The core popping up (V) / sinking back. */
	public static final SoundEvent CORE_UP = register("omnitrix_core_up");
	public static final SoundEvent CORE_DOWN = register("omnitrix_core_down");
	public static final SoundEvent SELECT = register("omnitrix_select");
	public static final SoundEvent RECHARGED = register("omnitrix_recharged");
	public static final SoundEvent EXTRACT_PULSE = register("omnitrix_extract_pulse");
	public static final SoundEvent SELF_DESTRUCT = register("omnitrix_self_destruct");
	public static final SoundEvent EXTRACT_DONE = register("omnitrix_extract_done");
	public static final SoundEvent POD_OPEN = register("omnitrix_pod_open");
	public static final SoundEvent METEOR = register("omnitrix_meteor");

	public static final SoundEvent HEATBLAST_BLAST = register("heatblast_blast");
	public static final SoundEvent HEATBLAST_STREAM = register("heatblast_stream");
	public static final SoundEvent HEATBLAST_NOVA = register("heatblast_nova");
	public static final SoundEvent HEATBLAST_JET = register("heatblast_jet");
	public static final SoundEvent HEATBLAST_SHIELD = register("heatblast_shield");
	public static final SoundEvent HEATBLAST_IGNITE = register("heatblast_ignite");

	public static final SoundEvent FOUR_ARMS_PUNCH = register("four_arms_punch");
	public static final SoundEvent FOUR_ARMS_CLAP = register("four_arms_clap");
	public static final SoundEvent FOUR_ARMS_SLAM = register("four_arms_slam");
	public static final SoundEvent FOUR_ARMS_THROW = register("four_arms_throw");
	public static final SoundEvent FOUR_ARMS_ROAR = register("four_arms_roar");

	private OmnitrixSounds() {
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
