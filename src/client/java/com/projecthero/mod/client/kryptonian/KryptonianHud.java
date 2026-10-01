package com.projecthero.mod.client.kryptonian;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilityManager;
import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * v0.14.8: the Kryptonian HUD (bottom-right), in the black-and-gray "mono" style of the reworked powers
 * ({@code AbilityHud} with {@code AbilityHudExtras.mono}):
 * <pre>
 *   Kryptonian
 *   _______________________   Hairline bars (3 px, no border, no text), bottom-up: Solar Energy (out of 100; duller
 *   _______________________   gold while it waits 5 s to refill), then -- only while they run -- the Solar Flare
 *                             charge and the burn-out timer
 *   [R] [G] [Z] [X] [C] [V]   the six keys (v0.14.16: + C); each box shows the longer cooldown of its plain and Shift
 *                             move, and lights up while Heat Vision / Freeze Breath / X-Ray is on
 * </pre>
 * No H / N boxes: H is the power wheel and N does nothing for him. Hold Left-Alt for the move names.
 */
public final class KryptonianHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int BAR_STEP = 5;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFC8C8C8;
	private static final int KEY = 0xFFB4B4B4;
	private static final int KEY_DIM = 0xFF5A5A5A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;

	private static final int SOLAR = 0xFFFFC83C;
	/** v0.14.16: the bar is not refilling yet (drained within the last 5 s): a duller gold. */
	private static final int SOLAR_PAUSED = 0xFFB88A2A;
	private static final int SOLAR_KRYPTONITE = 0xFF5CFF4A;
	private static final int FLARE = 0xFFFFF2C0;
	private static final int BURNT_OUT = 0xFF7A7A7A;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5) -- v0.14.16 added C. */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private record Bar(float ratio, int color) {
	}

	private KryptonianHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Kryptonian.hasPower(mc.player)) {
			return;
		}
		KryptonianState s = mc.player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		if (s == null) {
			return;
		}
		// a selected mutation owns the slots (and draws its own HUD) -- nothing to show for the Kryptonian keys then
		var exp = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean weak = s.weakened;
		boolean burnt = s.depoweredUntil > now;
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		// ---- Hairline bars above the keys, bottom-up
		List<Bar> bars = new ArrayList<>();
		// v0.14.16: out of 100; a duller gold while it is not refilling (drained in the last 5 s)
		boolean paused = s.solar < KryptonianConfig.SOLAR_MAX && Kryptonian.solarRegenPaused(mc.player);
		bars.add(new Bar(s.solar / KryptonianConfig.SOLAR_MAX, weak ? SOLAR_KRYPTONITE : paused ? SOLAR_PAUSED : SOLAR));
		if (s.flareChargeStart > 0L) {
			bars.add(new Bar((now - s.flareChargeStart) / (float) KryptonianConfig.FLARE_CHARGE_TICKS, FLARE));
		}
		if (burnt) {
			bars.add(new Bar((s.depoweredUntil - now) / (float) KryptonianConfig.FLARE_DEPOWER_TICKS, BURNT_OUT));
		}
		int by = y0 - 2 - BAR_H;
		for (Bar b : bars) {
			float r = Math.max(0f, Math.min(1f, b.ratio()));
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, b.color());
			by -= BAR_STEP;
		}

		// ---- the name
		Component name = Component.translatable("hud.projecthero.kryptonian.title");
		g.drawString(mc.font, name, x0, by - 7, weak ? SOLAR_KRYPTONITE : burnt ? BURNT_OUT : NAME, false);

		// ---- the five keys
		boolean expanded = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = KryptonianAbilityManager.idsOf(slot);
			int cd = 0;
			int max = 1;
			for (String id : ids) {
				int c = Kryptonian.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, KryptonianAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_2 && s.heatVision)
					|| (slot == AbilitySlot.SLOT_4 && (s.breathing || s.flareChargeStart > 0L))
					|| (slot == AbilitySlot.SLOT_5 && s.xray);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, weak || burnt ? KEY_DIM : KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				String secs = String.valueOf((cd + 19) / 20);
				g.drawCenteredString(mc.font, secs, x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded && ids.length == 2) {
				Component line = Component.translatable("projecthero.kryptonian.ability." + ids[0]).append(" / ")
						.append(Component.translatable("projecthero.kryptonian.ability." + ids[1]));
				g.drawString(mc.font, line, x0 - 8 - mc.font.width(line), y0 - 54 + i * 10, 0xFFCFCFCF, true);
			}
		}
	}

	/** The key actually bound to the slot (tracks rebinds), at most two characters. */
	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
