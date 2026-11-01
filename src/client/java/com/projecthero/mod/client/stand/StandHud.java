package com.projecthero.mod.client.stand;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.stand.StandAbilityManager;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandMove;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * v0.17.2: the Stand HUD (bottom-right), in the black-and-gray "mono" style of the reworked powers:
 * <pre>
 *   Star Platinum                (clock) 1s
 *   _______________________   Hairline bar (3 px, no border, no text): Stamina (violet)
 *   [R] [G] [Z] [X] [C] [V]   the six keys; each box shows the longer cooldown of its plain and Shift move and lights
 *                             up while that key's move runs (rush, grab, leap, charge, guard / sight, inhale)
 * </pre>
 * The little clock right of the title is the time stop: its length at the current mastery, or its cooldown in seconds
 * (red while it can't be used). No H / N boxes: H summons, N poses. Hold Left-Alt for the move names.
 */
public final class StandHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFB07CFF;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;
	private static final int STAMINA = 0xFF9A5CFF;
	private static final int READY = 0xFFD8C2FF;
	private static final int NOT_READY = 0xFFC83C3C;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private StandHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Stands.hasPower(mc.player)) {
			return;
		}
		StandState s = Stands.peek(mc.player);
		StandType type = Stands.type(mc.player);
		if (s == null || type == null) {
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

		int by = y0 - 2 - BAR_H;
		float r = Math.max(0f, Math.min(1f, s.stamina / StandConfig.STAMINA_MAX));
		g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
		g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, STAMINA);
		// the time stop's minimum Stamina, a tick on the bar
		int mark = x0 + Math.round(totalW * StandConfig.TIME_STOP_MIN_STAMINA / StandConfig.STAMINA_MAX);
		g.fill(mark, by - 1, mark + 1, by + BAR_H + 1, 0xFFEFEFEF);
		int titleY = by - 5 - 7;
		g.drawString(mc.font, Component.translatable(type.nameKey()), x0, titleY, NAME, false);

		// the time stop clock
		int cd = Stands.cooldownRemaining(mc.player, StandMove.THE_WORLD);
		boolean canStop = cd <= 0 && s.stamina >= StandConfig.TIME_STOP_MIN_STAMINA;
		String label = com.projecthero.mod.stand.TimeStop.isCaster(mc.player) || TimeStopClient.active() && TimeStopClient.casterId() == mc.player.getId()
				? "!" : cd > 0 ? ((cd + 19) / 20) + "s" : (StandConfig.timeStopTicks(s.timeStopUses) / 20) + "s";
		int lw = mc.font.width(label);
		int cx = x0 + totalW - lw - 11;
		drawClock(g, cx, titleY, canStop ? READY : NOT_READY, now);
		g.drawString(mc.font, label, x0 + totalW - lw, titleY, canStop ? READY : NOT_READY, false);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = StandAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.stand.move." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.stand.move." + ids[1]) : null));
				}
			}
			MoveListOverlay.draw(g, x0 + totalW, titleY, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = StandAbilityManager.idsOf(slot);
			int c = 0;
			int max = 1;
			for (String id : ids) {
				int rem = Stands.cooldownRemaining(mc.player, id);
				if (rem > c) {
					c = rem;
					max = Math.max(1, StandAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_1 && s.rushing)
					|| (slot == AbilitySlot.SLOT_2 && (s.grabbedEntity != -1 || s.grabbedBlock != 0))
					|| (slot == AbilitySlot.SLOT_3 && (s.leapRebounds >= 0 || s.dashUntil > now))
					|| (slot == AbilitySlot.SLOT_4 && s.chargeStart != 0L)
					|| (slot == AbilitySlot.SLOT_5 && (s.guarding || s.sightUntil > now))
					|| (slot == AbilitySlot.SLOT_6 && s.inhaleUntil > now);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (c > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, c / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((c + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}
	}

	/** A tiny 9x9 clock face with its two hands. */
	private static void drawClock(GuiGraphics g, int x, int y, int colour, long now) {
		g.fill(x + 2, y, x + 7, y + 1, colour);
		g.fill(x + 2, y + 8, x + 7, y + 9, colour);
		g.fill(x, y + 2, x + 1, y + 7, colour);
		g.fill(x + 8, y + 2, x + 9, y + 7, colour);
		g.fill(x + 1, y + 1, x + 2, y + 2, colour);
		g.fill(x + 7, y + 1, x + 8, y + 2, colour);
		g.fill(x + 1, y + 7, x + 2, y + 8, colour);
		g.fill(x + 7, y + 7, x + 8, y + 8, colour);
		g.fill(x + 4, y + 2, x + 5, y + 5, colour);
		g.fill(x + 5, y + 4, x + 7, y + 5, colour);
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
