package com.projecthero.mod.client.allmight;

import java.util.List;

import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.AllMightStrain;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.allmight.data.AllMightState.Limb;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.quirk.QuirkClientKit;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: One For All on the Quirk HUD -- its status block, drawn by {@code QuirkHud} between the Quirk's name and the six keys
 * (the old separate All Might HUD only remains for a lender, who holds no power). Top to bottom:
 * <pre>
 *   Power Form [H]                    the form / PLUS ULTRA 18s / a broken limb and how far it has to mend (wrapped)
 *   Output 45%            Limit 20%   Output (gold, red past the limit) and the safe limit
 *   [=====|######-----------]         the gauge: gold up to the limit, red past it; the white tick is the limit
 *   R arm     L arm     Legs          the three limbs: a bar each, green / yellow / orange / blinking red (broken)
 *   ----      ------    ---
 * </pre>
 */
public final class OneForAllClientKit implements QuirkClientKit {
	private static final int GOLD = 0xFFFFC83A;
	private static final int GOLD_WHITE = 0xFFFFF0B0;
	private static final int RED = 0xFFFF4A3A;
	private static final int RED_LIGHT = 0xFFFF8A7A;
	private static final int BAR_BG = 0xC0141414;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int TEXT = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int[] TIER_COLOUR = { 0xFF7FD06A, 0xFFE8C040, 0xFFFF8A2A, 0xFFFF3A2A };

	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower) {
			return -1;
		}
		Minecraft mc = Minecraft.getInstance();
		long now = player.level().getGameTime();
		boolean blink = (now / 5L) % 2L == 0L;
		int y = bottom;

		// the limbs: three cells, a label over a bar
		int gap = 4;
		int cellW = (width - 2 * gap) / 3;
		y -= 3;
		int labelY = y - 10;
		for (Limb l : Limb.values()) {
			int cx = x0 + l.ordinal() * (cellW + gap);
			int tier = AllMightStrain.tier(s, l);
			float v = Mth.clamp(s.limb(l) / 100f, 0f, 1f);
			g.fill(cx, y, cx + cellW, y + 3, BAR_BG);
			int col = tier == 3 ? (blink ? TIER_COLOUR[3] : RED_LIGHT) : TIER_COLOUR[tier];
			int fill = tier == 3 ? cellW : Math.round(cellW * v);
			if (fill > 0) {
				g.fill(cx, y, cx + fill, y + 3, col);
			}
			Component label = Component.translatable("hud.projecthero.all_might.limb." + l.id);
			int lc = tier == 0 ? HINT : tier == 3 ? RED : TIER_COLOUR[tier];
			drawFit(g, mc, label, cx, labelY, cellW, lc);
		}
		y = labelY - 4;

		// the gauge: Output, with the limit marked
		float limit = AllMightStrain.limit(s);
		float out = s.fullPower ? s.outputPct : 0f;
		boolean over = out > limit + 0.01f;
		int gh = 5;
		y -= gh;
		g.fill(x0 - 1, y - 1, x0 + width + 1, y + gh + 1, BORDER);
		g.fill(x0, y, x0 + width, y + gh, BAR_BG);
		int limX = x0 + Math.round(width * Mth.clamp(limit / 100f, 0f, 1f));
		int outX = x0 + Math.round(width * Mth.clamp(out / 100f, 0f, 1f));
		// the safe zone, faintly, then Output over it
		g.fill(x0, y, limX, y + gh, 0x40FFC83A);
		if (outX > x0) {
			g.fill(x0, y, Math.min(outX, limX), y + gh, out >= 90f ? GOLD_WHITE : GOLD);
			if (over) {
				g.fill(limX, y, outX, y + gh, blink ? RED : RED_LIGHT);
			}
			g.fill(x0, y, outX, y + 1, 0x60FFFFFF);
		}
		g.fill(limX - 1, y - 2, limX + 1, y + gh + 2, 0xFFFFFFFF); // the limit tick
		if (s.fullPower && s.chargeStart > 0L) {
			// the United States of Smash charge: a hairline over the gauge
			float cf = Mth.clamp((now - s.chargeStart) / (float) AllMightConfig.UNITED_STATES_CHARGE_TICKS, 0f, 1f);
			g.fill(x0, y - 4, x0 + (int) (width * cf), y - 3, 0xFFFFE04A);
			y -= 3;
		}
		y -= 2 + 9;

		// Output and the limit
		Component outText = s.fullPower
				? Component.translatable("hud.projecthero.all_might.output", Math.round(out))
				: Component.translatable("hud.projecthero.all_might.output_off");
		Component limText = Component.translatable("hud.projecthero.all_might.limit", limitText(limit));
		int lw = mc.font.width(limText);
		drawFit(g, mc, outText, x0, y, width - lw - 4, !s.fullPower ? HINT : over ? (blink ? RED : RED_LIGHT) : out >= 90f ? GOLD_WHITE : GOLD);
		g.drawString(mc.font, limText, x0 + width - lw, y, TEXT, false);

		// the status line (wrapped to the width, drawn upward)
		Component status = status(s, now);
		List<FormattedCharSequence> lines = mc.font.split(status, width);
		y -= 2 + lines.size() * 9;
		int ly = y;
		for (FormattedCharSequence line : lines) {
			g.drawString(mc.font, line, x0, ly, 0xFFFFFFFF, false);
			ly += 9;
		}
		if (s.loanRole == AllMightState.LOAN_BORROWER) {
			Component loan = Component.translatable("hud.projecthero.all_might.borrowed_from", s.loanPartnerName, clock(s.loanUntil - now))
					.withStyle(ChatFormatting.YELLOW);
			List<FormattedCharSequence> ll = mc.font.split(loan, width);
			y -= ll.size() * 9;
			int yy = y;
			for (FormattedCharSequence line : ll) {
				g.drawString(mc.font, line, x0, yy, 0xFFFFFFFF, false);
				yy += 9;
			}
		}
		return y - 2;
	}

	private static String limitText(float limit) {
		return limit >= 99.95f ? "100" : Math.abs(limit - Math.round(limit)) < 0.05f ? String.valueOf(Math.round(limit))
				: String.format(java.util.Locale.ROOT, "%.1f", limit);
	}

	/** The one-line status: transforming, Plus Ultra, a broken limb, or the form. */
	private static Component status(AllMightState s, long now) {
		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();
		if (s.transformUntil > now) {
			return Component.translatable("hud.projecthero.all_might.transforming").withStyle(ChatFormatting.GOLD);
		}
		if (s.fullPower && s.plusUltra) {
			return Component.translatable("hud.projecthero.all_might.plus_ultra", (s.plusUltraUntil - now + 19) / 20)
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		}
		for (Limb l : Limb.values()) {
			if (s.isBroken(l)) {
				return Component.translatable("hud.projecthero.all_might.broken." + l.id, Math.round(s.limb(l)), Math.round(AllMightConfig.LIMB_MENDED))
						.withStyle(ChatFormatting.RED);
			}
		}
		return Component.translatable(s.fullPower ? "hud.projecthero.all_might.power_form" : "hud.projecthero.all_might.base_form", key)
				.withStyle(s.fullPower ? ChatFormatting.GOLD : ChatFormatting.GRAY);
	}

	/** One label, shrunk to fit its width rather than spilling past it (the TooltipWrap rule). */
	private static void drawFit(GuiGraphics g, Minecraft mc, Component text, int x, int y, int width, int colour) {
		int w = mc.font.width(text);
		if (w <= width) {
			g.drawString(mc.font, text, x, y, colour, false);
			return;
		}
		float scale = Math.max(0.6f, width / (float) w);
		g.pose().pushPose();
		g.pose().translate(x, y + (1f - scale) * 4f, 0);
		g.pose().scale(scale, scale, 1f);
		g.drawString(mc.font, text, 0, 0, colour, false);
		g.pose().popPose();
	}

	private static String clock(long ticks) {
		long secs = Math.max(0L, (ticks + 19) / 20);
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}
}
