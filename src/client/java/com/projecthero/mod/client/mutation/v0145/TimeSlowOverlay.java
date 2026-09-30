package com.projecthero.mod.client.mutation.v0145;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.Util;

/**
 * v0.14.7: the Time Slow screen effect, drawn over the world but under the HUD ({@code SuperSpeedTimeSlowOverlayMixin},
 * the tail of {@code Gui.renderCameraOverlays}). For the caster, and a little stronger for everyone else (all caught in it):
 * <ul>
 *   <li>a cold blue-grey colour grade over the whole view and a soft dark vignette;</li>
 *   <li>a "time freeze" ripple expanding from the centre (with a faint flash) over 0.4 s when it starts, and the
 *       same ripple collapsing back into the centre when it ends;</li>
 *   <li>slow-drifting pale motes and a light film grain.</li>
 * </ul>
 * The grade, vignette and motes vanish the moment Time Slow ends; only the 0.4 s reverse
 * ripple plays out after it.
 */
public final class TimeSlowOverlay {
	private static final float PULSE_TICKS = 8.0f;
	private static final int MOTES = 36;

	private TimeSlowOverlay() {
	}

	private static double hash(double v) {
		double s = Math.sin(v * 12.9898 + 78.233) * 43758.5453;
		return s - Math.floor(s);
	}

	/**
	 * Drawn from the camera overlays. Everything is timed in real milliseconds (in 50 ms "ticks"), never game ticks:
	 * a caught player's game runs at 1 tick a second, and their screen effect must still move.
	 */
	public static void render(GuiGraphics g, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		long nowMs = Util.getMillis();
		float now = (nowMs % 3_600_000L) / 50.0f; // wraps hourly: keeps float precision for the drifting motes
		int w = g.guiWidth();
		int h = g.guiHeight();
		float sinceStart = (nowMs - TimeSlowClient.startedMs) / 50.0f;
		float sinceEnd = (nowMs - TimeSlowClient.endedMs) / 50.0f;
		if (TimeSlowClient.active()) {
			// everyone caught in it sees a slightly stronger grade than the caster
			float strength = TimeSlowClient.isLocalCaster() ? 1.0f : 1.3f;
			// fade the grade in over the first few ticks, under the ripple
			float in = Mth.clamp(sinceStart / 6.0f, 0f, 1f);
			grade(g, w, h, strength * in);
			vignette(g, w, h, strength * in);
			motes(g, w, h, now, strength * in);
			if (sinceStart >= 0 && sinceStart < PULSE_TICKS) {
				pulse(g, w, h, sinceStart / PULSE_TICKS, false);
			}
		} else if (sinceEnd >= 0 && sinceEnd < PULSE_TICKS) {
			pulse(g, w, h, sinceEnd / PULSE_TICKS, true);
		}
	}

	private static int argb(float a, int rgb) {
		return (Mth.clamp(Math.round(a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	/** The cold colour grade: a desaturating grey veil plus a blue cast. */
	private static void grade(GuiGraphics g, int w, int h, float k) {
		g.fill(0, 0, w, h, argb(0.2f * k, 0x8C96A0));
		g.fill(0, 0, w, h, argb(0.16f * k, 0x284878));
	}

	/**
	 * A soft vignette: nested edge frames that overlap, so the darkness builds up toward the edges (~45% at the very
	 * edge, nothing past a quarter of the screen in).
	 */
	private static void vignette(GuiGraphics g, int w, int h, float k) {
		int bands = 12;
		int depth = Math.max(24, Math.min(w, h) / 4);
		int c = argb(0.05f * k, 0x04070E);
		for (int i = 1; i <= bands; i++) {
			int t = Math.round(i * depth / (float) bands);
			g.fill(0, 0, w, t, c); // top
			g.fill(0, h - t, w, h, c); // bottom
			g.fill(0, t, t, h - t, c); // left
			g.fill(w - t, t, w, h - t, c); // right
		}
	}

	/** Pale motes drifting slowly up and sideways, and a light flickering grain. */
	private static void motes(GuiGraphics g, int w, int h, float now, float k) {
		for (int i = 0; i < MOTES; i++) {
			double sx = hash(i * 3.1);
			double sy = hash(i * 7.7 + 1.3);
			double sp = 0.15 + hash(i * 1.9 + 4.2) * 0.35;
			double x = (sx * w + now * sp * 0.6 + Math.sin(now * 0.02 + i) * 6.0) % w;
			double y = ((sy * h - now * sp) % h + h) % h;
			int size = hash(i * 5.3) > 0.7 ? 2 : 1;
			float twinkle = 0.5f + 0.5f * (float) Math.sin(now * 0.05 + i * 1.7);
			g.fill((int) x, (int) y, (int) x + size, (int) y + size, argb((0.18f + 0.22f * twinkle) * k, 0xD8E8FF));
		}
		long frame = (long) (now / 2.0f);
		for (int i = 0; i < 70; i++) {
			int x = (int) (hash(frame * 0.37 + i * 11.3) * w);
			int y = (int) (hash(frame * 0.71 + i * 5.9 + 2.0) * h);
			g.fill(x, y, x + 1, y + 1, argb(0.09f * k, (i & 1) == 0 ? 0xFFFFFF : 0x000000));
		}
	}

	/** The time-freeze ripple: a bright ring expanding from the centre (or collapsing into it) plus a faint flash. */
	private static void pulse(GuiGraphics g, int w, int h, float t, boolean reverse) {
		float p = reverse ? 1.0f - t : t;
		float ease = 1.0f - (1.0f - p) * (1.0f - p);
		float fade = 1.0f - t;
		g.fill(0, 0, w, h, argb(0.22f * fade * fade, 0xDDEEFF));
		float maxR = (float) Math.sqrt(w * w + h * h) * 0.55f;
		float r = maxR * ease;
		float thick = 6.0f + 18.0f * fade;
		ring(g, w * 0.5f, h * 0.5f, Math.max(0f, r - thick), r, argb(0.55f * fade, 0xE8F4FF), argb(0.0f, 0xE8F4FF));
		ring(g, w * 0.5f, h * 0.5f, r, r + thick * 0.5f, argb(0.0f, 0x9CC8FF), argb(0.35f * fade, 0x9CC8FF));
	}

	/** A flat annulus in screen space, colour {@code inner} at the inner edge blending to {@code outer}. */
	private static void ring(GuiGraphics g, float cx, float cy, float r0, float r1, int inner, int outer) {
		if (r1 <= r0) {
			return;
		}
		Matrix4f m = g.pose().last().pose();
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		int seg = 72;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg;
			double a1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			vc.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0).setColor(inner);
			vc.addVertex(m, cx + c0 * r1, cy + s0 * r1, 0).setColor(outer);
			vc.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0).setColor(outer);
			vc.addVertex(m, cx + c1 * r0, cy + s1 * r0, 0).setColor(inner);
		}
		g.flush();
	}
}
