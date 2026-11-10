package com.projecthero.mod.undead;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.3: Undead's own sounds ({@code projecthero:undead.*}). The takes are synthesised for the mod
 * ({@code scratchpad/synth_undead_sounds.js} writes {@code assets/projecthero/sounds/undead/*.ogg}: filtered noise, stick-slip
 * creaks, bubbles, click clusters -- no samples); {@code scratchpad/undead_sounds_json.js} writes the events, a few vanilla
 * files at another pitch as extra variants, the attenuation distances and the subtitles. Server events play through
 * {@link UndeadSfx}, the client loops and accents through {@code client.undead.UndeadSounds}.
 */
public final class UndeadSoundEvents {
	// ---- regrowth (client: UndeadSounds.regrowth) -- loops are 3 s seamless beds, the rest one-shot accents
	/** Loop: bone pushing out of the stump -- a hard stick-slip grind with small cracks. */
	public static final SoundEvent REGROW_BONE_PUSH = register("undead.regrow.bone_push");
	/** Accent: a small sharp bone crack while the bone grows. */
	public static final SoundEvent REGROW_BONE_CRACK = register("undead.regrow.bone_crack");
	/** Loop: the muscle knitting -- a wet churn of bubbles, squelch formants and low sinew creaks. */
	public static final SoundEvent REGROW_MUSCLE = register("undead.regrow.muscle");
	/** Accent: one squelch of flesh closing while the muscle grows. */
	public static final SoundEvent REGROW_SQUELCH = register("undead.regrow.squelch");
	/** Loop: the skin closing over -- tacky micro-crackle, a faint tight creak. */
	public static final SoundEvent REGROW_SKIN = register("undead.regrow.skin");
	/** Accent: the skin pulling tight. */
	public static final SoundEvent REGROW_SKIN_STRETCH = register("undead.regrow.skin_stretch");
	/** The part whole again: a wet snap and settle. */
	public static final SoundEvent REGROW_FINISH = register("undead.regrow.finish");
	// ---- breaks and blood
	public static final SoundEvent PART_BREAK = register("undead.part.break");
	public static final SoundEvent BLOOD_BURST = register("undead.blood.burst");
	public static final SoundEvent BLOOD_SPRAY = register("undead.blood.spray");
	public static final SoundEvent BLOOD_DRIP = register("undead.blood.drip");
	public static final SoundEvent BLOOD_SPLAT = register("undead.blood.splat");
	// ---- flight
	/** Loop: the pressurised blood jets. */
	public static final SoundEvent BLOOD_JET_LOOP = register("undead.blood.jet_loop");
	public static final SoundEvent BLOOD_JET_START = register("undead.blood.jet_start");
	public static final SoundEvent BLOOD_JET_STOP = register("undead.blood.jet_stop");
	/** Loop: the boost burning his body away (sizzle, sear, wind). */
	public static final SoundEvent BOOST_BURN_LOOP = register("undead.boost.burn_loop");
	// ---- moves
	public static final SoundEvent HEAD_ROLL = register("undead.head.roll");
	public static final SoundEvent LAUNCH_WHOOSH = register("undead.launch.whoosh");
	public static final SoundEvent BULLET_PARTS = register("undead.bullet.parts");
	public static final SoundEvent BULLET_CRIMSON = register("undead.bullet.crimson");
	/** Loop: sinew stretching under load (the punch wind-up; the client raises its pitch as it charges). */
	public static final SoundEvent PUNCH_STRETCH = register("undead.punch.stretch");
	public static final SoundEvent PUNCH_FIRE = register("undead.punch.fire");

	/** Every Undead sound (the gametest checks each is registered, defined in sounds.json and subtitled). */
	public static final SoundEvent[] ALL = { REGROW_BONE_PUSH, REGROW_BONE_CRACK, REGROW_MUSCLE, REGROW_SQUELCH, REGROW_SKIN,
			REGROW_SKIN_STRETCH, REGROW_FINISH, PART_BREAK, BLOOD_BURST, BLOOD_SPRAY, BLOOD_DRIP, BLOOD_SPLAT, BLOOD_JET_LOOP,
			BLOOD_JET_START, BLOOD_JET_STOP, BOOST_BURN_LOOP, HEAD_ROLL, LAUNCH_WHOOSH, BULLET_PARTS, BULLET_CRIMSON, PUNCH_STRETCH,
			PUNCH_FIRE };

	private UndeadSoundEvents() {
	}

	/** Class-load hook (called from {@link UndeadMod#initialize}). */
	public static void initialize() {
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
