package com.projecthero.mod.client.miraculous.multimouse;

import com.projecthero.mod.client.miraculous.MiraculousHud;
import com.projecthero.mod.miraculous.multimouse.Multimouse;
import com.projecthero.mod.miraculous.multimouse.MultimouseConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.17.1: Multimouse's own HUD card, stacked above the ability HUD (the shared {@link MiraculousHud.Panel}): while
 * Multitude runs, one little mouse pip per copy (lit = still fighting) and a bar for the time left; while Scurry runs,
 * its time left. The label wraps to the panel's width.
 */
public final class MultimouseHud implements MiraculousHud.Panel {
	private static final int PINK = 0xFFE0559E;
	private static final int PIP_OFF = 0xFF3A2A34;
	private static final int TEXT = 0xFFE2E2E2;

	private MultimouseHud() {
	}

	public static void initialize() {
		MiraculousHud.addPanel(new MultimouseHud());
	}

	private static Component label(LocalPlayer p) {
		if (Multimouse.multitude(p)) {
			return Component.translatable("hud.projecthero.multimouse.multitude", Multimouse.copiesLeft(p),
					Math.max(1, (int) Multimouse.val(p, Multimouse.V_MULTI_TOTAL)));
		}
		return Component.translatable("hud.projecthero.multimouse.scurry");
	}

	@Override
	public int height(LocalPlayer player) {
		if (!Multimouse.multitude(player) && !Multimouse.scurrying(player)) {
			return 0;
		}
		int lines = Minecraft.getInstance().font.split(label(player), 6 * 20 + 5 * 2).size(); // the six boxes' width
		return lines * 9 + (Multimouse.multitude(player) ? 11 : 5);
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, int x, int y, int width, float partial) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		int yy = y;
		for (FormattedCharSequence line : mc.font.split(label(p), width)) {
			g.drawString(mc.font, line, x, yy, TEXT, false);
			yy += 9;
		}
		long now = p.level().getGameTime();
		float frac;
		if (Multimouse.multitude(p)) {
			int total = Math.max(1, (int) Multimouse.val(p, Multimouse.V_MULTI_TOTAL));
			int left = Multimouse.copiesLeft(p);
			int pipW = Math.max(3, Math.min(9, (width - (total - 1)) / total));
			for (int i = 0; i < total; i++) {
				int px = x + i * (pipW + 1);
				g.fill(px, yy, px + pipW, yy + 4, i < left ? PINK : PIP_OFF);
			}
			yy += 6;
			frac = Multimouse.multitudeTicksLeft(p) / (float) MultimouseConfig.MULTITUDE_TICKS;
		} else {
			frac = Math.max(0L, Multimouse.val(p, Multimouse.V_SCURRY) - now) / (float) MultimouseConfig.SCURRY_TICKS;
		}
		g.fill(x, yy, x + width, yy + 3, 0xC0000000);
		g.fill(x, yy, x + Math.round(width * Math.min(1f, Math.max(0f, frac))), yy + 3, PINK);
	}
}
