package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hulk.HulkControl;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.network.HulkActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * The Hulk's client-side controls that are not ability keys.
 * <ul>
 *   <li><b>Hold H</b> (v0.16.4): the press goes out from the H chain ({@link #pressedH}); the release is sent from here, so the
 *       server can call an unfinished hold off.</li>
 *   <li><b>The rampage warning</b> (v0.16.4): while the arrow string shows, every arrow key pressed is sent
 *       ({@code CONTROL}, 1 up .. 4 right) -- the server checks it against the string.</li>
 * </ul>
 * v0.16.4: the calm-down (hold N, the breathing screen) is gone. The rampage view and the ring minigame are
 * {@link HulkRampageClient}.
 */
public final class HulkClient {
	private static final int[] ARROW_KEYS = { GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT };
	private static final boolean[] WAS_DOWN = new boolean[4];
	/** H went down for the Hulk -- its release is sent when it comes back up (it calls off a hold). */
	private static boolean hHeld;
	/** v0.16.4: Sprint held + moving forward as the Hulk, as last told to the server; ticks since the last report. */
	private static boolean pushSent;
	private static int pushAge;

	private HulkClient() {
	}

	/** The H press was sent as a Hulk {@code TRANSFORM}; {@link #tick} reports the release. */
	public static void pressedH() {
		hHeld = true;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			hHeld = false;
			pushSent = false;
			return;
		}
		// ---- H let go (or a screen took the keyboard) -- the server calls off an unfinished hold ----
		if (hHeld && (mc.screen != null || !ModKeyBindings.POWER_SELECT.isDown())) {
			hHeld = false;
			ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.TRANSFORM_RELEASE));
		}
		pollArrows(mc);
		tickPush(mc);
	}

	/**
	 * v0.16.4 (user): "make him able to break soft blocks by just holding the sprint key and moving". Tells the server while
	 * the Hulk holds Sprint and moves forward -- also when a trunk has stopped him and vanilla has dropped his sprint --
	 * re-sent every 10 ticks while it lasts so a lost packet never sticks.
	 */
	private static void tickPush(Minecraft mc) {
		HulkState s = mc.player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		boolean push = s != null && s.hasPower && s.hulk && mc.screen == null && !mc.player.isPassenger()
				&& mc.options.keySprint.isDown() && mc.player.input.forwardImpulse > 0.0f;
		pushAge++;
		if (push != pushSent || push && pushAge >= 10) {
			pushSent = push;
			pushAge = 0;
			ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.PUSH, push ? 1 : 0, 0));
		}
	}

	/**
	 * Sends each fresh arrow-key press while the warning shows. Polled every client tick and every frame (from the HUD), so a
	 * quick tap between two ticks is not lost.
	 */
	public static void pollArrows(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		HulkState s = mc.player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		long window = mc.getWindow().getWindow();
		boolean warning = s != null && s.hasPower && HulkControl.warning(s, mc.level.getGameTime()) && mc.screen == null;
		for (int i = 0; i < ARROW_KEYS.length; i++) {
			boolean down = GLFW.glfwGetKey(window, ARROW_KEYS[i]) == GLFW.GLFW_PRESS;
			if (warning && down && !WAS_DOWN[i]) {
				ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.CONTROL, i + 1, 0));
			}
			WAS_DOWN[i] = down;
		}
	}
}
