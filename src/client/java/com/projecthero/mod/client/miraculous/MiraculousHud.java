package com.projecthero.mod.client.miraculous;

import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.lwjgl.glfw.GLFW;

/**
 * v0.16.6: the Miraculous HUD (bottom-right), in the black-and-gray "mono" style of the reworked powers (like
 * {@code GhostRiderHud}):
 * <pre>
 *   Tikki is hungry -- feed her cookies        (when hungry; wrapped to the box row's width)
 *   Ladybug                                    the hero (v0.16.6: no mm:ss -- the spots below are the countdown)
 *   o o o o o                                  five jewel spots in the hero's colour, one blinks out per minute
 *   [R] [G] [Z] [X] [C] [V]                    the six keys (the longer cooldown of each key's plain and Shift move;
 *                                              lit while that key's move runs); dimmed while detransformed
 * </pre>
 * Shown whenever the power is held. Detransformed and fed, a gray hint gives the transformation phrase. Hold Left-Alt
 * for the move names ({@code projecthero.miraculous.ability.<id>}). Also draws the short full-screen flash the holder
 * sees at the transformation's flash and on detransforming.
 */
public final class MiraculousHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;

	private static final int BOX_BG = 0xD0080808;
	private static final int BOX_BG_DIM = 0x80080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int KEY_DIM = 0xFF5A5A5A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int COOLDOWN = 0xB0000000;
	private static final int SPOT_OFF = 0xFF2A2A2A;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private MiraculousHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		MiraculousState s = Miraculous.peek(mc.player);
		Kwami k = Miraculous.kwami(mc.player);
		if (s == null || k == null) {
			return;
		}
		flash(g, mc, s, k, delta.getGameTimeDeltaPartialTick(false));
		var exp = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean on = s.transformed;
		KwamiKit kit = MiraculousKits.of(k);
		int accent = k.color;
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		// the jewel's five spots
		int left = Miraculous.countdownLeft(mc.player);
		int spots = left > 0 ? (left + 1199) / 1200 : 5;
		boolean blink = left > 0 && (left <= 200 ? (now / 4) % 2 == 0 : (left % 1200) < 100 && (now / 6) % 2 == 0);
		int sy = y0 - 7;
		for (int i = 0; i < 5; i++) {
			int sx = x0 + i * 7;
			int c = i < spots ? accent : SPOT_OFF;
			if (i == spots - 1 && blink) {
				c = SPOT_OFF;
			}
			if (!on && c != SPOT_OFF) {
				c = (c & 0x00FFFFFF) | 0x70000000;
			}
			g.fill(sx + 1, sy, sx + 4, sy + 5, c);
			g.fill(sx, sy + 1, sx + 5, sy + 4, c);
		}

		// title (v0.16.6, user: no mm:ss -- the five spots show the countdown)
		int ty = sy - 11;
		g.drawString(mc.font, Component.translatable("projecthero.hero_tier." + k.heroKey), x0, ty, on ? NAME : HINT, false);

		// hungry / the phrase to say (wrapped to the row's width)
		Component note = null;
		int noteColour = HINT;
		int eatLeft = Miraculous.eatingSecondsLeft(mc.player);
		if (eatLeft > 0) {
			note = Component.translatable("hud.projecthero.miraculous.eating", Component.translatable("entity.projecthero.kwami." + k.id), eatLeft);
			noteColour = 0xFF9BE57A;
		} else if (!on && s.kwamiHidden) {
			note = Component.translatable("hud.projecthero.miraculous.hidden", Component.translatable("entity.projecthero.kwami." + k.id));
		} else if (s.hungry) {
			note = Component.translatable("hud.projecthero.miraculous.hungry." + k.id);
			noteColour = 0xFFFFB347;
		} else if (!on) {
			note = Component.translatable("hud.projecthero.miraculous.say", MiraculousPhrases.transformPhrase(k));
		}
		if (note != null) {
			List<FormattedCharSequence> lines = mc.font.split(note, totalW);
			int ny = ty - 2 - lines.size() * 9;
			for (FormattedCharSequence line : lines) {
				g.drawString(mc.font, line, x0, ny, noteColour, false);
				ny += 9;
			}
		}

		// v0.16.6: Left-Alt -> the shared move list, stacked above the whole HUD (title / note included)
		int hudTop = ty;
		if (note != null) {
			hudTop = ty - 2 - mc.font.split(note, totalW).size() * 9;
		}
		if (com.projecthero.mod.client.gui.MoveListOverlay.held()) {
			List<com.projecthero.mod.client.gui.MoveListOverlay.Row> rows = new java.util.ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = kit.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of(keyLabel(slot),
						Component.translatable("projecthero.miraculous.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.miraculous.ability." + ids[1]) : null));
			}
			com.projecthero.mod.client.gui.MoveListOverlay.draw(g, x0 + totalW, hudTop, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = kit.idsOf(slot);
			int cd = 0;
			int max = 1;
			for (String id : ids) {
				int c = Miraculous.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, kit.maxCooldown(id));
				}
			}
			boolean active = on && kit.active(mc.player, slot);
			g.fill(x, y0, x + BOX, y0 + BOX, on ? BOX_BG : BOX_BG_DIM);
			g.renderOutline(x, y0, BOX, BOX, active ? (accent | 0xFF000000) : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, on ? KEY : KEY_DIM, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}
	}

	/** The holder's own screen flash: the hero's colour at the transformation's flash, and on detransforming. */
	private static void flash(GuiGraphics g, Minecraft mc, MiraculousState s, Kwami k, float partial) {
		float age = (float) (mc.level.getGameTime() - s.formChangeAt) + partial;
		float a;
		if (s.transformed && s.animId == MiraculousState.ANIM_TRANSFORM) {
			a = 1f - Math.abs(age - MiraculousConfig.FLASH_TICK - 1f) / 4f;
		} else if (!s.transformed && s.animId == MiraculousState.ANIM_DETRANSFORM) {
			a = 1f - age / 6f;
		} else {
			return;
		}
		if (a <= 0f || s.formChangeAt <= 0L) {
			return;
		}
		int alpha = (int) (Math.min(1f, a) * 150f);
		int rgb = (k.color & 0xFFFFFF);
		// mostly white light, tinted with the hero's colour
		int r = 255 - ((255 - ((rgb >> 16) & 0xFF)) / 3);
		int gg = 255 - ((255 - ((rgb >> 8) & 0xFF)) / 3);
		int b = 255 - ((255 - (rgb & 0xFF)) / 3);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | (r << 16) | (gg << 8) | b);
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
