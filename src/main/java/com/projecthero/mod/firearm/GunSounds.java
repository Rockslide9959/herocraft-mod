package com.projecthero.mod.firearm;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * v0.15.16: the Punisher firearms' own sounds -- synthesised from scratch for the mod ({@code scratchpad/synth_gun_sounds.js}
 * writes the OGGs in {@code assets/projecthero/sounds/gun/}), replacing the old re-pitched crossbow twang. Each gun has
 * a close report (three variants) and a distant one (heard past {@link GunFx#FAR_FROM} blocks, muffled, echoing); plus
 * the dry-fire click, magazine out / in, the pistol-and-rifle charging handle, the shotgun pump, the sniper bolt, a
 * shell going into the tube, and bullet impacts (dirt / stone, ricochet, flesh). (The brass-casing / shotgun-hull
 * landing clinks were removed in v0.15.18 -- user: "remove that tring sound effect when shooting".)
 */
public final class GunSounds {
	public static final SoundEvent PISTOL_FIRE = register("gun_pistol_fire");
	public static final SoundEvent RIFLE_FIRE = register("gun_rifle_fire");
	public static final SoundEvent SHOTGUN_FIRE = register("gun_shotgun_fire");
	public static final SoundEvent SNIPER_FIRE = register("gun_sniper_fire");
	public static final SoundEvent PISTOL_FAR = register("gun_pistol_far");
	public static final SoundEvent RIFLE_FAR = register("gun_rifle_far");
	public static final SoundEvent SHOTGUN_FAR = register("gun_shotgun_far");
	public static final SoundEvent SNIPER_FAR = register("gun_sniper_far");
	public static final SoundEvent DRY = register("gun_dry");
	public static final SoundEvent MAG_OUT = register("gun_mag_out");
	public static final SoundEvent MAG_IN = register("gun_mag_in");
	public static final SoundEvent RACK = register("gun_rack");
	public static final SoundEvent PUMP = register("gun_pump");
	public static final SoundEvent BOLT = register("gun_bolt");
	public static final SoundEvent SHELL_IN = register("gun_shell_in");
	public static final SoundEvent IMPACT = register("gun_impact");
	public static final SoundEvent RICOCHET = register("gun_ricochet");
	public static final SoundEvent FLESH = register("gun_flesh");

	private GunSounds() {
	}

	/** Forces the registrations above (call before the firearms are built). */
	public static void initialize() {
	}

	/** The distant report for a gun's close one. */
	public static SoundEvent farOf(SoundEvent fire) {
		if (fire == PISTOL_FIRE) {
			return PISTOL_FAR;
		}
		if (fire == SHOTGUN_FIRE) {
			return SHOTGUN_FAR;
		}
		if (fire == SNIPER_FIRE) {
			return SNIPER_FAR;
		}
		return fire == RIFLE_FIRE ? RIFLE_FAR : null;
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
