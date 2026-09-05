package com.herocraft.mod.client.spider;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.spider.SpiderSwing;
import com.herocraft.mod.spider.SpiderSwingInput;
import com.herocraft.mod.spider.data.SpiderManState;

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

	private SpiderSwingClient() {
	}

	/** Called from {@code LocalPlayerMixin} at the top of {@code aiStep}, before vanilla moves anything. */
	public static void tick(LocalPlayer player) {
		SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (state == null || !state.swinging) {
			wasSwinging = false;
			return;
		}
		Vec3 anchor = new Vec3(state.anchorX, state.anchorY, state.anchorZ);
		double distance = anchor.distanceTo(player.getEyePosition());
		if (!wasSwinging) {
			// A fresh line starts at whatever length the server measured it at.
			ropeLength = state.ropeLength > 0.0 ? state.ropeLength : distance;
			wasSwinging = true;
		}

		int input = SpiderSwingInput.pack(
				player.input.up, player.input.down, player.input.left, player.input.right,
				player.input.jumping, player.input.shiftKeyDown);

		ropeLength = SpiderSwing.adjustRope(ropeLength, distance, input);
		Vec3 velocity = SpiderSwing.applyRope(player, anchor, ropeLength, player.getDeltaMovement(), input,
				state.artificialAnchor, state.airSwingBaselineY);
		player.setDeltaMovement(velocity);
		player.resetFallDistance();
	}

	/** Leaving a world must not carry a stale rope into the next one. */
	public static void reset() {
		ropeLength = 0.0;
		wasSwinging = false;
	}
}
