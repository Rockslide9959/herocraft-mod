package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "come up with a new HUD design that feels like minecraft built it ... one that can be used for all the powers in
 * the mod, for now just put this new HUD design on the ben 10 aliens"): a power HUD drawn from the game's own pieces, so it reads as
 * part of Minecraft's interface rather than an overlay on it.
 * <pre>
 *   Charge  [boss bar ============-----]  12s     meter rows: a boss-bar sprite in the nearest of its seven colours, shadowed text
 *   +----+----+----+----+----+----+               the key slots: a short hotbar (the real hotbar sprite, 20 px a slot)
 *   | R  | G  | Z  | X  | C  | V  |              the key letter where an item would sit, yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+              cooldown: the item-cooldown white sweep + the seconds like a stack count;
 *                                                the other move's cooldown: a durability bar; a toggle that is on: the selection frame
 * </pre>
 * Powers call {@link #strip}, then {@link #slot} for each key, and {@link #meter} for their rows.
 */
public final class VanillaHud {
	private static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hud/hotbar");
	private static final ResourceLocation SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
	/** One slot's width in the hotbar sprite (its 1-px outer edge aside). */
	public static final int SLOT = 20;
	/** The strip's height (the hotbar sprite's). */
	public static final int HEIGHT = 22;

	/** The text colours of the vanilla chat palette: white, grey, dark grey, yellow, gold, red, green. */
	public static final int WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA, DARK_GREY = 0xFF555555, YELLOW = 0xFFFFFF55, GOLD = 0xFFFFAA00,
			RED = 0xFFFF5555, GREEN = 0xFF55FF55;

	private VanillaHud() {
	}

	/** The width of a strip of {@code slots} key slots. */
	public static int width(int slots) {
		return slots * SLOT + 2;
	}

	/** A short hotbar of {@code slots} slots with its top-left at (x, y): the hotbar sprite cut to length, its right edge kept. */
	public static void strip(GuiGraphics g, int x, int y, int slots) {
		int w = 1 + slots * SLOT;
		g.blitSprite(HOTBAR, 182, 22, 0, 0, x, y, w, HEIGHT);
		g.blitSprite(HOTBAR, 182, 22, 181, 0, x + w, y, 1, HEIGHT);
	}

	/**
	 * Slot {@code i} of a strip at (x, y). {@code label}: the key, centred where an item would sit ({@code usable} false: dark grey,
	 * {@code shifted}: yellow). {@code cooldown} / {@code other}: 0..1 left of the shown move's and the other move's cooldowns,
	 * {@code seconds} the shown move's seconds left; {@code active}: the selection frame (a toggle that is on).
	 */
	public static void slot(GuiGraphics g, int x, int y, int i, String label, boolean usable, boolean shifted, float cooldown, int seconds,
			float other, boolean active) {
		Minecraft mc = Minecraft.getInstance();
		int ix = x + 3 + i * SLOT, iy = y + 3; // the 16x16 item cell
		int colour = !usable ? DARK_GREY : shifted ? YELLOW : WHITE;
		// cooling down: the letter steps up out of the way of the seconds (a stack count's corner) and greys
		boolean cooling = seconds > 0;
		g.drawString(mc.font, label, ix + 8 - mc.font.width(label) / 2 - (cooling ? 3 : 0), cooling ? iy : iy + 4, cooling ? GREY : colour, true);
		if (cooldown > 0f) {
			// the item cooldown: a white sweep that drains from the top
			int top = iy + Mth.floor(16f * (1f - Mth.clamp(cooldown, 0f, 1f)));
			g.fill(RenderType.guiOverlay(), ix, top, ix + 16, iy + 16, Integer.MAX_VALUE);
		}
		if (other > 0f) {
			// the durability bar: the other move's cooldown, red when it has just gone, green as it comes back
			float left = 1f - Mth.clamp(other, 0f, 1f);
			int w = Math.max(1, Math.round(13f * left));
			g.fill(RenderType.guiOverlay(), ix + 2, iy + 13, ix + 15, iy + 15, 0xFF000000);
			g.fill(RenderType.guiOverlay(), ix + 2, iy + 13, ix + 2 + w, iy + 14, 0xFF000000 | Mth.hsvToRgb(left / 3f, 1f, 1f));
		}
		if (seconds > 0) {
			// the seconds left, where a stack count sits
			String n = String.valueOf(seconds);
			g.pose().pushPose();
			g.pose().translate(0f, 0f, 200f);
			g.drawString(mc.font, n, ix + 17 - mc.font.width(n), iy + 9, WHITE, true);
			g.pose().popPose();
		}
		if (active) {
			g.blitSprite(SELECTION, x - 1 + i * SLOT, y - 1, 24, 23);
		}
	}

	/**
	 * A meter row {@code width} wide with its bar's top at {@code by}: the label, a boss bar (in the boss-bar colour nearest to
	 * {@code colour}) and the value right-aligned, all in shadowed text.
	 */
	public static void meter(GuiGraphics g, int x0, int by, int width, Component label, float frac, int colour, String value, int valueColour) {
		meter(g, x0, by, width, label, frac, colour, value, valueColour, MutationMeters.Style.METER);
	}

	/**
	 * v0.17.3: a meter row in one of the user's bar styles, kept on the vanilla look: <b>Meter</b> the plain boss bar,
	 * <b>Gauge</b> the boss bar with its ten notches, <b>Hairline</b> a 3-px cut of the boss bar, <b>Slab</b> the boss bar in a
	 * 1-px black frame (7 px tall).
	 */
	public static void meter(GuiGraphics g, int x0, int by, int width, Component label, float frac, int colour, String value, int valueColour,
			MutationMeters.Style style) {
		Minecraft mc = Minecraft.getInstance();
		int labelW = mc.font.width(label);
		int valueW = Math.max(mc.font.width("0:00"), mc.font.width(value));
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);
		g.drawString(mc.font, label, x0, by - 2, WHITE, true);
		bar(g, barX, by - 1, barW, frac, colour, style);
		g.drawString(mc.font, value, x0 + width - mc.font.width(value), by - 2, valueColour, true);
	}

	/** A bare boss bar (no label) {@code w} wide, its top at {@code y}, in the plain Meter style. */
	public static void bar(GuiGraphics g, int x, int y, int w, float frac, int colour) {
		bar(g, x, y, w, frac, colour, MutationMeters.Style.METER);
	}

	/** A bare boss bar (no label) {@code w} wide, its top at {@code y} (the 5-px bar; a Slab's frame sits 1 px outside it). */
	public static void bar(GuiGraphics g, int x, int y, int w, float frac, int colour, MutationMeters.Style style) {
		String c = bossColour(colour);
		int fill = Math.round(w * Mth.clamp(frac, 0f, 1f));
		ResourceLocation bg = ResourceLocation.withDefaultNamespace("boss_bar/" + c + "_background");
		ResourceLocation fg = ResourceLocation.withDefaultNamespace("boss_bar/" + c + "_progress");
		if (style == MutationMeters.Style.HAIRLINE) {
			// the boss bar's middle three rows
			hairline(g, bg, x, y + 1, w, w);
			if (fill > 0) {
				hairline(g, fg, x, y + 1, w, fill);
			}
			return;
		}
		if (style == MutationMeters.Style.SLAB) {
			g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xFF000000);
		}
		bar(g, bg, x, y, w, w);
		if (fill > 0) {
			bar(g, fg, x, y, w, fill);
		}
		if (style == MutationMeters.Style.GAUGE) {
			bar(g, ResourceLocation.withDefaultNamespace("boss_bar/notched_10_background"), x, y, w, w);
			if (fill > 0) {
				bar(g, ResourceLocation.withDefaultNamespace("boss_bar/notched_10_progress"), x, y, w, fill);
			}
		}
	}

	private static void hairline(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int shown) {
		int body = Math.min(shown, w - 2);
		if (body > 0) {
			g.blitSprite(sprite, 182, 5, 0, 1, x, y, body, 3);
		}
		if (shown > w - 2) {
			g.blitSprite(sprite, 182, 5, 180, 1, x + w - 2, y, shown - (w - 2), 3);
		}
	}

	/** A boss-bar sprite {@code w} wide (its left part and its 2-px right end, so the end caps stay crisp), cut off at {@code shown}. */
	private static void bar(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int shown) {
		int body = Math.min(shown, w - 2);
		if (body > 0) {
			g.blitSprite(sprite, 182, 5, 0, 0, x, y, body, 5);
		}
		if (shown > w - 2) {
			g.blitSprite(sprite, 182, 5, 180, 0, x + w - 2, y, shown - (w - 2), 5);
		}
	}

	/** The boss-bar colour (pink, blue, red, green, yellow, purple, white) nearest to {@code rgb}. */
	static String bossColour(int rgb) {
		float r = ((rgb >> 16) & 255) / 255f, gr = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
		if (max - min < 0.18f) {
			return "white";
		}
		float h;
		if (max == r) {
			h = ((gr - b) / (max - min)) % 6f;
		} else if (max == gr) {
			h = (b - r) / (max - min) + 2f;
		} else {
			h = (r - gr) / (max - min) + 4f;
		}
		h = (h * 60f + 360f) % 360f;
		if (h < 22f || h >= 340f) {
			return "red";
		}
		if (h < 70f) {
			return "yellow";
		}
		if (h < 165f) {
			return "green";
		}
		if (h < 255f) {
			return "blue";
		}
		if (h < 300f) {
			return "purple";
		}
		return "pink";
	}

	// ---------------------------------------------------------------- v0.17.3: the shared layout (every power's HUD)

	/** The key order of the newest powers' strips: R G Z X C V (the keyboard's own Z X C V order). */
	public static final AbilitySlot[] ORDER = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3, AbilitySlot.SLOT_6,
			AbilitySlot.SLOT_5 };

	/** The key bound to {@code slot} (follows rebinds), at most two characters, as the strip shows it. */
	public static String keyLabel(AbilitySlot slot) {
		net.minecraft.client.KeyMapping km = slot.ordinal() < 6 ? ModKeyBindings.ABILITY_SLOTS[slot.ordinal()]
				: slot == AbilitySlot.SLOT_7 ? ModKeyBindings.POWER_SELECT : ModKeyBindings.MAX_STEEL_TRANSFORM;
		String text = km.getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** Seconds shown in a slot for {@code ticks} of cooldown left (0 = none). */
	public static int seconds(int ticks) {
		return ticks > 0 ? (ticks + 19) / 20 : 0;
	}

	/** A coloured status word for {@link Stack#tags}. */
	public record Tag(String text, int colour) {
	}

	/**
	 * v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all the powers"): the placement
	 * the newest powers use, shared so every HUD sits and stacks the same way. Bottom-right, 4 px from the edges (41 px up on a
	 * narrow window, clear of the hotbar and food bar), as wide as a six-key strip; the key strip at the bottom, then meter rows,
	 * the title and status tags stacking upward from it. {@link #top()} is the top of everything drawn so far.
	 * <pre>
	 *   Flying  Shield                     tags()   (wrap to the width, first tags on the top line)
	 *   TITLE                              title()
	 *   Energy [boss bar =====---]  64     meter()  (each 14 px above the last)
	 *   +--+--+--+--+--+--+                keys()   then slot(i, ...) for each key
	 * </pre>
	 */
	public static final class Stack {
		public final GuiGraphics g;
		public final Font font;
		/** Left edge and width of the HUD column. */
		public final int x0, width;
		/** The bottom edge the column stands on. */
		public final int bottom;
		private int stripY = Integer.MIN_VALUE;
		/** Top of the last thing drawn, and what it was (so the next row keeps the newest powers' spacing). */
		private int top;
		private int last; // 0 nothing, 1 strip, 2 meter, 3 text

		private Stack(GuiGraphics g, int x0, int width, int bottom) {
			this.g = g;
			this.font = Minecraft.getInstance().font;
			this.x0 = x0;
			this.width = width;
			this.bottom = bottom;
			this.top = bottom;
		}

		/** The bottom-right column for a strip of up to {@code slots} keys (never narrower than six). */
		public static Stack bottomRight(GuiGraphics g, int slots) {
			int w = VanillaHud.width(Math.max(6, slots));
			int x0 = g.guiWidth() - 4 - w;
			int bottom = g.guiHeight() - 4;
			if (x0 < g.guiWidth() / 2 + 91 + 2) {
				bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
			}
			return new Stack(g, x0, w, bottom);
		}

		/** The key strip ({@code slots} keys) at the bottom of the column; then call {@link #slot} for each key. */
		public Stack keys(int slots) {
			if (slots <= 0) {
				return this;
			}
			stripY = bottom - HEIGHT;
			strip(g, x0, stripY, slots);
			top = stripY;
			last = 1;
			return this;
		}

		/** Key {@code i} of the strip (see {@link VanillaHud#slot}). */
		public Stack slot(int i, String label, boolean usable, boolean shifted, float cooldown, int seconds, float other, boolean active) {
			VanillaHud.slot(g, x0, stripY, i, label, usable, shifted, cooldown, seconds, other, active);
			return this;
		}

		/** Key {@code i} with a hold / charge fill rising from the bottom of its cell (0..1, brighter once full). */
		public Stack hold(int i, float progress) {
			if (progress <= 0f) {
				return this;
			}
			int ix = x0 + 3 + i * SLOT, iy = stripY + 3;
			int h = Math.round(16f * Mth.clamp(progress, 0f, 1f));
			g.fill(RenderType.guiOverlay(), ix, iy + 16 - h, ix + 16, iy + 16, progress >= 1f ? 0x90FFD24A : 0x60FFD24A);
			return this;
		}

		/** The strip's top-left (for a power's own extras on a key). */
		public int stripX() {
			return x0;
		}

		public int stripY() {
			return stripY;
		}

		private int barY() {
			return switch (last) {
				case 0 -> top - 7;
				case 1 -> top - 8;
				case 2 -> top - 12;
				default -> top - 10;
			};
		}

		private int textY() {
			return switch (last) {
				case 0 -> top - 9;
				case 1 -> top - 11;
				case 2 -> top - 12;
				default -> top - 10;
			};
		}

		/** A Meter-style row: label, boss bar, value. */
		public Stack meter(Component label, float frac, int colour, String value, int valueColour) {
			return meter(label, frac, colour, value, valueColour, MutationMeters.Style.METER);
		}

		/** A meter row in one of the user's bar styles (Slab / Meter / Gauge / Hairline). */
		public Stack meter(Component label, float frac, int colour, String value, int valueColour, MutationMeters.Style style) {
			int by = barY();
			VanillaHud.meter(g, x0, by, width, fit(label, value), frac, colour, value, valueColour, style);
			top = by - 2;
			last = 2;
			return this;
		}

		/** A full-width boss bar without a label (a charge-up, a secondary bar). */
		public Stack bar(float frac, int colour) {
			int by = last == 2 ? top - 7 : barY() + 2;
			VanillaHud.bar(g, x0, by, width, frac, colour);
			top = by - 1;
			last = 2;
			return this;
		}

		/** The label cut so the value and a 20-px bar still fit beside it. */
		private Component fit(Component label, String value) {
			int room = width - Math.max(font.width("0:00"), font.width(value)) - 3 - 4 - 20;
			if (font.width(label) <= room) {
				return label;
			}
			return Component.literal(font.plainSubstrByWidth(label.getString(), room - font.width(".")) + ".");
		}

		/** The power's name line (cut to the width). */
		public Stack title(Component text, int colour) {
			return line(text, colour);
		}

		/** A plain text line, cut to the column's width (left aligned, shadowed). */
		public Stack line(Component text, int colour) {
			int y = textY();
			String s = text.getString();
			g.drawString(font, font.width(s) > width ? font.plainSubstrByWidth(s, width) : s, x0, y, colour, true);
			top = y;
			last = 3;
			return this;
		}

		/** A text line, word-wrapped onto as many lines as it needs. */
		public Stack wrapped(Component text, int colour) {
			List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, width);
			for (int i = lines.size() - 1; i >= 0; i--) {
				int y = textY();
				g.drawString(font, lines.get(i), x0, y, colour, true);
				top = y;
				last = 3;
			}
			return this;
		}

		/** Status words, wrapped to the column's width, the first tags on the top line. */
		public Stack tags(List<Tag> tags) {
			if (tags.isEmpty()) {
				return this;
			}
			List<List<Tag>> lines = new ArrayList<>();
			List<Tag> line = new ArrayList<>();
			int w = 0;
			for (Tag t : tags) {
				String text = font.plainSubstrByWidth(t.text(), width);
				int tw = font.width(text);
				if (!line.isEmpty() && w + 6 + tw > width) {
					lines.add(line);
					line = new ArrayList<>();
					w = 0;
				}
				line.add(new Tag(text, t.colour()));
				w += (line.size() > 1 ? 6 : 0) + tw;
			}
			lines.add(line);
			for (int i = lines.size() - 1; i >= 0; i--) {
				int y = textY();
				int x = x0;
				for (Tag t : lines.get(i)) {
					g.drawString(font, t.text(), x, y, t.colour(), true);
					x += font.width(t.text()) + 6;
				}
				top = y;
				last = 3;
			}
			return this;
		}

		/** Leaves {@code px} of room above what is drawn (a power's own row drawn by hand from {@link #top()} - px). */
		public Stack reserve(int px) {
			top -= px;
			last = 3;
			return this;
		}

		/** Top of everything drawn so far. */
		public int top() {
			return top;
		}

		/** The Left-Alt move list, stacked above the column (draws only while Left-Alt is held). */
		public void moveList(List<MoveListOverlay.Row> rows) {
			if (MoveListOverlay.held() && !rows.isEmpty()) {
				MoveListOverlay.draw(g, x0 + width, top - 4, rows);
			}
		}
	}
}
