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
		if (s.animId == ErasureConfig.ANIM_BLINK) {
			float age = p.level().getGameTime() - s.animStart + delta.getGameTimeDeltaPartialTick(false);
			// shut fast, held, then open
			float shut = age < 4f ? age / 4f : age < 14f ? 1f : Math.max(0f, 1f - (age - 14f) / 6f);
			if (shut > 0f) {
				int lid = (int) (h * 0.5f * shut);
				g.fill(0, 0, w, lid, 0xF0050505);
				g.fill(0, h - lid, w, h, 0xF0050505);
			}
		}
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
