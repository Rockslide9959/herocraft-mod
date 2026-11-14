package com.projecthero.mod.client.spin;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinAbilityManager;
import com.projecthero.mod.spin.SpinConfig;
import com.projecthero.mod.spin.data.SpinState;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 the Spin HUD, bottom-right, built from the game's own pieces ({@link VanillaHud}) like the other recent
 * powers':
 * <pre>
 *   Riding +30%  Disguised 0:15  Valkyrie 0:42     status tags (wrap onto more lines when they don't fit)
 *   the Spin                      (o) (o)      the two balls: green in hand, dark while thrown or busy
 *   Spin     [Meter =======-]          64          gold-green (the user's "Meter" style)
 *   +----+----+----+----+----+----+                R G Z X C V: cooldowns; a move whose ball is out (or that he can't pay
 *   | R  | G  | Z  | X  | C  | V  |                for) is greyed; yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+
 * </pre>
 * And, centred on the crosshair while Shift+G / Shift+Z is held, the <b>Golden Rectangle</b>: a gold golden-ratio rectangle that
 * shrinks onto the crosshair's fixed frame. When the two line up (the Perfect window) it flashes white-gold; past it, it turns
 * orange and keeps closing. On release a "PERFECT SPIN" (or "Missed") flash. Hold Left-Alt for the move list.
 */
public final class SpinHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int GOLD_TEXT = 0xFFFFC83A;
	private static final int GREEN_TEXT = 0xFF6ED48A;
	/** The crosshair frame: a golden rectangle (23 x 14, near enough 1.618 : 1). */
	private static final int RECT_W = 23, RECT_H = 14;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private SpinHud() {
	}

	private record Tag(String text, int colour) {
	}

	static String clock(int ticks) {
		int s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		SpinState s = Spin.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		goldenRectangle(g, mc, p, s, partial);

		Font font = mc.font;
		long now = mc.level.getGameTime();
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, x0, y0);

		// Spin
		int by = y0 - 8;
		float spin = s.spin / SpinConfig.SPIN_MAX;
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.spin.spin"), spin, 0xE8C440, String.valueOf(Math.round(s.spin)),
				spin < 0.15f ? VanillaHud.RED : VanillaHud.WHITE);
		// the title line and the two balls
		by -= 11;
		String title = Component.translatable("hud.projecthero.spin.title").getString();
		g.drawString(font, font.plainSubstrByWidth(title, WIDTH - 22), x0, by - 2, GOLD_TEXT, true);
		for (int i = 0; i < 2; i++) {
			int bx = x0 + WIDTH - 18 + i * 10;
			ballIcon(g, bx, by - 2, Spin.ballReady(p, i)); // filled while that ball is in one of his hands (or, in Gyro's suit, on his belt)
		}
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		if (Spin.riding(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.spin.riding").getString(), GOLD_TEXT));
		}
		// v0.17.4: Gyro's suit -- the bar refills 30% faster (and the balls work from the holsters)
		if (com.projecthero.mod.spin.GyroSuitItem.suitWorn(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.spin.suit").getString(), GREEN_TEXT));
		}
		if (Spin.disguised(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.spin.disguised").getString() + " " + clock(Spin.disguiseTicksLeft(p)), GREEN_TEXT));
		}
		if (Spin.shielding(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.spin.shield").getString(), GREEN_TEXT));
		}
		if (Spin.valkyrieCooldown(p) > 0) {
			tags.add(new Tag(Component.translatable("hud.projecthero.spin.valkyrie").getString() + " " + clock(Spin.valkyrieCooldown(p)), VanillaHud.RED));
		}
		int top = by - 2;
		if (!tags.isEmpty()) {
			List<List<Tag>> lines = new ArrayList<>();
			List<Tag> line = new ArrayList<>();
			int w = 0;
			for (Tag t : tags) {
				String text = font.plainSubstrByWidth(t.text, WIDTH);
				int tw = font.width(text);
				if (!line.isEmpty() && w + 6 + tw > WIDTH) {
					lines.add(line);
					line = new ArrayList<>();
					w = 0;
				}
				line.add(new Tag(text, t.colour));
				w += (line.size() > 1 ? 6 : 0) + tw;
			}
			lines.add(line);
			for (List<Tag> l : lines) {
				top -= 10;
				int x = x0;
				for (Tag t : l) {
					g.drawString(font, t.text, x, top, t.colour, true);
					x += font.width(t.text) + 6;
				}
			}
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SpinAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.spin.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.spin.ability." + ids[1]) : null));
			}
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}
	}

	private static void ballIcon(GuiGraphics g, int x, int y, boolean ready) {
		int edge = ready ? 0xFF1E4A28 : 0xFF2A2A2A;
		int body = ready ? 0xFF5BAE5E : 0xFF4A4A4A;
		int shine = ready ? 0xFFB8F0B0 : 0xFF6A6A6A;
		g.fill(x + 2, y, x + 6, y + 8, edge);
		g.fill(x, y + 2, x + 8, y + 6, edge);
		g.fill(x + 1, y + 1, x + 7, y + 7, edge);
		g.fill(x + 2, y + 1, x + 6, y + 7, body);
		g.fill(x + 1, y + 2, x + 7, y + 6, body);
		g.fill(x + 2, y + 2, x + 4, y + 4, shine);
		if (ready) {
			g.fill(x + 3, y + 3, x + 5, y + 5, 0xFFE8C440); // the hexagon plate's glint
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, SpinState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = SpinAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SpinAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, SpinAbilityManager.maxCooldown(p, shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, SpinAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, SpinAbilityManager.maxCooldown(p, other)));
			boolean usable = SpinAbilityManager.ballsAllow(p, shown) && (p.getAbilities().instabuild || s.spin + 1.0e-3f >= SpinAbilityManager.cost(shown));
			boolean active = (slot == AbilitySlot.SLOT_6 && Spin.shielding(p)) || (slot == AbilitySlot.SLOT_5 && Spin.disguised(p))
					|| (SpinAbilityManager.isHold(shown) && Spin.charging(p) != SpinState.CHARGE_NONE);
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	// ---------------------------------------------------------------- the Golden Rectangle

	/** How far the moving rectangle is from the frame: its scale (1 = lined up) after {@code e} of {@code target} ticks. */
	public static float rectScale(float e, float target) {
		if (e <= target) {
			return 1f + 5f * (1f - e / target);
		}
		return Math.max(0.2f, 1f - (e - target) / Math.max(1f, target) * 0.9f);
	}

	private static void goldenRectangle(GuiGraphics g, Minecraft mc, LocalPlayer p, SpinState s, float partial) {
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		Font font = mc.font;
		long now = mc.level.getGameTime();
		int move = Spin.charging(p);
		if (move != SpinState.CHARGE_NONE) {
			float e = Spin.chargeElapsed(p) + partial;
			int target = Spin.chargeTarget(p);
			boolean inWindow = Math.abs(e - target) <= SpinConfig.PERFECT_WINDOW + 0.5f;
			boolean late = e > target + SpinConfig.PERFECT_WINDOW + 0.5f;
			float sc = rectScale(e, target);
			// the fixed frame round the crosshair
			frame(g, cx, cy, RECT_W, RECT_H, 1, inWindow ? 0xFFFFFFFF : 0x99E8C440);
			// the closing rectangle, with the golden spiral's squares inside it
			int w = Math.round(RECT_W * sc), h = Math.round(RECT_H * sc);
			int colour = inWindow ? ((now / 2) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFD84A) : late ? 0xFFFF7A30 : 0xFFF2C230;
			frame(g, cx, cy, w, h, sc > 2f ? 2 : 1, colour);
			goldenSquares(g, cx - w / 2, cy - h / 2, w, h, (colour & 0x00FFFFFF) | 0x70000000);
			String label = Component.translatable(inWindow ? "hud.projecthero.spin.perfect_now"
					: move == SpinState.CHARGE_BREAKER ? "hud.projecthero.spin.breaker_hint" : "hud.projecthero.spin.golden_hint").getString();
			label = font.plainSubstrByWidth(label, Math.min(200, g.guiWidth() - 20));
			g.drawString(font, label, cx - font.width(label) / 2, cy + Math.max(h / 2, RECT_H) + 6, inWindow ? 0xFFFFFFFF : GOLD_TEXT, true);
		}
		// the flash after a release
		long since = now - SpinFx.perfectFlashAt;
		if (since >= 0 && since < 24) {
			String text = Component.translatable(SpinFx.perfectFlash ? "hud.projecthero.spin.perfect" : "hud.projecthero.spin.miss").getString();
			int alpha = Mth.clamp((int) (255 * (1f - since / 24f)), 16, 255);
			int col = (alpha << 24) | (SpinFx.perfectFlash ? 0xFFD84A : 0xB0B0B0);
			g.pose().pushPose();
			float scale = SpinFx.perfectFlash ? 1.6f : 1.2f;
			g.pose().translate(cx, cy - 34, 0);
			g.pose().scale(scale, scale, 1f);
			g.drawString(font, text, -font.width(text) / 2, 0, col, true);
			g.pose().popPose();
		}
	}

	private static void frame(GuiGraphics g, int cx, int cy, int w, int h, int t, int colour) {
		int x0 = cx - w / 2, y0 = cy - h / 2, x1 = x0 + w, y1 = y0 + h;
		g.fill(x0, y0, x1, y0 + t, colour);
		g.fill(x0, y1 - t, x1, y1, colour);
		g.fill(x0, y0, x0 + t, y1, colour);
		g.fill(x1 - t, y0, x1, y1, colour);
	}

	/** The golden rectangle's classic subdivision: a square off one end, then the same again in what's left, turning each time. */
	private static void goldenSquares(GuiGraphics g, int x, int y, int w, int h, int colour) {
		for (int k = 0; k < 6 && w > 3 && h > 3; k++) {
			switch (k % 4) {
				case 0 -> { // square on the left
					int sq = Math.min(h, w);
					g.fill(x + sq, y, x + sq + 1, y + h, colour);
					x += sq;
					w -= sq;
				}
				case 1 -> { // square on top
					int sq = Math.min(w, h);
					g.fill(x, y + sq, x + w, y + sq + 1, colour);
					y += sq;
					h -= sq;
				}
				case 2 -> { // square on the right
					int sq = Math.min(h, w);
					g.fill(x + w - sq, y, x + w - sq + 1, y + h, colour);
					w -= sq;
				}
				default -> { // square at the bottom
					int sq = Math.min(w, h);
					g.fill(x, y + h - sq, x + w, y + h - sq + 1, colour);
					h -= sq;
				}
			}
		}
	}
}
