package com.projecthero.mod.client.quirk;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.hero.visual.MutationMeters;
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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.16.7: the Quirk HUD (bottom-right). v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this
 * to all the powers"): drawn from the game's own pieces on the shared column ({@link VanillaHud.Stack}):
 * <pre>
 *   OVERLOADED 6s                         (while overloaded: the kit's overload line, wrapped to the column's width)
 *   Hellflame                 Sweet spot  the Quirk (grey while Erased), the meter's band at the right in the band's colour
 *   Heat [boss bar =====|===|--]  63      the kit's meter: a boss bar in the Quirk's colour with the band ticks, the sweet-spot
 *                                         shimmer, the red-zone pulse and the push past 100 (or the kit's own widget)
 *   Plus Ultra [Hairline ======]  80%     the gold Plus Ultra gauge (the seven kit Quirks), pulsing when full
 *   +--+--+--+--+--+--+                   R G Z X C V: the shown move's cooldown (the Shift move's while sneaking: yellow keys),
 *   |R |G |Z |X |C |V |                   the other move's as the durability bar; the selection frame while that key's move
 *   +--+--+--+--+--+--+                   runs; grey keys while overloaded (except keys that work then) or Erased
 * </pre>
 * Hold Left-Alt for the move names ({@link MoveListOverlay}: plain and Shift per key). Also, for EVERY player (any
 * power): the big red ERASED banner while the local player is Erased ({@link PowerSuppression#isSuppressed}).
 *
 * <p>v0.16.7: a kit may draw its own status block instead of the meter row ({@link QuirkClientKit#drawStatus}: One For All's
 * Output / limit gauge and limbs), and the keys read the kit's own cooldowns, locks and move names
 * ({@link QuirkKit#cooldownLeft}, {@link QuirkKit#keyLocked}, {@link QuirkKit#abilityLangKey}); a broken limb crosses its key out.
 *
 * <p>v0.17.1 framework ({@code docs/QUIRK_FRAMEWORK_0171.md}): the meter's three bands ({@link #drawBands} for kits' own bars),
 * or the kit's own widget ({@link QuirkClientKit#drawMeterWidget}); Plus Ultra full lights the C key gold and holding C fills
 * it; the finisher of an open combo route flashes; a kit's hold charge fills its key ({@link QuirkClientKit#holdCharge}).
 */
public final class QuirkHud {
	/** The boss bar's height. */
	private static final int BAR_H = 5;

	private static final int OVERLOAD = 0xFFFF5A4A;
	private static final int ERASED_RED = 0xFFE03030;

	/** Keys left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5), as on the Miraculous HUD. */
	private static final AbilitySlot[] SLOTS = VanillaHud.ORDER;

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
		// the shared column (it lifts itself clear of the hotbar and food bar on a narrow window)
		VanillaHud.Stack stack = VanillaHud.Stack.bottomRight(g, SLOTS.length);
		QuirkClientKit clientKit = QuirkClient.kitFor(q);

		// the six keys
		stack.keys(SLOTS.length);
		drawKeys(stack, mc, q, kit, clientKit, overloaded, erased, shift);

		// v0.17.1 framework: the Plus Ultra gauge just above the keys (the seven kit Quirks)
		if (PlusUltra.applies(mc.player)) {
			drawPlusUltra(stack, mc, erased);
		}

		// v0.16.7: a kit's own status block (One For All) in place of the meter row
		int custom = clientKit == null ? -1 : clientKit.drawStatus(g, mc.player, stack.x0, stack.top() - 3, stack.width);
		if (custom >= 0) {
			stack.reserve(stack.top() - (custom - 1));
		} else {
			drawMeter(stack, mc, q, clientKit, overloaded, erased, now);
		}

		// the Quirk's name, and (v0.17.1 framework) the band's name at the right of the name line, in the band's colour
		Component name = Quirks.quirkName(q);
		stack.title(name, erased ? VanillaHud.GREY : VanillaHud.WHITE);
		QuirkMeterSpec spec = QuirkMeters.spec(q);
		if (spec != null && !erased) {
			QuirkBand band = QuirkMeters.clientBand(mc.player);
			Component bn = Component.translatable("projecthero.quirk.band." + q.id + "." + spec.names[band.ordinal()]);
			int bw = mc.font.width(bn);
			if (mc.font.width(name) + 6 + bw <= stack.width) {
				g.drawString(mc.font, bn, stack.x0 + stack.width - bw, stack.top(), bandColour(spec, band, q, now), true);
			}
		}

		// the overload line
		if (overloaded) {
			Component kitLine = clientKit == null ? null : clientKit.overloadLine(mc.player); // v0.16.7: e.g. Dark Shadow's Berserk
			Component note = kitLine != null ? kitLine : Component.translatable("hud.projecthero.quirk.overloaded", (Quirks.overloadLeft(mc.player) + 19) / 20);
			stack.wrapped(note, OVERLOAD);
		}
		moveList(stack, q, kit);
	}

	/**
	 * v0.17.1 framework: what a meter widget draws -- the bar's box, the synced value, its band and the spec (null for a
	 * Quirk without one), the Quirk's colour, the game time and the overload / Erased state.
	 */
	public record MeterView(int x, int y, int w, int h, float value, QuirkBand band, QuirkMeterSpec spec, int colour, long now, boolean overloaded,
			boolean erased) {
	}

	/**
	 * The generic meter row above the keys: the label, the bar (a boss bar with the bands over it, or the kit's own widget in the
	 * same box) and the value, laid out like {@link VanillaHud#meter}.
	 */
	private static void drawMeter(VanillaHud.Stack stack, Minecraft mc, Quirk q, QuirkClientKit clientKit, boolean overloaded, boolean erased,
			long now) {
		GuiGraphics g = stack.g;
		float meter = Mth.clamp(Quirks.clientMeter(mc.player), 0f, 100f);
		boolean beyond = QuirkMeters.beyond(mc.player);
		String value = beyond ? "100+" : String.valueOf(Math.round(meter));
		int valueW = Math.max(mc.font.width("0:00"), mc.font.width("100+"));
		Component label = Component.translatable("projecthero.quirk.meter." + q.meterId);
		int room = stack.width - valueW - 3 - 4 - 20;
		if (mc.font.width(label) > room) {
			label = Component.literal(mc.font.plainSubstrByWidth(label.getString(), room - mc.font.width(".")) + ".");
		}
		int labelW = mc.font.width(label);
		// the row sits where a meter row would (8 px over the keys, 12 over another row), then the title keeps its usual gap
		int before = stack.top();
		int by = before - (stack.stripY() == before ? 8 : 12);
		stack.reserve(before - (by - 4));
		int barX = stack.x0 + labelW + 4;
		int barW = Math.max(20, stack.width - labelW - 4 - valueW - 3);
		int colour = q.meterColour | 0xFF000000;
		if (overloaded && (now / 5) % 2 == 0) {
			colour = OVERLOAD;
		}
		if (erased) {
			colour = 0xFF505050;
		}
		int text = erased ? VanillaHud.GREY : VanillaHud.WHITE;
		g.drawString(mc.font, label, stack.x0, by - 2, text, true);
		QuirkMeterSpec spec = QuirkMeters.spec(q);
		QuirkBand band = QuirkMeters.clientBand(mc.player);
		MeterView view = new MeterView(barX, by - 1, barW, BAR_H, meter, band, spec, colour, now, overloaded, erased);
		if (clientKit == null || !clientKit.drawMeterWidget(g, mc.player, view)) {
			vanillaBands(g, view);
		}
		g.drawString(mc.font, value, stack.x0 + stack.width - mc.font.width(value), by - 2, text, true);
	}

	/**
	 * v0.17.3: the meter's bands on the vanilla boss bar -- the Meter-style bar in the Quirk's colour (pulsing toward red in the
	 * danger zone, flickering white past 100), the danger zone faintly tinted, the sweet-spot shimmer, the band ticks, the edge
	 * flashing in the red zone, and past 100 the push's time left as a sliver underneath.
	 */
	private static void vanillaBands(GuiGraphics g, MeterView v) {
		int x = v.x(), y = v.y(), w = v.w(), h = v.h();
		QuirkMeterSpec spec = v.spec();
		float ms = (System.currentTimeMillis() % 100000L) / 1000f;
		boolean spends = spec != null && spec.direction == QuirkMeterSpec.Direction.SPENDS;
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
		float frac = Mth.clamp(v.value(), 0f, 100f) / 100f;
		VanillaHud.bar(g, x, y, w, frac, colour, MutationMeters.Style.METER);
		if (spec != null && !v.erased()) {
			int sx = x + Math.round(w * spec.sweetFrom / 100f), rx = x + Math.round(w * spec.redFrom / 100f);
			if (spends) {
				g.fill(x, y, sx, y + h, 0x30FF3020);
				g.fill(rx, y, x + w, y + h, 0x28FFC83A);
			} else {
				g.fill(rx, y, x + w, y + h, 0x38FF3020);
			}
		}
		int fill = Math.round(w * frac);
		if (fill > 0 && v.band() == QuirkBand.SWEET && !v.erased() && !v.overloaded()) {
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
		if (fill > 0 && spends && v.band() == QuirkBand.RED && !v.erased()) {
			int a = (int) (60 + 50 * (0.5f + 0.5f * Mth.sin(ms * 4f)));
			g.fill(x, y, x + fill, y + 1, (a << 24) | 0xFFE080);
		}
		if (spec != null) {
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

	/** The Plus Ultra gauge: a gold Hairline row, the value pulsing gold to white when full. */
	private static void drawPlusUltra(VanillaHud.Stack stack, Minecraft mc, boolean erased) {
		float pu = PlusUltra.clientGauge(mc.player);
		float frac = Mth.clamp(pu / PlusUltra.MAX, 0f, 1f);
		int valueColour = erased ? VanillaHud.GREY : GOLD;
		if (pu >= PlusUltra.MAX && !erased) {
			float pulse = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 100000L) / 1000f * 6f);
			valueColour = lerpArgb(pulse * 0.7f, GOLD, 0xFFFFFFFF);
		}
		stack.meter(Component.translatable("projecthero.quirk.info.plus_ultra"), frac, erased ? 0xFF606060 : GOLD, Math.round(frac * 100) + "%",
				valueColour, MutationMeters.Style.HAIRLINE);
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
			case LOW -> spec.direction == QuirkMeterSpec.Direction.SPENDS ? DANGER : VanillaHud.GREY;
			case SWEET -> q.meterColour | 0xFF000000;
			case RED -> spec.direction == QuirkMeterSpec.Direction.SPENDS ? GOLD : DANGER;
			case BEYOND -> (now / 3) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFE6A0;
		};
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

	/** The Left-Alt move list (plain and Shift per key, the hold move, the ultimate and the combo routes), above the whole HUD. */
	private static void moveList(VanillaHud.Stack stack, Quirk q, QuirkKit kit) {
		if (!MoveListOverlay.held()) {
			return;
		}
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (AbilitySlot slot : SLOTS) {
			String[] ids = kit.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			// v0.17.1 (menus): the key's hold move too
			String hold = com.projecthero.mod.quirk.QuirkInfo.holdOf(kit, slot);
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
					Component.translatable(kit.abilityLangKey(ids[0])),
					ids.length > 1 ? Component.translatable(kit.abilityLangKey(ids[1])) : null,
					hold == null ? null : Component.translatable("hud.projecthero.move_list.hold", Component.translatable(kit.abilityLangKey(hold)))));
		}
		if (q == Quirk.DARK_SHADOW) {
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(7), Component.translatable("projecthero.quirk.info.hide_dark_shadow"), null));
		}
		if (kit.hasUltimate()) {
			// v0.17.1 (menus): one compact line for the ultimate, and one per combo route
			rows.add(MoveListOverlay.Row.of(Component.translatable("hud.projecthero.quirk.move_list.ultimate_key", VanillaHud.keyLabel(AbilitySlot.SLOT_6)).getString(),
					Component.translatable(kit.ultimateLangKey(q)), null));
		}
		for (QuirkCombos.Route r : QuirkCombos.routes(q)) {
			rows.add(MoveListOverlay.Row.of(Component.translatable("hud.projecthero.quirk.move_list.combo").getString(),
					Component.translatable(r.nameKey()), null,
					Component.translatable("hud.projecthero.quirk.move_list.route", Component.translatable(kit.abilityLangKey(r.opener)),
							Component.translatable(kit.abilityLangKey(r.finisher)))));
		}
		stack.moveList(rows);
	}

	/** The six keys, with Plus Ultra on the C key, the open combo window's finisher, the kit's hold charges and the Erased slash. */
	private static void drawKeys(VanillaHud.Stack stack, Minecraft mc, Quirk q, QuirkKit kit, QuirkClientKit clientKit, boolean overloaded,
			boolean erased, boolean shift) {
		GuiGraphics g = stack.g;
		boolean ultra = PlusUltra.applies(mc.player);
		boolean ultraFull = ultra && !erased && PlusUltra.clientGauge(mc.player) >= PlusUltra.MAX;
		float ultraHold = ultra ? PlusUltra.clientHold(mc.player) : -1f;
		QuirkCombos.Route window = erased ? null : QuirkCombos.openWindow(mc.player);
		long ms = System.currentTimeMillis();
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = kit.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			boolean showShift = shift && ids.length > 1;
			String id = ids[showShift ? 1 : 0];
			String other = ids.length > 1 ? ids[showShift ? 0 : 1] : null;
			int cd = kit.cooldownLeft(mc.player, id);
			float frac = Math.min(1f, cd / (float) Math.max(1, kit.maxCooldown(id)));
			float otherFrac = other == null ? 0f : Math.min(1f, kit.cooldownLeft(mc.player, other) / (float) Math.max(1, kit.maxCooldown(other)));
			boolean locked = kit.keyLocked(mc.player, slot, shift);
			boolean usable = !erased && !locked && (!overloaded || kit.usableWhileOverloaded(slot, shift));
			boolean active = !erased && kit.active(mc.player, slot);
			// the 16x16 item cell of this key, and its 20x20 slot
			int ix = stack.stripX() + 3 + i * VanillaHud.SLOT, iy = stack.stripY() + 3;
			if (slot == AbilitySlot.SLOT_6 && ultraFull) {
				// Plus Ultra full: the C key lit gold, pulsing (holding C fills it, below)
				float pulse = 0.5f + 0.5f * Mth.sin((ms % 100000L) / 1000f * 6f);
				g.fill(ix, iy, ix + 16, iy + 16, ((int) (40 + 50 * pulse) << 24) | 0xFFC83A);
			}
			stack.slot(i, VanillaHud.keyLabel(slot), usable, showShift, frac, VanillaHud.seconds(cd), otherFrac, active);
			if (slot == AbilitySlot.SLOT_6 && ultraFull) {
				float pulse = 0.5f + 0.5f * Mth.sin((ms % 100000L) / 1000f * 6f);
				g.renderOutline(ix - 2, iy - 2, 20, 20, lerpArgb(pulse * 0.6f, GOLD, 0xFFFFFFFF));
				if (ultraHold >= 0f) {
					stack.hold(i, ultraHold);
				}
			}
			if (locked && !erased && cd <= 0 && isInjuryLock(mc.player, q)) {
				// v0.16.7: a broken limb -- a red cross over the key
				over(g, mc, "x", ix + 11, iy + 8, OVERLOAD);
			}
			if (window != null && !erased && java.util.Arrays.asList(ids).contains(window.finisher) && (ms / 125L) % 2 == 0) {
				// a combo window is open: the finisher's key flashes
				g.renderOutline(ix - 2, iy - 2, 20, 20, 0xFFFFFFFF);
			}
			float charge = clientKit == null || erased ? -1f : clientKit.holdCharge(mc.player, slot);
			if (charge >= 0f) {
				stack.hold(i, charge);
			}
			if (erased) {
				// a red slash through every key
				for (int k = 0; k < 16; k++) {
					g.fill(RenderType.guiOverlay(), ix + k, iy + 15 - k, ix + k + 1, iy + 16 - k, 0xA0E03030);
				}
			}
		}
	}

	/** Shadowed text in front of the key's contents (the stack-count layer). */
	private static void over(GuiGraphics g, Minecraft mc, String text, int x, int y, int colour) {
		g.pose().pushPose();
		g.pose().translate(0f, 0f, 200f);
		g.drawString(mc.font, text, x, y, colour, true);
		g.pose().popPose();
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
}
