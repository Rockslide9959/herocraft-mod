package com.projecthero.mod.saiyan;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3: the Saiyan power's sounds, all synthesised for the mod by {@code scratchpad/synth_saiyan.js} (no show, film or
 * game audio). The OGGs live in {@code assets/projecthero/sounds/saiyan/}; events are listed in {@code sounds.json} with
 * subtitles. The {@code *_LOOP} events are seamless loops (play them as a looping client sound instance while the state
 * lasts); everything else is a one-shot. Registration only: the power's code plays them.
 */
public final class SaiyanSounds {
	/** Holding Q: the power-up burst (one-shot, ~1.6 s, from the user's clip), then {@link #KI_CHARGE_LOOP} while it is held. */
	public static final SoundEvent KI_CHARGE_START = register("saiyan_ki_charge_start");
	public static final SoundEvent KI_CHARGE_LOOP = register("saiyan_ki_charge_loop");
	/** Quiet aura hums for the forms: gold (SSJ 1-3), blue (Blue / Overdrive), red (God), silver (Instinct). Loops. */
	public static final SoundEvent AURA_LOOP_GOLD = register("saiyan_aura_loop_gold");
	public static final SoundEvent AURA_LOOP_BLUE = register("saiyan_aura_loop_blue");
	public static final SoundEvent AURA_LOOP_RED = register("saiyan_aura_loop_red");
	public static final SoundEvent AURA_LOOP_SILVER = register("saiyan_aura_loop_silver");
	/** Transforming into a form: energy blast + shockwave. */
	public static final SoundEvent TRANSFORM_BURST = register("saiyan_transform_burst");
	/** SSJ2's lightning crackle around the aura (play now and then). */
	public static final SoundEvent SSJ2_SPARK = register("saiyan_ssj2_spark");
	public static final SoundEvent POWER_DOWN = register("saiyan_power_down");
	public static final SoundEvent KI_DASH = register("saiyan_ki_dash");
	public static final SoundEvent INSTANT_STEP = register("saiyan_instant_step");
	public static final SoundEvent PUNCH_HIT_LIGHT = register("saiyan_punch_hit_light");
	public static final SoundEvent PUNCH_HIT_HEAVY = register("saiyan_punch_hit_heavy");
	/** The Ki Beam: charging (one-shot, ~1.6 s), firing (loop), hitting. */
	public static final SoundEvent BEAM_CHARGE = register("saiyan_beam_charge");
	public static final SoundEvent BEAM_FIRE_LOOP = register("saiyan_beam_fire_loop");
	public static final SoundEvent BEAM_IMPACT = register("saiyan_beam_impact");
	/** The Spirit Orb: the long gathering swell (~3.5 s) and the explosion. */
	public static final SoundEvent ORB_CHARGE = register("saiyan_orb_charge");
	public static final SoundEvent ORB_EXPLODE = register("saiyan_orb_explode");
	public static final SoundEvent DRAGON_RUSH = register("saiyan_dragon_rush");
	/** The Barrage: ~1.7 s of rapid hits in one sound. */
	public static final SoundEvent BARRAGE_RAPID = register("saiyan_barrage_rapid");
	/** Instinct's auto-dodge. */
	public static final SoundEvent INSTINCT_DODGE = register("saiyan_instinct_dodge");
	/** Out of Ki: the aura fizzles and he gasps. */
	public static final SoundEvent EXHAUSTED = register("saiyan_exhausted");

	private SaiyanSounds() {
	}

	/** Forces the registrations above (called from {@link ProjectHeroMod#onInitialize}). */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
