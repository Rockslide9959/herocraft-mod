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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Green Lantern HUD: the six ability keys with cooldown shading in the bottom-right corner (same
 * layout convention as {@link MaxSteelHud}/{@link ThorHud}), a green Ring Charge bar beneath it, the
 * selected construct's name, and -- while up -- the Directional Shield/Protective Dome HP bar. Drawn
 * whenever the player has the power at all -- Green Lantern's abilities work unsuited (only the suit's
 * own armour/Emergency Catch passives require actually wearing it), so the HUD is not suit-gated
 * either, or an unsuited player would get zero charge/cooldown feedback.
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

	/** Slot 1..6 -> the ability id whose cooldown that box should show (or null). */
	private static final String[] SLOT_COOLDOWNS = {
			"ring_bolt", "construct_fist", null, "directional_shield", "ring_scan", null
	};
	/** Slot 1..6 -> the {@code projecthero.guide.green_lantern.ability.<key>} suffix for its name. */
	private static final String[] SLOT_ABILITY_KEYS = {
			"ring_bolt", "construct_fist", "flight", "shield", "suit", "construct"
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

		// Three stacked label rows above the ability boxes, each on its own line so a long construct
		// name and the Mastery badge can never collide (they used to share one row -- see history).
		int labelY = y0 - LINE;
		g.drawString(mc.font, Component.translatable("projecthero.guide.green_lantern")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), x0, labelY, GREEN);
		labelY -= LINE;
		Component construct = Component.translatable(ConstructType.byOrdinal(s.selectedConstruct).translationKey())
				.withStyle(ChatFormatting.DARK_GREEN);
		g.drawString(mc.font, construct, x0, labelY, GREEN_DIM, false);
		labelY -= LINE;
		Component mastery = Component.literal("Mastery " + masteryLabel(s.masteryLevel)).withStyle(ChatFormatting.DARK_GREEN);
		g.drawString(mc.font, mastery, x0, labelY, 0xFFFFFFFF, false);
		labelY -= LINE;

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

		labelY = renderBarrier(g, mc, player, x0, labelY, totalW);

		if (Screen.hasAltDown()) {
			renderAltPanel(g, mc, x0, labelY, totalW);
		}
	}

	/** Returns the y just above whatever this drew, so the caller can keep stacking upward. */
	private static int renderBarrier(GuiGraphics g, Minecraft mc, Player player, int x0, int topY, int totalW) {
		float hp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
		if (hp <= 0f) {
			return topY;
		}
		boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		float max = dome ? GreenLanternConfig.DOME_HP : GreenLanternConfig.SHIELD_HP;
		float frac = Math.max(0f, Math.min(1f, hp / max));
		int h = 5;
		int y = topY - LINE - h;
		Component label = Component.translatable(dome
				? "hud.projecthero.green_lantern.dome" : "hud.projecthero.green_lantern.shield")
				.withStyle(ChatFormatting.AQUA);
		g.drawCenteredString(mc.font, label, x0 + totalW / 2, y - 10, 0xFFFFFFFF);
		g.fill(x0 - 1, y - 1, x0 + totalW + 1, y + h + 1, BORDER);
		g.fill(x0, y, x0 + totalW, y + h, 0xAA0A2412);
		g.fill(x0, y, x0 + Math.round(totalW * frac), y + h, 0xFF35C8F0);
		return y - LINE;
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

	private static String masteryLabel(int level) {
		return switch (level) {
			case GreenLanternState.MASTERY_I -> "I";
			case GreenLanternState.MASTERY_II -> "II";
			case GreenLanternState.MASTERY_III -> "III";
			case GreenLanternState.MASTERY_IV -> "IV";
			default -> "Bonded";
		};
	}
}
