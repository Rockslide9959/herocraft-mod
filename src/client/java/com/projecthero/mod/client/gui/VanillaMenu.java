package com.projecthero.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "change the way the menus look, the I menu, make it look more like minecraft and also make the scroll thing on
 * the side work properly, only apply this new menu style to the aliens and omnitrixes for now please"): the pieces of a menu drawn
 * the way Minecraft draws its own -- the light-grey bevelled container panel (the same 9-sliced texture as the demo / inventory
 * panels), the creative inventory's tabs, recessed slots and its scroller on a recessed track, and dark unshadowed text on the panel
 * like every vanilla container label. Kept power-neutral so other menus can move to it later; only the Omnitrix's I menu
 * ({@link OmnitrixInfoScreen}) uses it for now.
 */
public final class VanillaMenu {
	/** The plain vanilla panel: a 248x166 light-grey bevelled box with 4-px rounded edges (sliced, so any size keeps crisp corners). */
	private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
	private static final int PANEL_W = 248, PANEL_H = 166, EDGE = 4;
	private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
	private static final ResourceLocation SCROLLER_OFF = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
	/** The creative tabs' sprite size; a tab sits {@link #TAB_RISE} above the panel's top and overlaps it by 4 px. */
	public static final int TAB_W = 26, TAB_H = 32, TAB_RISE = 28, TAB_STEP = 27;
	/** The scroll track: the scroller (12 wide) in a 1-px recessed frame. */
	public static final int TRACK_W = 14;
	/** A vanilla slot. */
	public static final int SLOT = 18;

	/** Vanilla container label grey (the inventory's "Crafting", the creative tab's name). */
	public static final int LABEL = 0xFF404040;
	private static final int SLOT_DARK = 0xFF373737, SLOT_FILL = 0xFF8B8B8B, SLOT_LIGHT = 0xFFFFFFFF;

	private VanillaMenu() {
	}

	// ---------------------------------------------------------------- panel, slots, tabs

