package com.projecthero.mod.hero.power.p04;

import java.util.List;

import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "i noticed they slow down slightly when running up blocks with the step assist, i dont want that to happen,
 * they should maintain their speed and momentum even if running up blocks and stuff"). For every speedster -- the plain
 * Speedster in Speed Mode / Overdrive and the Speed Force Speedster in Speed Mode.
 *
 * <p><b>Why they slowed down.</b> Vanilla's step-up ({@code Entity.collide}) climbs one step per move: it lifts the box by the
 * lowest step height it finds ahead, sweeps the whole horizontal move at that height and stops there. A speedster moves 2 to
 * 12 blocks a tick, so on any hill or staircase one move reaches the NEXT step too: the sweep hits its face, the move comes
 * back short, {@code horizontalCollision} is set, and {@code Entity.move} zeroes the velocity along that axis. The next tick
 * starts from a standstill (a 0.546 a tick climb back to speed), and on the client a non-minor horizontal collision also ends
 * the sprint -- the sprint speed, and the Speed Force Speedster's momentum build, both read it.
 *
 * <p><b>The fix.</b> When a mode speedster's move comes back short while on the ground, the rest of it is climbed step by step
 * (each time: up by the lowest half-block that lets the box move on, along as far as it goes, back down by as much as it went
 * up) within the step assist's height, so the move covers its full distance up the hill and nothing is zeroed. A real wall
 * (taller than the step assist) still stops them exactly as before.
 */
public final class SpeedsterStepUp {
	/** At most this many steps climbed in one move, and this much height. */
	private static final int MAX_STEPS = 12;
	private static final double MAX_RISE = 16.0;

	private SpeedsterStepUp() {
	}

	/** Whether {@code p}'s moves get the multi-step climb (client-safe: both checks read synced state). */
	public static boolean applies(Player p) {
		if (p.maxUpStep() <= 0.6f || p.getAbilities().flying || p.isPassenger() || p.isSpectator()) {
			return false;
		}
		if (SpeedForceSpeedster.speedActive(p)) {
			return !SpeedForceSpeedster.phasing(p);
		}
		return SuperSpeedHandlers.speedFactor(p) > 1.0f && !SuperSpeedHandlers.phasing(p);
	}

	/**
	 * The move {@code movement} that vanilla collided to {@code result}: if it came back short on the ground, the rest climbed
	 * step by step; null when there is nothing better (left as vanilla had it).
	 */
	public static Vec3 climb(Entity e, Vec3 movement, Vec3 result) {
		double want = movement.horizontalDistanceSqr();
		if (want < 1.0e-6 || result.horizontalDistanceSqr() >= want - 1.0e-6) {
			return null;
		}
		boolean grounded = e.onGround() || (movement.y < 0.0 && result.y != movement.y);
		if (!grounded) {
			return null;
		}
		double step = e.maxUpStep();
		AABB cur = e.getBoundingBox().move(result);
		double rx = movement.x - result.x;
		double rz = movement.z - result.z;
		double tx = result.x;
		double ty = result.y;
		double tz = result.z;
		double rise = 0.0;
		for (int i = 0; i < MAX_STEPS && rx * rx + rz * rz > 1.0e-6 && rise < MAX_RISE; i++) {
			boolean moved = false;
			for (double h = 0.5; h <= step + 1.0e-6; h += 0.5) {
				Vec3 up = Entity.collideBoundingBox(e, new Vec3(0.0, h, 0.0), cur, e.level(), List.of());
				if (up.y <= 1.0e-4) {
					break; // a ceiling right overhead
				}
				AABB raised = cur.move(0.0, up.y, 0.0);
				Vec3 along = Entity.collideBoundingBox(e, new Vec3(rx, 0.0, rz), raised, e.level(), List.of());
				if (along.horizontalDistanceSqr() > 0.01) {
					AABB over = raised.move(along.x, 0.0, along.z);
					Vec3 down = Entity.collideBoundingBox(e, new Vec3(0.0, -up.y, 0.0), over, e.level(), List.of());
					cur = over.move(0.0, down.y, 0.0);
					tx += along.x;
					tz += along.z;
					ty += up.y + down.y;
					rise += Math.max(0.0, up.y + down.y);
					rx -= along.x;
					rz -= along.z;
					moved = true;
					break;
				}
				if (up.y < h - 1.0e-4) {
					break; // the ceiling stops it going any higher
				}
			}
			if (!moved) {
				break;
			}
		}
		Vec3 out = new Vec3(tx, ty, tz);
		return out.horizontalDistanceSqr() > result.horizontalDistanceSqr() + 1.0e-6 ? out : null;
	}
}
