package com.projecthero.mod.client.cyborg;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.cyborg.Cyborg;
import com.projecthero.mod.cyborg.CyborgAbilityManager;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.lwjgl.glfw.GLFW;

/**
 * v0.16.5: the Cyborg HUD (bottom-right), in the mono style of the reworked powers:
 * <pre>
 *   Cyborg
 *   _______________________   Hairline bars: the Power Core (blue), the Sonic Cannon charge and the Defense Matrix
 *   [R] [G] [Z] [X] [C] [V]   (only while they run); the six keys with their cooldowns
 * </pre>
 * While the Scan runs, the creature under the crosshair gets a readout under the crosshair: its name, health and armour.
 * Shown only in combat form. Hold Left-Alt for the move names.
 */
public final class CyborgHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int BAR_STEP = 5;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFF5CC8FF;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;

	private static final int CORE = 0xFF4FB8FF;
	private static final int CHARGE = 0xFFE8F8FF;
	private static final int MATRIX = 0xFF8FD8FF;
	private static final int SCAN = 0xFF5CC8FF;

	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private record Bar(float ratio, int color) {
	}

	private CyborgHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Cyborg.combat(mc.player)) {
			return;
		}
		CyborgState s = mc.player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
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
		bars.add(new Bar(s.core / CyborgConfig.CORE_MAX, CORE));
		if (s.chargeStart != 0L) {
			bars.add(new Bar((now - s.chargeStart) / (float) CyborgConfig.CANNON_CHARGE_TICKS, CHARGE));
		}
		if (s.shieldUntil > now && s.shieldHp > 0f) {
			bars.add(new Bar(s.shieldHp / CyborgConfig.MATRIX_HP, MATRIX));
		}
		int by = y0 - 2 - BAR_H;
		for (Bar b : bars) {
			float r = Math.max(0f, Math.min(1f, b.ratio()));
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, b.color());
			by -= BAR_STEP;
		}
		g.drawString(mc.font, Component.translatable("hud.projecthero.cyborg.title"), x0, by - 7, NAME, false);

		boolean expanded = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = CyborgAbilityManager.idsOf(slot);
			int cd = 0;
			int max = 1;
			for (String id : ids) {
				int c = Cyborg.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, CyborgAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_1 && s.chargeStart != 0L)
					|| (slot == AbilitySlot.SLOT_3 && s.hovering)
					|| (slot == AbilitySlot.SLOT_4 && s.scanUntil > now)
					|| (slot == AbilitySlot.SLOT_5 && s.waypoint.size() == 3)
					|| (slot == AbilitySlot.SLOT_6 && s.shieldUntil > now && s.shieldHp > 0f);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded && ids.length == 2) {
				Component line = Component.translatable("projecthero.cyborg.ability." + ids[0]).append(" / ")
						.append(Component.translatable("projecthero.cyborg.ability." + ids[1]));
				g.drawString(mc.font, line, x0 - 8 - mc.font.width(line), y0 - 54 + i * 10, 0xFFCFCFCF, true);
			}
		}

		// the Scan readout under the crosshair
		if (s.scanUntil > now && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY
				&& ((EntityHitResult) mc.hitResult).getEntity() instanceof LivingEntity t) {
			int cx = g.guiWidth() / 2;
			int cy = g.guiHeight() / 2 + 12;
			Component name = t.getDisplayName();
			String stats = String.format(java.util.Locale.ROOT, "%.0f / %.0f HP   %d armour", t.getHealth(), t.getMaxHealth(), t.getArmorValue());
			int w = Math.max(mc.font.width(name), mc.font.width(stats)) + 8;
			g.fill(cx - w / 2, cy, cx + w / 2, cy + 22, 0xA0060A10);
			g.renderOutline(cx - w / 2, cy, w, 22, SCAN);
			g.drawCenteredString(mc.font, name, cx, cy + 2, SCAN);
			g.drawCenteredString(mc.font, stats, cx, cy + 12, 0xFFE2E2E2);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
