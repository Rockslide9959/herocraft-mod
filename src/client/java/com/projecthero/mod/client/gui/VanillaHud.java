package com.projecthero.mod.client.gui;

import net.minecraft.client.Minecraft;
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
		Minecraft mc = Minecraft.getInstance();
		int labelW = mc.font.width(label);
		int valueW = Math.max(mc.font.width("0:00"), mc.font.width(value));
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);
		g.drawString(mc.font, label, x0, by - 2, WHITE, true);
		String c = bossColour(colour);
		bar(g, ResourceLocation.withDefaultNamespace("boss_bar/" + c + "_background"), barX, by - 1, barW, barW);
		int fill = Math.round(barW * Mth.clamp(frac, 0f, 1f));
		if (fill > 0) {
			bar(g, ResourceLocation.withDefaultNamespace("boss_bar/" + c + "_progress"), barX, by - 1, barW, fill);
		}
		g.drawString(mc.font, value, x0 + width - mc.font.width(value), by - 2, valueColour, true);
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
}
