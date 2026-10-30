package com.projecthero.mod.client.omnitrix;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1 polish (user: "move the aliens HUDS to the right side of the screen like all the other HUDS and also make them look
 * like all the other huds"): the Omnitrix HUD, bottom-right in the mono style of the Quirk / Miraculous / Green Lantern HUDs.
 * <pre>
 *   Hydration [==========----] 12s        a kit's own rows ({@link AlienClientKit#hud}: Ripjaws' hydration)
 *   Heatblast                             the alien
 *   Time      [==============--] 2:41     the time left (green, red in the last 15 s)
 *   [R] [G] [Z] [X] [C] [V]               the kit keys: the plain move's cooldown, the Shift move's while sneaking
 * </pre>
 * Human with the watch: a compact title row ({@code Omnitrix} and the selected alien), the red Recharge bar while it recharges,
 * and while the watch is up the scroll / click hint wrapped above it. No Omnitrix logo; v0.17.1 (user): V is a kit key with its
 * own box, N turns back (no box; the Left-Alt list keeps a line for it), never H. The transformation flashes are {@link OmnitrixTransformFx};
 * during a Containment Pod extraction a red SELF-DESTRUCT warning flashes at the top of the screen.
 */
public final class OmnitrixHud {
	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5), as the Quirk HUD (v0.17.1 user: every alien has a V move). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3, AbilitySlot.SLOT_6,
			AbilitySlot.SLOT_5 };
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 4;
	/** The HUD's width: the six key boxes. */
	public static final int WIDTH = BOX * 6 + GAP * 5;

	private static final int BOX_BG = 0xD0080808;
	private static final int BOX_BG_DIM = 0x80080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int KEY_DIM = 0xFF5A5A5A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int COOLDOWN = 0xB0000000;
	private static final int BAR_BG = 0xC0141414;
	private static final int GREEN = 0xFF4CE04A;
	private static final int RED = 0xFFFF3A2E;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private OmnitrixHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || !Omnitrix.hasPower(p)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		OmnitrixTransformFx.drawScreen(g, p, s, now, partial);
		extraction(g, mc, p, now);
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		// on a narrow window the HUD would sit on the right end of the hotbar (and the food bar above it): lift it clear
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41;
		}
		int hudTop = s.transformed() ? alien(g, mc, p, s, now, x0, bottom, partial) : human(g, mc, p, s, now, x0, bottom);
		// the shared Left-Alt move list, stacked above the HUD like every other power's
		if (MoveListOverlay.held()) {
			MoveListOverlay.draw(g, x0 + WIDTH, hudTop, moveRows(mc, p, s, now));
		}
	}

	/** Transformed: the keys, the time bar, the alien's name and the kit's rows. Returns the HUD's top. */
	private static int alien(GuiGraphics g, Minecraft mc, LocalPlayer p, OmnitrixState s, long now, int x0, int bottom, float partial) {
		Aliens.Alien a = Aliens.byId(s.active);
		AlienKit kit = Aliens.kit(s.active);
		if (a == null || kit == null) {
			return bottom;
		}
		int y0 = bottom - BOX;
		keys(g, mc, p, kit, a, x0, y0);
		int left = Omnitrix.displayLeft(s, now);
		boolean powerDown = Omnitrix.poweringDown(s);
		boolean warn = left <= OmnitrixConfig.WARN_TICKS || powerDown;
		float blink = Omnitrix.beepBlink(s, now, partial);
		int colour = powerDown ? 0xFF801010 : warn ? pulse(RED, 0xFFB01810) : GREEN;
		int by = y0 - 4 - BAR_H;
		meter(g, mc, x0, by, Component.translatable("hud.projecthero.omnitrix.meter.time"), left / (float) OmnitrixConfig.TRANSFORM_TICKS, colour,
				OmnitrixInfo.clock(left), warn ? RED : NAME);
		if (powerDown) {
			// v0.17.1: the power-down's five beeps -- the bar flashes red on each, five pips light up one per beep
			int barX = x0 + mc.font.width(Component.translatable("hud.projecthero.omnitrix.meter.time")) + 4;
			int valueW = Math.max(mc.font.width("0:00"), mc.font.width(OmnitrixInfo.clock(left)));
			int barW = Math.max(20, WIDTH - (barX - x0) - valueW - 3);
			if (blink > 0.01f) {
				g.fill(barX - 1, by - 1, barX + barW + 1, by + BAR_H + 1, ((int) (blink * 230) << 24) | 0xFF2A1A);
			}
			int done = Omnitrix.beepsDone(s, now);
			for (int i = 0; i < Omnitrix.POWER_DOWN_BEEPS.length; i++) {
				int px = barX + Math.round((i + 0.5f) * barW / Omnitrix.POWER_DOWN_BEEPS.length) - 1;
				g.fill(px, by + 1, px + 2, by + BAR_H - 1, i < done ? 0xFFFFD0C0 : 0xFF401010);
			}
		}
		// v0.17.1 (user: "i dont want to see the icon and alien name"): no name row; the kit's own rows sit right over the time bar
		int ty = by - 2;
		int top = ty;
		AlienClientKit extra = AlienClients.of(s.active);
		if (extra != null && extra.hudHeight() > 0) {
			extra.hud(g, p, x0, ty - 2, WIDTH, partial);
			top -= extra.hudHeight() + 2;
		}
		return top;
	}

	/** Human with the watch: only the recharge bar while it recharges (v0.17.1). Returns the HUD's top. */
	private static int human(GuiGraphics g, Minecraft mc, LocalPlayer p, OmnitrixState s, long now, int x0, int bottom) {
		int y = bottom;
		int recharge = (int) Math.max(0L, s.rechargeUntil - now);
		if (recharge > 0) {
			int by = y - BAR_H - 1;
			int total = Math.max(recharge, recharge > OmnitrixConfig.EARLY_RECHARGE_TICKS ? OmnitrixConfig.RECHARGE_TICKS : OmnitrixConfig.EARLY_RECHARGE_TICKS);
			meter(g, mc, x0, by, Component.translatable("hud.projecthero.omnitrix.meter.recharge"), recharge / (float) total, RED,
					(recharge + 19) / 20 + "s", NAME);
			y = by - 4;
		}
		// v0.17.1 (user: "there should be no UI basically until the player transforms but just keep the timers"): human, only the
		// recharge bar shows -- no title row, no selected alien, no scroll / click hint
		return y;
	}

	/**
	 * One meter row in the mod's standard style: the label, the bar (a 1-px border, the fill with its top highlight) and the
	 * value right-aligned; {@code by} is the bar's top. Shared with the alien kits' own rows.
	 */
	public static void meter(GuiGraphics g, Minecraft mc, int x0, int by, Component label, float frac, int colour, String value, int valueColour) {
		int labelW = mc.font.width(label);
		int valueW = Math.max(mc.font.width("0:00"), mc.font.width(value));
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, WIDTH - labelW - 4 - valueW - 3);
		g.drawString(mc.font, label, x0, by - 2, NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + BAR_H + 1, BORDER);
		g.fill(barX, by, barX + barW, by + BAR_H, BAR_BG);
		int fill = Math.round(barW * Mth.clamp(frac, 0f, 1f));
		if (fill > 0) {
			g.fill(barX, by, barX + fill, by + BAR_H, colour);
			g.fill(barX, by, barX + fill, by + 1, 0x60FFFFFF);
		}
		g.drawString(mc.font, value, x0 + WIDTH - mc.font.width(value), by - 2, valueColour, false);
	}

	/** A 12x12 dial icon (the 64-px texture, linear-filtered) with its top-left at (x, y); {@code red} the red-diamond variant. */
	static void dialIcon(GuiGraphics g, String alienId, boolean red, int x, int y) {
		if (alienId == null || alienId.isEmpty()) {
			return;
		}
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		g.blit(OmnitrixWatch.icon(alienId, red), x, y, 12, 12, 0f, 0f, 64, 64, 64, 64);
		com.mojang.blaze3d.systems.RenderSystem.disableBlend();
	}

	/** An alien's colour lifted toward white until it reads on any sky (Ripjaws' slate, Ghostfreak's grey). */
	private static int readable(int rgb) {
		int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
		float lum = (0.3f * r + 0.59f * gg + 0.11f * b) / 255f;
		float k = Mth.clamp((0.72f - lum) / 0.72f, 0f, 0.7f) + 0.15f;
		r = (int) Mth.lerp(k, r, 255);
		gg = (int) Mth.lerp(k, gg, 255);
		b = (int) Mth.lerp(k, b, 255);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}

	private static int pulse(int a, int b) {
		float k = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 100000L) / 1000f * 8f);
		int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
		int gg = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
		int bb = (int) Mth.lerp(k, a & 255, b & 255);
		return 0xFF000000 | (r << 16) | (gg << 8) | bb;
	}

	/** The six kit keys. */
	private static void keys(GuiGraphics g, Minecraft mc, LocalPlayer p, AlienKit kit, Aliens.Alien a, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			AlienMove tap = kit.moveOn(slot, false, false);
			AlienMove sh = kit.moveOn(slot, true, false);
			AlienMove hold = kit.moveOn(slot, false, true);
			AlienMove shown = shift && sh != null ? sh : tap != null ? tap : hold;
			boolean usable = shown != null;
			int cd = shown == null ? 0 : kit.cooldownLeft(p, shown.id());
			int max = shown == null ? 1 : Math.max(1, shown.cooldown());
			g.fill(x, y0, x + BOX, y0 + BOX, usable ? BOX_BG : BOX_BG_DIM);
			if (kit.keyActive(p, slot)) {
				// v0.17.1: a toggle that is on (Heatblast's flight...): the box lit in the alien's colour, a bright rim
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x66000000 | (a.colour() & 0xFFFFFF));
				g.renderOutline(x, y0, BOX, BOX, 0xFF000000 | a.colour());
			} else {
				g.renderOutline(x, y0, BOX, BOX, BORDER);
			}
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, usable ? KEY : KEY_DIM, false);
			if (shift && sh != null) {
				// the Shift view: a small bar along the bottom of the box
				g.fill(x + 3, y0 + BOX - 3, x + BOX - 3, y0 + BOX - 2, 0xFF000000 | a.colour());
			}
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/**
	 * The Left-Alt rows. Human: the scroll wheel and the click while the watch is up, the selected alien, and R (raise / lower,
	 * or the recharge left) last. Alien: every kit key's tap / Shift / hold move (V included) from the kit's declarations on the
	 * player's live keys, the attack / use / sneak moves, and N (turn back) last. Never H.
	 */
	static List<MoveListOverlay.Row> moveRows(Minecraft mc, LocalPlayer p, OmnitrixState s, long now) {
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		String v = MoveListOverlay.key(AbilitySlot.SLOT_8); // v0.17.1 (user): N turns back
		AlienKit kit = s.transformed() ? Aliens.kit(s.active) : null;
		if (kit == null) {
			if (s.selecting) {
				rows.add(MoveListOverlay.Row.of(Component.translatable("hud.projecthero.omnitrix.alt.key.scroll").getString(),
						Component.translatable("hud.projecthero.omnitrix.alt.scroll"), null));
				rows.add(MoveListOverlay.Row.of(Component.translatable("hud.projecthero.omnitrix.alt.key.click").getString(),
						Component.translatable("hud.projecthero.omnitrix.alt.slap"), null));
			}
			Aliens.Alien sel = Aliens.byId(s.selected);
			if (sel != null) {
				rows.add(MoveListOverlay.Row.of(Component.translatable("hud.projecthero.omnitrix.alt.key.dial").getString(),
						Component.translatable("hud.projecthero.omnitrix.alt.selected", OmnitrixInfo.iconName(sel)), null));
			}
			int recharge = (int) Math.max(0L, s.rechargeUntil - now);
			// v0.17.1 (user): R raises / lowers the watch (V only turns an alien back)
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_1),
					Component.translatable(s.selecting ? "hud.projecthero.omnitrix.alt.lower" : "hud.projecthero.omnitrix.alt.raise"),
					null, recharge > 0 ? Component.translatable("hud.projecthero.omnitrix.alt.recharging", (recharge + 19) / 20) : null));
			return rows;
		}
		for (AbilitySlot slot : OmnitrixInfo.KIT_SLOTS) {
			AlienMove tap = kit.moveOn(slot, false, false);
			AlienMove shift = kit.moveOn(slot, true, false);
			AlienMove hold = kit.moveOn(slot, false, true);
			if (tap == null && shift == null && hold == null) {
				continue;
			}
			Component holdText = hold == null ? null : Component.translatable("hud.projecthero.omnitrix.alt.hold", moveName(s.active, hold));
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), tap != null ? moveName(s.active, tap) : holdText != null ? holdText
					: Component.literal("-"), shift == null ? null : moveName(s.active, shift), tap != null ? holdText : null));
		}
		// the vanilla keys: grouped by the key the declaration names first (Attack / Use / Sneak)
		vanillaRow(rows, kit, s.active, "Attack", mc.options.keyAttack);
		vanillaRow(rows, kit, s.active, "Use", mc.options.keyUse);
		vanillaRow(rows, kit, s.active, "Sneak", mc.options.keyShift);
		rows.add(MoveListOverlay.Row.of(v, Component.translatable("hud.projecthero.omnitrix.alt.revert"), null,
				Component.translatable("hud.projecthero.omnitrix.alt.time_left", OmnitrixInfo.clock(Omnitrix.displayLeft(s, now)))));
		return rows;
	}

	private static void vanillaRow(List<MoveListOverlay.Row> rows, AlienKit kit, String alien, String word, net.minecraft.client.KeyMapping key) {
		net.minecraft.network.chat.MutableComponent names = null;
		for (AlienMove m : kit.moves()) {
			if (m.onSlot() || m.vanillaKey() == null || !m.vanillaKey().startsWith(word)) {
				continue;
			}
			if (names == null) {
				names = Component.empty().append(moveName(alien, m));
			} else {
				names.append(Component.literal(" / ")).append(moveName(alien, m));
			}
		}
		if (names != null) {
			// a short rebinding ("F") shows as itself; the default mouse buttons / Left Shift read as the action ("Attack")
			String label = key.getTranslatedKeyMessage().getString();
			if (label.length() > 5) {
				label = Component.translatable("hud.projecthero.omnitrix.alt.key." + word.toLowerCase(java.util.Locale.ROOT)).getString();
			}
			rows.add(MoveListOverlay.Row.of(label, names, null));
		}
	}

	private static Component moveName(String alien, AlienMove m) {
		return Component.translatable("projecthero.omnitrix." + alien + "." + m.id());
	}

	private static void extraction(GuiGraphics g, Minecraft mc, LocalPlayer p, long now) {
		Integer t = p.getAttached(OmnitrixAttachments.EXTRACTION);
		if (t == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		boolean on = (now / 6) % 2 == 0;
		// the red edges
		int edge = on ? 0x70FF1A10 : 0x40FF1A10;
		g.fill(0, 0, w, 6, edge);
		g.fill(0, h - 6, w, h, edge);
		g.fill(0, 0, 6, h, edge);
		g.fill(w - 6, 0, w, h, edge);
		int bw = Math.min(236, w - 16);
		int x = w / 2 - bw / 2;
		int y = 22;
		Component title = Component.translatable("hud.projecthero.omnitrix.self_destruct");
		Component sub = Component.translatable("hud.projecthero.omnitrix.extracting", (Math.max(0, OmnitrixConfig.EXTRACTION_TICKS - t) + 19) / 20);
		List<FormattedCharSequence> titleLines = mc.font.split(title, bw - 8);
		List<FormattedCharSequence> subLines = mc.font.split(sub, bw - 8);
		int bh = 4 + titleLines.size() * 10 + subLines.size() * 10 + 8;
		g.fill(x, y, x + bw, y + bh, 0xE0180404);
		g.renderOutline(x, y, bw, bh, on ? RED : 0xFF801010);
		int ly = y + 4;
		for (FormattedCharSequence line : titleLines) {
			g.drawCenteredString(mc.font, line, w / 2, ly, on ? RED : 0xFFFFB0A0);
			ly += 10;
		}
		for (FormattedCharSequence line : subLines) {
			g.drawCenteredString(mc.font, line, w / 2, ly, 0xFFFFFFFF);
			ly += 10;
		}
		float frac = Mth.clamp(t / (float) OmnitrixConfig.EXTRACTION_TICKS, 0f, 1f);
		g.fill(x + 6, y + bh - 8, x + bw - 6, y + bh - 4, 0xFF300808);
		g.fill(x + 6, y + bh - 8, x + 6 + (int) ((bw - 12) * frac), y + bh - 4, on ? RED : 0xFFC02018);
	}
}
