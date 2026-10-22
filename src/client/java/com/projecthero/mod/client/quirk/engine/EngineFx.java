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
 * v0.16.7 (revamp): Engine's exhaust, client side, for every viewer -- flame jets out of the calf engines' nozzles
 * (v0.16.7: the real drawn nozzles, {@link EngineCalves#nozzles}: three a calf), following the legs as they swing, and
 * streaming out behind him as <b>trails</b> (each particle is given the opposite of his motion, so at speed they draw lines
 * behind the calves). The jet grows with the gear and the momentum: a flicker in Gear 1, a steady jet in Gear 2, a roar
 * in Gear 3. <b>Recipro Turbo</b>: big blue flames out of every nozzle and blue speed streaks round the body (the aura
 * itself is {@link EngineCalvesLayer}). Idling in gear: a soft blue flicker. <b>Stalled</b>: steam puffing out of the
 * nozzles, with sputtering smoke. The engines' sound is {@link EngineSound}.
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
			EngineCalves.Nozzles n = EngineCalves.nozzles(p);
			if (Engine.stalled(p)) {
				// steam: soft white puffs drifting up out of the nozzles, a darker sputter now and then
				if (p.tickCount % 2 == 0) {
					int k = r.nextInt(6);
					Vec3 at = mouth(p, n, k);
					Vec3 d = blow(p, n, k);
					add(level, ParticleTypes.CLOUD, at, d.scale(0.03).add(0, 0.02, 0));
					if (r.nextInt(3) == 0) {
						add(level, ParticleTypes.WHITE_SMOKE, at, d.scale(0.02).add(0, 0.03, 0));
					}
				}
				if (r.nextInt(14) == 0) {
					int k = r.nextInt(6);
					add(level, ParticleTypes.LARGE_SMOKE, mouth(p, n, k), blow(p, n, k).scale(0.05).add(0, 0.02, 0));
				}
				continue;
			}
			float drive = Engine.drive(p);
			if (drive <= 0f) {
				continue;
			}
			float mom = Engine.momentum(p);
			boolean turbo = Engine.turbo(p);
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
			// at 20-80 b/s he covers 1-4 blocks a tick: more puffs, so the line laid along the path stays solid
			if (count > 0) {
				count = Math.min(10, count + (int) (speed / 0.6));
			}
			// trails: the puffs hang back against his motion -- gently, so a fast run leaves a long line on its path
			Vec3 trail = motion.scale(-0.35);
			if (trail.lengthSqr() > 0.25 * 0.25) {
				trail = trail.normalize().scale(0.25);
			}
			for (int side = 0; side < 2; side++) {
				for (int i = 0; i < count; i++) {
					int k = side * 3 + r.nextInt(3);
					Vec3 at = mouth(p, n, k);
					Vec3 out = blow(p, n, k).scale(speed < 0.06 ? 0.03 : 0.035 + 0.015 * drive);
					double jx = (r.nextDouble() - 0.5) * 0.04;
					double jy = (r.nextDouble() - 0.5) * 0.04;
					double jz = (r.nextDouble() - 0.5) * 0.04;
					// lay the puffs along this tick's path so a fast run draws a solid line, not dots
					Vec3 o = at.add(motion.scale(-r.nextDouble())).add(jx, jy, jz);
					Vec3 v = out.add(trail);
					add(level, ParticleTypes.SOUL_FIRE_FLAME, o, v);
					add(level, drive >= 2f || turbo ? EngineCombat.EXHAUST_WHITE : EngineCombat.EXHAUST, o.add(out.scale(1.5)), trail.scale(0.5));
					if (turbo) {
						add(level, ParticleTypes.SOUL_FIRE_FLAME, o.add(out.scale(2.5)), v.scale(1.4));
						add(level, EngineCombat.EXHAUST, o.add(out.scale(3.5)), trail.scale(0.7));
					}
				}
				if (r.nextInt(drive >= 3f ? 3 : drive >= 2f ? 6 : 12) == 0) {
					int k = side * 3 + r.nextInt(3);
					add(level, ParticleTypes.SMOKE, mouth(p, n, k).add(blow(p, n, k).scale(0.15)), new Vec3(0, 0.02, 0).add(trail.scale(0.3)));
				}
			}
			if (turbo) {
				// every nozzle blazes at once, and blue speed streaks peel off the body
				for (int k = 0; k < 6; k++) {
					if (r.nextInt(speed < 0.06 ? 4 : 2) == 0) {
						// a short lick of blue fire out of the mouth, curling up
						add(level, ParticleTypes.SOUL_FIRE_FLAME, mouth(p, n, k), blow(p, n, k).scale(0.035).add(0, 0.012, 0).add(trail));
					}
				}
				if (speed > 0.1 || r.nextInt(3) == 0) {
					double a = r.nextDouble() * Math.PI * 2;
					Vec3 at = p.position().add(Math.cos(a) * 0.45, 0.2 + r.nextDouble() * 1.5, Math.sin(a) * 0.45);
					add(level, ParticleTypes.END_ROD, at, trail.scale(0.6).add(0, 0.005, 0));
					add(level, EngineCombat.EXHAUST, at, trail.scale(0.4));
				}
			}
		}
	}

	private static void add(ClientLevel level, ParticleOptions o, Vec3 at, Vec3 v) {
		level.addParticle(o, at.x, at.y, at.z, v.x, v.y, v.z);
	}

	/** Nozzle {@code k}'s mouth (0..2 his right calf, 3..5 his left), from the drawn model or estimated. */
	private static Vec3 mouth(Player p, EngineCalves.Nozzles n, int k) {
		if (n != null && n.at()[k] != null) {
			return n.at()[k];
		}
		int side = k < 3 ? -1 : 1;
		return pipe(p, side, 0f).add(0, -0.11 * (k % 3 - 1), 0);
	}

	/** The way nozzle {@code k} blows. */
	private static Vec3 blow(Player p, EngineCalves.Nozzles n, int k) {
		if (n != null && n.dir()[k] != null && n.dir()[k].lengthSqr() > 0.5) {
			return n.dir()[k];
		}
		return pipeNormal(p, k < 3 ? -1 : 1);
	}

	/** The swing of one leg ({@code side} -1 = his right, +1 = his left) as vanilla animates it (positive = backward). */
	private static float swing(Player p, int side, float partial) {
		float pos = p.walkAnimation.position(partial);
		float spd = Math.min(1f, p.walkAnimation.speed(partial));
		return Mth.cos(pos * 0.6662f + (side < 0 ? 0f : (float) Math.PI)) * 1.4f * spd;
	}

	/** Estimated world position of one calf's engines (when the model wasn't drawn): the outer back of the calf. */
	public static Vec3 pipe(Player p, int side, float partial) {
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		// his right: facing +z (south) it is -x (west)
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		float th = swing(p, side, partial);
		double d = 0.47;
		double b = 0.08;
		// leg axis (hip -> foot) and the back face's normal, rotated by the swing
		double aF = -Mth.sin(th), aU = -Mth.cos(th);
		double nF = -Mth.cos(th), nU = Mth.sin(th);
		double f = aF * d + nF * b;
		double u = aU * d + nU * b;
		double scale = p.getScale();
		// side -1 (right) sits on the right vector, +1 (left) opposite; 0.2 = the leg's centre + its outer face
		return p.position().add(right.scale(-side * 0.2 * scale)).add(fwd.scale(f * scale)).add(0, (0.75 + u) * scale, 0);
	}

	/** The direction an estimated calf engine blows: out of the side and back of the calf. */
	private static Vec3 pipeNormal(Player p, int side) {
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		return fwd.scale(-0.55).add(right.scale(-side * 0.85)).normalize();
	}
}
