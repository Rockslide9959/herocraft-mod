package com.projecthero.mod.client.miraculous.ladybug;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.miraculous.ladybug.network.LadybugGlowPayload;
import com.projecthero.mod.miraculous.ladybug.network.LadybugPingPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: the client side of Ladybug's outlines and pings. {@link LadybugGlowPayload} (sent to her alone) outlines
 * the Scan's threats, the Phone's squadmates and Ladybug Vision's charm and threats red, through two client mixins
 * that only this client's renderer asks. {@link LadybugPingPayload} (a squadmate's Phone) puts a compass marker at the
 * top of the screen: a red ladybug sliding along a strip to show which way she is, with her name and distance.
 */
public final class LadybugGlowClient {
	public static final int RED = 0xFF1E2A;
	private static final Map<Integer, Long> MARKED = new ConcurrentHashMap<>();
	private static LadybugPingPayload ping;
	private static long pingUntil;

	private LadybugGlowClient() {
	}

	static void receive(LadybugGlowPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long until = mc.level.getGameTime() + payload.ticks();
		for (int id : payload.ids()) {
			MARKED.merge(id, until, Math::max);
		}
	}

	static void receive(LadybugPingPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		ping = payload;
		pingUntil = mc.level.getGameTime() + payload.ticks();
	}

	public static boolean marked(Entity e) {
		Long until = MARKED.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until || e.isRemoved()) {
			MARKED.remove(e.getId());
			return false;
		}
		return true;
	}

	static void reset() {
		MARKED.clear();
		ping = null;
	}

	/** The squadmate ping's compass marker. */
	static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (ping == null || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		if (mc.level.getGameTime() > pingUntil) {
			ping = null;
			return;
		}
		double dx = ping.x() - mc.player.getX(), dz = ping.z() - mc.player.getZ();
		float bearing = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float rel = Mth.wrapDegrees(bearing - mc.player.getYRot());
		int cx = g.guiWidth() / 2, y = 22, half = 70;
		g.fill(cx - half, y + 4, cx + half, y + 5, 0x88FFFFFF);
		g.fill(cx - 1, y + 1, cx + 1, y + 8, 0xCCFFFFFF);
		int mx = cx + Math.round(Mth.clamp(rel / 90f, -1f, 1f) * half);
		// the ladybug: a red dome, black head and spots
		g.fill(mx - 4, y, mx + 4, y + 9, 0xFFE0242C);
		g.fill(mx - 4, y, mx + 4, y + 2, 0xFF141012);
		g.fill(mx - 1, y + 2, mx + 1, y + 9, 0xFF141012);
		g.fill(mx - 3, y + 4, mx - 2, y + 6, 0xFF141012);
		g.fill(mx + 2, y + 4, mx + 3, y + 6, 0xFF141012);
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Font font = mc.font;
		Component label = Component.translatable("hud.projecthero.ladybug.ping", ping.name(), dist).withStyle(ChatFormatting.RED);
		// one short line; clipped to the strip so it can never sprawl
		String text = font.plainSubstrByWidth(label.getString(), half * 2 + 40);
		g.drawString(font, text, cx - font.width(text) / 2, y + 12, 0xFFFF5A60, true);
	}
}
