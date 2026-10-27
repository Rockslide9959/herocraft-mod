package com.projecthero.mod.client.greenlantern.recon;

import com.projecthero.mod.greenlantern.recon.GreenLanternReconConfig;
import com.projecthero.mod.greenlantern.recon.GreenLanternWaypoints;
import com.projecthero.mod.network.GreenLanternGatewayPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Hard-Light Gateway's waypoint picker (a tap of the Gateway key) -- a compact list of the ring's waypoints
 * with how far each is and what the gateway would cost. Click one, or press its number, to start the channel. A
 * waypoint in another world, or one too close to need a door, is greyed out with the reason; while the gateway recharges
 * every row is. "Waypoints..." opens the Waypoints screen.
 */
public final class GreenLanternGatewayPickerScreen extends Screen {
	private static final int ROW_H = 22;

	private final int cooldownSeconds;
	private final long openedAt = Util.getMillis();
	private int px;
	private int py;
	private int pw;
	private int ph;

	public GreenLanternGatewayPickerScreen(int cooldownSeconds) {
		super(Component.translatable("screen.projecthero.gl_gateway.title"));
		this.cooldownSeconds = cooldownSeconds;
	}

	@Override
	protected void init() {
		int n = Math.max(1, GreenLanternWaypointsScreen.waypoints().size());
		pw = Math.min(280, this.width - 24);
		ph = Math.min(this.height - 24, 44 + n * ROW_H + 34);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		addRenderableWidget(new ReconUi.LightButton(px + pw - 12 - 96, py + ph - 24, 96, 16,
				Component.translatable("screen.projecthero.gl_gateway.manage"), ReconUi.PALE,
				() -> Minecraft.getInstance().setScreen(new GreenLanternWaypointsScreen(cooldownSeconds))));
	}

	/** Why waypoint {@code i} can't be picked right now (a lang key), or null. */
	private String refusal(GreenLanternWaypoints.Waypoint w) {
		Minecraft mc = Minecraft.getInstance();
		if (!w.dimension().equals(GreenLanternWaypointsScreen.dimensionHere())) {
			return "screen.projecthero.gl_gateway.other_world";
		}
		if (mc.player != null && w.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()) < GreenLanternReconConfig.GATEWAY_MIN_DISTANCE) {
			return "screen.projecthero.gl_gateway.too_close";
		}
		if (cooldownSeconds > 0) {
			return "screen.projecthero.gl_gateway.recharging";
		}
		return null;
	}

	private int rowAt(double mx, double my) {
		int top = py + 40;
		if (mx < px + 8 || mx >= px + pw - 8 || my < top) {
			return -1;
		}
		int i = (int) ((my - top) / ROW_H);
		return i < GreenLanternWaypointsScreen.waypoints().size() ? i : -1;
	}

	private boolean pick(int i) {
		GreenLanternWaypoints.Waypoint w = GreenLanternWaypointsScreen.waypoints().get(i);
		if (w == null || refusal(w) != null) {
			ReconUi.click(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.7f);
			return false;
		}
		GreenLanternWaypointsScreen.send(GreenLanternGatewayPayload.OPEN, i, "");
		ReconUi.click(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
		onClose();
		return true;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			int i = rowAt(mx, my);
			if (i >= 0) {
				pick(i);
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key >= 49 && key < 49 + GreenLanternReconConfig.MAX_WAYPOINTS) { // 1..5
			pick(key - 49);
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		long now = Util.getMillis();
		float open = Mth.clamp((now - openedAt) / 180f, 0f, 1f);
		ReconUi.backdrop(g, this.width, this.height);
		int grow = Math.round((1f - open) * 5f);
		ReconUi.panel(g, px - grow, py - grow, pw + grow * 2, ph + grow * 2, now);
		ReconUi.glowLine(g, px + 8, px + pw - 8, py + 35, 0x35F075, 0x90);
		GreenLanternWaypoints w = GreenLanternWaypointsScreen.waypoints();
		int hovered = rowAt(mouseX, mouseY);
		for (int i = 0; i < w.size(); i++) {
			int ry = py + 40 + i * ROW_H;
			boolean ok = refusal(w.get(i)) == null;
			g.fill(px + 8, ry, px + pw - 8, ry + ROW_H - 2, i == hovered && ok ? 0x6014602E : 0x3010301A);
			g.renderOutline(px + 8, ry, pw - 16, ROW_H - 2, ok ? (i == hovered ? 0xC035F075 : 0x5035F075) : 0x306E7A72);
			if (i == hovered && ok) {
				ReconUi.glow(g, px + 8, ry, pw - 16, ROW_H - 2, 0x4035F075);
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), px + 12, py + 10, ReconUi.GREEN, false);
		Component status = cooldownSeconds > 0
				? Component.translatable("screen.projecthero.gl_waypoints.cooldown", GreenLanternWaypointsScreen.time(cooldownSeconds))
				: Component.translatable("screen.projecthero.gl_gateway.subtitle");
		ReconUi.wrapped(g, this.font, status, px + 12, py + 22, pw - 24, cooldownSeconds > 0 ? ReconUi.GOLD : ReconUi.DIM, 1);
		GreenLanternWaypoints w = GreenLanternWaypointsScreen.waypoints();
		Minecraft mc = Minecraft.getInstance();
		if (w.size() == 0) {
			ReconUi.wrapped(g, this.font, Component.translatable("screen.projecthero.gl_gateway.empty"), px + 14, py + 44, pw - 28, ReconUi.TEXT, 2);
			return;
		}
		for (int i = 0; i < w.size(); i++) {
			GreenLanternWaypoints.Waypoint wp = w.get(i);
			int ry = py + 40 + i * ROW_H;
			String why = refusal(wp);
			boolean ok = why == null;
			String num = String.valueOf(i + 1);
			g.drawString(this.font, num, px + 15, ry + 6, ok ? ReconUi.GREEN : ReconUi.GREY, false);
			String right;
			int rightColor;
			if (ok || "screen.projecthero.gl_gateway.recharging".equals(why)) {
				double d = mc.player == null ? 0 : wp.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
				right = Component.translatable("screen.projecthero.gl_gateway.cost", (int) Math.round(d),
						Math.round(GreenLanternReconConfig.gatewayCost(d))).getString();
				rightColor = ok ? ReconUi.PALE : ReconUi.GREY;
			} else if ("screen.projecthero.gl_gateway.other_world".equals(why)) {
				right = Component.translatable(why, ReconUi.dimensionName(wp.dimension())).getString();
				rightColor = ReconUi.GREY;
			} else {
				right = Component.translatable(why).getString();
				rightColor = ReconUi.GREY;
			}
			int maxRight = (pw - 40) / 2;
			right = ReconUi.fit(this.font, right, maxRight);
			int rw = this.font.width(right);
			g.drawString(this.font, right, px + pw - 14 - rw, ry + 6, rightColor, false);
			int nameW = pw - 40 - rw - 10;
			g.drawString(this.font, ReconUi.fit(this.font, wp.name(), nameW), px + 28, ry + 6, ok ? ReconUi.MINT : ReconUi.GREY, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
