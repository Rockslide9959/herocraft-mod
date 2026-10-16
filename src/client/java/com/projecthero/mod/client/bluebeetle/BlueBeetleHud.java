package com.projecthero.mod.client.bluebeetle;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.BlueBeetleAbilityManager;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * v0.16.5: the Blue Beetle HUD (bottom-right), mono style: hairline bars for Scarab Energy (blue; red while Khaji-Da is
 * unleashed, with its timer above), the Mega Cannon charge, and the Adaptation timer; a small "Adapted: fire" chip; the
 * six keys (lit while their move runs). v0.16.5 rework: no blue screen flash on the bonding (user: no blue glow).
 */
public final class BlueBeetleHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int BAR_STEP = 5;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFF3E8BFF;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;

	private static final int ENERGY = 0xFF2E7BFF;
	private static final int ENERGY_SLOW = 0xFF1E3E80;
	private static final int UNLEASHED = 0xFFFF3A2E;
	private static final int CHARGE = 0xFFCFE6FF;
	private static final int ADAPT = 0xFF7FD4FF;

	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private record Bar(float ratio, int color) {
	}

	private BlueBeetleHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !BlueBeetle.hasPower(mc.player)) {
			return;
		}
		BlueBeetleState s = mc.player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (mc.options.hideGui || !s.suited) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;
		boolean unleashed = s.unleashedUntil > now;

		List<Bar> bars = new ArrayList<>();
		bars.add(new Bar(unleashed ? 1f : s.energy / BlueBeetleConfig.ENERGY_MAX, unleashed ? UNLEASHED : s.slowRegenUntil > now ? ENERGY_SLOW : ENERGY));
		if (unleashed) {
			bars.add(new Bar((s.unleashedUntil - now) / (float) BlueBeetleConfig.UNLEASH_TICKS, UNLEASHED));
		}
		if (s.fx.chargeStart != 0L) {
			bars.add(new Bar((now - s.fx.chargeStart) / (float) BlueBeetleConfig.CANNON_CHARGE_TICKS, CHARGE));
		}
		if (s.adaptUntil > now && !s.adaptType.isEmpty()) {
			bars.add(new Bar((s.adaptUntil - now) / (float) BlueBeetleConfig.ADAPT_TICKS, ADAPT));
		}
		int by = y0 - 2 - BAR_H;
		for (Bar b : bars) {
			float r = Math.max(0f, Math.min(1f, b.ratio()));
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, b.color());
			by -= BAR_STEP;
		}
		g.drawString(mc.font, Component.translatable(unleashed ? "hud.projecthero.blue_beetle.unleashed" : "hud.projecthero.blue_beetle.title"), x0,
				by - 7, unleashed ? UNLEASHED : NAME, false);
		if (s.adaptUntil > now && !s.adaptType.isEmpty()) {
			Component chip = Component.translatable("hud.projecthero.blue_beetle.adapted", s.adaptType.replace('_', ' '));
			g.drawString(mc.font, chip, x0 + totalW - mc.font.width(chip), by - 7, ADAPT, false);
		}

		boolean expanded = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = BlueBeetleAbilityManager.idsOf(slot);
			int cd = 0;
			int max = 1;
			for (String id : ids) {
				int c = BlueBeetle.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, BlueBeetleAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_1 && (s.fx.chargeStart != 0L || s.fx.blasting))
					|| (slot == AbilitySlot.SLOT_2 && s.fx.grabTarget != -1)
					|| (slot == AbilitySlot.SLOT_3 && s.fx.bubbleTarget != -1)
					|| (slot == AbilitySlot.SLOT_4 && s.fx.spikeStart != 0L)
					|| (slot == AbilitySlot.SLOT_5 && (s.fx.shield || s.camo))
					|| (slot == AbilitySlot.SLOT_6 && (s.fx.blade || unleashed));
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded && ids.length == 2) {
				Component line = Component.translatable("projecthero.blue_beetle.ability." + ids[0]).append(" / ")
						.append(Component.translatable("projecthero.blue_beetle.ability." + ids[1]));
				g.drawString(mc.font, line, x0 - 8 - mc.font.width(line), y0 - 54 + i * 10, 0xFFCFCFCF, true);
			}
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
