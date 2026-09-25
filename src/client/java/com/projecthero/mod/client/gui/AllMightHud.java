package com.projecthero.mod.client.gui;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightAbilityManager;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The All Might HUD (v0.12.33), laid out like {@link WolverineHud}: bottom-right, the six ability boxes (R G X Z V C) plus
 * the N Leap box with their keys and cooldowns (hold Left-Alt for the move names), the "ONE FOR ALL" label with the current
 * form, the OFA Power bar ({@code OFA: 100 / 100}, each box dims while you cannot afford its cost) and the Full Cowl timer.
 */
public final class AllMightHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int COLOR_BOX_BG = 0xC0101A10;
	private static final int COLOR_BORDER = 0xFF2E6A3E;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8F0DC;
	private static final int COLOR_NAME = 0xFFE0F0E0;
	private static final int COLOR_OFA = 0xFF40E070;
	private static final int COLOR_OFA_LOW = 0xFF9AA030;

	/** Boxes in order: slots 1..6 (R G X Z V C), then N. */
	private static final String[] IDS = {
			AllMightAbilities.DETROIT, AllMightAbilities.TEXAS, AllMightAbilities.NEW_HAMPSHIRE, AllMightAbilities.CAROLINA,
			AllMightAbilities.UNITED_STATES, AllMightAbilities.COWL, AllMightAbilities.LEAP };

	private AllMightHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null || !AllMight.hasPower(mc.player)) {
			return;
		}
		AllMightState s = mc.player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		int totalW = BOX * 7 + GAP * 6;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - BOX - MARGIN - 26 - 24;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		for (int i = 0; i < 7; i++) {
			int x = x0 + i * (BOX + GAP);
			String id = IDS[i];
			String key = i < 6 ? String.valueOf(AbilitySlot.byNumber(i + 1).defaultKey()) : "N";
			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			boolean cowlUp = AllMightAbilities.COWL.equals(id) && s.cowlUntil > now;
			boolean poor = s.ofa + 1.0e-3f < AllMightAbilityManager.cost(id);
			if (cowlUp) {
				float frac = (s.cowlUntil - now) / (float) AllMightConfig.COWL_DURATION_TICKS;
				int h = (int) (BOX * Math.max(0f, Math.min(1f, frac)));
				g.fill(x + 1, y0 + BOX - h, x + BOX - 1, y0 + BOX - 1, 0xA040E070);
			}
			int cd = AllMight.cooldownRemaining(mc.player, id);
			int max = AllMightAbilityManager.maxCooldown(id);
			if (cd > 0 && max > 0 && !cowlUp) {
				int h = (int) (BOX * Math.min(1f, cd / (float) max));
				g.fill(x, y0 + BOX - h, x + BOX, y0 + BOX, COLOR_COOLDOWN);
				g.drawString(mc.font, String.valueOf((cd + 19) / 20), x + 5, y0 + 6, 0xFFFFFFFF, true);
			} else {
				g.drawString(mc.font, key, x + 7, y0 + 6, poor ? 0xFF7A7A7A : (cowlUp ? 0xFFFFFFFF : COLOR_KEY), true);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.all_might.ability." + id);
				g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10 - 62, COLOR_NAME, true);
			}
		}

		int line = y0 + BOX + 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.title")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), x0, line, 0xFF40E070, true);
		Component form = Component.translatable(s.fullPower ? "hud.projecthero.all_might.form_full" : "hud.projecthero.all_might.form_base");
		g.drawString(mc.font, form.copy().withStyle(s.fullPower ? ChatFormatting.GOLD : ChatFormatting.GRAY),
				x0 + totalW - mc.font.width(form), line, 0xFFFFFFFF, true);
		line += 10;

		// OFA Power bar
		float frac = Math.max(0f, Math.min(1f, s.ofa / AllMightConfig.OFA_MAX));
		g.fill(x0, line, x0 + totalW, line + 9, COLOR_BOX_BG);
		g.fill(x0, line, x0 + (int) (totalW * frac), line + 9, frac < 0.2f ? COLOR_OFA_LOW : COLOR_OFA);
		g.renderOutline(x0 - 1, line - 1, totalW + 2, 11, COLOR_BORDER);
		Component txt = Component.translatable("hud.projecthero.all_might.ofa", (int) Math.floor(s.ofa), (int) AllMightConfig.OFA_MAX);
		g.drawString(mc.font, txt, x0 + (totalW - mc.font.width(txt)) / 2, line + 1, 0xFFFFFFFF, true);
		line += 12;

		if (s.cowlUntil > now) {
			g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.cowl", (int) ((s.cowlUntil - now + 19) / 20))
					.withStyle(ChatFormatting.GREEN), x0, line, 0xFF40E070, true);
		} else if (s.transformUntil > now) {
			g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.transforming").withStyle(ChatFormatting.GOLD),
					x0, line, 0xFFFFAA00, true);
		}
	}
}
