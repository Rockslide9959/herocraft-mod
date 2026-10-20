package com.projecthero.mod.client.miraculous.viperion;

import com.projecthero.mod.client.miraculous.MiraculousHud;
import com.projecthero.mod.miraculous.viperion.Viperion;
import com.projecthero.mod.miraculous.viperion.ViperionConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.16.7: Viperion's Second Chance on his HUD.
 *
 * <p>v0.16.7 (user): a card stacked right above the ability HUD (a {@link MiraculousHud.Panel}, the width of the six
 * boxes), not floating over the hotbar:
 * <pre>
 *  ┌──────────────────────────────┐
 *  │ (snake)  SECOND CHANCE       │   the snake ring drains as the window runs out -- its head chases its tail
 *  │  ring    ◆ ◆ ◇ rewinds  1:24 │   one scale per rewind left, the time left
 *  │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│   the window, a draining bar (gold, then a red blink, in the last 10 s)
 *  └──────────────────────────────┘
 * </pre>
 * And the screen's green flashes: a soft one at the checkpoint, a stronger one at every rewind.
 */
public final class ViperionHud implements MiraculousHud.Panel {
	private static final int GREEN = 0xFF3CE0A0;
	private static final int GREEN_HI = 0xFFA8FFD8;
	private static final int GREEN_D = 0xFF1E7A50;
	private static final int GREEN_OFF = 0xFF173326;
	private static final int GOLD = 0xFFFFD25A;
	private static final int RED = 0xFFFF5A5A;
	private static final int EYE = 0xFFFFE860;
	private static final int BG = 0xD0080808;
	private static final int BG_TOP = 0xD0102018;
	private static final int BORDER = 0xFF2F6B50;
	private static final int TEXT_DIM = 0xFF8FB8A4;

	private static final int HEIGHT = 30;
	private static final int SCALES = 24;

	private ViperionHud() {
	}

	public static void initialize() {
		MiraculousHud.addPanel(new ViperionHud());
	}

	@Override
	public int height(LocalPlayer player) {
		return Viperion.is(player) && Viperion.checkpointTicksLeft(player) > 0 ? HEIGHT : 0;
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, int x, int y, int w, float partial) {
		LocalPlayer p = mc.player;
		long now = mc.level.getGameTime();
		int left = Viperion.checkpointTicksLeft(p);
		int rewinds = Viperion.rewindsLeft(p);
		float frac = Mth.clamp((left - partial) / (float) ViperionConfig.SECOND_CHANCE_WINDOW, 0f, 1f);
		boolean late = left <= 200;
		boolean blink = late && (now / 5) % 2 == 0;
		int accent = !late ? GREEN : blink ? RED : GOLD;

		// the card: a dark body, a faint green wash at the top, a thin border lit in the accent in the last 10 s
		g.fill(x, y, x + w, y + HEIGHT, BG);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + 12, BG_TOP, 0x00000000);
		g.renderOutline(x, y, w, HEIGHT, late ? accent : BORDER);

		// the snake ring: lit scales for the time left, the head at the end of them, biting toward the tail
		int cx = x + 14, cy = y + 13, r = 9;
		int lit = Math.round(frac * SCALES);
		for (int i = 0; i < SCALES; i++) {
			double a = Math.PI * 2 * i / SCALES - Math.PI / 2;
			int sx = cx + (int) Math.round(Math.cos(a) * r), sy = cy + (int) Math.round(Math.sin(a) * r);
			int c = i < lit ? (i % 2 == 0 ? accent : GREEN_D) : GREEN_OFF;
			g.fill(sx - 1, sy - 1, sx + 1, sy + 1, c);
		}
		if (lit > 0) {
			double ha = Math.PI * 2 * (lit - 0.5) / SCALES - Math.PI / 2;
			int hx = cx + (int) Math.round(Math.cos(ha) * r), hy = cy + (int) Math.round(Math.sin(ha) * r);
			g.fill(hx - 2, hy - 2, hx + 2, hy + 2, accent);
			g.fill(hx - 1, hy - 1, hx, hy, EYE);
		}
		// the clock hand in the middle: one turn per window
		double hand = Math.PI * 2 * (1f - frac) - Math.PI / 2;
		for (int k = 0; k <= 4; k++) {
			int hx = cx + (int) Math.round(Math.cos(hand) * k), hy = cy + (int) Math.round(Math.sin(hand) * k);
			g.fill(hx, hy, hx + 1, hy + 1, k == 0 ? GREEN_HI : TEXT_DIM);
		}

		// the title and the time
		int tx = x + 28;
		g.drawString(mc.font, Component.translatable("projecthero.miraculous.ability.viperion_second_chance"), tx, y + 4, GREEN_HI, false);
		int secs = (left + 19) / 20;
		String time = secs / 60 + ":" + String.format("%02d", secs % 60);

		g.drawString(mc.font, time, x + w - 4 - mc.font.width(time), y + 15, late ? accent : 0xFFFFFFFF, false);

		// one scale per rewind left (diamonds), then the word
		int total = ViperionConfig.SECOND_CHANCE_REWINDS;
		int py = y + 18;
		for (int i = 0; i < total; i++) {
			scale(g, tx + 2 + i * 9, py, i < rewinds ? GREEN : GREEN_OFF, i < rewinds);
		}
		g.drawString(mc.font, Component.translatable("hud.projecthero.viperion.rewinds"), tx + total * 9 - 1, py - 3, TEXT_DIM, false);

		// the window: a draining bar along the bottom edge
		int bx0 = x + 2, bx1 = x + w - 2, by = y + HEIGHT - 3;
		g.fill(bx0, by, bx1, by + 1, GREEN_OFF);
		g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * frac), by + 1, accent);
	}

	/** A small diamond scale, a highlight on the lit ones. */
	private static void scale(GuiGraphics g, int cx, int cy, int c, boolean lit) {
		g.fill(cx - 1, cy - 3, cx + 1, cy + 3, c);
		g.fill(cx - 2, cy - 2, cx + 2, cy + 2, c);
		g.fill(cx - 3, cy - 1, cx + 3, cy + 1, c);
		if (lit) {
			g.fill(cx - 1, cy - 2, cx, cy - 1, GREEN_HI);
		}
	}

	/** The screen's green flashes (the card itself is drawn by {@link MiraculousHud}). */
	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Viperion.is(mc.player)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		flash(g, now, partial, Viperion.val(mc.player, Viperion.V_CHECKPOINT_AT), 10f, 70);
		flash(g, now, partial, Viperion.val(mc.player, Viperion.V_REWIND_AT), 16f, 130);
	}

	private static void flash(GuiGraphics g, long now, float partial, long at, float len, int maxAlpha) {
		if (at <= 0L) {
			return;
		}
		float age = now - at + partial;
		if (age < 0f || age > len) {
			return;
		}
		float a = 1f - age / len;
		int alpha = (int) (Mth.clamp(a, 0f, 1f) * maxAlpha);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0x40E0A0);
	}
}
