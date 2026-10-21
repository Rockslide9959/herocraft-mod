package com.projecthero.mod.ironman;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * v0.16.7 (user) repulsor flight VFX: the thruster puffs every Iron Man flier's jets are made of. Only ever spawned on the
 * client ({@code client.IronManFlightFxClient}, driven by the synced flight flag -- no packets), which tints each one to
 * the suit's thrust colour after it is made ({@code client.ironman.IronManThrusterParticleClient}):
 * <ul>
 *   <li>{@link #THRUST}: a full-bright glowing puff that shrinks and fades fast -- the white-hot core and the coloured
 *   exhaust plume of a repulsor;</li>
 *   <li>{@link #AIR}: a pale, translucent, world-lit puff that GROWS as it fades -- the Mk V Stealth's clear air jets (no
 *   fire, no glow).</li>
 * </ul>
 * Sprites: vanilla's generic puffs ({@code particles/ironman_thrust.json}, {@code ironman_air_jet.json}).
 */
public final class IronManThrusterParticles {
	public static final SimpleParticleType THRUST = FabricParticleTypes.simple(true);
	public static final SimpleParticleType AIR = FabricParticleTypes.simple(true);
	private static boolean registered;

	private IronManThrusterParticles() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("ironman_thrust"), THRUST);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("ironman_air_jet"), AIR);
	}
}
