package com.projecthero.mod.client.greenlantern.mech;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the HUD while piloting the Hard-Light Mech (it replaces the Green Lantern HUD, same simple style, bottom
 * right): the mech's name and state, its INTEGRITY bar, the six mech-move keys (R G X Z V C -- never H or N) with their
 * cooldowns, a small bar along the R box for the Shift+R Rocket Fist, and the Ring Charge bar. Alt lists the moves. In
 * first person, thin cockpit brackets frame the view.
 */
public final class GreenLanternMechHud {
	private static final int BOX = 24;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int LINE = 10;
	private static final String[] KEYS = { "R", "G", "X", "Z", "V", "C" };
	private static final String[] NAMES = { "punch", "ground_slam", "dash", "beam", "shield", "missiles" };
	/** The cooldown slot each box shades from (-1: a hold move, no cooldown). */
	private static final int[] CD = { HardLightMechEntity.CD_PUNCH, HardLightMechEntity.CD_SLAM, HardLightMechEntity.CD_DASH, -1, -1,
			HardLightMechEntity.CD_MISSILES };

	private static final int GREEN = 0xFF35F075;
	private static final int PALE = 0xFFCFF8D4;
	private static final int LOW = 0xFFFF5A5A;
	private static final int BOX_BG = 0xC00A2412;
	private static final int BORDER = 0xFF1E661E;
	private static final int BORDER_ACTIVE = 0xFF35F075;
	private static final int COOLDOWN = 0xB0000000;
	private static final int TRACK = 0xAA0A2412;

	private GreenLanternMechHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		HardLightMechEntity m = HardLightMechEntity.mechOf(mc.player);
		if (m == null) {
			return;
		}
		if (mc.options.getCameraType().isFirstPerson()) {
			cockpitFrame(g, m);
		}
		int totalW = KEYS.length * BOX + (KEYS.length - 1) * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int right = x0 + totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20;

