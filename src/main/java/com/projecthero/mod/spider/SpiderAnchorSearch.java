package com.projecthero.mod.spider;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finds somewhere for a web to attach. Deliberately a <em>ray</em> search, not a block scan: a swing
 * request fires a fixed handful of clips through a cone above and ahead of the player rather than
 * walking a 32-block cube, so the cost is a couple of dozen raycasts on the tick a swing starts and
 * nothing at all on the ticks in between.
 *
 * <p>Identical code runs on the client and the server. The client uses it to start swinging the
 * instant the key goes down (no round trip, so the swing feels immediate even on a laggy server); the
 * server uses it to decide the real anchor, which is the one that counts. Because the inputs are the
 * same -- position, look, velocity -- the two answers agree except at the edges, and the server's
 * result overwrites the client's through the synced state.
 *
 * <h2>Hybrid anchoring</h2>
 * Real geometry always wins: a rooftop, cliff, canopy branch or cave ceiling makes a far better swing
 * than anything fabricated, and urban or mountainous terrain should feel dramatically better to move
 * through than open plains. But most survival worlds are mostly open plains, so when nothing real is
 * in reach the search returns a fabricated point instead, high and ahead of the player, and the swing
 * proceeds normally. {@link SpiderSwing} is what stops that from becoming flight -- a fabricated
 * anchor can only lift the player so far above the altitude they started the sequence at.
 */
public final class SpiderAnchorSearch {
	/** Longest web the search will accept. */
	public static final double MAX_RANGE = 32.0;
	/** Below this the swing is too short to be worth anything -- keep looking. */
	public static final double MIN_RANGE = 6.0;

	private SpiderAnchorSearch() {
	}

	/**
	 * @param pos        where the web attaches
	 * @param artificial true when nothing real was in reach and this point was fabricated
	 */
	public record Anchor(Vec3 pos, boolean artificial) {
	}

	/** Yaw offsets, in radians, of the candidate rays swept either side of the travel direction. */
	private static final double[] YAW_FAN = { 0.0, 0.30, -0.30, 0.62, -0.62, 1.05, -1.05 };
	/** Pitch angles, in radians above horizontal, of the candidate rays. */
	private static final double[] PITCH_FAN = { 1.15, 0.85, 0.60, 1.40 };

	/**
	 * Look for a real block to swing from, then fall back to a fabricated point.
	 *
	 * <p>Aim direction is a blend of where the player is looking and where they are actually
	 * travelling, so a swing chains forward out of the previous one instead of stalling whenever the
	 * camera drifts. Candidates are scored on height gained, how far ahead of the player they sit, and
	 * a preferred rope length -- not on being nearest, which would pick the ground at your feet.
	 */
	public static Anchor find(Player player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 aim = aimDirection(player);

		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		double baseYaw = Math.atan2(aim.z, aim.x);
		for (double pitch : PITCH_FAN) {
			for (double dYaw : YAW_FAN) {
				double yaw = baseYaw + dYaw;
				double horizontal = Math.cos(pitch);
				Vec3 dir = new Vec3(Math.cos(yaw) * horizontal, Math.sin(pitch), Math.sin(yaw) * horizontal);
				Vec3 end = eye.add(dir.scale(MAX_RANGE));
				BlockHitResult hit = level.clip(new ClipContext(eye, end,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
				if (hit.getType() != HitResult.Type.BLOCK) {
					continue;
				}
				Vec3 point = hit.getLocation();
				double score = score(eye, aim, point);
				if (score > bestScore) {
					bestScore = score;
					best = point;
				}
			}
		}

		if (best != null) {
			return new Anchor(best, false);
		}
		return new Anchor(artificialPoint(player, aim), true);
	}

	/**
	 * Where the swing should head. Mostly the camera, but blended toward current travel so momentum
	 * carries: a player already moving fast keeps going the way they were going even if they glance
	 * sideways mid-arc.
	 */
	private static Vec3 aimDirection(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flatLook = new Vec3(look.x, 0, look.z);
		if (flatLook.lengthSqr() < 1.0E-4) {
			flatLook = Vec3.directionFromRotation(0.0f, player.getYRot());
		}
		flatLook = flatLook.normalize();

		Vec3 v = player.getDeltaMovement();
		Vec3 flatVel = new Vec3(v.x, 0, v.z);
		if (flatVel.length() < 0.15) {
			return flatLook;
		}
		Vec3 blended = flatLook.scale(0.65).add(flatVel.normalize().scale(0.35));
		return blended.lengthSqr() < 1.0E-4 ? flatLook : blended.normalize();
	}

	/**
	 * Anchor quality. Height above the player and distance <em>ahead</em> of them are what make a
	 * swing feel like a swing; a rope near 20 blocks gives the widest, fastest arc; anything behind
	 * the player or below their eyeline is heavily penalised so a swing never yanks them backwards.
	 */
	private static double score(Vec3 eye, Vec3 aim, Vec3 point) {
		Vec3 delta = point.subtract(eye);
		double dist = delta.length();
		if (dist < MIN_RANGE || dist > MAX_RANGE) {
			return Double.NEGATIVE_INFINITY;
		}
		double height = delta.y;
		if (height < 2.0) {
			return Double.NEGATIVE_INFINITY; // never swing from something level with or below you
		}
		double forward = delta.x * aim.x + delta.z * aim.z;
		if (forward < 0.0) {
			return Double.NEGATIVE_INFINITY; // behind the player
		}

		double heightScore = Math.min(height, 22.0) * 1.4;
		double forwardScore = Math.min(forward, 22.0) * 1.1;
		double lengthScore = 14.0 - Math.abs(dist - 20.0) * 0.7;
		return heightScore + forwardScore + lengthScore;
	}

	/**
	 * The fabricated fallback: ahead of the player and well above them, pushed further out the faster
	 * they are already moving so a chain of swings keeps building speed rather than stalling into a
	 * vertical bounce. Never placed straight overhead -- that would produce a pogo stick, not a swing.
	 */
	private static Vec3 artificialPoint(Player player, Vec3 aim) {
		Vec3 v = player.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		double ahead = 7.5 + Math.min(5.0, speed * 6.0);
		double up = 13.0 + Math.min(3.0, speed * 3.0);
		return player.getEyePosition().add(aim.x * ahead, up, aim.z * ahead);
	}
}
