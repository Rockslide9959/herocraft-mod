package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Super Soldier HUD (v0.14.8), in the black-and-gray "mono" style of the reworked powers' {@code AbilityHud}: the
 * power name, Hairline bars (3 px, no border, no text) and five key boxes in the order R G Z X V. Each box shows the
 * longer of its two moves' cooldowns (plain and Shift). No H / N / C boxes. Hold Left-Alt for the move names.
 *
 * <pre>
 *   Super Soldier
 *   ------------------------  Onslaught: blue = ready, gray = recharging, white = running
 *   ------------------------  Tactical Focus (only while it runs)
 *   [R] [G] [Z] [X] [V]
 * </pre>
 */
public final class SuperSoldierHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int ROW_H = 5;

	private static final int MONO_BOX_BG = 0xD0080808;
	private static final int MONO_BORDER = 0xFF4A4A4A;
	private static final int MONO_BORDER_ACTIVE = 0xFFC8C8C8;
	private static final int MONO_KEY = 0xFFB4B4B4;
	private static final int MONO_NAME = 0xFFE2E2E2;
	private static final int MONO_BAR_BG = 0xAA0C0C0C;
	private static final int COLOR_COOLDOWN = 0xB0000000;

	private static final int ULT_READY = 0xFF4A7BD8;
	private static final int ULT_CHARGING = 0xFF6A6A6A;
	private static final int ULT_RUNNING = 0xFFE8E8E8;
	private static final int FOCUS_FILL = 0xFFE0B040;

	/** Boxes in draw order: R G Z X V. */
	private static final AbilitySlot[] ORDER = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_5 };
	private static final String[] NAME_KEYS = { "r", "g", "z", "x", "v" };

	private SuperSoldierHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null || !SuperSoldier.hasPower(mc.player)) {
			return;
		}
		ExperimentalState ex = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (ex != null && !ex.activePower.isEmpty()) {
			return; // a selected mutation owns the keys (and draws its own HUD)
		}
		SuperSoldierState s = mc.player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		int totalW = ORDER.length * BOX + (ORDER.length - 1) * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		// Hairline bars, bottom-up above the keys
		int rows = 0;
		float ultRatio;
		int ultColor;
		if (s.onslaughtUntil > now) {
			ultRatio = (s.onslaughtUntil - now) / (float) SuperSoldierConfig.ONSLAUGHT_DURATION;
			ultColor = ULT_RUNNING;
		} else {
			int cd = SuperSoldier.cooldownRemaining(mc.player, SuperSoldierAbilities.ONSLAUGHT);
			ultRatio = cd <= 0 ? 1.0f : 1.0f - cd / (float) SuperSoldierConfig.ONSLAUGHT_COOLDOWN;
			ultColor = cd <= 0 ? ULT_READY : ULT_CHARGING;
		}
		hairline(g, x0, y0 - 5 - rows++ * ROW_H, totalW, ultRatio, ultColor);
		if (s.focusUntil > now) {
			hairline(g, x0, y0 - 5 - rows++ * ROW_H, totalW, (s.focusUntil - now) / (float) SuperSoldierConfig.FOCUS_DURATION, FOCUS_FILL);
		}

		int nameY = y0 - 5 - (rows - 1) * ROW_H - 11;
		g.drawString(mc.font, Component.translatable("hud.projecthero.super_soldier.title"), x0, nameY, MONO_NAME);

		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		for (int i = 0; i < ORDER.length; i++) {
			AbilitySlot slot = ORDER[i];
			int x = x0 + i * (BOX + GAP);
			int cd = 0;
			for (String id : SuperSoldierAbilityManager.idsOf(slot)) {
				cd = Math.max(cd, SuperSoldier.cooldownRemaining(mc.player, id));
			}
			boolean active = (slot == AbilitySlot.SLOT_4 && s.onslaughtUntil > now) || (slot == AbilitySlot.SLOT_5 && s.focusUntil > now);
			g.fill(x, y0, x + BOX, y0 + BOX, MONO_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? MONO_BORDER_ACTIVE : MONO_BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, MONO_KEY, false);
			if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				String secs = String.valueOf((cd + 19) / 20);
				g.drawCenteredString(mc.font, secs, x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded) {
				Component name = Component.translatable("hud.projecthero.super_soldier.key." + NAME_KEYS[i]);
				g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10 - 52, 0xFFCFCFCF);
			}
		}
	}

	private static void hairline(GuiGraphics g, int x, int y, int w, float ratio, int color) {
		float r = Math.max(0f, Math.min(1f, ratio));
		g.fill(x, y, x + w, y + 3, MONO_BAR_BG);
		g.fill(x, y, x + Math.round(w * r), y + 3, color);
	}

	/** The key actually bound to the slot (tracks rebinds). */
	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.number() - 1].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
