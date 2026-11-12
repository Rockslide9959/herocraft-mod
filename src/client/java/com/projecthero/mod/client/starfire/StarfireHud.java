package com.projecthero.mod.client.starfire;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireAbilityManager;
import com.projecthero.mod.starfire.StarfireConfig;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Starfire's HUD, bottom-right, built from the game's own pieces ({@link VanillaHud}) like the other recent powers':
 * <pre>
 *   Flying  Radiance  Bond 0:42           status tags (wrap onto more lines when they don't fit)
 *   Joy            FURY 72%       Fury    the Joy / Fury needle: a Gauge (3 px, 1 px border, label and percentage above)
 *   [====green=====|=====red=====]  ^
 *   Starbolt  [Meter ========-]   64      the Starbolt bar (the user's "Meter" style)
 *   +----+----+----+----+----+----+       R G Z X C V: cooldowns; a move she can't pay for is greyed (Supernova until full Fury);
 *   | R  | G  | Z  | X  | C  | V  |       yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+
 * </pre>
 * Under the crosshair while Shift+R or Supernova charges: a small charge Gauge. Hold Left-Alt for the move list. H stays the power
 * selector (no H / N boxes).
 */
public final class StarfireHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	static final int GREEN_TEXT = 0xFF7CFF6A;
	static final int PURPLE_TEXT = 0xFFD58CFF;
	static final int FURY_TEXT = 0xFFFF6A3A;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private StarfireHud() {
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
		StarfireState s = Starfire.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		chargeGauge(g, mc, p, s, partial);

		Font font = mc.font;
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, x0, y0);

		// Starbolt
		int by = y0 - 8;
		float bolt = s.starbolt / StarfireConfig.STARBOLT_MAX;
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.starfire.starbolt"), bolt, 0x5CFF5A,
				String.valueOf(Math.round(s.starbolt)), bolt < 0.15f ? VanillaHud.RED : VanillaHud.WHITE);
		// the Joy / Fury Gauge
		by -= 14;
		moodGauge(g, font, x0, by, WIDTH, s, mc.level.getGameTime() + partial);
		// the title (clear of the Gauge's label line)
		by -= 15;
		String title = Component.translatable("hud.projecthero.starfire.title").getString();
		g.drawString(font, font.plainSubstrByWidth(title, WIDTH), x0, by - 2, PURPLE_TEXT, true);
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		if (s.flying) {
			tags.add(new Tag(Component.translatable("hud.projecthero.starfire.flying").getString(), GREEN_TEXT));
		}
		if (s.radiance) {
			tags.add(new Tag(Component.translatable("hud.projecthero.starfire.radiance").getString(), 0xFFFFF0A0));
		}
		if (s.beams) {
			tags.add(new Tag(Component.translatable("hud.projecthero.starfire.beams").getString(), GREEN_TEXT));
		}
		if (Starfire.bondedWith(p) != null) {
			tags.add(new Tag(Component.translatable("hud.projecthero.starfire.bond").getString() + " " + clock((int) (s.bondUntil - mc.level.getGameTime())),
					PURPLE_TEXT));
		}
		if (Starfire.sun(p) == Starfire.Sun.DIRECT) {
			tags.add(new Tag(Component.translatable("hud.projecthero.starfire.sun").getString(), 0xFFFFD24A));
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
				String[] ids = StarfireAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.starfire.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.starfire.ability." + ids[1]) : null));
			}
			rows.add(MoveListOverlay.inline("2xSpace", Component.translatable("projecthero.starfire.ability.flight")));
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}
	}

	/**
	 * The Joy / Fury needle as a Gauge: the label line above (Joy left, the reading in the middle, Fury right), then a 3 px bar
	 * with a 1 px border shading from green (Joy) through white to orange-red (Fury), a centre tick and the needle.
	 */
	static void moodGauge(GuiGraphics g, Font font, int x0, int by, int width, StarfireState s, float time) {
		String joy = Component.translatable("hud.projecthero.starfire.joy").getString();
		String fury = Component.translatable("hud.projecthero.starfire.fury").getString();
		int pct = Math.round(Math.abs(s.mood));
		boolean full = s.mood >= StarfireConfig.FURY_FULL;
		String reading = s.mood < -0.5f ? joy + " " + pct + "%" : s.mood > 0.5f ? (full ? fury.toUpperCase() + "!" : fury + " " + pct + "%") : "-";
		int readingCol = s.mood < -0.5f ? GREEN_TEXT : s.mood > 0.5f ? FURY_TEXT : VanillaHud.GREY;
		if (full && ((int) (time / 4) % 2 == 0)) {
			readingCol = 0xFFFFE0A0;
		}
		g.drawString(font, joy, x0, by - 9, GREEN_TEXT, true);
		g.drawString(font, fury, x0 + width - font.width(fury), by - 9, FURY_TEXT, true);
		String rd = font.plainSubstrByWidth(reading, Math.max(10, width - font.width(joy) - font.width(fury) - 8));
		g.drawString(font, rd, x0 + width / 2 - font.width(rd) / 2, by - 9, readingCol, true);
		// the bar: 1 px border, 3 px inside
		int bx0 = x0, bx1 = x0 + width, byTop = by;
		g.fill(bx0, byTop, bx1, byTop + 5, 0xFF000000);
		int inner = width - 2;
		for (int i = 0; i < inner; i++) {
			float t = i / (float) (inner - 1); // 0 Joy .. 1 Fury
			int col = t < 0.5f ? lerp(0x3CE04A, 0xE8E8E8, t * 2f) : lerp(0xE8E8E8, 0xFF4A20, (t - 0.5f) * 2f);
			// dim the side the needle isn't on
			float needleT = (s.mood + 100f) / 200f;
			boolean lit = needleT < 0.5f ? (t >= needleT && t <= 0.5f) : (t <= needleT && t >= 0.5f);
			if (!lit) {
				col = lerp(col, 0x202020, 0.65f);
			}
			g.fill(bx0 + 1 + i, byTop + 1, bx0 + 2 + i, byTop + 4, 0xFF000000 | col);
		}
		int mid = bx0 + 1 + inner / 2;
		g.fill(mid, byTop - 1, mid + 1, byTop + 6, 0xFF808080);
		int nx = bx0 + 1 + Math.round((inner - 1) * Mth.clamp((s.mood + 100f) / 200f, 0f, 1f));
		g.fill(nx - 1, byTop - 2, nx + 2, byTop + 7, 0xFF000000);
		g.fill(nx, byTop - 1, nx + 1, byTop + 6, full ? 0xFFFFE070 : 0xFFFFFFFF);
	}

	static int lerp(int a, int b, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int gr = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (gr << 8) | bl;
	}

	private static void keys(GuiGraphics g, LocalPlayer p, StarfireState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = StarfireAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = StarfireAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, StarfireAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, StarfireAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, StarfireAbilityManager.maxCooldown(other)));
			boolean usable = StarfireAbilityManager.allowed(p, shown)
					&& (p.getAbilities().instabuild || s.starbolt + 1.0e-3f >= StarfireAbilityManager.cost(p, shown));
			boolean active = (slot == AbilitySlot.SLOT_5 && s.radiance) || (slot == AbilitySlot.SLOT_2 && s.beams)
					|| (slot == AbilitySlot.SLOT_1 && s.chargeMove == StarfireState.CHARGE_BURST)
					|| (slot == AbilitySlot.SLOT_4 && (s.chargeMove == StarfireState.CHARGE_NOVA || Starfire.fullFury(p)));
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** Under the crosshair while Star Burst or Supernova charges: a 60 px Gauge with its label above. */
	private static void chargeGauge(GuiGraphics g, Minecraft mc, LocalPlayer p, StarfireState s, float partial) {
		int move = Starfire.charging(p);
		if (move == StarfireState.CHARGE_NONE) {
			return;
		}
		float e = Starfire.chargeElapsed(p) + partial;
		int target = move == StarfireState.CHARGE_NOVA ? StarfireConfig.NOVA_CHARGE_TICKS : StarfireConfig.BURST_CHARGE_TICKS;
		float f = Mth.clamp(e / target, 0f, 1f);
		int w = 60, cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
		String label = Component.translatable(move == StarfireState.CHARGE_NOVA ? "hud.projecthero.starfire.nova_charge"
				: "hud.projecthero.starfire.burst_charge").getString() + " " + Math.round(f * 100) + "%";
		label = mc.font.plainSubstrByWidth(label, Math.min(200, g.guiWidth() - 20));
		g.drawString(mc.font, label, cx - mc.font.width(label) / 2, y - 10, move == StarfireState.CHARGE_NOVA ? 0xFFFFD24A : GREEN_TEXT, true);
		g.fill(cx - w / 2, y, cx + w / 2, y + 5, 0xFF000000);
		g.fill(cx - w / 2 + 1, y + 1, cx - w / 2 + 1 + Math.round((w - 2) * f), y + 4, move == StarfireState.CHARGE_NOVA ? 0xFFFFD24A : 0xFF5CFF5A);
	}
}
