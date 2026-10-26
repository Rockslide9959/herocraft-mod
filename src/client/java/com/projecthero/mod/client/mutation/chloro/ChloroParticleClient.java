package com.projecthero.mod.client.mutation.chloro;

import com.projecthero.mod.hero.power.p22.ChloroParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

import net.minecraft.client.multiplayer.ClientLevel;


import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

import net.minecraft.util.Mth;

/**
 * v0.17.1 Chlorokinesis: the client side of {@link ChloroParticles}.
 * <ul>
 * <li>leaf -- drifts down swaying side to side and spinning, a random one of four leaves;</li>
 * <li>petal -- lighter, flutters longer;</li>
 * <li>spore -- a full-bright mote that hangs, rises a little and swells then fades (animated frames);</li>
 * <li>sap -- a full-bright green drop that holds still and shrinks away.</li>
 * </ul>
 */
public final class ChloroParticleClient {
	private ChloroParticleClient() {
	}

	static void register() {
		ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
		reg.register(ChloroParticles.LEAF, s -> (type, level, x, y, z, vx, vy, vz) -> new Drift(level, x, y, z, vx, vy, vz, s, false));
		reg.register(ChloroParticles.PETAL, s -> (type, level, x, y, z, vx, vy, vz) -> new Drift(level, x, y, z, vx, vy, vz, s, true));
		reg.register(ChloroParticles.SPORE, s -> (type, level, x, y, z, vx, vy, vz) -> new Mote(level, x, y, z, vx, vy, vz, s));
		reg.register(ChloroParticles.SAP, s -> (type, level, x, y, z, vx, vy, vz) -> new Sap(level, x, y, z, vx, vy, vz, s));
	}

	/** Leaves and petals: fall slowly, sway and spin. */
	static final class Drift extends TextureSheetParticle {
		private final float phase;
		private final float spin;
		private final float sway;

		Drift(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean petal) {
			super(level, x, y, z);
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.pickSprite(sprites);
			this.friction = 0.94f;
			this.gravity = petal ? 0.012f : 0.02f;
			this.hasPhysics = true;
			this.lifetime = (petal ? 60 : 45) + this.random.nextInt(30);
			this.quadSize = (petal ? 0.09f : 0.12f) * (0.8f + this.random.nextFloat() * 0.5f);
			this.phase = this.random.nextFloat() * Mth.TWO_PI;
			this.spin = (this.random.nextFloat() - 0.5f) * (petal ? 0.35f : 0.22f);
			this.sway = petal ? 0.012f : 0.008f;
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			this.oRoll = this.roll;
			if (!this.onGround) {
				this.roll += spin;
				this.xd += Mth.sin(this.age * 0.21f + phase) * sway;
				this.zd += Mth.cos(this.age * 0.17f + phase) * sway;
				this.yd = Math.max(this.yd, -0.07);
			}
			float t = (float) this.age / this.lifetime;
			this.alpha = t < 0.8f ? 1f : 1f - (t - 0.8f) / 0.2f;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** Spores: hang in the air, drift up, swell through their frames and fade. Full-bright. */
	static final class Mote extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float size0;

		Mote(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(level, x, y, z);
			this.sprites = sprites;
			this.xd = vx * 0.3 + (this.random.nextFloat() - 0.5f) * 0.01;
			this.yd = vy * 0.3 + 0.004 + this.random.nextFloat() * 0.004;
			this.zd = vz * 0.3 + (this.random.nextFloat() - 0.5f) * 0.01;
			this.friction = 0.96f;
			this.gravity = -0.0015f;
			this.hasPhysics = false;
			this.lifetime = 34 + this.random.nextInt(26);
			this.size0 = 0.07f + this.random.nextFloat() * 0.07f;
			this.quadSize = size0;
			float g = 0.85f + this.random.nextFloat() * 0.15f;
			this.setColor(0.9f * g, 1.0f * g, 0.55f * g);
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.removed) {
				this.setSpriteFromAge(sprites);
				float t = (float) this.age / this.lifetime;
				this.alpha = t < 0.15f ? t / 0.15f : t > 0.7f ? 1f - (t - 0.7f) / 0.3f : 1f;
			}
		}

		@Override
		public float getQuadSize(float partial) {
			float t = (this.age + partial) / this.lifetime;
			return size0 * (0.7f + 0.6f * t);
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

	/** Sap: a glowing drop that stays put and shrinks away. */
	static final class Sap extends TextureSheetParticle {
		private final float size0;

		Sap(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(level, x, y, z);
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.pickSprite(sprites);
			this.friction = 0.8f;
			this.gravity = 0.0f;
			this.hasPhysics = false;
			this.lifetime = 12 + this.random.nextInt(8);
			this.size0 = 0.06f + this.random.nextFloat() * 0.04f;
			this.quadSize = size0;
		}

		@Override
		public float getQuadSize(float partial) {
			float t = (this.age + partial) / this.lifetime;
			return size0 * (1.0f - 0.8f * t);
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
