package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.IronManThrusterParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * v0.16.7 (user) repulsor flight VFX -- the client side of {@link IronManThrusterParticles}. {@link #thrust} / {@link #air}
 * spawn one puff straight into the particle engine and tint it (colour, alpha, size, life), so one particle type serves
 * every suit's colour. The particle setting is honoured: "Decreased" keeps half, "Minimal" a quarter.
 */
public final class IronManThrusterParticleClient {
	private IronManThrusterParticleClient() {
	}

	public static void register() {
		ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
		reg.register(IronManThrusterParticles.THRUST, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, false));
		reg.register(IronManThrusterParticles.AIR, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, true));
	}

	private static boolean thinned(ClientLevel level) {
		ParticleStatus st = Minecraft.getInstance().options.particles().get();
		return st == ParticleStatus.MINIMAL ? level.random.nextInt(4) != 0
				: st == ParticleStatus.DECREASED && level.random.nextBoolean();
	}

	/** A full-bright glowing thrust puff of colour {@code rgb} that shrinks and fades over {@code life} ticks. */
	public static void thrust(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int rgb, float alpha,
			float size, int life) {
		spawn(level, IronManThrusterParticles.THRUST, x, y, z, vx, vy, vz, rgb, alpha, size, life);
	}

	/** A pale translucent air puff that grows as it fades (the Mk V's air jets). */
	public static void air(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int rgb, float alpha,
			float size, int life) {
		spawn(level, IronManThrusterParticles.AIR, x, y, z, vx, vy, vz, rgb, alpha, size, life);
	}

	private static void spawn(ClientLevel level, SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz,
			int rgb, float alpha, float size, int life) {
		if (thinned(level)) {
			return;
		}
		Particle p = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
		if (p instanceof Puff puff) {
			puff.tint(rgb, alpha, size, life);
		}
	}

	static final class Puff extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final boolean air;
		private float r0 = 1f;
		private float g0 = 1f;
		private float b0 = 1f;
		private float alpha0 = 1f;
		private float size0;

		Puff(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean air) {
			super(level, x, y, z);
			this.sprites = sprites;
			this.air = air;
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.friction = air ? 0.86f : 0.8f;
			this.gravity = air ? -0.004f : 0f;
			this.hasPhysics = false;
			this.lifetime = 8;
			this.size0 = 0.1f;
			this.quadSize = size0;
			if (air) {
				this.setSprite(sprites.get(this.random));
			} else {
				this.setSpriteFromAge(sprites);
			}
		}

		void tint(int rgb, float alpha, float size, int life) {
			float j = 0.94f + this.random.nextFloat() * 0.06f;
			this.r0 = ((rgb >> 16) & 0xFF) / 255f * j;
			this.g0 = ((rgb >> 8) & 0xFF) / 255f * j;
			this.b0 = (rgb & 0xFF) / 255f * j;
			this.setColor(r0, g0, b0);
			this.alpha0 = alpha;
			this.alpha = alpha;
			this.size0 = size * (0.85f + this.random.nextFloat() * 0.3f);
			this.quadSize = size0;
			this.lifetime = Math.max(2, life + this.random.nextInt(Math.max(1, life / 3 + 1)));
			if (air) {
				this.setSprite(sprites.get(this.random)); // one soft smoke puff, kept (no pixel-cross frames)
			} else {
				this.setSpriteFromAge(sprites);
			}
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			if (!air) {
				this.setSpriteFromAge(sprites);
			}
			float t = (float) this.age / this.lifetime;
			if (air) {
				this.alpha = alpha0 * (1f - t) * (1f - t);
			} else {
				// cools from the hot colour toward a deeper shade of itself as it trails off
				float k = 1f - 0.45f * t;
				this.setColor(r0 * k, g0 * k, b0 * Mth.lerp(t, 1f, 0.8f));
				this.alpha = alpha0 * (t < 0.4f ? 1f : 1f - (t - 0.4f) / 0.6f);
			}
		}

		@Override
		public float getQuadSize(float partial) {
			float t = Mth.clamp((this.age + partial) / this.lifetime, 0f, 1f);
			return air ? size0 * (1f + 1.4f * t) : size0 * (1f - 0.6f * t);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}

		@Override
		protected int getLightColor(float partial) {
			return air ? super.getLightColor(partial) : 0xF000F0;
		}
	}
}
