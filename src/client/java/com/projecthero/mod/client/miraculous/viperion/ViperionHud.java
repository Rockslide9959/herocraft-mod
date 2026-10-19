package com.projecthero.mod.client.miraculous.viperion;

import com.projecthero.mod.miraculous.viperion.Viperion;
import com.projecthero.mod.miraculous.viperion.ViperionConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.16.7: Viperion's Second Chance on his HUD -- while a checkpoint is live, a snake-ring marker (a ring of green scales,
 * the head biting the tail, one pip per rewind left) centred above the hotbar, with the window's time left under it
 * ({@code m:ss}, blinking in the last 10 s); and the screen's green flashes: a soft one at the checkpoint, a stronger
 * clock-ripple one at every rewind.
 */
public final class ViperionHud {
	private static final int GREEN = 0xFF3CE0A0;
	private static final int GREEN_D = 0xFF1E7A50;
	private static final int BLACK = 0xFF101214;

	private ViperionHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Viperion.is(mc.player)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		flash(g, now, partial, Viperion.val(mc.player, Viperion.V_CHECKPOINT_AT), 10f, 70);
		flash(g, now, partial, Viperion.val(mc.player, Viperion.V_REWIND_AT), 16f, 130);
		int left = Viperion.checkpointTicksLeft(mc.player);
		if (left <= 0) {
			return;
		}
		int rewinds = Viperion.rewindsLeft(mc.player);
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() - 112;
		int r = 9;
		// the ring of scales
		for (int i = 0; i < 28; i++) {
			double a = Math.PI * 2 * i / 28 - Math.PI / 2;
			int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x - 1, y - 1, x + 1, y + 1, i % 4 < 2 ? GREEN : BLACK);
		}
		// the head at the top, biting the tail
		g.fill(cx - 3, cy - r - 3, cx + 3, cy - r + 2, GREEN);
		g.fill(cx - 2, cy - r - 2, cx - 1, cy - r - 1, 0xFFFFE860);
		g.fill(cx + 1, cy - r - 2, cx + 2, cy - r - 1, 0xFFFFE860);
		// one pip per rewind left, in the middle
		int total = ViperionConfig.SECOND_CHANCE_REWINDS;
		for (int i = 0; i < total; i++) {
			int px = cx - (total - 1) * 3 + i * 6;
			g.fill(px - 2, cy - 2, px + 2, cy + 2, i < rewinds ? GREEN : GREEN_D & 0x60FFFFFF);
		}
		int secs = (left + 19) / 20;
		boolean blink = left <= 200 && (now / 5) % 2 == 0;
		String time = secs / 60 + ":" + String.format("%02d", secs % 60);
		Component label = Component.translatable("hud.projecthero.viperion.second_chance", rewinds, time);
		int w = mc.font.width(label);
		g.drawString(mc.font, label, cx - w / 2, cy + r + 4, blink ? 0xFFFFFFFF : GREEN, true);
	}

	private static void flash(GuiGraphics g, long now, float partial, long at, float len, int maxAlpha) {
		if (at <= 0L) {
			return;
		}
		float age = now - at + partial;
		if (age < 0f || age > len) {
			return;
		}
		float a = 1f - age / len;
		int alpha = (int) (Mth.clamp(a, 0f, 1f) * maxAlpha);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0x40E0A0);
	}
}
