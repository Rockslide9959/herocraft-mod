package com.projecthero.mod.client.alarm;

import com.projecthero.mod.alarm.AlarmState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * v0.17.7: the alarm HUD -- a small pixel eye near the top of the screen while you are inside a guarded place: closed
 * and grey when Calm, half open and amber when Suspicious, wide open and flashing red on Alert, with the state's name in
 * small text under it. It sits right of the boss-bar column (or against the right edge on a narrow screen) and its label
 * is shrunk to fit, so it never sprawls.
 */
public final class AlarmHud {
	private static final String[] OPEN = {
			".....#####.....",
			"...##wwwww##...",
			".##www###www##.",
			"#wwww#ooo#wwww#",
			"#www#ooooo#www#",
			"#wwww#ooo#wwww#",
			".##www###www##.",
			"...##wwwww##...",
			".....#####....." };
	private static final String[] HALF = {
			"...............",
			"...............",
			".#############.",
			"#wwww#ooo#wwww#",
			"#www#ooooo#www#",
			"#wwww#ooo#wwww#",
			".##www###www##.",
			"...##wwwww##...",
			".....#####....." };
	private static final String[] SHUT = {
			"...............",
			"...............",
			"...............",
			"...............",
			"#.............#",
			".##.........##.",
			"...#########...",
			"..#..#...#..#..",
			"..............." };
	public static final int W = 15;
	public static final int H = 9;
	private static final float LABEL_SCALE = 0.6f;

	private AlarmHud() {
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		AlarmState s = AlarmClient.state();
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.player == null || mc.options.hideGui) {
			return;
		}
		int sw = g.guiWidth();
		int x = sw / 2 + 100;
		if (x + W + 2 > sw) {
			x = sw - W - 4;
		}
		int y = 3;
		boolean flash = s == AlarmState.ALERT && (mc.player.tickCount / 5) % 2 == 0;
		int outline = switch (s) {
			case CALM -> 0xFF9AA3AD;
			case SUSPICIOUS -> 0xFFE8B030;
			case ALERT -> flash ? 0xFFFF4030 : 0xFFB01810;
		};
		int white = s == AlarmState.ALERT ? 0xFFFFE0D8 : 0xFFF0F0E8;
		int iris = switch (s) {
			case CALM -> 0xFF607080;
			case SUSPICIOUS -> 0xFFC08010;
			case ALERT -> flash ? 0xFFFF2010 : 0xFF901008;
		};
		String[] art = s == AlarmState.ALERT ? OPEN : s == AlarmState.SUSPICIOUS ? HALF : SHUT;
		g.fill(x - 2, y - 1, x + W + 2, y + H + 1, 0x50000000);
		for (int r = 0; r < H; r++) {
			String row = art[r];
			for (int c = 0; c < W; c++) {
				int col = switch (row.charAt(c)) {
					case '#' -> outline;
					case 'w' -> white;
					case 'o' -> iris;
					default -> 0;
				};
				if (col != 0) {
					g.fill(x + c, y + r, x + c + 1, y + r + 1, col);
				}
			}
		}
		Font font = mc.font;
		Component label = Component.translatable(s.key());
		float scale = LABEL_SCALE;
		int max = Math.max(20, sw - x + 2);
		int w = font.width(label);
		if (w * scale > max) {
			scale = max / (float) w; // never wider than the space left
		}
		g.pose().pushPose();
		float lx = x + W / 2f - w * scale / 2f;
		if (lx + w * scale > sw - 1) {
			lx = sw - 1 - w * scale;
		}
		g.pose().translate(lx, y + H + 2, 0);
		g.pose().scale(scale, scale, 1f);
		g.drawString(font, label, 0, 0, outline | 0xFF000000, true);
		g.pose().popPose();
	}
}
