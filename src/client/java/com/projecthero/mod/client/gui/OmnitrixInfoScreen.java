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
 * menu. v0.17.3: drawn on the shared see-through page ({@link GlassPage}) like every other power's I menu; the alien / watch icon
 * sits left of the heading.
 * {@link OmnitrixInfo} builds the page (the Guidebook uses the same builder), with the player's live keys and state, rebuilt every
 * half second (timers count down; the scroll position is kept).
 *
 * <p>v0.17.2 (user: "when the user presses I in alien form only make them have the alien menu they are transformed into and not
 * access to everything ... if they just have the omnitrix remove the aliens tab"): an alien sees only its own page, a human only the
 * watch's; the Aliens tab is gone (the Guidebook still has every alien).
 */
public final class OmnitrixInfoScreen extends Screen {
	private static final int LINE_H = GlassPage.LINE_H;

	public enum Tab {
		ALIEN, WATCH
	}

	private final List<Tab> tabs = new ArrayList<>();
	private Tab tab;
	/** v0.17.3: the shared see-through page, like every other power's I menu. */
	private final GlassPage page = new GlassPage();
	private final List<FormattedCharSequence> body = page.lines();
	private Component heading = Component.empty();
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

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
		page.layout(this.width, this.height);
		page.scroll().set(0);
		rebuild();
	}

	private static @Nullable Aliens.Alien current(LocalPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		return s.transformed() ? Aliens.byId(s.active) : null;
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
		for (Component line : lines) {
			page.wrap(this.font, line);
		}
		page.done(); // keeps (and re-clamps) the scroll position
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
	public void render(GuiGraphics g, int mx, int my, float partial) {
		super.render(g, mx, my, partial);
		g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		// v0.17.3 (user: "the see-through looking I menu ... apply this to all the powers"): the same page as every power; the
		// alien (or the watch) as a 16x16 icon left of the heading, which then starts after it
		page.render(g, this.font, Component.empty(), mx, my);
		int hx = page.scroll().viewX0() + 6, hy = page.scroll().viewY0() - 14;
		if (tab != null) {
			tabIcon(g, tab, hx, hy - 4);
		}
		g.drawString(this.font, net.minecraft.locale.Language.getInstance().getVisualOrder(
				this.font.substrByWidth(heading.copy().withStyle(net.minecraft.ChatFormatting.BOLD), page.wrapWidth() - 20)), hx + 20, hy, GlassPage.HEADING);
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
		return page.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return page.mouseDragged(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = page.mouseReleased(button);
		return super.mouseReleased(mx, my, button) || was;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		return page.mouseScrolled(dy) || super.mouseScrolled(mx, my, dx, dy);
	}

	/** Arrow keys / Page Up / Page Down / Home / End scroll the page. */
	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		return page.keyPressed(key) || super.keyPressed(key, scan, mods);
	}

	// ---------------------------------------------------------------- harness / tests

	/** The tabs this player has (tests / harnesses). */
	public List<Tab> tabs() {
		return List.copyOf(tabs);
	}

	public void scrollFraction(float f) {
		page.scroll().setFraction(f);
	}

	/** The page's scroll offset in pixels and its maximum. */
	public int[] scrollState() {
		return new int[] { page.scroll().offset(), page.scroll().max() };
	}

	/** Scrolls to the first line containing {@code text} (case-insensitive); false if none. */
	public boolean scrollTo(String text) {
		return page.scrollTo(text);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
