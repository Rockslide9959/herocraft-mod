package com.projecthero.mod.client.quirk.erasure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Erasure on the screen.
 * <ul>
 *   <li>Erased (anyone's stare holds YOU): a pulsing red vignette (the core's HUD says "ERASED").</li>
 *   <li>The Eraser's forced blink: the eyelids close and open again.</li>
 *   <li>The goggles: a faint yellow tint while they are down; the outlines of what they pick out
 *       ({@link #marked}, from {@code ErasureGogglesPayload}, via {@code ErasureGogglesGlowMixin}).</li>
 * </ul>
 */
public final class ErasureHud {
	private static final Set<Integer> MARKED = new HashSet<>();
	private static long markedUntil;
	public static final int YELLOW = 0xFFE8C020;

	private ErasureHud() {
	}

	static void receive(List<Integer> ids) {
		Minecraft mc = Minecraft.getInstance();
		synchronized (MARKED) {
			MARKED.clear();
			MARKED.addAll(ids);
		}
		markedUntil = mc.level == null ? 0L : mc.level.getGameTime() + 15L;
	}

	static void reset() {
		synchronized (MARKED) {
			MARKED.clear();
		}
		markedUntil = 0L;
	}

	/** The local Eraser's goggles pick this out right now. */
	public static boolean marked(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		Player me = mc.player;
		if (me == null || e == me || mc.level == null || mc.level.getGameTime() > markedUntil || !Quirks.holds(me, Quirk.ERASURE)) {
			return false;
		}
		QuirkState s = Quirks.peek(me);
		if (s == null || s.val(ErasureConfig.V_GOGGLES) == 0L) {
			return false;
		}
		synchronized (MARKED) {
			return MARKED.contains(e.getId());
		}
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.options.hideGui) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		float t = p.level().getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		if (PowerSuppression.isSuppressed(p)) {
			float pulse = 0.55f + 0.2f * Mth.sin(t * 0.25f);
			vignette(g, w, h, (int) (pulse * 150), 0xC01010);
		}
		if (!Quirks.holds(p, Quirk.ERASURE)) {
			return;
		}
		QuirkState s = Quirks.peek(p);
		if (s == null) {
			return;
		}
		if (s.val(ErasureConfig.V_GOGGLES) != 0L) {
			g.fill(0, 0, w, h, 0x18E8C020);
		}
		// v0.17.1 Dry Eyes bands: Strained reddens the screen's edge, Burning more (and it throbs)
		com.projecthero.mod.quirk.meter.QuirkBand band = com.projecthero.mod.quirk.meter.QuirkMeters.clientBand(p);
		if (band == com.projecthero.mod.quirk.meter.QuirkBand.SWEET && s.val(ErasureConfig.V_ERASE) == 0L) {
			vignette(g, w, h, 55, 0x901010);
		} else if (band == com.projecthero.mod.quirk.meter.QuirkBand.RED && s.val(ErasureConfig.V_SHUT) == 0L) {
			vignette(g, w, h, (int) (110 + 40 * Mth.sin(t * 0.3f)), 0xA00808);
		}
		// v0.16.7 revamp: eyes forced shut (Dry Eyes at 100): the lids close fast and stay nearly shut -- a sliver of
		// sight -- until the eyes recover, then open
		float pt = delta.getGameTimeDeltaPartialTick(false);
		if (s.val(ErasureConfig.V_SHUT) != 0L) {
			float age = p.level().getGameTime() - s.val(ErasureConfig.V_SHUT_AT) + pt;
			float shut = Math.min(1f, age / 4f) * (0.86f + 0.05f * Mth.sin(t * 0.3f));
			lids(g, w, h, shut);
			lastShut = p.level().getGameTime();
		} else if (lastShut > 0L) {
			float since = p.level().getGameTime() - lastShut + pt;
			if (since < 6f) {
				lids(g, w, h, 0.9f * (1f - since / 6f));
			} else {
				lastShut = 0L;
			}
		}
		// the stare: a faint red rim while he holds it, a little brighter as his eyes dry
		if (s.val(ErasureConfig.V_ERASE) != 0L) {
			float dry = Mth.clamp(Quirks.clientMeter(p) / 100f, 0f, 1f);
			vignette(g, w, h, (int) (40 + 90 * dry), 0xB01010);
		}
	}

	private static long lastShut;

	/** Eyelids closing from the top and bottom ({@code shut} 0..1). */
	private static void lids(GuiGraphics g, int w, int h, float shut) {
		if (shut <= 0f) {
			return;
		}
		int lid = (int) (h * 0.5f * shut);
		g.fillGradient(0, 0, w, lid, 0xF8050505, 0xF0100606);
		g.fillGradient(0, h - lid, w, h, 0xF0100606, 0xF8050505);
	}

	/** A soft frame of {@code rgb} round the screen edges, {@code alpha} 0..255 at the very edge. */
	private static void vignette(GuiGraphics g, int w, int h, int alpha, int rgb) {
		int band = Math.max(24, Math.min(w, h) / 5);
		int edge = (Mth.clamp(alpha, 0, 255) << 24) | rgb;
		int clear = rgb;
		g.fillGradient(0, 0, w, band, edge, clear);
		g.fillGradient(0, h - band, w, h, clear, edge);
		// the sides: vertical strips fading inward (a few steps)
		int steps = 8;
		for (int i = 0; i < steps; i++) {
			int a = (int) (Mth.clamp(alpha, 0, 255) * 0.5f * (1f - i / (float) steps));
			int c = (a << 24) | rgb;
			int x0 = i * band / steps, x1 = (i + 1) * band / steps;
			g.fill(x0, 0, x1, h, c);
			g.fill(w - x1, 0, w - x0, h, c);
		}
	}
}
