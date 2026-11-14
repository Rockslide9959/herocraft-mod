package com.projecthero.mod.saiyan;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * The Saiyan's sound events (the core's and the move kit's; {@code kit/SaiyanKitSounds} names them for the moves).
 *
 * <p><b>v0.17.4: two sound sets, picked at load time.</b>
 * <ul>
 * <li><b>Public (committed)</b>: {@code assets/projecthero/sounds.json} points every event at the v0.17.3 sounds -- the
 * synthesised OGGs in {@code sounds/saiyan/} ({@code scratchpad/synth_saiyan.js}) or a vanilla stand-in (a {@code "type": "event"}
 * entry). No show audio. This is what a CI / CurseForge build ships.</li>
 * <li><b>Test builds only</b>: the user's Dragon Ball clips. The clips and the trimmed / looped cuts made from them
 * ({@code scratchpad/saiyan/dbz_cut.js}) live in {@code sounds/saiyan/dbz/}, which is GIT-IGNORED (copyrighted show audio,
 * public repo). The built-in resource pack {@code resourcepacks/saiyan_dbz/} (its {@code sounds.json} only names files, so it
 * is committed) replaces every Saiyan event with the clips. The client registers that pack only when the cut clips are in the
 * jar ({@code client.saiyan.SaiyanDbzSounds}), so a build made without them silently falls back to the public set.</li>
 * </ul>
 * Before a public release the clips get swapped for synthesised sound-alikes: {@code docs/SAIYAN_DBZ_SOUND_MAP.md} lists each
 * event, its clip and trim, and what the replacement must sound like.
 *
 * <p>The {@code *_LOOP} events are seamless loops (the client plays them as looping instances while the state lasts); everything
 * else is a one-shot. Everything is registered here, at mod init.
 */
public final class SaiyanSounds {
	// ---------------------------------------------------------------- the core
	/** V: the power-up burst (one-shot), then {@link #KI_CHARGE_LOOP} while it lasts. */
	public static final SoundEvent KI_CHARGE_START = register("saiyan_ki_charge_start");
	public static final SoundEvent KI_CHARGE_LOOP = register("saiyan_ki_charge_loop");
	/** The aura hums: gold (Super Saiyan; the user: "the super saiyan aura should be the charge sound effect"), red (Kaioken). Loops. */
	public static final SoundEvent AURA_LOOP_GOLD = register("saiyan_aura_loop_gold");
	public static final SoundEvent AURA_LOOP_RED = register("saiyan_aura_loop_red");
	/** Transforming (and Kaioken flaring up): energy blast + shockwave. */
	public static final SoundEvent TRANSFORM_BURST = register("saiyan_transform_burst");
	/** Super Saiyan or Kaioken ending. */
	public static final SoundEvent POWER_DOWN = register("saiyan_power_down");
	/** A Ki dash (X) and other quick bursts of speed. */
	public static final SoundEvent KI_DASH = register("saiyan_ki_dash");
	/** Super Saiyan's 3 minutes are up: the aura fizzles and he gasps. */
	public static final SoundEvent EXHAUSTED = register("saiyan_exhausted");
	/** v0.17.4: taking off into flight. */
	public static final SoundEvent TAKE_OFF = register("saiyan_take_off");
	/** v0.17.4: the hero landing (a light one, and a big one from high up or as a Super Saiyan). */
	public static final SoundEvent LAND = register("saiyan_land");
	public static final SoundEvent LAND_HEAVY = register("saiyan_land_heavy");
	/** v0.17.4: Ultra Instinct sidesteps an attack. */
	public static final SoundEvent UI_DODGE = register("saiyan_ui_dodge");

	// ---------------------------------------------------------------- the move kit (v0.17.4: its own events)
	public static final SoundEvent PUNCH = register("saiyan_punch");
	public static final SoundEvent PUNCH_HEAVY = register("saiyan_punch_heavy");
	public static final SoundEvent FINISHER = register("saiyan_finisher");
	public static final SoundEvent SWING = register("saiyan_swing");
	public static final SoundEvent FLURRY = register("saiyan_flurry");
	public static final SoundEvent VANISH = register("saiyan_vanish");
	public static final SoundEvent APPEAR = register("saiyan_appear");
	public static final SoundEvent KI_BLAST = register("saiyan_ki_blast");
	public static final SoundEvent KI_BLAST_HIT = register("saiyan_ki_blast_hit");
	public static final SoundEvent RAIN_SHOT = register("saiyan_rain_shot");
	public static final SoundEvent KAME_CHARGE = register("saiyan_kame_charge");
	public static final SoundEvent KAME_FIRE = register("saiyan_kame_fire");
	public static final SoundEvent KAME_LOOP = register("saiyan_kame_loop");
	public static final SoundEvent BOOM = register("saiyan_boom");
	public static final SoundEvent WAVE = register("saiyan_wave");
	public static final SoundEvent SPIRIT_BOMB_RISE = register("saiyan_spirit_bomb_rise");
	public static final SoundEvent SPIRIT_BOMB_CHARGE = register("saiyan_spirit_bomb_charge");
	public static final SoundEvent SPIRIT_BOMB_THROW = register("saiyan_spirit_bomb_throw");
	public static final SoundEvent SPIRIT_BOMB_EXPLODE = register("saiyan_spirit_bomb_explode");
	public static final SoundEvent SPIRIT_BOMB_BOOM = register("saiyan_spirit_bomb_boom");

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
