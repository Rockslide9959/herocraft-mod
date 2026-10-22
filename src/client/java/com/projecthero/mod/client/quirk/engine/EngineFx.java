package com.projecthero.mod.client.quirk.engine;

import com.projecthero.mod.quirk.engine.Engine;
import com.projecthero.mod.quirk.engine.EngineCombat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (revamp): Engine's exhaust, client side, for every viewer -- flame jets out of the pipes on both calves that
 * follow the legs as they swing, and stream out behind him as <b>trails</b> (each particle is given the opposite of his
 * motion, so at speed they draw lines behind the calves). The jet grows with the gear and the momentum: a flicker in
 * Gear 1, a steady jet in Gear 2, a roar in Gear 3; Recipro turns it into big blue flames; the Turbo rush is a white-blue
 * blast. Idling in gear: a soft blue flicker. Stalled: sputtering smoke. The engines' sound is {@link EngineSound}.
 */
public final class EngineFx {
	private EngineFx() {
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			return;
		}
		EngineSound.tick(mc);
		RandomSource r = level.random;
		for (Player p : level.players()) {
			if (!Engine.is(p) || p.isSpectator() || p.isInvisible()) {
				continue;
			}
			if (Engine.stalled(p)) {
				if (p.tickCount % 3 == 0) {
					for (int side = -1; side <= 1; side += 2) {
						Vec3 at = pipe(p, side, 0f);
						level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.03, 0);
					}
				}
				continue;
			}
			float drive = Engine.drive(p);
			if (drive <= 0f) {
				continue;
			}
			float mom = Engine.momentum(p);
			boolean recipro = Engine.recipro(p);
			boolean turbo = Engine.running(p, Engine.V_TURBO);
			Vec3 motion = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
			double speed = Math.hypot(motion.x, motion.z);
			// how many flame puffs per calf this tick (fractional: a chance of one more)
			float rate;
			if (speed < 0.06) {
				rate = 0.25f + 0.1f * drive; // idling in gear
			} else {
				rate = 0.5f + 0.55f * drive * (0.5f + 0.5f * mom);
			}
			int count = (int) rate + (r.nextFloat() < rate - (int) rate ? 1 : 0);
			if (count <= 0) {
				continue;
			}
			// trails: the puffs hang back against his motion
			Vec3 trail = motion.scale(-0.35);
			for (int side = -1; side <= 1; side += 2) {
				Vec3 at = pipe(p, side, 0f);
				Vec3 out = pipeNormal(p, side).scale(0.035 + 0.015 * drive);
				for (int i = 0; i < count; i++) {
					double jx = (r.nextDouble() - 0.5) * 0.05;
					double jy = (r.nextDouble() - 0.5) * 0.05;
					double jz = (r.nextDouble() - 0.5) * 0.05;
					// lay the puffs along this tick's path so a fast run draws a solid line, not dots
					Vec3 along = motion.scale(-r.nextDouble());
					Vec3 o = at.add(along).add(jx, jy, jz);
					Vec3 v = out.add(trail);
					add(level, ParticleTypes.SOUL_FIRE_FLAME, o, v);
					add(level, drive >= 2f || recipro ? EngineCombat.EXHAUST_WHITE : EngineCombat.EXHAUST, o.add(out.scale(1.5)), trail.scale(0.5));
					if (recipro || turbo) {
						add(level, ParticleTypes.SOUL_FIRE_FLAME, o.add(out.scale(2.5)), v.scale(1.4));
						add(level, EngineCombat.EXHAUST, o.add(out.scale(3.5)), trail.scale(0.7));
					}
				}
				if (r.nextInt(drive >= 3f ? 3 : drive >= 2f ? 6 : 12) == 0) {
					add(level, ParticleTypes.SMOKE, at.add(out.scale(4.0)), new Vec3(0, 0.02, 0).add(trail.scale(0.3)));
				}
				if (turbo && r.nextInt(2) == 0) {
					add(level, ParticleTypes.END_ROD, at, trail.scale(0.4));
				}
			}
		}
	}

	private static void add(ClientLevel level, ParticleOptions o, Vec3 at, Vec3 v) {
		level.addParticle(o, at.x, at.y, at.z, v.x, v.y, v.z);
	}

	/** The swing of one leg ({@code side} -1 = his right, +1 = his left) as vanilla animates it (positive = backward). */
	private static float swing(Player p, int side, float partial) {
		float pos = p.walkAnimation.position(partial);
		float spd = Math.min(1f, p.walkAnimation.speed(partial));
		return Mth.cos(pos * 0.6662f + (side < 0 ? 0f : (float) Math.PI)) * 1.4f * spd;
	}

	/** World position of the exhaust pipe on one calf: on the back of the leg, about two thirds down. */
	public static Vec3 pipe(Player p, int side, float partial) {
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		float th = swing(p, side, partial);
		double d = 0.47;
		double b = 0.17;
		// leg axis (hip -> foot) and the back face's normal, rotated by the swing
		double aF = -Mth.sin(th), aU = -Mth.cos(th);
		double nF = -Mth.cos(th), nU = Mth.sin(th);
		double f = aF * d + nF * b;
		double u = aU * d + nU * b;
		double scale = p.getScale();
		return p.position().add(right.scale(side * 0.119 * scale)).add(fwd.scale(f * scale)).add(0, (0.75 + u) * scale, 0);
	}

	/** The direction the pipe blows: out of the back of the calf. */
	private static Vec3 pipeNormal(Player p, int side) {
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		float th = swing(p, side, 0f);
		// mostly straight back out of the calf; only a little of the leg swing tips it up or down (no rising sparks)
		double up = Mth.clamp((Mth.sin(th) - 0.15) * 0.35, -0.3, 0.12);
		return fwd.scale(-Math.max(0.6, Math.abs(Mth.cos(th)))).add(0, up, 0).normalize();
	}
}
