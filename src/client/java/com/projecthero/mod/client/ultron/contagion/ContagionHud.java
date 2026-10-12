package com.projecthero.mod.client.ultron.contagion;

import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.ultron.contagion.ContagionNet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.16.1: the Contagion HUD (the user's bar names: these are <b>Hairline</b> bars -- 3 px, no border).
 * <ul>
 *   <li>the red <b>Saturation</b> Hairline across the top of the screen with "37% | 3 Nexus | Trace 1/3";</li>
 *   <li>a red <b>OVERRIDE ATTEMPT</b> Hairline over the crosshair while Ultron tries to hijack the suit, "SUIT LOCKED" and a
 *       red edge while it is locked, and a 1 s red static flash now and then inside the corruption;</li>
 *   <li>the <b>capture</b> readout (cyan Hairline, time left, who is inside, the Mind Stone bonus);</li>
 *   <li>the Sokovia Protocol's <b>red diamond</b> on a compass strip, with the distance;</li>
 *   <li>the <b>Vision moment</b>: a gold tint, and for a Mind Stone holder the "hold right-click" prompt and its gold Hairline.</li>
 * </ul>
 * Every line is short and fitted to its width ({@link IronManGui#fit}).
 */
public final class ContagionHud {
	private static ContagionNet.Hud state;
	private static long glitchUntil;
	private static final RandomSource NOISE = RandomSource.create();

	private ContagionHud() {
	}

	public static void accept(ContagionNet.Hud hud) {
		state = hud;
		Minecraft mc = Minecraft.getInstance();
		if (hud.has(ContagionNet.F_GLITCH) && mc.level != null) {
			glitchUntil = mc.level.getGameTime() + 20;
		}
	}

	public static ContagionNet.Hud state() {
		return state;
	}

	public static void clear() {
		state = null;
		glitchUntil = 0;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ContagionNet.Hud s = state;
		if (s == null || !s.has(ContagionNet.F_ACTIVE) || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = mc.level.getGameTime();
		float time = now + delta.getGameTimeDeltaPartialTick(false);

		// --- the gold tint of the Vision moment, the red edge of a locked suit / the corruption
		if (s.has(ContagionNet.F_GOLD)) {
			int a = (int) (40 + 20 * Mth.sin(time * 0.1f));
			g.fill(0, 0, w, h, (a << 24) | 0xE8B830);
		}
		if (s.has(ContagionNet.F_LOCKED)) {
			edge(g, w, h, 0xC0FF1010, 10);
		} else if (s.has(ContagionNet.F_IN_CORRUPTION)) {
			edge(g, w, h, 0x50B00000, 6);
		}
		if (now < glitchUntil) {
			for (int i = 0; i < 26; i++) {
				int y = NOISE.nextInt(h);
				int x = NOISE.nextInt(w);
				g.fill(x, y, Math.min(w, x + 20 + NOISE.nextInt(w / 2)), y + 1 + NOISE.nextInt(2), (0x60 + NOISE.nextInt(0x60) << 24) | 0xFF2010);
			}
		}

		// --- the Saturation Hairline
		int bw = 182;
		int bx = (w - bw) / 2;
		IronManGui.hairline(g, bx, 1, bw, 3, s.saturation() / 100f, 0xFFE01818, 0x80300000);
		for (int t : new int[] { 25, 50, 75 }) {
			g.fill(bx + bw * t / 100, 0, bx + bw * t / 100 + 1, 5, 0xC0FFD0D0);
		}
		String sat = Component.translatable("hud.projecthero.contagion.saturation", Math.round(s.saturation()), s.nexus(), s.traces()).getString();
		int room = Math.max(40, (w - bw) / 2 - 6);
		sat = IronManGui.fit(font, sat, room);
		g.drawString(font, sat, bx + bw + 4, 0, 0xFFFF5050, true);
		if (s.version() > 1) {
			String v = Component.translatable("hud.projecthero.contagion.version", s.version()).getString();
			g.drawString(font, v, bx - 4 - font.width(v), 0, 0xFFFF5050, true);
		}

		// --- the Sokovia marker (compass strip under the top Hairline)
		if (s.has(ContagionNet.F_MARKER)) {
			double dx = s.markerX() + 0.5 - mc.player.getX();
			double dz = s.markerZ() + 0.5 - mc.player.getZ();
			float yawTo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			float diff = Mth.wrapDegrees(yawTo - mc.player.getYRot());
			int cx = w / 2 + Math.round(Mth.clamp(diff / 90f, -1f, 1f) * (bw / 2f));
			int cy = 10;
			diamond(g, cx, cy, 4, Math.abs(diff) <= 90 ? 0xFFFF2020 : 0x90FF2020);
			String dist = Component.translatable("hud.projecthero.contagion.marker", (int) Math.sqrt(dx * dx + dz * dz)).getString();
			g.drawString(font, dist, cx - font.width(dist) / 2, cy + 6, 0xFFFF6060, true);
		}

		int cy = h / 2;
		// --- the hijack attempt
		if (s.hijack() >= 0f) {
			String label = Component.translatable("hud.projecthero.contagion.override_attempt").getString();
			int lw = 120;
			int lx = (w - lw) / 2;
			int col = ((int) (time / 3) % 2 == 0) ? 0xFFFF2020 : 0xFFFF7060;
			g.drawString(font, IronManGui.fit(font, label, lw), (w - Math.min(lw, font.width(label))) / 2, cy - 34, col, true);
			IronManGui.hairline(g, lx, cy - 24, lw, 3, s.hijack(), 0xFFFF2020, 0x80300000);
			String hint = Component.translatable("hud.projecthero.contagion.override_hint").getString();
			hint = IronManGui.fit(font, hint, 200);
			g.drawString(font, hint, (w - font.width(hint)) / 2, cy - 19, 0xFFE0A0A0, true);
		} else if (s.has(ContagionNet.F_LOCKED)) {
			String label = Component.translatable("hud.projecthero.contagion.locked").getString();
			g.drawString(font, label, (w - font.width(label)) / 2, cy - 30, 0xFFFF2020, true);
		}

		// --- the capture
		if (s.capture() >= 0f) {
			int lw = 140;
			int lx = (w - lw) / 2;
			int y = s.has(ContagionNet.F_MARKER) ? 26 : 12;
			String label = Component.translatable(s.capturePlayers() > 0 ? "hud.projecthero.contagion.capture" : "hud.projecthero.contagion.capture_paused",
					Math.round(s.capture() * 100), s.captureSeconds(), s.capturePlayers()).getString();
			label = IronManGui.fit(font, label, 220);
			g.drawString(font, label, (w - font.width(label)) / 2, y, s.capturePlayers() > 0 ? 0xFF60E0FF : 0xFFA0A0A0, true);
			IronManGui.hairline(g, lx, y + 10, lw, 3, s.capture(), 0xFF40D8FF, 0x80102030);
			if (s.has(ContagionNet.F_CAPTURE_MIND)) {
				String mind = Component.translatable("hud.projecthero.contagion.capture_mind").getString();
				g.drawString(font, mind, (w - font.width(mind)) / 2, y + 15, 0xFFF5C542, true);
			}
		}

		// --- the Vision moment prompt
		if (s.has(ContagionNet.F_VISION_PROMPT)) {
			String label = Component.translatable("hud.projecthero.contagion.vision_prompt").getString();
			label = IronManGui.fit(font, label, 240);
			// above the crosshair, clear of the chat box (bottom-left) and the hotbar
			int py = cy - 46;
			g.fill((w - font.width(label)) / 2 - 4, py - 3, (w + font.width(label)) / 2 + 4, py + 15, 0x90201808);
			g.drawString(font, label, (w - font.width(label)) / 2, py, 0xFFFFD050, true);
			IronManGui.hairline(g, (w - 120) / 2, py + 11, 120, 3, Math.max(0f, s.visionHold()), 0xFFFFD050, 0x80403010);
		}
	}

	private static void edge(GuiGraphics g, int w, int h, int argb, int t) {
		g.fillGradient(0, 0, w, t, argb, argb & 0x00FFFFFF);
		g.fillGradient(0, h - t, w, h, argb & 0x00FFFFFF, argb);
		g.fill(0, 0, 2, h, argb);
		g.fill(w - 2, 0, w, h, argb);
	}

	private static void diamond(GuiGraphics g, int cx, int cy, int r, int argb) {
		for (int i = -r; i <= r; i++) {
			int half = r - Math.abs(i);
			g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, argb);
		}
	}
}
