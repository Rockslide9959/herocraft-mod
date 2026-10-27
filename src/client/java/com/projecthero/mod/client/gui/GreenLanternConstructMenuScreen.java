package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.network.GreenLanternMenuPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * v0.17.1 Green Lantern revamp: the Construct Menu (hold C for 0.5 s). v2 look: a "ring interface" -- a dark emerald
 * panel with glowing edges and the Lantern Corps symbol behind it, five tabs (Attack / Defence / Mobility / Utility /
 * Survival, from {@link ConstructType#category()}), a grid of construct cards with the 32x32 hard-light icons
 * ({@link GreenLanternIcons}), a detail panel for the hovered / selected entry, and the twelve bindable keys as keycaps.
 * <ul>
 *   <li><b>Bind</b>: click a construct, then a keycap -- or drag the construct onto a keycap -- or hover it and press
 *   G / H / Z / X / C / V (Shift for the Shift variant). With a construct selected, pressing a key binds it too.</li>
 *   <li><b>Clear</b>: right-click an entry, or "Clear keys" in the detail panel -- every key holding it is emptied.</li>
 *   <li><b>Spawn once</b>: hold left click on an entry for 0.5 s (the card fills), or "Spawn once" in the detail panel.</li>
 *   <li><b>Reset to default</b> puts the old fixed layout back.</li>
 * </ul>
 * C or Esc closes it when no entry is hovered. The server re-checks every request ({@code GreenLanternBindings}).
 */
public final class GreenLanternConstructMenuScreen extends Screen {
	/** The v0.17.1 16x16 entry atlas -- still what the HUD key boxes draw (see {@link #icon}). */
	public static final ResourceLocation ENTRY_ICONS = ProjectHeroMod.id("textures/gui/green_lantern/entries.png");
	/** v0.17.1: the survival constructs' icons (chest, scaffold tower, item net, tunneller). */
	public static final ResourceLocation SURVIVAL_ICONS = ProjectHeroMod.id("textures/gui/green_lantern/survival.png");
	public static final ResourceLocation MECH_ICON = ProjectHeroMod.id("textures/gui/green_lantern/mech_icon.png");
	/** v0.17.1: the Hard-Light Drone (cell 0) and Gateway (cell 1). */
	public static final ResourceLocation RING_TECH_ICONS = ProjectHeroMod.id("textures/gui/green_lantern/ring_tech.png");

	/** The menu's tabs. A construct goes in its {@link ConstructType#category()}'s tab; ring moves by their section. */
	public enum Tab {
		ATTACK("construct_fist"), DEFENCE("shield"), MOBILITY("c_platform"), UTILITY("ring_scan"), SURVIVAL("c_hard_light_tools");

		final String icon;

		Tab(String icon) {
			this.icon = icon;
		}

		public String translationKey() {
			return "screen.projecthero.gl_menu.tab." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Entries that sit in the Survival tab whatever their category (plus any category named SURVIVAL). */
	private static final Set<String> SURVIVAL_IDS = Set.of("c_hard_light_tools", "c_mining_drill", "c_lantern_light",
			"c_atmosphere_bubble", "c_med_bay", "c_hard_light_chest", "c_scaffold_tower", "c_item_net", "c_tunneller");

	public static Tab tabOf(Entry e) {
		if (SURVIVAL_IDS.contains(e.id())) {
			return Tab.SURVIVAL;
		}
		if (e.type() != null) {
			return switch (e.type().category().name()) {
				case "ATTACK" -> Tab.ATTACK;
				case "DEFENSE", "DEFENCE" -> Tab.DEFENCE;
				case "MOBILITY" -> Tab.MOBILITY;
				case "SURVIVAL" -> Tab.SURVIVAL;
				default -> Tab.UTILITY;
			};
		}
		return switch (e.section().name()) {
			case "ATTACK" -> Tab.ATTACK;
			case "DEFENCE" -> Tab.DEFENCE;
			default -> Tab.UTILITY;
		};
	}

	private static final int PAD = 8;
	private static final int SPAWN_HOLD_MS = 500;
	private static final int OPEN_MS = 220;
	private static final int DRAG_START = 4;

	private static final int GREEN = 0xFF35F075;
	private static final int PALE = 0xFFA8FFC0;
	private static final int MINT = 0xFFD8FFE2;
	private static final int DIM = 0xFF6FB884;
	private static final int TEXT = 0xFFB8DCC4;
	private static final int GOLD = 0xFFE8E070;
	private static final int RED = 0xFFFF6A6A;
	private static final int AQUA = 0xFF7FF3E0;

	/** Remembered between openings (client only). */
	private static Tab lastTab = Tab.ATTACK;
	private static String lastSelected = null;

	// ---- layout
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int tabY;
	private int tabH;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridBottom;
	private int detailX;
	private int detailW;
	private int card;
	private int iconSize;
	private int pitch;
	private int cols;
	private int gridOff;
	private int keysY;
	private int keySize;
	private int keysX0;
	private int footerY;
	private List<FormattedCharSequence> hintLines = List.of();
	private int resetX;
	private int resetW;

	// ---- state
	private Tab tab = lastTab;
	private String selectedId = lastSelected;
	private float scroll;
	private float scrollTarget;
	private int contentHeight;
	private float detailScroll;
	private int detailContentHeight;
	private long openedAt;
	private long lastFrame;
	private final Map<String, Float> hoverAnim = new HashMap<>();
	private final Map<String, Long> keyFlash = new HashMap<>();
	private Entry hovered;
	private String hoveredKey;
	private Object lastHoverTarget;
	/** The entry left click is being held on (spawn once / drag), since when (ms) and where. */
	private Entry pressing;
	private long pressStart;
	private double pressX;
	private double pressY;
	private boolean dragging;
	/** C opened the menu while held: its key repeat must not close it again until it has been let go once. */
	private boolean cReleased;
	private Component flash;
	private int flashColor = PALE;
	private long flashUntil;

	public GreenLanternConstructMenuScreen() {
		super(Component.translatable("screen.projecthero.gl_menu.title"));
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			// the physical key: setScreen releases every KeyMapping before init, so isDown() always read false here and the
			// held C's key repeat closed the menu, letting the hold reopen it (user: "it keeps playing the sound")
			cReleased = !cPhysicallyDown();
			sound(SoundEvents.BEACON_POWER_SELECT, 1.9f, 0.35f);
		}
		layout();
	}

	private void layout() {
		panelW = Math.min(this.width - 12, 500);
		panelH = Math.min(this.height - 12, 320);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		int inner = panelW - PAD * 2;
		tabY = panelY + 25;
		tabH = 18;

		// footer: the hint (wrapped, up to three lines) and the reset control
		Component reset = Component.translatable("screen.projecthero.gl_menu.reset");
		resetW = this.font.width(reset) + 14;
		resetX = panelX + panelW - PAD - resetW;
		List<FormattedCharSequence> lines = this.font.split(Component.translatable("screen.projecthero.gl_menu.hint_v2"),
				Math.max(40, inner - resetW - 10));
		hintLines = lines.subList(0, Math.min(3, lines.size()));
		int footerH = Math.max(14, hintLines.size() * 9);
		footerY = panelY + panelH - 6 - footerH;

		// the twelve keycaps, one row in two groups of six
		int groupGap = 8;
		keySize = Mth.clamp((inner - groupGap - 10 * 2) / 12, 16, 30);
		int keysW = keySize * 12 + 2 * 10 + groupGap;
		keysX0 = panelX + (panelW - keysW) / 2;
		keysY = footerY - 6 - keySize - 10;

		gridY = tabY + tabH + 5;
		gridBottom = keysY - 5;
		detailW = panelW >= 300 ? Mth.clamp(panelW * 36 / 100, 112, 176) : 0;
		gridX = panelX + PAD;
		gridW = inner - (detailW > 0 ? detailW + 6 : 0);
		detailX = gridX + gridW + 6;
		boolean big = gridW >= 4 * 39 && gridBottom - gridY >= 39;
		card = big ? 36 : 22;
		iconSize = big ? 32 : 16;
		pitch = card + 3;
		cols = Math.max(1, (gridW - 4 + 3) / pitch);
		gridOff = (gridW - (cols * pitch - 3)) / 2;
		clampScroll();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private GreenLanternState state() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
	}

	private static void send(int action, String key, String entry) {
		ClientPlayNetworking.send(new GreenLanternMenuPayload(action, key, entry, false));
	}

	private static void sound(SoundEvent s, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
	}

	private Entry selected() {
		return GreenLanternBinding.byId(selectedId);
	}

	private void select(Entry e) {
		selectedId = e == null ? null : e.id();
		lastSelected = selectedId;
		detailScroll = 0f;
		if (e != null) {
			sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.8f);
		}
	}

	private void setTab(Tab t) {
		if (t != tab) {
			tab = t;
			lastTab = t;
			scroll = scrollTarget = 0f;
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f);
		}
	}

	private static List<Entry> entriesOf(Tab t) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : GreenLanternBinding.all()) {
			if (tabOf(e) == t) {
				out.add(e);
			}
		}
		return out;
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight - (gridBottom - gridY));
		scrollTarget = Mth.clamp(scrollTarget, 0f, max);
	}

	// ---------------------------------------------------------------- hit tests

	private Tab tabAt(double mx, double my) {
		if (my < tabY || my >= tabY + tabH) {
			return null;
		}
		int inner = panelW - PAD * 2;
		int tw = inner / Tab.values().length;
		int i = (int) Math.floor((mx - (panelX + PAD)) / tw);
		return i >= 0 && i < Tab.values().length ? Tab.values()[i] : null;
	}

	private Entry cardAt(double mx, double my) {
		if (mx < gridX || mx >= gridX + gridW || my < gridY || my >= gridBottom) {
			return null;
		}
		List<Entry> list = entriesOf(tab);
		for (int i = 0; i < list.size(); i++) {
			int cx = cardX(i);
			int cy = cardY(i);
			if (mx >= cx && mx < cx + card && my >= cy && my < cy + card) {
				return list.get(i);
			}
		}
		return null;
	}

	private int cardX(int i) {
		return gridX + gridOff + (i % cols) * pitch;
	}

	private int cardY(int i) {
		return gridY + 3 + (i / cols) * pitch - Math.round(scroll);
	}

	private int keyX(int i) {
		return keysX0 + i * (keySize + 2) + (i >= 6 ? 6 : 0);
	}

	private int keyTop() {
		return keysY + 10;
	}

	private String keyAt(double mx, double my) {
		int y = keyTop();
		if (my < y || my >= y + keySize) {
			return null;
		}
		for (int i = 0; i < GreenLanternBinding.KEYS.size(); i++) {
			int x = keyX(i);
			if (mx >= x && mx < x + keySize) {
				return GreenLanternBinding.KEYS.get(i);
			}
		}
		return null;
	}

	private boolean detailButtonsShown() {
		return detailW > 0 && selected() != null && (hovered == null || hovered == selected());
	}

	/** 0 = Spawn once, 1 = Clear keys, -1 = neither. */
	private int detailButtonAt(double mx, double my) {
		if (!detailButtonsShown()) {
			return -1;
		}
		int by = gridBottom - 18;
		int bw = (detailW - 14) / 2;
		if (my < by || my >= by + 14) {
			return -1;
		}
		if (mx >= detailX + 5 && mx < detailX + 5 + bw) {
			return 0;
		}
		if (mx >= detailX + 9 + bw && mx < detailX + 9 + bw * 2) {
			return 1;
		}
		return -1;
	}

	private boolean overReset(double mx, double my) {
		return mx >= resetX && mx < resetX + resetW && my >= footerY && my < footerY + 14;
	}

	private boolean overDetail(double mx, double my) {
		return detailW > 0 && mx >= detailX && mx < detailX + detailW && my >= gridY && my < gridBottom;
	}

	// ---------------------------------------------------------------- render

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fillGradient(0, 0, this.width, this.height, 0x78000804, 0xB0000502);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = Mth.clamp((now - lastFrame) / 1000f, 0f, 0.1f);
		lastFrame = now;
		float open = Mth.clamp((now - openedAt) / (float) OPEN_MS, 0f, 1f);
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);

		RenderSystem.setShaderColor(1f, 1f, 1f, ease);
		this.renderBackground(g, mouseX, mouseY, partialTick);
		GreenLanternState s = state();
		if (s == null) {
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			return;
		}
		scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
		if (Math.abs(scrollTarget - scroll) < 0.3f) {
			scroll = scrollTarget;
		}

		g.pose().pushPose();
		float k = 0.94f + 0.06f * ease;
		float cx = panelX + panelW / 2f;
		float cy = panelY + panelH / 2f;
		g.pose().translate(cx, cy, 0f);
		g.pose().scale(k, k, 1f);
		g.pose().translate(-cx, -cy, 0f);

		// hover targets for this frame
		hovered = dragging ? null : cardAt(mouseX, mouseY);
		hoveredKey = keyAt(mouseX, mouseY);
		Object target = hovered != null ? hovered : hoveredKey;
		if (target != null && !target.equals(lastHoverTarget) && open >= 1f) {
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 2f, 0.12f);
		}
		lastHoverTarget = target;

		renderPanel(g, now);
		renderHeader(g, s);
		renderTabs(g, mouseX, mouseY);
		renderGrid(g, s, mouseX, mouseY, dt, now);
		if (detailW > 0) {
			renderDetail(g, s, mouseX, mouseY);
		}
		renderKeys(g, s, mouseX, mouseY, now);
		renderFooter(g, mouseX, mouseY);

		// hold-to-spawn
		if (pressing != null && !dragging && System.currentTimeMillis() - pressStart >= SPAWN_HOLD_MS) {
			spawn(pressing);
			select(pressing);
			pressing = null;
		}
		// the dragged construct follows the mouse
		if (dragging && pressing != null) {
			g.pose().pushPose();
			g.pose().translate(0f, 0f, 300f);
			glow(g, mouseX - 16, mouseY - 16, 32, 32, 0x6035F075);
			GreenLanternIcons.draw(g, GreenLanternIcons.of(pressing), mouseX - 16, mouseY - 16, 32, 0xD0FFFFFF);
			g.pose().popPose();
		}
		renderFlash(g, now);
		g.pose().popPose();

		// tooltips (outside the open-animation transform)
		if (!dragging) {
			if (hovered != null && detailW == 0) {
				g.renderComponentTooltip(this.font, tooltip(s, hovered), mouseX, mouseY);
			} else if (hoveredKey != null) {
				g.renderComponentTooltip(this.font, keyTooltip(s, hoveredKey), mouseX, mouseY);
			} else if (detailW == 0 || hovered == null) {
				Tab t = tabAt(mouseX, mouseY);
				if (t != null && tabCompact()) {
					g.renderTooltip(this.font, Component.translatable(t.translationKey()), mouseX, mouseY);
				}
			}
		}
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private void renderPanel(GuiGraphics g, long now) {
		float pulse = 0.5f + 0.5f * Mth.sin(now / 520f);
		// outer glow
		for (int i = 1; i <= 4; i++) {
			int a = (int) ((0x34 - i * 7) * (0.7f + 0.3f * pulse));
			g.renderOutline(panelX - i, panelY - i, panelW + i * 2, panelH + i * 2, (a << 24) | 0x35F075);
		}
		g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE071C0F, 0xF4020A05);
		// the Lantern Corps symbol, faintly, behind the grid
		int areaH = gridBottom - gridY;
		int es = Math.max(32, Math.min(gridW, areaH) * 9 / 10);
		int ex = gridX + (gridW - es) / 2;
		int ey = gridY + (areaH - es) / 2;
		int ea = (int) (0x16 + 0x0A * pulse);
		GreenLanternIcons.draw(g, GreenLanternIcons.EMBLEM, ex, ey, es, es, GreenLanternIcons.EMBLEM_SIZE, (ea << 24) | 0x5CFF90);
		// a slow scan line sweeping down
		int sy = panelY + 2 + (int) ((now / 22L) % Math.max(1, panelH - 4));
		g.fillGradient(panelX + 1, Math.max(panelY + 1, sy - 10), panelX + panelW - 1, sy, 0x0035F075, 0x0E35F075);
		g.fill(panelX + 1, sy, panelX + panelW - 1, sy + 1, 0x1835F075);
		// edges: the line, a faint inner line, and bright corner brackets
		int edgeA = (int) (0xA0 + 0x5F * pulse);
		g.renderOutline(panelX, panelY, panelW, panelH, (edgeA << 24) | 0x35F075);
		g.renderOutline(panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0x2235F075);
		int b = 10;
		corner(g, panelX, panelY, b, 1, 1, MINT);
		corner(g, panelX + panelW - 1, panelY, b, -1, 1, MINT);
		corner(g, panelX, panelY + panelH - 1, b, 1, -1, MINT);
		corner(g, panelX + panelW - 1, panelY + panelH - 1, b, -1, -1, MINT);
	}

	private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy, int color) {
		int x0 = dx > 0 ? x : x - len + 1;
		int y0 = dy > 0 ? y : y - len + 1;
		g.fill(x0, y, x0 + len, y + 1, color);
		g.fill(x, y0, x + 1, y0 + len, color);
		g.fill(dx > 0 ? x + 1 : x - 1, dy > 0 ? y + 1 : y - 1, dx > 0 ? x + 2 : x, dy > 0 ? y + 2 : y, color);
	}

	/** A horizontal line that is brightest in the middle and fades out to both ends. */
	private static void glowLine(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha) {
		int w = x1 - x0;
		int steps = 8;
		for (int i = 0; i < steps; i++) {
			float t = (i + 0.5f) / steps;
			int a = (int) (alpha * (1f - Math.abs(t - 0.5f) * 1.7f));
			if (a <= 0) {
				continue;
			}
			int sx = x0 + w * i / steps;
			int ex = x0 + w * (i + 1) / steps;
			g.fill(sx, y, ex, y + 1, (a << 24) | rgb);
			g.fill(sx, y - 1, ex, y, ((a / 5) << 24) | rgb);
			g.fill(sx, y + 1, ex, y + 2, ((a / 5) << 24) | rgb);
		}
	}

	/** A soft two-step glow around a box. */
	private static void glow(GuiGraphics g, int x, int y, int w, int h, int argb) {
		int a = argb >>> 24;
		int rgb = argb & 0xFFFFFF;
		g.renderOutline(x - 1, y - 1, w + 2, h + 2, (a << 24) | rgb);
		g.renderOutline(x - 2, y - 2, w + 4, h + 4, ((a / 2) << 24) | rgb);
		g.renderOutline(x - 3, y - 3, w + 6, h + 6, ((a / 5) << 24) | rgb);
	}

	private void small(GuiGraphics g, Component c, int x, int y, int color, float scale) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0f);
		g.pose().scale(scale, scale, 1f);
		g.drawString(this.font, c, 0, 0, color, false);
		g.pose().popPose();
	}

	private void small(GuiGraphics g, String str, int x, int y, int color, float scale) {
		small(g, Component.literal(str), x, y, color, scale);
	}

	private void renderHeader(GuiGraphics g, GreenLanternState s) {
		GreenLanternIcons.draw(g, GreenLanternIcons.EMBLEM, panelX + PAD, panelY + 6, 16, 16, GreenLanternIcons.EMBLEM_SIZE, 0xFFB8FFD0);
		Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
		int rank = GreenLanternRanks.rankFor(s.willpowerXp);
		boolean max = rank >= GreenLanternRanks.MAX_RANK;
		Component rankName = Component.translatable(GreenLanternRanks.nameKey(rank));
		int barW = Mth.clamp(panelW / 4, 60, 130);
		int barX = panelX + panelW - PAD - barW;
		int titleX = panelX + PAD + 21;
		int titleRoom = barX - 8 - titleX;
		if (this.font.width(title) <= titleRoom) {
			g.drawString(this.font, title, titleX, panelY + 10, MINT, true);
		} else {
			float sc = Math.max(0.6f, titleRoom / (float) this.font.width(title));
			small(g, title, titleX, panelY + 11, MINT, sc);
		}
		// rank: name over a glowing XP bar
		int rw = this.font.width(rankName);
		if (rw <= barW) {
			g.drawString(this.font, rankName, barX + barW - rw, panelY + 6, max ? GOLD : PALE, false);
		} else {
			small(g, rankName, barX, panelY + 7, max ? GOLD : PALE, barW / (float) rw);
		}
		float frac = rankProgress(s.willpowerXp, rank);
		int col = max ? GOLD : GREEN;
		g.fill(barX - 1, panelY + 16, barX + barW + 1, panelY + 20, 0xFF02100A);
		g.fill(barX, panelY + 17, barX + barW, panelY + 19, 0xFF0A2A16);
		int fw = Math.round(barW * frac);
		g.fill(barX, panelY + 17, barX + fw, panelY + 19, col);
		g.fill(barX, panelY + 17, barX + fw, panelY + 18, 0x60FFFFFF);
		if (fw > 0) {
			g.fill(barX + fw - 1, panelY + 15, barX + fw + 1, panelY + 21, 0xC0FFFFFF);
		}
		glowLine(g, panelX + 4, panelX + panelW - 4, panelY + 23, 0x35F075, 0xC0);
	}

	private boolean tabCompact() {
		int tw = (panelW - PAD * 2) / Tab.values().length;
		for (Tab t : Tab.values()) {
			if (16 + 6 + this.font.width(Component.translatable(t.translationKey())) + 6 > tw) {
				return true;
			}
		}
		return false;
	}

	private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
		int inner = panelW - PAD * 2;
		int n = Tab.values().length;
		int tw = inner / n;
		boolean compact = tabCompact();
		Tab over = tabAt(mouseX, mouseY);
		int y = tabY;
		for (int i = 0; i < n; i++) {
			Tab t = Tab.values()[i];
			int x = panelX + PAD + i * tw;
			int w = i == n - 1 ? inner - i * tw : tw - 2;
			boolean active = t == tab;
			boolean hov = t == over;
			if (active) {
				g.fillGradient(x, y, x + w, y + tabH, 0xD0249A4A, 0x70105A28);
				g.fill(x, y, x + w, y + 1, MINT);
				g.fill(x, y, x + 1, y + tabH, GREEN);
				g.fill(x + w - 1, y, x + w, y + tabH, GREEN);
				glow(g, x, y, w, tabH, 0x3035F075);
			} else {
				g.fillGradient(x, y + 2, x + w, y + tabH, hov ? 0xA0186E36 : 0x70082A14, hov ? 0x800C3A1C : 0x50041A0C);
				g.renderOutline(x, y + 2, w, tabH - 2, hov ? 0xA035F075 : 0x4035F075);
			}
			int count = entriesOf(t).size();
			Component name = Component.translatable(t.translationKey());
			int iconA = active || hov ? 0xFFFFFFFF : 0xB0FFFFFF;
			if (compact) {
				GreenLanternIcons.draw(g, GreenLanternIcons.of(t.icon), x + (w - 16) / 2, y + (active ? 1 : 2), 16, iconA);
			} else {
				int nw = this.font.width(name);
				int cw = 16 + 4 + nw;
				int sx = x + (w - cw) / 2;
				GreenLanternIcons.draw(g, GreenLanternIcons.of(t.icon), sx, y + (active ? 1 : 2), 16, iconA);
				g.drawString(this.font, name, sx + 20, y + (active ? 5 : 6), active ? MINT : hov ? PALE : DIM, active);
			}
			// the count, tiny, in the top-right corner
			String cs = String.valueOf(count);
			small(g, cs, x + w - 2 - Math.round(this.font.width(cs) * 0.6f), y + (active ? 2 : 4), active ? PALE : 0xFF4F9A68, 0.6f);
		}
		// the baseline under the tabs, open under the active one
		int ax = panelX + PAD + tab.ordinal() * tw;
		int aw = tab.ordinal() == n - 1 ? inner - tab.ordinal() * tw : tw - 2;
		g.fill(panelX + PAD, y + tabH, ax, y + tabH + 1, 0xC035F075);
		g.fill(ax + aw, y + tabH, panelX + PAD + inner, y + tabH + 1, 0xC035F075);
	}

	private void renderGrid(GuiGraphics g, GreenLanternState s, int mouseX, int mouseY, float dt, long now) {
		List<Entry> list = entriesOf(tab);
		int rows = (list.size() + cols - 1) / cols;
		contentHeight = rows * pitch + 3;
		clampScroll();
		if (list.isEmpty()) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable("screen.projecthero.gl_menu.empty_tab"), gridW - 16);
			int y = gridY + (gridBottom - gridY) / 2 - lines.size() * 5;
			for (FormattedCharSequence l : lines) {
				g.drawString(this.font, l, gridX + (gridW - this.font.width(l)) / 2, y, DIM, false);
				y += 10;
			}
			return;
		}
		g.enableScissor(gridX - 3, gridY, gridX + gridW + 3, gridBottom);
		Entry sel = selected();
		for (int i = 0; i < list.size(); i++) {
			Entry e = list.get(i);
			int x = cardX(i);
			int y = cardY(i);
			boolean over = e == hovered;
			float h = hoverAnim.getOrDefault(e.id(), 0f);
			h += ((over ? 1f : 0f) - h) * Math.min(1f, dt * 14f);
			hoverAnim.put(e.id(), h);
			if (y + card < gridY - 4 || y > gridBottom + 4) {
				continue;
			}
			drawCard(g, s, e, x, y, h, e == sel, now);
		}
		g.disableScissor();
		// fades where the grid is cut off, and a thin scroll bar
		int track = gridBottom - gridY;
		if (scroll > 0.5f) {
			g.fillGradient(gridX - 3, gridY, gridX + gridW + 3, gridY + 8, 0xD0051409, 0x00051409);
		}
		if (contentHeight - scroll > track + 0.5f) {
			g.fillGradient(gridX - 3, gridBottom - 8, gridX + gridW + 3, gridBottom, 0x00030C06, 0xD0030C06);
		}
		if (contentHeight > track) {
			int knob = Math.max(12, track * track / contentHeight);
			int ky = gridY + Math.round((track - knob) * (scroll / Math.max(1f, contentHeight - track)));
			int sx = gridX + gridW + 1;
			g.fill(sx, gridY, sx + 2, gridBottom, 0x3035F075);
			g.fill(sx, ky, sx + 2, ky + knob, 0xD035F075);
		}
	}

	private void drawCard(GuiGraphics g, GreenLanternState s, Entry e, int x, int y, float h, boolean sel, long now) {
		int size = card;
		float lit = Math.max(h, sel ? 0.55f : 0f);
		if (lit > 0.02f) {
			glow(g, x, y, size, size, ((int) (0x70 * lit) << 24) | 0x35F075);
		}
		g.fillGradient(x, y, x + size, y + size, lerpColor(0xA0103A1C, 0xD82A8A4A, h), lerpColor(0xA0061A0D, 0xC0124A26, h));
		g.fill(x + 1, y + 1, x + size - 1, y + 2, ((int) (0x30 + 0x50 * h) << 24) | 0x8CFFB0);
		g.renderOutline(x, y, size, size, sel ? MINT : lerpColor(0x7035F075, 0xFFA8FFC0, h));
		if (sel) {
			int t = 5;
			int c = GOLD;
			g.fill(x - 1, y - 1, x + t, y, c);
			g.fill(x - 1, y - 1, x, y + t, c);
			g.fill(x + size - t, y + size, x + size + 1, y + size + 1, c);
			g.fill(x + size, y + size - t, x + size + 1, y + size + 1, c);
		}
		if (pressing == e && !dragging) {
			float k = Mth.clamp((System.currentTimeMillis() - pressStart) / (float) SPAWN_HOLD_MS, 0f, 1f);
			int fh = Math.round((size - 2) * k);
			g.fill(x + 1, y + size - 1 - fh, x + size - 1, y + size - 1, 0x8035F075);
		}
		int lift = h > 0.5f ? 1 : 0;
		GreenLanternIcons.draw(g, GreenLanternIcons.of(e), x + (size - iconSize) / 2, y + (size - iconSize) / 2 - lift, iconSize,
				dragging && pressing == e ? 0x60FFFFFF : 0xFFFFFFFF);
		if (e.hold()) {
			// a small gold "hold" notch in the top-left corner
			g.fill(x + 2, y + 2, x + 5, y + 3, GOLD);
			g.fill(x + 2, y + 2, x + 3, y + 5, GOLD);
		}
		List<String> keys = GreenLanternBinding.keysOf(s, e.id());
		if (!keys.isEmpty()) {
			boolean sh = keys.get(0).startsWith("S");
			String badge = keys.get(0).substring(sh ? 1 : 0) + (keys.size() > 1 ? "+" : "");
			float sc = 0.75f;
			int bw = Math.round(this.font.width(badge) * sc) + 3 + (sh ? 6 : 0);
			int bh = 9;
			int bx = x + size - bw - 1;
			int by = y + size - bh - 1;
			g.pose().pushPose();
			g.pose().translate(0f, 0f, 50f);
			g.fill(bx, by, bx + bw, by + bh, 0xE0021008);
			g.renderOutline(bx, by, bw, bh, 0xA035F075);
			if (sh) {
				shiftGlyph(g, bx + 2, by + 2, GOLD);
			}
			small(g, badge, bx + 2 + (sh ? 6 : 0), by + 2, sh ? GOLD : MINT, sc);
			g.pose().popPose();
		}
	}

	private void renderDetail(GuiGraphics g, GreenLanternState s, int mouseX, int mouseY) {
		int x = detailX;
		int y0 = gridY;
		int w = detailW;
		int h = gridBottom - gridY;
		g.fillGradient(x, y0, x + w, y0 + h, 0xA0062012, 0xA0020C06);
		g.renderOutline(x, y0, w, h, 0x6035F075);
		g.fill(x + 1, y0, x + w - 1, y0 + 1, 0xC035F075);
		Entry d = hovered != null ? hovered : selected();
		if (d == null) {
			int es = Math.min(w - 20, 48);
			GreenLanternIcons.draw(g, GreenLanternIcons.EMBLEM, x + (w - es) / 2, y0 + 10, es, es, GreenLanternIcons.EMBLEM_SIZE, 0x405CFF90);
			List<FormattedCharSequence> lines = this.font.split(Component.translatable("screen.projecthero.gl_menu.empty_detail"), w - 12);
			int ty = y0 + 16 + es;
			for (FormattedCharSequence l : lines) {
				if (ty > y0 + h - 10) {
					break;
				}
				g.drawString(this.font, l, x + (w - this.font.width(l)) / 2, ty, DIM, false);
				ty += 10;
			}
			return;
		}
		boolean buttons = detailButtonsShown();
		int clipBottom = y0 + h - (buttons ? 22 : 2);
		g.enableScissor(x + 1, y0 + 1, x + w - 1, clipBottom);
		int top = y0 + 5 - Math.round(detailScroll);
		int y = top;
		// icon in a glowing frame, name beside it
		int fx = x + 6;
		glow(g, fx, y, 36, 36, 0x5035F075);
		g.fillGradient(fx, y, fx + 36, y + 36, 0xC0145A2C, 0xC0062010);
		g.renderOutline(fx, y, 36, 36, GREEN);
		GreenLanternIcons.draw(g, GreenLanternIcons.of(d), fx + 2, y + 2, 32, 0xFFFFFFFF);
		int nx = fx + 36 + 6;
		int nw = x + w - 5 - nx;
		Component name = Component.translatable(d.nameKey()).withStyle(ChatFormatting.BOLD);
		int ny = y + 2;
		if (nw >= 40) {
			List<FormattedCharSequence> nl = this.font.split(name, nw);
			for (FormattedCharSequence l : nl) {
				g.drawString(this.font, l, nx, ny, MINT, true);
				ny += 10;
			}
			small(g, Component.translatable(tabOf(d).translationKey()).getString().toUpperCase(Locale.ROOT), nx, ny + 1, DIM, 0.75f);
			ny += 9;
			y = Math.max(y + 36, ny) + 6;
		} else {
			y += 40;
			for (FormattedCharSequence l : this.font.split(name, w - 12)) {
				g.drawString(this.font, l, x + 6, y, MINT, true);
				y += 10;
			}
			y += 4;
		}
		glowLine(g, x + 3, x + w - 3, y, 0x35F075, 0x90);
		y += 5;
		for (FormattedCharSequence l : this.font.split(Component.translatable(d.descKey()), w - 12)) {
			g.drawString(this.font, l, x + 6, y, TEXT, false);
			y += 10;
		}
		y += 3;
		glowLine(g, x + 3, x + w - 3, y, 0x35F075, 0x90);
		y += 5;
		// the stats
		float costMult = GreenLanternRanks.costMult(GreenLanternRanks.rankFor(s.willpowerXp));
		y = stat(g, x, y, w, "use", Component.translatable(d.hold() ? "screen.projecthero.gl_menu.value.hold"
				: "screen.projecthero.gl_menu.value.tap"), d.hold() ? GOLD : PALE);
		float cost = cost(d);
		y = stat(g, x, y, w, "cost", cost > 0f ? Component.translatable("screen.projecthero.gl_menu.value.charge", fmt(cost * costMult))
				: Component.translatable("screen.projecthero.gl_menu.value.none"), AQUA);
		float upkeep = d.type() != null ? d.type().upkeepPerSec() : 0f;
		if (upkeep > 0f) {
			y = stat(g, x, y, w, "upkeep", Component.translatable("screen.projecthero.gl_menu.value.per_sec", fmt(upkeep * costMult)), AQUA);
		}
		int cd = cooldownTicks(d);
		y = stat(g, x, y, w, "cooldown", cd > 0 ? Component.translatable("screen.projecthero.gl_menu.value.seconds", fmt(cd / 20f))
				: Component.translatable("screen.projecthero.gl_menu.value.none"), AQUA);
		List<String> keys = GreenLanternBinding.keysOf(s, d.id());
		Component kv;
		if (keys.isEmpty()) {
			kv = Component.translatable("screen.projecthero.gl_menu.value.unbound");
		} else {
			StringBuilder sb = new StringBuilder();
			for (String key : keys) {
				sb.append(sb.length() == 0 ? "" : ", ").append(GreenLanternBinding.label(key));
			}
			kv = Component.literal(sb.toString());
		}
		y = stat(g, x, y, w, "keys", kv, keys.isEmpty() ? DIM : GOLD);
		if (d.hold()) {
			y += 2;
			for (FormattedCharSequence l : this.font.split(Component.translatable("screen.projecthero.gl_menu.c_tap_note"), w - 12)) {
				g.drawString(this.font, l, x + 6, y, 0xFFC8B060, false);
				y += 10;
			}
		}
		detailContentHeight = y + Math.round(detailScroll) - top + 4;
		g.disableScissor();
		int viewH = clipBottom - (y0 + 1);
		if (detailContentHeight > viewH) {
			detailScroll = Mth.clamp(detailScroll, 0f, detailContentHeight - viewH);
			int knob = Math.max(10, viewH * viewH / detailContentHeight);
			int ky = y0 + 1 + Math.round((viewH - knob) * (detailScroll / Math.max(1f, detailContentHeight - viewH)));
			g.fill(x + w - 3, ky, x + w - 1, ky + knob, 0xA035F075);
		} else {
			detailScroll = 0f;
		}
		if (buttons) {
			int by = gridBottom - 18;
			int bw = (w - 14) / 2;
			int over = detailButtonAt(mouseX, mouseY);
			button(g, x + 5, by, bw, Component.translatable("screen.projecthero.gl_menu.spawn"), over == 0, GREEN);
			button(g, x + 9 + bw, by, bw, Component.translatable("screen.projecthero.gl_menu.clear"), over == 1, 0xFFE07A5A);
		}
	}

	/** One label / value row of the detail panel; the value wraps under itself. Returns the next y. */
	private int stat(GuiGraphics g, int x, int y, int w, String label, Component value, int color) {
		Component l = Component.translatable("screen.projecthero.gl_menu.stat." + label);
		int lw = 0;
		for (String k : new String[] { "use", "cost", "upkeep", "cooldown", "keys" }) {
			lw = Math.max(lw, this.font.width(Component.translatable("screen.projecthero.gl_menu.stat." + k)));
		}
		int vx = x + 6 + lw + 6;
		int vw = x + w - 6 - vx;
		if (vw < 40) {
			// too narrow for two columns: label above value
			g.drawString(this.font, l, x + 6, y, DIM, false);
			y += 10;
			for (FormattedCharSequence line : this.font.split(value, w - 16)) {
				g.drawString(this.font, line, x + 10, y, color, false);
				y += 10;
			}
			return y + 1;
		}
		g.drawString(this.font, l, x + 6, y, DIM, false);
		for (FormattedCharSequence line : this.font.split(value, vw)) {
			g.drawString(this.font, line, vx, y, color, false);
			y += 10;
		}
		return y + 1;
	}

	private void button(GuiGraphics g, int x, int y, int w, Component label, boolean over, int accent) {
		int rgb = accent & 0xFFFFFF;
		if (over) {
			glow(g, x, y, w, 14, 0x60000000 | rgb);
		}
		g.fillGradient(x, y, x + w, y + 14, over ? 0xE0207A3C : 0xC0103A1C, over ? 0xE0104A24 : 0xC0061A0D);
		g.renderOutline(x, y, w, 14, over ? MINT : (0xA0000000 | rgb));
		int tw = this.font.width(label);
		if (tw <= w - 6) {
			g.drawString(this.font, label, x + (w - tw) / 2, y + 3, over ? 0xFFFFFFFF : PALE, false);
		} else {
			float sc = Math.max(0.5f, (w - 6) / (float) tw);
			small(g, label, x + 3, y + Math.round((14 - 8 * sc) / 2f), over ? 0xFFFFFFFF : PALE, sc);
		}
	}

	private void renderKeys(GuiGraphics g, GreenLanternState s, int mouseX, int mouseY, long now) {
		small(g, Component.translatable("screen.projecthero.gl_menu.keys_title").getString().toUpperCase(Locale.ROOT),
				keysX0, keysY + 1, DIM, 0.75f);
		glowLine(g, keysX0 + 46, keyX(5) + keySize, keysY + 4, 0x35F075, 0x50);
		shiftGlyph(g, keyX(6), keysY + 1, GOLD);
		String shiftLabel = Component.translatable("screen.projecthero.gl_menu.shift_group").getString().toUpperCase(Locale.ROOT);
		small(g, shiftLabel, keyX(6) + 7, keysY + 1, GOLD, 0.75f);
		glowLine(g, keyX(6) + 12 + Math.round(this.font.width(shiftLabel) * 0.75f), keyX(11) + keySize, keysY + 4, 0xE8E070, 0x50);
		Entry sel = dragging ? pressing : selected();
		float pulse = 0.5f + 0.5f * Mth.sin(now / 160f);
		int y = keyTop();
		int ks = keySize;
		for (int i = 0; i < GreenLanternBinding.KEYS.size(); i++) {
			String key = GreenLanternBinding.KEYS.get(i);
			int x = keyX(i);
			Entry e = GreenLanternBinding.resolve(s, key);
			boolean over = key.equals(hoveredKey);
			boolean shift = key.startsWith("S");
			// accept / refuse highlight while something is selected or dragged
			if (sel != null) {
				boolean ok = GreenLanternBinding.allowed(key, sel);
				if (ok && (dragging || over)) {
					glow(g, x, y, ks, ks, ((int) (0x60 + 0x60 * pulse) << 24) | 0x35F075);
				} else if (ok && dragging) {
					glow(g, x, y, ks, ks, 0x3035F075);
				} else if (!ok && (over || dragging)) {
					glow(g, x, y, ks, ks, 0x50FF5A5A);
				}
			} else if (over) {
				glow(g, x, y, ks, ks, 0x5035F075);
			}
			// the keycap: dark base, gradient face, darker lip at the bottom
			g.fill(x, y, x + ks, y + ks, 0xFF020C06);
			g.fillGradient(x + 1, y + 1, x + ks - 1, y + ks - 3, over ? 0xFF1E7A40 : 0xFF0F3E20, over ? 0xFF0F4A24 : 0xFF072412);
			g.fill(x + 1, y + ks - 3, x + ks - 1, y + ks - 1, 0xFF04170B);
			g.fill(x + 1, y + 1, x + ks - 1, y + 2, over ? 0x80D8FFE2 : 0x4035F075);
			g.renderOutline(x, y, ks, ks, over ? PALE : (shift ? 0x80E8E070 : 0x8035F075));
			if (sel != null && e == sel) {
				g.fill(x + 2, y + ks - 2, x + ks - 2, y + ks - 1, GOLD);
			}
			if (shift) {
				shiftGlyph(g, x + 3, y + 3, GOLD);
			}
			small(g, key.substring(shift ? 1 : 0), x + (shift ? 9 : 3), y + 3, shift ? GOLD : MINT, 0.75f);
			int ic = ks >= 24 ? 16 : Math.max(8, ks - 9);
			if (e != null) {
				GreenLanternIcons.draw(g, GreenLanternIcons.of(e), x + ks - ic - 2, y + ks - ic - 3, ic, 0xFFFFFFFF);
			} else {
				int mx = x + ks / 2 + 2;
				int my = y + ks / 2 + 1;
				g.fill(mx - 1, my - 1, mx + 1, my + 1, 0x5035F075);
			}
			Long fl = keyFlash.get(key);
			if (fl != null) {
				float t = (now - fl) / 450f;
				if (t >= 1f) {
					keyFlash.remove(key);
				} else {
					int grow = Math.round(t * 5f);
					int a = (int) (0xFF * (1f - t));
					g.renderOutline(x - grow, y - grow, ks + grow * 2, ks + grow * 2, (a << 24) | 0xD8FFE2);
				}
			}
		}
	}

	private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
		glowLine(g, panelX + 4, panelX + panelW - 4, footerY - 4, 0x35F075, 0x70);
		int y = footerY + (hintLines.size() == 1 ? 3 : 0);
		for (FormattedCharSequence l : hintLines) {
			g.drawString(this.font, l, panelX + PAD, y, DIM, false);
			y += 9;
		}
		button(g, resetX, footerY, resetW, Component.translatable("screen.projecthero.gl_menu.reset"), overReset(mouseX, mouseY), GREEN);
	}

	private void renderFlash(GuiGraphics g, long now) {
		if (flash == null || System.currentTimeMillis() >= flashUntil) {
			return;
		}
		List<FormattedCharSequence> lines = this.font.split(flash, Math.max(60, panelW - 40));
		int w = 0;
		for (FormattedCharSequence l : lines) {
			w = Math.max(w, this.font.width(l));
		}
		int h = lines.size() * 10 + 4;
		int y = panelY - h - 4 >= 0 ? panelY - h - 4 : panelY + 26;
		int x = this.width / 2 - w / 2 - 6;
		g.pose().pushPose();
		g.pose().translate(0f, 0f, 250f);
		g.fill(x, y, x + w + 12, y + h, 0xE0031008);
		g.renderOutline(x, y, w + 12, h, (flashColor & 0xFFFFFF) | 0xC0000000);
		int ty = y + 3;
		for (FormattedCharSequence l : lines) {
			g.drawString(this.font, l, this.width / 2 - this.font.width(l) / 2, ty, flashColor, false);
			ty += 10;
		}
		g.pose().popPose();
	}

	private static int lerpColor(int a, int b, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int aa = a >>> 24;
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int ba = b >>> 24;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	/** A 5x4 pixel "Shift" arrow. */
	private static void shiftGlyph(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 2, y, x + 3, y + 1, color);
		g.fill(x + 1, y + 1, x + 4, y + 2, color);
		g.fill(x, y + 2, x + 5, y + 3, color);
		g.fill(x + 1, y + 3, x + 4, y + 5, color);
	}

	/** The v0.17.1 16x16 atlas icon of an entry (the HUD key boxes draw these). */
	public static void icon(GuiGraphics g, Entry e, int x, int y, int size) {
		if (e.atlas() == GreenLanternBinding.ATLAS_CONSTRUCTS) {
			GreenLanternHud.icon(g, e.cell(), x, y, size);
		} else if (e.atlas() == GreenLanternBinding.ATLAS_SURVIVAL) {
			RenderSystem.enableBlend();
			g.blit(SURVIVAL_ICONS, x, y, size, size, e.cell() * 16f, 0f, 16, 16, 64, 16);
		} else if (e.atlas() == GreenLanternBinding.ATLAS_MECH) { // v0.17.1: the Hard-Light Mech
			RenderSystem.enableBlend();
			g.blit(MECH_ICON, x, y, size, size, 0f, 0f, 16, 16, 16, 16);
			RenderSystem.disableBlend();
		} else if (e.atlas() == GreenLanternBinding.ATLAS_RING_TECH) { // v0.17.1: the Hard-Light Drone and Gateway
			RenderSystem.enableBlend();
			g.blit(RING_TECH_ICONS, x, y, size, size, e.cell() * 16f, 0f, 16, 16, 32, 16);
			RenderSystem.disableBlend();
		} else {
			RenderSystem.enableBlend();
			g.blit(ENTRY_ICONS, x, y, size, size, (e.cell() % 8) * 16f, (e.cell() / 8) * 16f, 16, 16, 128, 64);
			RenderSystem.disableBlend();
		}
	}

	private static float rankProgress(long xp, int rank) {
		if (rank >= GreenLanternRanks.MAX_RANK) {
			return 1f;
		}
		long lo = GreenLanternConfig.RANK_XP[rank - 1];
		long hi = GreenLanternConfig.RANK_XP[rank];
		return Mth.clamp((xp - lo) / (float) (hi - lo), 0f, 1f);
	}

	/** The hover tooltip, used only when the window is too narrow for the detail panel. */
	private List<Component> tooltip(GreenLanternState s, Entry e) {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable(e.nameKey()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		out.addAll(TooltipWrap.wrap(this.font, Component.translatable(e.descKey()).withStyle(ChatFormatting.GRAY)));
		out.add(Component.translatable(e.hold() ? "screen.projecthero.gl_menu.hold" : "screen.projecthero.gl_menu.tap")
				.withStyle(ChatFormatting.YELLOW));
		float costMult = GreenLanternRanks.costMult(GreenLanternRanks.rankFor(s.willpowerXp));
		float cost = cost(e);
		if (cost > 0f) {
			out.add(Component.translatable("screen.projecthero.gl_menu.cost", fmt(cost * costMult)).withStyle(ChatFormatting.AQUA));
		}
		float upkeep = e.type() != null ? e.type().upkeepPerSec() : 0f;
		if (upkeep > 0f) {
			out.add(Component.translatable("screen.projecthero.gl_menu.upkeep", fmt(upkeep * costMult)).withStyle(ChatFormatting.AQUA));
		}
		List<String> keys = GreenLanternBinding.keysOf(s, e.id());
		if (!keys.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (String k : keys) {
				sb.append(sb.length() == 0 ? "" : ", ").append(GreenLanternBinding.label(k));
			}
			out.add(Component.translatable("screen.projecthero.gl_menu.bound", sb.toString()).withStyle(ChatFormatting.GOLD));
		}
		out.addAll(TooltipWrap.wrap(this.font, Component.translatable("screen.projecthero.gl_menu.controls")
				.withStyle(ChatFormatting.DARK_GRAY)));
		return out;
	}

	private List<Component> keyTooltip(GreenLanternState s, String key) {
		List<Component> out = new ArrayList<>();
		Entry e = GreenLanternBinding.resolve(s, key);
		String label = GreenLanternBinding.label(key);
		if (e == null) {
			out.addAll(TooltipWrap.wrap(this.font, Component.translatable("screen.projecthero.gl_menu.key_empty", label)
					.withStyle(ChatFormatting.GRAY)));
		} else {
			out.addAll(TooltipWrap.wrap(this.font, Component.translatable("screen.projecthero.gl_menu.key_bound", label,
					Component.translatable(e.nameKey())).withStyle(ChatFormatting.GREEN)));
		}
		Entry sel = selected();
		if (sel != null && sel != e) {
			if (GreenLanternBinding.allowed(key, sel)) {
				out.addAll(TooltipWrap.wrap(this.font, Component.translatable("screen.projecthero.gl_menu.key_click_bind",
						Component.translatable(sel.nameKey())).withStyle(ChatFormatting.YELLOW)));
			} else {
				out.addAll(TooltipWrap.wrap(this.font, Component.translatable("screen.projecthero.gl_menu.c_tap_note")
						.withStyle(ChatFormatting.RED)));
			}
		} else if (e != null && sel != e) {
			out.add(Component.translatable("screen.projecthero.gl_menu.key_click_select").withStyle(ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	private static String fmt(float v) {
		return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
	}

	/** The base Ring Charge cost shown for an entry (0 = none worth showing). */
	private static float cost(Entry e) {
		if (e.type() != null) {
			return e.type().initialCost();
		}
		return switch (e.id()) {
			case GreenLanternBinding.RING_BOLT -> GreenLanternConfig.BOLT_COST;
			case GreenLanternBinding.BLAST_WAVE -> GreenLanternConfig.BLAST_WAVE_COST;
			case GreenLanternBinding.FIST -> GreenLanternConfig.FIST_COST;
			case GreenLanternBinding.HAMMER -> GreenLanternConfig.HAMMER_COST;
			case GreenLanternBinding.MISSILES -> GreenLanternConfig.MISSILE_COST;
			case GreenLanternBinding.GIANT_HAND -> GreenLanternConfig.HAND_COST;
			case GreenLanternBinding.SHIELD -> GreenLanternConfig.SHIELD_INITIAL_COST;
			case GreenLanternBinding.DOME -> GreenLanternConfig.DOME_INITIAL_COST;
			case GreenLanternBinding.SCAN -> GreenLanternConfig.SCAN_COST;
			case GreenLanternBinding.SUIT -> GreenLanternConfig.SUIT_UP_COST;
			default -> 0f;
		};
	}

	/** The cooldown (ticks) an entry starts after use (or, for the shield, dome and turret, after it ends); 0 = none. */
	private static int cooldownTicks(Entry e) {
		if (e.type() != null) {
			return switch (e.type().name()) {
				case "SENTRY_TURRET" -> GreenLanternConfig.TURRET_COOLDOWN_TICKS;
				case "HARD_LIGHT_WALL" -> GreenLanternConfig.WALL_COOLDOWN_TICKS;
				case "BATTERING_RAM" -> GreenLanternConfig.RAM_COOLDOWN_TICKS;
				case "RESCUE_TETHER" -> GreenLanternConfig.TETHER_COOLDOWN_TICKS;
				case "BUZZSAW" -> GreenLanternConfig.BUZZSAW_COOLDOWN_TICKS;
				case "ANVIL_DROP" -> GreenLanternConfig.ANVIL_COOLDOWN_TICKS;
				case "CHAIN_SNARE" -> GreenLanternConfig.CHAINS_COOLDOWN_TICKS;
				case "EMERALD_WARRIOR" -> GreenLanternConfig.WARRIOR_COOLDOWN_TICKS;
				case "SNIPER_RIFLE" -> GreenLanternConfig.SNIPER_COOLDOWN_TICKS;
				default -> 0;
			};
		}
		return switch (e.id()) {
			case GreenLanternBinding.RING_BOLT -> GreenLanternConfig.BOLT_COOLDOWN_TICKS;
			case GreenLanternBinding.BLAST_WAVE -> GreenLanternConfig.BLAST_WAVE_COOLDOWN_TICKS;
			case GreenLanternBinding.FIST -> GreenLanternConfig.FIST_COOLDOWN_TICKS;
			case GreenLanternBinding.HAMMER -> GreenLanternConfig.HAMMER_COOLDOWN_TICKS;
			case GreenLanternBinding.GATLING -> GreenLanternConfig.GATLING_COOLDOWN_TICKS;
			case GreenLanternBinding.MISSILES -> GreenLanternConfig.MISSILE_COOLDOWN_TICKS;
			case GreenLanternBinding.GIANT_HAND -> GreenLanternConfig.HAND_COOLDOWN_TICKS;
			case GreenLanternBinding.SHIELD -> GreenLanternConfig.SHIELD_BREAK_COOLDOWN_TICKS;
			case GreenLanternBinding.DOME -> GreenLanternConfig.DOME_COOLDOWN_TICKS;
			case GreenLanternBinding.SCAN -> GreenLanternConfig.SCAN_COOLDOWN_TICKS;
			case GreenLanternBinding.OATH -> GreenLanternConfig.OATH_MODE_COOLDOWN_TICKS;
			default -> 0;
		};
	}

	private void flash(Component c, int color) {
		flash = c;
		flashColor = color;
		flashUntil = System.currentTimeMillis() + 1800;
	}

	// ---------------------------------------------------------------- actions

	private void tryBind(String key, Entry e) {
		if (e == null || key == null) {
			return;
		}
		if (!GreenLanternBinding.allowed(key, e)) {
			flash(Component.translatable(GreenLanternBinding.refusalKey(key, e)), RED);
			sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.6f);
			return;
		}
		send(GreenLanternMenuPayload.BIND, key, e.id());
		flash(Component.translatable("screen.projecthero.gl_menu.bound_to", Component.translatable(e.nameKey()),
				GreenLanternBinding.label(key)), PALE);
		keyFlash.put(key, Util.getMillis());
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.5f);
		sound(SoundEvents.BEACON_POWER_SELECT, 2f, 0.3f);
	}

	private void spawn(Entry e) {
		send(GreenLanternMenuPayload.SPAWN, "", e.id());
		sound(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
		flash(Component.translatable("screen.projecthero.gl_menu.spawned", Component.translatable(e.nameKey())), PALE);
	}

	private void clearKeys(Entry e) {
		send(GreenLanternMenuPayload.CLEAR, "", e.id());
		flash(Component.translatable("screen.projecthero.gl_menu.cleared", Component.translatable(e.nameKey())), PALE);
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f, 0.4f);
	}

	// ---------------------------------------------------------------- input

	/** The bindable key a key event is, as its base letter, or 0. */
	private static char baseKey(int keyCode, int scanCode) {
		if (ModKeyBindings.ABILITY_2.matches(keyCode, scanCode)) {
			return 'G';
		}
		if (ModKeyBindings.ABILITY_3.matches(keyCode, scanCode)) {
			return 'X';
		}
		if (ModKeyBindings.ABILITY_4.matches(keyCode, scanCode)) {
			return 'Z';
		}
		if (ModKeyBindings.ABILITY_5.matches(keyCode, scanCode)) {
			return 'V';
		}
		if (ModKeyBindings.ABILITY_6.matches(keyCode, scanCode)) {
			return 'C';
		}
		if (ModKeyBindings.POWER_SELECT.matches(keyCode, scanCode)) {
			return 'H';
		}
		return 0;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		char base = baseKey(keyCode, scanCode);
		if (base == 'C' && !cReleased) {
			return true; // the key repeat of the C that opened the menu
		}
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		if (base != 0 && hovered != null) {
			tryBind(GreenLanternBinding.key(base, shift), hovered);
			return true;
		}
		// a construct is selected: pressing a key puts it there (plain C still closes the menu)
		if (base != 0 && selected() != null && !(base == 'C' && !shift)) {
			tryBind(GreenLanternBinding.key(base, shift), selected());
			return true;
		}
		if (base == 'C') {
			onClose();
			return true;
		}
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + Tab.values().length) {
			setTab(Tab.values()[keyCode - GLFW.GLFW_KEY_1]);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_TAB) {
			int n = Tab.values().length;
			setTab(Tab.values()[(tab.ordinal() + (shift ? n - 1 : 1)) % n]);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		// closed with C (or anything else) while C is held: C stays dead until it is let go, so the held key's repeat can't
		// fire the C construct or start a new hold the moment the menu shuts
		if (cPhysicallyDown()) {
			com.projecthero.mod.client.ProjectHeroModClient.awaitGreenLanternCRelease();
		}
		super.removed();
	}

	/** v0.17.1: the real state of the key bound to C (keyboard or mouse button), whatever screen is open. */
	public static boolean cPhysicallyDown() {
		com.mojang.blaze3d.platform.InputConstants.Key key =
				net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITY_6);
		if (key == null || key.equals(com.mojang.blaze3d.platform.InputConstants.UNKNOWN)) {
			return false;
		}
		long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
		if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
				&& com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.ABILITY_6.matches(keyCode, scanCode)) {
			cReleased = true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Entry onCard = cardAt(mouseX, mouseY);
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			Tab t = tabAt(mouseX, mouseY);
			if (t != null) {
				setTab(t);
				return true;
			}
			if (overReset(mouseX, mouseY)) {
				send(GreenLanternMenuPayload.RESET, "", "");
				flash(Component.translatable("screen.projecthero.gl_menu.reset"), PALE);
				sound(SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
				return true;
			}
			int db = detailButtonAt(mouseX, mouseY);
			if (db >= 0 && selected() != null) {
				if (db == 0) {
					spawn(selected());
				} else {
					clearKeys(selected());
				}
				return true;
			}
			if (onCard != null) {
				pressing = onCard;
				pressStart = System.currentTimeMillis();
				pressX = mouseX;
				pressY = mouseY;
				dragging = false;
				return true;
			}
			String key = keyAt(mouseX, mouseY);
			if (key != null) {
				Entry sel = selected();
				GreenLanternState s = state();
				Entry bound = GreenLanternBinding.resolve(s, key);
				if (sel != null && sel != bound) {
					tryBind(key, sel);
				} else if (bound != null) {
					setTab(tabOf(bound));
					select(bound);
				}
				return true;
			}
			if (!overDetail(mouseX, mouseY) && mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridBottom) {
				select(null); // a click on empty grid space drops the selection
				return true;
			}
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && onCard != null) {
			clearKeys(onCard);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (pressing != null && !dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& Math.hypot(mouseX - pressX, mouseY - pressY) > DRAG_START) {
			dragging = true;
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.25f);
		}
		return pressing != null || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressing != null) {
			Entry e = pressing;
			pressing = null;
			if (dragging) {
				dragging = false;
				String key = keyAt(mouseX, mouseY);
				if (key != null) {
					select(e);
					tryBind(key, e);
				}
			} else {
				select(e);
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (overDetail(mouseX, mouseY)) {
			detailScroll = Math.max(0f, detailScroll - (float) scrollY * 12f);
			return true;
		}
		if (tabAt(mouseX, mouseY) != null) {
			int n = Tab.values().length;
			setTab(Tab.values()[(tab.ordinal() + (scrollY < 0 ? 1 : n - 1)) % n]);
			return true;
		}
		scrollTarget -= (float) scrollY * pitch;
		clampScroll();
		return true;
	}
}
