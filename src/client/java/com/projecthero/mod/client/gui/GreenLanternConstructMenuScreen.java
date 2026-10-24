package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Section;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.network.GreenLanternMenuPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * v0.17.1 Green Lantern revamp: the Construct Menu (hold C for 0.5 s). Every ring move and every construct in one grid,
 * in five labelled sections; the twelve bindable keys along the bottom show what each one holds.
 * <ul>
 *   <li><b>Bind</b>: hover an entry and press G / H / Z / X / C / V (hold Shift for the Shift variant).</li>
 *   <li><b>Clear</b>: right-click an entry -- every key holding it is emptied.</li>
 *   <li><b>Spawn once</b>: hold left click on an entry for 0.5 s (the cell fills); it goes off where you are aiming.</li>
 *   <li><b>Reset to default</b> puts the old fixed layout back.</li>
 * </ul>
 * C or Esc closes it when no entry is hovered. The server re-checks every request ({@code GreenLanternBindings}).
 */
public final class GreenLanternConstructMenuScreen extends Screen {
	public static final ResourceLocation ENTRY_ICONS = ProjectHeroMod.id("textures/gui/green_lantern/entries.png");

	private static final int CELL = 24;
	private static final int ICON = 16;
	private static final int PAD = 10;
	private static final int SECTION_GAP = 14;
	private static final int SPAWN_HOLD_MS = 500;

	private static final int PANEL_BG = 0xF4081A0E;
	private static final int PANEL_EDGE = 0xFF35F075;
	private static final int CELL_BG = 0xA0103A1C;
	private static final int CELL_HOVER = 0xE02A8A4A;
	private static final int GREEN = 0xFF35F075;
	private static final int PALE = 0xFFA8FFC0;
	private static final int GOLD = 0xFFE8E070;
	private static final int BADGE_BG = 0xE0000000;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int bodyTop;
	private int bodyBottom;
	private int scroll;
	private int contentHeight;
	private Entry hovered;
	/** The entry left click is being held on (spawn once), and since when (ms). */
	private Entry pressing;
	private long pressStart;
	/** C opened the menu while held: its key repeat must not close it again until it has been let go once. */
	private boolean cReleased;
	private Component flash;
	private long flashUntil;

	public GreenLanternConstructMenuScreen() {
		super(Component.translatable("screen.projecthero.gl_menu.title"));
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width - 32, 440);
		panelH = Math.min(this.height - 24, 330);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		bodyTop = panelY + 34;
		bodyBottom = panelY + panelH - 62;
		Component reset = Component.translatable("screen.projecthero.gl_menu.reset");
		int bw = this.font.width(reset) + 16;
		addRenderableWidget(Button.builder(reset, b -> send(GreenLanternMenuPayload.RESET, "", ""))
				.bounds(panelX + panelW - bw - PAD, panelY + panelH - 22, bw, 16).build());
		cReleased = !ModKeyBindings.ABILITY_6.isDown();
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

	// ---------------------------------------------------------------- layout

	/** Columns that fit across the body. */
	private int columns() {
		return Math.max(1, (panelW - PAD * 2) / CELL);
	}

