package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.spider.SpiderAbilities;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities;
import com.projecthero.mod.symbiote.SymbioteHostType;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitals;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Symbiote HUD: drawn only while the black suit is actually worn ({@link SymbioteState#active}),
 * built to match {@link MaxSteelHud} / {@link SpiderHud}'s visual language (the same boxed keybind
 * row with cooldown shading) rather than inventing a third look.
 *
 * <p>Which row it draws depends on {@link SymbioteHostType#of}:
 * <ul>
 *   <li><b>Normal host</b> -- the six {@code SymbioteAbilityManager} abilities (Tendril Grab, Tendril
 *       Strike, Symbiote Leap, Symbiote Slam, Symbiote Shield, Frenzy) in the same bottom-right corner
 *       every other power's row uses, with cooldowns read straight off the synced
 *       {@link SymbioteState#abilityCooldowns}, a SHIELD / FRENZY active badge, and (while it isn't
 *       sitting full and idle) the Symbiote Shield guard bar -- same visual language as Super
 *       Durability's guard bar, 9 seconds of hold time.</li>
 *   <li><b>Black Suit Spider-Man</b> -- {@link SpiderHud} already owns that corner for the six web
 *       abilities, so this only adds a small three-box row of the Symbiote-flavoured sneak-modified
 *       extras ({@link SymbioteBlackSuitAbilities}) just to its left, each box carrying the real Spider
 *       key it piggybacks on (Sneak + that key), reading their cooldowns off
 *       {@link SpiderMan#abilityCooldownRemaining} (the same synced map Spider-Man's own abilities use,
 *       since v0.9.15 routes these three through it instead of an invisible-to-the-client local map).
 *       Hold Left Alt to reveal each extra's full name above the row -- same convention every other
 *       power's ability row uses.</li>
 * </ul>
 */
public final class SymbioteHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 4;

	private static final int COLOR_BOX_BG = 0xC00A0A0C;
	private static final int COLOR_BORDER = 0xFF2A2A32;
	private static final int COLOR_BORDER_ACTIVE = 0xFF8A5FE0;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFCFC8E8;
	private static final int COLOR_LABEL = 0xFFB090F0;

	/** Slot order 1..6 -> the Normal host ability id shown in that box (matches {@code AbilitySlot}). */
	private static final String[] NORMAL_SLOT_ABILITIES = {
			"tendril_strike", "spike_shot", "leap", "barrage", "blade", "spikes"
	};

	/** The three Black Suit bonus abilities, left to right, with the real Spider-Man key each rides on. */
	private static final String[] BLACK_SUIT_ABILITIES = {
			SymbioteBlackSuitAbilities.TENDRIL_STRIKE, SymbioteBlackSuitAbilities.CRUSH,
			SymbioteBlackSuitAbilities.SLAM_ENHANCED
	};
	private static final String[] BLACK_SUIT_KEYED_ON = {
			SpiderAbilities.WEB_YANK, SpiderAbilities.WEB_SHOT, SpiderAbilities.WALL_CRAWL
	};

	private SymbioteHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		if (s == null || !s.hasSymbiote) {
			return;
		}

		// A fresh wild bond that has not settled yet: show the takeover progress, nothing else.
		SymbioteVitals vitals = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, new SymbioteVitals());
		long gameNow = mc.level != null ? mc.level.getGameTime() : 0L;
		if (vitals.bondingUntil > gameNow) {
			float frac = 1.0f - Math.min(1.0f,
					(vitals.bondingUntil - gameNow) / (float) com.projecthero.mod.symbiote.SymbioteVitalsManager.BONDING_TICKS);
			int w = 150;
			int x = g.guiWidth() / 2 - w / 2;
			int y = g.guiHeight() / 2 + 30;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.symbiote.bonding")
					.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), g.guiWidth() / 2, y - 12, 0xFFB090F0);
			g.fill(x - 1, y - 1, x + w + 1, y + 5, COLOR_BORDER);
			g.fill(x, y, x + w, y + 4, 0xAA100018);
			g.fill(x, y, x + Math.round(w * frac), y + 4, 0xFF8A5FE0);
			return;
		}

		boolean spiderMan = SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
		if (spiderMan && !s.active) {
			return;
		}
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		if (spiderMan) {
			renderBlackSuitRow(g, mc, player, now, expanded);
		} else {
			SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, new SymbioteVitals());
			renderNormalHostRow(g, mc, s, v, now, expanded);
		}
	}

	private static void renderNormalHostRow(GuiGraphics g, Minecraft mc, SymbioteState s, SymbioteVitals v,
			long now, boolean expanded) {
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 32;

		g.drawString(mc.font, Component.translatable("entity.projecthero.symbiote")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), x0, y0 - 10, COLOR_LABEL);

		boolean shield = s.shieldHeld;
		boolean charging = s.onslaughtChargeStart >= 0;
		float chargeFrac = charging ? Math.min(1.0f, (now - s.onslaughtChargeStart) / 60.0f) : 0.0f;

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			boolean active = (i == 3 && charging) || (i == 4 && v.bladeActive) || (i == 5 && v.thornsMode);

			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? COLOR_BORDER_ACTIVE : COLOR_BORDER);
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, COLOR_KEY, false);

			if (i == 3 && charging) {
				int fill = Math.round((BOX - 2) * chargeFrac);
				g.fill(x + 1, y0 + BOX - 1 - fill, x + BOX - 1, y0 + BOX - 1, 0xCC8A5FE0);
			}

			int cd = (int) Math.max(0L, s.abilityCooldowns.get(i) - now);
			if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cd / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (v.broken) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0xB0400000);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.symbiote.ability." + NORMAL_SLOT_ABILITIES[i]);
				int tw = mc.font.width(name);
				g.drawString(mc.font, name, x0 - 8 - tw, y0 + i * 10 - 52, 0xFFD8C8F0);
			}
		}

		// Symbiote health bar -- always shown while bonded; it is the headline mechanic.
		int hpY = y0 + BOX + 3;
		float hpRatio = Math.max(0.0f, Math.min(1.0f, v.hp / com.projecthero.mod.symbiote.SymbioteVitalsManager.MAX_HP));
		g.fill(x0 - 1, hpY - 1, x0 + totalW + 1, hpY + BAR_H + 1, COLOR_BORDER);
		g.fill(x0, hpY, x0 + totalW, hpY + BAR_H, 0xAA0A0A0C);
		g.fill(x0, hpY, x0 + Math.round(totalW * hpRatio), hpY + BAR_H,
				v.broken ? 0xFFC03030 : (hpRatio < 0.35f ? 0xFFE0A030 : 0xFF8A5FE0));
		g.drawString(mc.font, Component.translatable(v.broken
						? "hud.projecthero.symbiote.hp_broken" : "hud.projecthero.symbiote.hp")
				.withStyle(v.broken ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE), x0, hpY + BAR_H + 1, 0xFFB090F0, false);

		// Symbiote Blade charge bar -- only while out or refilling.
		if (v.bladeActive || v.bladeCharge < com.projecthero.mod.symbiote.SymbioteVitalsManager.BLADE_MAX - 0.5f) {
			int bY = hpY + BAR_H + 10;
			float r = Math.max(0.0f, Math.min(1.0f, v.bladeCharge / com.projecthero.mod.symbiote.SymbioteVitalsManager.BLADE_MAX));
			g.fill(x0 - 1, bY - 1, x0 + totalW + 1, bY + BAR_H + 1, COLOR_BORDER);
			g.fill(x0, bY, x0 + totalW, bY + BAR_H, 0xAA0A0A0C);
			g.fill(x0, bY, x0 + Math.round(totalW * r), bY + BAR_H, v.bladeActive ? 0xFF6A2FB0 : 0xFF4A3070);
		}

		// Symbiote Shield's guard bar, shown only while it is actually in play.
		if (s.shieldHeld || s.shieldGuard < SymbioteState.SHIELD_GUARD_MAX - 0.5f) {
			int barY = hpY + BAR_H + (v.bladeActive || v.bladeCharge < com.projecthero.mod.symbiote.SymbioteVitalsManager.BLADE_MAX - 0.5f ? 20 : 10);
			float ratio = Math.max(0.0f, Math.min(1.0f, s.shieldGuard / SymbioteState.SHIELD_GUARD_MAX));
			g.fill(x0 - 1, barY - 1, x0 + totalW + 1, barY + BAR_H + 1, COLOR_BORDER);
			g.fill(x0, barY, x0 + totalW, barY + BAR_H, 0xAA0A0A0C);
			g.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + BAR_H, s.shieldHeld ? 0xFF8A5FE0 : 0xFF5A4080);
		}
	}

	private static void renderBlackSuitRow(GuiGraphics g, Minecraft mc, Player player, long now, boolean expanded) {
		int rowW = BLACK_SUIT_ABILITIES.length * BOX + (BLACK_SUIT_ABILITIES.length - 1) * GAP;
		// Sits just to the left of SpiderHud's own six-box row, same bottom-right corner, same y.
		int spiderRowW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - spiderRowW - GAP * 4 - rowW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20;

		g.drawString(mc.font, Component.translatable("hud.projecthero.symbiote.black_suit")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), x0, y0 - 10, COLOR_LABEL);

		for (int i = 0; i < BLACK_SUIT_ABILITIES.length; i++) {
			String ability = BLACK_SUIT_ABILITIES[i];
			int x = x0 + i * (BOX + GAP);

			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			// These three all ride sneak + an existing Spider-Man key -- show that key so the box isn't
			// a mystery, matching every other power's boxed-keybind convention (the sneak modifier is
			// spelled out in the expanded name above, and in the guide).
			g.drawString(mc.font, String.valueOf(SpiderAbilities.slotKeyOf(BLACK_SUIT_KEYED_ON[i])),
					x + 2, y0 + 2, COLOR_KEY, false);

			int cd = SpiderMan.abilityCooldownRemaining(player, ability);
			if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cd / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			} else {
				g.drawCenteredString(mc.font, "✓", x + BOX / 2, y0 + BOX / 2 - 4, 0xFF8A5FE0);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.symbiote.ability." + ability);
				int tw = mc.font.width(name);
				g.drawString(mc.font, name, x0 - 8 - tw, y0 + i * 10 - 30, 0xFFD8C8F0);
			}
		}
	}
}
