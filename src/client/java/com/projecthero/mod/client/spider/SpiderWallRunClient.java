package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SpiderActionPayload;
import com.projecthero.mod.network.SpiderFreerunPayload;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderWallRun;
import com.projecthero.mod.spider.data.SpiderClimbLocal;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the Wall Run on the owning client ({@link SpiderWallRun} has the rules).
 *
 * <ul>
 * <li>{@link #clientTick} watches for the start: Sprint + W with a wall beside him and the heading at least
 * {@link SpiderWallRun#MIN_ANGLE} degrees off its normal -- from the ground (a small hop onto the wall), in the air, or
 * from a wall crawl. Walking / running straight INTO a wall still climbs.</li>
 * <li>{@link #travel} (from the climb engine's travel, while he is on that wall) runs him across the face.</li>
 * <li>Releasing Sprint or W, or looking into / away from the wall, ends the run and leaves him on the wall (the crawl);
 * Jump is the ordinary surface leap, which the server turns into the wall-run kick; Sneak lets go as usual.</li>
 * <li>An outside corner is wrapped: he steps round it onto the next face and the view turns with him.</li>
 * </ul>
 */
public final class SpiderWallRunClient {
	/** The climb engine's response / damping (same as the crawl, so the hand-over is seamless). */
	private static final double RESPONSE = 0.55;
	private static final double DAMPING = 0.86;
	private static final double SUCTION = 0.075;
	/** The hop off the ground onto the wall. */
	private static final double START_HOP = 0.24;
	/** Ticks after a run ends before another may start (no flicker at a wall's end). */
	private static final int RESTART_COOLDOWN = 8;
	/** Ticks the camera takes to turn round an outside corner. */
	private static final int TURN_TICKS = 5;
	/** The minimum ground speed (b/t) to start from the ground -- actually running. */
	private static final double MIN_GROUND_SPEED = 0.12;

	private static boolean active;
	private static Direction face;
	private static int ticks;
	private static int cooldown;
	private static int turnLeft;
	private static float turnStep;
	/** While the view turns round a corner: the run direction (the look has not caught up yet). */
	private static Vec3 latched;
	/** The wall ran out last tick: step round the corner this tick (one straight move per tick, so the server agrees). */
	private static boolean pendingWrap;
	/** Diagnostics (harness / tests): the last run's horizontal speed along the wall and corner wraps. */
	public static double lastSpeed;
	public static int wraps;

	private SpiderWallRunClient() {
	}

	public static boolean active() {
		return active;
	}

	public static Direction face() {
		return active ? face : null;
	}

	private static boolean sprintHeld(LocalPlayer p) {
		return Minecraft.getInstance().options.keySprint.isDown() || p.isSprinting();
	}

	/** End-of-tick: start / end checks and the corner turn. */
	public static void clientTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			reset();
			return;
		}
		if (turnLeft > 0) {
			turnLeft--;
			p.setYRot(p.getYRot() + turnStep);
			p.setYHeadRot(p.getYRot());
		}
		if (active) {
			ticks++;
			// arm the grip only once he is off the ground (the climb engine drops the intent of a grounded player)
			SpiderClimbLocal l = p.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL);
			if (!p.onGround() && !l.grabIntent() && ticks <= 6) {
				l.setGrabIntent(true);
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.CLIMB_GRAB));
			}
			boolean lost = ticks > 6 && !SpiderClimb.attachedToWall(p);
			if (lost || !SpiderMan.hasPower(p) || SpiderClimb.crawlOff(p) || SpiderSwing.isSwinging(p) || p.isInWater()
					|| p.isPassenger() || (ticks > 6 && p.onGround())) {
				end(p);
			}
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (mc.screen != null || !SpiderMan.hasPower(p) || SpiderClimb.crawlOff(p) || !sprintHeld(p)
				|| p.input.forwardImpulse <= 0.0f || p.isShiftKeyDown() || SpiderSwing.isSwinging(p)
				|| SpiderMoves.state(p).has(SpiderMoveState.FLAG_HANGING) || p.getAbilities().flying || p.isPassenger()
				|| p.isInWater() || p.isInLava() || p.isFallFlying() || SpiderClimb.onCeiling(p)) {
			return;
		}
		SpiderClimb.Profile profile = SpiderClimb.profile(p);
		if (profile == null) {
			return;
		}
		// the heading: where W (and A / D) is taking him
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double lx = Mth.cos(yaw), lz = Mth.sin(yaw);
		double hx = fx * p.input.forwardImpulse + lx * p.input.leftImpulse;
		double hz = fz * p.input.forwardImpulse + lz * p.input.leftImpulse;
		SpiderClimbLocal local = p.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL);
		Direction found = null;
		if (local.face() != null) {
			found = local.face().getAxis().isHorizontal() ? local.face() : null;
			if (found != null && !SpiderWallRun.alongWall(hx, hz, found)) {
				found = null;
			}
		} else {
			if (p.onGround() && p.getDeltaMovement().horizontalDistance() < MIN_GROUND_SPEED) {
				return;
			}
			double best = -2.0;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (!SpiderWallRun.alongWall(hx, hz, d)
						|| !SpiderClimb.touching(p.level(), p.getBoundingBox(), d, profile.stickyRadius(), profile)) {
					continue;
				}
				Vec3 look = p.getLookAngle();
				double dot = look.x * d.getStepX() + look.z * d.getStepZ();
				if (dot > best) {
					best = dot;
					found = d;
				}
			}
		}
		Vec3 look = p.getLookAngle();
		if (found == null || SpiderWallRun.runDirection(look.x, look.z, found) == null) {
			return;
		}
		start(p, local, found);
	}

	private static void start(LocalPlayer p, SpiderClimbLocal local, Direction wall) {
		active = true;
		face = wall;
		ticks = 0;
		if (p.onGround()) {
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x, Math.max(v.y, START_HOP), v.z);
		}
		if (!local.grabIntent() && !p.onGround()) {
			local.setGrabIntent(true);
			ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.CLIMB_GRAB));
		}
		ClientPlayNetworking.send(new SpiderFreerunPayload(SpiderFreerunPayload.WALL_RUN_START, wall.get3DDataValue()));
	}

	private static void end(LocalPlayer p) {
		if (!active) {
			return;
		}
		active = false;
		pendingWrap = false;
		cooldown = RESTART_COOLDOWN;
		turnLeft = 0;
		if (p != null) {
			ClientPlayNetworking.send(new SpiderFreerunPayload(SpiderFreerunPayload.WALL_RUN_STOP, 0));
		}
	}

	/**
	 * One tick of the run, from {@code SpiderClimbMovement.travel} while he is on a wall. Returns true when it moved him
	 * (the crawl is skipped); false hands the tick back to the crawl (the run just ended, or is not running).
	 */
	public static boolean travel(LocalPlayer p, SpiderClimb.Profile profile, SpiderClimbLocal local, Direction attached) {
		if (!active || profile == null || attached == null || !attached.getAxis().isHorizontal()) {
			return false;
		}
		if (attached != face && turnLeft <= 0 && !pendingWrap) {
			// the climb engine moved him to another face (an inside corner, a different wall): run along it if he can
			Vec3 look = p.getLookAngle();
			if (SpiderWallRun.runDirection(look.x, look.z, attached) == null) {
				end(p);
				return false;
			}
			face = attached;
		}
		Vec3 look = p.getLookAngle();
		Vec3 along = turnLeft > 0 && latched != null ? latched : SpiderWallRun.runDirection(look.x, look.z, face);
		if (along == null || !sprintHeld(p) || p.input.forwardImpulse <= 0.0f || p.isShiftKeyDown()) {
			end(p); // stays on the wall: the crawl takes over this very tick
			return false;
		}
		Vec3 into = new Vec3(face.getStepX(), 0.0, face.getStepZ());
		if (pendingWrap) {
			pendingWrap = false;
			if (!wrap(p, local, profile, along, into)) {
				end(p);
				return false;
			}
			p.resetFallDistance();
			return true;
		}
		Vec3 target = along.scale(SpiderWallRun.RUN_SPEED).add(0.0, SpiderWallRun.drift(p.getXRot()), 0.0)
				.add(into.scale(SUCTION));
		Vec3 cur = p.getDeltaMovement();
		Vec3 next = cur.add(target.subtract(cur).scale(RESPONSE));
		p.setDeltaMovement(next);
		Vec3 before = p.position();
		p.move(MoverType.SELF, next);
		lastSpeed = p.position().subtract(before).dot(along);
		// facing along the wall: the body runs the way he goes
		float yaw = (float) Math.toDegrees(Math.atan2(-along.x, along.z));
		p.yBodyRot = yaw;
		if (!SpiderClimb.touching(p.level(), p.getBoundingBox(), face, profile.stickyRadius(), profile)) {
			// the wall ended beside him: an outside corner -- step round it onto the next face next tick
			pendingWrap = true;
		} else if (ticks > 2 && lastSpeed < 0.04 && p.horizontalCollision) {
			// a wall ahead (an inside corner) or something in the way: stop running, the crawl takes it from here
			end(p);
		}
		p.setDeltaMovement(p.getDeltaMovement().scale(DAMPING));
		p.resetFallDistance();
		return true;
	}

	/** Round an outside corner: {@code along} the way he runs, {@code into} into the wall that just ended. */
	private static boolean wrap(LocalPlayer p, SpiderClimbLocal local, SpiderClimb.Profile profile, Vec3 along, Vec3 into) {
		AABB box = p.getBoundingBox();
		// the corner: the wall's end must be just behind him, on the wall side
		if (p.level().noCollision(p, box.move(into.scale(0.45)).move(along.scale(-0.7)))) {
			return false;
		}
		// and the way round clear
		Vec3 step1 = into.scale(0.75).add(along.scale(0.06));
		if (!p.level().noCollision(p, box.move(step1))) {
			return false;
		}
		Vec3 old = p.position();
		// two straight, collision-free steps (set directly, so neither clips the corner): past the corner, then back
		// against the new face (to within 0.05)
		AABB round = box.move(step1);
		double back = 0.0;
		for (int i = 1; i <= 14; i++) {
			if (!p.level().noCollision(p, round.move(along.scale(-0.05 * i)))) {
				break;
			}
			back = 0.05 * i;
		}
		p.setPos(old.add(step1).subtract(along.scale(back)));
		Direction next = Direction.getNearest(-along.x, 0.0, -along.z);
		if (!SpiderClimb.touching(p.level(), p.getBoundingBox(), next, profile.stickyRadius(), profile)) {
			p.setPos(old);
			return false;
		}
		local.attach(next);
		local.markTransition();
		face = next;
		wraps++;
		// the new run direction is the old "into"; turn the view with him over a few ticks
		float from = (float) Math.toDegrees(Math.atan2(-along.x, along.z));
		float to = (float) Math.toDegrees(Math.atan2(-into.x, into.z));
		turnStep = Mth.wrapDegrees(to - from) / TURN_TICKS;
		turnLeft = TURN_TICKS;
		latched = into;
		p.setDeltaMovement(into.scale(SpiderWallRun.RUN_SPEED * 0.8));
		return true;
	}

	public static void reset() {
		pendingWrap = false;
		active = false;
		face = null;
		cooldown = 0;
		turnLeft = 0;
	}
}
