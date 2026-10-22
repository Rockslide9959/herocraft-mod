package com.projecthero.mod.client.allmight;

import java.util.List;

import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;
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
 *   Power Form [H]                    the form / transforming / PLUS ULTRA 18s (wrapped)
 *   [==============-----]   18:42     the One For All timer (20:00 of Power Form), green / amber low / blinking red empty
 *   -----------                       the United States of Smash charge, a hairline (only while holding Z)
 * </pre>
 * v0.16.7 (user: "remove strain and output, it should just be the 20 minute timer"): the Output / Limit gauge and the three limb
 * bars are gone -- the timer is the whole block again, as in v0.12.39.
 */
public final class OneForAllClientKit implements QuirkClientKit {
	private static final int RED = 0xFFFF4A3A;
	private static final int RED_LIGHT = 0xFFFF8A7A;
	private static final int BAR_BG = 0xC0141414;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int TEXT = 0xFFE2E2E2;
	/** v0.16.7: the timer bar, as v0.12.39 drew it: green, amber when low. */
	private static final int TIMER = 0xFF40E070;
	private static final int TIMER_LOW = 0xFFD0A030;

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

		if (s.fullPower && s.chargeStart > 0L) {
			// the United States of Smash charge: a hairline under the timer
			float cf = Mth.clamp((now - s.chargeStart) / (float) AllMightConfig.UNITED_STATES_CHARGE_TICKS, 0f, 1f);
			y -= 2;
			g.fill(x0, y, x0 + width, y + 1, BAR_BG);
			g.fill(x0, y, x0 + (int) (width * cf), y + 1, 0xFFFFE04A);
			y -= 1;
		}
		// the One For All timer -- m:ss on the right, a draining bar beside it (one 9-px row)
		y = drawTimer(g, mc, s, x0, y - 9, width, blink);

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

	/**
	 * The timer row at {@code y}: a 3-px bar that drains left to right-aligned m:ss. Green while plenty is left, amber below the
	 * steam threshold, blinking red when it is too low to transform. Returns {@code y}.
	 */
	private static int drawTimer(GuiGraphics g, Minecraft mc, AllMightState s, int x0, int y, int width, boolean blink) {
		AllMightConfig.Timer t = AllMightConfig.timer();
		float max = s.ofaTimerMax > 0f ? s.ofaTimerMax : Math.max(1f, t.powerFormSeconds);
		float left = s.ofaTimer < 0f ? max : Math.min(max, s.ofaTimer);
		float frac = Mth.clamp(left / max, 0f, 1f);
		int secs = (int) Math.floor(left + 1.0e-3f);
		String clock = String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
		boolean empty = left + 1.0e-3f < t.minimumToTransformSeconds;
		boolean low = left < t.lowSteamSeconds;
		int colour = empty ? (blink ? RED : RED_LIGHT) : low ? TIMER_LOW : TIMER;
		int cw = mc.font.width("00:00");
		int barW = width - cw - 4;
		int by = y + 3;
		g.fill(x0 - 1, by - 1, x0 + barW + 1, by + 4, BORDER);
		g.fill(x0, by, x0 + barW, by + 3, BAR_BG);
		int fill = Math.round(barW * frac);
		if (fill > 0) {
			g.fill(x0, by, x0 + fill, by + 3, colour);
			g.fill(x0, by, x0 + fill, by + 1, 0x50FFFFFF);
		}
		int tw = mc.font.width(clock);
		g.drawString(mc.font, clock, x0 + width - tw, y, s.fullPower || empty || low ? colour : TEXT, false);
		return y;
	}

	/** The one-line status: transforming, Plus Ultra, or the form. */
	private static Component status(AllMightState s, long now) {
		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();
		if (s.transformUntil > now) {
			return Component.translatable("hud.projecthero.all_might.transforming").withStyle(ChatFormatting.GOLD);
		}
		if (s.fullPower && s.plusUltra) {
			return Component.translatable("hud.projecthero.all_might.plus_ultra", (s.plusUltraUntil - now + 19) / 20)
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		}
		return Component.translatable(s.fullPower ? "hud.projecthero.all_might.power_form" : "hud.projecthero.all_might.base_form", key)
				.withStyle(s.fullPower ? ChatFormatting.GOLD : ChatFormatting.GRAY);
	}

	private static String clock(long ticks) {
		long secs = Math.max(0L, (ticks + 19) / 20);
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}
}
