package com.projecthero.mod.client.ironman;

import java.util.UUID;

import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * v0.15.9 Sentry Mode, client side: the renderer registration, the per-draw hand-off to {@code SuperheroArmorRenderer}
 * (how far the standing suit's back is open, and whether its lights are off) and the owner's small HUD block.
 */
public final class IronManSentryClient {
	/** The back's half-shells swing this far open (degrees) when fully open. */
	public static final float OPEN_DEG = 62f;

	/** Set while a sentry's armour stand is being drawn: how far its back is open (degrees, 0 = shut). Render thread. */
	public static float openDeg;
	/** Set while a powered-down sentry is being drawn: no glow pass. Render thread. */
	public static boolean dark;
	/** Set while a sentry is being drawn: its eyes / reactor flash (0..1) during the mode-switch flourish. Render thread. */
	public static float flash;

	private static IronManSentryEntity cached;
	private static long cachedAt = Long.MIN_VALUE;

	private IronManSentryClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(IronManEntityTypes.SENTRY, IronManSentryRenderer::new);
	}

	/** The local player's own sentry, if one is loaded on this client (rescanned once a tick). */
	public static IronManSentryEntity mine() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			cached = null;
			return null;
		}
		long now = mc.level.getGameTime();
		if (now != cachedAt) {
			cachedAt = now;
			cached = null;
			UUID me = mc.player.getUUID();
			double best = Double.MAX_VALUE;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof IronManSentryEntity s && !s.isRemoved() && me.equals(s.ownerId())) {
					double d = s.distanceToSqr(mc.player);
					if (d < best) {
						best = d;
						cached = s;
					}
				}
			}
		}
		return cached;
	}

	/**
	 * The owner's status block while one of their suits stands as a sentry (drawn by {@code IronManHud} when no helmet is
	 * worn): a chip with the mode, then the suit's energy and integrity Gauges. Returns the next y.
	 */
	public static int renderHud(GuiGraphics g, Font font, int x, int y, int w) {
		IronManSentryEntity s = mine();
		if (s == null) {
			return y;
		}
		IronManSuit suit = s.suit();
		float cap = suit == null ? 0f : suit.energyCapacity();
		float maxInt = IronManEnergy.maxIntegrity(s.suitId());
		float ef = cap <= 0f ? 0f : IronManUiLayout.clamp01(s.energy() / cap);
		float inf = maxInt <= 0f ? 0f : IronManUiLayout.clamp01(s.integrity() / maxInt);
		String head = Component.translatable("hud.projecthero.ironman.sentry",
				IronManSentryEntity.modeName(s.mode())).getString();
		if (!s.powered()) {
			head = Component.translatable("hud.projecthero.ironman.sentry_offline").getString();
		}
		IronManGui.chip(g, font, x, y, w, head, s.powered() ? IronManGui.CYAN : IronManGui.RED);
		y += 13;
		g.fill(x, y - 1, x + w, y + 2 * IronManUiLayout.GAUGE_ROW_H, 0x8C050B12);
		IronManGui.gauge(g, font, x + 2, y, w - 4, Component.translatable("hud.projecthero.ironman.sentry_energy").getString(),
				IronManGui.TEXT_DIM, Math.round(ef * 100f) + "%", IronManGui.TEXT, ef, ef > 0.2f ? IronManGui.CYAN : IronManGui.ORANGE);
		y += IronManUiLayout.GAUGE_ROW_H;
		IronManGui.gauge(g, font, x + 2, y, w - 4, Component.translatable("hud.projecthero.ironman.sentry_integrity").getString(),
				IronManGui.TEXT_DIM, Math.round(inf * 100f) + "%", IronManGui.TEXT, inf, inf > 0.25f ? IronManGui.GREEN : IronManGui.RED);
		y += IronManUiLayout.GAUGE_ROW_H + 2;
		return y;
	}
}
