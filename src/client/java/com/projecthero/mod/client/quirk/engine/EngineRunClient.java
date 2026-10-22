package com.projecthero.mod.client.quirk.engine;

import com.projecthero.mod.quirk.engine.Engine;
import com.projecthero.mod.quirk.engine.EngineConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (revamp): the local Engine player's run -- the sliding turns, and the HUD's speed readout.
 * <ul>
 *   <li><b>Grip</b>: in gear with momentum, the run can't swing round instantly. At the end of each client tick the
 *       carried velocity may turn only {@link EngineConfig#GRIP_DEGREES} a tick (less grip in higher gears and with more
 *       momentum; Recipro has the least), so a sharp turn drifts wide before it bites -- the slide. The server sees the
 *       swing in his position and adds the skid sparks, sound and momentum cost.</li>
 *   <li><b>Speed</b>: his horizontal speed in blocks a second, smoothed (the HUD).</li>
 * </ul>
 */
public final class EngineRunClient {
	private static Vec3 lastVel = Vec3.ZERO;
	private static float speedBps;

	private EngineRunClient() {
	}

	public static void reset() {
		lastVel = Vec3.ZERO;
		speedBps = 0f;
	}

	/** The local player's speed for the HUD, blocks a second. */
	public static float speedBps() {
		return speedBps;
	}

	static void endTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		double raw = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20.0;
		speedBps = Mth.lerp(0.35f, speedBps, (float) raw);
		if (!Engine.is(p) || p.isPassenger() || p.isSpectator() || p.getAbilities().flying || p.isInWater()) {
			lastVel = Vec3.ZERO;
			return;
		}
		int gear = Engine.gear(p);
		float mom = Engine.momentum(p);
		Vec3 v = p.getDeltaMovement();
		Vec3 flat = new Vec3(v.x, 0, v.z);
		if (gear <= 0 || !p.onGround() || mom < EngineConfig.SLIDE_MIN_MOMENTUM || flat.lengthSqr() < 0.01 || lastVel.lengthSqr() < 0.01
				|| Engine.running(p, Engine.V_DASH) || Engine.running(p, Engine.V_TURBO)) {
			lastVel = flat;
			return;
		}
		float grip = EngineConfig.GRIP_DEGREES[gear] * (1.6f - 0.6f * mom);
		if (Engine.recipro(p)) {
			grip *= 0.7f;
		}
		double a0 = Math.atan2(lastVel.z, lastVel.x);
		double a1 = Math.atan2(flat.z, flat.x);
		double diff = Mth.wrapDegrees(Math.toDegrees(a1 - a0));
		if (Math.abs(diff) > grip) {
			double a = a0 + Math.toRadians(Math.signum(diff) * grip);
			double len = flat.length() * 0.97;
			Vec3 turned = new Vec3(Math.cos(a) * len, 0, Math.sin(a) * len);
			p.setDeltaMovement(turned.x, v.y, turned.z);
			flat = turned;
		}
		lastVel = flat;
	}
}
