package com.projecthero.mod.client.greenlantern.recon;

import java.util.List;
import java.util.Locale;

import com.projecthero.mod.client.gui.GreenLanternIcons;
import com.projecthero.mod.greenlantern.recon.GreenLanternWaypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Construct Menu's "ring interface" look for the Waypoints screen and the gateway picker -- a dark emerald
 * panel with glowing edges, bright corner brackets, the Lantern Corps symbol faint behind it and a slow scan line, plus a
 * hard-light button and the text helpers (every line wraps to the panel, nothing sprawls).
 */
final class ReconUi {
	static final int GREEN = 0xFF35F075;
	static final int PALE = 0xFFA8FFC0;
	static final int MINT = 0xFFD8FFE2;
	static final int DIM = 0xFF6FB884;
	static final int TEXT = 0xFFB8DCC4;
	static final int GREY = 0xFF6E7A72;
	static final int GOLD = 0xFFE8E070;
	static final int RED = 0xFFFF6A6A;

	private ReconUi() {
	}

	static void backdrop(GuiGraphics g, int w, int h) {
		g.fillGradient(0, 0, w, h, 0x78000804, 0xB0000502);
	}

	/** The panel: outer glow, gradient body, faint emblem, scan line, edges and corner brackets. */
	static void panel(GuiGraphics g, int x, int y, int w, int h, long now) {
		float pulse = 0.5f + 0.5f * Mth.sin(now / 520f);
		for (int i = 1; i <= 4; i++) {
			int a = (int) ((0x34 - i * 7) * (0.7f + 0.3f * pulse));
			g.renderOutline(x - i, y - i, w + i * 2, h + i * 2, (a << 24) | 0x35F075);
		}
		g.fillGradient(x, y, x + w, y + h, 0xEE071C0F, 0xF4020A05);
		int es = Math.max(32, Math.min(w, h) * 8 / 10);
		int ea = (int) (0x12 + 0x08 * pulse);
		GreenLanternIcons.draw(g, GreenLanternIcons.EMBLEM, x + (w - es) / 2, y + (h - es) / 2, es, es, GreenLanternIcons.EMBLEM_SIZE,
				(ea << 24) | 0x5CFF90);
		int sy = y + 2 + (int) ((now / 22L) % Math.max(1, h - 4));
		g.fillGradient(x + 1, Math.max(y + 1, sy - 10), x + w - 1, sy, 0x0035F075, 0x0E35F075);
		g.fill(x + 1, sy, x + w - 1, sy + 1, 0x1835F075);
		int edgeA = (int) (0xA0 + 0x5F * pulse);
		g.renderOutline(x, y, w, h, (edgeA << 24) | 0x35F075);
		g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0x2235F075);
		corner(g, x, y, 10, 1, 1);
		corner(g, x + w - 1, y, 10, -1, 1);
		corner(g, x, y + h - 1, 10, 1, -1);
		corner(g, x + w - 1, y + h - 1, 10, -1, -1);
	}

	private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy) {
		int x0 = dx > 0 ? x : x - len + 1;
		int y0 = dy > 0 ? y : y - len + 1;
		g.fill(x0, y, x0 + len, y + 1, MINT);
		g.fill(x, y0, x + 1, y0 + len, MINT);
	}

	/** A horizontal line brightest in the middle, fading out to both ends. */
	static void glowLine(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha) {
		int w = x1 - x0;
		int steps = 8;
		for (int i = 0; i < steps; i++) {
			float t = (i + 0.5f) / steps;
			int a = (int) (alpha * (1f - Math.abs(t - 0.5f) * 1.7f));
			if (a <= 0) {
				continue;
			}
			int sx = x0 + w * i / steps;
			int ex = x0 + w * (i + 1) / steps;
			g.fill(sx, y, ex, y + 1, (a << 24) | rgb);
		}
	}

	static void glow(GuiGraphics g, int x, int y, int w, int h, int argb) {
		int a = argb >>> 24;
		int rgb = argb & 0xFFFFFF;
		g.renderOutline(x - 1, y - 1, w + 2, h + 2, (a << 24) | rgb);
		g.renderOutline(x - 2, y - 2, w + 4, h + 4, ((a / 2) << 24) | rgb);
	}

	/** Draws {@code c} wrapped to {@code maxW}, at most {@code maxLines} lines; returns the height used. */
	static int wrapped(GuiGraphics g, Font font, Component c, int x, int y, int maxW, int color, int maxLines) {
		List<FormattedCharSequence> lines = font.split(c, Math.max(20, maxW));
		int n = Math.min(lines.size(), maxLines);
		for (int i = 0; i < n; i++) {
			g.drawString(font, lines.get(i), x, y + i * 10, color, false);
		}
		return n * 10;
	}

	/** {@code s} cut to {@code maxW} px with an ellipsis. */
	static String fit(Font font, String s, int maxW) {
		if (font.width(s) <= maxW) {
			return s;
		}
		String dots = "...";
		return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width(dots))) + dots;
	}

	/** "minecraft:the_nether" -> "The Nether". */
	static String dimensionName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder b = new StringBuilder();
		for (String part : path.split("[_/]")) {
			if (part.isEmpty()) {
				continue;
			}
			if (b.length() > 0) {
				b.append(' ');
			}
			b.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
		}
		return b.toString();
	}

	static String coords(GreenLanternWaypoints.Waypoint w) {
		return Mth.floor(w.x()) + ", " + Mth.floor(w.y()) + ", " + Mth.floor(w.z());
	}

	static void click(SoundEvent s, float pitch, float vol) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
	}

	/** A small hard-light button: dark green face, glowing edge on hover, greyed when inactive. */
	static final class LightButton extends AbstractButton {
		private final Runnable action;
		private final int accent;

		LightButton(int x, int y, int w, int h, Component label, int accent, Runnable action) {
			super(x, y, w, h, label);
			this.action = action;
			this.accent = accent;
		}

		@Override
		public void onPress() {
			action.run();
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
			boolean hot = this.active && this.isHoveredOrFocused();
			int face = !this.active ? 0xC0101A14 : hot ? 0xE0124A26 : 0xD00A2614;
			g.fill(getX(), getY(), getX() + width, getY() + height, face);
			int edge = !this.active ? 0x606E7A72 : hot ? 0xFF000000 | (accent & 0xFFFFFF) : 0x90000000 | (accent & 0xFFFFFF);
			g.renderOutline(getX(), getY(), width, height, edge);
			if (hot) {
				glow(g, getX(), getY(), width, height, 0x5035F075);
			}
			Font font = Minecraft.getInstance().font;
			String s = fit(font, getMessage().getString(), width - 6);
			int color = !this.active ? GREY : hot ? MINT : (0xFF000000 | accent);
			g.drawString(font, s, getX() + (width - font.width(s)) / 2, getY() + (height - 8) / 2, color, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			this.defaultButtonNarrationText(out);
		}
	}
}
