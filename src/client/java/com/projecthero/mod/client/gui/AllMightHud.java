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
 * The All Might HUD (bottom-right), v0.12.34 layout:
 * <pre>
 *   Base Form:   One For All / thin OFA bar + percentage / "Base Form [H]"
 *   Power Form:  One For All / thin OFA bar + percentage / the six ability keys (R G X Z V C) / "Power Form [H]"
 * </pre>
 * Shift+R (New Hampshire) is not drawn as a key. Hold Left-Alt for the move names.
 */
public final class AllMightHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int COLOR_BOX_BG = 0xC0101A10;
	private static final int COLOR_BORDER = 0xFF2E6A3E;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8F0DC;
	private static final int COLOR_NAME = 0xFFE0F0E0;
	private static final int COLOR_OFA = 0xFF40E070;
	private static final int COLOR_OFA_LOW = 0xFFD0A030;

	/** Boxes in slot order: R G X Z V C. */
	private static final String[] IDS = {
			AllMightAbilities.DETROIT, AllMightAbilities.TEXAS, AllMightAbilities.LEAP, AllMightAbilities.UNITED_STATES,
			AllMightAbilities.CAROLINA, AllMightAbilities.PLUS_ULTRA };

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
		boolean power = s.fullPower;
		int totalW = BOX * 6 + GAP * 5;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int height = 11 + 11 + BAR_H + 3 + (power ? BOX + 3 : 0) + 11;
		int y = g.guiHeight() - 12 - height;

		g.drawString(mc.font, Component.translatable("hud.projecthero.all_might.title")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), x0, y, 0xFF40E070, true);
		y += 11;

		// One For All: thin bar + percentage
		float frac = Math.max(0f, Math.min(1f, s.ofa / AllMightConfig.OFA_MAX));
		int pct = (int) Math.floor(frac * 100.0f + 1.0e-3f);
		String pctText = pct + "%";
		g.drawString(mc.font, pctText, x0 + totalW - mc.font.width(pctText), y, 0xFFFFFFFF, true);
		y += 11;
		g.fill(x0, y, x0 + totalW, y + BAR_H, COLOR_BOX_BG);
		g.fill(x0, y, x0 + (int) (totalW * frac), y + BAR_H, s.ofa < AllMightConfig.LOW_OFA_STEAM ? COLOR_OFA_LOW : COLOR_OFA);
		g.renderOutline(x0 - 1, y - 1, totalW + 2, BAR_H + 2, COLOR_BORDER);
		y += BAR_H + 3;

		if (power) {
			boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
					org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			for (int i = 0; i < 6; i++) {
				int x = x0 + i * (BOX + GAP);
				String id = IDS[i];
				g.fill(x, y, x + BOX, y + BOX, COLOR_BOX_BG);
				g.renderOutline(x, y, BOX, BOX, COLOR_BORDER);
				boolean on = AllMightAbilities.PLUS_ULTRA.equals(id) && s.plusUltra;
				boolean poor = s.ofa + 1.0e-3f < AllMightAbilityManager.cost(id);
				if (on) {
					g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0xA040E070);
				}
				int cd = AllMight.cooldownRemaining(mc.player, id);
				int max = AllMightAbilityManager.maxCooldown(id);
				if (cd > 0 && max > 0 && !on) {
					int h = (int) (BOX * Math.min(1f, cd / (float) max));
					g.fill(x, y + BOX - h, x + BOX, y + BOX, COLOR_COOLDOWN);
					g.drawString(mc.font, String.valueOf((cd + 19) / 20), x + 5, y + 6, 0xFFFFFFFF, true);
				} else {
					g.drawString(mc.font, String.valueOf(AbilitySlot.byNumber(i + 1).defaultKey()), x + 7, y + 6,
							poor ? 0xFF7A7A7A : (on ? 0xFFFFFFFF : COLOR_KEY), true);
				}
				if (expanded) {
					Component name = Component.translatable("projecthero.all_might.ability." + id);
					g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y + i * 10 - 30, COLOR_NAME, true);
				}
			}
			y += BOX + 3;
		}

		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();
		Component line = s.transformUntil > now
				? Component.translatable("hud.projecthero.all_might.transforming").withStyle(ChatFormatting.GOLD)
				: Component.translatable(power ? "hud.projecthero.all_might.power_form" : "hud.projecthero.all_might.base_form", key)
						.withStyle(power ? ChatFormatting.GOLD : ChatFormatting.GRAY);
		g.drawString(mc.font, line, x0, y, 0xFFFFFFFF, true);
	}
}
