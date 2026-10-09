package com.projecthero.mod.client.gui;

import com.projecthero.mod.client.firearm.FirearmClient;
import com.projecthero.mod.client.punisher.GunAnim;
import com.projecthero.mod.client.punisher.GunFirstPerson;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.punisher.PunisherConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The gun sights, drawn over the world in first person.
 *
 * <ul>
 *   <li><b>Sniper scope</b> (v0.15.16 rebuilt, user: "it should look like they're looking through a sniper scope with a
 *       circle view and a reticle"): the moment the sniper is raised to the eye ({@link GunFirstPerson#SCOPE_IN}) the
 *       view goes to a round sight in a black tube -- a soft dark edge, a faint lens tint, a duplex reticle (heavy outer
 *       posts, fine centre cross with mil dots, bullet-drop ticks below, a red centre dot), a short black blink as the eye
 *       meets the scope, a slight sway that grows with movement, and the magnification.</li>
 *   <li><b>Adrenaline</b>: a red pulse round the edges the moment the needle goes in.</li>
 * </ul>
 * Procedural {@code fill()} drawing like the rest of the mod's HUDs.
 */
public final class ScopeOverlay {
	private static final int SURROUND = 0xFF050506;
	private static final int RETICLE = 0xF0060607;
	private static final int DOT = 0xFFFF2A1E;

	private static float swayPhase;
	private static long scopedSince = -1L;

	private ScopeOverlay() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
			scopedSince = -1L;
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		adrenalinePulse(g, mc, pt);
		if (!FirearmClient.scopeOverlayActive()) {
			scopedSince = -1L;
			return;
		}
		long now = mc.level.getGameTime();
		if (scopedSince < 0L) {
			scopedSince = now;
		}
		scope(g, mc, pt, now);
	}

	private static void scope(GuiGraphics g, Minecraft mc, float pt, long now) {
		int sw = g.guiWidth();
		int sh = g.guiHeight();

		double speed = mc.player.getDeltaMovement().horizontalDistance();
		swayPhase += 0.05f + (float) speed * 0.4f;
		float swayAmt = (float) Math.min(4.0, 0.6 + speed * 14.0);
		float cx = sw / 2f + Mth.sin(swayPhase) * swayAmt;
		float cy = sh / 2f + Mth.cos(swayPhase * 0.7f) * swayAmt * 0.7f + Mth.sin((now + pt) * 0.06f) * 0.6f;
		float radius = sh * 0.47f;

		// the tube: black outside, a soft dark edge inside the lens, a faint blue lens tint
		float[] bands = { 1.0f, 0.975f, 0.95f, 0.91f, 0.85f };
		int[] shade = { 0xC0000000, 0x78000000, 0x3C000000, 0x18000000 };
		for (int row = 0; row < sh; row++) {
			float dy = row + 0.5f - cy;
			if (Math.abs(dy) >= radius) {
				g.fill(0, row, sw, row + 1, SURROUND);
				continue;
			}
			int outer = half(radius, dy);
			g.fill(0, row, Math.round(cx - outer), row + 1, SURROUND);
			g.fill(Math.round(cx + outer), row, sw, row + 1, SURROUND);
			for (int b = 0; b < shade.length; b++) {
				int o = half(radius * bands[b], dy);
				int in = Math.abs(dy) < radius * bands[b + 1] ? half(radius * bands[b + 1], dy) : 0;
				if (o <= in) {
					continue;
				}
				g.fill(Math.round(cx - o), row, Math.round(cx - in), row + 1, shade[b]);
				g.fill(Math.round(cx + in), row, Math.round(cx + o), row + 1, shade[b]);
			}
			int lens = half(radius * 0.85f, dy);
			if (Math.abs(dy) < radius * 0.85f) {
				g.fill(Math.round(cx - lens), row, Math.round(cx + lens), row + 1, 0x0C1A3050);
			}
		}

		// duplex reticle: heavy posts from the edge, a fine cross in the middle
		int icx = Math.round(cx);
		int icy = Math.round(cy);
		int r = Math.round(radius);
		int fine = Math.round(radius * 0.3f);
		g.fill(icx - r, icy - 1, icx - fine, icy + 2, RETICLE);
		g.fill(icx + fine, icy - 1, icx + r, icy + 2, RETICLE);
		g.fill(icx - 1, icy + fine, icx + 2, icy + r, RETICLE);
		g.fill(icx - 1, icy - r, icx + 2, icy - fine, RETICLE);
		g.fill(icx - fine, icy, icx - 3, icy + 1, RETICLE);
		g.fill(icx + 3, icy, icx + fine, icy + 1, RETICLE);
		g.fill(icx, icy - fine, icx + 1, icy - 3, RETICLE);
		g.fill(icx, icy + 3, icx + 1, icy + fine, RETICLE);
		// mil dots along the fine cross
		int step = Math.max(4, Math.round(radius * 0.06f));
		for (int k = step; k < fine - 1; k += step) {
			g.fill(icx + k - 1, icy - 1, icx + k + 1, icy + 1 + 1, RETICLE);
			g.fill(icx - k - 1, icy - 1, icx - k + 1, icy + 1 + 1, RETICLE);
			g.fill(icx - 1, icy - k - 1, icx + 1 + 1, icy - k + 1, RETICLE);
		}
		// bullet-drop ticks under the centre, shrinking
		for (int i = 1; i <= 3; i++) {
			int y = icy + i * step;
			int w = Math.max(2, step - i);
			g.fill(icx - w, y, icx + w + 1, y + 1, RETICLE);
		}
		// the red centre dot
		g.fill(icx - 1, icy - 1, icx + 2, icy + 2, 0x80FF2A1E);
		g.fill(icx, icy, icx + 1, icy + 1, DOT);

		int zoom = zoomTimes(mc);
		if (zoom > 1) {
			Component z = Component.translatable("firearm.projecthero.hud.zoom", zoom);
			g.drawString(mc.font, z, icx + Math.round(radius * 0.55f), icy + Math.round(radius * 0.62f), 0xFF8090C0, true);
		}

		// the eye meeting the scope: a short black blink
		float blink = 1f - Mth.clamp((now - scopedSince + pt) / 4f, 0f, 1f);
		if (blink > 0f) {
			g.fill(0, 0, sw, sh, ((int) (blink * 255f) << 24));
		}
	}

	private static int half(float radius, float dy) {
		return (int) Math.sqrt(Math.max(0f, radius * radius - dy * dy));
	}


	/** A red pulse round the screen edges as the Adrenaline needle goes in, fading over ~0.7 s. */
	private static void adrenalinePulse(GuiGraphics g, Minecraft mc, float pt) {
		float t = GunAnim.stab(mc.player, pt);
		int hit = PunisherConfig.ADRENALINE_STAB_TICKS;
		if (t < hit) {
			return;
		}
		float a = 1f - Mth.clamp((t - hit) / 10f, 0f, 1f);
		if (a <= 0f) {
			return;
		}
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		int edge = (int) (a * 150f) << 24 | 0xB01010;
		int clear = 0x00B01010;
		int band = sh / 4;
		g.fillGradient(0, 0, sw, band, edge, clear);
		g.fillGradient(0, sh - band, sw, sh, clear, edge);
		for (int i = 0; i < band; i += 2) {
			int al = (int) (a * 150f * (1f - i / (float) band));
			g.fill(i, 0, i + 2, sh, al << 24 | 0xB01010);
			g.fill(sw - i - 2, 0, sw - i, sh, al << 24 | 0xB01010);
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
