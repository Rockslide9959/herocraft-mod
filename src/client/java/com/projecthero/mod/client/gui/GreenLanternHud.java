package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Green Lantern HUD: the six ability keys with cooldown shading in the bottom-right corner (same
 * layout convention as {@link MaxSteelHud}/{@link ThorHud}), a green Ring Charge bar beneath it, the
 * selected construct's name, and -- while up -- the Directional Shield/Protective Dome HP bar. Drawn
 * whenever the player has the power at all -- Green Lantern's abilities work unsuited (only the suit's
 * own armour bonus requires actually wearing it; the ring's fall-damage immunity does not), so the HUD
 * is not suit-gated either, or an unsuited player would get zero charge/cooldown feedback.
 *
 * <p>Holding Alt reveals each slot's ability name (see {@link #renderAltPanel}), reusing the same
 * {@code projecthero.guide.green_lantern.ability.<key>} strings the guide chapter already shows.
 */
public final class GreenLanternHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	/** Vertical spacing between stacked label rows above the ability-key boxes. */
	private static final int LINE = 10;

	private static final int GREEN = 0xFF35F075;
	private static final int GREEN_DIM = 0xFF1A7838;
	private static final int LOW = 0xFFFF5A5A;
	private static final int BOX_BG = 0xC00A2412;
	private static final int BORDER = 0xFF1E661E;
	private static final int BORDER_ACTIVE = 0xFF35F075;
	private static final int COOLDOWN = 0xB0000000;
	private static final int KEY = 0xFFCFF8D4;
	private static final int PANEL_BG = 0xD0071812;

	/** Slot 1..6 -> the {@code projecthero.guide.green_lantern.ability.<key>} suffix for its name. */
	private static final String[] SLOT_ABILITY_KEYS = {
			"ring_bolt", "construct_fist", "oath", "shield", "suit", "construct"
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
		if (s == null || !s.hasPower) {
			return;
		}
		// The six slots belong to Green Lantern only while no experimental mutation is selected from
		// the wheel (see GreenLanternAbilityManager.hasContext) -- mirror that here, or a bonded
		// Lantern with a mutation active gets this HUD's boxes drawn on top of AbilityHud's.
		com.projecthero.mod.hero.data.ExperimentalState experimental =
				player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}

		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		float charge = Math.max(0f, Math.min(GreenLanternConfig.MAX_RING_CHARGE, s.ringCharge));

		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20;

		// Two stacked label rows above the ability boxes (the Mastery badge row is gone -- v0.11.5
		// removed the Willpower Mastery progression system entirely, every construct is unlocked from
		// the moment the ring bonds).
		int labelY = y0 - LINE;
		g.drawString(mc.font, Component.translatable("projecthero.guide.green_lantern")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), x0, labelY, GREEN);
		labelY -= LINE;
		Component construct = Component.translatable(ConstructType.byOrdinal(s.selectedConstruct).translationKey())
				.withStyle(ChatFormatting.DARK_GREEN);
		g.drawString(mc.font, construct, x0, labelY, GREEN_DIM, false);
		labelY -= LINE;

		// v0.11.8: constructs on cooldown get their own persistent row above the ability keys (explicit
		// user request: "show it above the HUD ... dont show it as a bar just show the construct with a
		// timer going down"), instead of only the fleeting action-bar message from trying to redeploy one.
		labelY = renderConstructCooldowns(g, mc, player, x0, labelY, totalW);

		boolean suited = s.suited;
		boolean barrierUp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		// v0.11.7: X's "Green Lantern's Light!" Oath empowerment mode -- both are synced non-persisted
		// attachments (same pattern as the barrier HP above), so the HUD can read them directly.
		long oathUntil = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L);
		long oathRecitingSince = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
		boolean oathActive = oathUntil > now;
		boolean oathReciting = !oathActive && oathRecitingSince > 0L;
		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			// v0.11.5: "make the toggleable abilities ability key outlines glow when used" -- Z glows
			// while a Shield/Dome is up, V glows while the suit is worn, same convention as
			// MaxSteelHud/ThorHud's own BORDER_ACTIVE use for an engaged mode/toggle. X (v0.11.7) glows
			// while reciting the Oath or empowered by it.
			boolean active = (i == 3 && barrierUp) || (i == 4 && suited) || (i == 2 && (oathActive || oathReciting));
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, KEY, false);

			if (i == 2 && oathActive) {
				// Empowered -- a green (not the ordinary dark cooldown) overlay showing the countdown,
				// so it reads as "active buff" rather than "can't use this".
				int remaining = (int) Math.max(0L, oathUntil - now);
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x8010A040);
				g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(remaining / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			} else if (i == 2 && oathReciting) {
				g.drawCenteredString(mc.font, "...", x + BOX / 2, y0 + BOX / 2 - 4, GREEN);
			} else {
				int cd = cooldownForSlot(player, i, s);
				if (cd > 0) {
					g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
					g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cd / 20.0f)),
							x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
				}
			}
		}

		// Ring Charge bar below the row. v0.11.7: the pulse now reacts to all eight escalating low-charge
		// thresholds (was a single hardcoded 10% cutoff) and speeds up the lower charge gets, so the bar
		// itself is the continuous "flash above the hotbar" the warning sounds/messages announce.
		float frac = charge / GreenLanternConfig.MAX_RING_CHARGE;
		int severity = com.projecthero.mod.greenlantern.GreenLanternEnergy.severityTier(frac);
		boolean low = severity > 0;
		int pulsePeriod = Math.max(3, 16 - severity * 2);
		boolean pulseOff = low && (now % pulsePeriod) < Math.max(1, pulsePeriod / 3);
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

		labelY = renderBarrier(g, mc, player, x0, labelY, totalW);

		if (Screen.hasAltDown()) {
			renderAltPanel(g, mc, x0, labelY, totalW);
		}
	}

	/**
	 * The shared shield/dome uptime meter row -- v0.11.8 rework. Previously showed the barrier's HP
	 * fraction, which only visibly moved when it actually absorbed a hit; explicit user request was "the
	 * dome bar doesn't deplete as the dome usage goes up ... make the bar stay while its regenerating and
	 * disappear once its full", i.e. this should track time-in-use, not damage taken. Visible whenever a
	 * barrier is actually up, OR the meter hasn't finished refilling yet; hidden entirely once neither is
	 * true. Returns the y just above whatever this drew, so the caller can keep stacking upward.
	 */
	private static int renderBarrier(GuiGraphics g, Minecraft mc, Player player, int x0, int topY, int totalW) {
		boolean active = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		float meter = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_METER, 1f);
		if (!active && meter >= 1f) {
			return topY;
		}
		boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		int h = 5;
		int y = topY - LINE - h;
		Component label = Component.translatable(active
				? (dome ? "hud.projecthero.green_lantern.dome" : "hud.projecthero.green_lantern.shield")
				: "hud.projecthero.green_lantern.barrier").withStyle(ChatFormatting.AQUA);
		g.drawCenteredString(mc.font, label, x0 + totalW / 2, y - 10, 0xFFFFFFFF);
		g.fill(x0 - 1, y - 1, x0 + totalW + 1, y + h + 1, BORDER);
		g.fill(x0, y, x0 + totalW, y + h, 0xAA0A2412);
		g.fill(x0, y, x0 + Math.round(totalW * meter), y + h, active ? 0xFF35C8F0 : 0xFF1E7A94);
		return y - LINE;
	}

	/**
	 * One right-aligned line per construct currently on its post-use cooldown, each with a countdown --
	 * not the generic per-slot ability-key boxes (those only ever show the currently *selected*
	 * construct's cooldown; this shows every one, including a type that isn't selected any more).
	 */
	private static int renderConstructCooldowns(GuiGraphics g, Minecraft mc, Player player, int x0, int topY, int totalW) {
		List<ConstructType> onCooldown = new ArrayList<>();
		for (ConstructType type : ConstructType.values()) {
			if (GreenLanternConstructs.cooldownRemainingFor(player, type) > 0) {
				onCooldown.add(type);
			}
		}
		if (onCooldown.isEmpty()) {
			return topY;
		}
		int y = topY;
		for (ConstructType type : onCooldown) {
			y -= LINE;
			int seconds = (int) Math.ceil(GreenLanternConstructs.cooldownRemainingFor(player, type) / 20.0);
			Component label = Component.literal("■ ").withStyle(s -> s.withColor(GREEN_DIM))
					.append(Component.translatable(type.translationKey()).withStyle(ChatFormatting.GRAY))
					.append(Component.literal("  " + seconds + "s").withStyle(ChatFormatting.WHITE));
			int w = mc.font.width(label);
			g.drawString(mc.font, label, x0 + totalW - w, y, 0xFFFFFFFF, false);
		}
		return y - 2;
	}

	/**
	 * Alt-hold panel: one line per ability slot naming what it does (tap and shift variants together,
	 * e.g. "R  Ring Bolt / Shift: Continuous Beam"), reusing the guide chapter's own strings so the two
	 * never drift. Grows upward from {@code topY} (the caller has already cleared every other stacked
	 * element) and its RIGHT edge lines up with the ability-key row's right edge, growing leftward --
	 * several of these lines are wider than the 130px key row, so left-anchoring at {@code x0} like
	 * every other element here would run most of the panel off the edge of the screen (the same reason
	 * {@code AbilityHud}'s own Alt-expanded names right-align instead of left-align).
	 */
	private static void renderAltPanel(GuiGraphics g, Minecraft mc, int x0, int topY, int totalW) {
		int lines = SLOT_ABILITY_KEYS.length;
		Component[] labels = new Component[lines];
		int panelW = 0;
		for (int i = 0; i < lines; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			labels[i] = Component.literal(slot.defaultKey() + "  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.green_lantern.ability." + SLOT_ABILITY_KEYS[i])
							.withStyle(ChatFormatting.WHITE));
			panelW = Math.max(panelW, mc.font.width(labels[i]));
		}
		panelW = Math.max(panelW + 8, totalW);
		// Clamp the width itself (not just the origin) so a narrow/auto-scaled GUI (Minecraft's scaled
		// width can be as low as ~320px) can't push the right edge off-screen the same way the
		// left-anchored version used to.
		panelW = Math.min(panelW, g.guiWidth() - 6);
		int panelH = lines * LINE + 4;
		int panelBottom = topY - 4;
		int panelTop = panelBottom - panelH;
		int px = Math.max(2, x0 + totalW - panelW);
		g.fill(px - 2, panelTop - 2, px + panelW, panelBottom + 2, PANEL_BG);
		g.renderOutline(px - 2, panelTop - 2, panelW + 2, panelH + 4, BORDER);

		int y = panelTop + 2;
		for (Component label : labels) {
			g.drawString(mc.font, label, px + 2, y, 0xFFFFFFFF, false);
			y += LINE;
		}
	}

	/**
	 * Slot 0/1/3 (R/G/Z) each have a tap AND a shift ability with their own separate cooldown key --
	 * v0.11.5 fix: previously only the tap half's key was checked, so e.g. War Hammer Slam's cooldown
	 * never showed on the G box at all. Slot 5 (C) shows whatever the currently *selected* construct's
	 * own cooldown is, since C can deploy any of them.
	 */
	private static int cooldownForSlot(Player player, int slot, GreenLanternState s) {
		return switch (slot) {
			case 0 -> Math.max(GreenLantern.cooldownRemaining(player, "ring_bolt"),
					GreenLantern.cooldownRemaining(player, "continuous_beam"));
			case 1 -> Math.max(GreenLantern.cooldownRemaining(player, "construct_fist"),
					GreenLantern.cooldownRemaining(player, "war_hammer_slam"));
			case 2 -> GreenLantern.cooldownRemaining(player, "oath_mode");
			case 3 -> Math.max(GreenLantern.cooldownRemaining(player, "directional_shield"),
					GreenLantern.cooldownRemaining(player, "protective_dome"));
			case 4 -> GreenLantern.cooldownRemaining(player, "ring_scan");
			case 5 -> com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.cooldownRemainingFor(
					player, ConstructType.byOrdinal(s.selectedConstruct));
			default -> 0;
		};
	}
}
