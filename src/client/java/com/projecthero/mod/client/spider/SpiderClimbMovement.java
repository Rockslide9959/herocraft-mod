package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.data.SpiderClimbLocal;

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
	/** v0.16.2: added to the look's y on a wall, so W climbs unless he looks clearly downward. */
	private static final double UP_BIAS = 0.65;
	/** v0.16.2 the mantle: how far above his hull the wall must have ended, and the hop up and over the lip. */
	private static final double MANTLE_PROBE = 0.6;
	private static final double MANTLE_UP = 0.46;
	private static final double MANTLE_FORWARD = 0.3;
	private static final int MANTLE_LOCK = 8;
	/** v0.16.2: the push away from the wall after his head hits a block jutting out of it. */
	private static final double BUMP_OUT = 0.22;
	/** v0.16.2: at most this long sliding out round one bump. */
	private static final int SLIDE_TICKS = 14;
	/** v0.16.2: the little hop that steps him from the ground onto a wall he walks into. */
	private static final double START_HOP = 0.3;

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
		// v0.16.2: sliding out round a jutting block he stays on his wall -- the block's underside is not a
		// ceiling to crawl along (that carried him straight back into the corner under it)
		local.tickSlide();
		Direction slide = local.slideFace();
		if (slide != null && profile != null && !player.isShiftKeyDown() && (face == null || face == Direction.UP)) {
			face = slide;
		}
		if (face == null || profile == null) {
			startFromGround(player, profile);
			return false;
		}

		Vec3 normal = Vec3.atLowerCornerOf(face.getOpposite().getNormal());
		boolean sneaking = player.isShiftKeyDown();

		// --- build the surface-local frame from where the player is looking ---
		Vec3 look = player.getLookAngle();
		if (face.getAxis().isHorizontal()) {
			// v0.16.2: on a wall W means "up" unless he is clearly looking down (~45 degrees or more) -- a glance at
			// the floor in a cave no longer flips W into a descent. Looking along the wall still crawls sideways.
			look = look.add(0.0, UP_BIAS, 0.0);
		}
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

		boolean climbingUp = !sneaking && face.getAxis().isHorizontal() && target.y > 0.01;
		if (climbingUp) {
			net.minecraft.world.phys.AABB box = player.getBoundingBox();
			// v0.16.2 the mantle: the wall ends just above him (the top of a cliff / the lip of a cave shaft) --
			// hop up and over onto the top instead of hanging under the edge
			if (!SpiderClimb.touching(player.level(), box.move(0.0, MANTLE_PROBE, 0.0), face, profile.stickyRadius(), profile)
					&& SpiderClimb.touching(player.level(), box, face, profile.stickyRadius(), profile)) {
				Vec3 over = Vec3.atLowerCornerOf(face.getNormal()).scale(MANTLE_FORWARD);
				player.setDeltaMovement(over.x, MANTLE_UP, over.z);
				player.move(MoverType.SELF, player.getDeltaMovement());
				player.resetFallDistance();
				// one hop: let go of the wall for a moment so vanilla gravity carries the arc onto the top
				local.lockOut(MANTLE_LOCK);
				return true;
			}
			// v0.16.2 rough walls: last tick his head hit a block jutting out of the wall -- ease out from the wall
			// so he slides round the bump and grips its outer face, instead of being stuck under it
			if (local.bumpedHead()) {
				local.startSlide(face, SLIDE_TICKS);
			}
			if (local.slideFace() != null) {
				// straight up and out from the wall, no suction, until his head is past the jutting block
				target = new Vec3(0.0, Math.max(target.y, profile.climbSpeed() * 0.6), 0.0).add(normal.scale(BUMP_OUT));
			}
		}

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
		local.setBumpedHead(climbingUp && player.verticalCollision && next.y > 0.0 && !player.onGround());
		if (local.slideFace() != null && !player.verticalCollision
				&& !SpiderClimb.touching(player.level(), player.getBoundingBox(), Direction.UP, 0.05, profile)) {
			local.endSlide(); // clear of the bump: the block's outer face is the wall now
		}
		// Keep the velocity the collision resolution actually produced, so running into a corner does
		// not leave the player carrying speed they never had.
		player.setDeltaMovement(player.getDeltaMovement().multiply(0.86, 0.86, 0.86));
		player.resetFallDistance();
		return true;
	}

	/**
	 * v0.16.2: walking into a wall with W held (wall crawl on, not sneaking, not looking down at his feet) steps him
	 * straight onto it -- a small hop, and the next tick the airborne grip takes over. No jump needed to start a climb.
	 */
	private static void startFromGround(LocalPlayer player, SpiderClimb.Profile profile) {
		if (profile == null || !player.onGround() || player.isShiftKeyDown() || player.input.forwardImpulse <= 0.0f
				|| !player.horizontalCollision || player.getXRot() > 50.0f || player.isInWater()
				|| (profile == SpiderClimb.Profile.SPIDER_MAN && SpiderClimb.crawlOff(player))) { // N switched the crawl off
			return;
		}
		Direction facing = player.getDirection();
		if (!SpiderClimb.touching(player.level(), player.getBoundingBox(), facing, profile.stickyRadius(), profile)) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, Math.max(v.y, START_HOP), v.z);
	}

	/** Component of {@code v} lying in the plane whose normal is {@code n}. */
	private static Vec3 project(Vec3 v, Vec3 n) {
		return v.subtract(n.scale(v.dot(n)));
	}
}
