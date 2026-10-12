package com.projecthero.mod.client.gui;

import com.projecthero.mod.client.greenlantern.GreenLanternSniperClient;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternSniper;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.16.1: the Green Lantern Sniper Rifle's scope, drawn over the world while scoped in first person. Built like the
 * Punisher sniper's {@link ScopeOverlay} (procedural {@code fill()} rows, a round sight with a soft edge, a slight sway)
 * but made of hard light: the tube is a deep translucent green instead of black, the lens is rimmed with a glowing green
 * ring, the reticle is fine green light with a hollow centre, and a ring of light round the centre fills as the shot
 * charges (pulsing once it is full). The scope "forms" with a short green flash rather than the black blink.
 */
public final class GreenLanternScopeOverlay {
	private static final int SURROUND = 0xE8020E06;
	private static final int RIM = 0xE035F075;
	private static final int RIM_GLOW = 0x5535F075;
	private static final int LENS = 0x1035F075;
	private static final int LINE = 0xE05CFF8E;
	private static final int POST = 0xC035F075;
	private static final int BRIGHT = 0xFFE8FFEE;
	private static final int DIM = 0x6035F075;

	private static long scopedSince = -1L;
	private static float swayPhase;

	private GreenLanternScopeOverlay() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !GreenLanternSniperClient.scopedFirstPerson()) {
			scopedSince = -1L;
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		if (scopedSince < 0L) {
			scopedSince = now;
		}
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		double speed = mc.player.getDeltaMovement().horizontalDistance();
		swayPhase += 0.04f + (float) speed * 0.3f;
		float swayAmt = (float) Math.min(3.0, 0.4 + speed * 10.0);
		float cx = sw / 2f + Mth.sin(swayPhase) * swayAmt;
		float cy = sh / 2f + Mth.cos(swayPhase * 0.7f) * swayAmt * 0.7f;
		float radius = sh * 0.46f;

		// the tube: deep green outside, a glowing rim of light, a faint green lens
		for (int row = 0; row < sh; row++) {
			float dy = row + 0.5f - cy;
			if (Math.abs(dy) >= radius) {
				g.fill(0, row, sw, row + 1, SURROUND);
				continue;
			}
			int outer = half(radius, dy);
			g.fill(0, row, Math.round(cx - outer), row + 1, SURROUND);
			g.fill(Math.round(cx + outer), row, sw, row + 1, SURROUND);
			int rimIn = Math.abs(dy) < radius * 0.975f ? half(radius * 0.975f, dy) : 0;
			g.fill(Math.round(cx - outer), row, Math.round(cx - rimIn), row + 1, RIM);
			g.fill(Math.round(cx + rimIn), row, Math.round(cx + outer), row + 1, RIM);
			if (rimIn > 0) {
				int glowIn = Math.abs(dy) < radius * 0.91f ? half(radius * 0.91f, dy) : 0;
				g.fill(Math.round(cx - rimIn), row, Math.round(cx - glowIn), row + 1, RIM_GLOW);
				g.fill(Math.round(cx + glowIn), row, Math.round(cx + rimIn), row + 1, RIM_GLOW);
				if (glowIn > 0) {
					g.fill(Math.round(cx - glowIn), row, Math.round(cx + glowIn), row + 1, LENS);
				}
			}
		}

		int icx = Math.round(cx);
		int icy = Math.round(cy);
		int r = Math.round(radius * 0.9f);
		int gap = Math.max(6, Math.round(radius * 0.1f));
		// heavy posts from the rim, fine lines of light to the centre, a hollow middle
		int post = Math.round(radius * 0.55f);
		g.fill(icx - r, icy - 1, icx - post, icy + 2, POST);
		g.fill(icx + post, icy - 1, icx + r, icy + 2, POST);
		g.fill(icx - 1, icy + post, icx + 2, icy + r, POST);
		g.fill(icx - 1, icy - r, icx + 2, icy - post, POST);
		g.fill(icx - post, icy, icx - gap, icy + 1, LINE);
		g.fill(icx + gap, icy, icx + post, icy + 1, LINE);
		g.fill(icx, icy - post, icx + 1, icy - gap, LINE);
		g.fill(icx, icy + gap, icx + 1, icy + post, LINE);
		// range ticks along the fine lines
		int step = Math.max(5, Math.round(radius * 0.08f));
		for (int k = gap + step; k < post; k += step) {
			g.fill(icx + k, icy - 2, icx + k + 1, icy + 3, LINE);
			g.fill(icx - k, icy - 2, icx - k + 1, icy + 3, LINE);
			g.fill(icx - 2, icy + k, icx + 3, icy + k + 1, LINE);
		}
		// the hollow centre: a small diamond of light and a bright point
		int d = Math.max(3, gap / 2);
		for (int i = 0; i <= d; i++) {
			g.fill(icx - d + i, icy - i, icx - d + i + 1, icy - i + 1, LINE);
			g.fill(icx + d - i, icy - i, icx + d - i + 1, icy - i + 1, LINE);
			g.fill(icx - d + i, icy + i, icx - d + i + 1, icy + i + 1, LINE);
			g.fill(icx + d - i, icy + i, icx + d - i + 1, icy + i + 1, LINE);
		}
		g.fill(icx, icy, icx + 1, icy + 1, BRIGHT);

		// the charge ring: 24 points of light round the centre, filling as the shot charges, pulsing once full
		float charge = GreenLanternSniperClient.chargeProgress(pt);
		int cd = GreenLantern.cooldownRemaining(mc.player, GreenLanternSniper.COOLDOWN_ID);
		int points = 24;
		float ring = gap * 2.2f;
		int lit = charge < 0f ? 0 : Math.round(points * charge);
		boolean full = charge >= 1f;
		float pulse = 0.5f + 0.5f * Mth.sin((now + pt) * 0.9f);
		for (int i = 0; i < points; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / points);
			int px = icx + (int) Math.round(Math.cos(a) * ring);
			int py = icy + (int) Math.round(Math.sin(a) * ring);
			int col;
			if (i < lit) {
				col = full ? ((int) (180 + 75 * pulse) << 24) | 0xE8FFEE : 0xF05CFF8E;
			} else {
				col = DIM;
			}
			g.fill(px - 1, py - 1, px + 1, py + 1, col);
		}

		// status and magnification under the reticle
		Component status;
		int statusColour;
		if (cd > 0) {
			status = Component.translatable("hud.projecthero.green_lantern.sniper.cooldown", String.format(java.util.Locale.ROOT, "%.1f", cd / 20f));
			statusColour = 0xFFFF8A8A;
		} else if (full) {
			status = Component.translatable("hud.projecthero.green_lantern.sniper.charged");
			statusColour = 0xFFE8FFEE;
		} else if (charge >= 0f) {
			status = Component.translatable("hud.projecthero.green_lantern.sniper.charging", Math.round(charge * 100f));
			statusColour = 0xFF5CFF8E;
		} else {
			status = Component.translatable("hud.projecthero.green_lantern.sniper.ready");
			statusColour = 0xFF5CFF8E;
		}
		g.drawCenteredString(mc.font, status, icx, icy + Math.round(radius * 0.62f), statusColour);
		int zoom = Math.max(1, Math.round(1f / GreenLanternConfig.SNIPER_SCOPE_FOV));
		Component z = Component.translatable("hud.projecthero.green_lantern.sniper.zoom", zoom);
		// the magnification sits in the clear upper-left of the lens, away from the HUDs at the bottom of the screen
		g.drawString(mc.font, z, icx - Math.round(radius * 0.5f), icy - Math.round(radius * 0.55f), 0xFF35F075, true);

		// the scope forming: a short flash of green light
		float form = 1f - Mth.clamp((now - scopedSince + pt) / 5f, 0f, 1f);
		if (form > 0f) {
			g.fill(0, 0, sw, sh, ((int) (form * 200f) << 24) | 0x35F075);
		}
	}

	private static int half(float radius, float dy) {
		return (int) Math.sqrt(Math.max(0f, radius * radius - dy * dy));
	}
}
