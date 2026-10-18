package com.projecthero.mod.client.quirk.hellflame;

import com.projecthero.mod.quirk.hellflame.HellflameParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * v0.16.7: the client side of {@link HellflameParticles}: full-bright glowing puffs that drift up, shrink, cool from
 * their colour toward a deep red and fade out.
 */
public final class HellflameParticleClient {
	private HellflameParticleClient() {
	}

	static void register() {
		ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
		reg.register(HellflameParticles.CORE, s -> new Provider(s, 1.0f, 0.97f, 0.84f, 0.9f, 8));
		reg.register(HellflameParticles.GLOW, s -> new Provider(s, 1.0f, 0.80f, 0.28f, 1.25f, 11));
		reg.register(HellflameParticles.BLAZE, s -> new Provider(s, 1.0f, 0.46f, 0.08f, 1.5f, 13));
		reg.register(HellflameParticles.BIG, s -> new Provider(s, 1.0f, 0.55f, 0.12f, 2.6f, 16));
	}

	record Provider(SpriteSet sprites, float r, float g, float b, float size, int life) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			return new Puff(level, x, y, z, vx, vy, vz, this);
		}
	}

	static final class Puff extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float r0, g0, b0;
		private final float size0;

		Puff(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, Provider p) {
			super(level, x, y, z);
			this.sprites = p.sprites();
			this.xd = vx;
			this.yd = vy + 0.01;
			this.zd = vz;
			this.friction = 0.86f;
			this.gravity = -0.015f;
			this.hasPhysics = false;
			float j = 0.92f + this.random.nextFloat() * 0.08f;
			this.r0 = p.r() * j;
			this.g0 = p.g() * j;
			this.b0 = p.b() * j;
			this.setColor(r0, g0, b0);
			this.lifetime = p.life() + this.random.nextInt(Math.max(1, p.life() / 2));
			this.size0 = 0.1f * p.size() * (0.8f + this.random.nextFloat() * 0.4f);
			this.quadSize = size0;
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.removed) {
				this.setSpriteFromAge(sprites);
				float t = (float) this.age / this.lifetime;
				// cools: toward a deep red-orange as it dies, fading out over the last third
				this.setColor(Mth.lerp(t, r0, 0.85f), Mth.lerp(t, g0, 0.18f), Mth.lerp(t, b0, 0.04f));
				this.alpha = t < 0.66f ? 1f : 1f - (t - 0.66f) / 0.34f;
			}
		}

		@Override
		public float getQuadSize(float partial) {
			float t = (this.age + partial) / this.lifetime;
			return size0 * (1.0f - 0.55f * t);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}

		@Override
		protected int getLightColor(float partial) {
			return 0xF000F0;
		}
	}
}
