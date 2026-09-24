package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * v0.12.17: a pulsing red shadow around the edges of the screen for the whole Death Surge window (the 10
 * seconds he is debuffed), fading in as it starts and out as it ends.
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
		float left = s.emergencyHealUntil - now;
		if (left <= 0.0f || left > WolverineConfig.EMERGENCY_HEAL_TICKS) {
			return;
		}
		float elapsed = WolverineConfig.EMERGENCY_HEAL_TICKS - left;
		float fade = Math.min(1.0f, Math.min(elapsed / 10.0f, left / 30.0f));
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
