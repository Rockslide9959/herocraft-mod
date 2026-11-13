package com.projecthero.mod.raven;

import com.projecthero.mod.flight.landing.HeroLandingStyle;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.raven.network.RavenFxPayload;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 tester round ("add a landing like Green Lantern and Kryptonian"): Raven's signature on the shared superhero landing
 * ({@code flight.landing.HeroLanding}, style {@link HeroLandingStyle#RAVEN}; she kneels with her arms swept back as the shadow
 * folds round her). The shared part plays the shockwave in violet, the dust and the thud; this adds her soul energy: a pool of
 * shadow under her, her own dark Wave ring ({@link RavenFxPayload#WAVE}, drawn by the client's {@code RavenFx}) spreading out with a
 * violet edge, a second violet ring and dark-violet motes rising off the ground.
 */
public final class RavenLanding {
	private RavenLanding() {
	}

	/** Called from {@code HeroLandingFx.play} for her style. */
	public static void play(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_POOL).colour(0x1A0828).size(1.5f + 1.0f * s).life(90).yaw(p.getYRot()).spawn();
		RavenMoves.fx(p, new RavenFxPayload(RavenFxPayload.WAVE, p.getId(), feet.add(0, 0.06, 0), new Vec3(3.5 + 3.0 * s, 0, 0), 16));
		QuirkFx.ring(level, feet).colours(0xE0C8FF, 0x6A2BD0).size(4.5f + 3.5f * s).thickness(0.25f).life(18).ease(QuirkFx.Ease.OUT_CUBIC)
				.ground().spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0x8A4AE0, 0x2A0A50).size(1.6f + 0.8f * s).thickness(1.0f + 0.4f * s)
				.life(14).ground().spawn();
		var rnd = level.getRandom();
		DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.10f, 0.04f, 0.15f), 1.2f);
		DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.42f, 0.17f, 0.69f), 0.9f);
		for (int i = 0; i < 20; i++) {
			double a = (i + rnd.nextDouble() * 0.6) * Math.PI * 2 / 20;
			double d = 0.6 + (1.6 + s) * rnd.nextDouble();
			level.sendParticles(i % 3 == 0 ? violet : dark, feet.x + Math.cos(a) * d, feet.y + 0.1, feet.z + Math.sin(a) * d, 0, Math.cos(a) * 0.4,
					1.0, Math.sin(a) * 0.4, 0.06);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 0.3, feet.z, 10 + (int) (8 * s), 0.7, 0.15, 0.7, 0.03);
		level.playSound(null, feet.x, feet.y, feet.z, RavenSounds.WAVE, SoundSource.PLAYERS, 0.5f + 0.2f * s, 0.8f);
		level.playSound(null, feet.x, feet.y, feet.z, RavenSounds.FLIGHT, SoundSource.PLAYERS, 0.7f, 0.6f);
		QuirkCamera.vignette(p, 0x3A0A6A, 0.25f + 0.15f * s, 12);
	}
}
