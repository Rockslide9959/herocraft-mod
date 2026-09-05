package com.herocraft.mod.client.spider;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.spider.SpiderClimb;
import com.herocraft.mod.spider.data.SpiderClimbLocal;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * The movement half of the adhesion engine: what the player's velocity should be while they are stuck
 * to a wall or a ceiling.
 *
 * <p>Client-side, for the client's own player. That is the same arrangement Super Speed already uses
 * (see {@code LocalPlayerMixin}) and it is the reason the movement feels smooth: the machine holding
 * the keyboard is the one integrating the motion, so there is no round-trip latency between pressing
 * a key and moving, and no per-tick correction packet to rubber-band against. The server keeps its
 * own copy of the attachment state for authority and for telling other clients what to draw, and it
 * still owns every ability, cost and permission; it simply does not fight the owner over their own
 * position, which is exactly how vanilla treats player movement in the first place.
 *
 * <h2>The model</h2>
 * Everything is expressed relative to the held surface, so one piece of code drives a wall, a ceiling
 * and every transition between them:
 * <ul>
 *   <li>the player's <em>look</em> direction is projected onto the surface plane and becomes
 *       "forward". Look up a wall and W climbs; look sideways and W crawls sideways; look down and W
 *       descends. The camera is never seized or snapped -- the input is reinterpreted instead, which
 *       is what section 28 of the design asks for;</li>
 *   <li>with no input the player simply stops, held by a small pull into the surface. No sliding;</li>
 *   <li>sneaking holds harder and refuses to move at all;</li>
 *   <li>gravity is not applied, so a ceiling crawl is a crawl rather than a fall.</li>
 * </ul>
 */
public final class SpiderClimbMovement {
	/** Constant pull into the held surface; keeps contact across seams without shoving the player in. */
	private static final double SUCTION = 0.075;
	private static final double SUCTION_SNEAK = 0.16;
	/** How quickly the velocity converges on the target -- high enough to feel responsive, low enough
	 *  that a transition eases rather than snapping. */
	private static final double RESPONSE = 0.55;

	private SpiderClimbMovement() {
	}

	/**
	 * Run one tick of adhered movement. Returns true when it took over, in which case the caller must
	 * skip vanilla's {@code travel} entirely.
	 */
	public static boolean travel(LocalPlayer player) {
		SpiderClimb.Profile profile = SpiderClimb.profile(player);
		SpiderClimbLocal local = player.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL);
		local.tickLock();
		local.tickTransition();

		Direction face = SpiderClimb.updateAttachment(player, profile, local);
		if (face == null || profile == null) {
			return false;
		}

		Vec3 normal = Vec3.atLowerCornerOf(face.getOpposite().getNormal());
		boolean sneaking = player.isShiftKeyDown();

		// --- build the surface-local frame from where the player is looking ---
		Vec3 look = player.getLookAngle();
		Vec3 forward = project(look, normal);
		if (forward.lengthSqr() < 1.0E-4) {
			// staring straight into the surface: fall back to "up the surface", or to the facing
			// direction on a ceiling where there is no such thing as up
			Vec3 fallback = face == Direction.UP
					? new Vec3(-Math.sin(Math.toRadians(player.getYRot())), 0, Math.cos(Math.toRadians(player.getYRot())))
					: new Vec3(0, 1, 0);
			forward = project(fallback, normal);
			if (forward.lengthSqr() < 1.0E-4) {
				forward = new Vec3(0, 0, 1);
			}
		}
		forward = forward.normalize();
		Vec3 right = forward.cross(normal).normalize();

		// --- inputs ---
		double zz = player.input.forwardImpulse;
		double xx = player.input.leftImpulse;
		Vec3 target = Vec3.ZERO;
		if (!sneaking && (Math.abs(zz) > 1.0E-3 || Math.abs(xx) > 1.0E-3)) {
			Vec3 dir = forward.scale(zz).add(right.scale(-xx));
			if (dir.lengthSqr() > 1.0E-6) {
				double speed = profile.climbSpeed() * (player.isSprinting() ? 1.35 : 1.0);
				// ease through a face change instead of instantly redirecting at full speed
				if (local.transitioning()) {
					speed *= 0.6;
				}
				target = dir.normalize().scale(speed);
			}
		}
		target = target.subtract(normal.scale(sneaking ? SUCTION_SNEAK : SUCTION));

		Vec3 current = player.getDeltaMovement();
		Vec3 next = current.add(target.subtract(current).scale(RESPONSE));

		// v0.6.17: sneaking while clinging locks the player to that exact spot on the surface, like
		// grabbing a ladder rung -- strip every bit of along-surface velocity so nothing (a lingering
		// nudge, a shove) can slide them out of position; only the pull into the wall remains.
		if (sneaking) {
			double intoWall = next.dot(normal);
			next = normal.scale(Math.min(0.0, intoWall));
		}

		player.setDeltaMovement(next);
		player.move(MoverType.SELF, player.getDeltaMovement());
		// Keep the velocity the collision resolution actually produced, so running into a corner does
		// not leave the player carrying speed they never had.
		player.setDeltaMovement(player.getDeltaMovement().multiply(0.86, 0.86, 0.86));
		player.resetFallDistance();
		return true;
	}

	/** Component of {@code v} lying in the plane whose normal is {@code n}. */
	private static Vec3 project(Vec3 v, Vec3 n) {
		return v.subtract(n.scale(v.dot(n)));
	}
}
