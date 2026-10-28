package com.projecthero.mod.client.greenlantern.mech;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;
import com.projecthero.mod.greenlantern.mech.MechComboKeys;

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
 *
 * <p>v0.17.1 construct combos: the keys a combo sits on ({@link MechComboKeys}) carry a small corner tag; holding Shift
 * flips the boxes to the Shift layer -- the Giga Drill and the Katana icons on their keys (with their cooldowns), the
 * Rocket Fist (or the katana's iaido) on R, the rest dimmed. With the katana drawn R and G show its cuts. In first
 * person a spin gauge rings the crosshair while the drill spins up.
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
	// glyph ids past the six moves
	private static final int G_SLASH = 6, G_SPIN = 7, G_IAIDO = 8, G_DRILL = 9, G_KATANA = 10, G_ROCKET = 11;

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
		drillGauge(g, m, mc);
		GreenLanternState s = mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		char[] combo = MechComboKeys.keys(s);
		boolean shift = Screen.hasShiftDown();
		boolean katana = m.katanaDrawn();
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
			default -> m.drillState() == HardLightMechEntity.DRILL_SPIN
					? Component.translatable("hud.projecthero.green_lantern.mech.drill_spin", Math.round(m.drillPower() * 100f)).withStyle(ChatFormatting.AQUA)
					: m.drillState() == HardLightMechEntity.DRILL_CHARGE ? Component.translatable("hud.projecthero.green_lantern.mech.drill_charge").withStyle(ChatFormatting.AQUA)
					: m.hasFlag(HardLightMechEntity.FLAG_BEAM) ? Component.translatable("hud.projecthero.green_lantern.mech.beam").withStyle(ChatFormatting.AQUA)
					: m.hasFlag(HardLightMechEntity.FLAG_SHIELD) ? Component.translatable("hud.projecthero.green_lantern.mech.shield").withStyle(ChatFormatting.AQUA)
					: m.katanaOut() ? Component.translatable("hud.projecthero.green_lantern.mech.katana").withStyle(ChatFormatting.AQUA)
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
			char base = KEYS[i].charAt(0);
			MechComboKeys.Combo here = comboAt(combo, base);
			int glyph = i;
			int cdSlot = CD[i];
			boolean active = (i == 3 && m.hasFlag(HardLightMechEntity.FLAG_BEAM)) || (i == 4 && m.hasFlag(HardLightMechEntity.FLAG_SHIELD))
					|| (i == 0 && m.hasFlag(HardLightMechEntity.FLAG_ROCKET_OUT));
			boolean dim = false;
			if (shift) {
				if (i == 0) {
					glyph = katana ? G_IAIDO : G_ROCKET;
					cdSlot = katana ? HardLightMechEntity.CD_IAIDO : HardLightMechEntity.CD_ROCKET;
				} else if (here == MechComboKeys.Combo.GIGA_DRILL) {
					glyph = G_DRILL;
					cdSlot = HardLightMechEntity.CD_DRILL;
					active = m.drillState() == HardLightMechEntity.DRILL_SPIN || m.drillState() == HardLightMechEntity.DRILL_CHARGE;
				} else if (here == MechComboKeys.Combo.KATANA) {
					glyph = G_KATANA;
					cdSlot = HardLightMechEntity.CD_KATANA;
					active = m.katanaOut();
				} else {
					dim = true; // nothing on the Shift layer here (it does the plain move)
				}
			} else if (katana && i == 0) {
				glyph = G_SLASH;
				cdSlot = HardLightMechEntity.CD_SLASH;
			} else if (katana && i == 1) {
				glyph = G_SPIN;
				cdSlot = HardLightMechEntity.CD_SPIN;
			}
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			String label = shift && !dim ? "⇧" + KEYS[i] : KEYS[i];
			g.drawString(mc.font, KEYS[i], x + 2, y0 + 2, dim ? 0xFF4E7A58 : PALE, false);
			if (shift && !dim) {
				g.fill(x + 2, y0 + 11, x + 7, y0 + 12, PALE); // a little underline: the Shift layer
			}
			glyph(g, glyph, x + BOX - 13, y0 + BOX - 13, dim ? 0xFF2E5A3A : active || !m.isActive() ? GREEN : 0xFF8FE8A8);
			if (!shift && here != null) {
				comboTag(g, here, x + BOX - 7, y0 + 1);
			}
			int cd = cdSlot >= 0 ? m.cooldownLeft(cdSlot) : 0;
			if (!m.isActive()) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x80000000);
			} else if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				if (cd > 20) {
					g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
				}
			}
			if (i == 0 && !shift) { // the Shift+R move (Rocket Fist, or the iaido with the katana drawn), a small bar along R
				int slot = katana ? HardLightMechEntity.CD_IAIDO : HardLightMechEntity.CD_ROCKET;
				int rc = m.cooldownLeft(slot);
				float ready = 1f - rc / (float) HardLightMechEntity.CD_LENGTHS[slot];
				g.fill(x + 1, y0 + BOX - 3, x + BOX - 1, y0 + BOX - 1, 0xC0062010);
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1, ready >= 1f ? 0xFF5CFF8E : 0xFF1E8A44);
			}
			if (here == MechComboKeys.Combo.GIGA_DRILL && m.drillState() == HardLightMechEntity.DRILL_SPIN) {
				float k = m.drillPower();
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * k), y0 + BOX - 1, k >= 1f ? 0xFFFFFFFF : 0xFF5CFF8E);
			}
		}

		// ---- Ring Charge
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
			moveList(g, mc, m, combo, katana, right, topY - LINE - 2);
		}
	}

	private static MechComboKeys.Combo comboAt(char[] combo, char base) {
		for (MechComboKeys.Combo c : MechComboKeys.Combo.values()) {
			if (combo[c.ordinal()] == base) {
				return c;
			}
		}
		return null;
	}

	/** The Alt list, bottom up: the six keys (the katana's cuts on R / G while it is out), Shift+R, then the combos. */
	private static void moveList(GuiGraphics g, Minecraft mc, HardLightMechEntity m, char[] combo, boolean katana, int right, int y) {
		java.util.List<Component> lines = new java.util.ArrayList<>();
		for (int i = 0; i < NAMES.length; i++) {
			String key = katana && i == 0 ? "katana_slash" : katana && i == 1 ? "katana_spin" : NAMES[i];
			lines.add(line(KEYS[i], Component.translatable("hud.projecthero.green_lantern.mech.move." + key)));
		}
		lines.add(line("Shift+R", Component.translatable("hud.projecthero.green_lantern.mech.move." + (katana ? "katana_iaido" : "rocket_fist"))));
		for (MechComboKeys.Combo c : MechComboKeys.Combo.values()) {
			char k = combo[c.ordinal()];
			if (k != 0) {
				lines.add(line("Shift+" + k, Component.translatable("hud.projecthero.green_lantern.mech.move." + c.id())));
			}
		}
		for (int i = lines.size() - 1; i >= 0; i--) {
			Component l = lines.get(i);
			g.drawString(mc.font, l, right - mc.font.width(l), y, 0xFFFFFFFF, true);
			y -= LINE;
		}
	}

	private static Component line(String key, Component name) {
		return Component.literal(key + "  ").withStyle(ChatFormatting.GREEN).append(name.copy().withStyle(ChatFormatting.WHITE));
	}

	/** The little corner tag on a key that holds a combo on its Shift layer (a drill point / a blade). */
	private static void comboTag(GuiGraphics g, MechComboKeys.Combo c, int x, int y) {
		int col = 0xFFE8FFEE;
		if (c == MechComboKeys.Combo.GIGA_DRILL) {
			g.fill(x, y + 1, x + 5, y + 2, col);
			g.fill(x + 1, y + 2, x + 4, y + 3, col);
			g.fill(x + 2, y + 3, x + 3, y + 5, col);
		} else {
			for (int k = 0; k < 5; k++) {
				g.fill(x + k, y + 4 - k, x + k + 1, y + 5 - k, col);
			}
			g.fill(x, y + 3, x + 2, y + 4, 0xFF35F075);
		}
	}

	/** A tiny drawn symbol per move (no icon atlas needed). */
	private static void glyph(GuiGraphics g, int i, int x, int y, int c) {
		int hi = 0xFFFFFFFF;
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
			case 5 -> { // missiles: three rising darts
				for (int k = 0; k < 3; k++) {
					g.fill(x + 1 + k * 4, y + 3 - (k % 2), x + 3 + k * 4, y + 10 - (k % 2), c);
					g.fill(x + 1 + k * 4, y + 1 - (k % 2), x + 3 + k * 4, y + 2 - (k % 2), hi);
				}
			}
			case G_SLASH -> { // a curved cut: an arc of steps with a bright edge
				for (int k = 0; k < 10; k++) {
					int yy = y + 1 + k + (k > 5 ? (k - 5) / 2 : 0) - (k > 5 ? k - 5 : 0);
					g.fill(x + 1 + k, yy, x + 3 + k, yy + 2, k > 6 ? hi : c);
				}
			}
			case G_SPIN -> { // a ring with an arrowhead
				g.renderOutline(x + 1, y + 1, 10, 10, c);
				g.fill(x + 9, y, x + 12, y + 1, hi);
				g.fill(x + 10, y + 1, x + 12, y + 3, hi);
				g.fill(x + 5, y + 5, x + 7, y + 7, c);
			}
			case G_IAIDO -> { // the crescent
				for (int k = 0; k < 12; k++) {
					int dy = (int) Math.round(Math.abs(k - 5.5) * Math.abs(k - 5.5) / 6.0);
					g.fill(x + k, y + 3 + dy, x + k + 1, y + 6 + dy - (Math.abs(k - 5.5) > 4 ? 2 : 0), k > 2 && k < 9 ? hi : c);
				}
			}
			case G_DRILL -> { // the spiral cone, point right
				for (int k = 0; k < 11; k++) {
					int h = Math.max(0, 5 - k / 2);
					g.fill(x + k, y + 6 - h, x + k + 1, y + 6 + h, c);
					if (k % 3 == 0) {
						g.fill(x + k, y + 6 - h, x + k + 1, y + 6 - h + 2, hi);
					}
				}
				g.fill(x + 11, y + 5, x + 12, y + 7, hi);
			}
			case G_KATANA -> { // a diagonal blade with its guard and grip
				for (int k = 0; k < 8; k++) {
					g.fill(x + 3 + k, y + 8 - k, x + 5 + k, y + 9 - k, k > 5 ? hi : c);
				}
				g.fill(x + 1, y + 7, x + 5, y + 8, hi); // tsuba
				g.fill(x + 2, y + 6, x + 4, y + 10, hi);
				g.fill(x, y + 10, x + 2, y + 12, c); // tsuka
			}
			default -> { // rocket fist: a fist with a flame tail
				g.fill(x + 5, y + 3, x + 12, y + 9, c);
				g.fill(x + 1, y + 5, x + 5, y + 7, hi);
				g.fill(x, y + 4, x + 2, y + 8, c);
			}
		}
	}

	/** First person, while the Giga Drill spins up: a gauge of light round the crosshair (white when it is full). */
	private static void drillGauge(GuiGraphics g, HardLightMechEntity m, Minecraft mc) {
		if (m.drillState() != HardLightMechEntity.DRILL_SPIN) {
			return;
		}
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		float k = m.drillPower();
		int n = 24, lit = Math.round(n * k);
		boolean full = k >= 1f;
		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2 + i * Math.PI * 2 / n;
			int x = cx + (int) Math.round(Math.cos(a) * 16), y = cy + (int) Math.round(Math.sin(a) * 16);
			int c = i < lit ? (full && (mc.level.getGameTime() / 3) % 2 == 0 ? 0xFFFFFFFF : 0xFF5CFF8E) : 0x60206030;
			g.fill(x - 1, y - 1, x + 2, y + 2, c);
		}
		Component t = Component.translatable(full ? "hud.projecthero.green_lantern.mech.drill_release" : "hud.projecthero.green_lantern.mech.drill_spin",
				Math.round(k * 100f)).withStyle(ChatFormatting.BOLD);
		g.drawCenteredString(mc.font, t, cx, cy + 24, full ? 0xFFFFFFFF : 0xFF8FE8A8);
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
