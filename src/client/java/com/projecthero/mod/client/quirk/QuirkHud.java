package com.projecthero.mod.client.quirk;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.16.7: the Quirk HUD (bottom-right), in the black-and-gray "mono" style of the reworked powers (like
 * {@code MiraculousHud}):
 * <pre>
 *   OVERLOADED 6s                         (while overloaded: the kit's overload line, wrapped to the row's width)
 *   Hellflame                             the Quirk (gray while Erased)
 *   Heat [=========-----] 63              the kit's meter in the Quirk's colour (pulses red while overloaded)
 *   [R] [G] [Z] [X] [C] [V]               the six keys: the plain move's cooldown, or the Shift move's while sneaking
 *                                         (a small bar under the box marks the Shift view); lit while that key's move
 *                                         runs; dimmed while overloaded (except keys that work then) or Erased
 * </pre>
 * Hold Left-Alt for the move names ({@link MoveListOverlay}: plain and Shift per key). Also, for EVERY player (any
 * power): the big red ERASED banner while the local player is Erased ({@link PowerSuppression#isSuppressed}).
 */
public final class QuirkHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 4;

	private static final int BOX_BG = 0xD0080808;
	private static final int BOX_BG_DIM = 0x80080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int KEY_DIM = 0xFF5A5A5A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int COOLDOWN = 0xB0000000;
	private static final int BAR_BG = 0xC0141414;
	private static final int OVERLOAD = 0xFFFF5A4A;
	private static final int ERASED_RED = 0xFFE03030;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5), as on the Miraculous HUD. */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private QuirkHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		boolean erased = PowerSuppression.isSuppressed(mc.player);
		if (erased) {
			erasedBanner(g, mc);
		}
		Quirk q = Quirks.quirk(mc.player);
		if (q == null) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		QuirkKit kit = QuirkKits.of(q);
		boolean overloaded = Quirks.overloaded(mc.player);
		boolean shift = forceShift || mc.player.isShiftKeyDown();
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		// the meter: label, bar, value
		float meter = Mth.clamp(Quirks.clientMeter(mc.player), 0f, 100f);
		int by = y0 - 4 - BAR_H;
		Component label = Component.translatable("projecthero.quirk.meter." + q.meterId);
		int labelW = mc.font.width(label);
		String value = String.valueOf(Math.round(meter));
		int valueW = mc.font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, totalW - labelW - 4 - valueW - 3);
		int colour = q.meterColour | 0xFF000000;
		if (overloaded && (now / 5) % 2 == 0) {
			colour = OVERLOAD;
		}
		if (erased) {
			colour = 0xFF505050;
		}
		g.drawString(mc.font, label, x0, by - 2, erased ? HINT : NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + BAR_H + 1, BORDER);
		g.fill(barX, by, barX + barW, by + BAR_H, BAR_BG);
		int fill = Math.round(barW * meter / 100f);
		if (fill > 0) {
			g.fill(barX, by, barX + fill, by + BAR_H, colour);
			// a lighter top edge, like the other mono bars
			g.fill(barX, by, barX + fill, by + 1, 0x60FFFFFF);
		}
		g.drawString(mc.font, value, x0 + totalW - mc.font.width(value), by - 2, erased ? HINT : NAME, false);

		// the Quirk's name
		int ty = by - 13;
		g.drawString(mc.font, Quirks.quirkName(q), x0, ty, erased ? HINT : NAME, false);

		// the overload line
		int hudTop = ty;
		if (overloaded) {
			Component note = Component.translatable("hud.projecthero.quirk.overloaded", (Quirks.overloadLeft(mc.player) + 19) / 20);
			List<FormattedCharSequence> lines = mc.font.split(note, totalW);
			int ny = ty - 2 - lines.size() * 9;
			hudTop = ny;
			for (FormattedCharSequence line : lines) {
				g.drawString(mc.font, line, x0, ny, OVERLOAD, false);
				ny += 9;
			}
		}

		// Left-Alt: the shared move list (plain and Shift per key), stacked above the whole HUD
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = kit.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(keyLabel(slot),
						Component.translatable("projecthero.quirk.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.quirk.ability." + ids[1]) : null));
			}
			if (!rows.isEmpty()) {
				MoveListOverlay.draw(g, x0 + totalW, hudTop, rows);
			}
		}

		// the six keys
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = kit.idsOf(slot);
			String id = ids.length == 0 ? null : ids[Math.min(ids.length - 1, shift ? 1 : 0)];
			int cd = id == null ? 0 : Quirks.cooldownRemaining(mc.player, id);
			int max = id == null ? 1 : Math.max(1, kit.maxCooldown(id));
			boolean usable = !erased && id != null && (!overloaded || kit.usableWhileOverloaded(slot, shift));
			boolean active = !erased && kit.active(mc.player, slot);
			g.fill(x, y0, x + BOX, y0 + BOX, usable ? BOX_BG : BOX_BG_DIM);
			g.renderOutline(x, y0, BOX, BOX, active ? (q.meterColour | 0xFF000000) : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, usable ? KEY : KEY_DIM, false);
			if (shift && ids.length > 1) {
				// the Shift view: a small bar along the bottom of the box
				g.fill(x + 3, y0 + BOX - 3, x + BOX - 3, y0 + BOX - 2, usable ? (q.meterColour | 0xFF000000) : KEY_DIM);
			}
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (erased) {
				// a red slash through every key
				for (int k = 2; k < BOX - 2; k++) {
					g.fill(x + k, y0 + BOX - 1 - k, x + k + 1, y0 + BOX - k, 0xA0E03030);
				}
			}
		}
	}

	/** The big red ERASED banner (any power), a third of the way down the screen, with a thin red edge on the screen. */
	private static void erasedBanner(GuiGraphics g, Minecraft mc) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = mc.level.getGameTime();
		int edge = (now / 10) % 2 == 0 ? 0x50E03030 : 0x38E03030;
		g.fill(0, 0, w, 2, edge);
		g.fill(0, h - 2, w, h, edge);
		g.fill(0, 0, 2, h, edge);
		g.fill(w - 2, 0, w, h, edge);

		Component big = Component.translatable("hud.projecthero.erased");
		float scale = 3f;
		int bigW = (int) (mc.font.width(big) * scale);
		int cy = h / 4;
		var pose = g.pose();
		pose.pushPose();
		pose.translate((w - bigW) / 2f, cy, 0f);
		pose.scale(scale, scale, 1f);
		g.drawString(mc.font, big, 0, 0, ERASED_RED, true);
		pose.popPose();

		int left = PowerSuppression.clientRemaining(mc.player);
		Component sub = left > 60
				? Component.translatable("hud.projecthero.erased.timed", (left + 19) / 20)
				: Component.translatable("hud.projecthero.erased.sub");
		List<FormattedCharSequence> lines = mc.font.split(sub, Math.min(220, w - 32));
		int sy = cy + (int) (9 * scale) + 3;
		for (FormattedCharSequence line : lines) {
			g.drawCenteredString(mc.font, line, w / 2, sy, 0xFFF0B0B0);
			sy += 10;
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
