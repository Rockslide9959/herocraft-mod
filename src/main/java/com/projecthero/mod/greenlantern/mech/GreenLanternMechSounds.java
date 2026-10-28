package com.projecthero.mod.greenlantern.mech;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.1: the Hard-Light Mech construct combos' own sounds ({@code gl_mech_*} in {@code sounds.json}), synthesised by
 * {@code scratchpad/synth_gl_mech_combo_sounds.js} (mono OGG Vorbis).
 */
public final class GreenLanternMechSounds {
	/** The Giga Drill spinning up: a whine over a grinding rattle (replayed with the pitch rising as it spins faster). */
	public static final SoundEvent DRILL_SPIN = register("gl_mech_drill_spin");
	/** Let go: the thrusters and the drill roar into the charge. */
	public static final SoundEvent DRILL_CHARGE = register("gl_mech_drill_charge");
	/** The drill biting rock: a gritty grind. */
	public static final SoundEvent DRILL_GRIND = register("gl_mech_drill_grind");
	/** GIGA DRILL BREAK: a rising scream into a huge boom. */
	public static final SoundEvent GIGA_BREAK = register("gl_mech_giga_break");
	/** The katana drawn: a long bright metallic ring. */
	public static final SoundEvent KATANA_DRAW = register("gl_mech_katana_draw");
	/** The katana sheathed: a slide and a click. */
	public static final SoundEvent KATANA_SHEATH = register("gl_mech_katana_sheath");
	/** A cut: an airy swish. */
	public static final SoundEvent KATANA_SWISH = register("gl_mech_katana_swish");
	/** A cut that connects. */
	public static final SoundEvent KATANA_HIT = register("gl_mech_katana_hit");
	/** The iaido draw-cut: a sharp ring snapping into a rushing crescent. */
	public static final SoundEvent KATANA_IAIDO = register("gl_mech_katana_iaido");

	private GreenLanternMechSounds() {
	}

	/** Class-load hook (from {@code GreenLanternMech#initialize}). */
	static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
