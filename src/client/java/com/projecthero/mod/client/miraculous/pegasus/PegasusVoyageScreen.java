package com.projecthero.mod.client.miraculous.pegasus;

import java.util.List;

import com.projecthero.mod.miraculous.pegasus.PegasusConfig;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageChoicePayload;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageMenuPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.16.6, Shift+Z (Voyage): pick where the big portal opens -- your bed / spawn point, the world spawn, each squadmate
 * online, your waypoints -- one row each with its coordinates and world; the server re-checks the pick
 * ({@link PegasusVoyageChoicePayload}). Gold on night blue, a hexagon under the title. Long lists scroll (mouse wheel);
 * every line of text wraps or is trimmed to the panel.
 */
public final class PegasusVoyageScreen extends Screen {
	private static final int PANEL_W = 280;
	private static final int ROW_H = 24;
	private static final int GOLD = 0xFFE8C276;
	private static final int GOLD_DIM = 0xFF8C7536;
	private static final int TEXT = 0xFFF2EEE4;
	private static final int MUTED = 0xFF9A98A8;

	private final List<PegasusVoyageMenuPayload.Dest> dests;
	private final String hereDim;
	private int scroll;
	private boolean picked;

	private PegasusVoyageScreen(List<PegasusVoyageMenuPayload.Dest> dests, String hereDim) {
		super(Component.translatable("screen.projecthero.pegasus_voyage.title"));
		this.dests = dests;
		this.hereDim = hereDim;
	}

	static void receive(PegasusVoyageMenuPayload payload) {
		Minecraft.getInstance().setScreen(new PegasusVoyageScreen(List.copyOf(payload.destinations()), payload.hereDim()));
	}

	private int rows() {
		return Math.max(1, Math.min(dests.size(), (this.height - 110) / ROW_H));
	}

	private int panelH() {
		return 84 + rows() * ROW_H;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	@Override
	protected void init() {
		int l = left(), t = top();
		scroll = Mth.clamp(scroll, 0, Math.max(0, dests.size() - rows()));
		for (int i = 0; i < rows() && i + scroll < dests.size(); i++) {
			final int index = i + scroll;
			PegasusVoyageMenuPayload.Dest d = dests.get(index);
			addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.pegasus_voyage.go"), b -> pick(index))
					.bounds(l + PANEL_W - 62, t + 58 + i * ROW_H, 52, 18).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(l + PANEL_W / 2 - 40, t + panelH() - 24, 80, 18).build());
	}

	private void pick(int index) {
		picked = true;
		ClientPlayNetworking.send(new PegasusVoyageChoicePayload(index));
		Minecraft.getInstance().setScreen(null);
	}

	@Override
	public void onClose() {
		if (!picked) {
			picked = true;
			ClientPlayNetworking.send(new PegasusVoyageChoicePayload(-1));
		}
		super.onClose();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		int max = Math.max(0, dests.size() - rows());
		int ns = Mth.clamp(scroll - (int) Math.signum(dy), 0, max);
		if (ns != scroll) {
			scroll = ns;
			rebuildWidgets();
		}
		return true;
	}

	private Component label(PegasusVoyageMenuPayload.Dest d) {
		return switch (d.kind()) {
			case PegasusVoyageMenuPayload.BED -> Component.translatable("screen.projecthero.pegasus_voyage.bed");
			case PegasusVoyageMenuPayload.WORLD_SPAWN -> Component.translatable("screen.projecthero.pegasus_voyage.world_spawn");
			case PegasusVoyageMenuPayload.SQUADMATE -> Component.translatable("screen.projecthero.pegasus_voyage.squadmate", d.name());
			default -> Component.translatable("screen.projecthero.pegasus_voyage.waypoint", d.name());
		};
	}

	private static String dimName(String id) {
		int c = id.indexOf(':');
		String path = c >= 0 ? id.substring(c + 1) : id;
		return path.replace('_', ' ');
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		int l = left(), t = top(), h = panelH();
		g.fillGradient(l, t, l + PANEL_W, t + h, 0xF2141A2E, 0xF20A0D18);
		g.renderOutline(l, t, PANEL_W, h, GOLD_DIM);
		for (int i = 0; i < rows() && i + scroll < dests.size(); i++) {
			int ry = t + 56 + i * ROW_H;
			g.fill(l + 8, ry, l + PANEL_W - 8, ry + ROW_H - 2, 0x60283050);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int l = left(), t = top(), h = panelH();
		g.drawCenteredString(this.font, this.title, l + PANEL_W / 2, t + 8, GOLD);
		// a little hexagon under the title
		int cx = l + PANEL_W / 2, cy = t + 24;
		for (int k = 0; k < 6; k++) {
			double a0 = Math.PI / 2 + k * Math.PI / 3, a1 = a0 + Math.PI / 3;
			line(g, cx + (int) Math.round(Math.cos(a0) * 5), cy + (int) Math.round(Math.sin(a0) * 5), cx + (int) Math.round(Math.cos(a1) * 5),
					cy + (int) Math.round(Math.sin(a1) * 5), GOLD);
		}
		List<FormattedCharSequence> hint = this.font.split(Component.translatable("screen.projecthero.pegasus_voyage.hint", PegasusConfig.VOYAGE_TICKS / 20),
				PANEL_W - 20);
		int y = t + 34;
		for (int i = 0; i < Math.min(2, hint.size()); i++) {
			g.drawCenteredString(this.font, hint.get(i), cx, y, MUTED);
			y += 10;
		}
		if (dests.isEmpty()) {
			g.drawCenteredString(this.font, Component.translatable("screen.projecthero.pegasus_voyage.none"), cx, t + 62, MUTED);
		}
		for (int i = 0; i < rows() && i + scroll < dests.size(); i++) {
			PegasusVoyageMenuPayload.Dest d = dests.get(i + scroll);
			int ry = t + 56 + i * ROW_H;
			int textW = PANEL_W - 86;
			g.drawString(this.font, this.font.plainSubstrByWidth(label(d).getString(), textW), l + 14, ry + 3, TEXT, false);
			boolean other = !d.dim().equals(hereDim);
			String where = d.x() + ", " + d.y() + ", " + d.z() + (other ? "  (" + dimName(d.dim()) + ")" : "");
			g.drawString(this.font, this.font.plainSubstrByWidth(where, textW), l + 14, ry + 13, other ? GOLD_DIM : MUTED, false);
		}
		if (dests.size() > rows()) {
			g.drawString(this.font, (scroll + 1) + "-" + Math.min(dests.size(), scroll + rows()) + " / " + dests.size(), l + 10, t + h - 20, MUTED, false);
		}
	}

	private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
		int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= n; i++) {
			int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
			g.fill(x, y, x + 1, y + 1, c);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
