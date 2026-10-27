package com.projecthero.mod.client.quirk.engine;

import com.projecthero.mod.quirk.engine.Engine;
import com.projecthero.mod.quirk.engine.EngineConfig;
import com.projecthero.mod.quirk.engine.EngineSurvival;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (revamp): the local Engine player's run -- the sliding turns, the jumps, and the HUD's speed readout. The run's
 * speed itself is the server's movement-speed modifier (20 / 30 / 40 b/s per gear, doubled in Recipro Turbo), simulated
 * here by vanilla's own ground physics; this adds what vanilla gets wrong at those speeds:
 * <ul>
 *   <li><b>Grip</b>: in gear with momentum, the run can't swing round instantly. At the end of each client tick the
 *       carried velocity may turn only {@link EngineConfig#grip} degrees a tick (less in higher gears, with more momentum
 *       and in Turbo), so a sharp turn drifts wide before it bites -- the slide. The server sees the swing in his position
 *       and adds the skid sparks, sound and momentum cost. Tuned so even 80 b/s stays steerable.</li>
 *   <li><b>Jumps</b> (the way Super Speed carries its run): in the air vanilla bleeds 9% of the speed a tick, so a 40 b/s
 *       run would die in one hop; here the airborne speed eases back toward the take-off speed instead.</li>
 *   <li><b>Speed</b>: his horizontal speed in blocks a second, smoothed (the HUD).</li>
 * </ul>
 */
public final class EngineRunClient {
	private static Vec3 lastVel = Vec3.ZERO;
	private static float speedBps;
	/** Horizontal speed (blocks a tick) the last time he was on the ground in gear. */
	private static double groundSpeed;
	/** v0.17.1 the wall kick: the height he started up the wall at (NaN: not on a wall). */
	private static double wallStart = Double.NaN;

	private EngineRunClient() {
	}

	public static void reset() {
		lastVel = Vec3.ZERO;
		speedBps = 0f;
		groundSpeed = 0.0;
		wallStart = Double.NaN;
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
		// v0.17.1 survival: fast enough at Gear 3, he runs across water...
		boolean skimming = Engine.is(p) && EngineSurvival.skim(p);
		// ...and sprinting into a wall in gear runs up it (up to 4 blocks)
		if (Engine.is(p) && p.horizontalCollision && EngineSurvival.canWallKick(p)) {
			if (Double.isNaN(wallStart)) {
				wallStart = p.getY();
			}
			double lift = EngineSurvival.wallLift(p.getY() - wallStart);
			if (lift > 0.0) {
				Vec3 v0 = p.getDeltaMovement();
				p.setDeltaMovement(v0.x, Math.max(v0.y, lift), v0.z);
				p.resetFallDistance();
			}
		} else if (p.onGround() || !p.horizontalCollision) {
			if (!Double.isNaN(wallStart) && !p.horizontalCollision && !p.onGround()) {
				// off the top of the wall: a small hop forward over the lip
				Vec3 v0 = p.getDeltaMovement();
				if (v0.y > 0.2) {
					Vec3 f = Vec3.directionFromRotation(0f, p.getYRot());
					p.setDeltaMovement(v0.x + f.x * 0.25, 0.32, v0.z + f.z * 0.25);
				}
			}
			wallStart = Double.NaN;
		}
		if (!Engine.is(p) || p.isPassenger() || p.isSpectator() || p.getAbilities().flying || (p.isInWater() && !skimming) || p.isFallFlying()) {
			lastVel = Vec3.ZERO;
			groundSpeed = 0.0;
			return;
		}
		int gear = Engine.gear(p);
		float mom = Engine.momentum(p);
		boolean turbo = Engine.turbo(p);
		Vec3 v = p.getDeltaMovement();
		Vec3 flat = new Vec3(v.x, 0, v.z);
		double horiz = flat.length();

		// --- jumps: carry the run through the air
		if (gear <= 0) {
			groundSpeed = 0.0;
		} else if (p.onGround()) {
			groundSpeed = Math.min(EngineConfig.AIR_CARRY_MAX, horiz);
		} else if (groundSpeed > 0.3 && horiz > 1.0e-3 && horiz < groundSpeed && !p.horizontalCollision) {
			double target = horiz + (groundSpeed - horiz) * EngineConfig.AIR_CARRY;
			double f = target / horiz;
			p.setDeltaMovement(v.x * f, v.y, v.z * f);
			v = p.getDeltaMovement();
			flat = new Vec3(v.x, 0, v.z);
		}

		// --- grip: a sharp turn at speed drifts wide (the slide)
		if (gear <= 0 || !p.onGround() || mom < EngineConfig.SLIDE_MIN_MOMENTUM || flat.lengthSqr() < 0.01 || lastVel.lengthSqr() < 0.01
				|| Engine.running(p, Engine.V_DASH) || Engine.running(p, Engine.V_STRIKE)) {
			lastVel = flat;
			return;
		}
		float grip = EngineConfig.grip(gear, mom, turbo || Engine.burst(p));
		if (Engine.braking(p)) {
			grip *= EngineConfig.BRAKE_GRIP; // v0.17.1 the Engine Brake: the skid drifts the turn
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
