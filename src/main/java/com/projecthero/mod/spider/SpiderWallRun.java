package com.projecthero.mod.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the Wall Run. Sprinting ALONG a wall beside him (heading {@link #MIN_ANGLE} degrees or more off the wall's
 * normal -- W straight into a wall still climbs) puts Spider-Man on the wall face, running sideways across it at about
 * 0.45 b/t, his feet on the wall, drifting up or down a little with his pitch. Releasing Sprint (or W) drops him into the
 * ordinary wall crawl, Jump kicks him off the wall away and up, outside corners are wrapped. N's crawl-off switch turns
 * it off with the rest of the crawl.
 *
 * <p>The run is client-simulated ({@code client.spider.SpiderWallRunClient}, through the climb engine's travel hook);
 * the server checks the request, holds the climb grab, and sets {@link #FLAG} on the synced move state so every viewer
 * draws the run.
 */
public final class SpiderWallRun {
	/** {@link SpiderMoveState} flag bit: wall running (a high bit, clear of the stance flags). */
	public static final int FLAG = 1 << 22;
	/** The heading must be at least this far off the wall normal (degrees; 90 = exactly along the wall). */
	public static final double MIN_ANGLE = 50.0;
	/** ... and at most this far (beyond it he is heading away from the wall). */
	public static final double MAX_ANGLE = 110.0;
	/** Once running, looking closer than this to straight into / away from the wall ends the run (degrees). */
	public static final double KEEP_ANGLE = 35.0;
	/** Target speed along the wall (the climb engine's damping settles it at about 0.45 b/t). */
	public static final double RUN_SPEED = 0.5;
	/** Pitch within this many degrees of level gives no vertical drift. */
	public static final double DRIFT_DEADZONE = 22.0;
	/** The fastest vertical drift (b/t), at a 90-degree pitch. */
	public static final double MAX_DRIFT = 0.16;
	/** The kick off the wall: out from it, up, and along the way he looks. */
	public static final double KICK_OUT = 0.55;
	public static final double KICK_UP = 0.5;
	public static final double KICK_ALONG = 0.45;
	/** Server: ticks after a start before "not on the wall" ends the run (the client's hop onto it). */
	private static final int START_GRACE = 10;

	/** Server: when each runner started. */
	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();

	private SpiderWallRun() {
	}

	// ------------------------------------------------------------------ pure geometry (both sides, gametested)

	/**
	 * Degrees between the horizontal heading ({@code dx, dz}) and the direction INTO the wall {@code face} (the
	 * direction from the player into the surface): 0 = straight at it, 90 = exactly along it, 180 = straight away.
	 * Returns 0 for a zero heading.
	 */
	public static double angleOffNormal(double dx, double dz, Direction face) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-6 || face.getAxis().isVertical()) {
			return 0.0;
		}
		double dot = (dx * face.getStepX() + dz * face.getStepZ()) / len;
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
	}

	/** True when the heading runs along the wall closely enough to wall-run rather than climb. */
	public static boolean alongWall(double dx, double dz, Direction face) {
		double a = angleOffNormal(dx, dz, face);
		return a >= MIN_ANGLE && a <= MAX_ANGLE;
	}

	/**
	 * The unit horizontal direction along the wall {@code face} that the look ({@code lookX, lookZ}) points down, or null
	 * when the look is within {@link #KEEP_ANGLE} of straight into or away from the wall.
	 */
	public static Vec3 runDirection(double lookX, double lookZ, Direction face) {
		double a = angleOffNormal(lookX, lookZ, face);
		if (a < KEEP_ANGLE || a > 180.0 - KEEP_ANGLE) {
			return null;
		}
		// the two tangents of a vertical face: the face's step turned 90 degrees either way
		double tx = -face.getStepZ();
		double tz = face.getStepX();
		double s = lookX * tx + lookZ * tz >= 0.0 ? 1.0 : -1.0;
		return new Vec3(tx * s, 0.0, tz * s);
	}

	/** Vertical drift (b/t) for a pitch in degrees (+ = looking down, as Minecraft's xRot). */
	public static double drift(float pitchDeg) {
		double p = -pitchDeg;
		double mag = Math.max(0.0, Math.abs(p) - DRIFT_DEADZONE) / (90.0 - DRIFT_DEADZONE);
		return Math.signum(p) * Math.min(1.0, mag) * MAX_DRIFT;
	}

	/** The kick off the wall: {@code outward} is the wall's outward normal, {@code look} where he looks. */
	public static Vec3 kick(Vec3 outward, Vec3 look) {
		Vec3 along = new Vec3(look.x, 0.0, look.z);
		along = along.subtract(outward.scale(along.dot(outward)));
		along = along.lengthSqr() < 1.0e-6 ? Vec3.ZERO : along.normalize();
		return outward.scale(KICK_OUT).add(along.scale(KICK_ALONG)).add(0.0, KICK_UP, 0.0);
	}

	/** Whether {@code player} is wall running (the synced flag -- right for every viewer). */
	public static boolean running(Player player) {
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		return s != null && s.has(FLAG);
	}

	// ------------------------------------------------------------------ server

	private static void setFlag(ServerPlayer player, boolean on) {
		SpiderMoveState s = SpiderMoves.state(player);
		if (s.has(FLAG) == on) {
			return;
		}
		SpiderMoveState c = s.copy();
		c.set(FLAG, on);
		player.setAttached(ModAttachments.SPIDER_MOVE_STATE, c);
	}

	/** The client started a run along {@code face}. */
	public static boolean start(ServerPlayer player, int faceIndex) {
		Direction face = Direction.from3DDataValue(faceIndex);
		if (!SpiderMan.hasPower(player) || SpiderClimb.crawlOff(player) || face.getAxis().isVertical()
				|| SpiderSwing.isSwinging(player) || player.isInWater() || player.isPassenger()
				|| player.getAbilities().flying || player.isFallFlying()) {
			return false;
		}
		// the wall must really be there (a little extra reach for latency)
		if (!SpiderClimb.touching(player.level(), player.getBoundingBox().inflate(0.25, 0.0, 0.25), face, 0.6,
				SpiderClimb.Profile.SPIDER_MAN)) {
			return false;
		}
		SpiderClimb.requestGrab(player);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		setFlag(player, true);
		return true;
	}

	/** The client ended the run (sprint released, the wall ran out ...). He stays on the wall if he still is. */
	public static void stop(ServerPlayer player) {
		STARTED.remove(player.getUUID());
		setFlag(player, false);
	}

	/** Jump during a run (from {@link SpiderClimbActions#leap}): the kick away and up, and the run ends. */
	public static void kickOff(ServerPlayer player, Vec3 outward) {
		stop(player);
		com.projecthero.mod.hero.power.AbilityHelpers.launchSelf(player, kick(outward, player.getLookAngle()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPIDER_STEP,
				player.getSoundSource(), 0.6f, 1.25f);
		if (player.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 0.9, player.getZ(), 8, 0.25, 0.3,
					0.25, 0.02);
		}
	}

	/** Ends a run the client did not (power gone, crawl switched off, swinging, landed, off the wall). */
	static void serverTick(ServerPlayer player) {
		if (!running(player)) {
			STARTED.remove(player.getUUID());
			return;
		}
		long now = player.level().getGameTime();
		long since = now - STARTED.getOrDefault(player.getUUID(), now - START_GRACE - 1L);
		boolean lost = since > START_GRACE && (player.onGround() || !SpiderClimb.attachedToWall(player));
		if (!SpiderMan.hasPower(player) || SpiderClimb.crawlOff(player) || SpiderSwing.isSwinging(player)
				|| player.isInWater() || player.isPassenger() || lost) {
			stop(player);
		}
	}

	static void remove(UUID id) {
		STARTED.remove(id);
	}

	static void clearSessionState() {
		STARTED.clear();
	}
}
