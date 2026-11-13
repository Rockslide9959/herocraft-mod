package com.projecthero.mod.doctorfate;

import com.projecthero.mod.doctorfate.network.FateSpellFxPayload;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate effects pass (tester: "needs more work on effects"): the server side of the spells' look. Each spell sends
 * one {@link FateSpellFxPayload} to every player within {@link #RANGE} blocks, whose client draws glowing golden geometry (ankhs,
 * ankh gates, rune circles, chains, cones, falling hieroglyph blades; {@code client.doctorfate.FateSpellFxClient}), and adds a
 * few server particles and the shared {@link QuirkFx} shock rings on top. Also the hit feedback and the hero landing.
 */
public final class DoctorFateFx {
	public static final double RANGE = 96.0;
	public static final int WHITE_GOLD = 0xFFF2C0;

	private DoctorFateFx() {
	}

	/** Sends one effect to every player near {@code at}. */
	public static void send(ServerLevel level, int kind, Vec3 at, Vec3 dir, float size, int life, int entity, int colour) {
		FateSpellFxPayload payload = new FateSpellFxPayload(kind, (float) at.x, (float) at.y, (float) at.z, (float) dir.x, (float) dir.y,
				(float) dir.z, size, life, entity, colour);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(at) <= RANGE * RANGE) {
				DoctorFate.send(p, payload);
			}
		}
	}

	public static void send(ServerLevel level, int kind, Vec3 at, Vec3 dir, float size, int life) {
		send(level, kind, at, dir, size, life, -1, DoctorFate.GOLD);
	}

	/** A spell landed on {@code target}: a small ankh flashes at the hit and a bright chime plays. */
	static void hit(ServerPlayer caster, Entity target) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 c = target.getBoundingBox().getCenter();
		Vec3 toCaster = caster.getEyePosition().subtract(c);
		send(level, FateSpellFxPayload.ANKH, c, toCaster, 0.55f + target.getBbHeight() * 0.15f, 8);
		level.sendParticles(DoctorFate.dust(WHITE_GOLD, 0.9f), c.x, c.y, c.z, 6, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3,
				target.getBbWidth() * 0.4, 0.0);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 5, 0.2, 0.3, 0.2, 0.2);
		DoctorFate.sound(level, c, SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 1.3f + level.random.nextFloat() * 0.2f);
	}

	/**
	 * The hero landing ({@code flight.landing.HeroLandingFx}): a golden ankh shockwave -- the ankh flares flat on the ground
	 * under him and a rune ring races out, gold dust and a deep chime.
	 */
	public static void landing(ServerPlayer p, ServerLevel level, Vec3 feet, float s, float yaw) {
		double r = Math.toRadians(yaw);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		send(level, FateSpellFxPayload.LANDING, feet.add(0, 0.06, 0), fwd, 2.2f + 2.6f * s, 30);
		QuirkFx.ring(level, feet).colours(0xFFF6D0, 0xFFB020).size(5.0f + 5.0f * s).thickness(0.3f + 0.2f * s).life(18)
				.ease(QuirkFx.Ease.OUT_CUBIC).ground().spawn();
		QuirkFx.ring(level, feet).style(QuirkFx.RING_SOFT).colours(0xFFE080, 0xC07010).size(1.6f + 0.8f * s).thickness(1.0f + 0.5f * s)
				.life(14).ground().spawn();
		var rnd = level.getRandom();
		int n = 18 + (int) (10 * s);
		for (int i = 0; i < n; i++) {
			double a = (i + rnd.nextDouble() * 0.6) * Math.PI * 2 / n;
			double d = 1.0 + rnd.nextDouble() * (1.5 + 1.5 * s);
			level.sendParticles(DoctorFate.dust(i % 3 == 0 ? WHITE_GOLD : DoctorFate.GOLD, 1.1f), feet.x + Math.cos(a) * d, feet.y + 0.15,
					feet.z + Math.sin(a) * d, 0, Math.cos(a), 0.12, Math.sin(a), 0.25);
		}
		level.sendParticles(ParticleTypes.END_ROD, feet.x, feet.y + 0.3, feet.z, 6 + (int) (6 * s), 0.6, 0.2, 0.6, 0.06);
		DoctorFate.sound(level, feet, SoundEvents.BEACON_POWER_SELECT, 0.7f, 0.55f);
		DoctorFate.sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
		DoctorFate.sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.25f + 0.25f * s, 0.75f);
		QuirkCamera.vignette(p, 0xFFC93A, 0.22f + 0.2f * s, 12);
	}
}
