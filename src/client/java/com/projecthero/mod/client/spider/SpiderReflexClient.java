package com.projecthero.mod.client.spider;

import com.projecthero.mod.network.SpiderReflexFxPayload;
import com.projecthero.mod.network.SpiderReflexPayload;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSounds;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.2: the Spider-Man's own client side of the Inverted Takedown and the perfect dodge.
 *
 * <ul>
 * <li><b>Takedown:</b> the server names the mob a left-click would take down ({@link SpiderReflexFxPayload#TAKEDOWN_TARGET});
 * while he hangs upside down with one named, a small "[LMB] Takedown" prompt sits under the crosshair, and the
 * left-click is claimed at the start of the tick (it never also punches) and sent as {@link SpiderReflexPayload#TAKEDOWN}.</li>
 * <li><b>Perfect dodge:</b> a warning ({@link SpiderReflexFxPayload#DODGE_WARNING}) flashes the screen edges in
 * spider-sense red, plays a distinct tingle and shows a "[Space] Dodge!" cue; a Jump press while it is up (not swinging,
 * not hanging) asks the server to arm the dodge. The dodge itself
 * ({@link SpiderReflexFxPayload#DODGE_PERFECT}) is a brief desaturating vignette pulse and a whoosh; "Counter x2" shows
 * while the counter window is open.</li>
 * </ul>
 */
public final class SpiderReflexClient {
	private static final int PULSE_TICKS = 24;
	private static final int FLASH_TICKS = 10;

	private static int takedownTarget = -1;
	private static long warnUntil;
	private static long warnStart = Long.MIN_VALUE;
	private static long pulseStart = Long.MIN_VALUE;
	private static long counterUntil;
	private static boolean jumpWasDown;

	private SpiderReflexClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(SpiderReflexFxPayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
		ClientTickEvents.START_CLIENT_TICK.register(SpiderReflexClient::startTick);
		HudRenderCallback.EVENT.register(SpiderReflexClient::render);
	}

	private static long now(Minecraft mc) {
		return mc.level == null ? 0L : mc.level.getGameTime();
	}

	/** True while the perfect-dodge warning is up (the harness reads it). */
	public static boolean warning() {
		return now(Minecraft.getInstance()) <= warnUntil;
	}

	public static void accept(SpiderReflexFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		long now = now(mc);
		switch (p.kind()) {
			case SpiderReflexFxPayload.TAKEDOWN_TARGET -> takedownTarget = p.value();
			case SpiderReflexFxPayload.DODGE_WARNING -> {
				boolean onset = now > warnUntil + 2;
				warnUntil = now + p.value();
				if (onset) {
					warnStart = now;
					if (mc.player != null && !SpiderMoves.soundsOff(mc.player)) {
						mc.getSoundManager().play(SimpleSoundInstance.forUI(SpiderSounds.SENSE, 1.45f, 0.9f));
						mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.9f, 0.8f));
					}
				}
			}
			case SpiderReflexFxPayload.DODGE_PERFECT -> {
				pulseStart = now;
				warnUntil = 0L;
				counterUntil = now + p.value();
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.3f, 0.9f));
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SpiderSounds.SENSE, 0.6f, 0.7f));
			}
			case SpiderReflexFxPayload.COUNTER_LANDED -> counterUntil = 0L;
			default -> {
			}
		}
	}

	// ---------------- input ----------------

	private static void startTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			takedownTarget = -1;
			warnUntil = 0L;
			counterUntil = 0L;
			jumpWasDown = false;
			return;
		}
		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		if (mc.screen != null || !SpiderMan.hasPower(player) || player.isSpectator()) {
			return;
		}
		SpiderMoveState s = SpiderMoves.state(player);
		// ---- the takedown: a left-click while hanging over a named target
		if (takedownTarget != -1 && SpiderMoves.hangingUpsideDown(player) && !s.has(SpiderMoveState.FLAG_WEAVING)) {
			boolean clicked = false;
			while (mc.options.keyAttack.consumeClick()) {
				clicked = true;
			}
			if (clicked) {
				mc.options.keyAttack.setDown(false); // and no block breaking either
				ClientPlayNetworking.send(new SpiderReflexPayload(SpiderReflexPayload.TAKEDOWN));
				player.swing(InteractionHand.MAIN_HAND);
				takedownTarget = -1;
			}
		}
		// ---- the perfect dodge: Jump during the warning (a plain jump otherwise -- nothing is consumed)
		if (jumpPressed && warning() && !SpiderSwing.isSwinging(player) && !s.has(SpiderMoveState.FLAG_HANGING)) {
			ClientPlayNetworking.send(new SpiderReflexPayload(SpiderReflexPayload.DODGE));
		}
	}

	// ---------------- HUD ----------------

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !SpiderMan.hasPower(player)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		int w = g.guiWidth();
		int h = g.guiHeight();
		boolean hud = !mc.options.hideGui;

		// the perfect dodge: a desaturating vignette pulse
		double pa = now - pulseStart;
		if (pa >= 0 && pa < PULSE_TICKS) {
			double u = pa / PULSE_TICKS;
			float a = (float) (u < 0.15 ? u / 0.15 : 1.0 - (u - 0.15) / 0.85);
			a = a * a * (3f - 2f * a);
			g.fill(0, 0, w, h, argb(a * 0.30f, 0x8C9098)); // washed-out grey over everything
			edges(g, w, h, Math.max(40, h / 4), 0x0A0C14, a * 0.75f);
		}
		// the warning: spider-sense red at the screen edges, strong on the onset then a steady throb
		if (now <= warnUntil + 1) {
			double since = now - warnStart;
			float onset = (float) Math.max(0.0, 1.0 - since / FLASH_TICKS);
			float throb = 0.22f + 0.12f * Mth.sin((float) now * 1.6f);
			float a = Math.min(1f, onset * 0.75f + throb);
			edges(g, w, h, Math.max(24, h / 9), 0xFF3A2A, a);
			if (hud) {
				Component cue = Component.translatable("hud.projecthero.spider_man.dodge_cue",
						mc.options.keyJump.getTranslatedKeyMessage());
				int tw = mc.font.width(cue);
				int col = onset > 0.3f && ((long) now & 1L) == 0L ? 0xFFFFFFFF : 0xFFFF6A50;
				g.drawString(mc.font, cue, (w - tw) / 2, h / 2 + 14, col, true);
			}
		}
		if (!hud) {
			return;
		}
		if (now <= counterUntil) {
			Component c = Component.translatable("hud.projecthero.spider_man.counter_ready");
			g.drawString(mc.font, c, (w - mc.font.width(c)) / 2, h / 2 + 26, 0xFFFFC040, true);
		}
		// the takedown prompt
		if (takedownTarget != -1 && SpiderMoves.hangingUpsideDown(player)) {
			Entity e = mc.level.getEntity(takedownTarget);
			if (e != null && e.isAlive()) {
				Component t = Component.translatable("hud.projecthero.spider_man.takedown_prompt",
						mc.options.keyAttack.getTranslatedKeyMessage());
				int tw = mc.font.width(t);
				int x = (w - tw) / 2;
				int y = h / 2 + 12;
				g.fill(x - 4, y - 3, x + tw + 4, y + 11, 0xA0140A0E);
				g.fill(x - 4, y - 3, x - 2, y + 11, 0xFFE0303C);
				g.drawString(mc.font, t, x, y, 0xFFF0E6E8, true);
			}
		}
	}

	private static int argb(float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Soft gradients in from all four edges ({@code depth} px), colour {@code rgb} at {@code alpha} at the very edge. */
	private static void edges(GuiGraphics g, int w, int h, int depth, int rgb, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		int edge = argb(alpha, rgb);
		int clear = argb(0f, rgb);
		g.fillGradient(0, 0, w, depth, edge, clear);
		g.fillGradient(0, h - depth, w, h, clear, edge);
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			float k = 1f - i / (float) steps;
			int col = argb(alpha * k * k, rgb);
			int x0 = depth * i / steps;
			int x1 = depth * (i + 1) / steps;
			g.fill(x0, 0, x1, h, col);
			g.fill(w - x1, 0, w - x0, h, col);
		}
	}
}
