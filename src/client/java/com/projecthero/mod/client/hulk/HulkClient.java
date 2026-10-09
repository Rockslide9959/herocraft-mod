package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkControl;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.network.HulkActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * v0.13.14: the Hulk's client-side controls that are not ability keys.
 * <ul>
 *   <li><b>Hold N</b> for 2 s (Utility 2) to ask to calm down; the server checks the 3-second out-of-combat rule and
 *       flags {@link HulkState.Combat#calming}, which opens {@link HulkCalmScreen}.</li>
 *   <li><b>Keep control</b>: while a prompt is showing, the first movement key pressed (forward / left / back / right) is
 *       sent as the answer.</li>
 * </ul>
 */
public final class HulkClient {
	private static int calmHold;
	private static boolean calmSent;
	private static boolean calmScreenOpened;
	private static final boolean[] WAS_DOWN = new boolean[5];
	private static int answeredPrompt = -1;
	private static long answeredUntil;
	/** v0.15.18: H went down for the Hulk -- its release is sent when it comes back up (it calls off a strain). */
	private static boolean hHeld;

	private HulkClient() {
	}

	/** v0.15.18: the H press was sent as a Hulk {@code TRANSFORM}; {@link #tick} reports the release. */
	public static void pressedH() {
		hHeld = true;
	}

	/** 0..1 while N is being held toward a calm-down (for the HUD). */
	public static float calmHoldProgress() {
		return calmSent ? 0.0f : Math.min(1.0f, calmHold / (float) Math.max(1, HulkConfig.calm().holdTicks));
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			calmHold = 0;
			calmSent = false;
			calmScreenOpened = false;
			hHeld = false;
			return;
		}
		// ---- v0.15.18: H let go (or a screen took the keyboard) -- the server calls off an unfinished strain ----
		if (hHeld && (mc.screen != null || !ModKeyBindings.POWER_SELECT.isDown())) {
			hHeld = false;
			ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.TRANSFORM_RELEASE));
		}
		HulkState s = mc.player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower) {
			calmHold = 0;
			return;
		}
		long now = mc.level.getGameTime();

		// ---- hold N to calm down ----
		boolean nDown = mc.screen == null && ModKeyBindings.MAX_STEEL_TRANSFORM.isDown();
		if (nDown && !s.combat.calming && !s.rampaging(now)) {
			calmHold++;
			if (!calmSent && calmHold >= HulkConfig.calm().holdTicks) {
				calmSent = true;
				ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.CALM_START));
			}
		} else if (!nDown) {
			calmHold = 0;
			calmSent = false;
		}
		// the server said yes: open the breathing exercise once per session
		if (s.combat.calming && !calmScreenOpened && mc.screen == null) {
			calmScreenOpened = true;
			mc.setScreen(new HulkCalmScreen());
		} else if (!s.combat.calming) {
			calmScreenOpened = false;
		}

		// ---- keep control: answer the prompt with the matching movement key ----
		boolean[] down = { false, mc.options.keyUp.isDown(), mc.options.keyLeft.isDown(), mc.options.keyDown.isDown(),
				mc.options.keyRight.isDown() };
		boolean prompt = s.hulk && s.combat.promptKey != 0 && now <= s.combat.promptUntil && !HulkControl.rampaging(mc.player);
		if (prompt && mc.screen == null && !(answeredPrompt == s.combat.promptKey && now < answeredUntil)) {
			for (int k = 1; k <= 4; k++) {
				if (down[k] && !WAS_DOWN[k]) {
					ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.CONTROL, k, 0));
					answeredPrompt = s.combat.promptKey;
					answeredUntil = s.combat.promptUntil;
					break;
				}
			}
		}
		System.arraycopy(down, 0, WAS_DOWN, 0, 5);
	}
}
