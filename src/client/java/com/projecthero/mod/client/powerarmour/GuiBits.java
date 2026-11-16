package com.projecthero.mod.client.powerarmour;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** v0.17.5 Power Armour: vanilla-looking menu pieces drawn from fills (panel, inset, slot, bars) and wrapped text. */
final class GuiBits {
	static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, DARK = 0xFF373737, SLOT = 0xFF8B8B8B, TEXT = 0xFF404040;

	private GuiBits() {
	}

	/** The raised grey window. */
	static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
		g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
		g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
		g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
		g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
		g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
	}

	/** A sunken box (preview window, bar frame). */
	static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, DARK);
		g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	/** An 18x18 slot frame whose item cell starts at (x, y). */
	static void slot(GuiGraphics g, int x, int y) {
		inset(g, x - 1, y - 1, 18, 18, SLOT);
	}

	/** {@code text} wrapped to {@code width}; returns the height used. */
	static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int colour, boolean shadow) {
		List<FormattedCharSequence> lines = font.split(text, width);
		for (FormattedCharSequence line : lines) {
			g.drawString(font, line, x, y, colour, shadow);
			y += font.lineHeight;
		}
		return lines.size() * font.lineHeight;
	}

	/** A horizontal bar (0..1) in a sunken frame. */
	static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int colour) {
		inset(g, x, y, w, h, 0xFF1E1E1E);
		int fw = Math.round((w - 2) * Math.max(0f, Math.min(1f, frac)));
		if (fw > 0) {
			g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, colour);
			g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x40FFFFFF);
		}
	}
}
