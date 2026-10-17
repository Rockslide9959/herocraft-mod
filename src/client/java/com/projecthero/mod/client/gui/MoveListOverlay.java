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
	private static final int EXTRA_COLOUR = 0xFFB0A8C0;

	/** One key: its label ("R", "Shift+H"...), the plain move and the Shift move (null when the key has none). */
	public record Row(String key, Component plain, @Nullable Component shift, @Nullable Component extra) {
		public static Row of(String key, Component plain, @Nullable Component shift) {
			return new Row(key, plain, shift, null);
		}

		/** v0.16.6: with a third, ready-worded move after the Shift one (Spider-Man's "Hold: Web Pull"). */
		public static Row of(String key, Component plain, @Nullable Component shift, @Nullable Component extra) {
			return new Row(key, plain, shift, extra);
		}
	}

	private MoveListOverlay() {
	}

	/** Set only by the client screenshot harness: the list is drawn as if Left-Alt were held. */
	public static volatile boolean forceHeld;

	/** Left-Alt is down (the list is up). */
	public static boolean held() {
		if (forceHeld) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		return GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
	}

	/** The default letters of the eight ability keys (R G X Z V C, then H and N), as every description spells them. */
	private static final String DEFAULT_LETTERS = "RGXZVCHN";

	/**
	 * The key the player really has bound to ability key {@code index} (0-5 = Ability 1-6, 6 = H / power select,
	 * 7 = N), capped at ten characters.
	 */
	public static String key(int index) {
		net.minecraft.client.KeyMapping km = index < 6 ? com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS[index]
				: index == 6 ? com.projecthero.mod.client.ModKeyBindings.POWER_SELECT
				: com.projecthero.mod.client.ModKeyBindings.MAX_STEEL_TRANSFORM;
		String text = km.getTranslatedKeyMessage().getString();
		return text.length() > 10 ? text.substring(0, 10) : text;
	}

	/** {@link #key(int)} for an ability slot. */
	public static String key(com.projecthero.mod.hero.AbilitySlot slot) {
		return key(slot.ordinal());
	}

	private static final java.util.regex.Pattern SHIFT_IN_DESC = java.util.regex.Pattern.compile(
			"Shift\\s*\\+\\s*([A-Z])(?:\\s*\\([^)]*\\)|\\s+with [a-z ]{1,24}?)?\\s*[:=]\\s*(.{2,40}?)(?:\\s*\\((?:toggle|hold)\\))?\\s*(?:—|--|\\s-\\s|:|\\.|,|\\(|$)");

	/**
	 * v0.16.6: the mutations name their Shift moves only inside the ability descriptions ("... Shift+R: Dive Bomb --
	 * ..."), so the move list reads the Shift move for ability key {@code index} out of the descriptions of every key
	 * of the power. Null when none of them names one (or the language spells it differently).
	 */
	@Nullable
	public static Component shiftFromDescs(int index, List<String> descKeys) {
		if (index < 0 || index >= DEFAULT_LETTERS.length()) {
			return null;
		}
		char letter = DEFAULT_LETTERS.charAt(index);
		net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
		for (String descKey : descKeys) {
			if (!lang.has(descKey)) {
				continue;
			}
			java.util.regex.Matcher m = SHIFT_IN_DESC.matcher(lang.getOrDefault(descKey));
			while (m.find()) {
				if (m.group(1).charAt(0) == letter) {
					String name = m.group(2).trim();
					if (!name.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).startsWith("again")) {
						return Component.literal(name);
					}
				}
			}
		}
		return null;
	}

	private static final java.util.regex.Pattern INLINE_SHIFT = java.util.regex.Pattern.compile("(?:Shift|Sneak)\\s*:\\s*([^,;)]+)");

	/**
	 * v0.16.6: a row for a HUD whose names already spell the Shift move inside them ("Strong Punch  (Shift: Ground
	 * Pound)", "Weapon Wheel (Sneak: Shield)"): the Shift / Sneak part is lifted out into the row's Shift column and the
	 * rest stays the plain move. A name with no such part is the whole plain move.
	 */
	public static Row inline(String key, Component full) {
		String text = full.getString();
		java.util.regex.Matcher m = INLINE_SHIFT.matcher(text);
		if (!m.find()) {
			return Row.of(key, full, null);
		}
		String shift = m.group(1).trim();
		int start = m.start();
		int end = m.end();
		// take the separator with it: "(Shift: X)" whole, ", Shift: X" / "; Shift: X" with its comma
		int before = start;
		while (before > 0 && text.charAt(before - 1) == ' ') {
			before--;
		}
		String plain;
		if (before > 0 && text.charAt(before - 1) == '(' && end < text.length() && text.charAt(end) == ')') {
			plain = text.substring(0, before - 1).stripTrailing() + text.substring(end + 1);
		} else if (before > 0 && (text.charAt(before - 1) == ',' || text.charAt(before - 1) == ';')) {
			plain = text.substring(0, before - 1) + text.substring(end);
		} else if (before > 0 && text.charAt(before - 1) == '/') {
			plain = text.substring(0, before - 1).stripTrailing() + text.substring(end); // "Fist / Shift: Slam"
		} else {
			plain = text.substring(0, start).stripTrailing() + text.substring(end);
		}
		plain = plain.replace("()", "").strip();
		if (plain.isEmpty() || shift.isEmpty()) {
			return Row.of(key, full, null);
		}
		return Row.of(key, Component.literal(plain), Component.literal(shift));
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
			if (r.extra() != null) {
				line.append(Component.literal("   ")).append(r.extra().copy().withStyle(s -> s.withColor(EXTRA_COLOUR)));
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
