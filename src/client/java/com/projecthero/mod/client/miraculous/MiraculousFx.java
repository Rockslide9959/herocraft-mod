package com.projecthero.mod.client.miraculous;

import org.joml.Vector3f;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: the transformation's light show, played by every client from the synced {@code formChangeAt}: three ribbons
 * of light in the hero's colour spiral up round the player with sparkles for {@link MiraculousConfig#TRANSFORM_TICKS},
 * tightening as they rise; at {@link MiraculousConfig#FLASH_TICK} a flash and a burst. Detransforming: a flash and a
 * burst in the hero's colour as the Kwami pops out.
 */
public final class MiraculousFx {
	private MiraculousFx() {
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		for (Player p : level.players()) {
			if (p.distanceToSqr(mc.player) > 64 * 64 || p.isInvisible()) {
				continue;
			}
			MiraculousState s = Miraculous.peek(p);
			Kwami k = Miraculous.kwami(p);
			if (s == null || k == null || s.formChangeAt <= 0L) {
				continue;
			}
			long age = now - s.formChangeAt;
			if (s.transformed && s.animId == MiraculousState.ANIM_TRANSFORM && age >= 0 && age <= MiraculousConfig.TRANSFORM_TICKS) {
				swirl(level, p, k, age);
			} else if (!s.transformed && s.animId == MiraculousState.ANIM_DETRANSFORM && age >= 0 && age <= 2) {
				burst(level, p, k, age == 0 ? 40 : 12, true);
			}
		}
	}

	private static Vector3f colour(Kwami k, float whiten) {
		float r = ((k.color >> 16) & 0xFF) / 255f;
		float g = ((k.color >> 8) & 0xFF) / 255f;
		float b = (k.color & 0xFF) / 255f;
		return new Vector3f(Mth.lerp(whiten, r, 1f), Mth.lerp(whiten, g, 1f), Mth.lerp(whiten, b, 1f));
	}

	private static void swirl(ClientLevel level, Player p, Kwami k, long age) {
		RandomSource rnd = level.random;
		float prog = age / (float) MiraculousConfig.TRANSFORM_TICKS;
		double h = p.getBbHeight();
		DustParticleOptions main = new DustParticleOptions(colour(k, 0f), 1.1f);
		DustParticleOptions light = new DustParticleOptions(colour(k, 0.6f), 0.8f);
		if (age < MiraculousConfig.FLASH_TICK) {
			for (int strand = 0; strand < 3; strand++) {
				for (int sub = 0; sub < 3; sub++) {
					double t = age + sub / 3.0;
					double angle = t * 0.62 + strand * (Math.PI * 2 / 3);
					double y = ((t * 0.075 + strand * 0.33) % 1.0) * (h + 0.3);
					double r = 0.95 - 0.45 * prog - 0.12 * (y / h);
					level.addParticle(sub == 0 ? light : main, p.getX() + Math.cos(angle) * r, p.getY() + y, p.getZ() + Math.sin(angle) * r,
							0, 0, 0);
				}
			}
			if (age % 2 == 0) {
				level.addParticle(ParticleTypes.END_ROD, p.getX() + (rnd.nextDouble() - 0.5) * 1.4, p.getY() + rnd.nextDouble() * h,
						p.getZ() + (rnd.nextDouble() - 0.5) * 1.4, 0, 0.02, 0);
			}
			if (age % 3 == 0) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (rnd.nextDouble() - 0.5), p.getY() + rnd.nextDouble() * h,
						p.getZ() + (rnd.nextDouble() - 0.5), 0, 0, 0);
			}
		}
		if (age == MiraculousConfig.FLASH_TICK) {
			burst(level, p, k, 60, false);
		}
	}

	private static void burst(ClientLevel level, Player p, Kwami k, int count, boolean small) {
		RandomSource rnd = level.random;
		double cy = p.getY() + p.getBbHeight() * 0.55;
		level.addParticle(ParticleTypes.FLASH, p.getX(), cy, p.getZ(), 0, 0, 0);
		DustParticleOptions main = new DustParticleOptions(colour(k, 0f), small ? 1.0f : 1.4f);
		DustParticleOptions light = new DustParticleOptions(colour(k, 0.7f), 1.0f);
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double up = (rnd.nextDouble() - 0.3) * p.getBbHeight();
			double r = 0.4 + rnd.nextDouble() * (small ? 0.5 : 0.9);
			level.addParticle(i % 3 == 0 ? light : main, p.getX() + Math.cos(a) * r, cy + up * 0.6, p.getZ() + Math.sin(a) * r,
					Math.cos(a) * 0.2, 0.05, Math.sin(a) * 0.2);
		}
		for (int i = 0; i < (small ? 4 : 14); i++) {
			level.addParticle(ParticleTypes.END_ROD, p.getX(), cy, p.getZ(), (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.3) * 0.2,
					(rnd.nextDouble() - 0.5) * 0.3);
		}
	}
}
