package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Green Lantern HUD: the six ability keys with cooldown shading in the bottom-right corner (same
 * layout convention as {@link MaxSteelHud}/{@link ThorHud}), a green Ring Charge bar beneath it, the
 * selected construct's name, and -- while up -- the Directional Shield/Protective Dome HP bar. Only
 * drawn while the player is suited (matches every other Hero-Tier power's HUD).
 */
public final class GreenLanternHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;

	private static final int GREEN = 0xFF35F075;
	private static final int GREEN_DIM = 0xFF1A7838;
	private static final int LOW = 0xFFFF5A5A;
	private static final int BOX_BG = 0xC00A2412;
	private static final int BORDER = 0xFF1E661E;
	private static final int BORDER_ACTIVE = 0xFF35F075;
	private static final int COOLDOWN = 0xB0000000;
	private static final int KEY = 0xFFCFF8D4;

	/** Slot 1..6 -> the ability id whose cooldown that box should show (or null). */
	private static final String[] SLOT_COOLDOWNS = {
			"ring_bolt", "construct_fist", null, "directional_shield", "ring_scan", null
	};

	private GreenLanternHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null || !s.hasPower || !s.suited) {
			return;
		}

		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		float charge = Math.max(0f, Math.min(GreenLanternConfig.MAX_RING_CHARGE, s.ringCharge));

		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20;

		g.drawString(mc.font, Component.translatable("projecthero.guide.green_lantern")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), x0, y0 - 10, GREEN);

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, BORDER);
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, KEY, false);

			int cd = SLOT_COOLDOWNS[i] != null ? GreenLantern.cooldownRemaining(player, SLOT_COOLDOWNS[i]) : 0;
			if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cd / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}

		// Ring Charge bar below the row.
		float frac = charge / GreenLanternConfig.MAX_RING_CHARGE;
		boolean low = frac <= GreenLanternConfig.LOW_CHARGE_WARN_10;
		boolean pulseOff = low && (now % 8) < 3;
		int barY = y0 + BOX + 4;
		g.fill(x0 - 1, barY - 1, x0 + totalW + 1, barY + 5, BORDER);
		g.fill(x0, barY, x0 + totalW, barY + 4, 0xAA0A2412);
		if (!pulseOff) {
			g.fill(x0, barY, x0 + Math.round(totalW * frac), barY + 4, low ? LOW : GREEN);
		}
		// mark where the emergency reserve begins
		int reserveMark = x0 + Math.round(totalW * (GreenLanternConfig.EMERGENCY_RESERVE / GreenLanternConfig.MAX_RING_CHARGE));
		g.fill(reserveMark, barY - 1, reserveMark + 1, barY + 5, 0xFFFFDD33);
		g.drawString(mc.font, String.format(java.util.Locale.ROOT, "%d / %d", Math.round(charge),
				Math.round(GreenLanternConfig.MAX_RING_CHARGE)), x0, barY + 5, low ? LOW : 0xFFA8E6B8, false);

		Component construct = Component.translatable(ConstructType.byOrdinal(s.selectedConstruct).translationKey())
				.withStyle(GREEN_DIM_STYLE());
		Component mastery = Component.literal("Mastery " + masteryLabel(s.masteryLevel)).withStyle(ChatFormatting.DARK_GREEN);
		int mw = mc.font.width(mastery);
		g.drawString(mc.font, construct, x0, y0 - 20, GREEN_DIM, false);
		g.drawString(mc.font, mastery, x0 + totalW - mw, y0 - 20, 0xFFFFFFFF, false);

		renderBarrier(g, mc, player, x0, y0, totalW);
	}

	private static void renderBarrier(GuiGraphics g, Minecraft mc, Player player, int x0, int y0, int totalW) {
		float hp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
		if (hp <= 0f) {
			return;
		}
		boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		float max = dome ? GreenLanternConfig.DOME_HP : GreenLanternConfig.SHIELD_HP;
		float frac = Math.max(0f, Math.min(1f, hp / max));
		int h = 5;
		int y = y0 - 40;
		Component label = Component.translatable(dome
				? "hud.projecthero.green_lantern.dome" : "hud.projecthero.green_lantern.shield")
				.withStyle(ChatFormatting.AQUA);
		g.drawCenteredString(mc.font, label, x0 + totalW / 2, y - 10, 0xFFFFFFFF);
		g.fill(x0 - 1, y - 1, x0 + totalW + 1, y + h + 1, BORDER);
		g.fill(x0, y, x0 + totalW, y + h, 0xAA0A2412);
		g.fill(x0, y, x0 + Math.round(totalW * frac), y + h, 0xFF35C8F0);
	}

	private static String masteryLabel(int level) {
		return switch (level) {
			case GreenLanternState.MASTERY_I -> "I";
			case GreenLanternState.MASTERY_II -> "II";
			case GreenLanternState.MASTERY_III -> "III";
			case GreenLanternState.MASTERY_IV -> "IV";
			default -> "Bonded";
		};
	}

	private static ChatFormatting GREEN_DIM_STYLE() {
		return ChatFormatting.DARK_GREEN;
	}
}
