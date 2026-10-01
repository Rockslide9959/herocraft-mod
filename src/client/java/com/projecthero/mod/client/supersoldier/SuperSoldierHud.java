package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Super Soldier HUD, in the black-and-gray "mono" style of the reworked powers' {@code AbilityHud}: the power name
 * and the key boxes in the order R G Z X V. Each box shows the longer of its two moves' cooldowns (plain and Shift); Z's
 * box is outlined white while Onslaught runs, V's while Tactical Focus runs. v0.14.9: no bars at all (the ultimate and
 * Tactical Focus hairlines are gone), and a sixth box, C (the shield throw), only while he holds a shield -- or while a
 * throw is still cooling down, so the timer does not vanish when the shield leaves his hand. No H / N boxes. Hold
 * Left-Alt for the move names.
 *
 * <pre>
 *   Super Soldier
 *   [R] [G] [Z] [X] [V] ([C])
 * </pre>
 */
public final class SuperSoldierHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;

	private static final int MONO_BOX_BG = 0xD0080808;
	private static final int MONO_BORDER = 0xFF4A4A4A;
	private static final int MONO_BORDER_ACTIVE = 0xFFC8C8C8;
	private static final int MONO_KEY = 0xFFB4B4B4;
	private static final int MONO_NAME = 0xFFE2E2E2;
	private static final int COLOR_COOLDOWN = 0xB0000000;

	/** Boxes in draw order: R G Z X V, then C when it applies. */
	private static final AbilitySlot[] ORDER = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 };
	private static final String[] NAME_KEYS = { "r", "g", "z", "x", "v", "c" };

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
		boolean showC = SuperSoldierAbilities.shieldHand(mc.player) != null
				|| SuperSoldier.cooldownRemaining(mc.player, SuperSoldierAbilities.SHIELD_THROW) > 0;
		int boxes = showC ? ORDER.length : ORDER.length - 1;
		// anchored on the five fixed boxes, so the row does not jump when C appears (C grows out to the left)
		int baseW = 5 * BOX + 4 * GAP;
		int x0 = g.guiWidth() - MARGIN - baseW - (showC ? BOX + GAP : 0);
		int y0 = g.guiHeight() - MARGIN - BOX;

		g.drawString(mc.font, Component.translatable("hud.projecthero.super_soldier.title"), g.guiWidth() - MARGIN - baseW, y0 - 11,
				MONO_NAME);

		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		for (int n = 0; n < boxes; n++) {
			// C first (leftmost), then R G Z X V
			int i = showC ? (n == 0 ? ORDER.length - 1 : n - 1) : n;
			AbilitySlot slot = ORDER[i];
			int x = x0 + n * (BOX + GAP);
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
				g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + n * 10 - 52, 0xFFCFCFCF);
			}
		}
	}

	/** The key actually bound to the slot (tracks rebinds). */
	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.number() - 1].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
