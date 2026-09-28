package com.projecthero.mod.client.hulk;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.network.HulkActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.13.14: the calm-down minigame -- a breathing exercise. A faint guide ring slowly swells (breathe in) and shrinks
 * (breathe out). Your own ring grows while you hold Space (or a mouse button) and shrinks while you let go. Keep your
 * ring on the guide: while it is, the ring glows green and the rage drains fast; drift off and it turns red and he gets
 * angrier. The breathing slows as the rage falls. Once a second the screen tells the server how many ticks were in
 * rhythm ({@code HulkCalm#report}). It closes itself when the server ends the session (calm, hurt, or Esc).
 */
public class HulkCalmScreen extends Screen {
	private static final float R_MIN = 22.0f;
	private static final float R_MAX = 70.0f;
	/** How close (as a fraction of the swing) your ring must stay to the guide to count as in rhythm. */
	private static final float TOLERANCE = 0.2f;

	private int tick;
	private float phase;
	private float ring = R_MIN;
	private float prevRing = R_MIN;
	private boolean breathing;
	private int in;
	private int out;
	private int streak;
	private boolean inRhythm;
	private boolean stopped;

	public HulkCalmScreen() {
		super(Component.translatable("screen.projecthero.hulk_calm"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // handled in keyPressed so the server always hears about it
	}

	private HulkState state() {
		return minecraft == null || minecraft.player == null ? null
				: minecraft.player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
	}

	/** One breath (in + out), in ticks: 3 s when furious, stretching to 5 s as he calms. */
	private float period() {
		HulkState s = state();
		float rage = s == null ? 50.0f : s.rage;
		return 60.0f + (1.0f - rage / HulkConfig.RAGE_MAX) * 40.0f;
	}

	private float guide(float ph) {
		float t = (1.0f - Mth.cos(ph * Mth.TWO_PI)) * 0.5f;
		return R_MIN + (R_MAX - R_MIN) * t;
	}

	@Override
	public void tick() {
		HulkState s = state();
		if (s == null || !s.combat.calming) {
			stopped = true;
			onClose();
			return;
		}
		tick++;
		float p = period();
		phase = (phase + 1.0f / p) % 1.0f;
		prevRing = ring;
		float speed = (R_MAX - R_MIN) / (p * 0.5f);
		ring = Mth.clamp(ring + (breathing ? speed : -speed), R_MIN, R_MAX);
		inRhythm = Math.abs(ring - guide(phase)) <= (R_MAX - R_MIN) * TOLERANCE;
		if (inRhythm) {
			in++;
			streak++;
		} else {
			out++;
			streak = 0;
		}
		if (tick % 20 == 0) {
			ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.CALM_REPORT, in, out));
			in = 0;
			out = 0;
		}
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			stop();
			return true;
		}
		if (key == GLFW.GLFW_KEY_SPACE) {
			breathing = true;
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean keyReleased(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_SPACE) {
			breathing = false;
			return true;
		}
		return super.keyReleased(key, scan, mods);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		breathing = true;
		return true;
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		breathing = false;
		return true;
	}

	private void stop() {
		if (!stopped) {
			stopped = true;
			ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.CALM_STOP));
		}
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xC0061208);
		int cx = width / 2;
		int cy = height / 2 - 6;
		HulkState s = state();

		g.drawCenteredString(font, Component.translatable("screen.projecthero.hulk_calm")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), cx, cy - (int) R_MAX - 34, 0xFFFFFFFF);
		boolean inhale = phase < 0.5f;
		g.drawCenteredString(font, Component.translatable(inhale ? "screen.projecthero.hulk_calm.in" : "screen.projecthero.hulk_calm.out"),
				cx, cy - (int) R_MAX - 20, inhale ? 0xFFB8F0B0 : 0xFF9FC8E8);

		float guide = guide((phase + partialTick / period()) % 1.0f);
		drawRing(g, cx, cy, guide, 0x60FFFFFF, 1);
		float mine = Mth.lerp(partialTick, prevRing, ring);
		int col = inRhythm ? 0xFF55FF66 : 0xFFFF5544;
		drawRing(g, cx, cy, mine, col, 2);
		// a calm core that fills as the streak grows
		int core = (int) Math.min(R_MIN - 4, 4 + streak / 6.0f);
		g.fill(cx - core, cy - core / 2, cx + core, cy + core / 2, 0x6055FF66);

		// the rage he has left
		int bw = 160;
		int bx = cx - bw / 2;
		int by = cy + (int) R_MAX + 18;
		float rage = s == null ? 0.0f : s.rage / HulkConfig.RAGE_MAX;
		g.drawCenteredString(font, Component.translatable("screen.projecthero.hulk_calm.rage", Math.round(rage * 100.0f)), cx, by - 11,
				0xFFB0D8A8);
		g.fill(bx, by, bx + bw, by + 3, 0x80000000);
		g.fill(bx, by, bx + Math.round(bw * rage), by + 3, 0xFF3FAF3A);
		g.drawCenteredString(font, Component.translatable("screen.projecthero.hulk_calm.hint"), cx, by + 10, 0xFF708070);
	}

	/** A circle outline of small squares. */
	private static void drawRing(GuiGraphics g, int cx, int cy, float r, int colour, int thick) {
		int points = Math.max(24, (int) (r * 1.6f));
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x - thick, y - thick, x + thick, y + thick, colour);
		}
	}
}
