package com.projecthero.mod.shazam;

import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.shazam.network.ShazamFxPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam effects pass (tester: "needs a little work effect-wise"): the server side of his lightning's look. Each move sends
 * one {@link ShazamFxPayload} to every player within {@link #RANGE} blocks; their client draws branching bolt geometry, the red
 * Mercury blur, the Thunderclap rings, the Achilles aura and the landing's cracks ({@code client.shazam.ShazamBoltFx}). Sounds are
 * layered vanilla thunder / crackle at a volume the power keeps sane.
 */
public final class ShazamFx {
	public static final double RANGE = 128.0;

	private ShazamFx() {
	}

	public static void send(ServerLevel level, int kind, Vec3 a, Vec3 b, float size, int life, int entity) {
		ShazamFxPayload payload = new ShazamFxPayload(kind, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, size, life,
				entity, level.random.nextInt());
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(a) <= RANGE * RANGE && p.connection != null && ServerPlayNetworking.canSend(p, ShazamFxPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** A branching arc a to b with its crackle. */
	public static void arc(ServerLevel level, Vec3 a, Vec3 b, float size) {
		send(level, ShazamFxPayload.ARC, a, b, size, 7, -1);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 6, 0.2, 0.2, 0.2, 0.25);
	}

	/**
	 * A bolt from the sky onto {@code at}: the branching geometry, a flash on the ground, sparks, a crack and (for a big one) the
	 * rolling thunder. {@code size} 1 is a normal bolt, 2 the transform / Eternity Strike.
	 */
	public static void sky(ServerLevel level, Vec3 at, float size) {
		send(level, ShazamFxPayload.SKY, at.add(0, 0, 0), at, size, 9, -1);
		QuirkFx.ring(level, at).colours(0xFFFFFF, 0xFFD24A).size(2.2f * size + 1.2f).thickness(0.35f).life(9).ease(QuirkFx.Ease.OUT_EXPO).ground()
				.spawn();
		QuirkFx.decal(level, at).style(QuirkFx.DECAL_SCORCH).colour(0x1A1814).size(0.9f * size + 0.5f).life(120).yaw(level.random.nextFloat() * 360f)
				.spawn();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.4, at.z, (int) (18 * size), 0.5 * size, 0.4, 0.5 * size, 0.35);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.6, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.55f + 0.2f * size,
				0.9f + level.random.nextFloat() * 0.2f);
		if (size >= 1.5f) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.45f, 0.95f);
		} else {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.18f, 1.4f);
		}
	}

	/** Bolts bursting out along the ground from {@code at}. */
	public static void burst(ServerLevel level, Vec3 at, float size) {
		send(level, ShazamFxPayload.BURST, at, at, size, 8, -1);
		QuirkFx.ring(level, at).colours(0xFFFFFF, 0xFFC93A).size(2.5f + 2.5f * size).thickness(0.3f).life(10).ease(QuirkFx.Ease.OUT_EXPO).ground()
				.spawn();
	}

	/**
	 * The hero landing ({@code flight.landing.HeroLandingFx}): a lightning-crack ground slam -- glowing cracks race across the
	 * ground from his fist, a bolt answers from the sky on a hard landing, sparks and a thunder crack.
	 */
	public static void landing(ServerPlayer p, ServerLevel level, Vec3 feet, float s, float yaw) {
		double r = Math.toRadians(yaw);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		send(level, ShazamFxPayload.CRACK, feet.add(0, 0.05, 0), fwd, 2.5f + 3.0f * s, 34, -1);
		if (s >= 0.45f) {
			send(level, ShazamFxPayload.SKY, feet, feet, 1.0f + s, 8, -1);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.3f + 0.2f * s, 1.1f);
		}
		QuirkFx.ring(level, feet).colours(0xFFFFFF, 0xFFC93A).size(4.5f + 5.0f * s).thickness(0.3f + 0.2f * s).life(12).ease(QuirkFx.Ease.OUT_EXPO)
				.ground().spawn();
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_SCORCH).colour(0x16130F).size(1.2f + 1.2f * s).life(160).yaw(yaw).spawn();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 0.2, feet.z, 30 + (int) (20 * s), 1.4, 0.15, 1.4, 0.45);
		level.sendParticles(ParticleTypes.FLASH, feet.x, feet.y + 0.3, feet.z, 1, 0, 0, 0, 0);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7f, 1.0f);
		level.playSound(null, feet.x, feet.y, feet.z, ShazamSounds.CRACKLE, SoundSource.PLAYERS, 0.6f, 0.8f);
		QuirkCamera.vignette(p, 0xFFE27A, 0.2f + 0.2f * s, 10);
	}
}
