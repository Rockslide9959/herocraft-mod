package com.projecthero.mod.client.sentry;

import com.projecthero.mod.sentry.LetItOut;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.5 rework: <b>Hold the Light</b>, the Let It Out trial (Shift+V as the Sentry). A tall dark shaft; a golden sun drifts up and
 * down it (faster and smaller the more Void he carries); his light is a white bead that falls -- the Void pulling it down -- unless he holds
 * Space or the left mouse button, which lifts it. While the bead sits inside the sun the Light bar fills (+26 a second), outside it drains
 * (-12). Fill it before the 8 s run out to win (merged with the Void); let it empty, run out of time or close the screen (Esc) and it is
 * lost (+15 Void). The server decides ({@code LetItOut.report}): a win quicker than 2.5 s on its clock counts as lost. Never pauses the
 * game. All text wraps to the window.
 */
public class LetItOutScreen extends Screen {
	private static final float START_LIGHT = 30f;
	private static final float FILL_PER_SECOND = 26f;
	private static final float DRAIN_PER_SECOND = 12f;
	/** Set only by the client screenshot harness: the bead follows the sun on its own (true) or never lifts (false); null = the player. */
	public static volatile Boolean autoPlay;

	private final long trialStart;
	private final long openedMs = Util.getMillis();
	private long lastMs;
	private float light = START_LIGHT;
	/** The bead and the sun, 0 (bottom) .. 1 (top), and the bead's speed. */
	private float bead = 0.35f, beadVel;
	private float sun = 0.5f, sunVel, sunTarget = 0.6f;
	private boolean holdingKey, holdingMouse;
	private boolean sent;
	private boolean verdictWin;
	private long verdictUntil;
	private final java.util.Random rnd = new java.util.Random();

	public LetItOutScreen(long trialStart) {
		super(Component.translatable("screen.projecthero.let_it_out"));
		this.trialStart = trialStart;
	}

	public long trialStart() {
		return trialStart;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (!sent) {
			finish(false); // walking away from it is losing it
		}
		super.onClose();
	}

