package com.projecthero.mod.client.greenlantern.recon;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.greenlantern.GreenLanternRingScanClient;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.recon.GreenLanternRecon;
import com.projecthero.mod.greenlantern.recon.GreenLanternReconConfig;
import com.projecthero.mod.greenlantern.recon.HardLightGatewayEntity;
import com.projecthero.mod.network.GreenLanternReconPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Hard-Light Drone and Gateway, client side -- the two renderers, the private outlines from the drone's
 * sweeps, the scout run's summary popup ("SCOUT REPORT -- 4 hostiles, 2 chests, 3 other creatures", top of the screen,
 * wrapped, fading), the gateway's channel bar under the crosshair, and opening the picker / Waypoints screens.
 */
public final class GreenLanternReconClient {
	private static final long REPORT_MS = 4500L;
	private static final long FADE_MS = 600L;
	private static final int MAX_W = 220;

	private static int[] report;
	private static long reportAt;

	private GreenLanternReconClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(GreenLanternRecon.DRONE_TYPE, HardLightDroneRenderer::new);
		EntityRendererRegistry.register(GreenLanternRecon.GATEWAY_TYPE, HardLightGatewayRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(GreenLanternReconPayload.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
		HudRenderCallback.EVENT.register(GreenLanternReconClient::render);
	}

	private static void receive(GreenLanternReconPayload p) {
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		switch (p.action()) {
			case GreenLanternReconPayload.MARKS -> GreenLanternRingScanClient.acceptDrone(p.ids(), p.a(), now);
			case GreenLanternReconPayload.SCOUT_REPORT -> {
				GreenLanternRingScanClient.acceptDrone(p.ids(), 120, now);
				showReport(p.a(), p.b(), p.c());
			}
			case GreenLanternReconPayload.OPEN_PICKER -> mc.setScreen(new GreenLanternGatewayPickerScreen(p.a()));
			case GreenLanternReconPayload.OPEN_WAYPOINTS -> mc.setScreen(new GreenLanternWaypointsScreen(p.a()));
			default -> {
			}
		}
	}

	/** Show the scout popup now (also used by the visual-check harness). */
	public static void showReport(int hostiles, int chests, int others) {
		report = new int[] { hostiles, chests, others };
		reportAt = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.8f);
		}
	}

	private static int argb(int rgb, float a) {
		return (Mth.clamp((int) (a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) {
			return;
		}
		renderReport(g, mc);
		renderChannel(g, mc, delta.getGameTimeDeltaPartialTick(false));
	}

	private static void renderReport(GuiGraphics g, Minecraft mc) {
		if (report == null) {
			return;
		}
		long age = System.currentTimeMillis() - reportAt;
		if (age > REPORT_MS) {
			report = null;
			return;
		}
		float a = age > REPORT_MS - FADE_MS ? Math.max(0.04f, (REPORT_MS - age) / (float) FADE_MS) : Math.min(1f, age / 120f + 0.1f);
		Font font = mc.font;
		int maxW = Math.min(MAX_W, g.guiWidth() - 24);
		Component tag = Component.translatable("hud.projecthero.green_lantern.scout.title");
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.addAll(font.split(Component.translatable("hud.projecthero.green_lantern.scout.hostiles", report[0]), maxW));
		lines.addAll(font.split(Component.translatable("hud.projecthero.green_lantern.scout.chests", report[1]), maxW));
		lines.addAll(font.split(Component.translatable("hud.projecthero.green_lantern.scout.others", report[2]), maxW));
		int widest = font.width(tag);
		for (FormattedCharSequence l : lines) {
			widest = Math.max(widest, font.width(l));
		}
		int cx = g.guiWidth() / 2;
		int top = 26;
		int h = 12 + lines.size() * 10;
		int x0 = cx - widest / 2 - 10;
		int x1 = cx + widest / 2 + 10;
		g.fill(x0, top - 5, x1, top + h + 2, argb(0x041A0C, 0.6f * a));
		g.fill(x0, top - 5, x1, top - 4, argb(0x5CFF8E, 0.85f * a));
		g.fill(x0, top + h + 1, x1, top + h + 2, argb(0x5CFF8E, 0.35f * a));
		g.drawString(font, tag, cx - font.width(tag) / 2, top, argb(0x5CFF8E, a), true);
		int y = top + 12;
		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence l = lines.get(i);
			int color = i == 0 && report[0] > 0 ? 0xFF7A7A : 0xC8F5D6;
			g.drawString(font, l, cx - font.width(l) / 2, y, argb(color, a), true);
			y += 10;
		}
	}

	/** While the bearer channels a gateway: "Opening gateway" and a bar filling over the 3 s, under the crosshair. */
	private static void renderChannel(GuiGraphics g, Minecraft mc, float partial) {
		GreenLanternFx fx = mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
		if (!fx.has(GreenLanternFx.CH_GATEWAY) || mc.level == null) {
			return;
		}
		float t = 0f;
		for (HardLightGatewayEntity e : mc.level.getEntitiesOfClass(HardLightGatewayEntity.class, mc.player.getBoundingBox().inflate(8.0),
				e -> e.state() == HardLightGatewayEntity.TRACE && e.ownerEntityId() == mc.player.getId())) {
			t = Math.max(t, Mth.clamp(e.stateAge(partial) / GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS, 0f, 1f));
		}
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 14;
		int w = 90;
		Component label = Component.translatable("hud.projecthero.green_lantern.gateway.channel");
		String s = font.plainSubstrByWidth(label.getString(), g.guiWidth() - 24);
		g.drawString(font, s, cx - font.width(s) / 2, y, 0xFFA8FFC0, true);
		g.fill(cx - w / 2 - 1, y + 11, cx + w / 2 + 1, y + 16, 0xA0041A0C);
		g.fill(cx - w / 2, y + 12, cx - w / 2 + Math.round(w * t), y + 15, 0xFF35F075);
		g.fill(cx - w / 2, y + 12, cx - w / 2 + Math.round(w * t), y + 13, 0xFFD8FFE2);
	}
}
