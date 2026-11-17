package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.symbiote.SymbioteParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * v0.17.5 round 2: the client side of {@link SymbioteParticles}.
 * <ul>
 *   <li><b>saliva</b> -- beads at the lip, stretches down on its strand, lets go and falls, and leaves a small clear splat where it
 *   lands. Pale, translucent, with a hard glint (replaces the green slime the Lethal Protector used to drool);</li>
 *   <li><b>goo</b> -- a wet droplet of suit goo in the wearer's body colour: flies, stretches while it is fast, falls, and splats
 *   flat on the ground before it soaks away.</li>
 * </ul>
 */
public final class SymbioteParticleClient {
	private SymbioteParticleClient() {
	}

	static void register() {
		ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
		reg.register(SymbioteParticles.SALIVA, s -> (type, level, x, y, z, vx, vy, vz) -> new Saliva(level, x, y, z, vx, vy, vz, s));
		reg.register(SymbioteParticles.GOO, s -> (type, level, x, y, z, vx, vy, vz) -> new Goo(level, x, y, z, vx, vy, vz, s, type));
	}

	/** Saliva: bead (frame 0), stretch (1), fall (2), splat (3). */
	static final class Saliva extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final int hang;
		private int splatAge = -1;

		Saliva(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(level, x, y, z);
			this.sprites = sprites;
			this.xd = vx;
			this.yd = 0;
			this.zd = vz;
			this.hang = 6 + this.random.nextInt(8);
			this.gravity = 0f;
			this.friction = 0.9f;
			this.hasPhysics = true;
			this.lifetime = 90;
			this.quadSize = 0.045f + this.random.nextFloat() * 0.02f;
			this.alpha = 0.92f;
			this.setSprite(sprites.get(0, 3));
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			if (splatAge >= 0) {
				splatAge++;
				this.xd = this.yd = this.zd = 0;
				this.alpha = Math.max(0f, 0.9f - splatAge / 14f);
				if (splatAge > 14) {
					remove();
				}
				return;
			}
			if (this.onGround) {
				splatAge = 0;
				this.setSprite(sprites.get(3, 3));
				this.quadSize *= 1.5f;
				return;
			}
			if (this.age < hang) {
				// beading on the lip, then stretching down its strand
				this.yd = -0.002;
				this.setSprite(sprites.get(this.age < hang / 2 ? 0 : 1, 3));
			} else {
				this.gravity = 0.55f;
				this.setSprite(sprites.get(2, 3));
			}
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** Goo: tinted to its colour; stretched while fast (frame 1), round when slow (0), a splat on the ground (2). */
	static final class Goo extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float size0;
		private int splatAge = -1;

		Goo(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, SymbioteParticles.Goo opts) {
			super(level, x, y, z);
			this.sprites = sprites;
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.gravity = 0.7f;
			this.friction = 0.96f;
			this.hasPhysics = true;
			this.lifetime = 40 + this.random.nextInt(20);
			this.size0 = opts.size() * (0.8f + this.random.nextFloat() * 0.4f);
			this.quadSize = size0;
			// a black suit would turn the droplet invisible: lift it a little, keeping its hue
			int c = opts.rgb();
			float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
			float lift = 0.16f;
			this.setColor(r + (0.82f - r) * lift + 0.04f, g + (0.86f - g) * lift + 0.04f, b + (1f - b) * lift + 0.06f);
			this.setSprite(sprites.get(0, 2));
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			if (splatAge >= 0) {
				splatAge++;
				this.xd = this.yd = this.zd = 0;
				this.alpha = Math.max(0f, 1f - splatAge / 24f);
				if (splatAge > 24) {
					remove();
				}
				return;
			}
			if (this.onGround) {
				splatAge = 0;
				this.setSprite(sprites.get(2, 2));
				this.quadSize = size0 * 1.6f;
				return;
			}
			double sp = this.xd * this.xd + this.yd * this.yd + this.zd * this.zd;
			this.setSprite(sprites.get(sp > 0.012 ? 1 : 0, 2));
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}
}