		// ---- title + state
		int labelY = y0 - LINE;
		Component title = Component.translatable("hud.projecthero.green_lantern.mech.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
		g.drawString(mc.font, title, x0, labelY, GREEN, true);
		Component state = switch (m.state()) {
			case HardLightMechEntity.STATE_ASSEMBLING -> Component.translatable("hud.projecthero.green_lantern.mech.assembling").withStyle(ChatFormatting.YELLOW);
			case HardLightMechEntity.STATE_DISSOLVING -> Component.translatable("hud.projecthero.green_lantern.mech.dissolving").withStyle(ChatFormatting.GRAY);
			default -> m.hasFlag(HardLightMechEntity.FLAG_BEAM) ? Component.translatable("hud.projecthero.green_lantern.mech.beam").withStyle(ChatFormatting.AQUA)
					: m.hasFlag(HardLightMechEntity.FLAG_SHIELD) ? Component.translatable("hud.projecthero.green_lantern.mech.shield").withStyle(ChatFormatting.AQUA)
					: null;
		};
		if (state != null && mc.font.width(title) + 6 + mc.font.width(state) <= totalW) {
			g.drawString(mc.font, state, right - mc.font.width(state), labelY, 0xFFFFFFFF, true);
		}
		labelY -= LINE + 6;

		// ---- integrity
		float hp = Math.max(0f, m.getHealth()), max = Math.max(1f, m.getMaxHealth());
		float frac = Mth.clamp(hp / max, 0f, 1f);
		boolean low = frac < 0.25f;
		Component integ = Component.translatable("hud.projecthero.green_lantern.mech.integrity", Math.round(hp), Math.round(max));
		g.drawString(mc.font, integ, x0, labelY, low ? LOW : PALE, true);
		g.fill(x0, labelY + 10, right, labelY + 14, TRACK);
		int col = low && (mc.level.getGameTime() / 4) % 2 == 0 ? 0xFFFFA0A0 : low ? LOW : frac < 0.5f ? 0xFFE8D84A : GREEN;
		g.fill(x0, labelY + 10, x0 + Math.round(totalW * frac), labelY + 14, col);
		int topY = labelY;

		// ---- the keys
		for (int i = 0; i < KEYS.length; i++) {
			int x = x0 + i * (BOX + GAP);
			boolean active = (i == 3 && m.hasFlag(HardLightMechEntity.FLAG_BEAM)) || (i == 4 && m.hasFlag(HardLightMechEntity.FLAG_SHIELD))
					|| (i == 0 && m.hasFlag(HardLightMechEntity.FLAG_ROCKET_OUT));
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, KEYS[i], x + 2, y0 + 2, PALE, false);
			glyph(g, i, x + BOX - 13, y0 + BOX - 13, active || !m.isActive() ? GREEN : 0xFF8FE8A8);
			int cd = CD[i] >= 0 ? m.cooldownLeft(CD[i]) : 0;
			if (!m.isActive()) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x80000000);
			} else if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				if (cd > 20) {
					g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
				}
			}
			if (i == 0) { // the Shift+R Rocket Fist, a small bar along the bottom of R
				int rc = m.cooldownLeft(HardLightMechEntity.CD_ROCKET);
				float ready = 1f - rc / (float) HardLightMechEntity.CD_LENGTHS[HardLightMechEntity.CD_ROCKET];
				g.fill(x + 1, y0 + BOX - 3, x + BOX - 1, y0 + BOX - 1, 0xC0062010);
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1, ready >= 1f ? 0xFF5CFF8E : 0xFF1E8A44);
			}
		}

		// ---- Ring Charge
		GreenLanternState s = mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s != null) {
			float maxCharge = GreenLanternRanks.maxCharge(mc.player);
			float cf = Mth.clamp(s.ringCharge, 0f, maxCharge) / maxCharge;
			int barY = y0 + BOX + 4;
			g.fill(x0, barY, right, barY + 3, TRACK);
			g.fill(x0, barY, x0 + Math.round(totalW * cf), barY + 3, cf < 0.15f ? LOW : GREEN);
			g.drawString(mc.font, Component.translatable("hud.projecthero.green_lantern.mech.charge", Math.round(cf * 100f)), x0, barY + 5,
					cf < 0.15f ? LOW : 0xFFA8E6B8, true);
		}

		if (Screen.hasAltDown() || MoveListOverlay.held()) {
			int y = topY - LINE - 2;
			for (int i = NAMES.length; i >= 0; i--) {
				String key = i == NAMES.length ? "rocket_fist" : NAMES[i];
				String label = i == NAMES.length ? "Shift+R" : KEYS[i];
				Component line = Component.literal(label + "  ").withStyle(ChatFormatting.GREEN)
						.append(Component.translatable("hud.projecthero.green_lantern.mech.move." + key).withStyle(ChatFormatting.WHITE));
				g.drawString(mc.font, line, right - mc.font.width(line), y, 0xFFFFFFFF, true);
				y -= LINE;
			}
		}
	}

	/** A tiny drawn symbol per move (no icon atlas needed): fist, slam, dash chevron, beam, shield, missiles. */
	private static void glyph(GuiGraphics g, int i, int x, int y, int c) {
		switch (i) {
			case 0 -> { // fist
				g.fill(x + 2, y + 3, x + 10, y + 9, c);
				g.fill(x + 3, y + 2, x + 10, y + 3, c);
				g.fill(x + 3, y + 9, x + 8, y + 11, c);
			}
			case 1 -> { // slam: a fist over a ground line + cracks
				g.fill(x + 3, y + 1, x + 9, y + 6, c);
				g.fill(x, y + 9, x + 12, y + 10, c);
				g.fill(x + 1, y + 7, x + 2, y + 9, c);
				g.fill(x + 10, y + 7, x + 11, y + 9, c);
			}
			case 2 -> { // dash: two chevrons
				for (int k = 0; k < 2; k++) {
					int ox = x + k * 5;
					for (int r = 0; r < 4; r++) {
						g.fill(ox + r, y + 2 + r, ox + r + 2, y + 3 + r, c);
						g.fill(ox + r, y + 9 - r, ox + r + 2, y + 10 - r, c);
					}
				}
			}
			case 3 -> { // beam: the lantern ring + a ray
				g.renderOutline(x, y + 3, 6, 6, c);
				g.fill(x + 6, y + 5, x + 12, y + 7, c);
			}
			case 4 -> { // shield wall
				g.renderOutline(x + 1, y + 1, 10, 10, c);
				g.fill(x + 3, y + 3, x + 9, y + 9, (c & 0x00FFFFFF) | 0x60000000);
			}
			default -> { // missiles: three rising darts
				for (int k = 0; k < 3; k++) {
					g.fill(x + 1 + k * 4, y + 3 - (k % 2), x + 3 + k * 4, y + 10 - (k % 2), c);
					g.fill(x + 1 + k * 4, y + 1 - (k % 2), x + 3 + k * 4, y + 2 - (k % 2), 0xFFFFFFFF);
				}
			}
		}
	}

	/** First person: thin hard-light brackets in the corners, like looking out of the cockpit glass. */
	private static void cockpitFrame(GuiGraphics g, HardLightMechEntity m) {
		int w = g.guiWidth(), h = g.guiHeight();
		int c = m.golden() ? 0x70E8D040 : 0x7035F075;
		int L = Math.min(w, h) / 6, t = 2, in = 10;
		g.fill(in, in, in + L, in + t, c);
		g.fill(in, in, in + t, in + L, c);
		g.fill(w - in - L, in, w - in, in + t, c);
		g.fill(w - in - t, in, w - in, in + L, c);
		g.fill(in, h - in - t, in + L, h - in, c);
		g.fill(in, h - in - L, in + t, h - in, c);
		g.fill(w - in - L, h - in - t, w - in, h - in, c);
		g.fill(w - in - t, h - in - L, w - in, h - in, c);
		g.fill(0, 0, w, 3, 0x2035F075);
		g.fill(0, h - 3, w, h, 0x2035F075);
	}
}
