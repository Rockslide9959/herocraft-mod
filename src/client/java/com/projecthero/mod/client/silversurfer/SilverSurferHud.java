package com.projecthero.mod.client.silversurfer;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.SilverSurferAbilityManager;
import com.projecthero.mod.silversurfer.SilverSurferConfig;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * v0.17.1: the Silver Surfer HUD (bottom-right), framed in the mono style of the reworked powers:
 * <pre>
 *   Silver Surfer                SURFING
 *   _______________________   Hairline bars: the Power Cosmic (silver-blue), the beam / barrier / Light-Speed while they run
 *   [R] [G] [Z] [X] [C] [V]   the six keys: the box fills with the plain move's cooldown, the strip under it with the Shift move's
 *   ===  ===  ===  ===  ===  ===
 * </pre>
 * No H / N boxes. Hold Left-Alt for the move names.
 */
public final class SilverSurferHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int BAR_STEP = 5;
	private static final int SHIFT_H = 2;

	private static final int FRAME_BG = 0x90050608;
	private static final int FRAME = 0xFF5A6070;
	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFD8E6FF;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE8EEF8;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;

	private static final int COSMIC = 0xFFB8D4FF;
	private static final int BEAM = 0xFFF2F8FF;
	private static final int BARRIER = 0xFF9FC0FF;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHIFT_READY = 0xFF6E86B0;
	private static final int SHIFT_CD = 0xFF2A2F38;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private record Bar(float ratio, int color) {
	}

	private SilverSurferHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !SilverSurfer.hasPower(mc.player)) {
			return;
		}
		SilverSurferState s = SilverSurfer.peek(mc.player);
		if (s == null) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - 3 - totalW;
		int y0 = g.guiHeight() - MARGIN - 3 - SHIFT_H - 1 - BOX;

		List<Bar> bars = new ArrayList<>();
		bars.add(new Bar(s.cosmic / SilverSurferConfig.COSMIC_MAX, COSMIC));
		if (s.beamStart != 0L && now - s.beamStart >= SilverSurferConfig.BEAM_HOLD_TICKS) {
			bars.add(new Bar(1f - (now - s.beamStart - SilverSurferConfig.BEAM_HOLD_TICKS) / (float) SilverSurferConfig.BEAM_MAX_TICKS, BEAM));
		}
		if (s.barrierUntil > now && s.barrierHp > 0f) {
			bars.add(new Bar(s.barrierHp / SilverSurferConfig.BARRIER_HP, BARRIER));
		}
		if (s.lightSpeedUntil > now) {
			bars.add(new Bar((s.lightSpeedUntil - now) / (float) SilverSurferConfig.LIGHT_SPEED_TICKS, LIGHT));
		}
		Component status = s.lightSpeedUntil > now ? Component.translatable("hud.projecthero.silver_surfer.light_speed")
				: s.surfing ? Component.translatable("hud.projecthero.silver_surfer.surfing") : null;
		Component title = Component.translatable("hud.projecthero.silver_surfer.title");
		// the status sits right of the title when both fit, on its own line above otherwise
		boolean ownLine = status != null && mc.font.width(title) + 6 + mc.font.width(status) > totalW;
		int titleY = y0 - 2 - BAR_H - BAR_STEP * (bars.size() - 1) - 9;
		int topY = ownLine ? titleY - 10 : titleY;
		// the frame
		int fx0 = x0 - 3;
		int fy0 = topY - 3;
		int fx1 = x0 + totalW + 3;
		int fy1 = y0 + BOX + 1 + SHIFT_H + 3;
		g.fill(fx0, fy0, fx1, fy1, FRAME_BG);
		g.renderOutline(fx0, fy0, fx1 - fx0, fy1 - fy0, FRAME);

		int by = y0 - 2 - BAR_H;
		for (Bar b : bars) {
			float r = Math.max(0f, Math.min(1f, b.ratio()));
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, b.color());
			by -= BAR_STEP;
		}
		g.drawString(mc.font, title, x0, titleY, NAME, false);
		if (status != null) {
			g.drawString(mc.font, status, x0 + totalW - mc.font.width(status), topY, s.lightSpeedUntil > now ? LIGHT : COSMIC, false);
		}

		// Left-Alt: the shared move list, stacked above the frame
		if (com.projecthero.mod.client.gui.MoveListOverlay.held()) {
			List<com.projecthero.mod.client.gui.MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SilverSurferAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of(com.projecthero.mod.client.gui.MoveListOverlay.key(slot),
							Component.translatable("projecthero.silver_surfer.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.silver_surfer.ability." + ids[1]) : null));
				}
			}
			com.projecthero.mod.client.gui.MoveListOverlay.draw(g, fx1, fy0 - 2, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = SilverSurferAbilityManager.idsOf(slot);
			int cd = ids.length > 0 ? SilverSurfer.cooldownRemaining(mc.player, ids[0]) : 0;
			int max = ids.length > 0 ? Math.max(1, SilverSurferAbilityManager.maxCooldown(ids[0])) : 1;
			int scd = ids.length > 1 ? SilverSurfer.cooldownRemaining(mc.player, ids[1]) : 0;
			int smax = ids.length > 1 ? Math.max(1, SilverSurferAbilityManager.maxCooldown(ids[1])) : 1;
			boolean active = (slot == AbilitySlot.SLOT_1 && s.beamStart != 0L)
					|| (slot == AbilitySlot.SLOT_2 && s.ramUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && s.lightSpeedUntil > now)
					|| (slot == AbilitySlot.SLOT_4 && (s.barrierUntil > now || s.phaseUntil > now))
					|| (slot == AbilitySlot.SLOT_5 && s.awareness);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			// the Shift move's cooldown strip under the box (full and lit = ready)
			int sy = y0 + BOX + 1;
			g.fill(x, sy, x + BOX, sy + SHIFT_H, SHIFT_CD);
			float ready = scd <= 0 ? 1f : 1f - Math.min(1f, scd / (float) smax);
			g.fill(x, sy, x + Math.round(BOX * ready), sy + SHIFT_H, scd <= 0 ? SHIFT_READY : 0xFF46526A);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
