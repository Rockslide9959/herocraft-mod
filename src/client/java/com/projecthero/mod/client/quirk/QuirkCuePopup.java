package com.projecthero.mod.client.quirk;

import java.util.List;

import com.projecthero.mod.quirk.fx.QuirkCuePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Quirk framework: the HUD cues from {@link QuirkCuePayload}, in the {@code GreenLanternComboPopup} style.
 * <ul>
 * <li><b>Combo</b> -- a small "COMBO" tag and the route's name in bold, in the Quirk's colour, just above the crosshair,
 * with its one-line effect under it (wrapped, never wider than {@link #MAX_W}). It pops in oversized, settles and fades.</li>
 * <li><b>Plus Ultra</b> -- a white-gold flash over the screen, "PLUS ULTRA!" in big gold letters and the ultimate's name
 * under it.</li>
 * </ul>
 */
public final class QuirkCuePopup {
	public static final int MAX_W = 220;
	private static final long COMBO_MS = 2000L;
	private static final long ULT_MS = 2400L;
	private static final long FADE_MS = 450L;

	private static String comboKey;
	private static int comboColour;
	private static long comboAt;
	private static String ultKey;
	private static int ultColour;
	private static long ultAt;

	private QuirkCuePopup() {
	}

	public static void accept(QuirkCuePayload p) {
		if (p.kind() == QuirkCuePayload.COMBO) {
			showCombo(p.key(), p.colour());
		} else if (p.kind() == QuirkCuePayload.ULTIMATE) {
			showUltimate(p.key(), p.colour());
		}
	}

	/** Show a combo now (also used by the screenshot harness). */
	public static void showCombo(String key, int colour) {
		comboKey = key;
		comboColour = colour & 0xFFFFFF;
		comboAt = System.currentTimeMillis();
	}

	public static void showUltimate(String key, int colour) {
		ultKey = key;
		ultColour = colour & 0xFFFFFF;
		ultAt = System.currentTimeMillis();
		QuirkCameraFx.vignette(0xFFE08A, 0.8f, 20);
	}

	private static int argb(int rgb, float a) {
		return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static int lighter(int rgb, float t) {
		int r = (int) Mth.lerp(t, (rgb >> 16) & 255, 255), g = (int) Mth.lerp(t, (rgb >> 8) & 255, 255), b = (int) Mth.lerp(t, rgb & 255, 255);
		return (r << 16) | (g << 8) | b;
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (ultKey != null) {
			if (now - ultAt > ULT_MS) {
				ultKey = null;
			} else {
				ultimate(g, mc, now - ultAt);
			}
		}
		if (comboKey != null) {
			if (now - comboAt > COMBO_MS) {
				comboKey = null;
			} else {
				combo(g, mc, now - comboAt);
			}
		}
	}

	private static void combo(GuiGraphics g, Minecraft mc, long age) {
		float alpha = age > COMBO_MS - FADE_MS ? Math.max(0.04f, (COMBO_MS - age) / (float) FADE_MS) : Math.min(1f, age / 90f + 0.1f);
		float pop = age < 180 ? Mth.lerp(age / 180f, 1.9f, 1.5f) : 1.5f;
		Font font = mc.font;
		Component name = Component.translatable(comboKey).withStyle(s -> s.withBold(true));
		Component tag = Component.translatable("hud.projecthero.quirk.combo");
		int maxW = Math.min(MAX_W, g.guiWidth() - 24);
		List<FormattedCharSequence> nameLines = font.split(name, Math.max(40, (int) (maxW / pop)));
		List<FormattedCharSequence> desc = font.split(Component.translatable(comboKey + ".desc"), maxW);
		int cx = g.guiWidth() / 2;
		int nameH = Math.round(nameLines.size() * 10 * pop);
		int total = 10 + nameH + 2 + desc.size() * 10;
		int top = Math.max(8, g.guiHeight() / 2 - 22 - total);
		if (ultKey != null) {
			top = g.guiHeight() / 2 + 14; // the Plus Ultra banner holds the top half: drop below the crosshair
		}
		int widest = font.width(tag);
		for (FormattedCharSequence l : nameLines) {
			widest = Math.max(widest, Math.round(font.width(l) * pop));
		}
		for (FormattedCharSequence l : desc) {
			widest = Math.max(widest, font.width(l));
		}
		int pale = lighter(comboColour, 0.65f);
		g.fill(cx - widest / 2 - 8, top - 4, cx + widest / 2 + 8, top + total + 2, argb(0x0A0A0A, 0.5f * alpha));
		g.fill(cx - widest / 2 - 8, top - 4, cx + widest / 2 + 8, top - 3, argb(comboColour, 0.8f * alpha));
		g.drawString(font, tag, cx - font.width(tag) / 2, top, argb(pale, alpha), true);
		int y = top + 10;
		for (FormattedCharSequence l : nameLines) {
			g.pose().pushPose();
			g.pose().translate(cx - font.width(l) * pop / 2f, y, 0);
			g.pose().scale(pop, pop, 1f);
			g.drawString(font, l, 0, 0, argb(lighter(comboColour, 0.15f), alpha), true);
			g.pose().popPose();
			y += Math.round(10 * pop);
		}
		y += 2;
		for (FormattedCharSequence l : desc) {
			g.drawString(font, l, cx - font.width(l) / 2, y, argb(pale, alpha * 0.9f), true);
			y += 10;
		}
	}

	private static void ultimate(GuiGraphics g, Minecraft mc, long age) {
		int w = g.guiWidth(), h = g.guiHeight();
		// the flash: white-gold, gone in a third of a second
		if (age < 340) {
			float f = 1f - age / 340f;
			g.fill(0, 0, w, h, argb(0xFFF4D8, 0.35f * f * f));
		}
		float alpha = age > ULT_MS - FADE_MS ? Math.max(0.04f, (ULT_MS - age) / (float) FADE_MS) : Math.min(1f, age / 80f + 0.1f);
		float pop = age < 220 ? Mth.lerp(age / 220f, 4.2f, 3.0f) : 3.0f;
		Font font = mc.font;
		Component big = Component.translatable("hud.projecthero.quirk.plus_ultra").withStyle(s -> s.withBold(true));
		int bigW = Math.round(font.width(big) * pop);
		int cy = h / 4;
		g.pose().pushPose();
		g.pose().translate((w - bigW) / 2f, cy, 0);
		g.pose().scale(pop, pop, 1f);
		g.drawString(font, big, 0, 0, argb(0xFFC83A, alpha), true);
		g.pose().popPose();
		List<FormattedCharSequence> sub = font.split(Component.translatable(ultKey), Math.min(MAX_W, w - 32));
		int sy = cy + Math.round(9 * pop) + 4;
		for (FormattedCharSequence l : sub) {
			g.drawCenteredString(font, l, w / 2, sy, argb(lighter(ultColour, 0.5f), alpha));
			sy += 10;
		}
	}
}
