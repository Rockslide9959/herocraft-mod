package com.projecthero.mod.saiyan;

import org.joml.Vector3f;

import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 tester note ("give Super Saiyan a landing like Green Lantern or Kryptonian"): the signature of the Saiyan's
 * superhero landing ({@code flight.landing.HeroLanding}, style {@code SAIYAN}). The shared system already lays the shockwave,
 * the crack and the thud; this adds the Saiyan part: a Ki burst flaring up round the fist, a dust-wall crater and debris.
 * In base form the Ki is white with a gold edge; as a Super Saiyan it is full gold, about 60% bigger, with a second golden
 * shockwave, a rising aura column, ki sparks, a deeper crater and a heavier shake. Server-side; everything is cosmetic.
 */
public final class SaiyanLanding {
	private SaiyanLanding() {
	}

	private static Vector3f rgb(int c) {
		return new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
	}

	public static void play(ServerPlayer p, ServerLevel level, Vec3 feet, float s, float yaw) {
		boolean ssj = Saiyan.ssj(p);
		float k = ssj ? 1.6f : 1.0f; // the Super Saiyan multiplier
		int core = ssj ? 0xFFF4B0 : 0xFFFFFF;
		int edge = ssj ? 0xFFC21A : 0xFFE27A;

		// the Ki burst: a flash at the feet and a fast, bright shockwave
		QuirkFx.blast(level, feet.add(0, 0.2, 0)).colours(core, edge).size((0.55f + 0.35f * s) * k).life(7)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkFx.ring(level, feet).colours(core, edge).size((4.5f + 4.0f * s) * k).thickness(0.3f + 0.15f * s).life(15)
				.ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		QuirkFx.ring(level, feet.add(0, 0.9, 0)).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 0, 1).yRot((float) Math.toRadians(-yaw)))
				.colours(core, edge).size((1.4f + 1.0f * s) * k).thickness(0.22f).life(9).spawn();

		// the crater: scorched bowl, crack web, flying chunks of the ground, a dust wall
		BlockState ground = p.getBlockStateOn();
		if (ground.isAir()) {
			ground = level.getBlockState(p.blockPosition().below());
		}
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_SCORCH).colour(0x3A3226).size((1.5f + 1.4f * s) * k).life(180).yaw(yaw + 40f).spawn();
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0x1E1A16).size((2.2f + 1.8f * s) * k).life(200).yaw(yaw + 17f).spawn();
		QuirkFx.shards(level, feet.add(0, 0.05, 0)).block(ground).size(0.8f + 0.5f * s).count((int) ((4 + 6 * s) * k)).life(22).spawn();
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		var rnd = level.getRandom();
		int n = (int) ((16 + 10 * s) * k);
		for (int i = 0; i < n; i++) {
			double a = (i + rnd.nextDouble() * 0.6) * Math.PI * 2 / n;
			double r = 0.8 + rnd.nextDouble() * (1.4 + s) * k;
			level.sendParticles(dust, feet.x + Math.cos(a) * r, feet.y + 0.15, feet.z + Math.sin(a) * r, 0, Math.cos(a), 0.35, Math.sin(a), 0.3);
		}
		level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + 0.2, feet.z, (int) (8 * k), 1.0 * k, 0.1, 1.0 * k, 0.06);

		// the Ki itself: gold motes flung out and rising
		DustParticleOptions ki = new DustParticleOptions(rgb(edge), 1.1f);
		for (int i = 0; i < (int) (18 * k); i++) {
			double a = i * Math.PI * 2 / (18 * k);
			level.sendParticles(ki, feet.x + Math.cos(a) * 0.6, feet.y + 0.2, feet.z + Math.sin(a) * 0.6, 0, Math.cos(a), 0.4, Math.sin(a), 0.35);
		}
		level.sendParticles(ParticleTypes.END_ROD, feet.x, feet.y + 0.3, feet.z, (int) ((4 + 4 * s) * k), 0.5, 0.2, 0.5, 0.12);

		if (ssj) {
			// Super Saiyan: a second, slower golden wave, the aura column flaring up, ki sparks
			QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0xFFE680, 0xFFB000).size(2.4f + 1.6f * s).thickness(1.2f).life(14).ground()
					.spawn();
			QuirkFx.ring(level, feet).colours(0xFFF8D0, 0xFFC21A).size(9.0f + 5.0f * s).thickness(0.2f).life(22).ease(QuirkFx.Ease.OUT_CUBIC)
					.ground().spawn();
			for (int y = 0; y < 5; y++) {
				level.sendParticles(ki, feet.x, feet.y + 0.3 + y * 0.45, feet.z, 6, 0.35, 0.15, 0.35, 0.05);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 1.0, feet.z, 14, 0.5, 0.6, 0.5, 0.35);
			level.playSound(null, feet.x, feet.y, feet.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.25f + 0.2f * s, 1.25f);
			QuirkCamera.vignette(p, 0xFFC21A, 0.25f + 0.2f * s, 12);
		}
		QuirkCamera.shakeAround(level, feet, (16 + 10 * s) * k, (0.28f + 0.45f * s) * (ssj ? 1.25f : 1f), 9 + (int) (8 * s));
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, (0.3f + 0.3f * s) * (ssj ? 1.2f : 1f),
				ssj ? 0.55f : 0.65f);
		// v0.17.4: a light landing, or the big crash from high up / as a Super Saiyan
		boolean heavy = ssj || s >= 0.6f;
		level.playSound(null, feet.x, feet.y, feet.z, heavy ? SaiyanSounds.LAND_HEAVY : SaiyanSounds.LAND, SoundSource.PLAYERS, heavy ? 0.8f : 0.6f, 1.0f);
	}
}
