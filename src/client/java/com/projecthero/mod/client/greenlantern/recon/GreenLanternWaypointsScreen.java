package com.projecthero.mod.client.greenlantern.recon;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.recon.GreenLanternReconConfig;
import com.projecthero.mod.greenlantern.recon.GreenLanternWaypoints;
import com.projecthero.mod.network.GreenLanternGatewayPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the ring's Waypoints screen (Shift + the Hard-Light Gateway key). Up to five named waypoints, each with its
 * coordinates, its world and how far it is; one in another world is greyed out with the reason (gateways stay in one
 * world). Type a name and "Mark here" saves where you stand; Rename edits a name in place; Delete forgets it. The server
 * re-checks every change ({@code GreenLanternGateway}); the list is the synced attachment, so it redraws as changes land.
 */
public final class GreenLanternWaypointsScreen extends Screen {
	private static final int ROW_H = 30;

	private final int cooldownSeconds;
	private final long openedAt = Util.getMillis();
	private int px;
	private int py;
	private int pw;
	private int ph;
	private EditBox nameBox;
	private ReconUi.LightButton addButton;
	/** The row being renamed (-1 = none) and its edit box. */
	private int renaming = -1;
	private EditBox renameBox;
	private GreenLanternWaypoints shown;

	public GreenLanternWaypointsScreen(int cooldownSeconds) {
		super(Component.translatable("screen.projecthero.gl_waypoints.title"));
		this.cooldownSeconds = cooldownSeconds;
	}

