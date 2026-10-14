package com.projecthero.mod.client.hulk;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkControl;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.network.HulkActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * v0.16.4 (user): the rampage, seen from the player's chair.
 * <ul>
 *   <li><b>The view</b> -- "when the hulk rampages make the players screen zoom out and get smaller and have a little black box
 *       around the screen and put the minigame at the bottom in the black to make it look like the player is watching the hulk
 *       from an outside perspective, the player should also be forced into third person view during rampage". The camera is
 *       forced to third person (the player's own choice comes back afterwards). Every frame, before the HUD, the finished world
 *       picture is copied aside and drawn back into an inset ({@link #shrinkWorld}: two framebuffer blits, no shaders) on a
 *       black frame -- a thin border on top and the sides, a deep strip at the bottom. It eases in over
 *       {@link #INSET_IN_MS} and back out when the rampage ends.</li>
 *   <li><b>The minigame</b> in that strip: a fixed circle and a ring closing in on it, over and over. Space as the ring meets
 *       the circle is a hit, too early / too late (or letting it pass) a miss; each verdict goes to the server
 *       ({@code RING}), which keeps the streak -- {@code ringHitsNeeded} in a row (3) win control back.</li>
 *   <li><b>No control</b> -- the movement, jump and sprint keys are let go at the start of every client tick (so Space never
 *       makes him jump; the server's jump lock backs it up), and his body turns the way the server is driving him.</li>
 *   <li><b>The warning</b> before a rampage: the arrow string, mid-screen (the arrow keys are read in {@link HulkClient}).</li>
 * </ul>
 */
public final class HulkRampageClient {
	/** How long the view takes to shrink into the inset, and to grow back. */
	public static final long INSET_IN_MS = 600L;
	public static final long INSET_OUT_MS = 450L;
	/** The inset at its smallest: this fraction of the window. */
	public static final float INSET_SCALE = 0.72f;
	/** How much of the free height sits under the inset (the rest is the border above it). */
	private static final float BOTTOM_SHARE = 0.78f;
	/** The ring: a hit is within this many ms of the moment the ring lies on the circle. */
	public static final long HIT_WINDOW_MS = 110L;
	/** The ring starts this many times the circle's radius out. */
	private static final float RING_START = 2.8f;
	/** Pause after a press before the next ring, and the least time between two reports (the server's gap is 600 ms). */
	private static final long RING_PAUSE_MS = 300L;
	private static final long REPORT_GAP_MS = 650L;

	private static final String[] ARROWS = { "", "↑", "←", "↓", "→" };

	private static CameraType savedCamera;
	private static float inset;
	private static long lastFrameMs;
	private static RenderTarget copy;

	// the ring
	private static long cycleStart;
	private static long cycleMs = 1300L;
	private static boolean cycleOpen;
	private static long lastReportMs;
	private static long flashUntil;
	private static boolean flashHit;
	private static boolean spaceWasDown;

	private HulkRampageClient() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(HulkRampageClient::startTick);
		HudRenderCallback.EVENT.register(HulkRampageClient::renderHud);
	}

	private static HulkState state(Player player) {
		return player == null ? null : player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
	}

	/** The local player is rampaging. */
	public static boolean rampaging(Minecraft mc) {
		return mc.player != null && mc.level != null && HulkControl.rampaging(mc.player);
	}

	/** The world is drawn shrunk (the rampage view, or easing in / out of it). */
	public static boolean insetShowing() {
		return inset > 0.001f;
	}

	// ---------------------------------------------------------------- tick: camera and controls

	private static void startTick(Minecraft mc) {
		boolean on = rampaging(mc);
		if (on) {
			if (savedCamera == null) {
				savedCamera = mc.options.getCameraType();
				cycleOpen = false;
				lastReportMs = 0L;
			}
			if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			// the Hulk has the body: nothing the player holds moves him (and Space is only for the ring)
			mc.options.keyUp.setDown(false);
			mc.options.keyDown.setDown(false);
			mc.options.keyLeft.setDown(false);
			mc.options.keyRight.setDown(false);
			mc.options.keyJump.setDown(false);
			mc.options.keySprint.setDown(false);
			mc.options.keyShift.setDown(false);
			faceTheRun(mc.player);
		} else if (savedCamera != null) {
			mc.options.setCameraType(savedCamera);
			savedCamera = null;
			cycleOpen = false;
		}
	}

	/** His body (and the third-person camera behind it) turns the way the server is driving him. */
	private static void faceTheRun(Player player) {
		Vec3 v = player.getDeltaMovement();
		if (v.x * v.x + v.z * v.z < 0.004) {
			return;
		}
		float want = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0f;
		float yaw = player.getYRot() + Mth.wrapDegrees(want - player.getYRot()) * 0.35f;
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(Mth.lerp(0.2f, player.getXRot(), 12.0f));
	}

	// ---------------------------------------------------------------- the inset

	/** 0..1 eased amount of the shrink, advanced by real time. */
	private static void advanceInset(Minecraft mc) {
		long nowMs = Util.getMillis();
		long dt = lastFrameMs == 0L ? 0L : Math.min(100L, nowMs - lastFrameMs);
		lastFrameMs = nowMs;
		boolean on = rampaging(mc);
		if (on) {
			inset = Math.min(1.0f, inset + dt / (float) INSET_IN_MS);
		} else {
			inset = Math.max(0.0f, inset - dt / (float) INSET_OUT_MS);
		}
	}

	private static float eased() {
		float f = Mth.clamp(inset, 0.0f, 1.0f);
		return f * f * (3.0f - 2.0f * f);
	}

	/** The inset's scale right now (1 = full window). */
	public static float insetScale() {
		return 1.0f - (1.0f - INSET_SCALE) * eased();
	}

	/**
	 * Called at the head of the HUD pass (GuiHulkRampageMixin): copy the finished world picture aside, clear the window to
	 * black and draw the copy back into the inset. Plain framebuffer blits -- nothing here depends on a shader.
	 */
	public static void shrinkWorld() {
		Minecraft mc = Minecraft.getInstance();
		advanceInset(mc);
		if (!insetShowing()) {
			return;
		}
		RenderTarget main = mc.getMainRenderTarget();
		int w = main.width;
		int h = main.height;
		if (w <= 0 || h <= 0) {
			return;
		}
		if (copy == null) {
			copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
		} else if (copy.width != w || copy.height != h) {
			copy.resize(w, h, Minecraft.ON_OSX);
		}
		float k = insetScale();
		int iw = Math.round(w * k);
		int ih = Math.round(h * k);
		int x0 = (w - iw) / 2;
		int y0 = Math.round((h - ih) * BOTTOM_SHARE); // GL counts from the bottom: the deep strip is below
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
		GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
		GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copy.frameBufferId);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
		GlStateManager._glBlitFrameBuffer(0, 0, w, h, x0, y0, x0 + iw, y0 + ih, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
		main.bindWrite(true);
	}

	// ---------------------------------------------------------------- HUD

	private static void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		HulkClient.pollArrows(mc);
		HulkState s = state(mc.player);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = mc.level.getGameTime();
		if (HulkControl.warning(s, now)) {
			renderWarning(g, mc, s, now, delta.getGameTimeDeltaPartialTick(false));
		}
		if (insetShowing()) {
			renderFrame(g);
			if (s.rampaging(now)) {
				tickRing(mc);
				renderRing(g, mc, s);
			}
		}
	}

	/** A hairline round the inset, so the picture reads as a screen inside the black. */
	private static void renderFrame(GuiGraphics g) {
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float k = insetScale();
		int iw = Math.round(gw * k);
		int ih = Math.round(gh * k);
		int x0 = (gw - iw) / 2;
		int y0 = Math.round((gh - ih) * (1.0f - BOTTOM_SHARE));
		int col = 0xFF3A5A3A;
		g.fill(x0 - 1, y0 - 1, x0 + iw + 1, y0, col);
		g.fill(x0 - 1, y0 + ih, x0 + iw + 1, y0 + ih + 1, col);
		g.fill(x0 - 1, y0, x0, y0 + ih, col);
		g.fill(x0 + iw, y0, x0 + iw + 1, y0 + ih, col);
	}

	/** The warning: the arrow string to type, mid-screen, with the time left. */
	private static void renderWarning(GuiGraphics g, Minecraft mc, HulkState s, long now, float partial) {
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 - 70;
		int flash = (now / 4L) % 2L == 0L ? 0xFFFF5544 : 0xFFFFC040;
		g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.warning").withStyle(ChatFormatting.BOLD), cx, y, flash);
		int len = s.rules.warnLen;
		int box = 16;
		int gap = 3;
		int total = len * box + (len - 1) * gap;
		int x = cx - total / 2;
		int by = y + 13;
		for (int i = 0; i < len; i++) {
			int arrow = HulkControl.arrowAt(s.rules.warnSeq, i);
			boolean done = i < s.rules.warnPos;
			boolean next = i == s.rules.warnPos;
			g.fill(x, by, x + box, by + box, done ? 0xC0205020 : 0xC0101010);
			g.renderOutline(x, by, box, box, done ? 0xFF7CFF4A : next ? 0xFFFFE070 : 0xFF707070);
			g.drawCenteredString(mc.font, ARROWS[arrow], x + box / 2, by + 4, done ? 0xFF7CFF4A : next ? 0xFFFFE070 : 0xFFFFFFFF);
			x += box + gap;
		}
		float left = Math.max(0.0f, (s.rules.warnUntil - now - partial) / (float) Math.max(1, HulkConfig.control().warningTicks));
		int w = Math.max(total, 80);
		g.fill(cx - w / 2, by + box + 4, cx + w / 2, by + box + 6, 0x80000000);
		g.fill(cx - w / 2, by + box + 4, cx - w / 2 + Math.round(w * Math.min(1.0f, left)), by + box + 6, left < 0.3f ? 0xFFFF5544 : 0xFFFFE070);
		g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.warning_hint"), cx, by + box + 10, 0xFFB0B0B0);
	}

	// ---------------------------------------------------------------- the ring

	/** Fraction of a cycle at which the ring lies exactly on the circle. */
	private static float hitAt() {
		return 1.0f - 1.0f / RING_START;
	}

	private static void newCycle(long nowMs) {
		cycleStart = nowMs + RING_PAUSE_MS;
		cycleMs = 1100L + (long) (Math.random() * 500.0);
		cycleOpen = true;
	}

	private static void report(Minecraft mc, boolean hit, long nowMs) {
		lastReportMs = nowMs;
		flashHit = hit;
		flashUntil = nowMs + 350L;
		ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.RING, hit ? 1 : 0, 0));
		mc.player.playSound(hit ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value() : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
				0.6f, hit ? 1.4f : 0.6f);
	}

	/** Space presses (read every frame for timing), and a ring let past counts as a miss. */
	private static void tickRing(Minecraft mc) {
		long nowMs = Util.getMillis();
		if (!cycleOpen) {
			newCycle(nowMs);
		}
		boolean space = mc.screen == null
				&& GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
		boolean pressed = space && !spaceWasDown;
		spaceWasDown = space;
		long t = nowMs - cycleStart;
		long hitMs = Math.round(cycleMs * hitAt());
		if (t < 0L) {
			return; // the pause between rings
		}
		if (pressed && nowMs - lastReportMs >= REPORT_GAP_MS) {
			report(mc, Math.abs(t - hitMs) <= HIT_WINDOW_MS, nowMs);
			newCycle(nowMs);
			return;
		}
		if (t > hitMs + HIT_WINDOW_MS && nowMs - lastReportMs >= REPORT_GAP_MS) {
			report(mc, false, nowMs); // let it pass
			newCycle(nowMs);
		} else if (t > cycleMs) {
			newCycle(nowMs);
		}
	}

	private static void renderRing(GuiGraphics g, Minecraft mc, HulkState s) {
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float k = insetScale();
		int strip = Math.round((gh - gh * k) * BOTTOM_SHARE);
		if (strip < 24) {
			return;
		}
		int cy = gh - strip / 2;
		int cx = gw / 2;
		float target = Math.max(6.0f, Math.min(14.0f, strip * 0.22f));
		long nowMs = Util.getMillis();
		long t = nowMs - cycleStart;
		boolean flash = nowMs < flashUntil;
		int circleCol = flash ? (flashHit ? 0xFF7CFF4A : 0xFFFF5544) : 0xFFFFFFFF;
		circle(g, cx, cy, target, 2.0f, circleCol);
		if (t >= 0L && cycleOpen) {
			float f = Math.min(1.0f, t / (float) cycleMs);
			float r = target * RING_START * (1.0f - f);
			if (r > 0.5f) {
				boolean close = Math.abs(r - target) <= target * 0.25f;
				circle(g, cx, cy, r, 1.5f, close ? 0xFFFFE070 : 0xFF7CFF4A);
			}
		}
		// the streak, left of the circle; the hint, right of it
		int need = Math.max(1, HulkConfig.control().ringHitsNeeded);
		int dot = 6;
		int dx = cx - Math.round(target * RING_START) - 12 - need * (dot + 3);
		for (int i = 0; i < need; i++) {
			int x = dx + i * (dot + 3);
			g.fill(x, cy - dot / 2, x + dot, cy + dot / 2, i < s.rules.ringStreak ? 0xFF7CFF4A : 0xFF303830);
		}
		Component title = Component.translatable("hud.projecthero.hulk.rampage_view").withStyle(ChatFormatting.BOLD);
		int hx = cx + Math.round(target * RING_START) + 12;
		int col = (mc.level.getGameTime() / 6L) % 2L == 0L ? 0xFFFF5544 : 0xFF7CFF4A;
		g.drawString(mc.font, title, hx, cy - 10, col, false);
		// v0.16.4 (screenshot check): wrapped to the space right of the ring -- it ran off a small window
		int room = Math.max(60, g.guiWidth() - hx - 8);
		int ly = cy + 1;
		for (net.minecraft.util.FormattedCharSequence line : mc.font.split(Component.translatable("hud.projecthero.hulk.ring_hint"), room)) {
			g.drawString(mc.font, line, hx, ly, 0xFFC0C0C0, false);
			ly += 9;
		}
	}

	/** A circle outline of radius {@code r} (GUI pixels), {@code thick} wide. */
	private static void circle(GuiGraphics g, int cx, int cy, float r, float thick, int col) {
		int points = Math.max(16, Math.round(r * 5.0f));
		float half = thick * 0.5f;
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2.0 / points;
			float x = cx + (float) Math.cos(a) * r;
			float y = cy + (float) Math.sin(a) * r;
			g.fill(Math.round(x - half), Math.round(y - half), Math.round(x + half) + 1, Math.round(y + half) + 1, col);
		}
	}
}
