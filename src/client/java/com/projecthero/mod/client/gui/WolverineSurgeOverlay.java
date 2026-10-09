package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * v0.12.17: a pulsing red shadow around the edges of the screen for the whole Death Surge window (while he is
 * debuffed -- v0.15.18: until his skin is 50% back), fading in as it starts and out as it ends.
 */
public final class WolverineSurgeOverlay {
	private WolverineSurgeOverlay() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		WolverineState s = mc.player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		float now = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		// v0.15.18: the debuffed part of the surge lasts until the skin is SURGE_DEBUFF_UNTIL (50%) back, not a fixed time
		float left = WolverineConfig.SURGE_DEBUFF_UNTIL - s.skinRecovery;
		if (left <= 0.0f) {
			return;
		}
		float elapsed = s.fleshStartedAt > 0L ? Math.max(0.0f, now - s.fleshStartedAt) : 10.0f;
		float fade = Math.min(1.0f, Math.min(elapsed / 10.0f, left / 0.075f)); // fades out over the last ~1.5 s of full-HP recovery
		float pulse = 0.85f + 0.15f * (float) Math.sin(now * 0.35f);
		int alpha = Math.round(170.0f * fade * pulse);
		if (alpha <= 0) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		int strong = (alpha << 24) | 0x8A0000;
		int clear = 0x008A0000;
		int tv = Math.max(24, h / 4);
		int th = Math.max(24, w / 6);
		g.fillGradient(0, 0, w, tv, strong, clear);
		g.fillGradient(0, h - tv, w, h, clear, strong);
		// left / right edges: stepped strips fading toward the centre
		int steps = 24;
		for (int i = 0; i < steps; i++) {
			float k = 1.0f - i / (float) steps;
			int col = (Math.round(alpha * k * k) << 24) | 0x8A0000;
			int x0 = i * th / steps;
			int x1 = (i + 1) * th / steps;
			g.fill(x0, tv / 2, x1, h - tv / 2, col);
			g.fill(w - x1, tv / 2, w - x0, h - tv / 2, col);
		}
	}
}