	static GreenLanternWaypoints waypoints() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? GreenLanternWaypoints.EMPTY
				: mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_WAYPOINTS, GreenLanternWaypoints.EMPTY);
	}

	static String dimensionHere() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().location().toString();
	}

	@Override
	protected void init() {
		pw = Math.min(380, this.width - 24);
		ph = Math.min(this.height - 24, 70 + GreenLanternReconConfig.MAX_WAYPOINTS * ROW_H + 44);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		rebuild();
	}

	private void rebuild() {
		String typed = nameBox != null ? nameBox.getValue() : "";
		String renameText = renameBox != null ? renameBox.getValue() : null;
		clearWidgets();
		shown = waypoints();
		int listTop = py + 40;
		for (int i = 0; i < shown.size(); i++) {
			final int idx = i;
			int ry = listTop + i * ROW_H;
			int bx = px + pw - 12;
			if (renaming == i) {
				renameBox = new EditBox(this.font, px + 34, ry + 3, pw - 34 - 12 - 2 * 52 - 8, 14, Component.empty());
				renameBox.setMaxLength(GreenLanternReconConfig.WAYPOINT_NAME_MAX);
				renameBox.setValue(renameText != null ? renameText : shown.get(i).name());
				addRenderableWidget(renameBox);
				setFocused(renameBox);
				addRenderableWidget(new ReconUi.LightButton(bx - 48, ry + 3, 48, 14, Component.translatable("screen.projecthero.gl_waypoints.cancel"),
						ReconUi.DIM, () -> {
							renaming = -1;
							rebuild();
						}));
				addRenderableWidget(new ReconUi.LightButton(bx - 100, ry + 3, 48, 14, Component.translatable("screen.projecthero.gl_waypoints.save"),
						ReconUi.GREEN, () -> commitRename(idx)));
			} else {
				addRenderableWidget(new ReconUi.LightButton(bx - 48, ry + 5, 48, 14, Component.translatable("screen.projecthero.gl_waypoints.delete"),
						ReconUi.RED, () -> {
							send(GreenLanternGatewayPayload.DELETE, idx, "");
							ReconUi.click(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.6f);
							renaming = -1;
						}));
				addRenderableWidget(new ReconUi.LightButton(bx - 100, ry + 5, 48, 14, Component.translatable("screen.projecthero.gl_waypoints.rename"),
						ReconUi.PALE, () -> {
							renaming = idx;
							renameBox = null;
							rebuild();
						}));
			}
		}
		int by = py + ph - 30;
		nameBox = new EditBox(this.font, px + 12, by, pw - 24 - 96, 16, Component.translatable("screen.projecthero.gl_waypoints.name_hint"));
		nameBox.setMaxLength(GreenLanternReconConfig.WAYPOINT_NAME_MAX);
		nameBox.setHint(Component.translatable("screen.projecthero.gl_waypoints.name_hint").withStyle(ChatFormatting.DARK_GRAY));
		nameBox.setValue(typed);
		addRenderableWidget(nameBox);
		addButton = new ReconUi.LightButton(px + pw - 12 - 90, by, 90, 16, Component.translatable("screen.projecthero.gl_waypoints.add"),
				ReconUi.GREEN, this::add);
		addButton.active = !shown.full();
		nameBox.setEditable(!shown.full());
		addRenderableWidget(addButton);
		if (renaming < 0) {
			setInitialFocus(nameBox);
		}
	}

	private void add() {
		if (waypoints().full()) {
			return;
		}
		send(GreenLanternGatewayPayload.ADD, -1, nameBox.getValue());
		nameBox.setValue("");
		ReconUi.click(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.7f);
	}

	private void commitRename(int idx) {
		if (renameBox != null && !GreenLanternWaypoints.clean(renameBox.getValue()).isEmpty()) {
			send(GreenLanternGatewayPayload.RENAME, idx, renameBox.getValue());
			ReconUi.click(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.5f);
		}
		renaming = -1;
		renameBox = null;
		rebuild();
	}

	static void send(int action, int index, String name) {
		if (ClientPlayNetworking.canSend(GreenLanternGatewayPayload.TYPE)) {
			ClientPlayNetworking.send(new GreenLanternGatewayPayload(action, index, name == null ? "" : name));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!waypoints().equals(shown)) {
			rebuild(); // a change landed
		}
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == 257 || key == 335) { // Enter
			if (renaming >= 0) {
				commitRename(renaming);
				return true;
			}
			if (nameBox != null && nameBox.isFocused()) {
				add();
				return true;
			}
		}
		if (key == 256 && renaming >= 0) { // Esc leaves the rename first
			renaming = -1;
			rebuild();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	/** The backdrop, the panel and the row plates (Screen#render draws this first, then the widgets). */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		long now = Util.getMillis();
		float open = Mth.clamp((now - openedAt) / 200f, 0f, 1f);
		ReconUi.backdrop(g, this.width, this.height);
		int grow = Math.round((1f - open) * 6f);
		ReconUi.panel(g, px - grow, py - grow, pw + grow * 2, ph + grow * 2, now);
		ReconUi.glowLine(g, px + 8, px + pw - 8, py + 35, 0x35F075, 0x90);
		ReconUi.glowLine(g, px + 8, px + pw - 8, py + ph - 38, 0x35F075, 0x60);
		GreenLanternWaypoints w = waypoints();
		String here = dimensionHere();
		for (int i = 0; i < w.size(); i++) {
			int ry = py + 40 + i * ROW_H;
			boolean same = w.get(i).dimension().equals(here);
			boolean over = mouseX >= px + 8 && mouseX < px + pw - 8 && mouseY >= ry && mouseY < ry + ROW_H - 2;
			g.fill(px + 8, ry, px + pw - 8, ry + ROW_H - 2, over ? 0x5014502A : 0x3010301A);
			g.renderOutline(px + 8, ry, pw - 16, ROW_H - 2, same ? 0x5035F075 : 0x306E7A72);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		// header
		Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
		g.drawString(this.font, title, px + 12, py + 10, ReconUi.GREEN, false);
		String count = waypoints().size() + " / " + GreenLanternReconConfig.MAX_WAYPOINTS;
		g.drawString(this.font, count, px + pw - 12 - this.font.width(count), py + 10, ReconUi.PALE, false);
		Component sub = cooldownSeconds > 0
				? Component.translatable("screen.projecthero.gl_waypoints.cooldown", time(cooldownSeconds))
				: Component.translatable("screen.projecthero.gl_waypoints.subtitle");
		ReconUi.wrapped(g, this.font, sub, px + 12, py + 22, pw - 24, cooldownSeconds > 0 ? ReconUi.GOLD : ReconUi.DIM, 1);
		renderRows(g, mouseX, mouseY);
		// footer
		if (waypoints().full()) {
			ReconUi.wrapped(g, this.font, Component.translatable("screen.projecthero.gl_waypoints.full", GreenLanternReconConfig.MAX_WAYPOINTS),
					px + 12, py + ph - 50, pw - 24, ReconUi.GOLD, 1);
		}
	}

	private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
		GreenLanternWaypoints w = waypoints();
		int listTop = py + 40;
		String here = dimensionHere();
		Minecraft mc = Minecraft.getInstance();
		if (w.size() == 0) {
			ReconUi.wrapped(g, this.font, Component.translatable("screen.projecthero.gl_waypoints.empty"), px + 16, listTop + 8, pw - 32,
					ReconUi.TEXT, 3);
			return;
		}
		for (int i = 0; i < w.size(); i++) {
			GreenLanternWaypoints.Waypoint wp = w.get(i);
			int ry = listTop + i * ROW_H;
			boolean same = wp.dimension().equals(here);
			// the number badge
			int badge = same ? ReconUi.GREEN : ReconUi.GREY;
			g.renderOutline(px + 13, ry + 6, 16, 16, badge);
			String n = String.valueOf(i + 1);
			g.drawString(this.font, n, px + 21 - this.font.width(n) / 2, ry + 10, badge, false);
			if (renaming == i) {
				continue;
			}
			int textW = pw - 34 - 12 - 2 * 52 - 10;
			g.drawString(this.font, ReconUi.fit(this.font, wp.name(), textW), px + 34, ry + 5, same ? ReconUi.MINT : ReconUi.GREY, false);
			String line;
			int color;
			if (same && mc.player != null) {
				int dist = (int) Math.round(wp.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
				line = Component.translatable("screen.projecthero.gl_waypoints.where", ReconUi.coords(wp), dist).getString();
				color = ReconUi.DIM;
			} else {
				line = Component.translatable("screen.projecthero.gl_waypoints.other_world", ReconUi.dimensionName(wp.dimension())).getString();
				color = ReconUi.GREY;
			}
			g.drawString(this.font, ReconUi.fit(this.font, line, textW), px + 34, ry + 16, color, false);
		}
	}

	static String time(int seconds) {
		return seconds >= 60 ? (seconds / 60) + ":" + String.format("%02d", seconds % 60) : seconds + "s";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
