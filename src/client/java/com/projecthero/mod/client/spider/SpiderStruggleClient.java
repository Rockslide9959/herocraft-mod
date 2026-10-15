package com.projecthero.mod.client.spider;

import com.mojang.blaze3d.platform.InputConstants;
import com.projecthero.mod.network.SpiderStrugglePayload;
import com.projecthero.mod.network.SpiderStruggleSyncPayload;
import com.projecthero.mod.spider.SpiderStruggle;
import com.projecthero.mod.spider.SpiderWrap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * v0.16.4 (user): the struggle to break out of a web cocoon, on the cocooned player's side ({@link SpiderStruggle} owns the
 * progress).
 * <ul>
 *   <li><b>Input</b> -- the two strafe keys (whatever {@code keyLeft} / {@code keyRight} are bound to) are read straight
 *       from the keyboard every frame and every tick, so each press is caught in order; each press goes to the server.
 *       Both keys going down in the same instant counts as a slip (mashing both at once never works).</li>
 *   <li><b>Nothing else sees them</b> -- while cocooned the movement, jump and sprint keys are let go and their queued
 *       clicks drained at the start of every client tick, so A / D never strafe him, trigger a swing trick, a wall run
 *       or anything else that reads the movement input.</li>
 *   <li><b>The HUD</b> -- under the crosshair: "Struggle! Alternate [A] [D]" with the two keys drawn as keycaps that light up
 *       while held, the struggle bar (shakes on every good press, flashes red on a slip) and a one-line hint.</li>
 * </ul>
 */
public final class SpiderStruggleClient {
	private static final int BAR_W = 120;
	private static final int BAR_H = 5;
	private static final int HINT_WIDTH = 200;
	/** Least gap between two reported presses (the server ignores past 20 a second anyway). */
	private static final long MIN_SEND_GAP_MS = 30L;

	private static boolean active;
	private static boolean leftWas;
	private static boolean rightWas;
	private static int lastSent = -1;
	private static long lastSendMs;
	private static long leftFlashUntil;
	private static long rightFlashUntil;

	private static float serverProgress;
	private static float shown;
	private static long lastFrameMs;
	private static long shakeUntil;
	private static long slipUntil;
	private static long freedUntil;

	private SpiderStruggleClient() {
	}

