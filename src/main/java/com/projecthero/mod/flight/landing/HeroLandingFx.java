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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			for (double r = r0; r <= r1 + 1e-6; r += Math.max(0.3, (r1 - r0) / 2.0)) {
				level.sendParticles(particle, c.x + Math.cos(a) * r, c.y + 0.12, c.z + Math.sin(a) * r, 0, Math.cos(a), 0.18, Math.sin(a), speed);
			}
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
		QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(dustColour(p, ground)).size(2.2f + 3.2f * s).thickness(0.8f + 0.5f * s)
				.life(22).ground().spawn();
		if (style != HeroLandingStyle.GREEN_LANTERN && style.element != HeroLandingStyle.Element.WATER) {
			QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0x2A2622).size(1.3f + 1.5f * s).life(140 + (int) (80 * s)).yaw(yaw)
					.spawn();
		}
		QuirkFx.shards(level, feet.add(0, 0.1, 0)).block(ground).size(1.0f + 1.4f * s).count(6 + (int) (14 * s)).life(24).spawn();
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		burst(level, dust, feet, 20 + (int) (12 * s), 0.6, 1.6 + 1.4 * s, 0.25 + 0.2 * s);
		level.sendParticles(ParticleTypes.POOF, feet.x, feet.y + 0.1, feet.z, 6 + (int) (8 * s), 0.8, 0.05, 0.8, 0.04);
		sound(level, feet, HeroLandingSounds.IMPACT, 0.7f + 0.5f * s, 0.95f - 0.15f * s + p.getRandom().nextFloat() * 0.08f);
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
		return c == 0 ? 0xB8A890 : blend(c, 0xD8D0C0, 0.45f);
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
		QuirkFx.blast(level, fist.add(0, 0.15, 0)).colours(0xFFFFFF, 0x2AFF5A).size(0.9f + 0.7f * s).life(9)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		// a second, slower hard-light wave and a soft green floor glow
		QuirkFx.ring(level, feet).colours(0xF0FFF0, 0x22FF55).size(5.0f + 5.0f * s).thickness(0.25f + 0.2f * s).life(18)
				.ease(QuirkFx.Ease.OUT_CUBIC).ground().spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0x6AFF8A, 0x10A030).size(2.4f + 1.6f * s).thickness(2.4f + 1.6f * s)
				.life(16).ground().spawn();
		// the emblem: a crack web with the Lantern symbol glowing in it, centred on the fist, facing the way he landed
		QuirkFx.decal(level, fist).style(QuirkFx.DECAL_EMBLEM).colours(0x3CFF6A, 0x1E1C1A).size(1.5f + 1.1f * s).life(130 + (int) (60 * s))
				.yaw(yaw).spawn();
		DustParticleOptions green = new DustParticleOptions(rgb(0x3CFF6A), 1.3f);
		burst(level, green, feet, 24, 0.5, 2.2 + 1.5 * s, 0.3);
		level.sendParticles(ParticleTypes.END_ROD, fist.x, fist.y + 0.2, fist.z, 8 + (int) (10 * s), 0.25, 0.2, 0.25, 0.12);
		sound(level, feet, HeroLandingSounds.HARD_LIGHT, 0.9f, 1.0f + p.getRandom().nextFloat() * 0.06f);
		QuirkCamera.vignette(p, 0x2AFF5A, 0.25f + 0.2f * s, 12);
	}

	// ---------------------------------------------------------------- Kryptonian / rock

	/** A crater: a dark scorched bowl under the crack web, a big dust wall and a heavy shake. */
	private static void crater(ServerPlayer p, ServerLevel level, Vec3 feet, float s, float yaw, boolean kryptonian) {
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_SCORCH).colour(0x3A3430).size(1.6f + 1.6f * s).life(180).yaw(yaw + 40f).spawn();
		QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0x1E1A18).size(2.4f + 2.0f * s).life(200).yaw(yaw + 17f).spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(dustColour(p, ground(p))).size(4.0f + 4.5f * s).thickness(1.4f + 0.8f * s)
				.life(30).ground().spawn();
		QuirkFx.shards(level, feet.add(0, 0.1, 0)).block(ground(p)).size(1.8f + 1.6f * s).count(10 + (int) (18 * s)).life(30).spawn();
		QuirkCamera.shakeAround(level, feet, 18 + 10 * s, 0.3f + 0.55f * s, 10 + (int) (8 * s));
		sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.35f + 0.35f * s, 0.6f);
		if (kryptonian) {
			sound(level, feet, HeroLandingSounds.COSMIC, 0.4f, 0.6f);
		}
	}

	// ---------------------------------------------------------------- Thor

	/** Hammer-first: a (harmless, visual-only) bolt onto the hammer head and a lightning-crackle ring. */
	private static void thor(ServerPlayer p, ServerLevel level, float s) {
		Vec3 head = fist(p, HeroLandingStyle.THOR);
		if (s >= 0.3f) {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			if (bolt != null) {
				bolt.moveTo(head.x, head.y, head.z);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}
		QuirkFx.ring(level, head).colours(0xFFFFFF, 0x6AA8FF).size(2.5f + 3.0f * s).thickness(0.3f).life(8).ease(QuirkFx.Ease.OUT_EXPO).ground()
				.spawn();
		QuirkFx.blast(level, head.add(0, 0.2, 0)).colours(0xFFFFFF, 0x7AB0FF).size(0.8f + 0.6f * s).life(7)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		burst(level, ParticleTypes.ELECTRIC_SPARK, p.position(), 28, 0.6, 2.6 + 1.6 * s, 0.6);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y + 0.3, head.z, 24, 0.3, 0.3, 0.3, 0.5);
		sound(level, head, HeroLandingSounds.THUNDER, 0.8f + 0.3f * s, 0.95f + p.getRandom().nextFloat() * 0.1f);
	}

	// ---------------------------------------------------------------- Nova

	/** A cosmic flare: a gold ring racing out, a slower red one behind it, starlight. */
	private static void nova(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.ring(level, feet).colours(0xFFF2B0, 0xE8282E).size(5.5f + 4.5f * s).thickness(0.35f).life(17).ground().spawn();
		QuirkFx.ring(level, feet.add(0, 0.6, 0)).flags(QuirkFx.F_VERTICAL).vec(forward(p)).colours(0xFFE27A, 0xFF7A20).size(1.6f + 1.2f * s)
				.thickness(0.25f).life(10).spawn();
		QuirkFx.blast(level, feet.add(0, 0.3, 0)).colours(0xFFF6D0, 0xFFB020).size(1.2f + 0.8f * s).life(8)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		burst(level, ParticleTypes.END_ROD, feet, 18, 0.5, 1.5 + s, 0.2);
		burst(level, new DustParticleOptions(rgb(0xE8282E), 1.2f), feet, 18, 0.8, 2.2 + s, 0.3);
		sound(level, feet, HeroLandingSounds.COSMIC, 0.9f, 1.15f);
	}

	// ---------------------------------------------------------------- Silver Surfer

	/** A silver cosmic ripple; the board skids him forward a little. */
	private static void surfer(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0x9FC4FF).size(4.0f + 3.5f * s).thickness(1.0f).life(20).ground()
				.spawn();
		QuirkFx.ring(level, feet).colours(0xFFFFFF, 0xC8D8FF).size(6.0f + 3.0f * s).thickness(0.2f).life(24).ease(QuirkFx.Ease.OUT_QUAD).ground()
				.spawn();
		burst(level, ParticleTypes.END_ROD, feet, 16, 0.6, 1.8, 0.12);
		sound(level, feet, HeroLandingSounds.COSMIC, 1.0f, 0.85f);
		Vec3 f = forward(p);
		p.setDeltaMovement(f.x * (0.5 + 0.35 * s), Math.max(0.0, p.getDeltaMovement().y), f.z * (0.5 + 0.35 * s));
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
		if (age == 2) {
			sound(level, back, SoundEvents.GRINDSTONE_USE, 0.25f, 1.6f);
		}
	}

	// ---------------------------------------------------------------- Max Steel

	/** The Turbo burst -- always Max Steel blue. */
	private static void maxSteel(ServerPlayer p, ServerLevel level, Vec3 feet, float s) {
		QuirkFx.blast(level, feet.add(0, 0.2, 0)).colours(0xE8FAFF, 0x1E90FF).size(1.4f + 1.0f * s).life(9)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkFx.ring(level, feet).colours(0xBFF4FF, 0x1060FF).size(5.0f + 3.0f * s).thickness(0.25f).life(16).ground().spawn();
		burst(level, new DustParticleOptions(rgb(0x1E90FF), 1.3f), feet, 22, 0.5, 2.4 + s, 0.3);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 0.3, feet.z, 14, 0.5, 0.2, 0.5, 0.4);
		sound(level, feet, HeroLandingSounds.HARD_LIGHT, 0.7f, 1.45f);
	}

	// ---------------------------------------------------------------- the element variants

	private static void element(ServerPlayer p, ServerLevel level, Vec3 feet, HeroLandingStyle style, float s, float yaw) {
		DustParticleOptions tint = new DustParticleOptions(rgb(style.colourB), 1.2f);
		switch (style.element) {
			case FIRE -> {
				QuirkFx.decal(level, feet).style(QuirkFx.DECAL_SCORCH).colour(0x1A1008).size(1.4f + 1.2f * s).life(200).yaw(yaw).spawn();
				QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0xFFC040, 0xFF3A00).size(3.0f + 2.5f * s).thickness(1.4f).life(14)
						.ground().spawn();
				burst(level, ParticleTypes.FLAME, feet, 20, 0.4, 1.6 + s, 0.12);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 0.2, feet.z, 8, 0.6, 0.1, 0.6, 0.02);
				sound(level, feet, SoundEvents.FIRECHARGE_USE, 0.6f, 0.7f);
			}
			case SHADOW -> {
				QuirkFx.decal(level, feet).style(QuirkFx.DECAL_POOL).colour(style == HeroLandingStyle.DARK_SHADOW ? 0x12061E : 0x2A1040)
						.size(1.6f + 1.0f * s).life(90).yaw(yaw).spawn();
				QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(style.colourB).size(3.0f + 2.5f * s).thickness(1.0f).life(20).ground()
						.spawn();
				burst(level, style == HeroLandingStyle.HAWK_MOTH ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK, feet, 16, 0.4, 1.4 + s, 0.08);
				sound(level, feet, SoundEvents.PHANTOM_FLAP, 0.8f, 0.6f);
			}
			case ICE -> {
				QuirkFx.decal(level, feet).style(QuirkFx.DECAL_CRACK).colour(0xC8F4FF).size(1.6f + 1.4f * s).life(160).yaw(yaw + 30f).spawn();
				burst(level, ParticleTypes.SNOWFLAKE, feet, 22, 0.5, 2.0 + s, 0.15);
				sound(level, feet, SoundEvents.GLASS_BREAK, 0.45f, 1.4f);
				sound(level, feet, SoundEvents.PHANTOM_FLAP, 0.5f, 1.0f);
			}
			case WATER -> {
				QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(0xCFEFFF).size(3.0f + 2.5f * s).thickness(1.0f).life(18).ground().spawn();
				burst(level, ParticleTypes.SPLASH, feet, 24, 0.4, 2.0 + s, 0.4);
				level.sendParticles(ParticleTypes.FALLING_WATER, feet.x, feet.y + 0.8, feet.z, 20, 0.8, 0.4, 0.8, 0.0);
				sound(level, feet, SoundEvents.GENERIC_SPLASH, 0.8f, 0.9f);
			}
			case TECH -> {
				burst(level, tint, feet, 20, 0.5, 2.0 + s, 0.3);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 0.3, feet.z, 16, 0.5, 0.2, 0.5, 0.45);
				sound(level, feet, SoundEvents.ANVIL_LAND, 0.25f, 0.7f);
				sound(level, feet, HeroLandingSounds.HARD_LIGHT, 0.45f, 1.7f);
			}
			case WIND -> {
				QuirkFx.ring(level, feet).style(QuirkFx.RING_STEAM).colour(0xF0F6FF).size(3.5f + 3.0f * s).thickness(0.8f).life(16).ground()
						.spawn();
				burst(level, ParticleTypes.CLOUD, feet, 16, 0.6, 1.6 + s, 0.15);
				sound(level, feet, HeroLandingSounds.COSMIC, 0.55f, 0.75f);
			}
			default -> burst(level, tint, feet, 18, 0.5, 2.0 + s, 0.3);
		}
	}
}
