package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Hulk HUD (v0.13.11, Phase 1): the Rage bar, drawn in the bottom-right ability HUD exactly where Thor's
 * Storm Energy meter sits (label + percentage, then a Hairline bar under it), with a white tick at the
 * 75-rage mark where H starts to work. Only drawn for a player with the Gamma power. Phase 2 adds the
 * Thunderclap / Ground Smash / Super Leap cooldown boxes in the row above it.
 */
public final class HulkHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int HAIRLINE = 3;

	private static final int COLOR_BG = 0x80000000;
	private static final int COLOR_RAGE = 0xFF3FAF3A;
	private static final int COLOR_RAGE_READY = 0xFF7CFF4A;
	private static final int COLOR_RAGE_HULK = 0xFF9BFF3A;
	private static final int COLOR_LABEL = 0xFF9FD890;
	private static final int COLOR_EXHAUSTED = 0xFF8A8A8A;

	private HulkHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = mc.level.getGameTime();
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20; // the ability row (Thor's), where Phase 2's boxes go
		int labelY = y0 + BOX + 4;
		int barY = labelY + mc.font.lineHeight + 2;

		// who is in charge right now
		Component title = s.hulk
				? Component.translatable("hud.projecthero.hulk.hulk").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
				: Component.translatable("hud.projecthero.hulk.banner").withStyle(ChatFormatting.DARK_GREEN);
		g.drawString(mc.font, title, x0, labelY - 11, 0xFF55FF55, true);

		float ratio = Math.max(0.0f, Math.min(1.0f, s.rage / HulkConfig.RAGE_MAX));
		boolean exhausted = s.exhaustedUntil > now;
		Component label = exhausted
				? Component.translatable("hud.projecthero.hulk.exhausted", (int) Math.ceil((s.exhaustedUntil - now) / 20.0))
				: Component.translatable("hud.projecthero.hulk.rage", (int) Math.floor(s.rage));
		g.drawString(mc.font, label, x0, labelY, exhausted ? COLOR_EXHAUSTED : COLOR_LABEL, false);

		int fill = s.hulk ? COLOR_RAGE_HULK : (s.rage >= HulkConfig.MANUAL_TRANSFORM_RAGE ? COLOR_RAGE_READY : COLOR_RAGE);
		// a Hulk about to shrink back pulses
		if (s.hulk && s.rage < 15.0f && (now / 5L) % 2L == 0L) {
			fill = 0xFF4F7F2A;
		}
		g.fill(x0, barY, x0 + totalW, barY + HAIRLINE, COLOR_BG);
		g.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + HAIRLINE, exhausted ? COLOR_EXHAUSTED : fill);
		if (!s.hulk) {
			int mark = x0 + Math.round(totalW * (HulkConfig.MANUAL_TRANSFORM_RAGE / HulkConfig.RAGE_MAX));
			g.fill(mark, barY - 1, mark + 1, barY + HAIRLINE + 1, 0xFFFFFFFF);
		}
		renderAbilities(g, mc, player, s, x0, y0, totalW);
	}

	/**
	 * v0.13.12 (Phase 2): the ability row -- Thunderclap (R), Ground Smash (G) and Super Leap (X) with their cooldowns,
	 * and Sprint Smash (C) showing whether it is on. Right-aligned in the row above the rage bar, greyed out while he is
	 * Banner (the abilities only work as the Hulk). A Hairline charge bar sits above it while Super Leap is held.
	 */
	private static void renderAbilities(GuiGraphics g, Minecraft mc, Player player, HulkState s, int x0, int y0, int totalW) {
		com.projecthero.mod.hero.AbilitySlot[] slots = { com.projecthero.mod.hero.AbilitySlot.SLOT_1,
				com.projecthero.mod.hero.AbilitySlot.SLOT_2, com.projecthero.mod.hero.AbilitySlot.SLOT_3,
				com.projecthero.mod.hero.AbilitySlot.SLOT_6 };
		String[] names = { "thunderclap", "ground_smash", "super_leap", "sprint_smash" };
		int rowW = slots.length * BOX + (slots.length - 1) * GAP;
		int rx = x0 + totalW - rowW;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		for (int i = 0; i < slots.length; i++) {
			int x = rx + i * (BOX + GAP);
			boolean sprint = i == 3;
			boolean on = sprint && s.sprintSmash && com.projecthero.mod.hulk.HulkConfig.world().sprintSmashEnabled;
			g.fill(x, y0, x + BOX, y0 + BOX, 0xC0101A10);
			g.renderOutline(x, y0, BOX, BOX, on ? 0xFF7CFF4A : 0xFF2E6A3E);
			g.drawString(mc.font, String.valueOf(slots[i].defaultKey()), x + 2, y0 + 2, s.hulk ? 0xFFD8F0DC : 0xFF6A7A6A, false);
			if (!s.hulk) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x90000000);
			} else if (!sprint) {
				String id = com.projecthero.mod.hulk.HulkAbilityManager.abilityIdOf(slots[i]);
				int cd = com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(player, id);
				int max = com.projecthero.mod.hulk.HulkAbilityManager.maxCooldown(id);
				if (cd > 0 && max > 0) {
					int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
					g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, 0xB0000000);
					g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + 6, 0xFFFFFFFF);
				}
			} else {
				g.drawCenteredString(mc.font, on ? "ON" : "OFF", x + BOX / 2 + 1, y0 + 10, on ? 0xFF7CFF4A : 0xFF8A8A8A);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.hulk.ability." + names[i]);
				g.drawString(mc.font, name, rx - 8 - mc.font.width(name), y0 + i * 10 - 30, 0xFFCFE8CF, true);
			}
		}
		float charge = com.projecthero.mod.hulk.HulkAbilities.leapCharge(player);
		if (charge > 0.0f) {
			int cy = y0 - 6;
			g.fill(rx, cy, rx + rowW, cy + HAIRLINE, COLOR_BG);
			g.fill(rx, cy, rx + Math.round(rowW * charge), cy + HAIRLINE, charge >= 1.0f ? 0xFFFFFFFF : COLOR_RAGE_READY);
		}
	}
}
