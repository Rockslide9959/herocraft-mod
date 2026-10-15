package com.projecthero.mod.client.ghostrider;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.ghostrider.GhostRider;
import com.projecthero.mod.ghostrider.GhostRiderAbilityManager;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * v0.16.5: the Ghost Rider HUD (bottom-right), in the black-and-gray "mono" style of the reworked powers:
 * <pre>
 *   Ghost Rider
 *   _______________________   Hairline bars (3 px, no border, no text), bottom-up: Hellfire (orange), Vengeance (red)
 *   _______________________
 *   [R] [G] [Z] [X] [C] [V]   the six keys; each box shows the longer cooldown of its plain and Shift move and
 *                             lights up while that key's move runs (blast, spin, bike, stare, rush, shield)
 * </pre>
 * No H / N boxes: H is the change. Hold Left-Alt for the move names. Shown only while transformed (human, the bars
 * would only read "full"). v0.16.5 rework: after a cheated death a short red "bound till dawn" sits right of the title.
 */
public final class GhostRiderHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int BAR_STEP = 5;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFFF9A3C;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;

	private static final int HELLFIRE = 0xFFFF7A1A;
	private static final int VENGEANCE = 0xFFC81E1E;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private record Bar(float ratio, int color) {
	}

	private GhostRiderHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !GhostRider.transformed(mc.player)) {
			return;
		}
		GhostRiderState s = mc.player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s == null) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		List<Bar> bars = new ArrayList<>();
		bars.add(new Bar(s.hellfire / GhostRiderConfig.HELLFIRE_MAX, HELLFIRE));
		bars.add(new Bar(s.vengeance / GhostRiderConfig.VENGEANCE_MAX, VENGEANCE));
		int by = y0 - 2 - BAR_H;
		for (Bar b : bars) {
			float r = Math.max(0f, Math.min(1f, b.ratio()));
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, b.color());
			by -= BAR_STEP;
		}
		g.drawString(mc.font, Component.translatable("hud.projecthero.ghost_rider.title"), x0, by - 7, NAME, false);
		if (s.dayLockUntil > 0L) {
			Component bound = Component.translatable("hud.projecthero.ghost_rider.bound");
			g.drawString(mc.font, bound, x0 + totalW - mc.font.width(bound), by - 7, VENGEANCE, false);
		}

		boolean expanded = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = GhostRiderAbilityManager.idsOf(slot);
			int cd = 0;
			int max = 1;
			for (String id : ids) {
				int c = GhostRider.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, GhostRiderAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_1 && s.blasting)
					|| (slot == AbilitySlot.SLOT_2 && s.spinUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && mc.player.getVehicle() instanceof HellCycleEntity)
					|| (slot == AbilitySlot.SLOT_4 && s.stareTarget != -1)
					|| (slot == AbilitySlot.SLOT_5 && s.rushing)
					|| (slot == AbilitySlot.SLOT_6 && s.shieldUntil > now);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded && ids.length == 2) {
				Component line = Component.translatable("projecthero.ghost_rider.ability." + ids[0]).append(" / ")
						.append(Component.translatable("projecthero.ghost_rider.ability." + ids[1]));
				g.drawString(mc.font, line, x0 - 8 - mc.font.width(line), y0 - 54 + i * 10, 0xFFCFCFCF, true);
			}
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
