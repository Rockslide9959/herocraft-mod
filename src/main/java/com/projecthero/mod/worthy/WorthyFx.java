package com.projecthero.mod.worthy;

import org.joml.Vector3f;

import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor x Super Soldier: the server-side look and sound of the lent-weapon moves -- Thor's vocabulary (visual-only bolts,
 * electric sparks, thunder, the screen shake), used more sparingly: he is a soldier holding a god's weapon, not the god. Mjolnir's
 * sparks are blue-white; Stormbreaker's add white-hot dust (never tinted -- the user's "no rainbow" rule for Stormbreaker).
 */
public final class WorthyFx {
	private WorthyFx() {
	}

	private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.1f);
	private static final DustParticleOptions PALE_BLUE = new DustParticleOptions(new Vector3f(0.75f, 0.88f, 1.0f), 1.0f);

	/** A visual-only lightning bolt (no fire, no damage of its own). */
	public static void bolt(ServerLevel level, ServerPlayer cause, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			bolt.setCause(cause);
			level.addFreshEntity(bolt);
		}
	}

	/** A crackle of lightning at {@code at}: sparks, plus white-hot dust for Stormbreaker. */
	public static void crackle(ServerLevel level, Vec3 at, int count, double spread, boolean axe) {
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, spread, spread, spread, 0.15);
		level.sendParticles(axe ? WHITE : PALE_BLUE, at.x, at.y, at.z, Math.max(1, count / 3), spread, spread, spread, 0.0);
	}

	/** Sparks along a line (a lightning arc, a chain). */
	public static void arc(ServerLevel level, Vec3 a, Vec3 b, boolean axe) {
		double len = a.distanceTo(b);
		int n = (int) Math.max(3, len * 3);
		Vec3 prev = a;
		for (int i = 1; i <= n; i++) {
			Vec3 at = a.lerp(b, i / (double) n);
			if (i < n) {
				at = at.add((level.random.nextDouble() - 0.5) * 0.35, (level.random.nextDouble() - 0.5) * 0.35, (level.random.nextDouble() - 0.5) * 0.35);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (axe && i % 2 == 0) {
				level.sendParticles(WHITE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			prev = at;
		}
	}

	/** A flat ring of sparks (a slam's shockwave front). */
	public static void ring(ServerLevel level, Vec3 c, double r, boolean axe) {
		int points = (int) Math.max(12, r * 8);
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2.0 / points;
			double x = c.x + Math.cos(a) * r;
			double z = c.z + Math.sin(a) * r;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, c.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.02);
			if (i % 3 == 0) {
				level.sendParticles(axe ? WHITE : PALE_BLUE, x, c.y + 0.2, z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Screen shake for everyone within {@code radius} (Thor's {@code TitanShakePayload}), weaker with distance. */
	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		double r2 = radius * radius;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2 && ServerPlayNetworking.canSend(p, TitanShakePayload.TYPE)) {
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * (float) (1.0 - Math.sqrt(d2 / r2) * 0.8), ticks));
			}
		}
	}
}
