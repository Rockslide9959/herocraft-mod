package com.projecthero.mod.starfire;

import com.projecthero.mod.flight.landing.HeroLandingStyle;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 tester round ("add a landing like Green Lantern and Kryptonian"): Starfire's signature on the shared superhero landing
 * ({@code flight.landing.HeroLanding}, style {@link HeroLandingStyle#STARFIRE}). The shared part already plays the shockwave in
 * her greens, the dust, the crack and the thud; this adds the starbolt: the fist she punches into the ground flares green-white,
 * a green scorch blooms under it, a fast starbolt ring and a slower one race out, and green sparks spray off the fist.
 */
public final class StarfireLanding {
	private StarfireLanding() {
	}

	/** Called from {@code HeroLandingFx.play} for her style. */
	public static void play(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		float yaw = p.getYRot();
		float r = yaw * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
		Vec3 right = new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
		double side = p.getMainArm() == HumanoidArm.RIGHT ? 0.33 : -0.33;
		Vec3 fist = feet.add(fwd.scale(0.55)).add(right.scale(side)).add(0, 0.05, 0);
		// the starbolt in the fist bursts
		QuirkFx.blast(level, fist.add(0, 0.2, 0)).colours(0xF0FFE8, Starfire.GREEN).size(0.5f + 0.35f * s).life(7)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkFx.decal(level, fist).style(QuirkFx.DECAL_SCORCH).colour(0x0E2A10).size(1.3f + 1.0f * s).life(140 + (int) (60 * s)).yaw(yaw + 25f)
				.spawn();
		// two green rings: a fast bright one and a slower, wider one
		QuirkFx.ring(level, feet).colours(0xEAFFD8, 0x3CFF3A).size(4.5f + 4.0f * s).thickness(0.3f + 0.2f * s).life(14)
				.ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		QuirkFx.ring(level, feet).colours(0xB8FFB0, Starfire.GREEN_DARK).size(6.5f + 4.5f * s).thickness(0.2f).life(22)
				.ease(QuirkFx.Ease.OUT_CUBIC).ground().spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0x7CFF70, 0x18A028).size(1.6f + 0.8f * s).thickness(1.0f + 0.5f * s)
				.life(12).ground().spawn();
		// starbolt streaks: short green rays out along the ground from the fist
		var rnd = level.getRandom();
		int rays = 7 + (int) (4 * s);
		for (int i = 0; i < rays; i++) {
			double a = (i + rnd.nextDouble() * 0.5) * Math.PI * 2 / rays;
			double len = 1.4 + 1.6 * s + rnd.nextDouble() * 0.6;
			Vec3 tip = fist.add(Math.cos(a) * len, 0.04, Math.sin(a) * len);
			QuirkFx.ribbon(level, fist.add(0, 0.08, 0), tip).style(QuirkFx.RIBBON_LIGHT).colours(0xE8FFE0, 0x3CFF3A).size(0.08f)
					.life(5 + rnd.nextInt(4)).spawn();
		}
		var green = Starfire.dust(Starfire.GREEN, 1.0f);
		for (int i = 0; i < 18; i++) {
			double a = (i + rnd.nextDouble() * 0.6) * Math.PI * 2 / 18;
			double d = 1.0 + (1.4 + s) * rnd.nextDouble();
			level.sendParticles(green, feet.x + Math.cos(a) * d, feet.y + 0.12, feet.z + Math.sin(a) * d, 0, Math.cos(a), 0.2, Math.sin(a), 0.25);
		}
		level.sendParticles(ParticleTypes.END_ROD, fist.x, fist.y + 0.2, fist.z, 4 + (int) (4 * s), 0.2, 0.15, 0.2, 0.08);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.45f + 0.25f * s, 1.3f);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.25f + 0.25f * s,
				0.8f);
		QuirkCamera.vignette(p, 0x3CFF3A, 0.22f + 0.18f * s, 12);
	}
}
