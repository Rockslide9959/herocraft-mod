package com.projecthero.mod.client.gui;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightAbilityManager;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The All Might HUD (bottom-right). v0.16.6 layout -- Output and Strain replace the old 5:00 OFA timer:
 * <pre>
 *   One For All                        45%     (the Output level; "(borrowed)" on a loan)
 *   [ 20 ][ 45 ][ 100 ]                        Output gauge: reached levels solid, the next one filling with landed hits
 *   Strain                             62%
 *   ------------------------                   Strain hairline (orange, red and pulsing from 75)
 *   [R][G][X][Z][V][C]                         Power Form keys (crossed out while his arms have given out)
 *   Power Form [H]  /  Arms gave out 14s  /  Plus Ultra 18s
 * </pre>
 * The Base Form shows the title, Strain (while any is left) and "Base Form [H]"; a lender sees who has their One For All and for
 * how long. Hold Left-Alt for the shared move list (every key with its Shift move).
 */
public final class AllMightHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int SEG_H = 4;
	private static final int COLOR_BOX_BG = 0xC0101A10;
	private static final int COLOR_BORDER = 0xFF2E6A3E;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8F0DC;
	private static final int COLOR_GOLD = 0xFFFFC83A;
	private static final int COLOR_GOLD_WHITE = 0xFFFFF0B0;
	private static final int COLOR_FILLING = 0xA0FFC83A;
	private static final int COLOR_STRAIN = 0xFFFF8A2A;
	private static final int COLOR_STRAIN_HIGH = 0xFFFF3A2A;
	private static final int COLOR_LOCK = 0xC0400808;

	/** Boxes in slot order: R G X Z V C. */
	private static final String[] IDS = {
			AllMightAbilities.DETROIT, AllMightAbilities.TEXAS, AllMightAbilities.LEAP, AllMightAbilities.UNITED_STATES,
			AllMightAbilities.CAROLINA, AllMightAbilities.PLUS_ULTRA };
	/** Their Shift moves. */
	private static final String[] SHIFT_IDS = {
			AllMightAbilities.NEW_HAMPSHIRE, AllMightAbilities.DELAWARE, AllMightAbilities.MANCHESTER, AllMightAbilities.OKLAHOMA,
			AllMightAbilities.MISSOURI, AllMightAbilities.ST_LOUIS };

	private AllMightHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		AllMightState s = mc.player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null) {
			return;
		}
		if (!s.hasPower) {
			if (s.loanRole == AllMightState.LOAN_LENDER) {
				renderLender(g, mc, s);
			}
			return;
		}
		long now = mc.level.getGameTime();
		boolean power = s.fullPower;
		boolean recoil = s.recoilUntil > now;
		int totalW = BOX * 6 + GAP * 5;
		int x0 = g.guiWidth() - MARGIN - totalW;
		boolean charging = power && s.chargeStart > 0L;
		boolean showStrain = power || s.strain > 0.05f;
		boolean loan = s.loanRole == AllMightState.LOAN_BORROWER;
		int height = 11 + (power ? SEG_H + 3 : 0) + (showStrain ? 10 + BAR_H + 3 : 0) + (power ? BOX + 3 : 0) + (charging ? BAR_H + 3 : 0) + 11
				+ (loan ? 10 : 0);
		int y = g.guiHeight() - 12 - height;
		// v0.16.6: Left-Alt -> the shared move list, stacked above the title (every key with its Shift move, then H and N)
		if (MoveListOverlay.held()) {
			java.util.List<MoveListOverlay.Row> rows = new java.util.ArrayList<>();
			if (power) {
				for (int i = 0; i < 6; i++) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(i), Component.translatable("projecthero.all_might.ability." + IDS[i]),
							Component.translatable("projecthero.all_might.ability." + SHIFT_IDS[i])));
				}
			}
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(6), Component.translatable("projecthero.all_might.ability.transform"), null));
			if (power) {
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(7), Component.translatable("projecthero.all_might.ability.locker"), null));
			}
			MoveListOverlay.draw(g, x0 + totalW, y, rows);
		}

		// title + the Output level
		int level = Math.max(0, Math.min(2, s.output));
		Component title = Component.translatable(loan ? "hud.projecthero.all_might.title_borrowed" : "hud.projecthero.all_might.title")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
		g.drawString(mc.font, title, x0, y, 0xFF40E070, true);
		if (power) {
			String pct = AllMightConfig.OUTPUT_PERCENT[level] + "%";
			int col = recoil ? 0xFF8A8A8A : level == 2 ? COLOR_GOLD_WHITE : level == 1 ? COLOR_GOLD : 0xFFFFFFFF;
			g.drawString(mc.font, pct, x0 + totalW - mc.font.width(pct), y, col, true);
		}
		y += 11;

		if (power) {
			// the Output gauge: three segments -- reached ones solid, the next one filling with landed hits
			int segW = (totalW - 2 * GAP) / 3;
			int cap = loan ? AllMightConfig.BORROWED_OUTPUT_CAP : 2;
			for (int i = 0; i < 3; i++) {
				int sx = x0 + i * (segW + GAP);
				g.fill(sx, y, sx + segW, y + SEG_H, COLOR_BOX_BG);
				if (i > cap) {
					// past a borrower's cap: hatched out
					for (int hx = 0; hx < segW; hx += 3) {
						g.fill(sx + hx, y, sx + hx + 1, y + SEG_H, 0xFF3A3A3A);
					}
					continue;
				}
				if (i <= level) {
					g.fill(sx, y, sx + segW, y + SEG_H, recoil ? 0xFF6A6A6A : i == 2 ? COLOR_GOLD_WHITE : COLOR_GOLD);
				} else if (i == level + 1 && s.outputProgress > 0f && !recoil) {
					g.fill(sx, y, sx + (int) (segW * Math.min(1f, s.outputProgress)), y + SEG_H, COLOR_FILLING);
				}
			}
			y += SEG_H + 3;
		}

		if (showStrain) {
			float sf = Math.max(0f, Math.min(1f, s.strain / AllMightConfig.STRAIN_MAX));
			boolean high = s.strain >= AllMightConfig.STRAIN_WARNING;
			g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.strain").withStyle(high ? ChatFormatting.RED : ChatFormatting.GRAY),
					x0, y, 0xFFFFFFFF, true);
			String sp = Math.round(sf * 100f) + "%";
			g.drawString(mc.font, sp, x0 + totalW - mc.font.width(sp), y, high ? 0xFFFF6A5A : 0xFFD0D0D0, true);
			y += 10;
			g.fill(x0, y, x0 + totalW, y + BAR_H, COLOR_BOX_BG);
			boolean blink = high && (now / 5L) % 2L == 0L;
			g.fill(x0, y, x0 + (int) (totalW * sf), y + BAR_H, high ? (blink ? 0xFFFF7A6A : COLOR_STRAIN_HIGH) : COLOR_STRAIN);
			y += BAR_H + 3;
		}

		if (power) {
			for (int i = 0; i < 6; i++) {
				int x = x0 + i * (BOX + GAP);
				String id = IDS[i];
				g.fill(x, y, x + BOX, y + BOX, COLOR_BOX_BG);
				g.renderOutline(x, y, BOX, BOX, COLOR_BORDER);
				boolean on = AllMightAbilities.PLUS_ULTRA.equals(id) && s.plusUltra;
				boolean locked = recoil && !AllMightAbilities.LEAP.equals(id);
				if (on) {
					g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0xA0E0B030);
				}
				int cd = AllMight.cooldownRemaining(mc.player, id);
				int max = AllMightAbilityManager.maxCooldown(id);
				if (locked) {
					g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, COLOR_LOCK);
					g.drawString(mc.font, "x", x + 7, y + 6, 0xFFFF6A5A, true);
				} else if (cd > 0 && max > 0 && !on) {
					int h = (int) (BOX * Math.min(1f, cd / (float) max));
					g.fill(x, y + BOX - h, x + BOX, y + BOX, COLOR_COOLDOWN);
					g.drawString(mc.font, String.valueOf((cd + 19) / 20), x + 5, y + 6, 0xFFFFFFFF, true);
				} else if (on) {
					// Plus Ultra is running: the seconds it has left
					g.drawString(mc.font, String.valueOf((int) Math.max(0L, (s.plusUltraUntil - now + 19) / 20)), x + 5, y + 6, 0xFFFFFFFF, true);
				} else {
					g.drawString(mc.font, String.valueOf(AbilitySlot.byNumber(i + 1).defaultKey()), x + 7, y + 6, COLOR_KEY, true);
				}
			}
			y += BOX + 3;
			if (charging) {
				// United States of Smash: a hairline charge bar while Z is held
				float cf = Math.max(0f, Math.min(1f, (now - s.chargeStart) / (float) AllMightConfig.UNITED_STATES_CHARGE_TICKS));
				g.fill(x0, y, x0 + totalW, y + BAR_H, COLOR_BOX_BG);
				g.fill(x0, y, x0 + (int) (totalW * cf), y + BAR_H, 0xFFFFE04A);
				y += BAR_H + 3;
			}
		}

		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();
		Component line;
		if (s.transformUntil > now) {
			line = Component.translatable("hud.projecthero.all_might.transforming").withStyle(ChatFormatting.GOLD);
		} else if (recoil) {
			line = Component.translatable("hud.projecthero.all_might.recoil", (s.recoilUntil - now + 19) / 20).withStyle(ChatFormatting.RED);
		} else if (power && s.plusUltra) {
			line = Component.translatable("hud.projecthero.all_might.plus_ultra", (s.plusUltraUntil - now + 19) / 20)
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		} else {
			line = Component.translatable(power ? "hud.projecthero.all_might.power_form" : "hud.projecthero.all_might.base_form", key)
					.withStyle(power ? ChatFormatting.GOLD : ChatFormatting.GRAY);
		}
		drawClipped(g, mc, line, x0, y, totalW);
		y += 11;
		if (loan) {
			drawClipped(g, mc, Component.translatable("hud.projecthero.all_might.borrowed_from", s.loanPartnerName, clock(s.loanUntil - now))
					.withStyle(ChatFormatting.YELLOW), x0, y, totalW);
		}
	}

	/** A lender: who holds their One For All, how long is left, and that they are exhausted. */
	private static void renderLender(GuiGraphics g, Minecraft mc, AllMightState s) {
		long now = mc.level.getGameTime();
		int totalW = BOX * 6 + GAP * 5;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y = g.guiHeight() - 12 - 33;
		g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.title").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), x0, y, 0xFFFFFFFF, true);
		y += 11;
		drawClipped(g, mc, Component.translatable("hud.projecthero.all_might.lent_to", s.loanPartnerName, clock(s.loanUntil - now))
				.withStyle(ChatFormatting.YELLOW), x0, y, totalW);
		y += 11;
		drawClipped(g, mc, Component.translatable("hud.projecthero.all_might.exhausted").withStyle(ChatFormatting.RED), x0, y, totalW);
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
