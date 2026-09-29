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
	private static final int HAIRLINE = 3;

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

		SymbioteHostType type = SymbioteHostType.of(player);
		boolean spiderMan = type == SymbioteHostType.SPIDER_MAN;
		if (type != SymbioteHostType.NORMAL && !s.active) {
			return;
		}
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		if (type == SymbioteHostType.AGENT_VENOM) {
			renderAgentVenomRow(g, mc, player, expanded);
		} else if (spiderMan) {
			renderBlackSuitRow(g, mc, player, now, expanded);
		} else {
			SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, new SymbioteVitals());
			renderNormalHostRow(g, mc, s, v, now, expanded);
		}
	}

	/**
	 * v0.13.19 layout (bottom-right, top to bottom):
	 * <pre>
	 *   [R][G][X][Z][V][C]      the six ability boxes, labelled with the keys actually bound
	 *   Symbiote
	 *   Biomass 85%
	 *   ------------------      Hairline Biomass bar
	 * </pre>
	 * The blade and shield have no time limit any more (they feed on Biomass), so their old charge bars are gone.
	 */
	private static void renderNormalHostRow(GuiGraphics g, Minecraft mc, SymbioteState s, SymbioteVitals v,
			long now, boolean expanded) {
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int barY = g.guiHeight() - MARGIN - HAIRLINE;
		int biomassY = barY - 11;
		int titleY = biomassY - 10;
		int y0 = titleY - 3 - BOX;

		boolean charging = s.onslaughtChargeStart >= 0;
		float chargeFrac = charging ? Math.min(1.0f, (now - s.onslaughtChargeStart) / 60.0f) : 0.0f;

		for (int i = 0; i < 6; i++) {
			int x = x0 + i * (BOX + GAP);
			boolean active = (i == 3 && charging) || (i == 4 && (v.bladeActive || s.shieldHeld))
					|| (i == 5 && (v.thornsMode || s.tendrilGrabHeld));

			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? COLOR_BORDER_ACTIVE : COLOR_BORDER);
			g.drawString(mc.font, boundKey(i), x + 2, y0 + 2, COLOR_KEY, false);

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
			// Shift+G's Spike Fan runs its own cooldown: a thin strip along the bottom of the G box
			if (i == 1 && v.spikeConeReadyAt > now) {
				float left = Math.min(1.0f, (v.spikeConeReadyAt - now) / 200.0f);
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * left), y0 + BOX - 1, 0xFF8A5FE0);
			}
			if (v.broken) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0xB0400000);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.symbiote.ability." + NORMAL_SLOT_ABILITIES[i]);
				int tw = mc.font.width(name);
				g.drawString(mc.font, name, x0 - 8 - tw, y0 + i * 10 - 40, 0xFFD8C8F0);
			}
		}

		g.drawString(mc.font, Component.translatable("entity.projecthero.symbiote")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), x0, titleY, COLOR_LABEL, false);

		float ratio = Math.max(0.0f, Math.min(1.0f, v.hp / com.projecthero.mod.symbiote.SymbioteVitalsManager.MAX_HP));
		int pct = Math.round(ratio * 100.0f);
		Component label = v.broken
				? Component.translatable("hud.projecthero.symbiote.hp_broken").withStyle(ChatFormatting.RED)
				: Component.translatable("hud.projecthero.symbiote.biomass", pct);
		g.drawString(mc.font, label, x0, biomassY, v.broken ? 0xFFFF6060 : COLOR_LABEL, false);

		int fill = v.broken ? 0xFFC03030 : (ratio < 0.35f ? 0xFFE0A030 : 0xFF8A5FE0);
		if (v.bladeActive || s.shieldHeld) {
			// feeding the blade / shield: the bar breathes so you can see it is being spent
			fill = ((now / 6L) % 2L == 0L) ? fill : 0xFFB38AF5;
		}
		g.fill(x0, barY, x0 + totalW, barY + HAIRLINE, 0x80000000);
		g.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + HAIRLINE, fill);
	}

	/** The key actually bound to ability slot {@code i} (0-based), so a rebind shows up on the HUD. */
	private static String boundKey(int i) {
		String k = com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS[i].getTranslatedKeyMessage().getString();
		return k.length() > 2 ? k.substring(0, 2).toUpperCase(java.util.Locale.ROOT) : k.toUpperCase(java.util.Locale.ROOT);
	}

	/**
	 * v0.13.11: Agent Venom's three Symbiote extras (Sneak + X / Z / V), stacked right-aligned just above
	 * the Punisher's own six-box row (clear of its key letters), with the "Agent Venom" label to their left.
	 * Above rather than beside it so the row never runs into the hotbar's hunger bar at large GUI scales.
	 */
	private static void renderAgentVenomRow(GuiGraphics g, Minecraft mc, Player player, boolean expanded) {
		String[] abilities = com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.ABILITIES;
		AbilitySlot[] keys = com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.KEYED_ON;
		int rowW = abilities.length * BOX + (abilities.length - 1) * GAP;
		int x0 = g.guiWidth() - MARGIN - rowW;
		int punisherY = g.guiHeight() - BOX - MARGIN - 26; // PunisherHud's row; its key letters sit 9 px above it
		int y0 = punisherY - 13 - BOX;

		Component title = Component.translatable("hud.projecthero.agent_venom.title").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
		g.drawString(mc.font, title, x0 - 6 - mc.font.width(title), y0 + 6, 0xFFE8E8F0);
		for (int i = 0; i < abilities.length; i++) {
			int x = x0 + i * (BOX + GAP);
			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			g.drawString(mc.font, String.valueOf(keys[i].defaultKey()), x + 2, y0 + 2, COLOR_KEY, false);
			int cd = com.projecthero.mod.punisher.Punisher.cooldownRemaining(player, abilities[i]);
			if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cd / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			} else {
				g.drawCenteredString(mc.font, "✓", x + BOX / 2, y0 + BOX / 2 - 4, 0xFFE8E8F0);
			}
			if (expanded) {
				// Sneak + key: name, listed above the row
				Component name = Component.literal("Sneak+" + keys[i].defaultKey() + "  ")
						.append(Component.translatable("projecthero.agent_venom.ability." + abilities[i]));
				g.drawString(mc.font, name, g.guiWidth() - MARGIN - mc.font.width(name), y0 - 12 - (abilities.length - 1 - i) * 10, 0xFFD8D8E8);
			}
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
