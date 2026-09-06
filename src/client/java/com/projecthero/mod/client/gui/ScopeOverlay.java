package com.projecthero.mod.client.gui;

import com.projecthero.mod.client.firearm.FirearmClient;
import com.projecthero.mod.firearm.Firearms;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The sniper scope overlay: a black surround with a clear circular sight, a thin blue-purple lens
 * ring, a crosshair with range ticks, and a small zoom readout. Only drawn while a scoped firearm is
 * raised past its first zoom step. Movement adds a very slight sway to the sight so holding a shot
 * while walking is imprecise, without being nauseating (spec section 7).
 *
 * <p>Drawn procedurally with {@code fill()} to match the rest of the mod's HUDs (no blit / texture
 * registration); the pre-rendered {@code textures/gui/sniper_scope.png} is kept for a later polish
 * pass if a nicer reticle is wanted.
 */
public final class ScopeOverlay {
	private static final int SURROUND = 0xFF07070A;
	private static final int RING = 0xC0242A5A;
	private static final int RETICLE = 0xE00A0A0C;

	private static float swayPhase;

	private ScopeOverlay() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !FirearmClient.scopeOverlayActive()) {
			return;
		}
		int sw = g.guiWidth();
		int sh = g.guiHeight();

		double speed = mc.player.getDeltaMovement().horizontalDistance();
		swayPhase += 0.08f + (float) speed * 0.4f;
		float swayAmt = (float) Math.min(3.5, 0.4 + speed * 12.0);
		float cx = sw / 2f + (float) Math.sin(swayPhase) * swayAmt;
		float cy = sh / 2f + (float) Math.cos(swayPhase * 0.7f) * swayAmt * 0.7f;

		float radius = sh * 0.46f;
		float r2 = radius * radius;

		for (int row = 0; row < sh; row++) {
			double dy = row - cy;
			if (Math.abs(dy) >= radius) {
				g.fill(0, row, sw, row + 1, SURROUND);
				continue;
			}
			int half = (int) Math.sqrt(r2 - dy * dy);
			int left = (int) (cx - half);
			int right = (int) (cx + half);
			g.fill(0, row, left, row + 1, SURROUND);
			g.fill(right, row, sw, row + 1, SURROUND);
			// blue lens ring, 3px
			g.fill(left, row, left + 3, row + 1, RING);
			g.fill(right - 3, row, right, row + 1, RING);
		}

		// crosshair with a central gap
		int icx = (int) cx;
		int icy = (int) cy;
		g.fill(icx - (int) radius + 6, icy, icx - 10, icy + 1, RETICLE);
		g.fill(icx + 10, icy, icx + (int) radius - 6, icy + 1, RETICLE);
		g.fill(icx, icy - (int) radius + 6, icx + 1, icy - 10, RETICLE);
		g.fill(icx, icy + 10, icx + 1, icy + (int) radius - 6, RETICLE);
		for (int k = -60; k <= 60; k += 20) {
			if (k == 0) {
				continue;
			}
			g.fill(icx + k, icy - 2, icx + k + 1, icy + 2, RETICLE);
			g.fill(icx - 2, icy + k, icx + 2, icy + k + 1, RETICLE);
		}

		int zoom = zoomTimes(mc);
		if (zoom > 1) {
			Component z = Component.translatable("firearm.projecthero.hud.zoom", zoom);
			g.drawString(mc.font, z, icx + (int) radius - 34, icy + (int) radius - 14, 0xFF8090C0, true);
		}
	}

	private static int zoomTimes(Minecraft mc) {
		var d = Firearms.of(mc.player.getMainHandItem());
		if (d == null || d.zoomLevels.length == 0) {
			return 1;
		}
		int idx = Math.min(FirearmClient.zoomIndex(), d.zoomLevels.length - 1);
		// zoomLevels are FOV multipliers; report an approximate magnification
		return Math.max(1, Math.round(1f / d.zoomLevels[idx]));
	}
}
