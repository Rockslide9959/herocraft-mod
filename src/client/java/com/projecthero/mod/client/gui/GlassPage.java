package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.17.3 (user: "I really like ... the see-through looking I menu. Is it possible to apply this to all the powers, but make the
 * scroll wheels actually work in the menus"): the see-through I menu every power's info screen now shares -- the world showing
 * through a faint dark panel, the title over it, a gold heading and light text, and a slim gold scroll bar that works like a real
 * one ({@link VanillaMenu.Scroll}: the wheel anywhere over the page or the bar, drag the thumb, click the track to page, arrow
 * keys / Page Up / Page Down / Home / End). The page is clipped to its view and its lines are wrapped to the panel's width.
 * <pre>
 *                 Your Power                 title
 *   +----------------------------------------+
 *   | Heading                                |  gold
 *   | text text text text text text text   # |  light text, clipped; the bar: 3 px, thumb sized to the page
 *   | text text text text text text text   | |
 *   +----------------------------------------+
 *               [      Done      ]
 * </pre>
 */
public final class GlassPage {
	public static final int LINE_H = 11;
	/** The panel wash (the same faint dark the newest powers' I menu always had). */
	public static final int PANEL = 0x50000000;
	public static final int HEADING = 0xFFFFE08A;
	public static final int TEXT = 0xFFD8D8E4;
	public static final int THUMB = 0xFFFFD25A;

	private final List<FormattedCharSequence> body = new ArrayList<>();
	private final VanillaMenu.Scroll scroll = new VanillaMenu.Scroll(LINE_H * 3, 1).glass(9);
	private int left, right, top, bottom;

	/** Lays the page out for a screen {@code width} x {@code height} (call from init). */
	public void layout(int width, int height) {
		this.left = Math.max(20, width / 2 - 180);
		this.right = Math.min(width - 20, width / 2 + 180);
		this.top = 40;
		this.bottom = height - 40;
		scroll.layout(left - 6, top + 12, right, bottom, right + 1);
		scroll.content(contentHeight());
	}

	/** The width a line may take. */
	public int wrapWidth() {
		return right - left;
	}

	public void clear() {
		body.clear();
	}

	public List<FormattedCharSequence> lines() {
		return body;
	}

	public void add(FormattedCharSequence line) {
		body.add(line);
	}

	/** Word-wraps {@code line} onto the page (hanging indent kept). */
	public void wrap(Font font, Component line) {
		VanillaMenu.wrap(font, line, wrapWidth(), body);
	}

	/** Call after (re)building the lines: re-clamps the kept scroll position to the new length. */
	public void done() {
		scroll.content(contentHeight());
	}

	private int contentHeight() {
		return body.size() * LINE_H + 4;
	}

	/** The F1 state from before the page opened (null = not hidden by the page). */
	private Boolean hudWas;

	/**
	 * While an I menu is open the HUD (hotbar, hearts, every power's corner HUD) is put away like F1 does, so only the world shows
	 * through the see-through panel and no HUD text runs under the page. Call from init (safe to call again); {@link #restoreHud}
	 * from the screen's removed().
	 */
	public void hideHud() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (hudWas == null) {
			hudWas = mc.options.hideGui;
			mc.options.hideGui = true;
		}
	}

	public void restoreHud() {
		if (hudWas != null) {
			net.minecraft.client.Minecraft.getInstance().options.hideGui = hudWas;
			hudWas = null;
		}
	}

	public VanillaMenu.Scroll scroll() {
		return scroll;
	}

	/** The panel, the heading and the clipped page with its bar (after the screen's own background and widgets). */
	public void render(GuiGraphics g, Font font, Component heading, int mx, int my) {
		g.fill(left - 8, top - 6, right + 8, bottom + 6, PANEL);
		// the heading cut to the panel (TooltipWrap rule: no text runs past it)
		g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(heading, right - left)), left, top - 2,
				HEADING);
		scroll.beginClip(g);
		int y = top + 12 + 1 - scroll.offset();
		for (FormattedCharSequence line : body) {
			if (y + LINE_H > top + 12 && y < bottom) {
				g.drawString(font, line, left, y, TEXT, false);
			}
			y += LINE_H;
		}
		scroll.endClip(g);
		scroll.renderGlass(g, THUMB, mx, my);
	}

	// ---------------------------------------------------------------- input (screens forward to these first)

	public boolean mouseClicked(double mx, double my, int button) {
		return scroll.mouseClicked(mx, my, button);
	}

	public boolean mouseDragged(double mx, double my, int button) {
		return scroll.mouseDragged(mx, my, button);
	}

	public boolean mouseReleased(int button) {
		return scroll.mouseReleased(button);
	}

	/** The wheel scrolls the page from anywhere on the screen (it is the only scrolling thing on it). */
	public boolean mouseScrolled(double dy) {
		return scroll.mouseScrolled(dy);
	}

	public boolean keyPressed(int key) {
		return scroll.keyPressed(key, LINE_H);
	}

	/** Scrolls to the first line containing {@code text} (case-insensitive); false if none. */
	public boolean scrollTo(String text) {
		String want = text.toLowerCase(java.util.Locale.ROOT);
		for (int i = 0; i < body.size(); i++) {
			StringBuilder sb = new StringBuilder();
			body.get(i).accept((idx, style, cp) -> {
				sb.appendCodePoint(cp);
				return true;
			});
			if (sb.toString().toLowerCase(java.util.Locale.ROOT).contains(want)) {
				scroll.set(i * LINE_H);
				return true;
			}
		}
		return false;
	}
}
