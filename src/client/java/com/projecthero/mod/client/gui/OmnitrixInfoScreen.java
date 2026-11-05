package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.item.OmnitrixItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.2 (user: "change the way the menus look, the I menu, make it look more like minecraft and also make the scroll thing on
 * the side work properly, only apply this new menu style to the aliens and omnitrixes for now please"): the Omnitrix wearer's I
 * menu, drawn like a vanilla container ({@link VanillaMenu}) -- every other power keeps {@link PowerInfoScreen}'s look.
 * <pre>
 *   [tab]                                  one creative-inventory tab: the alien while transformed, the watch otherwise
 *  +--------------------------------------+
 *  | Omnitrix: Heatblast                  |  the tab's name, vanilla label grey
 *  | text text text text text text     |#||  the page, dark text on the panel, clipped to the view
 *  | text text text text text text     | ||  the creative scroller on a recessed track: drag, click to page, wheel
 *  +--------------------------------------+
 *               [      Done      ]
 * </pre>
 * {@link OmnitrixInfo} builds the page (the Guidebook uses the same builder), with the player's live keys and state, rebuilt every
 * half second (timers count down; the scroll position is kept).
 *
 * <p>v0.17.2 (user: "when the user presses I in alien form only make them have the alien menu they are transformed into and not
 * access to everything ... if they just have the omnitrix remove the aliens tab"): an alien sees only its own page, a human only the
 * watch's; the Aliens tab is gone (the Guidebook still has every alien).
 */
public final class OmnitrixInfoScreen extends Screen {
	private static final int LINE_H = 10;

	public enum Tab {
		ALIEN, WATCH
	}

	private final List<Tab> tabs = new ArrayList<>();
	private Tab tab;
	private final List<FormattedCharSequence> body = new ArrayList<>();
	private Component heading = Component.empty();
	private final VanillaMenu.Scroll scroll = new VanillaMenu.Scroll(LINE_H * 3, LINE_H);
	private int px, py, pw, ph;
	private String shownActive = "";
	private int ticks;

	public OmnitrixInfoScreen() {
		super(Component.translatable("screen.projecthero.power_info"));
	}

	/** Whether {@code p}'s I menu is this one. */
	public static boolean applies(@Nullable LocalPlayer p) {
		return p != null && Omnitrix.hasPower(p);
	}

	@Override
	protected void init() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		Aliens.Alien alien = current(p);
		shownActive = s.active == null ? "" : s.active;
		tabs.clear();
		tabs.add(alien != null ? Tab.ALIEN : Tab.WATCH);
		tab = tabs.get(0);

		this.pw = Math.min(this.width - 16, 344);
		this.px = (this.width - pw) / 2;
		this.py = 30;
		this.ph = Math.max(80, this.height - 30 - 30);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(this.width / 2 - 100, this.py + this.ph + 5, 200, 20).build());
		layout();
		rebuild();
	}

	private static @Nullable Aliens.Alien current(LocalPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		return s.transformed() ? Aliens.byId(s.active) : null;
	}

	/** The view: under the heading, left of the scroll track. */
	private void layout() {
		int top = py + 18;
		int trackX = px + pw - 8 - VanillaMenu.TRACK_W;
		// a whole number of lines tall (plus the 1-px pads), so the page ends on a whole line
		int bottom = top + Math.max(LINE_H, (py + ph - 8 - top - 2) / LINE_H * LINE_H) + 2;
		scroll.layout(px + 8, top, trackX - 4, bottom, trackX);
	}

	/** The tab's page, wrapped to the view (TooltipWrap rule: nothing runs past the panel). */
	private void rebuild() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		Function<AbilitySlot, Component> keys = slot -> Component.keybind(slot.keyBindingTranslationKey());
		List<Component> lines = new ArrayList<>();
		String P = "screen.projecthero.power_info.omnitrix.";
		switch (tab) {
			case ALIEN -> {
				Aliens.Alien a = current(p);
				if (a == null) {
					break;
				}
				heading = Component.translatable(P + "heading_alien", OmnitrixInfo.alienName(a));
				OmnitrixInfo.status(lines, p);
				lines.add(Component.empty());
				OmnitrixInfo.alienPage(lines, a, keys, p);
			}
			case WATCH -> {
				heading = Component.translatable("projecthero.guide.omnitrix");
				lines.add(Component.translatable("projecthero.guide.omnitrix.tier").withStyle(ChatFormatting.GREEN));
				lines.add(Component.empty());
				OmnitrixInfo.status(lines, p);
				lines.add(Component.empty());
				OmnitrixInfo.controls(lines, keys);
				lines.add(Component.empty());
				OmnitrixInfo.rules(lines, keys);
				lines.add(Component.empty());
				OmnitrixInfo.containment(lines);
			}
		}
		body.clear();
		int w = scroll.viewX1() - scroll.viewX0();
		for (Component line : lines) {
			VanillaMenu.wrap(this.font, line, w, body);
		}
		scroll.content(body.size() * LINE_H + 2);
	}

	@Override
	public void tick() {
		super.tick();
		LocalPlayer p = Minecraft.getInstance().player;
		if (!applies(p)) {
			onClose();
			return;
		}
		String active = Omnitrix.state(p).active == null ? "" : Omnitrix.state(p).active;
		if (!active.equals(shownActive)) {
			// transformed or turned back while open: the page swaps with the body
			rebuildWidgets();
			return;
		}
		if (++ticks % 10 == 0) {
			rebuild(); // the live timers; content() re-clamps the kept offset
		}
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
		super.renderBackground(g, mx, my, partial);
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i) != tab) {
				VanillaMenu.tab(g, px, py, i, false);
			}
		}
		VanillaMenu.panel(g, px, py, pw, ph);
		int sel = tabs.indexOf(tab);
		if (sel >= 0) {
			VanillaMenu.tab(g, px, py, sel, true);
		}
		for (int i = 0; i < tabs.size(); i++) {
			tabIcon(g, tabs.get(i), VanillaMenu.tabX(px, i) + 5, VanillaMenu.tabIconY(py, tabs.get(i) == tab));
		}
	}

	private void tabIcon(GuiGraphics g, Tab t, int x, int y) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		switch (t) {
			case ALIEN -> dialIcon(g, current(p), x, y);
			case WATCH -> {
				net.minecraft.world.item.Item item = Omnitrix.recalibrated(p) ? OmnitrixItems.RECALIBRATED_OMNITRIX : OmnitrixItems.OMNITRIX;
				if (item != null) {
					g.renderItem(new ItemStack(item), x, y);
				}
			}
		}
	}

	/** The alien's dial art (the user's hologram icons, 32x32) as a 16x16 item-style icon. */
	private static void dialIcon(GuiGraphics g, @Nullable Aliens.Alien a, int x, int y) {
		if (a == null) {
			return;
		}
		// v0.17.2 (user: "for the recalibrate omnitrix menu dont use alien icons, use the alien models instead since we dont have alien
		// icons for them"): the Recalibrated Omnitrix's aliens show their own bodies in full colour
		if (a.recalibrated() && com.projecthero.mod.client.omnitrix.RecalHologram.renderGuiModel(g, a.id(), x, y, 16)) {
			return;
		}
		ResourceLocation tex = com.projecthero.mod.ProjectHeroMod.id("textures/font/omnitrix_dial/" + a.id() + ".png");
		g.blit(tex, x, y, 16, 16, 0f, 0f, 32, 32, 32, 32);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float partial) {
		super.render(g, mx, my, partial);
		// the tab's name, like a container's label
		g.drawString(this.font, VanillaMenu.onPanel(heading.getVisualOrderText(), OmnitrixInfo.DIAL_FONT), px + 8, py + 6, VanillaMenu.LABEL, false);

		// the page, clipped to the view
		scroll.beginClip(g);
		int y = scroll.viewY0() + 1 - scroll.offset();
		for (FormattedCharSequence line : body) {
			// whole lines only (the offset snaps to lines, so every line in the view is either fully shown or not at all)
			if (y >= scroll.viewY0() && y + this.font.lineHeight <= scroll.viewY1()) {
				g.drawString(this.font, VanillaMenu.onPanel(line, OmnitrixInfo.DIAL_FONT), scroll.viewX0(), y, VanillaMenu.LABEL, false);
			}
			y += LINE_H;
		}
		scroll.endClip(g);
		scroll.render(g);

		for (int i = 0; i < tabs.size(); i++) {
			if (VanillaMenu.overTab(mx, my, px, py, i)) {
				g.renderTooltip(this.font, VanillaMenu.tooltip(this.font, List.of(tabName(tabs.get(i)))), mx, my);
			}
		}
	}

	private Component tabName(Tab t) {
		LocalPlayer p = Minecraft.getInstance().player;
		return switch (t) {
			case ALIEN -> {
				Aliens.Alien a = p == null ? null : current(p);
				yield a == null ? Component.empty() : OmnitrixInfo.alienName(a);
			}
			case WATCH -> Component.translatable("projecthero.guide.omnitrix");
		};
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (scroll.mouseClicked(mx, my, button)) {
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return scroll.mouseDragged(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = scroll.mouseReleased(button);
		return super.mouseReleased(mx, my, button) || was;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		return scroll.mouseScrolled(dy) || super.mouseScrolled(mx, my, dx, dy);
	}

	/** Arrow keys / Page Up / Page Down / Home / End scroll the page. */
	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		int page = Math.max(LINE_H, scroll.viewHeight() - LINE_H);
		switch (key) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> scroll.by(LINE_H * 2);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> scroll.by(-LINE_H * 2);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> scroll.by(page);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> scroll.by(-page);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> scroll.set(Integer.MAX_VALUE / 2);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> scroll.set(0);
			default -> {
				return super.keyPressed(key, scan, mods);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- harness / tests

	/** The tabs this player has (tests / harnesses). */
	public List<Tab> tabs() {
		return List.copyOf(tabs);
	}

	public void scrollFraction(float f) {
		scroll.setFraction(f);
	}

	/** The page's scroll offset in pixels and its maximum. */
	public int[] scrollState() {
		return new int[] { scroll.offset(), scroll.max() };
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
