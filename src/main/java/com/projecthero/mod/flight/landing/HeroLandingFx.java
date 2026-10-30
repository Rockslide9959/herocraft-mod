package com.projecthero.mod.flight.landing;

import org.joml.Vector3f;

import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the effects of a superhero landing ({@link HeroLanding}), spawned on the server so every nearby player sees and
 * hears them: the impact shockwave ring in the style's colours, a crack decal (cosmetic only -- no block is ever touched),
 * dust and debris of the block underneath, the impact thud plus the style's element layer, and a camera shake scaled by the
 * landing's strength. Each style then adds its own signature (the Green Lantern emblem and ring flare, Thor's lightning, the
 * Kryptonian's crater...).
 */
public final class HeroLandingFx {
	private HeroLandingFx() {
	}

	private static Vector3f rgb(int c) {
		return new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
	}

	/** The block the lander came down on (never air: falls back to dirt for the debris). */
	private static BlockState ground(ServerPlayer p) {
		BlockState st = p.getBlockStateOn();
		if (st.isAir()) {
			st = p.level().getBlockState(p.blockPosition().below());
		}
		return st.isAir() ? Blocks.DIRT.defaultBlockState() : st;
	}

	private static Vec3 forward(ServerPlayer p) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	private static Vec3 right(ServerPlayer p) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/** Where the planted fist (or hammer) meets the ground. */
	static Vec3 fist(ServerPlayer p, HeroLandingStyle style) {
		boolean rightHanded = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		double side = rightHanded ? 0.33 : -0.33;
		double ahead = style == HeroLandingStyle.THOR ? 0.95 : 0.55;
		return p.position().add(forward(p).scale(ahead)).add(right(p).scale(side)).add(0, 0.05, 0);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent e, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch);
	}

	/** A ring of particles on the ground, flung outward. */
	private static void burst(ServerLevel level, ParticleOptions particle, Vec3 c, int n, double r0, double r1, double speed) {
		var rnd = level.getRandom();
		for (int i = 0; i < n; i++) {
			double a = (i + rnd.nextDouble() * 0.6) * Math.PI * 2 / n;
			double r = r0 + (r1 - r0) * rnd.nextDouble();
			level.sendParticles(particle, c.x + Math.cos(a) * r, c.y + 0.12, c.z + Math.sin(a) * r, 0, Math.cos(a), 0.18, Math.sin(a), speed);
		}
	}

	public static void play(ServerPlayer p, HeroLandingStyle style, float s) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 feet = p.position();
		BlockState ground = ground(p);
		float yaw = p.getYRot();
		int a = style.colourA, b = style.colourB;

		// ---- the shared impact: shockwave, crack, dust, debris, thud, shake
		QuirkFx.ring(level, feet).colours(a, b).size(3.2f + 4.5f * s).thickness(0.45f + 0.45f * s).life(11 + (int) (4 * s))
				.ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(dustColour(p, ground), dustColour(p, ground)).size(2.0f + 2.2f * s)
				.thickness(0.9f + 0.4f * s).life(16).ground().spawn();
		if (style != HeroLandingStyle.GREEN_LANTERN && style.element != HeroLandingStyle.Element.WATER) {
			QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0x2A2622).size(1.3f + 1.5f * s).life(140 + (int) (80 * s)).yaw(yaw)
					.spawn();
		}
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		burst(level, dust, feet, 14 + (int) (6 * s), 0.9, 1.6 + 1.0 * s, 0.22 + 0.15 * s);
		level.sendParticles(ParticleTypes.POOF, feet.x, feet.y + 0.1, feet.z, 3 + (int) (3 * s), 1.0, 0.02, 1.0, 0.02);
		if (style == HeroLandingStyle.HEATBLAST) {
			// v0.17.2 (user: "change heatblasts landing sound effect, it sounds weird, make it slightly softer too"): his own
			// softer fire-and-rock touchdown instead of the shared thud (and the FIRE element's fire charge below)
			com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit.heroLanding(p, level, s);
		} else {
			sound(level, feet, HeroLandingSounds.IMPACT, 0.7f + 0.5f * s, 0.95f - 0.15f * s + p.getRandom().nextFloat() * 0.08f);
		}
		QuirkCamera.shakeAround(level, feet, 10 + 12 * s, 0.16f + 0.5f * s, 6 + (int) (8 * s));
		if (s > 0.55f) {
			QuirkCamera.hitStop(p, 2);
		}
		QuirkCamera.fovPunch(p, -0.035f - 0.04f * s, 8);

		// ---- the style's signature
		switch (style) {
			case GREEN_LANTERN -> greenLantern(p, level, feet, s, yaw);
			case KRYPTONIAN, ROCK -> crater(p, level, feet, s, yaw, style == HeroLandingStyle.KRYPTONIAN);
			case THOR -> thor(p, level, s);
			case NOVA -> nova(p, level, feet, s);
			case SILVER_SURFER -> surfer(p, level, feet, s);
			case MAX_STEEL -> maxSteel(p, level, feet, s);
			default -> element(p, level, feet, style, s, yaw);
		}
	}

	private static int dustColour(ServerPlayer p, BlockState ground) {
		int c;
		try {
			c = ground.getMapColor(p.level(), p.blockPosition().below()).col;
		} catch (RuntimeException e) {
			c = 0;
		}
		return c == 0 ? 0xB8A890 : blend(c, 0xC8C0B0, 0.75f);
	}

	private static int blend(int c1, int c2, float t) {
		int r = (int) Mth.lerp(t, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, c1 & 0xFF, c2 & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	// ---------------------------------------------------------------- Green Lantern

	/** The ring fist punched into the ground: the ring flares, a green hard-light shockwave, the emblem glowing in the crack. */
	private static void greenLantern(ServerPlayer p, ServerLevel level, Vec3 feet, float s, float yaw) {
		Vec3 fist = fist(p, HeroLandingStyle.GREEN_LANTERN);
		// the ring flares at the knuckles
		QuirkFx.blast(level, fist.add(0, 0.15, 0)).colours(0xFFFFFF, 0x2AFF5A).size(0.3f + 0.2f * s).life(5)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		// a second, slower hard-light wave and a soft green floor glow
		QuirkFx.ring(level, feet).colours(0xF0FFF0, 0x22FF55).size(5.0f + 5.0f * s).thickness(0.25f + 0.2f * s).life(18)
				.ease(QuirkFx.Ease.OUT_CUBIC).ground().spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0x6AFF8A, 0x10A030).size(1.4f + 0.8f * s).thickness(1.0f + 0.6f * s)
				.life(12).ground().spawn();
		// the emblem: a crack web with the Lantern symbol glowing in it, centred on the fist, facing the way he landed
		QuirkFx.decal(level, fist).style(QuirkFx.DECAL_EMBLEM).colours(0x3CFF6A, 0x1E1C1A).size(1.5f + 1.1f * s).life(130 + (int) (60 * s))
				.yaw(yaw).spawn();
		DustParticleOptions green = new DustParticleOptions(rgb(0x3CFF6A), 0.9f);
		burst(level, green, feet, 16, 1.0, 2.2 + 1.2 * s, 0.25);
		level.sendParticles(ParticleTypes.END_ROD, fist.x, fist.y + 0.2, fist.z, 4 + (int) (5 * s), 0.2, 0.15, 0.2, 0.08);
		sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.3f + 0.3f * s, 0.7f); // v0.17.1 (user): a plain boom, no ringing layer
		QuirkCamera.vignette(p, 0x2AFF5A, 0.25f + 0.2f * s, 12);
	}

	// ---------------------------------------------------------------- Kryptonian / rock

	/** A crater: a dark scorched bowl under the crack web, a big dust wall and a heavy shake. */
	private static void crater(ServerPlayer p, ServerLevel level, Vec3 feet, float s, float yaw, boolean kryptonian) {
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_SCORCH).colour(0x3A3430).size(1.6f + 1.6f * s).life(180).yaw(yaw + 40f).spawn();
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0x1E1A18).size(2.4f + 2.0f * s).life(200).yaw(yaw + 17f).spawn();
		QuirkFx.shards(level, feet.add(0, 0.05, 0)).block(ground(p)).size(0.9f + 0.6f * s).count(5 + (int) (6 * s)).life(22).spawn();
		QuirkCamera.shakeAround(level, feet, 18 + 10 * s, 0.3f + 0.55f * s, 10 + (int) (8 * s));
		sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.35f + 0.35f * s, 0.6f);
	}

	// ---------------------------------------------------------------- Thor

	/** Hammer-first: a (harmless, visual-only) bolt onto the hammer head and a lightning-crackle ring. */
	private static void thor(ServerPlayer p, ServerLevel level, float s) {
		Vec3 head = fist(p, HeroLandingStyle.THOR);
		var rnd = level.getRandom();
		if (s >= 0.3f) {
			Vec3 sky = head.add(rnd.nextDouble() * 1.2 - 0.6, 9.0, rnd.nextDouble() * 1.2 - 0.6);
			QuirkFx.cone(level, sky, head.subtract(sky)).style(QuirkFx.CONE_BEAM).colours(0xFFFFFF, 0x6AA8FF).size(0.22f).length((float) sky.distanceTo(head))
					.life(5).spawn();
		}
		// the crackle: jagged two-segment arcs from the hammer head out along the ground
		int arcs = 6 + (int) (4 * s);
		for (int i = 0; i < arcs; i++) {
			double a = (i + rnd.nextDouble() * 0.5) * Math.PI * 2 / arcs;
			double r = 1.6 + 1.4 * s + rnd.nextDouble() * 0.8;
			Vec3 mid = head.add(Math.cos(a + 0.35) * r * 0.5, 0.08, Math.sin(a + 0.35) * r * 0.5);
			Vec3 tip = head.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
			QuirkFx.ribbon(level, head.add(0, 0.1, 0), mid).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, 0x7AB0FF).size(0.09f).life(6 + rnd.nextInt(4))
					.spawn();
			QuirkFx.ribbon(level, mid, tip).style(QuirkFx.RIBBON_LIGHT).colours(0xE8F2FF, 0x4A8CFF).size(0.07f).life(5 + rnd.nextInt(4)).spawn();
		}
		QuirkFx.ring(level, head).colours(0xFFFFFF, 0x6AA8FF).size(2.5f + 3.0f * s).thickness(0.3f).life(8).ease(QuirkFx.Ease.OUT_EXPO).ground()
				.spawn();
		QuirkFx.blast(level, head.add(0, 0.2, 0)).colours(0xFFFFFF, 0x7AB0FF).size(0.45f + 0.3f * s).life(6)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		burst(level, ParticleTypes.ELECTRIC_SPARK, p.position(), 24, 0.8, 2.4 + 1.2 * s, 0.5);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y + 0.3, head.z, 12, 0.25, 0.2, 0.25, 0.4);
		sound(level, head, HeroLandingSounds.THUNDER, 0.8f + 0.3f * s, 0.95f + p.getRandom().nextFloat() * 0.1f);
	}

	// ---------------------------------------------------------------- Nova

	/** A cosmic flare: a gold ring racing out, a slower red one behind it, starlight. */
	private static void nova(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.ring(level, feet).colours(0xFFF2B0, 0xE8282E).size(5.5f + 4.5f * s).thickness(0.35f).life(17).ground().spawn();
		QuirkFx.ring(level, feet.add(0, 0.6, 0)).flags(QuirkFx.F_VERTICAL).vec(forward(p)).colours(0xFFE27A, 0xFF7A20).size(1.6f + 1.2f * s)
				.thickness(0.25f).life(10).spawn();
		QuirkFx.blast(level, feet.add(0, 0.15, 0)).colours(0xFFF6D0, 0xFFB020).size(0.55f + 0.35f * s).life(6)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		burst(level, ParticleTypes.END_ROD, feet, 8, 0.8, 1.6 + s, 0.1);
		burst(level, new DustParticleOptions(rgb(0xE8282E), 0.8f), feet, 10, 1.0, 2.2 + s, 0.22);
		sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.3f + 0.25f * s, 0.75f); // v0.17.1 (user): a plain boom, no ringing layer
	}

	// ---------------------------------------------------------------- Silver Surfer

	/** A silver cosmic ripple; the board skids him forward a little. */
	private static void surfer(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0x9FC4FF).size(4.0f + 3.5f * s).thickness(1.0f).life(20).ground()
				.spawn();
		QuirkFx.ring(level, feet).colours(0xFFFFFF, 0xC8D8FF).size(6.0f + 3.0f * s).thickness(0.2f).life(24).ease(QuirkFx.Ease.OUT_QUAD).ground()
				.spawn();
		burst(level, ParticleTypes.END_ROD, feet, 8, 0.8, 1.8, 0.1);
		sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.25f + 0.25f * s, 0.8f); // v0.17.1 (user): a plain boom, no ringing layer
		Vec3 f = forward(p);
		p.setDeltaMovement(f.x * (0.35 + 0.2 * s), 0.0, f.z * (0.35 + 0.2 * s));
		p.hurtMarked = true;
	}

	/** The skid's spray (each hold tick for the first few). */
	static void skid(ServerPlayer p, int age) {
		if (age < 1 || age > 8 || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 back = p.position().subtract(forward(p).scale(0.6));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground(p)), back.x, back.y + 0.1, back.z, 6, 0.3, 0.05, 0.3, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, back.x, back.y + 0.15, back.z, 1, 0.2, 0.02, 0.2, 0.02);
	}

	// ---------------------------------------------------------------- Max Steel

	/** The Turbo burst -- always Max Steel blue. */
	private static void maxSteel(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.blast(level, feet.add(0, 0.1, 0)).colours(0xE8FAFF, 0x1E90FF).size(0.6f + 0.4f * s).life(6)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkFx.ring(level, feet).colours(0xBFF4FF, 0x1060FF).size(5.0f + 3.0f * s).thickness(0.25f).life(16).ground().spawn();
		burst(level, new DustParticleOptions(rgb(0x1E90FF), 0.8f), feet, 14, 0.9, 2.2 + s, 0.22);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 0.2, feet.z, 8, 0.6, 0.1, 0.6, 0.3);
		sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.3f + 0.25f * s, 0.75f); // v0.17.1 (user): a plain boom, no ringing layer
	}

	// ---------------------------------------------------------------- the element variants

	private static void element(ServerPlayer p, ServerLevel level, Vec3 feet, HeroLandingStyle style, float s, float yaw) {
		DustParticleOptions tint = new DustParticleOptions(rgb(style.colourB), 0.85f);
		switch (style.element) {
			case FIRE -> {
				QuirkFx.decal(level, feet).style(QuirkFx.DECAL_SCORCH).colour(0x1A1008).size(1.4f + 1.2f * s).life(200).yaw(yaw).spawn();
				QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0xFFC040, 0xFF3A00).size(3.0f + 2.5f * s).thickness(1.4f).life(14)
						.ground().spawn();
				burst(level, ParticleTypes.FLAME, feet, 20, 0.4, 1.6 + s, 0.12);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 0.2, feet.z, 8, 0.6, 0.1, 0.6, 0.02);
				if (style != HeroLandingStyle.HEATBLAST) {
					sound(level, feet, SoundEvents.FIRECHARGE_USE, 0.6f, 0.7f);
				}
			}
			case SHADOW -> {
				QuirkFx.decal(level, feet).style(QuirkFx.DECAL_POOL).colour(style == HeroLandingStyle.DARK_SHADOW ? 0x12061E : 0x2A1040)
						.size(1.6f + 1.0f * s).life(90).yaw(yaw).spawn();
				QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(style.colourB).size(3.0f + 2.5f * s).thickness(0.4f).life(16).ground()
						.spawn();
				burst(level, style == HeroLandingStyle.HAWK_MOTH ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK, feet, 16, 0.4, 1.4 + s, 0.08);
				sound(level, feet, SoundEvents.PHANTOM_FLAP, 0.8f, 0.6f);
			}
			case ICE -> {
				QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0xC8F4FF).size(1.6f + 1.4f * s).life(160).yaw(yaw + 30f).spawn();
				burst(level, ParticleTypes.SNOWFLAKE, feet, 22, 0.5, 2.0 + s, 0.15);
				sound(level, feet, SoundEvents.POWDER_SNOW_BREAK, 0.9f, 0.9f); // v0.17.1 (user): no glassy tinkle
			}
			case WATER -> {
				QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(0xCFEFFF).size(3.0f + 2.5f * s).thickness(0.4f).life(16).ground().spawn();
				burst(level, ParticleTypes.SPLASH, feet, 24, 0.4, 2.0 + s, 0.4);
				level.sendParticles(ParticleTypes.FALLING_WATER, feet.x, feet.y + 0.8, feet.z, 20, 0.8, 0.4, 0.8, 0.0);
				sound(level, feet, SoundEvents.GENERIC_SPLASH, 0.8f, 0.9f);
			}
			case TECH -> {
				burst(level, tint, feet, 20, 0.5, 2.0 + s, 0.3);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 0.3, feet.z, 16, 0.5, 0.2, 0.5, 0.45);
				sound(level, feet, SoundEvents.ANVIL_LAND, 0.25f, 0.7f);
			}
			case WIND -> {
				QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(0xF0F6FF).size(3.5f + 3.0f * s).thickness(0.35f).life(14).ground()
						.spawn();
				burst(level, ParticleTypes.CLOUD, feet, 16, 0.6, 1.6 + s, 0.15);
				sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.2f, 0.85f); // v0.17.1 (user): a plain boom, no ringing layer
			}
			default -> burst(level, tint, feet, 18, 0.5, 2.0 + s, 0.3);
		}
	}
}