	public static void initialize() {
		ClientTickEvents.START_CLIENT_TICK.register(SpiderStruggleClient::startTick);
		HudRenderCallback.EVENT.register(SpiderStruggleClient::render);
		ClientPlayNetworking.registerGlobalReceiver(SpiderStruggleSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
	}

	private static void accept(SpiderStruggleSyncPayload p) {
		serverProgress = Mth.clamp(p.progress(), 0.0f, 1.0f);
		long now = Util.getMillis();
		Minecraft mc = Minecraft.getInstance();
		if (p.good() == 1) {
			shakeUntil = now + 140L;
			if (mc.player != null) {
				mc.player.playSound(SoundEvents.WOOL_HIT, 0.35f, 0.8f + mc.player.getRandom().nextFloat() * 0.5f);
			}
		} else if (p.good() == -1) {
			slipUntil = now + 220L;
		} else if (p.good() == 2) {
			freedUntil = now + 1200L;
			serverProgress = 0.0f;
			shown = 0.0f;
		}
	}

	private static boolean cocooned(Minecraft mc) {
		return mc.player != null && mc.level != null && mc.player.isAlive() && SpiderWrap.isWrapped(mc.player);
	}

	// ---------------------------------------------------------------- input

	private static void startTick(Minecraft mc) {
		boolean on = cocooned(mc);
		if (!on) {
			if (active) {
				reset();
			}
			return;
		}
		if (!active) {
			reset();
			active = true;
			// whatever was held when the cocoon closed is not a press
			leftWas = physical(mc, mc.options.keyLeft);
			rightWas = physical(mc, mc.options.keyRight);
		}
		poll(mc);
		// held in place anyway: nothing that reads the movement keys may act on them while he struggles
		var o = mc.options;
		for (KeyMapping k : new KeyMapping[] { o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint }) {
			while (k.consumeClick()) {
				// drained
			}
			k.setDown(false);
		}
		mc.player.xxa = 0.0f;
		mc.player.zza = 0.0f;
		mc.player.setJumping(false);
		mc.player.setSprinting(false);
	}

	private static void reset() {
		active = false;
		lastSent = -1;
		serverProgress = 0.0f;
		shown = 0.0f;
		leftWas = false;
		rightWas = false;
	}

	/** Edge-detect the two strafe keys and report each press (called every frame and every tick). */
	private static void poll(Minecraft mc) {
		if (!active) {
			return;
		}
		boolean free = mc.screen == null;
		boolean left = free && physical(mc, mc.options.keyLeft);
		boolean right = free && physical(mc, mc.options.keyRight);
		boolean lp = left && !leftWas;
		boolean rp = right && !rightWas;
		leftWas = left;
		rightWas = right;
		long now = Util.getMillis();
		if (lp) {
			leftFlashUntil = now + 120L;
		}
		if (rp) {
			rightFlashUntil = now + 120L;
		}
		if (lp && rp) {
			// both at once: a slip (the same key as last time), never a free alternation
			if (lastSent >= 0) {
				send(lastSent, now);
			}
		} else if (lp) {
			send(SpiderStruggle.LEFT, now);
		} else if (rp) {
			send(SpiderStruggle.RIGHT, now);
		}
	}

	private static void send(int key, long now) {
		if (now - lastSendMs < MIN_SEND_GAP_MS) {
			return;
		}
		lastSendMs = now;
		lastSent = key;
		ClientPlayNetworking.send(new SpiderStrugglePayload(key));
	}

	/** The real, physical state of the key bound to {@code k} (keyboard or mouse button) -- untouched by setDown. */
	private static boolean physical(Minecraft mc, KeyMapping k) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(k);
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		long window = mc.getWindow().getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(window, key.getValue());
		}
		return false;
	}

	// ---------------------------------------------------------------- HUD

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		long dt = lastFrameMs == 0L ? 0L : Math.min(100L, now - lastFrameMs);
		lastFrameMs = now;
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 18;
		if (!active || !cocooned(mc)) {
			if (now < freedUntil && mc.player != null) {
				g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.spider.struggle.free").withStyle(ChatFormatting.BOLD),
						cx, y, 0xFF7CFF4A);
			}
			return;
		}
		poll(mc); // per-frame read: quick taps between ticks are not missed
		shown += (serverProgress - shown) * Math.min(1.0f, dt / 60.0f);

		// line 1: "Struggle! Alternate [A] [D]"
		Component title = Component.translatable("hud.projecthero.spider.struggle.title").withStyle(ChatFormatting.BOLD);
		Component leftName = mc.options.keyLeft.getTranslatedKeyMessage();
		Component rightName = mc.options.keyRight.getTranslatedKeyMessage();
		int tw = mc.font.width(title);
		int lw = Math.max(14, mc.font.width(leftName) + 6);
		int rw = Math.max(14, mc.font.width(rightName) + 6);
		int capH = 13;
		int total = tw + 6 + lw + 3 + rw;
		int x = cx - total / 2;
		int titleCol = (now / 250L) % 2L == 0L ? 0xFFFFFFFF : 0xFFFFD0D8;
		g.drawString(mc.font, title, x, y + 3, titleCol, true);
		x += tw + 6;
		keycap(g, mc, x, y, lw, capH, leftName, leftWas || now < leftFlashUntil, lastSent == SpiderStruggle.LEFT);
		x += lw + 3;
		keycap(g, mc, x, y, rw, capH, rightName, rightWas || now < rightFlashUntil, lastSent == SpiderStruggle.RIGHT);

		// the bar (shakes on a good press, red flash on a slip)
		int bx = cx - BAR_W / 2;
		int by = y + capH + 5;
		if (now < shakeUntil) {
			float k = (shakeUntil - now) / 140.0f;
			bx += Math.round((float) (Math.random() * 2.0 - 1.0) * 3.0f * k);
			by += Math.round((float) (Math.random() * 2.0 - 1.0) * 1.5f * k);
		}
		boolean slip = now < slipUntil;
		g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, slip ? 0xFFFF5544 : 0xFF2A2A30);
		g.fill(bx, by, bx + BAR_W, by + BAR_H, 0xC0101018);
		int fillW = Math.round(BAR_W * Mth.clamp(shown, 0.0f, 1.0f));
		int fillCol = shown > 0.75f ? 0xFF7CFF4A : shown > 0.4f ? 0xFFFFE070 : 0xFFE8E8F4;
		if (fillW > 0) {
			g.fill(bx, by, bx + fillW, by + BAR_H, fillCol);
			g.fill(bx, by, bx + fillW, by + 1, 0x60FFFFFF);
		}

		// the hint, wrapped (TooltipWrap rule: UI text never sprawls)
		int ly = by + BAR_H + 4;
		for (FormattedCharSequence line : mc.font.split(Component.translatable("hud.projecthero.spider.struggle.hint"), HINT_WIDTH)) {
			g.drawCenteredString(mc.font, line, cx, ly, 0xFFB0B0B8);
			ly += 9;
		}
	}

	/** A keycap: lit while the key is down, outlined brighter when it was the last key pressed. */
	private static void keycap(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, Component name, boolean lit, boolean last) {
		g.fill(x, y, x + w, y + h, lit ? 0xE0E8E8F4 : 0xC0181820);
		g.renderOutline(x, y, w, h, lit ? 0xFFFFFFFF : last ? 0xFFFF8A96 : 0xFF707078);
		g.drawString(mc.font, name, x + (w - mc.font.width(name)) / 2, y + (h - 8) / 2 + 1, lit ? 0xFF101018 : 0xFFFFFFFF, !lit);
	}
}
