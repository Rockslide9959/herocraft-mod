package com.projecthero.mod.quirk.fx;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: the shared Quirk sounds, synthesised for the mod ({@code scratchpad/synth_quirk_framework_sounds.js}
 * writes {@code assets/projecthero/sounds/quirk/*.ogg}; the events are {@code quirk_*} in {@code sounds.json}). Kits add their
 * own {@code quirk_<kit>_*} events the same way (a new constant here, an entry in {@code sounds.json}, a subtitle in
 * {@code en_us.json}); layer a vanilla event underneath when a move wants a familiar texture.
 */
public final class QuirkSounds {
	/** A fast body / limb whoosh (2 takes). */
	public static final SoundEvent WHOOSH = register("quirk_whoosh");
	/** A meaty punch / kick impact (2 takes). */
	public static final SoundEvent IMPACT = register("quirk_impact");
	/** A heavy impact with a sub-bass thump (finishers, blasts). */
	public static final SoundEvent IMPACT_HEAVY = register("quirk_impact_heavy");
	/** A rising one-shot charge-up (about 1.1 s: the Plus Ultra hold, a charged move's wind-up). */
	public static final SoundEvent CHARGE_UP = register("quirk_charge_up");
	/** A steady charged hum, exactly 2 s and seamless, for a long hold (play it as a looping client sound instance). */
	public static final SoundEvent CHARGE_LOOP = register("quirk_charge_loop");
	/** The Plus Ultra ultimate sting (a rising swell into a hit). */
	public static final SoundEvent ULTIMATE_STING = register("quirk_ultimate_sting");
	/** The meter entering its red zone (a soft double beep). */
	public static final SoundEvent METER_WARNING = register("quirk_meter_warning");
	/** A combo route landing (a bright two-note chime). */
	public static final SoundEvent COMBO = register("quirk_combo");
	/** The Plus Ultra gauge reaching 100 (a shimmer). */
	public static final SoundEvent ULTRA_READY = register("quirk_ultra_ready");

	private QuirkSounds() {
	}

	public static void initialize() {
	}

	/** Plays at {@code at} for everyone near. */
	public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
