package com.projecthero.mod.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.hero.guide.HeroPackGuide.Category;
import com.projecthero.mod.hero.guide.HeroPackGuide.IndexEntry;
import com.projecthero.mod.hero.guide.HeroPackGuide.SearchHit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Guidebook (v0.16.7 remake; user: "make it look nicer and have more sub menus like marvel, dc, quirks, miraculous
 * ... add a search bar as well").
 *
 * <ul>
 * <li><b>Home</b>: a framed panel of category tiles ({@link Category}: Marvel, DC, Quirks, Miraculous, ...), each with
 * its item icon, colour, chapter count and a one-line description, plus "Continue reading".</li>
 * <li><b>Category</b>: a smooth-scrolling list of its chapters (icon, title, the chapter's first line as a subtitle)
 * under sub-headings.</li>
 * <li><b>Chapter</b>: the chapter laid out as numbered book pages (two side by side when there is room), turned with
 * the wheel, the arrow keys or the Prev / Next links; the last page links the next chapter of the category.</li>
 * <li><b>Search</b>: the box at the top of every page. Typing anywhere starts a search across every chapter's title,
 * section headings and body text (case-insensitive, {@link HeroPackGuide#search}); each result shows its chapter, the
 * section it is in and the matching text with the match highlighted. Clicking one opens the chapter at that line and
 * flashes it. Esc leaves the box (a second Esc closes the book); while the box has focus no key does anything else.</li>
 * </ul>
 * A breadcrumb (Guidebook > Marvel > Iron Man) and a back arrow sit in the header; Backspace / right-click go back.
 * All text wraps or is cut to fit, so it reads at every GUI scale. Content comes from {@link HeroPackGuide} only.
 */
public final class HeroPackGuideScreen extends Screen {
	private enum View {
		HOME, CATEGORY, CHAPTER, SEARCH
	}

	/** One place in the book, for Back and for reopening where you left off. */
	private record Nav(View view, Category category, int chapter, int spread, double scroll) {
	}

	// palette (the mod's HUD / overlay style: dark glass, grey rules, gold accents)
	private static final int PANEL_BG = 0xE8101218;
	private static final int PANEL_EDGE = 0xFF3A3A3A;
	private static final int PANEL_EDGE_HI = 0xFF5A5A5A;
	private static final int HEADER_BG = 0xF0181B24;
	private static final int ROW_BG = 0x60202430;
	private static final int ROW_HOVER = 0xA0303648;
	private static final int GOLD = 0xFFFFD25A;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int TEXT_DIM = 0xFF9A9AA8;
	private static final int TEXT_FAINT = 0xFF6A6A78;
	private static final int MATCH_BG = 0xB0806010;
	private static final int MATCH_FG = 0xFFFFF0A0;

	private static final int LINE_H = 10;
	private static final int HEADER_H = 24;
	private static final int FOOTER_H = 16;
	private static final int ROW_H = 22;
	private static final int SUB_H = 15;
	private static final int RESULT_H = 24;
	private static final int TITLE_BAND = 22;

	// v0.12.25: the book reopens where you left it (for the whole game session)
	private static Nav saved = new Nav(View.HOME, Category.GETTING_STARTED, -1, 0, 0);
	private static int lastChapterRead = -1;

	private View view = View.HOME;
	private Category category = Category.GETTING_STARTED;
	private int chapter = -1;
	private int spread = 0;
	private final Deque<Nav> history = new ArrayDeque<>();

	// list scrolling (category + search), eased toward the target each frame
	private double scrollTarget = 0;
	private double scrollPos = 0;
	private long lastFrameNanos = 0;

	// search
	private EditBox searchBox;
	private String query = "";
	private List<SearchHit> hits = List.of();

	// chapter layout
	private final List<FormattedCharSequence> rows = new ArrayList<>();
	private final List<Integer> rowLine = new ArrayList<>();
	private final List<int[]> pages = new ArrayList<>();
	private int columns = 1;
	private int colW = 100;
	private int flashLine = -1;
	private long flashUntil = 0;
	private int pendingLine = -1;

	// panel geometry
	private int px, py, pw, ph;
	private int bx, by, bw, bh;

	private final Map<String, ItemStack> iconCache = new HashMap<>();

	public HeroPackGuideScreen() {
		super(Component.translatable("screen.projecthero.guide"));
		Nav s = saved;
		this.view = s.view() == View.SEARCH ? View.HOME : s.view();
		this.category = s.category();
		this.chapter = s.chapter();
		this.spread = s.spread();
		this.scrollTarget = this.scrollPos = s.scroll();
		if (this.view == View.CHAPTER && (chapter < 0 || chapter >= HeroPackGuide.chapters().size())) {
			this.view = View.HOME;
		}
	}

	@Override
	public void removed() {
		if (view != View.SEARCH) {
			saved = here();
		} else if (!history.isEmpty()) {
			Nav before = history.peek();
			saved = before.view() == View.SEARCH ? new Nav(View.HOME, category, -1, 0, 0) : before;
		}
		super.removed();
	}

	private Nav here() {
		return new Nav(view, category, chapter, spread, scrollTarget);
	}

	@Override
	protected void init() {
		this.pw = Math.min(this.width - 12, 620);
		this.ph = Math.min(this.height - 12, 380);
		this.px = (this.width - pw) / 2;
		this.py = (this.height - ph) / 2;
		this.bx = px + 8;
		this.by = py + HEADER_H + 6;
		this.bw = pw - 16;
		this.bh = ph - HEADER_H - 6 - FOOTER_H - 4;

		int sw = Math.max(70, Math.min(150, pw / 3));
		searchBox = new EditBox(this.font, px + pw - sw - 6, py + 5, sw, 14, Component.translatable("projecthero.guide.ui.search"));
		searchBox.setHint(Component.translatable("projecthero.guide.ui.search").withStyle(s -> s.withColor(TEXT_FAINT & 0xFFFFFF)));
		searchBox.setMaxLength(40);
		searchBox.setValue(query);
		searchBox.setResponder(this::onQuery);
		addRenderableWidget(searchBox);

		if (view == View.CHAPTER) {
			layoutChapter();
		}
		clampScroll();
	}

	// ---------------------------------------------------------------- navigation

	private void go(View v, Category c, int ch, int line) {
		history.push(here());
		this.view = v;
		this.category = c;
		this.chapter = ch;
		this.spread = 0;
		this.scrollTarget = this.scrollPos = 0;
		if (v == View.CHAPTER) {
			lastChapterRead = ch;
			pendingLine = line;
			layoutChapter();
		}
	}

	private void openCategory(Category c) {
		go(View.CATEGORY, c, -1, -1);
	}

	private void openChapter(int ch, int line) {
		go(View.CHAPTER, HeroPackGuide.categoryOf(ch), ch, line);
	}

	private void goHome() {
		history.clear();
		view = View.HOME;
		scrollTarget = scrollPos = 0;
		if (!query.isEmpty()) {
			query = "";
			searchBox.setValue("");
		}
	}

	private void back() {
		if (view == View.SEARCH) {
			searchBox.setValue(""); // onQuery pops back
			return;
		}
		if (history.isEmpty()) {
			if (view != View.HOME) {
				view = view == View.CHAPTER ? View.CATEGORY : View.HOME;
				scrollTarget = scrollPos = 0;
			}
			return;
		}
		Nav n = history.pop();
		view = n.view();
		category = n.category();
		chapter = n.chapter();
		spread = n.spread();
		scrollTarget = scrollPos = n.scroll();
		if (view == View.SEARCH && query.isEmpty()) {
			back();
			return;
		}
		if (view == View.CHAPTER) {
			layoutChapter();
		}
	}

	private void onQuery(String value) {
		String q = value == null ? "" : value;
		boolean was = !query.isBlank();
		query = q;
		if (q.isBlank()) {
			if (view == View.SEARCH) {
				back();
			}
			hits = List.of();
			return;
		}
		hits = HeroPackGuide.search(q);
		if (!was || view != View.SEARCH) {
			if (view != View.SEARCH) {
				history.push(here());
			}
			view = View.SEARCH;
		}
		scrollTarget = scrollPos = 0;
	}

	// ---------------------------------------------------------------- chapter layout

	private void layoutChapter() {
		rows.clear();
		rowLine.clear();
		pages.clear();
		if (chapter < 0 || chapter >= HeroPackGuide.chapters().size()) {
			return;
		}
		int gap = 14;
		columns = bw >= 380 ? 2 : 1;
		colW = (bw - gap * (columns - 1)) / columns - 4;
		List<Component> lines = HeroPackGuide.chapters().get(chapter).lines();
		for (int i = 0; i < lines.size(); i++) {
			List<FormattedCharSequence> split = this.font.split(lines.get(i), colW);
			if (split.isEmpty()) {
				rows.add(FormattedCharSequence.EMPTY);
				rowLine.add(i);
			} else {
				for (FormattedCharSequence s : split) {
					rows.add(s);
					rowLine.add(i);
				}
			}
		}
		int perPage = Math.max(4, (bh - TITLE_BAND - 12) / LINE_H);
		int r = 0;
		while (r < rows.size()) {
			while (r < rows.size() && isBlankRow(r)) {
				r++; // no page starts on an empty line
			}
			if (r >= rows.size()) {
				break;
			}
			int end = Math.min(rows.size(), r + perPage);
			// v0.17.1: a forced page break (HeroPackGuide.pageBreak) ends the page early -- the Quirk chapters' own pages
			for (int b = r + 1; b < end; b++) {
				if (HeroPackGuide.isPageBreak(lines.get(rowLine.get(b)))) {
					end = b;
					break;
				}
			}
			// don't leave a section heading (or a key row) alone at the foot of a page
			if (end < rows.size() && end - r > 3) {
				int last = end - 1;
				Component src = lines.get(rowLine.get(last));
				boolean firstRowOfLine = last == 0 || !rowLine.get(last - 1).equals(rowLine.get(last));
				if (firstRowOfLine && HeroPackGuide.isSectionLine(src)) {
					end = last;
				}
			}
			pages.add(new int[] { r, end });
			r = end;
		}
		if (pages.isEmpty()) {
			pages.add(new int[] { 0, 0 });
		}
		if (pendingLine >= 0) {
			int row = rowLine.indexOf(pendingLine);
			if (row >= 0) {
				for (int p = 0; p < pages.size(); p++) {
					if (row < pages.get(p)[1] || p == pages.size() - 1) {
						spread = p / columns;
						break;
					}
				}
				flashLine = pendingLine;
				flashUntil = System.currentTimeMillis() + 2500;
			}
			pendingLine = -1;
		}
		spread = Math.max(0, Math.min(spread, spreadCount() - 1));
	}

	private boolean isBlankRow(int r) {
		return HeroPackGuide.chapters().get(chapter).lines().get(rowLine.get(r)).getString().isBlank();
	}

	private int spreadCount() {
		return Math.max(1, (pages.size() + columns - 1) / columns);
	}

	private int nextChapterInCategory() {
		List<Integer> list = HeroPackGuide.chaptersIn(HeroPackGuide.categoryOf(chapter));
		int at = list.indexOf(chapter);
		return at >= 0 && at + 1 < list.size() ? list.get(at + 1) : -1;
	}

	private int prevChapterInCategory() {
		List<Integer> list = HeroPackGuide.chaptersIn(HeroPackGuide.categoryOf(chapter));
		int at = list.indexOf(chapter);
		return at > 0 ? list.get(at - 1) : -1;
	}

	private void turn(int dir) {
		int next = spread + dir;
		if (next < 0) {
			return;
		}
		if (next >= spreadCount()) {
			return;
		}
		spread = next;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (searchBox.isFocused()) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				searchBox.setFocused(false);
				setFocused(null);
				return true;
			}
			if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && view == View.SEARCH && !hits.isEmpty()) {
				SearchHit h = hits.get(0);
				searchBox.setFocused(false);
				setFocused(null);
				openChapter(h.chapter(), h.line());
				return true;
			}
			searchBox.keyPressed(key, scan, mods);
			return true; // nothing else reacts while typing
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (view != View.HOME) {
					back();
					return true;
				}
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> {
				if (view == View.CHAPTER) {
					turn(-1);
					return true;
				}
			}
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> {
				if (view == View.CHAPTER) {
					turn(1);
					return true;
				}
			}
			case GLFW.GLFW_KEY_UP -> {
				if (view == View.CATEGORY || view == View.SEARCH) {
					scrollTarget -= 30;
					clampScroll();
					return true;
				}
			}
			case GLFW.GLFW_KEY_DOWN -> {
				if (view == View.CATEGORY || view == View.SEARCH) {
					scrollTarget += 30;
					clampScroll();
					return true;
				}
			}
			case GLFW.GLFW_KEY_F -> {
				if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
					focusSearch();
					return true;
				}
			}
			default -> {
			}
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			return super.keyPressed(key, scan, mods);
		}
		return true; // swallow everything else: no keybind fires from inside the book
	}

	@Override
	public boolean charTyped(char c, int mods) {
		if (!searchBox.isFocused()) {
			if (Character.isISOControl(c) || (c == ' ' && query.isEmpty())) {
				return true;
			}
			focusSearch(); // typing anywhere searches
		}
		return searchBox.charTyped(c, mods);
	}

	private void focusSearch() {
		setFocused(searchBox);
		searchBox.setFocused(true);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (searchBox.isMouseOver(mx, my)) {
			focusSearch();
			return searchBox.mouseClicked(mx, my, button);
		}
		if (searchBox.isFocused()) {
			searchBox.setFocused(false);
			setFocused(null);
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == 3) {
			back();
			return true;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		// header: back arrow and breadcrumbs
		if (view != View.HOME && hit(mx, my, px + 6, py + 5, 14, 14)) {
			back();
			return true;
		}
		for (Crumb c : crumbs()) {
			if (c.action != null && hit(mx, my, c.x, py + 5, c.w, 14)) {
				c.action.run();
				return true;
			}
		}
		switch (view) {
			case HOME -> {
				Category c = tileAt(mx, my);
				if (c != null) {
					openCategory(c);
					return true;
				}
				if (lastChapterRead >= 0 && continueRowHit(mx, my)) {
					openChapter(lastChapterRead, -1);
					return true;
				}
			}
			case CATEGORY -> {
				int ch = categoryRowAt(mx, my);
				if (ch >= 0) {
					openChapter(ch, -1);
					return true;
				}
			}
			case SEARCH -> {
				SearchHit h = resultAt(mx, my);
				if (h != null) {
					openChapter(h.chapter(), h.line());
					return true;
				}
			}
			case CHAPTER -> {
				int fy = py + ph - FOOTER_H - 2;
				if (my >= fy && my <= fy + FOOTER_H) {
					if (mx < px + pw / 3) {
						if (spread > 0) {
							turn(-1);
						} else if (prevChapterInCategory() >= 0) {
							int prev = prevChapterInCategory();
							this.chapter = prev;
							this.spread = 0;
							lastChapterRead = prev;
							layoutChapter();
						}
						return true;
					}
					if (mx > px + pw * 2 / 3) {
						if (spread < spreadCount() - 1) {
							turn(1);
						} else if (nextChapterInCategory() >= 0) {
							int next = nextChapterInCategory();
							this.chapter = next;
							this.spread = 0;
							lastChapterRead = next;
							layoutChapter();
						}
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (view == View.CHAPTER) {
			turn(dy < 0 ? 1 : -1);
			return true;
		}
		if (view == View.CATEGORY || view == View.SEARCH || view == View.HOME) {
			scrollTarget -= dy * 26;
			clampScroll();
			return true;
		}
		return false;
	}

	private void clampScroll() {
		double max = Math.max(0, contentHeight() - (listBottom() - listTop()));
		scrollTarget = Math.max(0, Math.min(max, scrollTarget));
		scrollPos = Math.max(0, Math.min(max, scrollPos));
	}

	private int contentHeight() {
		return switch (view) {
			case CATEGORY -> {
				int h = 0;
				for (IndexEntry e : HeroPackGuide.categoryEntries(category)) {
					h += e.isHeading() ? SUB_H : ROW_H + 2;
				}
				yield h + 4;
			}
			case SEARCH -> hits.size() * (RESULT_H + 2) + 4;
			case HOME -> homeHeight();
			default -> 0;
		};
	}

	private int listTop() {
		return view == View.SEARCH ? by + 14 : by;
	}

	private int listBottom() {
		return by + bh;
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		long now = System.nanoTime();
		double dt = lastFrameNanos == 0 ? 0.016 : Math.min(0.1, (now - lastFrameNanos) / 1.0e9);
		lastFrameNanos = now;
		scrollPos += (scrollTarget - scrollPos) * (1 - Math.exp(-dt * 16));
		if (Math.abs(scrollTarget - scrollPos) < 0.3) {
			scrollPos = scrollTarget;
		}

		drawPanel(g);
		drawHeader(g, mouseX, mouseY);
		switch (view) {
			case HOME -> drawHome(g, mouseX, mouseY);
			case CATEGORY -> drawCategory(g, mouseX, mouseY);
			case CHAPTER -> drawChapter(g, mouseX, mouseY);
			case SEARCH -> drawSearch(g, mouseX, mouseY);
		}
		drawFooter(g, mouseX, mouseY);
	}

	private void drawPanel(GuiGraphics g) {
		int accent = view == View.HOME ? GOLD : category.colour;
		// soft drop shadow, body, double rule
		g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x60000000);
		g.fill(px, py, px + pw, py + ph, PANEL_BG);
		g.renderOutline(px, py, pw, ph, PANEL_EDGE);
		g.renderOutline(px + 1, py + 1, pw - 2, ph - 2, 0x40FFFFFF & PANEL_EDGE_HI);
		// header band with an accent rule in the section's colour
		g.fill(px + 1, py + 1, px + pw - 1, py + HEADER_H, HEADER_BG);
		g.fill(px + 1, py + HEADER_H, px + pw - 1, py + HEADER_H + 1, accent);
		g.fill(px + 1, py + HEADER_H + 1, px + pw - 1, py + HEADER_H + 2, (accent & 0x00FFFFFF) | 0x50000000);
		// corner studs
		for (int[] c : new int[][] { { px + 2, py + 2 }, { px + pw - 5, py + 2 }, { px + 2, py + ph - 5 }, { px + pw - 5, py + ph - 5 } }) {
			g.fill(c[0], c[1], c[0] + 3, c[1] + 3, accent);
		}
		// footer rule
		g.fill(px + 6, py + ph - FOOTER_H - 4, px + pw - 6, py + ph - FOOTER_H - 3, 0x40FFFFFF);
	}

	private record Crumb(String text, int x, int w, int colour, Runnable action) {
	}

	private List<Crumb> crumbs() {
		List<Crumb> out = new ArrayList<>();
		int x = px + (view == View.HOME ? 8 : 24);
		int limit = searchBox.getX() - 8;
		List<String> labels = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		labels.add(this.title.getString());
		colours.add(GOLD);
		actions.add(view == View.HOME ? null : this::goHome);
		if (view == View.CATEGORY || view == View.CHAPTER) {
			labels.add(Component.translatable(category.nameKey()).getString());
			colours.add(category.colour);
			Category c = category;
			actions.add(view == View.CHAPTER ? () -> {
				history.clear();
				history.push(new Nav(View.HOME, c, -1, 0, 0));
				view = View.CATEGORY;
				scrollTarget = scrollPos = 0;
			} : null);
		}
		if (view == View.CHAPTER) {
			labels.add(HeroPackGuide.chapters().get(chapter).title().getString());
			colours.add(TEXT);
			actions.add(null);
		}
		if (view == View.SEARCH) {
			labels.add(Component.translatable("projecthero.guide.ui.search").getString().replace("...", ""));
			colours.add(TEXT);
			actions.add(null);
		}
		for (int i = 0; i < labels.size(); i++) {
			String s = labels.get(i);
			if (i > 0) {
				x += this.font.width(" > ");
			}
			int room = limit - x;
			if (room < 12) {
				break;
			}
			if (this.font.width(s) > room) {
				s = this.font.plainSubstrByWidth(s, room - this.font.width("...")) + "...";
			}
			int w = this.font.width(s);
			out.add(new Crumb(s, x, w, colours.get(i), actions.get(i)));
			x += w;
		}
		return out;
	}

	private void drawHeader(GuiGraphics g, int mx, int my) {
		if (view != View.HOME) {
			boolean over = hit(mx, my, px + 6, py + 5, 14, 14);
			g.fill(px + 6, py + 5, px + 20, py + 19, over ? 0xFF3A4050 : 0xFF242834);
			g.renderOutline(px + 6, py + 5, 14, 14, over ? GOLD : PANEL_EDGE_HI);
			g.drawString(this.font, "<", px + 11, py + 8, over ? GOLD : TEXT, false);
		}
		List<Crumb> crumbs = crumbs();
		for (int i = 0; i < crumbs.size(); i++) {
			Crumb c = crumbs.get(i);
			if (i > 0) {
				g.drawString(this.font, ">", c.x - this.font.width(" > ") + this.font.width(" "), py + 8, TEXT_FAINT, false);
			}
			boolean over = c.action != null && hit(mx, my, c.x, py + 5, c.w, 14);
			g.drawString(this.font, c.text, c.x, py + 8, over ? 0xFFFFFFFF : c.colour, i == 0);
			if (over) {
				g.fill(c.x, py + 18, c.x + c.w, py + 19, c.colour);
			}
		}
	}

	private void drawFooter(GuiGraphics g, int mx, int my) {
		int fy = py + ph - FOOTER_H + 2;
		if (view == View.CHAPTER) {
			int n = spreadCount();
			int first = spread * columns + 1;
			int last = Math.min(pages.size(), first + columns - 1);
			Component mid = first == last ? Component.translatable("projecthero.guide.ui.page", first, pages.size())
					: Component.translatable("projecthero.guide.ui.pages", first, last, pages.size());
			g.drawCenteredString(this.font, mid, px + pw / 2, fy, TEXT_DIM);
			// prev
			String prev = null;
			if (spread > 0) {
				prev = Component.translatable("projecthero.guide.ui.prev").getString();
			} else if (prevChapterInCategory() >= 0) {
				prev = "< " + HeroPackGuide.chapters().get(prevChapterInCategory()).title().getString();
			}
			if (prev != null) {
				prev = fit(prev, pw / 3 - 10);
				boolean over = my >= fy - 3 && my <= fy + 11 && mx >= px && mx < px + pw / 3;
				g.drawString(this.font, prev, px + 8, fy, over ? GOLD : TEXT, false);
			}
			String next = null;
			if (spread < n - 1) {
				next = Component.translatable("projecthero.guide.ui.next").getString();
			} else if (nextChapterInCategory() >= 0) {
				next = Component.translatable("projecthero.guide.ui.next_chapter",
						HeroPackGuide.chapters().get(nextChapterInCategory()).title().getString()).getString();
			}
			if (next != null) {
				next = fitEnd(next, pw / 3 - 10);
				boolean over = my >= fy - 3 && my <= fy + 11 && mx > px + pw * 2 / 3;
				g.drawString(this.font, next, px + pw - 8 - this.font.width(next), fy, over ? GOLD : TEXT, false);
			}
			return;
		}
		String hint = switch (view) {
			case HOME -> Component.translatable("projecthero.guide.ui.hint.home").getString();
			case SEARCH -> Component.translatable("projecthero.guide.ui.hint.search").getString();
			default -> Component.translatable("projecthero.guide.ui.hint.list").getString();
		};
		String right = view == View.CATEGORY
				? Component.translatable("projecthero.guide.ui.chapters", HeroPackGuide.chapterCount(category)).getString()
				: "";
		int rw = this.font.width(right);
		g.drawString(this.font, fit(hint, pw - 24 - rw), px + 8, fy, TEXT_FAINT, false);
		if (!right.isEmpty()) {
			g.drawString(this.font, right, px + pw - 8 - rw, fy, TEXT_DIM, false);
		}
	}

	// ---- home

	private int tileCols() {
		return bw >= 360 ? 4 : 2;
	}

	private int tileH() {
		int rowsN = (Category.values().length + tileCols() - 1) / tileCols();
		int room = bh - (lastChapterRead >= 0 ? 20 : 0) - (rowsN - 1) * 6;
		int fit = room / rowsN;
		if (fit < 50) {
			return Math.max(30, fit); // short tiles: icon + name + count
		}
		return Math.max(50, Math.min(fit, neededTileH()));
	}

	private int neededTileH = -1;
	private int neededForW = -1;

	/** The tallest a big tile needs to be for its name (3 lines max), count and wrapped description. */
	private int neededTileH() {
		int w = tileW();
		if (neededTileH > 0 && neededForW == w) {
			return neededTileH;
		}
		int need = 50;
		for (Category c : Category.values()) {
			int textW = w - 4 - 34;
			int n = Math.min(3, this.font.split(Component.literal(Component.translatable(c.nameKey()).getString()).withStyle(st -> st.withBold(true)), Math.max(20, textW)).size());
			int nameBottom = n == 1 ? 12 + 9 : 7 + n * 9;
			int cy = Math.max(nameBottom + 3, 34);
			int desc = this.font.split(Component.translatable(c.descKey()), w - 12).size();
			need = Math.max(need, cy + 11 + desc * 9 + 6);
		}
		neededTileH = need;
		neededForW = w;
		return need;
	}

	/** The home grid sits a little above the middle when there is spare room. */
	private int homeOffset() {
		return Math.max(0, (bh - homeHeight()) / 3);
	}

	private int homeHeight() {
		int rowsN = (Category.values().length + tileCols() - 1) / tileCols();
		return rowsN * (tileH() + 6) + (lastChapterRead >= 0 ? 20 : 0);
	}

	private int tileW() {
		return (bw - (tileCols() - 1) * 6) / tileCols();
	}

	private Category tileAt(double mx, double my) {
		if (my < by || my > by + bh) {
			return null;
		}
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			int[] r = tileRect(i);
			if (hit(mx, my, r[0], r[1], r[2], r[3])) {
				return cats[i];
			}
		}
		return null;
	}

	private int[] tileRect(int i) {
		int cols = tileCols();
		int tw = tileW();
		int th = tileH();
		int x = bx + (i % cols) * (tw + 6);
		int y = by + homeOffset() + (i / cols) * (th + 6) - (int) Math.round(scrollPos);
		return new int[] { x, y, tw, th };
	}

	private boolean continueRowHit(double mx, double my) {
		int y = continueRowY();
		return hit(mx, my, bx, y, bw, 16);
	}

	private int continueRowY() {
		int rowsN = (Category.values().length + tileCols() - 1) / tileCols();
		return by + homeOffset() + rowsN * (tileH() + 6) - (int) Math.round(scrollPos);
	}

	private void drawHome(GuiGraphics g, int mx, int my) {
		g.enableScissor(bx - 2, by - 2, bx + bw + 2, by + bh + 2);
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			Category c = cats[i];
			int[] r = tileRect(i);
			int x = r[0], y = r[1], w = r[2], h = r[3];
			boolean over = hit(mx, my, x, y, w, h) && my >= by && my <= by + bh;
			int dark = (c.colour & 0x00FFFFFF) | (over ? 0x50000000 : 0x28000000);
			g.fillGradient(x, y, x + w, y + h, over ? 0xE0262A38 : 0xD01A1D27, dark);
			g.renderOutline(x, y, w, h, over ? c.colour : PANEL_EDGE);
			g.fill(x + 1, y + 1, x + 3, y + h - 1, c.colour);
			// icon (bigger on tall tiles)
			ItemStack icon = categoryIcon(c);
			boolean big = h >= 50 && w >= 90;
			int textX;
			if (big) {
				g.pose().pushPose();
				g.pose().translate(x + 7, y + 6, 0);
				g.pose().scale(1.5f, 1.5f, 1f);
				g.renderItem(icon, 0, 0);
				g.pose().popPose();
				textX = x + 34;
			} else {
				g.renderItem(icon, x + 6, y + (h - 16) / 2);
				textX = x + 26;
			}
			int textW = x + w - 4 - textX;
			String name = Component.translatable(c.nameKey()).getString();
			String count = Component.translatable("projecthero.guide.ui.chapters", HeroPackGuide.chapterCount(c)).getString();
			if (!big) {
				// a short tile: one line of name, one of count, centred on the icon
				int ty = y + Math.max(3, (h - 20) / 2);
				g.drawString(this.font, Component.literal(fit(name, textW)).withStyle(s -> s.withBold(true)), textX, ty, over ? 0xFFFFFFFF : TEXT, true);
				g.drawString(this.font, fit(count, textW), textX, ty + 11, c.colour, false);
			} else {
				// name beside the big icon (up to 3 lines), then the count and the description across the whole tile
				List<FormattedCharSequence> nameLines = this.font.split(Component.literal(name).withStyle(s -> s.withBold(true)), Math.max(20, textW));
				int ty = y + 7;
				if (nameLines.size() == 1) {
					ty = y + 12;
				}
				for (int l = 0; l < Math.min(3, nameLines.size()); l++) {
					g.drawString(this.font, nameLines.get(l), textX, ty, over ? 0xFFFFFFFF : TEXT, true);
					ty += 9;
				}
				int cy = Math.max(ty + 3, y + 34);
				g.drawString(this.font, fit(count, w - 12), x + 7, cy, c.colour, false);
				int dy = cy + 11;
				for (FormattedCharSequence line : this.font.split(Component.translatable(c.descKey()), w - 12)) {
					if (dy + 9 > y + h - 2) {
						break;
					}
					g.drawString(this.font, line, x + 7, dy, TEXT_DIM, false);
					dy += 9;
				}
			}
		}
		if (lastChapterRead >= 0 && lastChapterRead < HeroPackGuide.chapters().size()) {
			int y = continueRowY();
			boolean over = hit(mx, my, bx, y, bw, 16);
			g.fill(bx, y, bx + bw, y + 16, over ? ROW_HOVER : ROW_BG);
			g.renderItem(chapterIcon(lastChapterRead), bx + 2, y);
			String s = Component.translatable("projecthero.guide.ui.continue",
					HeroPackGuide.chapters().get(lastChapterRead).title().getString()).getString();
			g.drawString(this.font, fit(s, bw - 30), bx + 22, y + 4, over ? GOLD : TEXT_DIM, false);
		}
		g.disableScissor();
	}

	// ---- category

	private int categoryRowAt(double mx, double my) {
		if (my < listTop() || my > listBottom() || mx < bx || mx > bx + bw) {
			return -1;
		}
		int y = listTop() - (int) Math.round(scrollPos);
		for (IndexEntry e : HeroPackGuide.categoryEntries(category)) {
			int h = e.isHeading() ? SUB_H : ROW_H + 2;
			if (!e.isHeading() && my >= y && my < y + ROW_H) {
				return e.chapterIndex();
			}
			y += h;
		}
		return -1;
	}

	private void drawCategory(GuiGraphics g, int mx, int my) {
		int top = listTop();
		int bottom = listBottom();
		g.enableScissor(bx - 2, top, bx + bw + 2, bottom);
		int y = top - (int) Math.round(scrollPos);
		int sbW = contentHeight() > bottom - top ? 6 : 0;
		int rowW = bw - sbW;
		boolean pointerIn = my >= top && my <= bottom;
		for (IndexEntry e : HeroPackGuide.categoryEntries(category)) {
			if (e.isHeading()) {
				if (y + SUB_H > top && y < bottom) {
					String s = e.label().getString().toUpperCase(java.util.Locale.ROOT);
					g.drawString(this.font, fit(s, rowW - 8), bx + 2, y + 5, category.colour, false);
					int lx = bx + 6 + Math.min(this.font.width(s), rowW - 8);
					g.fill(lx, y + 9, bx + rowW, y + 10, (category.colour & 0x00FFFFFF) | 0x50000000);
				}
				y += SUB_H;
				continue;
			}
			if (y + ROW_H > top && y < bottom) {
				int indent = e.depth() >= 2 ? 6 : 0;
				int x = bx + indent;
				int w = rowW - indent;
				boolean over = pointerIn && hit(mx, my, x, y, w, ROW_H);
				g.fill(x, y, x + w, y + ROW_H, over ? ROW_HOVER : ROW_BG);
				g.fill(x, y, x + 2, y + ROW_H, over ? category.colour : (category.colour & 0x00FFFFFF) | 0x80000000);
				g.renderItem(chapterIcon(e.chapterIndex()), x + 5, y + 3);
				String title = e.label().getString();
				g.drawString(this.font, fit(title, w - 40), x + 26, y + 3, over ? 0xFFFFFFFF : TEXT, false);
				String sub = subtitle(e.chapterIndex());
				if (!sub.isEmpty()) {
					g.drawString(this.font, fit(sub, w - 40), x + 26, y + 12, TEXT_FAINT, false);
				}
				g.drawString(this.font, ">", x + w - 10, y + 7, over ? GOLD : TEXT_FAINT, false);
			}
			y += ROW_H + 2;
		}
		g.disableScissor();
		drawScrollbar(g, top, bottom, sbW);
	}

	private void drawScrollbar(GuiGraphics g, int top, int bottom, int sbW) {
		if (sbW == 0) {
			return;
		}
		int total = contentHeight();
		int vis = bottom - top;
		int x = bx + bw - 4;
		g.fill(x, top, x + 3, bottom, 0x40FFFFFF);
		int knob = Math.max(12, vis * vis / Math.max(1, total));
		int max = Math.max(1, total - vis);
		int ky = top + (int) ((vis - knob) * (scrollPos / max));
		g.fill(x, ky, x + 3, ky + knob, this.view == View.SEARCH ? GOLD : category.colour);
	}

	private final Map<Integer, String> subtitles = new HashMap<>();

	/** The chapter's first non-empty line, as a one-line subtitle in the list. */
	private String subtitle(int ch) {
		return subtitles.computeIfAbsent(ch, k -> {
			for (Component line : HeroPackGuide.chapters().get(k).lines()) {
				String s = line.getString().trim();
				if (!s.isEmpty()) {
					return s;
				}
			}
			return "";
		});
	}

	// ---- search

	private SearchHit resultAt(double mx, double my) {
		if (my < listTop() || my > listBottom() || mx < bx || mx > bx + bw) {
			return null;
		}
		int i = (int) ((my - listTop() + scrollPos) / (RESULT_H + 2));
		return i >= 0 && i < hits.size() ? hits.get(i) : null;
	}

	private void drawSearch(GuiGraphics g, int mx, int my) {
		Component head = hits.isEmpty() ? Component.translatable("projecthero.guide.ui.no_results", query.trim())
				: hits.size() == 1 ? Component.translatable("projecthero.guide.ui.result", query.trim())
						: Component.translatable("projecthero.guide.ui.results", hits.size(), query.trim());
		g.drawString(this.font, fit(head.getString(), bw), bx + 2, by + 1, GOLD, false);
		if (hits.isEmpty()) {
			int y = by + 20;
			for (FormattedCharSequence line : this.font.split(Component.translatable("projecthero.guide.ui.no_results.hint"), bw - 4)) {
				g.drawString(this.font, line, bx + 2, y, TEXT_DIM, false);
				y += 10;
			}
			return;
		}
		int top = listTop();
		int bottom = listBottom();
		int sbW = contentHeight() > bottom - top ? 6 : 0;
		int w = bw - sbW;
		g.enableScissor(bx - 2, top, bx + bw + 2, bottom);
		boolean pointerIn = my >= top && my <= bottom;
		for (int i = 0; i < hits.size(); i++) {
			int y = top + i * (RESULT_H + 2) - (int) Math.round(scrollPos);
			if (y + RESULT_H <= top || y >= bottom) {
				continue;
			}
			SearchHit h = hits.get(i);
			Category c = HeroPackGuide.categoryOf(h.chapter());
			boolean over = pointerIn && hit(mx, my, bx, y, w, RESULT_H);
			g.fill(bx, y, bx + w, y + RESULT_H, over ? ROW_HOVER : ROW_BG);
			g.fill(bx, y, bx + 2, y + RESULT_H, c.colour);
			g.renderItem(chapterIcon(h.chapter()), bx + 5, y + 4);
			int tx = bx + 26;
			int tw = w - 30;
			String title = HeroPackGuide.chapters().get(h.chapter()).title().getString();
			if (h.line() < 0) {
				drawHighlighted(g, h.text(), h.matchStart(), h.matchLength(), tx, y + 3, tw, over ? 0xFFFFFFFF : TEXT);
				String in = Component.translatable("projecthero.guide.ui.in", Component.translatable(c.nameKey())).getString();
				g.drawString(this.font, fit(in, tw), tx, y + 13, TEXT_FAINT, false);
			} else {
				String t = fit(title, tw / 2);
				g.drawString(this.font, t, tx, y + 3, over ? 0xFFFFFFFF : TEXT, false);
				int sx = tx + this.font.width(t);
				boolean headingHit = h.text().equals(h.section());
				if (!h.section().equals(title)) {
					String arrow = "  >  ";
					g.drawString(this.font, arrow, sx, y + 3, TEXT_FAINT, false);
					sx += this.font.width(arrow);
					if (headingHit) {
						drawHighlighted(g, h.section(), h.matchStart(), h.matchLength(), sx, y + 3, tx + tw - sx, c.colour);
					} else {
						g.drawString(this.font, fit(h.section(), tx + tw - sx), sx, y + 3, c.colour, false);
					}
				}
				if (headingHit) {
					g.drawString(this.font, fit(lineAfter(h.chapter(), h.line()), tw), tx, y + 13, TEXT_FAINT, false);
				} else {
					drawHighlighted(g, h.text(), h.matchStart(), h.matchLength(), tx, y + 13, tw, TEXT_DIM);
				}
			}
		}
		g.disableScissor();
		drawScrollbar(g, top, bottom, sbW);
	}

	/** The first non-empty line after {@code line} (what a matching section heading is about). */
	private static String lineAfter(int chapter, int line) {
		List<Component> lines = HeroPackGuide.chapters().get(chapter).lines();
		for (int i = line + 1; i < lines.size(); i++) {
			String s = lines.get(i).getString().trim();
			if (!s.isEmpty()) {
				return s;
			}
		}
		return "";
	}

	/** One line of text with the match shown on a gold background, windowed so the match is always in view. */
	private void drawHighlighted(GuiGraphics g, String text, int start, int len, int x, int y, int width, int colour) {
		start = Math.max(0, Math.min(start, text.length()));
		int end = Math.max(start, Math.min(text.length(), start + len));
		String match = text.substring(start, end);
		String pre = text.substring(0, start);
		String post = text.substring(end);
		int matchW = this.font.width(match);
		// keep up to a third of the width for the text before the match
		int preRoom = Math.max(0, Math.min(this.font.width(pre), width / 3));
		if (this.font.width(pre) > preRoom) {
			String cut = this.font.plainSubstrByWidth(new StringBuilder(pre).reverse().toString(), Math.max(0, preRoom - this.font.width("...")));
			pre = "..." + new StringBuilder(cut).reverse();
		}
		int cx = x;
		g.drawString(this.font, pre, cx, y, colour, false);
		cx += this.font.width(pre);
		if (cx + matchW > x + width) {
			match = this.font.plainSubstrByWidth(match, Math.max(0, x + width - cx));
			matchW = this.font.width(match);
		}
		g.fill(cx - 1, y - 1, cx + matchW + 1, y + 9, MATCH_BG);
		g.drawString(this.font, match, cx, y, MATCH_FG, false);
		cx += matchW;
		int room = x + width - cx;
		if (room > 6 && !post.isEmpty()) {
			g.drawString(this.font, fit(post, room), cx, y, colour, false);
		}
	}

	// ---- chapter

	private void drawChapter(GuiGraphics g, int mx, int my) {
		if (chapter < 0) {
			return;
		}
		HeroPackGuide.Chapter ch = HeroPackGuide.chapters().get(chapter);
		// title band: icon, title, a rule in the category's colour
		g.renderItem(chapterIcon(chapter), bx + 2, by + 1);
		String title = fit(ch.title().getString(), bw - 26);
		g.drawString(this.font, Component.literal(title).withStyle(s -> s.withBold(true)), bx + 22, by + 5, 0xFFFFFFFF, true);
		g.fill(bx, by + TITLE_BAND - 3, bx + bw, by + TITLE_BAND - 2, (category.colour & 0x00FFFFFF) | 0xA0000000);

		int top = by + TITLE_BAND + 2;
		int gap = 14;
		int colStride = colW + 4 + gap;
		long now = System.currentTimeMillis();
		float flash = flashLine >= 0 && now < flashUntil ? (flashUntil - now) / 2500f : 0f;
		for (int c = 0; c < columns; c++) {
			int p = spread * columns + c;
			int x = bx + 2 + c * colStride;
			if (c > 0) {
				int dx = x - gap / 2 - 2;
				g.fill(dx, top, dx + 1, by + bh - 2, 0x30FFFFFF);
			}
			if (p >= pages.size()) {
				continue;
			}
			int[] page = pages.get(p);
			int y = top;
			for (int r = page[0]; r < page[1]; r++) {
				if (flash > 0 && rowLine.get(r) == flashLine) {
					int a = (int) (flash * 0x90);
					g.fill(x - 2, y - 1, x + colW + 2, y + LINE_H - 1, (a << 24) | 0x806010);
				}
				g.drawString(this.font, rows.get(r), x, y, 0xFFD8D8E4, false);
				y += LINE_H;
			}
			// page number under the column
			String num = String.valueOf(p + 1);
			g.drawString(this.font, num, x + colW / 2 - this.font.width(num) / 2, by + bh - 9, TEXT_FAINT, false);
		}
	}

	// ---------------------------------------------------------------- helpers

	private String fit(String s, int width) {
		if (width <= 0) {
			return "";
		}
		if (this.font.width(s) <= width) {
			return s;
		}
		return this.font.plainSubstrByWidth(s, Math.max(0, width - this.font.width("..."))) + "...";
	}

	/** Like {@link #fit} but keeps the end (the "Next: <chapter> >" link keeps its arrow). */
	private String fitEnd(String s, int width) {
		if (this.font.width(s) <= width) {
			return s;
		}
		String tail = s.endsWith(">") ? " >" : "";
		String body = tail.isEmpty() ? s : s.substring(0, s.length() - 2);
		return fit(body, width - this.font.width(tail)) + tail;
	}

	private ItemStack categoryIcon(Category c) {
		for (String id : c.icons) {
			ItemStack s = stackOf(id);
			if (!s.isEmpty()) {
				return s;
			}
		}
		return new ItemStack(Items.BOOK);
	}

	private ItemStack chapterIcon(int ch) {
		ItemStack s = stackOf(HeroPackGuide.iconOf(ch));
		return s.isEmpty() ? categoryIcon(HeroPackGuide.categoryOf(ch)) : s;
	}

	private ItemStack stackOf(String id) {
		if (id == null) {
			return ItemStack.EMPTY;
		}
		return iconCache.computeIfAbsent(id, k -> {
			ResourceLocation rl = ResourceLocation.tryParse(k);
			if (rl == null) {
				return ItemStack.EMPTY;
			}
			return BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
