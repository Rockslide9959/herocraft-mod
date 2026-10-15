package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SpiderActionPayload;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderClimbLocal;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Watches the vanilla jump, sneak and sprint keys for Spider-Man's / Spider Adhesion's movement gestures and sends one
 * edge-triggered packet when one fires. No new keybinding: every gesture is something a player would try anyway, and
 * the server re-checks all of them so these are requests, not orders.
 *
 * <h2>The gestures</h2>
 * <ul>
 *   <li><b>Run or fly into a wall</b> (v0.16.1) -- airborne, or sprinting into it on the ground -- and Spider-Man
 *       sticks to it: the climb engine's grab intent is armed on this client and the server. The old C toggle is
 *       gone.</li>
 *   <li><b>Sneak while stuck</b> &rarr; let go (v0.16.1: one press; the old double-tap still works). Reaching the
 *       ground also lets go.</li>
 *   <li><b>Jump while stuck</b> &rarr; shove off the surface.</li>
 *   <li><b>Jump in mid-air</b> (Spider-Man only) &rarr; the second jump; <b>with Sprint held</b> (v0.16.1) &rarr; the
 *       Web Zip boost instead.</li>
 *   <li><b>Sneak on a tightrope</b> &rarr; drop under it and hang; <b>Jump</b> while hanging flips back on top,
 *       <b>Sneak</b> lets go.</li>
 * </ul>
 */
public final class SpiderInputClient {
	/** Double-tap window for the release gesture, in client ticks (~0.35 s). */
	private static final int DOUBLE_TAP_TICKS = 7;
	/** Minimum airtime before a mid-air double jump can fire. */
	private static final int MIN_AIR_TICKS = 5;
	/** A double jump is refused when solid ground is within this distance below the player. */
	private static final double GROUND_CLEARANCE = 1.15;
	/** Ticks the client copy of the attachment refuses to re-grab after a leap (mirrors the server). */
	private static final int LEAP_LOCKOUT = 7;
	/** v0.16.1: after Sneak lets go of a wall, this long before running into it grabs again. */
	private static final int RELEASE_LOCKOUT = 12;

	private static boolean jumpWasDown;
	private static boolean sneakWasDown;
	private static boolean leftGround;
	private static int airTicks;
	private static int releaseWindow;
	private static int regrabLock;

	/** v0.16.1: a swing must have been held this many ticks before swinging into a wall lets go of it. */
	private static final long SWING_WALL_GRACE = 4L;
	/** v0.16.1: the current line was fired while already pressed against a wall (cleared once he is off it). */
	private static boolean wallAtLineStart;

	private SpiderInputClient() {
	}

