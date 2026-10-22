package com.projecthero.mod.client.gui;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The All Might HUD (bottom-right). v0.16.7: One For All is a Quirk now -- a holder's HUD (Output, the safe limit, the limbs, the
 * six keys) is the Quirk HUD ({@code QuirkHud} + {@code OneForAllClientKit}). This only remains for a <b>lender</b>, who holds
 * no power while their One For All is out on loan:
 * <pre>
 *   One For All
 *   Lent to Steve -- 3:12
 *   Exhausted without it
 * </pre>
 */
public final class AllMightHud {
	private static final int WIDTH = 20 * 6 + 2 * 5;
	private static final int MARGIN = 4;

	private AllMightHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		AllMightState s = mc.player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || s.hasPower || s.loanRole != AllMightState.LOAN_LENDER) {
			return;
		}
		long now = mc.level.getGameTime();
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int y = g.guiHeight() - 12 - 33;
		g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.title").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), x0, y,
				0xFFFFFFFF, true);
		y += 11;
		drawClipped(g, mc, Component.translatable("hud.projecthero.all_might.lent_to", s.loanPartnerName, clock(s.loanUntil - now))
				.withStyle(ChatFormatting.YELLOW), x0, y, WIDTH);
		y += 11;
		drawClipped(g, mc, Component.translatable("hud.projecthero.all_might.exhausted").withStyle(ChatFormatting.RED), x0, y, WIDTH);
	}

	private static String clock(long ticks) {
		long secs = Math.max(0L, (ticks + 19) / 20);
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}

	/** One HUD line, shrunk to fit the HUD's width rather than sprawling past it (the TooltipWrap rule). */
	private static void drawClipped(GuiGraphics g, Minecraft mc, Component text, int x, int y, int width) {
		int w = mc.font.width(text);
		if (w <= width) {
			g.drawString(mc.font, text, x, y, 0xFFFFFFFF, true);
			return;
		}
		float scale = Math.max(0.6f, width / (float) w);
		g.pose().pushPose();
		g.pose().translate(x, y + (1f - scale) * 4f, 0);
		g.pose().scale(scale, scale, 1f);
		g.drawString(mc.font, text, 0, 0, 0xFFFFFFFF, true);
		g.pose().popPose();
	}
}
