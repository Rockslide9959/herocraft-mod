package com.projecthero.mod.raven;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.3 Raven: her sounds, picked from the game's own (no new sound files), always played quietly. Each call site sets a low
 * volume (0.3 - 0.6) and a pitch that makes it darker.
 */
public final class RavenSounds {
	private RavenSounds() {
	}

	public static final SoundEvent LEARN = SoundEvents.ENCHANTMENT_TABLE_USE;
	public static final SoundEvent CHANT = SoundEvents.AMETHYST_BLOCK_RESONATE;
	public static final SoundEvent BOLT = SoundEvents.EVOKER_CAST_SPELL;
	public static final SoundEvent BOLT_HIT = SoundEvents.AMETHYST_BLOCK_BREAK;
	public static final SoundEvent GRAB = SoundEvents.ILLUSIONER_CAST_SPELL;
	public static final SoundEvent THROW = SoundEvents.ENDER_DRAGON_FLAP;
	public static final SoundEvent CLAWS = SoundEvents.EVOKER_FANGS_ATTACK;
	public static final SoundEvent SOUL = SoundEvents.PHANTOM_SWOOP;
	public static final SoundEvent SOUL_HIT = SoundEvents.SOUL_ESCAPE.value();
	public static final SoundEvent SHIELD_UP = SoundEvents.BEACON_ACTIVATE;
	public static final SoundEvent SHIELD_HIT = SoundEvents.AMETHYST_CLUSTER_HIT;
	public static final SoundEvent SHIELD_BREAK = SoundEvents.GLASS_BREAK;
	public static final SoundEvent WRATH = SoundEvents.WARDEN_ROAR;
	public static final SoundEvent WRATH_SLAM = SoundEvents.WARDEN_ATTACK_IMPACT;
	public static final SoundEvent DASH = SoundEvents.ENDERMAN_TELEPORT;
	public static final SoundEvent WAVE = SoundEvents.EVOKER_PREPARE_ATTACK;
	public static final SoundEvent SLAM = SoundEvents.GENERIC_EXPLODE.value();
	public static final SoundEvent DEBRIS = SoundEvents.ROOTED_DIRT_BREAK;
	public static final SoundEvent HEAL = SoundEvents.BEACON_POWER_SELECT;
	public static final SoundEvent CRACK = SoundEvents.GLASS_BREAK;
	public static final SoundEvent FLIGHT = SoundEvents.PHANTOM_FLAP;
}
