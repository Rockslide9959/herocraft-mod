package com.projecthero.mod.stand;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.17.2: every Stand sound slot. The user is recording the voice lines and SFX, so each event is its own named slot
 * ({@code projecthero:stand.star_platinum.<name>}); for now {@code sounds.json} points each at a vanilla sound (as an
 * {@code "type": "event"} reference). To use a real clip, drop {@code assets/projecthero/sounds/stand/star_platinum/<name>.ogg}
 * in and change that entry's sound to {@code "projecthero:stand/star_platinum/<name>"} -- the list is in
 * {@code docs/STAR_PLATINUM_DESIGN.md}. Only Stand users ever hear them ({@link Stands#sound}).
 */
public final class StandSounds {
	public static final SoundEvent SUMMON = register("summon");
	public static final SoundEvent DISMISS = register("dismiss");
	public static final SoundEvent MENACING = register("menacing");
	public static final SoundEvent PUNCH = register("punch");
	public static final SoundEvent ORA_RUSH = register("ora_rush");
	/** v0.17.2: the user's ORA ORA ORA clip -- one ORA per punch (five takes, picked at random) and the whole barrage. */
	public static final SoundEvent ORA = register("ora");
	public static final SoundEvent ORA_BARRAGE = register("ora_barrage");
	public static final SoundEvent ORA_FINISHER = register("ora_finisher");
	public static final SoundEvent STAR_FINGER = register("star_finger");
	public static final SoundEvent GRAB = register("grab");
	public static final SoundEvent THROW = register("throw");
	public static final SoundEvent FLICK = register("bearing_flick");
	public static final SoundEvent LEAP = register("leap");
	public static final SoundEvent DASH = register("dash");
	public static final SoundEvent BREAKER_CHARGE = register("breaker_charge");
	public static final SoundEvent STAR_BREAKER = register("star_breaker");
	public static final SoundEvent TIME_STOP = register("time_stop");
	public static final SoundEvent TIME_RESUME = register("time_resume");
	public static final SoundEvent CLOCK_TICK = register("clock_tick");
	public static final SoundEvent GUARD = register("guard");
	public static final SoundEvent CATCH = register("catch");
	public static final SoundEvent SIGHT = register("precision_sight");
	public static final SoundEvent INHALE = register("inhale");
	public static final SoundEvent UPPERCUT = register("uppercut");
	public static final SoundEvent YARE_YARE = register("yare_yare");
	public static final SoundEvent AWAKEN = register("awaken");

	private StandSounds() {
	}

	private static SoundEvent register(String name) {
		ResourceLocation id = ProjectHeroMod.id("stand.star_platinum." + name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Loads the class (registers every event). */
	public static void initialize() {
	}
}
