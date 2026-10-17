package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.16.6 (user: "I like the ability list showing up and having the shift variants show up next to it, do this for all
 * powers and also move the ability list to above the HUD because some of it gets covered up by the players hotbar"):
 * the one move list every power HUD shows while Left-Alt is held. A small panel stacked ABOVE the HUD (above its
 * topmost line), right-aligned to the HUD's right edge, one row per key:
 * <pre>
 *   R  Yo-yo Strike   Shift: Ricochet
 *   G  Yo-yo Bind     Shift: Lasso Fling
 * </pre>
 * Rows wrap to {@link #maxWidth} (TooltipWrap rule: UI text never sprawls) and the panel is clamped to the top of the
 * screen. A HUD calls {@link #held()} and, when true, {@link #draw} with its own rows and the top of its own stack.
 */
public final class MoveListOverlay {
	private static final int PAD = 3;
	private static final int GAP_ABOVE_HUD = 4;
	private static final int LINE = 10;
	private static final int BG = 0xB0000000;
	private static final int BORDER = 0xFF3A3A3A;
	private static final int KEY_COLOUR = 0xFFFFD25A;
	private static final int NAME_COLOUR = 0xFFE6E6E6;
	private static final int SHIFT_COLOUR = 0xFF9FB4C8;

	/** One key: its label ("R", "Shift+H"...), the plain move and the Shift move (null when the key has none). */
	public record Row(String key, Component plain, @Nullable Component shift) {
		public static Row of(String key, Component plain, @Nullable Component shift) {
			return new Row(key, plain, shift);
		}
	}

	private MoveListOverlay() {
	}

	/** Left-Alt is down (the list is up). */
	public static boolean held() {
		Minecraft mc = Minecraft.getInstance();
		return GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
	}

	/** The widest a row may be on this screen. */
	public static int maxWidth(GuiGraphics g) {
		return Math.max(120, Math.min(260, g.guiWidth() / 2 - 8));
	}

	/**
	 * Draws the panel so its bottom sits {@link #GAP_ABOVE_HUD} px above {@code hudTop} and its right edge on
	 * {@code hudRight}. Rows with no names are skipped.
	 */
	public static void draw(GuiGraphics g, int hudRight, int hudTop, List<Row> rows) {
		if (rows.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int maxW = maxWidth(g);
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Row r : rows) {
			if (r == null || r.plain() == null) {
				continue;
			}
			MutableComponent line = Component.literal(r.key() + "  ").withStyle(s -> s.withColor(KEY_COLOUR))
					.append(r.plain().copy().withStyle(s -> s.withColor(NAME_COLOUR)));
			if (r.shift() != null) {
				line.append(Component.literal("   ")).append(Component.translatable("hud.projecthero.move_list.shift", r.shift())
						.withStyle(s -> s.withColor(SHIFT_COLOUR)));
			}
			// a wrapped row keeps its continuation lines indented under the name
			List<FormattedCharSequence> parts = font.split(line, maxW - 2 * PAD);
			lines.addAll(parts);
		}
		if (lines.isEmpty()) {
			return;
		}
		int w = 0;
		for (FormattedCharSequence l : lines) {
			w = Math.max(w, font.width(l));
		}
		int boxW = w + 2 * PAD;
		int boxH = lines.size() * LINE + 2 * PAD - 1;
		int right = Math.min(g.guiWidth() - 2, hudRight);
		int x = Math.max(2, right - boxW);
		int y = Math.max(2, hudTop - GAP_ABOVE_HUD - boxH);
		g.fill(x, y, x + boxW, y + boxH, BG);
		g.renderOutline(x - 1, y - 1, boxW + 2, boxH + 2, BORDER);
		int ly = y + PAD;
		for (FormattedCharSequence l : lines) {
			g.drawString(font, l, x + PAD, ly, NAME_COLOUR, false);
			ly += LINE;
		}
	}

	/** Convenience: the hint shown under a HUD title when the list is hidden ("Alt: moves"), drawn by HUDs that want it. */
	public static Component hint() {
		return Component.translatable("hud.projecthero.move_list.hint").withStyle(ChatFormatting.DARK_GRAY);
	}
}