	private List<Entry> entriesOf(Section s) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : GreenLanternBinding.all()) {
			if (e.section() == s) {
				out.add(e);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- render

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fillGradient(0, 0, this.width, this.height, 0x70000000, 0xA0000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		GreenLanternState s = state();
		if (s == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
		g.renderOutline(panelX, panelY, panelW, panelH, PANEL_EDGE);

		// ---- header: title + Lantern Rank and its XP bar
		GreenLanternHud.icon(g, GreenLanternHud.EMBLEM_ICON, panelX + PAD - 2, panelY + 7, 16);
		g.drawString(this.font, this.title, panelX + PAD + 18, panelY + 11, PALE, false);
		int rank = GreenLanternRanks.rankFor(s.willpowerXp);
		Component rankName = Component.translatable(GreenLanternRanks.nameKey(rank));
		int barW = Math.min(130, panelW / 3);
		int barX = panelX + panelW - PAD - barW;
		g.drawString(this.font, rankName, barX + barW - this.font.width(rankName), panelY + 7,
				rank >= GreenLanternRanks.MAX_RANK ? GOLD : GREEN, false);
		float frac = rankProgress(s.willpowerXp, rank);
		g.fill(barX, panelY + 19, barX + barW, panelY + 21, 0xFF06200E);
		g.fill(barX, panelY + 19, barX + Math.round(barW * frac), panelY + 21, rank >= GreenLanternRanks.MAX_RANK ? GOLD : GREEN);
		g.fill(panelX + PAD, panelY + 28, panelX + panelW - PAD, panelY + 29, 0x6035F075);

		// ---- body: the five sections, scrollable
		hovered = null;
		g.enableScissor(panelX + 1, bodyTop, panelX + panelW - 1, bodyBottom);
		int y = bodyTop + 2 - scroll;
		int cols = columns();
		for (Section sec : Section.values()) {
			List<Entry> list = entriesOf(sec);
			g.drawString(this.font, Component.translatable(sec.translationKey()).withStyle(ChatFormatting.BOLD),
					panelX + PAD, y, GREEN, false);
			y += 11;
			for (int i = 0; i < list.size(); i++) {
				Entry e = list.get(i);
				int cx = panelX + PAD + (i % cols) * CELL;
				int cy = y + (i / cols) * CELL;
				boolean over = mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2
						&& mouseY >= bodyTop && mouseY < bodyBottom;
				if (over) {
					hovered = e;
				}
				drawCell(g, s, e, cx, cy, over);
			}
			y += ((list.size() + cols - 1) / cols) * CELL + SECTION_GAP - 8;
		}
		contentHeight = y + scroll - bodyTop;
		g.disableScissor();
		if (contentHeight > bodyBottom - bodyTop) {
			// a thin scroll bar
			int track = bodyBottom - bodyTop;
			int knob = Math.max(12, track * track / contentHeight);
			int ky = bodyTop + Math.round((track - knob) * (scroll / (float) Math.max(1, contentHeight - track)));
			g.fill(panelX + panelW - 4, ky, panelX + panelW - 2, ky + knob, 0xA035F075);
		}

		// ---- the twelve keys
		renderKeyStrip(g, s, mouseX, mouseY);

		// ---- footer hint (wrapped to the panel)
		Component hint = Component.translatable("screen.projecthero.gl_menu.hint");
		List<FormattedCharSequence> lines = this.font.split(hint, panelW - PAD * 3 - 90);
		int hy = panelY + panelH - 24;
		for (FormattedCharSequence line : lines.subList(0, Math.min(2, lines.size()))) {
			g.drawString(this.font, line, panelX + PAD, hy, 0xFF8FCFA0, false);
			hy += 10;
		}

		// ---- hold-to-spawn progress, the tooltip, the flash message
		if (pressing != null && pressing != hovered) {
			pressing = null;
		}
		if (pressing != null && System.currentTimeMillis() - pressStart >= SPAWN_HOLD_MS) {
			send(GreenLanternMenuPayload.SPAWN, "", pressing.id());
			mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1.6f));
			flash(Component.translatable("screen.projecthero.gl_menu.spawned", Component.translatable(pressing.nameKey())));
			pressing = null;
		}
		if (flash != null && System.currentTimeMillis() < flashUntil) {
			g.drawCenteredString(this.font, flash, this.width / 2, panelY - 12, PALE);
		}
		if (hovered != null) {
			g.renderComponentTooltip(this.font, tooltip(s, hovered), mouseX, mouseY);
		}
	}

	private void drawCell(GuiGraphics g, GreenLanternState s, Entry e, int x, int y, boolean over) {
		int size = CELL - 2;
		g.fill(x, y, x + size, y + size, over ? CELL_HOVER : CELL_BG);
		g.renderOutline(x, y, size, size, over ? PALE : 0x8035F075);
		if (pressing == e) {
			float k = Mth.clamp((System.currentTimeMillis() - pressStart) / (float) SPAWN_HOLD_MS, 0f, 1f);
			int h = Math.round((size - 2) * k);
			g.fill(x + 1, y + size - 1 - h, x + size - 1, y + size - 1, 0x9035F075);
		}
		icon(g, e, x + (size - ICON) / 2, y + (size - ICON) / 2, ICON);
		if (e.hold()) {
			// a tiny "hold" pip in the top-left corner
			g.fill(x + 2, y + 2, x + 5, y + 5, 0xFFE8E070);
		}
		List<String> keys = GreenLanternBinding.keysOf(s, e.id());
		if (!keys.isEmpty()) {
			String badge = badge(keys.get(0)) + (keys.size() > 1 ? "+" : "");
			g.pose().pushPose();
			g.pose().translate(x + size, y + size, 200);
			g.pose().scale(0.5f, 0.5f, 1f);
			int w = this.font.width(badge);
			g.fill(-w - 3, -10, 0, 0, BADGE_BG);
			g.drawString(this.font, badge, -w - 1, -9, PALE, false);
			g.pose().popPose();
		}
	}

	/** The bottom strip: the twelve keys, each showing the icon of what it holds. */
	private void renderKeyStrip(GuiGraphics g, GreenLanternState s, int mouseX, int mouseY) {
		int n = GreenLanternBinding.KEYS.size();
		int slot = Math.min(30, (panelW - PAD * 2) / n);
		int x0 = panelX + (panelW - slot * n) / 2;
		int y0 = bodyBottom + 4;
		for (int i = 0; i < n; i++) {
			String key = GreenLanternBinding.KEYS.get(i);
			int x = x0 + i * slot;
			Entry e = GreenLanternBinding.resolve(s, key);
			g.fill(x + 1, y0, x + slot - 1, y0 + 26, 0xA0062010);
			g.renderOutline(x + 1, y0, slot - 2, 26, 0x6035F075);
			String label = badge(key);
			g.pose().pushPose();
			g.pose().translate(x + slot / 2f, y0 + 2, 0);
			g.pose().scale(0.5f, 0.5f, 1f);
			g.drawCenteredString(this.font, label, 0, 0, key.startsWith("S") ? GOLD : PALE);
			g.pose().popPose();
			if (e != null) {
				icon(g, e, x + (slot - 14) / 2, y0 + 9, 14);
				if (mouseX >= x && mouseX < x + slot && mouseY >= y0 && mouseY < y0 + 26 && hovered == null) {
					g.renderTooltip(this.font, Component.literal(GreenLanternBinding.label(key) + ": ")
							.append(Component.translatable(e.nameKey())), mouseX, mouseY);
				}
			}
		}
	}

	private static String badge(String key) {
		return key.startsWith("S") ? "⇧" + key.substring(1) : key;
	}

	public static void icon(GuiGraphics g, Entry e, int x, int y, int size) {
		if (e.atlas() == GreenLanternBinding.ATLAS_CONSTRUCTS) {
			GreenLanternHud.icon(g, e.cell(), x, y, size);
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

	private void flash(Component c) {
		flash = c;
		flashUntil = System.currentTimeMillis() + 1800;
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
		if (base != 0 && hovered != null) {
			String key = GreenLanternBinding.key(base, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
			if (!GreenLanternBinding.allowed(key, hovered)) {
				flash(Component.translatable("message.projecthero.green_lantern.menu.c_tap_only").withStyle(ChatFormatting.RED));
				return true;
			}
			send(GreenLanternMenuPayload.BIND, key, hovered.id());
			flash(Component.translatable("screen.projecthero.gl_menu.bound_to", Component.translatable(hovered.nameKey()),
					GreenLanternBinding.label(key)));
			Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.4f));
			return true;
		}
		if (base == 'C') {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
		if (hovered != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressing = hovered;
			pressStart = System.currentTimeMillis();
			return true;
		}
		if (hovered != null && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			send(GreenLanternMenuPayload.CLEAR, "", hovered.id());
			flash(Component.translatable("screen.projecthero.gl_menu.cleared", Component.translatable(hovered.nameKey())));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressing = null;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, contentHeight - (bodyBottom - bodyTop));
		scroll = Mth.clamp(scroll - (int) Math.round(scrollY * 18), 0, max);
		return true;
	}
}
