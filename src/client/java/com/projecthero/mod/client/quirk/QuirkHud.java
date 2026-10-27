package com.projecthero.mod.client.quirk;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;
import com.projecthero.mod.quirk.meter.QuirkMeters;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.16.7: the Quirk HUD (bottom-right), in the black-and-gray "mono" style of the reworked powers (like
 * {@code MiraculousHud}):
 * <pre>
 *   OVERLOADED 6s                         (while overloaded: the kit's overload line, wrapped to the row's width)
 *   Hellflame                             the Quirk (gray while Erased)
 *   Heat [=========-----] 63              the kit's meter in the Quirk's colour (pulses red while overloaded)
 *   [R] [G] [Z] [X] [C] [V]               the six keys: the plain move's cooldown, or the Shift move's while sneaking
 *                                         (a small bar under the box marks the Shift view); lit while that key's move
 *                                         runs; dimmed while overloaded (except keys that work then) or Erased
 * </pre>
 * Hold Left-Alt for the move names ({@link MoveListOverlay}: plain and Shift per key). Also, for EVERY player (any
 * power): the big red ERASED banner while the local player is Erased ({@link PowerSuppression#isSuppressed}).
 *
 * <p>v0.16.7: a kit may draw its own status block instead of the meter row ({@link QuirkClientKit#drawStatus}: One For All's
 * Output / limit gauge and limbs), and the keys read the kit's own cooldowns, locks and move names
 * ({@link QuirkKit#cooldownLeft}, {@link QuirkKit#keyLocked}, {@link QuirkKit#abilityLangKey}); a locked key is crossed out.
 *
 * <p>v0.17.1 framework ({@code docs/QUIRK_FRAMEWORK_0171.md}): the meter is drawn in its three bands ({@link #drawBands}:
 * ticks, the sweet-spot shimmer, the red-zone pulse, the push past 100) with the band's name on the name line, or by the
 * kit's own widget ({@link QuirkClientKit#drawMeterWidget}); the gold <b>Plus Ultra</b> hairline sits between the meter and
 * the keys (pulsing when full, the C box lit gold and ringed while C is held); the finisher of an open combo route flashes;
 * a kit's hold charge rings its key ({@link QuirkClientKit#holdCharge}).
 */
public final class QuirkHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 4;

	private static final int BOX_BG = 0xD0080808;
	private static final int BOX_BG_DIM = 0x80080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int KEY_DIM = 0xFF5A5A5A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int COOLDOWN = 0xB0000000;
	private static final int BAR_BG = 0xC0141414;
	private static final int OVERLOAD = 0xFFFF5A4A;
	private static final int ERASED_RED = 0xFFE03030;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5), as on the Miraculous HUD. */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private QuirkHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		QuirkCameraFx.drawVignette(g); // v0.17.1 framework: any player can be sent a vignette
		boolean erased = PowerSuppression.isSuppressed(mc.player);
		if (erased) {
			erasedBanner(g, mc);
		}
		Quirk q = Quirks.quirk(mc.player);
		if (q == null) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		QuirkKit kit = QuirkKits.of(q);
		boolean overloaded = Quirks.overloaded(mc.player);
		boolean shift = forceShift || mc.player.isShiftKeyDown();
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;
		// v0.16.7: on a narrow window the keys would sit on the right end of the hotbar (and the food bar above it) -- lift the
		// whole HUD clear of both
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			y0 = g.guiHeight() - 41 - BOX;
		}

		// v0.16.7: a kit's own status block (One For All) in place of the meter row
		QuirkClientKit clientKit = QuirkClient.kitFor(q);
		// v0.17.1 framework: the Plus Ultra hairline just above the keys (the seven kit Quirks)
		boolean ultra = PlusUltra.applies(mc.player);
		int meterBase = ultra ? y0 - 4 : y0;
		if (ultra) {
			drawPlusUltra(g, mc, x0, y0 - 3, totalW, erased);
		}
		int custom = clientKit == null ? -1 : clientKit.drawStatus(g, mc.player, x0, meterBase - 3, totalW);
		int ty;
		if (custom >= 0) {
			ty = custom - 11;
		} else {
			ty = drawMeter(g, mc, q, clientKit, x0, meterBase, totalW, overloaded, erased, now);
		}
		g.drawString(mc.font, Quirks.quirkName(q), x0, ty, erased ? HINT : NAME, false);
		// v0.17.1 framework: the band's name at the right of the name line, in the band's colour
		QuirkMeterSpec spec = QuirkMeters.spec(q);
		if (spec != null && !erased) {
			QuirkBand band = QuirkMeters.clientBand(mc.player);
			Component bn = Component.translatable("projecthero.quirk.band." + q.id + "." + spec.names[band.ordinal()]);
			int bw = mc.font.width(bn);
			if (mc.font.width(Quirks.quirkName(q)) + 6 + bw <= totalW) {
				g.drawString(mc.font, bn, x0 + totalW - bw, ty, bandColour(spec, band, q, now), false);
			}
		}

		// the overload line
		int hudTop = ty;
		if (overloaded) {
			Component kitLine = clientKit == null ? null : clientKit.overloadLine(mc.player); // v0.16.7: e.g. Dark Shadow's Berserk
			Component note = kitLine != null ? kitLine : Component.translatable("hud.projecthero.quirk.overloaded", (Quirks.overloadLeft(mc.player) + 19) / 20);
			List<FormattedCharSequence> lines = mc.font.split(note, totalW);
			int ny = ty - 2 - lines.size() * 9;
			hudTop = ny;
			for (FormattedCharSequence line : lines) {
				g.drawString(mc.font, line, x0, ny, OVERLOAD, false);
				ny += 9;
			}
		}
		drawKeys(g, mc, q, kit, x0, y0, totalW, hudTop, overloaded, erased, shift);
	}

	/**
	 * v0.17.1 framework: what a meter widget draws -- the bar's box, the synced value, its band and the spec (null for a
	 * Quirk without one), the Quirk's colour, the game time and the overload / Erased state.
	 */
	public record MeterView(int x, int y, int w, int h, float value, QuirkBand band, QuirkMeterSpec spec, int colour, long now, boolean overloaded,
			boolean erased) {
	}

	/** The generic meter row (label, banded bar, value) above the keys; returns the y of the Quirk's name line. */
	private static int drawMeter(GuiGraphics g, Minecraft mc, Quirk q, QuirkClientKit clientKit, int x0, int y0, int totalW, boolean overloaded,
			boolean erased, long now) {
		float meter = Mth.clamp(Quirks.clientMeter(mc.player), 0f, 100f);
		int by = y0 - 4 - BAR_H;
		Component label = Component.translatable("projecthero.quirk.meter." + q.meterId);
		int labelW = mc.font.width(label);
		boolean beyond = QuirkMeters.beyond(mc.player);
		String value = beyond ? "100+" : String.valueOf(Math.round(meter));
		int valueW = mc.font.width("100+");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, totalW - labelW - 4 - valueW - 3);
		int colour = q.meterColour | 0xFF000000;
		if (overloaded && (now / 5) % 2 == 0) {
			colour = OVERLOAD;
		}
		if (erased) {
			colour = 0xFF505050;
		}
		g.drawString(mc.font, label, x0, by - 2, erased ? HINT : NAME, false);
		QuirkMeterSpec spec = QuirkMeters.spec(q);
		QuirkBand band = QuirkMeters.clientBand(mc.player);
		MeterView view = new MeterView(barX, by, barW, BAR_H, meter, band, spec, colour, now, overloaded, erased);
		if (clientKit == null || !clientKit.drawMeterWidget(g, mc.player, view)) {
			g.fill(barX - 1, by - 1, barX + barW + 1, by + BAR_H + 1, BORDER);
			g.fill(barX, by, barX + barW, by + BAR_H, BAR_BG);
			drawBands(g, view);
		}
		g.drawString(mc.font, value, x0 + totalW - mc.font.width(value), by - 2, erased ? HINT : NAME, false);
		return by - 13;
	}

	/**
	 * v0.17.1 framework: the shared banded fill for a kit's own status block ({@link QuirkClientKit#drawStatus}) -- the bar's
	 * box (border and background already drawn by the kit), {@code value} 0..100 and the kit's fill colour.
	 */
	public static void bandedFill(GuiGraphics g, net.minecraft.world.entity.player.Player player, Quirk q, int x, int y, int w, int h, float value,
			int colour) {
		drawBands(g, new MeterView(x, y, w, h, value, QuirkMeters.clientBand(player), QuirkMeters.spec(q), colour | 0xFF000000,
				player.level().getGameTime(), Quirks.overloaded(player), PowerSuppression.isSuppressed(player)));
	}

	/** The danger colour (the red zone's pulse). */
	private static final int DANGER = 0xFFFF4A3A;
	private static final int GOLD = 0xFFFFC83A;

	/**
	 * v0.17.1 framework: the shared banded fill on any bar box -- the band tints and tick marks (SWEET start, RED start), the
	 * fill in the Quirk's colour, the sweet-spot shimmer sweeping across it, the red-zone pulse (toward the danger colour, and
	 * the box edge flashing), and past 100 a white-hot flicker with the push's time left as a sliver underneath. A
	 * {@code SPENDS} meter (Focus / Matter) pulses in its LOW band instead and glows gold in its top band.
	 */
	public static void drawBands(GuiGraphics g, MeterView v) {
		int x = v.x(), y = v.y(), w = v.w(), h = v.h();
		QuirkMeterSpec spec = v.spec();
		float ms = (System.currentTimeMillis() % 100000L) / 1000f;
		boolean spends = spec != null && spec.direction == QuirkMeterSpec.Direction.SPENDS;
		if (spec != null && !v.erased()) {
			// band tints: the danger zone faintly red, a SPENDS meter's top band faintly gold
			int sx = x + Math.round(w * spec.sweetFrom / 100f), rx = x + Math.round(w * spec.redFrom / 100f);
			if (spends) {
				g.fill(x, y, sx, y + h, 0x30FF3020);
				g.fill(rx, y, x + w, y + h, 0x28FFC83A);
			} else {
				g.fill(rx, y, x + w, y + h, 0x38FF3020);
			}
		}
		int fill = Math.round(w * Mth.clamp(v.value(), 0f, 100f) / 100f);
		int colour = v.colour();
		boolean danger = spec != null && spec.channelIsMeter && !v.erased() && spec.dangerous(v.value());
		boolean beyond = v.band() == QuirkBand.BEYOND;
		if (danger && !v.overloaded()) {
			float pulse = 0.5f + 0.5f * Mth.sin(ms * 9f);
			colour = lerpArgb(pulse * 0.65f, colour, DANGER);
		}
		if (beyond) {
			float flick = 0.5f + 0.5f * Mth.sin(ms * 23f);
			colour = lerpArgb(0.55f + 0.35f * flick, colour, 0xFFFFFFFF);
		}
		if (fill > 0) {
			g.fill(x, y, x + fill, y + h, colour);
			g.fill(x, y, x + fill, y + 1, 0x60FFFFFF);
			if (v.band() == QuirkBand.SWEET && !v.erased() && !v.overloaded()) {
				// the sweet-spot shimmer: a soft highlight sweeping along the fill
				float pos = (ms * 0.7f) % 1.4f - 0.2f;
				int cx = x + Math.round(fill * pos);
				for (int k = -4; k <= 4; k++) {
					int px = cx + k;
					if (px >= x && px < x + fill) {
						int a = (int) (110 * (1f - Math.abs(k) / 5f));
						g.fill(px, y, px + 1, y + h, (a << 24) | 0xFFFFFF);
					}
				}
			}
			if (spends && v.band() == QuirkBand.RED && !v.erased()) {
				int a = (int) (60 + 50 * (0.5f + 0.5f * Mth.sin(ms * 4f)));
				g.fill(x, y, x + fill, y + 1, (a << 24) | 0xFFE080);
			}
		}
		if (spec != null) {
			// the band ticks
			for (float t : new float[] { spec.sweetFrom, spec.redFrom }) {
				int tx = x + Math.round(w * t / 100f);
				g.fill(tx, y - 1, tx + 1, y + h + 1, 0xC0D8D8D8);
			}
		}
		if (danger && !v.overloaded() && (System.currentTimeMillis() / 250L) % 2 == 0) {
			g.renderOutline(x - 1, y - 1, w + 2, h + 2, DANGER);
		}
		if (beyond) {
			Minecraft mc = Minecraft.getInstance();
			float left = mc.player == null ? 0f : QuirkMeters.beyondLeft(mc.player);
			g.fill(x, y + h + 1, x + Math.round(w * left), y + h + 2, 0xFFFFF0C0);
			g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFFFFE6A0);
		}
	}

	private static int bandColour(QuirkMeterSpec spec, QuirkBand band, Quirk q, long now) {
		return switch (band) {
			case LOW -> spec.direction == QuirkMeterSpec.Direction.SPENDS ? DANGER : HINT;
			case SWEET -> q.meterColour | 0xFF000000;
			case RED -> spec.direction == QuirkMeterSpec.Direction.SPENDS ? GOLD : DANGER;
			case BEYOND -> (now / 3) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFE6A0;
		};
	}

	/** The Plus Ultra hairline (gold), pulsing when full. */
	private static void drawPlusUltra(GuiGraphics g, Minecraft mc, int x0, int y, int totalW, boolean erased) {
		float pu = PlusUltra.clientGauge(mc.player);
		g.fill(x0, y, x0 + totalW, y + 1, 0x90202020);
		int fill = Math.round(totalW * pu / PlusUltra.MAX);
		if (fill <= 0) {
			return;
		}
		int colour = erased ? 0xFF606060 : GOLD;
		if (pu >= PlusUltra.MAX && !erased) {
			float ms = (System.currentTimeMillis() % 100000L) / 1000f;
			float pulse = 0.5f + 0.5f * Mth.sin(ms * 6f);
			colour = lerpArgb(pulse * 0.7f, GOLD, 0xFFFFFFFF);
			g.fill(x0, y - 1, x0 + totalW, y, (int) (pulse * 120) << 24 | 0xFFC83A);
			g.fill(x0, y + 1, x0 + totalW, y + 2, (int) (pulse * 120) << 24 | 0xFFC83A);
		}
		g.fill(x0, y, x0 + fill, y + 1, colour);
	}

	private static int lerpArgb(float t, int a, int b) {
		t = Mth.clamp(t, 0f, 1f);
		int aa = (int) Mth.lerp(t, (a >>> 24) & 255, (b >>> 24) & 255);
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bb = (int) Mth.lerp(t, a & 255, b & 255);
		return (aa << 24) | (r << 16) | (gg << 8) | bb;
	}

	/** A progress ring round a key box: the outline filled clockwise from the top-left by {@code progress} (0..1). */
	public static void ring(GuiGraphics g, int x, int y, int size, float progress, int colour) {
		int per = (size - 1) * 4;
		int n = Math.round(per * Mth.clamp(progress, 0f, 1f));
		for (int i = 0; i < n; i++) {
			int px, py;
			int side = i / (size - 1), k = i % (size - 1);
			switch (side) {
				case 0 -> {
					px = x + k;
					py = y;
				}
				case 1 -> {
					px = x + size - 1;
					py = y + k;
				}
				case 2 -> {
					px = x + size - 1 - k;
					py = y + size - 1;
				}
				default -> {
					px = x;
					py = y + size - 1 - k;
				}
			}
			g.fill(px, py, px + 1, py + 1, colour);
			// a 2-px ring: the inner pixel too
			int ix = px == x ? px + 1 : px == x + size - 1 ? px - 1 : px;
			int iy = py == y ? py + 1 : py == y + size - 1 ? py - 1 : py;
			g.fill(ix, iy, ix + 1, iy + 1, colour);
		}
	}

	/** The Left-Alt move list and the six keys. */
	private static void drawKeys(GuiGraphics g, Minecraft mc, Quirk q, QuirkKit kit, int x0, int y0, int totalW, int hudTop, boolean overloaded,
			boolean erased, boolean shift) {
		// Left-Alt: the shared move list (plain and Shift per key), stacked above the whole HUD
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = kit.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(keyLabel(slot),
						Component.translatable(kit.abilityLangKey(ids[0])),
						ids.length > 1 ? Component.translatable(kit.abilityLangKey(ids[1])) : null));
			}
			if (!rows.isEmpty()) {
				MoveListOverlay.draw(g, x0 + totalW, hudTop, rows);
			}
		}

		// v0.17.1 framework: Plus Ultra on the C box, the open combo window's finisher, the kit's hold charges
		boolean ultra = PlusUltra.applies(mc.player);
		boolean ultraFull = ultra && !erased && PlusUltra.clientGauge(mc.player) >= PlusUltra.MAX;
		float ultraHold = ultra ? PlusUltra.clientHold(mc.player) : -1f;
		QuirkCombos.Route window = erased ? null : QuirkCombos.openWindow(mc.player);
		QuirkClientKit clientKit = QuirkClient.kitFor(q);
		long ms = System.currentTimeMillis();

		// the six keys
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = kit.idsOf(slot);
			String id = ids.length == 0 ? null : ids[Math.min(ids.length - 1, shift ? 1 : 0)];
			int cd = id == null ? 0 : kit.cooldownLeft(mc.player, id);
			int max = id == null ? 1 : Math.max(1, kit.maxCooldown(id));
			boolean locked = id != null && kit.keyLocked(mc.player, slot, shift);
			boolean usable = !erased && id != null && !locked && (!overloaded || kit.usableWhileOverloaded(slot, shift));
			boolean active = !erased && kit.active(mc.player, slot);
			g.fill(x, y0, x + BOX, y0 + BOX, usable ? BOX_BG : BOX_BG_DIM);
			g.renderOutline(x, y0, BOX, BOX, active ? (q.meterColour | 0xFF000000) : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, usable ? KEY : KEY_DIM, false);
			if (shift && ids.length > 1) {
				// the Shift view: a small bar along the bottom of the box
				g.fill(x + 3, y0 + BOX - 3, x + BOX - 3, y0 + BOX - 2, usable ? (q.meterColour | 0xFF000000) : KEY_DIM);
			}
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (locked && !erased && cd <= 0 && isInjuryLock(mc.player, q)) {
				// v0.16.7: a broken limb -- a red cross over the key
				g.drawCenteredString(mc.font, "x", x + BOX / 2, y0 + BOX / 2 - 4, OVERLOAD);
			}
			if (slot == AbilitySlot.SLOT_6 && ultraFull) {
				// Plus Ultra full: the C box lit gold, pulsing; held: a gold charge ring fills round it
				float pulse = 0.5f + 0.5f * Mth.sin((ms % 100000L) / 1000f * 6f);
				g.renderOutline(x, y0, BOX, BOX, lerpArgb(pulse * 0.6f, GOLD, 0xFFFFFFFF));
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, ((int) (30 + 40 * pulse) << 24) | 0xFFC83A);
				if (ultraHold >= 0f) {
					ring(g, x, y0, BOX, ultraHold, 0xFFFFFFFF);
				}
			}
			if (window != null && id != null && !erased && java.util.Arrays.asList(ids).contains(window.finisher) && (ms / 125L) % 2 == 0) {
				// a combo window is open: the finisher's key flashes
				g.renderOutline(x - 1, y0 - 1, BOX + 2, BOX + 2, 0xFFFFFFFF);
			}
			float charge = clientKit == null || erased ? -1f : clientKit.holdCharge(mc.player, slot);
			if (charge >= 0f) {
				ring(g, x, y0, BOX, charge, q.meterColour | 0xFF000000);
			}
			if (erased) {
				// a red slash through every key
				for (int k = 2; k < BOX - 2; k++) {
					g.fill(x + k, y0 + BOX - 1 - k, x + k + 1, y0 + BOX - k, 0xA0E03030);
				}
			}
		}
	}

	/** The big red ERASED banner (any power), a third of the way down the screen, with a thin red edge on the screen. */
	private static void erasedBanner(GuiGraphics g, Minecraft mc) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = mc.level.getGameTime();
		int edge = (now / 10) % 2 == 0 ? 0x50E03030 : 0x38E03030;
		g.fill(0, 0, w, 2, edge);
		g.fill(0, h - 2, w, h, edge);
		g.fill(0, 0, 2, h, edge);
		g.fill(w - 2, 0, w, h, edge);

		Component big = Component.translatable("hud.projecthero.erased");
		float scale = 3f;
		int bigW = (int) (mc.font.width(big) * scale);
		int cy = h / 4;
		var pose = g.pose();
		pose.pushPose();
		pose.translate((w - bigW) / 2f, cy, 0f);
		pose.scale(scale, scale, 1f);
		g.drawString(mc.font, big, 0, 0, ERASED_RED, true);
		pose.popPose();

		int left = PowerSuppression.clientRemaining(mc.player);
		Component sub = left > 60
				? Component.translatable("hud.projecthero.erased.timed", (left + 19) / 20)
				: Component.translatable("hud.projecthero.erased.sub");
		List<FormattedCharSequence> lines = mc.font.split(sub, Math.min(220, w - 32));
		int sy = cy + (int) (9 * scale) + 3;
		for (FormattedCharSequence line : lines) {
			g.drawCenteredString(mc.font, line, w / 2, sy, 0xFFF0B0B0);
			sy += 10;
		}
	}

	/** The lock is an injury, not just the Base Form (only One For All locks keys). */
	private static boolean isInjuryLock(net.minecraft.world.entity.player.Player player, Quirk q) {
		return q == Quirk.ONE_FOR_ALL && com.projecthero.mod.allmight.AllMight.isFullPower(player);
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
