package com.projecthero.mod.darkseid;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * The Darkseid Raid's sound events. Like {@code ProjectHeroSounds}, none ships a bespoke .ogg: each one is built
 * in {@code sounds.json} from vanilla sound FILES (paths checked against the 1.21.1 asset index), re-pitched and
 * pooled -- a deep-pitched Warden sonic charge for the Omega Beam wind-up, heavy mace smashes for the Ground Slam,
 * thunder for a Boom Tube opening, beacon and conduit hums for the Mother Boxes, and the End's own boss track for
 * the Omega Rage. Big moments play several of these at once in code (a pooled event only ever plays one file).
 *
 * <p>To replace any with real audio, drop the file under {@code assets/projecthero/sounds/darkseid/} and point
 * that event's entry at {@code projecthero:darkseid/<name>} -- nothing here changes.
 */
public final class DarkseidSounds {
	public static final SoundEvent ENTRANCE = register("darkseid_entrance");
	public static final SoundEvent STEP = register("darkseid_step");
	public static final SoundEvent PUNCH = register("darkseid_punch");
	public static final SoundEvent SLAM = register("darkseid_slam");
	public static final SoundEvent OMEGA_CHARGE = register("omega_beam_charge");
	public static final SoundEvent OMEGA_FIRE = register("omega_beam_fire");
	public static final SoundEvent OMEGA_IMPACT = register("omega_impact");
	public static final SoundEvent GRIP = register("darkseid_grip");
	public static final SoundEvent TELEPORT = register("darkseid_teleport");
	public static final SoundEvent BOOM_TUBE = register("boom_tube");
	public static final SoundEvent MOTHER_BOX_HUM = register("mother_box_hum");
	public static final SoundEvent MOTHER_BOX_ACTIVATE = register("mother_box_activate");
	public static final SoundEvent MOTHER_BOX_DISABLE = register("mother_box_disable");
	public static final SoundEvent MOTHER_BOX_OVERLOAD = register("mother_box_overload");
	public static final SoundEvent PHASE = register("darkseid_phase");
	public static final SoundEvent ANNIHILATION = register("omega_annihilation");
	public static final SoundEvent HURT = register("darkseid_hurt");
	public static final SoundEvent DEATH = register("darkseid_death");
	public static final SoundEvent VICTORY = register("apokolips_victory");
	public static final SoundEvent DEFEAT = register("apokolips_defeat");
	public static final SoundEvent PARADEMON_SCREECH = register("parademon_screech");
	public static final SoundEvent PARADEMON_HURT = register("parademon_hurt");
	public static final SoundEvent PARADEMON_DEATH = register("parademon_death");
	public static final SoundEvent PARADEMON_WINGS = register("parademon_wings");
	/** The End's own boss track, streamed once at the Omega Rage and stopped when the raid ends. */
	public static final SoundEvent RAID_MUSIC = register("apokolips_music");

	private DarkseidSounds() {
	}

	public static void initialize() {
		// referencing the class registers every event
	}

	public static Holder<SoundEvent> holder(SoundEvent event) {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event);
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