	public static void clientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			jumpWasDown = false;
			sneakWasDown = false;
			leftGround = false;
			airTicks = 0;
			releaseWindow = 0;
			SpiderSwingClient.reset();
			SpiderAirMomentumClient.reset();
			SpiderStrands.reset();
			FirstPersonWebAnchor.reset();
			SpiderTraversalClient.reset();
			return;
		}
		if (client.screen != null) {
			jumpWasDown = client.options.keyJump.isDown();
			sneakWasDown = client.options.keyShift.isDown();
			return;
		}

		SpiderClimb.Profile profile = SpiderClimb.profile(player);
		boolean climber = profile != null;
		boolean spiderMan = SpiderMan.hasPower(player);
		if (!climber && !spiderMan) {
			jumpWasDown = client.options.keyJump.isDown();
			sneakWasDown = client.options.keyShift.isDown();
			leftGround = false;
			airTicks = 0;
			return;
		}

		boolean adhered = SpiderClimb.attached(player);
		boolean swinging = SpiderSwing.isSwinging(player);
		boolean hanging = spiderMan && SpiderMoves.state(player).has(SpiderMoveState.FLAG_HANGING);
		if (regrabLock > 0) {
			regrabLock--;
		}

		if (player.onGround() || adhered || swinging || player.isInWater() || player.isInLava()) {
			airTicks = 0;
			leftGround = false;
		} else {
			if (airTicks < 400) {
				airTicks++;
			}
			// only count as "left the ground under our own steam" once the key has been released once,
			// so holding jump off a ledge does not silently arm the second jump
			if (!jumpWasDown) {
				leftGround = true;
			}
		}

		boolean jumpDown = client.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;

		boolean sneakDown = client.options.keyShift.isDown();
		boolean sneakPressed = sneakDown && !sneakWasDown;
		sneakWasDown = sneakDown;
		boolean sprintHeld = client.options.keySprint.isDown() || player.isSprinting();

		// ---- hanging under a tightrope (v0.16.1): Jump climbs back on top, Sneak lets go ----
		if (hanging) {
			if (SpiderMoves.hangingUpsideDown(player)) {
				return; // v0.16.1: upside down, held Jump / Sneak reel the line (SpiderSwingClient); Z / Shift+Z do the rest
			}
			if (jumpPressed) {
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.ROPE_CLIMB));
			} else if (sneakPressed) {
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SWING_STOP));
			}
			return;
		}

		// v0.16.2 (user): Sneak on a tightrope just crouches now -- hanging under it is Z (the upside-down hang)

		// ---- sneak + jump on the ground -> the super leap (v0.6.17) ----
		if (spiderMan && jumpPressed && sneakDown && player.onGround() && !adhered && !swinging
				&& !player.getAbilities().flying && !player.isPassenger()) {
			ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SUPER_JUMP));
			return;
		}

		// ---- sneak while stuck -> let go (v0.16.1: a single press; a double tap is still two presses) ----
		if (climber && sneakPressed) {
			if (adhered && (spiderMan || releaseWindow > 0)) {
				releaseWindow = 0;
				localRelease(player);
				regrabLock = RELEASE_LOCKOUT;
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.CLIMB_RELEASE));
			} else {
				releaseWindow = DOUBLE_TAP_TICKS;
			}
		}
		if (releaseWindow > 0) {
			releaseWindow--;
		}

		// v0.16.1 FIX: a line fired while already against a wall is not "swung into" it -- remember that until he is
		// clear of the wall, so it can never cut the fresh line
		if (!swinging) {
			wallAtLineStart = false;
		} else if (SpiderSwingClient.ticksOnLine(player) <= 1 && player.horizontalCollision) {
			wallAtLineStart = true;
		} else if (!player.horizontalCollision) {
			wallAtLineStart = false;
		}
		// ---- running / flying into a wall sticks to it (v0.16.1, replaces the C toggle) ----
		if (spiderMan && !SpiderClimb.crawlOff(player) && !adhered && !sneakDown && regrabLock == 0 && player.horizontalCollision
				&& !player.getAbilities().flying && !player.isPassenger() && !player.isInWater() && !player.isInLava()
				&& !local(player).lockedOut()) {
			boolean airborne = !player.onGround();
			boolean sprintingIn = player.onGround() && sprintHeld && player.input.up;
			// v0.16.1 FIX: only a swing that has actually carried him into the wall lets go for it -- a line fired while
			// already brushing a wall (jumping beside one, grazing a pillar) was cut the very next tick ("it shoots a web
			// and immediately releases")
			if (swinging && airborne && (wallAtLineStart || SpiderSwingClient.ticksOnLine(player) < SWING_WALL_GRACE)) {
				// fresh line: keep it, do not take the wall
			} else if (swinging && airborne) {
				// swung into a wall: let go of the line and take the wall
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SWING_STOP));
				grab(player);
			} else if (!swinging && (airborne || sprintingIn)) {
				if (sprintingIn) {
					// a little hop off the floor so the climb engine (which never holds a grounded player) takes over
					Vec3 v = player.getDeltaMovement();
					player.setDeltaMovement(v.x, Math.max(v.y, 0.32), v.z);
				}
				grab(player);
			}
		}

		// ---- jump gestures ----
		if (jumpPressed) {
			if (adhered) {
				localLeap(player);
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SURFACE_LEAP));
				return;
			}
			if (swinging) {
				return; // jump reels the line in; the swing handler reads the key itself
			}
			// v0.16.2: a tap just after letting go of a swing is a swing trick (not the double jump / zip boost)
			if (spiderMan && SpiderTrickClient.claimJump(player)) {
				return;
			}
			// v0.16.1: Sprint + Jump in the air is the Web Zip boost; plain Jump the double jump
			if (spiderMan && sprintHeld && !player.onGround() && airTicks >= 2 && !player.getAbilities().flying
					&& !player.isPassenger() && !player.isInWater()
					// v0.16.1: only with a surface to web onto in range -- otherwise this stays a plain double jump
					&& player.pick(com.projecthero.mod.spider.SpiderManConfig.get().moves.zipBoostRange, 1.0f, false)
							.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.ZIP_BOOST));
				SpiderAirMomentumClient.arm(); // v0.16.4: the zip's speed carries (no air drag) until he lands
				return;
			}
			if (spiderMan && doubleJumpAllowed(player)) {
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.DOUBLE_JUMP));
			}
		}
	}

	private static void grab(LocalPlayer player) {
		SpiderClimbLocal l = local(player);
		if (l.grabIntent()) {
			return;
		}
		l.setGrabIntent(true);
		ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.CLIMB_GRAB));
	}

	/** The client-visible half of the double-jump gate. The server re-checks every part of this. */
	private static boolean doubleJumpAllowed(LocalPlayer player) {
		return leftGround
				&& airTicks >= MIN_AIR_TICKS
				&& !player.onGround()
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isPassenger()
				&& !player.isInWater()
				&& !player.isInLava()
				&& !player.onClimbable()
				&& !SpiderClimb.attached(player)
				&& !SpiderSwing.isSwinging(player)
				&& !player.horizontalCollision // step assist over a block edge is not a leap (v0.6.17)
				&& !groundClose(player);
	}

	private static boolean groundClose(LocalPlayer player) {
		AABB swept = player.getBoundingBox().expandTowards(0.0, -GROUND_CLEARANCE, 0.0);
		return !player.level().noCollision(player, swept);
	}

	private static SpiderClimbLocal local(LocalPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL);
	}

	private static void localRelease(LocalPlayer player) {
		SpiderClimbLocal l = local(player);
		l.setGrabIntent(false);
		l.lockOut(6);
	}

	private static void localLeap(LocalPlayer player) {
		SpiderClimbLocal l = local(player);
		l.setGrabIntent(false);
		l.lockOut(LEAP_LOCKOUT);
	}
}
