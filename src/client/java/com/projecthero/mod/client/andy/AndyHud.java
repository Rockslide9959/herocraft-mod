package com.projecthero.mod.client.andy;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyAbilityManager;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3: the Andy HUD (bottom-right), mono style: a little pixel body with each part shaded by its HP (gone parts hollow,
 * regrowing ones filling from the joint), the regeneration speed, Victor's hairline, and the six keys.
 */
public final class AndyHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFE0303A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;
	private static final int VICTOR = 0xFFE0303A;

	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	/** The little body: x, y, w, h (in HUD px) of head, body, right arm, left arm, right leg, left leg -- seen from the front. */
	private static final int[][] FIGURE = { { 4, 0, 8, 8 }, { 4, 8, 8, 12 }, { 0, 8, 4, 12 }, { 12, 8, 4, 12 }, { 4, 20, 4, 12 }, { 8, 20, 4, 12 } };

	private AndyHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		AndyState s = Andy.peek(mc.player);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		float now = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		int by = y0 - 2 - BAR_H;
		boolean victor = s.victorUntil > now;
		if (victor) {
			float r = (s.victorUntil - now) / AndyConfig.VICTOR_TICKS;
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * Math.max(0f, Math.min(1f, r))), by + BAR_H, VICTOR);
			by -= 5;
		}
		// the body, right-aligned above the keys, with the speed beside it
		int fx = x0 + totalW - 16, fy = by - 33;
		for (int i = 0; i < AndyState.PARTS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			if (!s.broken(i)) {
				float hp = s.hp(i) / AndyConfig.PART_HP[i];
				g.fill(px, py, px + f[2], py + f[3], partColour(hp, victor));
			} else {
				float p = s.regrowth(i, now);
				g.renderOutline(px, py, f[2], f[3], 0xFF5A1010);
				if (p > 0f) {
					int h = Math.round(f[3] * p);
					// from the joint outward: legs and arms fill downward, the head upward
					if (i == AndyState.HEAD) {
						g.fill(px, py + f[3] - h, px + f[2], py + f[3], 0xFFFFD8D0);
					} else {
						g.fill(px, py, px + f[2], py + h, 0xFFFFD8D0);
					}
				}
			}
		}
		String[] speeds = { "slow", "normal", "burst" };
		Component speed = Component.translatable("projecthero.andy.speed." + speeds[s.regenSpeed]);
		int textY = fy + 24;
		g.drawString(mc.font, Component.translatable(victor ? "hud.projecthero.andy.victor" : "hud.projecthero.andy.title"), x0, textY - 10,
				victor ? VICTOR : NAME, false);
		g.drawString(mc.font, Component.translatable("hud.projecthero.andy.regen", speed), x0, textY, KEY, false);
		if (s.reformUntil > now) {
			g.drawString(mc.font, Component.translatable("hud.projecthero.andy.reforming"), x0, textY - 20, VICTOR, false);
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = AndyAbilityManager.idsOf(mc.player, slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.andy.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.andy.ability." + ids[1]) : null));
				}
			}
			MoveListOverlay.draw(g, x0 + totalW, fy - 4, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			int cd = 0;
			int max = 1;
			for (String id : AndyAbilityManager.idsOf(mc.player, slot)) {
				int c = Andy.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, AndyAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_3 && (s.flag(AndyState.FLAG_PROPELLING) || s.flag(AndyState.FLAG_GLIDING)))
					|| (slot == AbilitySlot.SLOT_6 && (s.flag(AndyState.FLAG_DEAD_LINE) || s.flag(AndyState.FLAG_DEAD_ROAD)))
					|| (slot == AbilitySlot.SLOT_5 && victor);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}
	}

	/** Whole: pale; hurt: toward red. */
	private static int partColour(float hp, boolean victor) {
		hp = Math.max(0f, Math.min(1f, hp));
		int r = 0xE6, gb = (int) (0x50 + (0xD8 - 0x50) * hp);
		if (victor) {
			gb = Math.min(gb, 0x90);
		}
		return 0xFF000000 | (r << 16) | (gb << 8) | gb;
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
