package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * v0.16.7: Endeavor's own flame particles -- full-bright glowing puffs (vanilla dust is lit by the world, so at night it
 * looks brown): the white-hot core, the yellow glow, the orange blaze and a big orange billow. The client factory is
 * {@code client.quirk.hellflame.HellflameParticleClient}; the sprites are vanilla's generic puffs
 * ({@code particles/hellflame_*.json}).
 */
public final class HellflameParticles {
	public static final SimpleParticleType CORE = FabricParticleTypes.simple(true);
	public static final SimpleParticleType GLOW = FabricParticleTypes.simple(true);
	public static final SimpleParticleType BLAZE = FabricParticleTypes.simple(true);
	public static final SimpleParticleType BIG = FabricParticleTypes.simple(true);

	private HellflameParticles() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("hellflame_core"), CORE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("hellflame_glow"), GLOW);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("hellflame_blaze"), BLAZE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("hellflame_big"), BIG);
	}
}
