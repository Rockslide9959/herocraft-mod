package com.projecthero.mod.ironman.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.21: the pure (no client classes) layout + formatting rules behind every Iron Man screen and the helmet
 * HUD, kept in the common source set so gametests can check that everything fits a 320 x 240 scaled screen (GUI
 * scale 4 / a small window) without a client. The client screens call these same functions, so a test that
 * passes here is a statement about the real layout.
 *
 * <p>Text width: the client measures with the real {@code Font}; the tests use {@link #approxWidth}, a
 * deliberately pessimistic copy of the vanilla default font's glyph advances (unknown glyphs count as 9 px).
 */
public final class IronManUiLayout {
	/** The smallest scaled screen every Iron Man UI must fit: GUI scale 4 on a 1280 x 960 window. */
	public static final int MIN_W = 320;
	public static final int MIN_H = 240;

	private IronManUiLayout() {
	}

	/** A screen-space rectangle. */
	public record Rect(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}

		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		public boolean insideScreen(int sw, int sh) {
			return x >= 0 && y >= 0 && x + w <= sw && y + h <= sh;
		}

		public boolean overlaps(Rect o) {
			return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
		}
	}

	// ------------------------------------------------------------------ text

	/** Pessimistic vanilla-font width of {@code s} (advance incl. the 1 px spacing); {@code bold} adds 1 px a glyph. */
	public static int approxWidth(String s, boolean bold) {
		int w = 0;
		for (int i = 0; i < s.length(); i++) {
			w += advance(s.charAt(i)) + (bold ? 1 : 0);
		}
		return w;
	}

	public static int approxWidth(String s) {
		return approxWidth(s, false);
	}

	private static int advance(char c) {
		switch (c) {
			case ' ':
				return 4;
			case 'i': case '!': case '.': case ',': case ':': case ';': case '\'': case '|':
				return 2;
			case 'l': case '`':
				return 3;
			case 'I': case 't': case '[': case ']': case '"':
				return 4;
			case 'f': case 'k': case '(': case ')': case '{': case '}': case '<': case '>': case '*':
				return 5;
			case '@': case '~':
				return 7;
			default:
				return c < 128 ? 6 : 9;
		}
	}

	/** {@code s} cut to fit {@code maxW} px with a trailing "..." (unchanged if it already fits). */
	public static String ellipsize(String s, int maxW, ToIntFunction<String> width) {
		if (width.applyAsInt(s) <= maxW) {
			return s;
		}
		String dots = "...";
		int dotsW = width.applyAsInt(dots);
		int end = s.length();
		while (end > 0 && width.applyAsInt(s.substring(0, end)) + dotsW > maxW) {
			end--;
		}
		return end <= 0 ? (dotsW <= maxW ? dots : "") : s.substring(0, end).stripTrailing() + dots;
	}

	// ------------------------------------------------------------------ number formats (the HUD's house style)

	/** "76,67%" -- two decimals with a comma separator, as the HUD has always shown it. */
	public static String pct(float frac) {
		return String.format(Locale.ROOT, "%.2f", clamp01(frac) * 100f).replace('.', ',') + "%";
	}

	/** "(7667 / 10000)" -- the HUD's real current / max pair. */
	public static String amount(float current, float max) {
		return "(" + exact(current, max) + ")";
	}

	/** "7667 / 10000". */
	public static String exact(float current, float max) {
		return Math.round(current) + " / " + Math.round(max);
	}

	/** "4,5s" -- seconds with one decimal, comma separator (matches {@link #pct}). */
	public static String secs(long ticks) {
		return String.format(Locale.ROOT, "%.1f", Math.max(0L, ticks) / 20.0).replace('.', ',') + "s";
	}

	/** "02:13" (minutes:seconds), rounding partial seconds up. */
	public static String mmss(long ticks) {
		long s = (Math.max(0L, ticks) + 19) / 20;
		return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
	}

	/** Whole seconds left, rounded up -- the number drawn over a cooling ability slot. */
	public static int ceilSecs(long ticks) {
		return (int) ((Math.max(0L, ticks) + 19) / 20);
	}

	public static float clamp01(float f) {
		return Math.max(0f, Math.min(1f, f));
	}

	// ------------------------------------------------------------------ helmet HUD

	/** Top-left block anchor and width. */
	public static final int HUD_X = 6;
	public static final int HUD_Y = 6;
	public static final int HUD_W = 150;
	/** One Gauge row: a 9 px label/value line over a 3 px bar, 2 px gap. */
	public static final int GAUGE_ROW_H = 15;

	/** Ability strip slot size and gap. */
	public static final int SLOT_W = 26;
	public static final int SLOT_H = 22;
	public static final int SLOT_GAP = 3;
	/** The strip's bottom edge sits this far above the screen bottom: clear of hotbar, XP bar, hearts and armour. */
	public static final int STRIP_BOTTOM_MARGIN = 52;

	/** The six ability-strip slots (R G X Z V C), centred above the hotbar. */
	public static Rect[] abilityStrip(int screenW, int screenH) {
		Rect[] out = new Rect[6];
		int total = 6 * SLOT_W + 5 * SLOT_GAP;
		int x0 = screenW / 2 - total / 2;
		int y0 = screenH - STRIP_BOTTOM_MARGIN - SLOT_H;
		for (int i = 0; i < 6; i++) {
			out[i] = new Rect(x0 + i * (SLOT_W + SLOT_GAP), y0, SLOT_W, SLOT_H);
		}
		return out;
	}

	/**
	 * Lays out status chips of the given widths left to right from ({@code x}, {@code y}), wrapping to a new row
	 * whenever the next chip would pass {@code maxW}. A chip wider than {@code maxW} gets a row of its own,
	 * clamped to {@code maxW} (its text is ellipsized by the caller).
	 */
	public static List<Rect> flowChips(int[] widths, int x, int y, int maxW, int gap, int rowH) {
		List<Rect> out = new ArrayList<>();
		int cx = x;
		int cy = y;
		for (int w : widths) {
			int cw = Math.min(w, maxW);
			if (cx > x && cx + cw > x + maxW) {
				cx = x;
				cy += rowH + gap;
			}
			out.add(new Rect(cx, cy, cw, rowH));
			cx += cw + gap;
		}
		return out;
	}

	/**
	 * Client-side cooldown bookkeeping for the sweep: the server only syncs a ready-at tick, so the total length
	 * of a cooldown is taken as what was left the first time a new ready-at value was seen. Returns the sweep
	 * fraction 0..1 still to go (1 = just started).
	 */
	public static float cooldownFraction(long readyAt, long firstSeenRemaining, long now) {
		long left = readyAt - now;
		if (left <= 0 || firstSeenRemaining <= 0) {
			return 0f;
		}
		return clamp01(left / (float) firstSeenRemaining);
	}

	// ------------------------------------------------------------------ suit-call card grid

	public static final int CARD_W = 150;
	public static final int CARD_H = 72;
	public static final int CARD_GAP = 4;
	public static final int GRID_MARGIN = 6;
	/** Grid viewport top (below the title) and bottom reserve (hint line at h-35, cancel button at h-22..h-6). */
	public static final int GRID_TOP = 24;
	public static final int GRID_BOTTOM_RESERVE = 40;

	public static int gridColumns(int screenW) {
		int avail = screenW - 2 * GRID_MARGIN;
		return Math.max(1, Math.min(4, (avail + CARD_GAP) / (CARD_W + CARD_GAP)));
	}

	public static int gridWidth(int cols) {
		return cols * CARD_W + (cols - 1) * CARD_GAP;
	}

	public static int gridRows(int count, int cols) {
		return (count + cols - 1) / Math.max(1, cols);
	}

	public static int gridContentHeight(int count, int cols) {
		int rows = gridRows(count, cols);
		return rows <= 0 ? 0 : rows * CARD_H + (rows - 1) * CARD_GAP;
	}

	public static int gridViewportHeight(int screenH) {
		return Math.max(CARD_H, screenH - GRID_TOP - GRID_BOTTOM_RESERVE);
	}

	public static int gridMaxScroll(int count, int screenW, int screenH) {
		return Math.max(0, gridContentHeight(count, gridColumns(screenW)) - gridViewportHeight(screenH));
	}

	/** Card {@code index} in screen space at the given scroll offset (may be partly outside the viewport). */
	public static Rect card(int index, int screenW, int scroll) {
		int cols = gridColumns(screenW);
		int x0 = screenW / 2 - gridWidth(cols) / 2;
		int col = index % cols;
		int row = index / cols;
		return new Rect(x0 + col * (CARD_W + CARD_GAP), GRID_TOP + row * (CARD_H + CARD_GAP) - scroll, CARD_W, CARD_H);
	}

	/** The scroll offset that brings card {@code index} fully into view (keyboard navigation). */
	public static int scrollToShow(int index, int screenW, int screenH, int scroll, int count) {
		Rect r = card(index, screenW, 0);
		int top = r.y() - GRID_TOP;
		int view = gridViewportHeight(screenH);
		int s = scroll;
		if (top < s) {
			s = top;
		} else if (top + CARD_H > s + view) {
			s = top + CARD_H - view;
		}
		return Math.max(0, Math.min(gridMaxScroll(count, screenW, screenH), s));
	}

	/** Card info column (right of the 3D preview), relative to the card's left edge. */
	public static final int CARD_PREVIEW_W = 40;
	public static final int CARD_INFO_X = CARD_PREVIEW_W + 6;
	public static final int CARD_INFO_W = CARD_W - CARD_INFO_X - 4;

	/** Lang key for where a call option's suit is (0 platform, 1 inventory, anything newer = on its way). */
	public static String locationKey(int source) {
		return switch (source) {
			case 0 -> "screen.projecthero.suit_call.on_platform";
			case 1 -> "screen.projecthero.suit_call.in_inventory";
			default -> "screen.projecthero.suit_call.pending";
		};
	}

	// ------------------------------------------------------------------ blank-blueprint progression track

	public static final int NODE = 24;

	/**
	 * Node rectangles for an {@code n}-mark track, centred on a single row: spacing shrinks with the screen but
	 * never below {@code NODE + 14}, and the track always stays inside a {@code screenW}-wide screen.
	 */
	public static Rect[] trackNodes(int n, int screenW, int centreY) {
		Rect[] out = new Rect[n];
		if (n == 0) {
			return out;
		}
		int spacing = trackSpacing(n, screenW);
		int total = (n - 1) * spacing;
		int x0 = screenW / 2 - total / 2;
		for (int i = 0; i < n; i++) {
			int cx = x0 + i * spacing;
			out[i] = new Rect(cx - NODE / 2, centreY - NODE / 2, NODE, NODE);
		}
		return out;
	}

	public static int trackSpacing(int n, int screenW) {
		if (n <= 1) {
			return 0;
		}
		int avail = Math.min(screenW, 420) - 2 * 18 - NODE;
		return Math.max(NODE + 14, Math.min(64, avail / (n - 1)));
	}

	/** Labels alternate below / above the line, so each label may use up to two node spacings. */
	public static int trackLabelMaxWidth(int n, int screenW) {
		return n <= 1 ? 120 : Math.min(120, 2 * trackSpacing(n, screenW) - 6);
	}

	// ------------------------------------------------------------------ weapon wheel

	/**
	 * Which of {@code n} sectors the offset ({@code dx}, {@code dy}) from the wheel centre points into, or -1 inside
	 * {@code deadZone}. Sector 0 is centred straight up, increasing clockwise; the half-sector offset ("changes 17"
	 * fix) makes each sector's hit wedge the wedge drawn around its centre angle.
	 */
	public static int wheelSector(double dx, double dy, int n, double deadZone) {
		if (n <= 0 || Math.hypot(dx, dy) <= deadZone) {
			return -1;
		}
		double ang = Math.atan2(dy, dx);
		double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
		return (int) (norm / (Math.PI * 2) * n) % n;
	}

	/** Centre angle (radians, screen space: 0 = right, +y down) of sector {@code i}. */
	public static double sectorCentre(int i, int n) {
		return -Math.PI / 2 + i * (Math.PI * 2 / n);
	}

	/** Wheel ring thickness: room for a 16 px icon and one label line. */
	public static final int WHEEL_RING = 34;

	/** Wheel ring radii that fit the screen (title above, hint below): {inner, outer}. */
	public static int[] wheelRadii(int screenW, int screenH) {
		int outer = Math.max(70, Math.min(100, Math.min(screenW, screenH) / 2 - 28));
		return new int[] { outer - WHEEL_RING, outer };
	}

	/** Usable text width inside the centre disc (radius {@code inner - 6}) for lines within 16 px of the centre. */
	public static int wheelTextWidth(int inner) {
		double r = inner - 6;
		return (int) Math.floor(2 * Math.sqrt(Math.max(0, r * r - 16 * 16))) - 6;
	}

	/** How many lines a greedy word wrap of {@code s} at {@code maxW} takes (a single over-long word counts once). */
	public static int wrapLines(String s, int maxW, ToIntFunction<String> width) {
		int lines = 0;
		String cur = "";
		for (String word : s.trim().split(" +")) {
			String next = cur.isEmpty() ? word : cur + " " + word;
			if (!cur.isEmpty() && width.applyAsInt(next) > maxW) {
				lines++;
				cur = word;
			} else {
				cur = next;
			}
		}
		return cur.isEmpty() ? lines : lines + 1;
	}

	// ------------------------------------------------------------------ Stark Fabricator (200 x 236, panel-relative)

	public static final int FAB_W = 200;
	public static final int FAB_H = 236;
	public static final int FAB_TAB_X = 8;
	public static final int FAB_TAB_Y = 92;
	public static final int FAB_TAB_W = 44;
	public static final int FAB_TAB_H = 18;
	public static final int FAB_TAB_GAP = 2;
	public static final int FAB_BOX_X = 7;
	public static final int FAB_BOX_Y = 112;
	public static final int FAB_BOX_W = 186;
	public static final int FAB_BOX_H = 40;
	public static final int FAB_MORE_W = 58;
	public static final int FAB_CHECK_PER_ROW = 4;
	public static final int FAB_CHECK_CELL_W = 44;
	public static final int FAB_CHECK_ROW_H = 9;
	public static final int FAB_CHECK_TOP = FAB_BOX_Y + 12;
	public static final int FAB_PROGRESS_Y = 75;
	public static final int FAB_STATUS_Y = 82;
	/** Player inventory rows / hotbar (moved up from 164 / 224 in v0.14.21 so the panel fits 240 high). */
	public static final int FAB_INV_Y = 156;
	public static final int FAB_HOTBAR_Y = 214;
	/** Every slot the Stark Fabricator menu places (item top-left), for overlap checks: 9 inputs, blueprint, output. */
	public static final int[][] FAB_SLOTS = {
			{ 44, 17 }, { 62, 17 }, { 80, 17 }, { 44, 35 }, { 62, 35 }, { 80, 35 }, { 44, 53 }, { 62, 53 }, { 80, 53 },
			{ 12, 35 }, { 120, 35 } };

	public static Rect fabTab(int i) {
		return new Rect(FAB_TAB_X + i * (FAB_TAB_W + FAB_TAB_GAP), FAB_TAB_Y, FAB_TAB_W, FAB_TAB_H);
	}

	// ------------------------------------------------------------------ Suit Platform (176 x 202, panel-relative)

	public static final int PLAT_W = 176;
	public static final int PLAT_H = 202;
	public static final int PLAT_METER_X = 52;
	public static final int PLAT_METER_W = 118;

	// ------------------------------------------------------------------ Stark Fabricator checklist

	/** How many of {@code item} the nine input slots hold between them (the Fabricator's own matching rule). */
	public static int have(List<ItemStack> inputs, Item item) {
		int found = 0;
		for (int i = 0; i < inputs.size() && i < 9; i++) {
			ItemStack s = inputs.get(i);
			if (s.is(item)) {
				found += s.getCount();
			}
		}
		return found;
	}

	/** Checklist entry cells inside the Fabricator's checklist box: {@code perRow} across, {@code cellW} wide. */
	public static Rect checklistCell(int index, int boxX, int boxY, int perRow, int cellW, int rowH) {
		return new Rect(boxX + (index % perRow) * cellW, boxY + (index / perRow) * rowH, cellW, rowH);
	}
}
