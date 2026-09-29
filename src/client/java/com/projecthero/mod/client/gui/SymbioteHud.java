package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.spider.SpiderAbilities;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities;
import com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities;
import com.projecthero.mod.symbiote.SymbioteHostType;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;

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
 *   <li><b>Normal host</b> -- the six {@code SymbioteAbilityManager} abilities in the same bottom-right corner
 *       every other power's row uses, with cooldowns read straight off the synced
 *       {@link SymbioteState#abilityCooldowns}, and the Hairline Biomass bar under them.</li>
 *   <li><b>Black Suit Spider-Man</b> and <b>Agent Venom</b> -- v0.13.21: one shared "hero host" panel
 *       ({@link #renderHeroHostPanel}), stacked directly above that hero's own six-box row (never beside it, where
 *       it ran into the hunger bar at large GUI scales). A bold title; the three extras' boxes, each with a
 *       little sneak chevron, draining their cooldown like the Hulk / All Might boxes do; the Symbiote revive
 *       timer (or Agent Venom's live Unleashed timer) left of them; and a Hairline underneath that fills as the
 *       revive recharges. Hold Left Alt for each extra's full name.</li>
 * </ul>
 */
public final class SymbioteHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int HAIRLINE = 3;
	/** The hero-host panel is as wide as the hero row it sits on. */
	private static final int PANEL_W = 6 * BOX + 5 * GAP;
	/** Title line + boxes + hairline: the panel's full height. */
	private static final int PANEL_H = 11 + BOX + 3 + HAIRLINE;
	/** Gap between the hero-host panel and whatever it stacks on. */
	private static final int PANEL_GAP = 4;

	private static final int COLOR_BOX_BG = 0xC00A0A0C;
	private static final int COLOR_BORDER = 0xFF2A2A32;
	private static final int COLOR_BORDER_ACTIVE = 0xFF8A5FE0;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFCFC8E8;
	private static final int COLOR_KEY_DIM = 0xFF7E7A8E;
	private static final int COLOR_LABEL = 0xFFB090F0;
	private static final int COLOR_MUTED = 0xFF9A96A8;
	private static final int COLOR_BAR_BG = 0x80000000;

	/** Black Suit Spider-Man's accent (the Symbiote's purple) and Agent Venom's (the suit's bone white). */
	private static final int ACCENT_BLACK_SUIT = 0xFF9A6CF0;
	private static final int ACCENT_AGENT_VENOM = 0xFFE4E6F0;

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
	private static final int[] BLACK_SUIT_COOLDOWNS = {
			SymbioteBlackSuitAbilities.CD_TENDRIL_STRIKE, SymbioteBlackSuitAbilities.CD_CRUSH,
			SymbioteBlackSuitAbilities.CD_SLAM_ENHANCED
	};
	private static final int[] AGENT_VENOM_COOLDOWNS = {
			SymbioteAgentVenomAbilities.CD_SWING, SymbioteAgentVenomAbilities.CD_SNATCH,
			SymbioteAgentVenomAbilities.CD_UNLEASHED
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
					(vitals.bondingUntil - gameNow) / (float) SymbioteVitalsManager.BONDING_TICKS);
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
		if (type != SymbioteHostType.NORMAL && !s.active) {
			return;
		}
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		if (type == SymbioteHostType.AGENT_VENOM) {
			renderAgentVenom(g, mc, player, vitals, now, expanded);
		} else if (type == SymbioteHostType.SPIDER_MAN) {
			renderBlackSuit(g, mc, player, vitals, now, expanded);
		} else {
			renderNormalHostRow(g, mc, s, vitals, now, expanded);
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
	 * v0.13.21: while the bar is spent a white tick marks the {@link SymbioteVitalsManager#RECOVER_FRACTION} it has
	 * to climb back to before the abilities -- and the suit -- come back.
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

		float ratio = Math.max(0.0f, Math.min(1.0f, v.hp / SymbioteVitalsManager.MAX_HP));
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
		g.fill(x0, barY, x0 + totalW, barY + HAIRLINE, COLOR_BAR_BG);
		g.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + HAIRLINE, fill);
		if (v.broken) {
			int mark = x0 + Math.round(totalW * SymbioteVitalsManager.RECOVER_FRACTION);
			g.fill(mark, barY - 1, mark + 1, barY + HAIRLINE + 1, 0xFFFFFFFF);
		}
	}

	/** The key actually bound to ability slot {@code i} (0-based), so a rebind shows up on the HUD. */
	private static String boundKey(int i) {
		String k = com.projecthero.mod.client.ModKeyBindings.ABILITY_SLOTS[i].getTranslatedKeyMessage().getString();
		return k.length() > 2 ? k.substring(0, 2).toUpperCase(java.util.Locale.ROOT) : k.toUpperCase(java.util.Locale.ROOT);
	}

	// ---------------- the hero hosts (v0.13.21) ----------------

	/**
	 * How far {@link SpiderHud} has to lift its Web Blossom charge bar to clear the Black Suit panel -- 0 whenever
	 * the panel is not drawn.
	 */
	public static int blackSuitPanelLift(Player player) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		boolean drawn = s != null && s.hasSymbiote && s.active && SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
		return drawn ? PANEL_H + PANEL_GAP : 0;
	}

	/** Black Suit Spider-Man: the panel stacks right on top of {@link SpiderHud}'s six web boxes. */
	private static void renderBlackSuit(GuiGraphics g, Minecraft mc, Player player, SymbioteVitals v, long now,
			boolean expanded) {
		int spiderBoxesY = g.guiHeight() - MARGIN - BOX - SpiderHud.BOTTOM_STACK;
		int n = BLACK_SUIT_ABILITIES.length;
		char[] keys = new char[n];
		int[] cds = new int[n];
		Component[] names = new Component[n];
		for (int i = 0; i < n; i++) {
			keys[i] = SpiderAbilities.slotKeyOf(BLACK_SUIT_KEYED_ON[i]);
			cds[i] = SpiderMan.abilityCooldownRemaining(player, BLACK_SUIT_ABILITIES[i]);
			names[i] = Component.translatable("projecthero.symbiote.ability." + BLACK_SUIT_ABILITIES[i]);
		}
		renderHeroHostPanel(g, mc, player, v, now, spiderBoxesY - PANEL_GAP,
				Component.translatable("hud.projecthero.symbiote.black_suit"), ACCENT_BLACK_SUIT,
				keys, cds, BLACK_SUIT_COOLDOWNS, new int[n], names, null, expanded);
	}

	/**
	 * Agent Venom: the panel stacks above the Punisher's six-box row, clear of the key letters PunisherHud draws
	 * 9 px above it. The V box glows while Symbiote Unleashed is running, and the "Sneak +" cue becomes its timer.
	 */
	private static void renderAgentVenom(GuiGraphics g, Minecraft mc, Player player, SymbioteVitals v, long now,
			boolean expanded) {
		String[] abilities = SymbioteAgentVenomAbilities.ABILITIES;
		AbilitySlot[] keyedOn = SymbioteAgentVenomAbilities.KEYED_ON;
		int punisherY = g.guiHeight() - BOX - MARGIN - 26; // PunisherHud's row
		int n = abilities.length;
		char[] keys = new char[n];
		int[] cds = new int[n];
		int[] active = new int[n];
		Component[] names = new Component[n];
		for (int i = 0; i < n; i++) {
			keys[i] = keyedOn[i].defaultKey();
			cds[i] = com.projecthero.mod.punisher.Punisher.cooldownRemaining(player, abilities[i]);
			names[i] = Component.translatable("projecthero.agent_venom.ability." + abilities[i]);
		}
		// Unleashed's cooldown starts on activation, so its first UNLEASHED_TICKS are the power itself running
		int unleashedLeft = cds[2] - (SymbioteAgentVenomAbilities.CD_UNLEASHED - SymbioteAgentVenomAbilities.UNLEASHED_TICKS);
		Component status = null;
		if (unleashedLeft > 0) {
			active[2] = unleashedLeft;
			status = Component.translatable("hud.projecthero.agent_venom.unleashed");
		}
		renderHeroHostPanel(g, mc, player, v, now, punisherY - 9 - PANEL_GAP,
				Component.translatable("hud.projecthero.agent_venom.title"), ACCENT_AGENT_VENOM,
				keys, cds, AGENT_VENOM_COOLDOWNS, active, names, status, expanded);
	}

	/**
	 * The shared hero-host panel, {@link #PANEL_W} wide and right-aligned, its bottom edge at {@code bottom}:
	 * <pre>
	 *   Black Suit                                  title
	 *   Revive           [X^][Z^][C^]               Symbiote revive (or a live status) / the three Sneak extras
	 *   12:34                                       (^ = the little sneak chevron in each box)
	 *   ------------------------------------        Hairline: the revive recharging
	 * </pre>
	 *
	 * @param active ticks each box's ability is still running for (0 = not running)
	 * @param status replaces the revive readout while something is running, or null
	 */
	private static void renderHeroHostPanel(GuiGraphics g, Minecraft mc, Player player, SymbioteVitals v, long now,
			int bottom, Component title, int accent, char[] keys, int[] cds, int[] maxCds, int[] active,
			Component[] names, Component status, boolean expanded) {
		int x0 = g.guiWidth() - MARGIN - PANEL_W;
		int barY = bottom - HAIRLINE;
		int y0 = barY - 3 - BOX;
		int titleY = y0 - 11;

		// ---- title
		g.drawString(mc.font, title.copy().withStyle(ChatFormatting.BOLD), x0, titleY, accent, true);

		// ---- the extras, right-aligned under the title
		int n = keys.length;
		int rowW = n * BOX + (n - 1) * GAP;
		int bx0 = x0 + PANEL_W - rowW;
		int runningTicks = 0;
		for (int i = 0; i < n; i++) {
			drawHostBox(g, mc, bx0 + i * (BOX + GAP), y0, keys[i], cds[i], maxCds[i], active[i], accent);
			runningTicks = Math.max(runningTicks, active[i]);
		}

		// ---- left of the boxes, two lines: the Symbiote revive (Revive / 12:34 or Ready), or -- while an extra
		// is running -- what is running and for how long
		long reviveLeft = Math.max(0L, v.resurrectReadyAt - now);
		if (status != null && runningTicks > 0) {
			g.drawString(mc.font, status, x0, y0 + 1, accent, true);
			g.drawString(mc.font, (runningTicks + 19) / 20 + "s", x0, y0 + 11, 0xFFFFFFFF, true);
		} else {
			g.drawString(mc.font, Component.translatable("hud.projecthero.symbiote.revive"), x0, y0 + 1, COLOR_MUTED, true);
			Component when = reviveLeft <= 0L
					? Component.translatable("hud.projecthero.symbiote.revive_ready")
					: Component.literal(clock(reviveLeft));
			g.drawString(mc.font, when, x0, y0 + 11, reviveLeft <= 0L ? accent : 0xFFFFFFFF, true);
		}

		// ---- the revive hairline
		int total = SymbioteVitalsManager.resurrectCooldownFor(player);
		float frac = reviveLeft <= 0L ? 1.0f : 1.0f - Math.min(1.0f, reviveLeft / (float) Math.max(1, total));
		g.fill(x0, barY, x0 + PANEL_W, barY + HAIRLINE, COLOR_BAR_BG);
		g.fill(x0, barY, x0 + Math.round(PANEL_W * frac), barY + HAIRLINE, reviveLeft <= 0L ? accent : dim(accent));

		if (expanded) {
			// Sneak + key: name, listed above the panel, bottom line = the rightmost box
			for (int i = 0; i < n; i++) {
				Component name = Component.literal("Sneak+" + keys[i] + "  ").append(names[i]);
				g.drawString(mc.font, name, g.guiWidth() - MARGIN - mc.font.width(name),
						titleY - 14 - (n - 1 - i) * 10, 0xFFD8D4E8, true);
			}
		}
	}

	/**
	 * One hero-host box, in the house style: the key top-left, a dark overlay that drains away with the cooldown
	 * and the seconds left on it; while the ability is running it is tinted with the accent instead, with the
	 * seconds it has left. A ready box wears a dimmed accent border.
	 */
	private static void drawHostBox(GuiGraphics g, Minecraft mc, int x, int y, char key, int cd, int maxCd,
			int active, int accent) {
		boolean running = active > 0;
		g.fill(x, y, x + BOX, y + BOX, COLOR_BOX_BG);
		if (running) {
			g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, (accent & 0x00FFFFFF) | 0x50000000);
		}
		g.renderOutline(x, y, BOX, BOX, running ? accent : (cd > 0 ? COLOR_BORDER : dim(accent)));
		g.drawString(mc.font, String.valueOf(key), x + 2, y + 2, cd > 0 && !running ? COLOR_KEY_DIM : COLOR_KEY, false);
		// the sneak chevron, top-right: these extras are all Sneak + that key
		int chevron = cd > 0 && !running ? COLOR_KEY_DIM : accent;
		g.fill(x + BOX - 5, y + 2, x + BOX - 4, y + 3, chevron);
		g.fill(x + BOX - 6, y + 3, x + BOX - 3, y + 4, chevron);
		g.fill(x + BOX - 7, y + 4, x + BOX - 6, y + 5, chevron);
		g.fill(x + BOX - 3, y + 4, x + BOX - 2, y + 5, chevron);
		if (running) {
			g.drawCenteredString(mc.font, String.valueOf((active + 19) / 20), x + BOX / 2 + 2, y + 10, 0xFFFFFFFF);
		} else if (cd > 0) {
			int h = maxCd > 0 ? Math.round((BOX - 2) * Math.min(1.0f, cd / (float) maxCd)) : BOX - 2;
			g.fill(x + 1, y + BOX - 1 - h, x + BOX - 1, y + BOX - 1, COLOR_COOLDOWN);
			g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2 + 2, y + 10, 0xFFFFFFFF);
		}
	}

	/** Half-brightness version of an opaque colour. */
	private static int dim(int argb) {
		return 0xFF000000 | ((argb >> 1) & 0x7F7F7F);
	}

	/** Ticks as m:ss. */
	private static String clock(long ticks) {
		long secs = (ticks + 19L) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60L, secs % 60L);
	}
}
