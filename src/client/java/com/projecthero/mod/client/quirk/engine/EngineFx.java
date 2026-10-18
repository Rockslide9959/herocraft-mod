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
 * v0.16.7: Engine's exhaust, client side, for every viewer: blue-white flame jets out of the pipes on both calves
 * (soul-fire flames, pale blue dust and a wisp of campfire smoke), following the legs as they swing. A faint flicker
 * while he sprints, a steady jet in Recipro Burst, a roar in Gear Up / Turbo; sputtering smoke while stalled.
 */
public final class EngineFx {
	private EngineFx() {
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			return;
		}
		RandomSource r = level.random;
		for (Player p : level.players()) {
			if (!Engine.is(p) || p.isSpectator() || p.isInvisible()) {
				continue;
			}
			boolean stalled = Engine.stalled(p);
			int gear = Engine.gear(p);
			double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
			if (stalled) {
				if (p.tickCount % 4 == 0) {
					for (int side = -1; side <= 1; side += 2) {
						Vec3 at = pipe(p, side, 0f);
						level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.03, 0);
					}
				}
				continue;
			}
			int count;
			if (gear >= 3) {
				count = 3;
			} else if (gear == 2) {
				count = 2;
			} else if (gear == 1 || Engine.running(p, Engine.V_EXTEND)) {
				count = 1;
			} else if (p.isSprinting() && speed > 0.15) {
				count = p.tickCount % 3 == 0 ? 1 : 0;
			} else {
				count = 0;
			}
			if (count == 0) {
				continue;
			}
			for (int side = -1; side <= 1; side += 2) {
				Vec3 at = pipe(p, side, 0f);
				Vec3 out = pipeNormal(p, side).scale(0.04 + 0.02 * gear);
				for (int i = 0; i < count; i++) {
					double jx = (r.nextDouble() - 0.5) * 0.04;
					double jy = (r.nextDouble() - 0.5) * 0.04;
					double jz = (r.nextDouble() - 0.5) * 0.04;
					add(level, ParticleTypes.SOUL_FIRE_FLAME, at.add(jx, jy, jz), out);
					add(level, gear >= 2 ? EngineCombat.EXHAUST_WHITE : EngineCombat.EXHAUST, at.add(out.scale(2.0)).add(jx, jy, jz), Vec3.ZERO);
				}
				if (r.nextInt(gear >= 2 ? 6 : 12) == 0) {
					add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.add(out.scale(4.0)), new Vec3(0, 0.015, 0));
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
		return fwd.scale(-Mth.cos(th)).add(0, Mth.sin(th) - 0.15, 0).normalize();
	}
}
