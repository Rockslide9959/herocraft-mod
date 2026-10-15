package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderSwingInput;
import com.projecthero.mod.spider.data.SpiderManState;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of web swinging: it runs {@link SpiderSwing#applyRope} on the local player every
 * tick while a line is attached.
 *
 * <p>The anchor comes from the server through the synced state -- the client never chooses one -- but
 * the physics run here so the arc is drawn by the machine that owns the movement, with no latency and
 * nothing to rubber-band against.
 *
 * <p>Rope length is kept locally. It is the one swing value the server does not need: reeling in and
 * paying out only shapes the rider's own arc, nothing else in the world can see it, and syncing a
 * number that changes every tick a key is held would cost far more than it is worth. The server keeps
 * the length the line started at for its sanity checks and is the only thing that can end the swing.
 */
public final class SpiderSwingClient {
	private static double ropeLength;
	private static boolean wasSwinging;
	/** v0.16.1: game time the current line was first seen on this client. */
	private static long attachedAt;
	/** v0.16.1: game time of the last in-air release (for chaining). */
	private static long releasedAt = Long.MIN_VALUE;
	/** v0.16.2: the upside-down hang's current climb (-) / lower (+) speed, eased toward the held key. */
	private static double reelSpeed;

	private SpiderSwingClient() {
	}


	/** Called from {@code LocalPlayerMixin} at the top of {@code aiStep}, before vanilla moves anything. */
	public static void tick(LocalPlayer player) {
		SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (state == null || !state.swinging) {
			if (wasSwinging && state != null && !player.onGround() && !player.isInWater()
					&& !com.projecthero.mod.spider.SpiderMoves.state(player).has(com.projecthero.mod.spider.data.SpiderMoveState.FLAG_HANGING)) {
				// v0.16.1: let go in the air -- carry more of the arc's momentum, with a little lift
				com.projecthero.mod.spider.SpiderManConfig.Moves cfg = com.projecthero.mod.spider.SpiderManConfig.moves();
				Vec3 v = player.getDeltaMovement().scale(cfg.swingReleaseBoost).add(0.0, cfg.swingReleaseLift, 0.0);
				double len = v.length();
				if (len > cfg.swingMaxSpeed) {
					v = v.scale(cfg.swingMaxSpeed / len);
				}
				player.setDeltaMovement(v);
				releasedAt = player.level().getGameTime();
				SpiderAirMomentumClient.arm(); // v0.16.4: and no air drag from here until he lands
			}
			wasSwinging = false;
			return;
		}
		Vec3 anchor = new Vec3(state.anchorX, state.anchorY, state.anchorZ);
		double distance = anchor.distanceTo(player.getEyePosition());
		if (!wasSwinging) {
			// A fresh line starts at whatever length the server measured it at.
			ropeLength = state.ropeLength > 0.0 ? state.ropeLength : distance;
			wasSwinging = true;
			attachedAt = player.level().getGameTime();
			// v0.16.1: a line fired from the ground lifts him into the swing (a hop) instead of dragging him along it
			if (player.onGround() && !com.projecthero.mod.spider.SpiderMoves.state(player)
					.has(com.projecthero.mod.spider.data.SpiderMoveState.FLAG_HANGING)) {
				Vec3 v0 = player.getDeltaMovement();
				player.setDeltaMovement(v0.x, Math.max(v0.y, 0.48), v0.z);
			}
			// v0.16.1: a line fired soon after letting go chains -- a kick along the way he is travelling
			com.projecthero.mod.spider.SpiderManConfig.Moves cfg = com.projecthero.mod.spider.SpiderManConfig.moves();
			if (!player.onGround() && player.level().getGameTime() - releasedAt <= cfg.swingChainWindowTicks) {
				Vec3 v = player.getDeltaMovement();
				Vec3 flat = new Vec3(v.x, 0.0, v.z);
				if (flat.lengthSqr() > 1.0E-3) {
					player.setDeltaMovement(v.add(flat.normalize().scale(cfg.swingChainBoost)));
				}
			}
		}

		int input = SpiderSwingInput.pack(
				player.input.up, player.input.down, player.input.left, player.input.right,
				player.input.jumping, player.input.shiftKeyDown);

		// v0.16.1: the upside-down hang -- Jump climbs the line (shorter), Sneak lowers him (longer); he hangs straight
		// down from the anchor by his feet (the body is drawn inverted, so the top of his box is where his feet are)
		if (com.projecthero.mod.spider.SpiderMoves.hangingUpsideDown(player)) {
			// v0.16.2: faster (up to HANG_REEL_SPEED a tick, eased in so it never jerks) and as far down as the ground
			int dir = (SpiderSwingInput.sneak(input) ? 1 : 0) - (SpiderSwingInput.jump(input) ? 1 : 0);
			double accel = com.projecthero.mod.spider.SpiderMoves.HANG_REEL_ACCEL;
			double top = com.projecthero.mod.spider.SpiderMoves.HANG_REEL_SPEED;
			reelSpeed = dir != 0 ? Math.max(-top, Math.min(top, reelSpeed + dir * accel))
					: Math.abs(reelSpeed) <= accel * 2.0 ? 0.0 : reelSpeed - Math.signum(reelSpeed) * accel * 2.0;
			double before = ropeLength;
			double want = Math.max(com.projecthero.mod.spider.SpiderMoves.HANG_MIN_LENGTH,
					Math.min(com.projecthero.mod.spider.SpiderMoves.HANG_MAX_LENGTH, ropeLength + reelSpeed));
			// lowering stops with his head just above the ground / whatever is under him (never through it)
			ropeLength = com.projecthero.mod.spider.SpiderMoves.hangLowerTo(player, anchor, ropeLength, want);
			if (ropeLength != want || ropeLength <= com.projecthero.mod.spider.SpiderMoves.HANG_MIN_LENGTH && reelSpeed < 0.0
					|| ropeLength >= com.projecthero.mod.spider.SpiderMoves.HANG_MAX_LENGTH && reelSpeed > 0.0) {
				reelSpeed = 0.0;
			}
			// the line ends where his feet meet (HANG_FEET_BELOW_TOP under the top of his box)
			Vec3 target = new Vec3(anchor.x, com.projecthero.mod.spider.SpiderMoves.hangBoxBottom(anchor, ropeLength), anchor.z);
			// feed this tick's reel straight through, then pull in whatever error is left
			Vec3 v = target.subtract(player.position()).scale(0.35).add(0.0, -(ropeLength - before), 0.0);
			// a slow sway, so he is plainly hanging on a line rather than standing on air
			double sway = Math.sin(player.level().getGameTime() * 0.08) * 0.004;
			v = v.add(sway, 0.0, -sway);
			if (v.length() > 1.2) {
				v = v.normalize().scale(1.2);
			}
			player.setDeltaMovement(v);
			player.resetFallDistance();
			return;
		}
		// v0.16.1: hanging under a tightrope keeps a short fixed line (no reel-in / pay-out)
		if (com.projecthero.mod.spider.SpiderMoves.state(player).has(com.projecthero.mod.spider.data.SpiderMoveState.FLAG_HANGING)) {
			ropeLength = state.ropeLength > 0.0 ? state.ropeLength : 1.7;
		} else {
			ropeLength = SpiderSwing.adjustRope(ropeLength, distance, input);
		}
		Vec3 velocity = SpiderSwing.applyRope(player, anchor, ropeLength, player.getDeltaMovement(), input,
				state.artificialAnchor, state.airSwingBaselineY);
		player.setDeltaMovement(velocity);
		player.resetFallDistance();
	}

	/** v0.16.1: ticks the current line has been held (0 when not swinging). */
	public static long ticksOnLine(LocalPlayer player) {
		return wasSwinging ? player.level().getGameTime() - attachedAt : 0L;
	}

	/** Leaving a world must not carry a stale rope into the next one. */
	public static void reset() {
		ropeLength = 0.0;
		wasSwinging = false;
		releasedAt = Long.MIN_VALUE;
	}
}