	/** The vanilla container panel at (x, y), w x h. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		int in = PANEL_W - 2 * EDGE, inH = PANEL_H - 2 * EDGE;
		int mw = w - 2 * EDGE, mh = h - 2 * EDGE;
		// corners
		g.blit(PANEL, x, y, 0, 0, EDGE, EDGE);
		g.blit(PANEL, x + w - EDGE, y, PANEL_W - EDGE, 0, EDGE, EDGE);
		g.blit(PANEL, x, y + h - EDGE, 0, PANEL_H - EDGE, EDGE, EDGE);
		g.blit(PANEL, x + w - EDGE, y + h - EDGE, PANEL_W - EDGE, PANEL_H - EDGE, EDGE, EDGE);
		// edges and the middle, stretched (they are uniform across their length)
		g.blit(PANEL, x + EDGE, y, mw, EDGE, EDGE, 0, in, EDGE, 256, 256);
		g.blit(PANEL, x + EDGE, y + h - EDGE, mw, EDGE, EDGE, PANEL_H - EDGE, in, EDGE, 256, 256);
		g.blit(PANEL, x, y + EDGE, EDGE, mh, 0, EDGE, EDGE, inH, 256, 256);
		g.blit(PANEL, x + w - EDGE, y + EDGE, EDGE, mh, PANEL_W - EDGE, EDGE, EDGE, inH, 256, 256);
		g.blit(PANEL, x + EDGE, y + EDGE, mw, mh, EDGE, EDGE, in, inH, 256, 256);
	}

	/** A recessed box (a slot's bevel: dark top-left, white bottom-right, mid-grey inside) covering (x, y) .. (x + w, y + h). */
	public static void inset(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, SLOT_FILL);
		g.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
		g.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
		g.fill(x + 1, y + h - 1, x + w, y + h, SLOT_LIGHT);
		g.fill(x + w - 1, y + 1, x + w, y + h, SLOT_LIGHT);
	}

	/** An 18x18 slot at (x, y); its item cell is (x + 1, y + 1). */
	public static void slot(GuiGraphics g, int x, int y) {
		inset(g, x, y, SLOT, SLOT);
	}

	/** The slot hover wash over the 16x16 cell (drawn after the icon, like vanilla). */
	public static void slotHighlight(GuiGraphics g, int x, int y) {
		g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF, 0x80FFFFFF, 0);
	}

	/** The picked slot: a white frame just outside the slot, like the advancement / recipe-book selection. */
	public static void slotSelected(GuiGraphics g, int x, int y) {
		int c = 0xFFFFFFFF;
		g.fill(x - 1, y - 1, x + SLOT + 1, y, c);
		g.fill(x - 1, y + SLOT, x + SLOT + 1, y + SLOT + 1, c);
		g.fill(x - 1, y, x, y + SLOT, c);
		g.fill(x + SLOT, y, x + SLOT + 1, y + SLOT, c);
	}

	/**
	 * A creative-inventory top tab, column {@code col} (0 = flush with the panel's left edge), above a panel whose top-left is
	 * (panelX, panelY). Unselected tabs go under the panel (draw before it), the selected one over it (after), as vanilla does.
	 */
	public static void tab(GuiGraphics g, int panelX, int panelY, int col, boolean selected) {
		int n = col == 0 ? 1 : Math.min(6, col + 1);
		g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + n),
				tabX(panelX, col), panelY - TAB_RISE, TAB_W, TAB_H);
	}

	public static int tabX(int panelX, int col) {
		return panelX + col * TAB_STEP;
	}

	/** Where a tab's 16x16 icon sits (as the creative inventory places it). */
	public static int tabIconY(int panelY, boolean selected) {
		return panelY - TAB_RISE + 9 + (selected ? 0 : 1);
	}

	public static boolean overTab(double mx, double my, int panelX, int panelY, int col) {
		int x = tabX(panelX, col), y = panelY - TAB_RISE;
		return mx >= x && mx < x + TAB_W && my >= y && my < panelY;
	}

	// ---------------------------------------------------------------- text on the light panel

	/**
	 * Text recoloured for the light panel: white / light text becomes vanilla's dark label greys and every colour is darkened to a
	 * readable tone of the same hue, so the content's colour coding (gold keys, green passives, red weaknesses, an alien's own
	 * colour) survives. A glyph font (the Omnitrix dial icons) keeps its own colours.
	 */
	public static FormattedCharSequence onPanel(FormattedCharSequence line, ResourceLocation keepFont) {
		return sink -> line.accept((i, style, cp) -> sink.accept(i, darken(style, keepFont), cp));
	}

	private static Style darken(Style s, ResourceLocation keepFont) {
		if (keepFont != null && keepFont.equals(s.getFont())) {
			return s;
		}
		TextColor c = s.getColor();
		return s.withColor(TextColor.fromRgb(dark(c == null ? 0xFFFFFF : c.getValue())));
	}

	/** The panel-readable version of {@code rgb}. */
	public static int dark(int rgb) {
		int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
		if (Math.abs(r - gr) < 12 && Math.abs(gr - b) < 12) {
			// greys: white is the main text (darkest), grey the body, dark grey the hints (lightest)
			int v = Math.max(r, Math.max(gr, b));
			return v >= 0xD0 ? 0x2B2B2B : v >= 0x80 ? 0x4A4A4A : v >= 0x30 ? 0x6B6B6B : 0x000000;
		}
		float luma = (0.299f * r + 0.587f * gr + 0.114f * b) / 255f;
		float k = Math.min(1f, 0.33f / Math.max(0.01f, luma));
		return (Mth.clamp(Math.round(r * k), 0, 255) << 16) | (Mth.clamp(Math.round(gr * k), 0, 255) << 8) | Mth.clamp(Math.round(b * k), 0, 255);
	}

	/**
	 * Word-wraps {@code line} to {@code width} into {@code out}; a line that starts indented keeps its continuation lines under the
	 * same indent (a hanging indent), so a wrapped move description never runs back to the left edge. Never wider than {@code width}.
	 */
	public static void wrap(Font font, Component line, int width, List<FormattedCharSequence> out) {
		String text = line.getString();
		int lead = 0;
		while (lead < text.length() && lead < 8 && text.charAt(lead) == ' ') {
			lead++;
		}
		String indent = " ".repeat(lead);
		int indentW = font.width(indent);
		List<FormattedCharSequence> split = font.split(line, Math.max(40, width - indentW));
		if (split.isEmpty() || text.isEmpty()) {
			out.add(FormattedCharSequence.EMPTY);
			return;
		}
		for (int i = 0; i < split.size(); i++) {
			out.add(i == 0 || lead == 0 ? split.get(i)
					: FormattedCharSequence.composite(FormattedCharSequence.forward(indent, Style.EMPTY), split.get(i)));
		}
	}

	/** Tooltip lines wrapped to the project's tooltip width ({@link TooltipWrap#MAX_WIDTH}). */
	public static List<FormattedCharSequence> tooltip(Font font, List<Component> lines) {
		java.util.ArrayList<FormattedCharSequence> out = new java.util.ArrayList<>();
		for (Component c : lines) {
			out.addAll(font.split(FormattedText.composite(c), TooltipWrap.MAX_WIDTH));
		}
		return out;
	}

	// ---------------------------------------------------------------- the scroll bar

	/**
	 * A vertical scroll bar the way the creative inventory draws it -- the scroller sprite in a recessed track -- that behaves like
	 * a real one: the thumb's length is the visible share of the content (never shorter than the sprite), dragging it moves the
	 * view 1:1 with the mouse, clicking the track above / below the thumb pages up / down, the mouse wheel scrolls three lines, and
	 * the offset is always clamped to 0..max. The owner draws its content at {@code -offset()} inside {@link #view}'s scissor.
	 */
	public static final class Scroll {
		/** The view (content area) and the track, screen coordinates; set by {@link #layout}. */
		private int viewX0, viewY0, viewX1, viewY1, trackX;
		private int content;
		private int offset;
		private boolean dragging;
		private double grab;
		private final int wheelStep, snap;

		/**
		 * {@code snap}: offsets land on multiples of it (a text line), so no half-cut line sits at the top of the view -- the way
		 * the creative scroller snaps to whole rows.
		 */
		public Scroll(int wheelStep, int snap) {
			this.wheelStep = wheelStep;
			this.snap = Math.max(1, snap);
		}

		/** The view rectangle and the track's left x (the track spans the view's height). */
		public void layout(int x0, int y0, int x1, int y1, int trackX) {
			this.viewX0 = x0;
			this.viewY0 = y0;
			this.viewX1 = x1;
			this.viewY1 = y1;
			this.trackX = trackX;
			clamp();
		}

		/** The content's full height in pixels (re-clamps, so a shorter rebuild never leaves the view past the end). */
		public void content(int height) {
			this.content = Math.max(0, height);
			clamp();
		}

		public int viewHeight() {
			return viewY1 - viewY0;
		}

		public int max() {
			return Math.max(0, content - viewHeight());
		}

		public int offset() {
			return offset;
		}

		public void set(int px) {
			offset = px;
			clamp();
		}

		public void by(int px) {
			set(offset + px);
		}

		/** 0..1 of the way down. */
		public void setFraction(float f) {
			set(Math.round(max() * Mth.clamp(f, 0f, 1f)));
		}

		private void clamp() {
			int max = max();
			int snapped = Math.round(offset / (float) snap) * snap;
			// the very end may not be on a step: let it be reached exactly
			offset = offset >= max ? max : Mth.clamp(snapped, 0, max);
		}

		public boolean inView(double mx, double my) {
			return mx >= viewX0 && mx < viewX1 && my >= viewY0 && my < viewY1;
		}

		private int innerTop() {
			return viewY0 + 1;
		}

		private int innerH() {
			return viewHeight() - 2;
		}

		private int thumbH() {
			int inner = innerH();
			if (content <= 0 || max() == 0) {
				return inner;
			}
			return Mth.clamp(Math.round(inner * (viewHeight() / (float) content)), 15, inner);
		}

		private int thumbY() {
			int travel = innerH() - thumbH();
			return innerTop() + (max() == 0 ? 0 : Math.round(travel * (offset / (float) max())));
		}

		private boolean overTrack(double mx, double my) {
			return mx >= trackX && mx < trackX + trackW && my >= viewY0 && my < viewY1;
		}

		private double shown;
		private long lastFrame;

		/**
		 * v0.17.3: the offset to draw at this frame, eased toward the real one (a smooth-scrolling list); exact while the thumb is
		 * dragged. Call once per frame.
		 */
		public double eased() {
			long now = System.nanoTime();
			double dt = lastFrame == 0 ? 0.016 : Math.min(0.1, (now - lastFrame) / 1.0e9);
			lastFrame = now;
			if (dragging) {
				shown = offset;
			} else {
				shown += (offset - shown) * (1 - Math.exp(-dt * 16));
				if (Math.abs(offset - shown) < 0.3) {
					shown = offset;
				}
			}
			shown = Mth.clamp(shown, 0, max());
			return shown;
		}

		/** The eased offset as last computed by {@link #eased} (for hit tests between frames). */
		public double shown() {
			return shown;
		}

		/** Sets the offset with no easing (a new page / list). */
		public void jump(int px) {
			set(px);
			shown = offset;
		}

		/** The track's hit width (the creative track's 14 px, or the glass bar's grab zone). */
		private int trackW = TRACK_W;

		/**
		 * v0.17.3: the see-through I menu's slim bar -- a 3-px line on a faint track, drawn centred in a {@code hitW}-px grab zone
		 * that starts at the layout's track x (so it is easy to catch with the mouse).
		 */
		public Scroll glass(int hitW) {
			this.trackW = hitW;
			return this;
		}

		/** Tests / harnesses: the bar's grab zone (x, width) and the thumb's top and height, screen pixels. */
		public int[] barGeometry() {
			return new int[] { trackX, trackW, thumbY(), thumbH() };
		}

		/** Over the content or the bar: where the mouse wheel scrolls this view. */
		public boolean over(double mx, double my) {
			return inView(mx, my) || overTrack(mx, my);
		}

		/** The wheel, only while the mouse is over this view or its bar (for screens with several scroll areas). */
		public boolean mouseScrolled(double mx, double my, double dy) {
			return over(mx, my) && mouseScrolled(dy);
		}

		/** Arrow keys (two lines), Page Up / Down, Home / End; false for any other key. */
		public boolean keyPressed(int key, int line) {
			int page = Math.max(line, viewHeight() - line);
			switch (key) {
				case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> by(line * 2);
				case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> by(-line * 2);
				case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> by(page);
				case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> by(-page);
				case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> set(Integer.MAX_VALUE / 2);
				case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> set(0);
				default -> {
					return false;
				}
			}
			return true;
		}

		/** The glass bar: faint track, the thumb in {@code thumb} (brighter while hovered or dragged); nothing when it all fits. */
		public void renderGlass(GuiGraphics g, int thumb, double mx, double my) {
			if (max() == 0) {
				return;
			}
			int x = trackX + (trackW - 3) / 2;
			g.fill(x, viewY0, x + 3, viewY1, 0x40FFFFFF);
			int ty = thumbY() - 1, th = thumbH() + 2;
			boolean hot = dragging || (overTrack(mx, my) && my >= ty && my < ty + th);
			int col = hot ? (thumb | 0xFF000000) : (thumb & 0x00FFFFFF) | 0xC0000000;
			g.fill(x, Math.max(viewY0, ty), x + 3, Math.min(viewY1, ty + th), col);
		}

		public boolean mouseClicked(double mx, double my, int button) {
			if (button != 0 || !overTrack(mx, my) || max() == 0) {
				return false;
			}
			int ty = thumbY();
			if (my >= ty && my < ty + thumbH()) {
				dragging = true;
				grab = my - ty;
			} else {
				// page toward the click, like a desktop scroll bar
				by((my < ty ? -1 : 1) * Math.max(10, viewHeight() - 10));
			}
			return true;
		}

		public boolean mouseDragged(double mx, double my, int button) {
			if (!dragging || button != 0) {
				return false;
			}
			int travel = innerH() - thumbH();
			if (travel > 0) {
				double top = my - grab - innerTop();
				set((int) Math.round(top / travel * max()));
			}
			return true;
		}

		public boolean mouseReleased(int button) {
			if (button == 0 && dragging) {
				dragging = false;
				return true;
			}
			return false;
		}

		public boolean mouseScrolled(double dy) {
			if (dy == 0) {
				return false;
			}
			by((int) Math.round(-dy * wheelStep));
			return true;
		}

		public boolean dragging() {
			return dragging;
		}

		/** The recessed track and the scroller (the disabled sprite when everything fits), stretched to the thumb's length. */
		public void render(GuiGraphics g) {
			inset(g, trackX, viewY0, trackW, viewHeight());
			boolean on = max() > 0;
			int h = on ? thumbH() : 15;
			int y = on ? thumbY() : innerTop();
			int x = trackX + 1;
			ResourceLocation s = on ? SCROLLER : SCROLLER_OFF;
			// 3-slice the 12x15 sprite: its two top rows, its 2-row grip pattern repeated, its bottom row
			g.blitSprite(s, 12, 15, 0, 0, x, y, 12, 2);
			int mid = h - 3;
			for (int yy = 0; yy < mid; yy += 2) {
				int rows = Math.min(2, mid - yy);
				// a full tile is the grip line (row 2) and the plain row under it (row 3); a lone last row is the plain one
				g.blitSprite(s, 12, 15, 0, rows == 2 ? 2 : 3, x, y + 2 + yy, 12, rows);
			}
			g.blitSprite(s, 12, 15, 0, 14, x, y + h - 1, 12, 1);
		}

		/** Scissor to the view while drawing the content. */
		public void beginClip(GuiGraphics g) {
			g.enableScissor(viewX0, viewY0, viewX1, viewY1);
		}

		public void endClip(GuiGraphics g) {
			g.disableScissor();
		}

		public int viewX0() {
			return viewX0;
		}

		public int viewY0() {
			return viewY0;
		}

		public int viewY1() {
			return viewY1;
		}

		public int viewX1() {
			return viewX1;
		}
	}
}
