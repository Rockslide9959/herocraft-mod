package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SpiderActionPayload;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderClimbLocal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Watches the vanilla jump and sneak keys for Spider-Man's / Spider Adhesion's movement gestures and
 * sends one edge-triggered packet when one fires. No new keybinding: every gesture is something a
 * player would try anyway, and the server re-checks all of them so these are requests, not orders.
 *
 * <h2>The gestures</h2>
 * <ul>
 *   <li><b>Double-tap sneak while stuck</b> &rarr; let go. Reaching the ground also lets go.</li>
 *   <li><b>Jump while stuck</b> &rarr; shove off the surface.</li>
 *   <li><b>Jump in mid-air</b> (Spider-Man only) &rarr; the second jump. It is deliberately refused
 *       when solid ground is right below or the player only just left it, so tapping jump to hop the
 *       instant you land is read as an ordinary jump rather than eaten as an accidental double jump.</li>
 * </ul>
 *
 * <p>v0.9.3: the old <b>double-tap-jump-against-a-wall grab</b> is gone -- it collided with the
 * double jump above. Wall crawling now engages purely from a mode being on: Spider-Man's slot-6
 * wall-crawl toggle, or Spider Adhesion's Adhesion Mode / Wall Grip toggle. Surface contact in
 * mid-air with that mode on sticks automatically.
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

	private static boolean jumpWasDown;
	private static boolean sneakWasDown;
	private static boolean leftGround;
	private static int airTicks;
	private static int releaseWindow;

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

		// ---- sneak + jump on the ground -> the 6-block super leap (v0.6.17) ----
		if (spiderMan && jumpPressed && sneakDown && player.onGround() && !adhered && !swinging
				&& !player.getAbilities().flying && !player.isPassenger()) {
			ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SUPER_JUMP));
			return;
		}

		// ---- double-tap sneak while stuck -> let go ----
		if (climber && sneakPressed) {
			if (adhered && releaseWindow > 0) {
				releaseWindow = 0;
				localRelease(player);
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.CLIMB_RELEASE));
			} else {
				releaseWindow = DOUBLE_TAP_TICKS;
			}
		}
		if (releaseWindow > 0) {
			releaseWindow--;
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
			// v0.9.3: a mid-air jump near a wall is just the double jump now -- no grab gesture to
			// disambiguate against.
			if (spiderMan && doubleJumpAllowed(player)) {
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.DOUBLE_JUMP));
			}
		}
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
