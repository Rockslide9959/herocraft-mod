package com.projecthero.mod.client.quirk.fx;

import com.projecthero.mod.quirk.fx.QuirkParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Quirk framework: the client side of {@link QuirkParticles}. Each is one small {@link Look} -- colour ramp,
 * size, life, drag, gravity, full-bright or lit -- so a kit wanting a variant adds a line here.
 */
public final class QuirkParticleClient {
	private QuirkParticleClient() {
	}

	/** One particle's look: start / end colour (RGB), size (blocks), life (ticks), drag, gravity, full-bright, swell. */
	record Look(int from, int to, float size, int life, float friction, float gravity, boolean bright, float swell, boolean physics) {
	}

	static final Look EMBER = new Look(0xFFE7A0, 0xC2280A, 0.09f, 26, 0.92f, -0.012f, true, -0.5f, false);
	static final Look SPARK = new Look(0xFFFFFF, 0xFFB040, 0.07f, 10, 0.88f, 0.05f, true, -0.6f, false);
	static final Look SMOKE = new Look(0x6A6460, 0x302E2E, 0.35f, 40, 0.9f, -0.006f, false, 1.4f, false);
	static final Look SHADOW = new Look(0xFFFFFF, 0x8A70C0, 0.18f, 30, 0.93f, -0.002f, false, 0.4f, false);
	static final Look STEAM = new Look(0xF4F8FF, 0xC8D0DA, 0.3f, 28, 0.9f, -0.03f, false, 1.6f, false);
	static final Look PINK = new Look(0xFFD6EE, 0xFF6FB4, 0.08f, 22, 0.9f, -0.004f, true, -0.3f, false);
	static final Look DUST = new Look(0x9A8E80, 0x6E665C, 0.07f, 40, 0.98f, 0.06f, false, 0f, true);

	public static void register() {
		ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
		reg.register(QuirkParticles.EMBER, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, EMBER));
		reg.register(QuirkParticles.SPARK, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, SPARK));
		reg.register(QuirkParticles.SMOKE_PUFF, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, SMOKE));
		reg.register(QuirkParticles.SHADOW_MOTE, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, SHADOW));
		reg.register(QuirkParticles.STEAM, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, STEAM));
		reg.register(QuirkParticles.PINK_SPARKLE, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, PINK));
		reg.register(QuirkParticles.DUST_CHUNK, s -> (type, level, x, y, z, vx, vy, vz) -> new Puff(level, x, y, z, vx, vy, vz, s, DUST));
	}

	static final class Puff extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final Look look;
		private final float size0;

		Puff(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Look look) {
			super(level, x, y, z);
			this.sprites = sprites;
			this.look = look;
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.friction = look.friction();
			this.gravity = look.gravity();
			this.hasPhysics = look.physics();
			this.lifetime = look.life() + this.random.nextInt(Math.max(1, look.life() / 2));
			this.size0 = look.size() * (0.75f + this.random.nextFloat() * 0.5f);
			this.quadSize = size0;
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			colour(0f);
			this.setSpriteFromAge(sprites);
		}

		private void colour(float t) {
			int a = look.from(), b = look.to();
			this.setColor(Mth.lerp(t, (a >> 16 & 255) / 255f, (b >> 16 & 255) / 255f), Mth.lerp(t, (a >> 8 & 255) / 255f, (b >> 8 & 255) / 255f),
					Mth.lerp(t, (a & 255) / 255f, (b & 255) / 255f));
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			this.setSpriteFromAge(sprites);
			float t = (float) this.age / this.lifetime;
			colour(t);
			this.alpha = t < 0.1f ? t / 0.1f : t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;
			this.oRoll = this.roll;
			if (!this.onGround) {
				this.roll += 0.04f * (look.swell() >= 0 ? 1 : -1);
			}
		}

		@Override
		public float getQuadSize(float partial) {
			float t = (this.age + partial) / this.lifetime;
			return size0 * Math.max(0.05f, 1f + look.swell() * t);
		}

		@Override
		protected int getLightColor(float partial) {
			return look.bright() ? 0xF000F0 : super.getLightColor(partial);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}
}
