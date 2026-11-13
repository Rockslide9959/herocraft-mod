package com.projecthero.mod.client.greenlantern.mech;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
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
 * v0.17.1: the HUD while piloting the Hard-Light Mech (it replaces the Green Lantern HUD). v0.17.3 (user: "I really like the
 * Minecraft-style HUD and ability keybinds ... apply this to all the powers"): the shared bottom-right {@link VanillaHud.Stack}
 * on the game's own pieces -- the six mech-move keys (R G Z X C V -- never H or N) with each move's glyph in its slot like an
 * item and the cooldown sweep, R's other-layer move (the Shift+R Rocket Fist / iaido, or the plain punch while Shift is held) as
 * its durability bar; the Ring Charge and Integrity rows over them, then the mech's name and state. Alt lists the moves. In
 * first person, thin cockpit brackets frame the view (kept as they are).
 *
 * <p>v0.17.1 construct combos: the keys a combo sits on ({@link MechComboKeys}) carry a small corner tag; holding Shift
 * flips the slots to the Shift layer (yellow keys) -- the Giga Drill and the Katana glyphs on their keys (with their cooldowns),
 * the Rocket Fist (or the katana's iaido) on R, the rest greyed. With the katana drawn R and G show its cuts. The Giga Drill's
 * spin-up fills its slot; in first person a spin gauge also rings the crosshair.
 */
public final class GreenLanternMechHud {
	private static final String[] KEYS = { "R", "G", "X", "Z", "V", "C" };
	private static final String[] NAMES = { "punch", "ground_slam", "dash", "beam", "shield", "missiles" };
	/** The cooldown slot each box shades from (-1: a hold move, no cooldown). */
	private static final int[] CD = { HardLightMechEntity.CD_PUNCH, HardLightMechEntity.CD_SLAM, HardLightMechEntity.CD_DASH, -1, -1,
			HardLightMechEntity.CD_MISSILES };
	// glyph ids past the six moves
	private static final int G_SLASH = 6, G_SPIN = 7, G_IAIDO = 8, G_DRILL = 9, G_KATANA = 10, G_ROCKET = 11;
	/** v0.17.1 round 2: the Meteor Mace, Wrecking Whirl and Lantern Cannon glyphs. */
	private static final int G_MACE = 12, G_WHIRL = 13, G_CANNON = 14;

	private static final int GREEN = 0xFF35F075;
	private static final int PALE = 0xFFCFF8D4;
	private static final int LOW = 0xFFFF5A5A;
	private static final int AQUA = 0xFF55FFFF;

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

		// ---- the keys, R G Z X C V (v0.17.3: the shared bottom-right column on the game's own pieces)
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, KEYS.length).keys(KEYS.length);
		for (int n = 0; n < KEYS.length; n++) {
			int i = VanillaHud.ORDER[n].ordinal(); // the mech key index (R G X Z V C)
			int ix = st.stripX() + 3 + n * VanillaHud.SLOT;
			int iy = st.stripY() + 3;
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
				} else if (here == MechComboKeys.Combo.METEOR_MACE) {
					glyph = G_MACE;
					cdSlot = HardLightMechEntity.CD_MACE;
					active = m.move() == HardLightMechEntity.MOVE_MACE;
				} else if (here == MechComboKeys.Combo.WRECKING_WHIRL) {
					glyph = G_WHIRL;
					cdSlot = HardLightMechEntity.CD_WHIRL;
					active = m.move() == HardLightMechEntity.MOVE_WHIRL;
				} else if (here == MechComboKeys.Combo.LANTERN_CANNON) {
					glyph = G_CANNON;
					cdSlot = HardLightMechEntity.CD_CANNON;
					active = m.move() == HardLightMechEntity.MOVE_CANNON;
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
			// the move's glyph sits in the slot like an item, the key letter over it
			glyph(g, glyph, ix + 2, iy + 2, dim ? 0xFF2E5A3A : active || !m.isActive() ? GREEN : 0xFF8FE8A8);
			int cd = cdSlot >= 0 ? m.cooldownLeft(cdSlot) : 0;
			float frac = cd > 0 ? Math.min(1f, cd / (float) Math.max(1, HardLightMechEntity.CD_LENGTHS[cdSlot])) : 0f;
			// the other layer's R move: the Shift+R move (Rocket Fist, or the iaido with the katana drawn) unshifted, the plain R
			// move (the punch, or the katana's slash) while Shift shows the Shift layer
			float other = 0f;
			if (i == 0) {
				int os = shift ? (katana ? HardLightMechEntity.CD_SLASH : HardLightMechEntity.CD_PUNCH)
						: (katana ? HardLightMechEntity.CD_IAIDO : HardLightMechEntity.CD_ROCKET);
				other = Math.min(1f, m.cooldownLeft(os) / (float) Math.max(1, HardLightMechEntity.CD_LENGTHS[os]));
			}
			boolean usable = m.isActive() && !dim;
			st.slot(n, VanillaHud.keyLabel(VanillaHud.ORDER[n]), usable || active, shift && !dim, m.isActive() ? frac : 0f,
					m.isActive() ? VanillaHud.seconds(cd) : 0, other, active);
			if (!shift && here != null) {
				comboTag(g, here, ix + 11, iy); // the key holds a combo on its Shift layer
			}
			if (here == MechComboKeys.Combo.GIGA_DRILL && m.drillState() == HardLightMechEntity.DRILL_SPIN) {
				st.hold(n, m.drillPower()); // the drill spinning up
			}
		}

		// ---- Ring Charge, then the Integrity
		if (s != null) {
			float maxCharge = GreenLanternRanks.maxCharge(mc.player);
			float cf = Mth.clamp(s.ringCharge, 0f, maxCharge) / maxCharge;
			st.meter(Component.translatable("hud.projecthero.vanilla_hud.ring"), cf, cf < 0.15f ? LOW : GREEN, Math.round(cf * 100f) + "%",
					cf < 0.15f ? LOW : 0xFFA8E6B8);
		}
		float hp = Math.max(0f, m.getHealth()), max = Math.max(1f, m.getMaxHealth());
		float frac = Mth.clamp(hp / max, 0f, 1f);
		boolean low = frac < 0.25f;
		boolean blink = low && (mc.level.getGameTime() / 4) % 2 == 0;
		st.meter(Component.translatable("hud.projecthero.vanilla_hud.integrity"), frac, low ? LOW : frac < 0.5f ? 0xFFE8D84A : GREEN,
				Math.round(hp) + "/" + Math.round(max), blink ? 0xFFFFA0A0 : low ? LOW : PALE);

		// ---- title + state
		st.title(Component.translatable("hud.projecthero.green_lantern.mech.title"), GREEN);
		VanillaHud.Tag state = switch (m.state()) {
			case HardLightMechEntity.STATE_ASSEMBLING -> tag("hud.projecthero.green_lantern.mech.assembling", VanillaHud.YELLOW);
			case HardLightMechEntity.STATE_DISSOLVING -> tag("hud.projecthero.green_lantern.mech.dissolving", VanillaHud.GREY);
			default -> m.drillState() == HardLightMechEntity.DRILL_SPIN
					? new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.mech.drill_spin", Math.round(m.drillPower() * 100f)).getString(), AQUA)
					: m.drillState() == HardLightMechEntity.DRILL_CHARGE ? tag("hud.projecthero.green_lantern.mech.drill_charge", AQUA)
					: m.hasFlag(HardLightMechEntity.FLAG_BEAM) ? tag("hud.projecthero.green_lantern.mech.beam", AQUA)
					: m.hasFlag(HardLightMechEntity.FLAG_SHIELD) ? tag("hud.projecthero.green_lantern.mech.shield", AQUA)
					: m.katanaOut() ? tag("hud.projecthero.green_lantern.mech.katana", AQUA)
					: null;
		};
		if (state != null) {
			st.tags(java.util.List.of(state));
		}

		if (Screen.hasAltDown() || MoveListOverlay.held()) {
			MoveListOverlay.draw(g, st.x0 + st.width, st.top() - 4, moveRows(combo, katana));
		}
	}

	private static VanillaHud.Tag tag(String key, int colour) {
		return new VanillaHud.Tag(Component.translatable(key).getString(), colour);
	}

	private static MechComboKeys.Combo comboAt(char[] combo, char base) {
		for (MechComboKeys.Combo c : MechComboKeys.Combo.values()) {
			if (combo[c.ordinal()] == base) {
				return c;
			}
		}
		return null;
	}

	/**
	 * The Alt list (the shared move list), R G Z X C V: each key's move (the katana's cuts on R / G while it is out) and its
	 * Shift move -- Shift+R the Rocket Fist (or the iaido), the other keys the construct combo sitting on them.
	 */
	private static java.util.List<MoveListOverlay.Row> moveRows(char[] combo, boolean katana) {
		java.util.List<MoveListOverlay.Row> rows = new java.util.ArrayList<>();
		for (com.projecthero.mod.hero.AbilitySlot slot : VanillaHud.ORDER) {
			int i = slot.ordinal();
			String key = katana && i == 0 ? "katana_slash" : katana && i == 1 ? "katana_spin" : NAMES[i];
			MechComboKeys.Combo here = comboAt(combo, KEYS[i].charAt(0));
			Component shifted = i == 0 ? move(katana ? "katana_iaido" : "rocket_fist") : here != null ? move(here.id()) : null;
			Component extra = i == 0 && here != null ? move(here.id()) : null;
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), move(key), shifted, extra));
		}
		return rows;
	}

	private static Component move(String id) {
		return Component.translatable("hud.projecthero.green_lantern.mech.move." + id);
	}

	/** The little corner tag on a key that holds a combo on its Shift layer (a drill point / a blade). */
	private static void comboTag(GuiGraphics g, MechComboKeys.Combo c, int x, int y) {
		int col = 0xFFE8FFEE;
		if (c == MechComboKeys.Combo.GIGA_DRILL) {
			g.fill(x, y + 1, x + 5, y + 2, col);
			g.fill(x + 1, y + 2, x + 4, y + 3, col);
			g.fill(x + 2, y + 3, x + 3, y + 5, col);
		} else if (c != MechComboKeys.Combo.KATANA) { // v0.17.1 round 2: a dot of light for the one-shot combos
			g.fill(x + 1, y + 1, x + 4, y + 4, col);
			g.fill(x + 2, y + 2, x + 3, y + 3, 0xFF35F075);
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
			case G_MACE -> { // a haft and a spiked head
				g.fill(x + 1, y + 10, x + 7, y + 11, c);
				for (int k = 0; k < 5; k++) {
					g.fill(x + 1 + k, y + 10 - k, x + 2 + k, y + 11 - k, c);
				}
				g.fill(x + 6, y + 1, x + 11, y + 6, c);
				g.fill(x + 8, y, x + 9, y + 7, hi);
				g.fill(x + 5, y + 3, x + 12, y + 4, hi);
			}
			case G_WHIRL -> { // a ball on a chain, swung round
				g.renderOutline(x, y, 12, 12, (c & 0x00FFFFFF) | 0x80000000);
				for (int k = 0; k < 4; k++) {
					g.fill(x + 5 + k, y + 6 - k / 2, x + 6 + k, y + 7 - k / 2, c);
				}
				g.fill(x + 8, y + 1, x + 12, y + 5, hi);
				g.fill(x + 5, y + 5, x + 7, y + 7, c);
			}
			case G_CANNON -> { // a barrel and a shell flying out
				g.fill(x, y + 4, x + 8, y + 8, c);
				g.fill(x + 7, y + 3, x + 9, y + 9, hi);
				g.fill(x + 10, y + 5, x + 12, y + 7, hi);
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