	private SentryState state() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : Sentry.peek(mc.player);
	}

	/** The sun's half-height (0..1 of the shaft): smaller with more Void. */
	private float sunHalf(SentryState s) {
		float v = s == null ? 0f : Mth.clamp(s.voidLevel / SentryConfig.VOID_MAX, 0f, 1f);
		return 0.18f - 0.06f * v;
	}

	@Override
	public void tick() {
		SentryState s = state();
		Minecraft mc = Minecraft.getInstance();
		if (sent) {
			if (Util.getMillis() > verdictUntil) {
				mc.setScreen(null);
			}
			return;
		}
		if (s == null || !LetItOut.running(s) || s.letOutTrialStart != trialStart) {
			sent = true; // the server ended it (a takeover, a death, the timeout)
			mc.setScreen(null);
		}
	}

	private void step(float dt, SentryState s) {
		if (sent) {
			return;
		}
		float v = s == null ? 0f : Mth.clamp(s.voidLevel / SentryConfig.VOID_MAX, 0f, 1f);
		// the sun wanders: a new target now and then, eased toward with a little inertia
		if (Math.abs(sun - sunTarget) < 0.03f || rnd.nextFloat() < dt * 0.45f) {
			sunTarget = 0.15f + rnd.nextFloat() * 0.7f;
		}
		float pull = (sunTarget - sun) * (1.3f + 1.2f * v);
		sunVel += (pull - sunVel) * Math.min(1f, dt * 3f);
		sun = Mth.clamp(sun + sunVel * dt, 0.1f, 0.9f);
		// the bead: the Void pulls it down; holding lifts it
		boolean lift = holdingKey || holdingMouse;
		if (autoPlay != null) {
			lift = autoPlay && (sun - bead) + (sunVel - beadVel) * 0.4f > 0f;
		}
		beadVel += (lift ? 2.6f : -2.2f) * dt;
		beadVel *= (float) Math.pow(0.25, dt);
		bead += beadVel * dt;
		if (bead < 0f) {
			bead = 0f;
			beadVel = 0f;
		} else if (bead > 1f) {
			bead = 1f;
			beadVel = 0f;
		}
		boolean inside = Math.abs(bead - sun) <= sunHalf(s);
		light += (inside ? FILL_PER_SECOND : -DRAIN_PER_SECOND) * dt;
		long elapsed = Util.getMillis() - openedMs;
		if (light >= 100f) {
			light = 100f;
			finish(true);
		} else if (light <= 0f) {
			light = 0f;
			finish(false);
		} else if (elapsed > SentryConfig.TRIAL_TICKS * 50L) {
			finish(false);
		}
	}

	private void finish(boolean won) {
		if (sent) {
			return;
		}
		sent = true;
		verdictWin = won;
		verdictUntil = Util.getMillis() + 1200L;
		ClientPlayNetworking.send(new SentryActionPayload(SentryActionPayload.TRIAL_RESULT, won));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(won ? SoundEvents.WARDEN_EMERGE : SoundEvents.BEACON_DEACTIVATE,
				won ? 0.7f : 1.2f, 0.7f));
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == 32) { // Space
			holdingKey = true;
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean keyReleased(int key, int scan, int mods) {
		if (key == 32) {
			holdingKey = false;
			return true;
		}
		return super.keyReleased(key, scan, mods);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (button == 0) {
			holdingMouse = true;
			return true;
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if (button == 0) {
			holdingMouse = false;
			return true;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		g.fillGradient(0, 0, width, height, 0xE0050308, 0xF0000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		SentryState s = state();
		long ms = Util.getMillis();
		float dt = lastMs == 0L ? 0f : Math.min(0.1f, (ms - lastMs) / 1000f);
		lastMs = ms;
		step(dt, s);
		renderBackground(g, mouseX, mouseY, partial);
		float t = ms / 1000f;
		// the darkness creeping in from the sides, the more the less light he holds
		float dark = 1f - Mth.clamp(light / 100f, 0f, 1f);
		int reach = (int) (width * (0.08f + 0.25f * dark));
		for (int i = 0; i < 24; i++) {
			int y = height * i / 24;
			int len = (int) (reach * (0.6f + 0.4f * Mth.sin(t * 2f + i * 1.7f)));
			g.fillGradient(0, y, len, y + height / 24 + 1, 0xC0000000, 0x00000000);
			g.fill(width - len, y, width, y + height / 24 + 1, 0x60000000);
		}
		// the shaft
		int shaftH = Math.min(150, height - 110);
		int shaftW = 26;
		int sx = width / 2 - shaftW / 2;
		int sy = (height - shaftH) / 2 + 6;
		g.fill(sx - 2, sy - 2, sx + shaftW + 2, sy + shaftH + 2, 0xFF2A2010);
		g.fill(sx, sy, sx + shaftW, sy + shaftH, 0xFF07050C);
		// the sun: a gold band with a halo, pulsing
		float half = sunHalf(s);
		int sunTop = sy + (int) ((1f - (sun + half)) * shaftH);
		int sunBot = sy + (int) ((1f - (sun - half)) * shaftH);
		float pulse = 0.8f + 0.2f * Mth.sin(t * 6f);
		g.fill(sx - 4, sunTop - 3, sx + shaftW + 4, sunBot + 3, ((int) (50 * pulse) << 24) | 0xFFC83A);
		g.fill(sx, sunTop, sx + shaftW, sunBot, ((int) (170 * pulse) << 24) | 0xFFC83A);
		g.fill(sx, sunTop, sx + shaftW, sunTop + 1, 0xFFFFE89A);
		g.fill(sx, sunBot - 1, sx + shaftW, sunBot, 0xFFFFE89A);
		// the bead (his light): white, with a soft glow; violet when outside the sun
		boolean inside = Math.abs(bead - sun) <= half;
		int by = sy + (int) ((1f - bead) * (shaftH - 6));
		g.fill(sx + 3, by - 2, sx + shaftW - 3, by + 8, inside ? 0x60FFFFFF : 0x40B088FF);
		g.fill(sx + 6, by, sx + shaftW - 6, by + 6, inside ? 0xFFFFFFFF : 0xFFC8B0FF);
		// the Light bar
		int bw = Math.min(220, width - 40);
		int bx = (width - bw) / 2, barY = sy + shaftH + 12;
		g.fill(bx - 2, barY - 2, bx + bw + 2, barY + 10, 0xFF3A2A10);
		g.fill(bx, barY, bx + bw, barY + 8, 0xFF120C04);
		g.fill(bx, barY, bx + Math.round(bw * Mth.clamp(light / 100f, 0f, 1f)), barY + 8, 0xFFFFC83A);
		for (int i = 1; i < 10; i++) {
			g.fill(bx + bw * i / 10, barY, bx + bw * i / 10 + 1, barY + 8, 0x60000000);
		}
		long left = Math.max(0L, SentryConfig.TRIAL_TICKS * 50L - (ms - openedMs));
		String secs = String.format(java.util.Locale.ROOT, "%.1f", left / 1000f);
		g.drawCenteredString(font, Component.translatable("screen.projecthero.let_it_out.light", Math.round(light), secs), width / 2, barY + 13,
				0xFFE8D8A0);
		// title + hint, wrapped to the window
		g.drawCenteredString(font, Component.translatable("screen.projecthero.let_it_out"), width / 2, 10, 0xFFB088FF);
		int y = 24;
		for (FormattedCharSequence line : font.split(Component.translatable("screen.projecthero.let_it_out.hint"), Math.min(300, width - 40))) {
			g.drawCenteredString(font, line, width / 2, y, 0xFF9A90B0);
			y += 10;
		}
		if (sent && ms < verdictUntil) {
			int w2 = Math.min(320, width - 20);
			g.fill(0, height / 2 - 16, width, height / 2 + 16, 0xC0000000);
			int ly = height / 2 - 4;
			var lines = font.split(Component.translatable(verdictWin ? "screen.projecthero.let_it_out.won" : "screen.projecthero.let_it_out.lost",
					Math.round(SentryConfig.TRIAL_LOSE_VOID)), w2);
			ly -= (lines.size() - 1) * 5;
			for (FormattedCharSequence line : lines) {
				g.drawCenteredString(font, line, width / 2, ly, verdictWin ? 0xFFB088FF : 0xFFFF5A5A);
				ly += 10;
			}
		}
		// (no super.render: no widgets, and it would draw the background over the shaft)
	}
}
