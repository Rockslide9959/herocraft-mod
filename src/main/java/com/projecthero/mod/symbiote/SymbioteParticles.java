package com.projecthero.mod.symbiote;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.17.5 round 2: the Symbiote suit's own particles (client factories in {@code client.symbiote.SymbioteParticleClient}, sprites made by
 * {@code scratchpad/symbiote/gen_symbiote_particles_v0175.js}):
 * <ul>
 *   <li>{@link #SALIVA} -- a drop of clear, glistening saliva that beads, stretches and falls from the Lethal Protector's mouth (user:
 *   "remove the green from the mouth" -- it used to be the vanilla green slime particle);</li>
 *   <li>{@link #GOO} -- a droplet of living suit goo in the wearer's body colour ({@link Goo#rgb}): flung off as the suit bursts out
 *   of the chest and crawls over the body, and pulled back in as it recedes.</li>
 * </ul>
 * Both are only ever spawned client side (the suit-up and the mouth are drawn from synced state), so nothing is sent.
 */
public final class SymbioteParticles {
	public static final SimpleParticleType SALIVA = FabricParticleTypes.simple(false);
	public static final ParticleType<Goo> GOO = FabricParticleTypes.complex(false, Goo.CODEC, Goo.STREAM_CODEC);

	private static boolean registered;

	private SymbioteParticles() {
	}

	/** A goo droplet: its colour (0xRRGGBB) and size (blocks). */
	public record Goo(int rgb, float size) implements ParticleOptions {
		public static final MapCodec<Goo> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("rgb").forGetter(Goo::rgb),
				Codec.FLOAT.fieldOf("size").forGetter(Goo::size)).apply(i, Goo::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Goo> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, Goo::rgb, ByteBufCodecs.FLOAT, Goo::size, Goo::new);

		public Goo {
			rgb &= 0xFFFFFF;
			size = Math.max(0.02f, Math.min(0.4f, size));
		}

		@Override
		public ParticleType<?> getType() {
			return GOO;
		}
	}

	/** From {@code ProjectHeroMod.onInitialize}. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("symbiote_saliva"), SALIVA);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ProjectHeroMod.id("symbiote_goo"), GOO);
	}
}
