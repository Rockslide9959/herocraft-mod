package com.projecthero.mod.client.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.speedforce.SpeedForceAbilities;
import com.projecthero.mod.speedforce.SpeedForceAbilityManager;
import com.projecthero.mod.speedforce.SpeedForceColours;
import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceRealm;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Speed Force Speedster's HUD. v0.17.1 (user: "make the regular HUD look the same as regular super speed but
 * add the bars when they show up"): the six keys bottom-right like the plain Speedster's, titled with the mode, with thin
 * bars stacked over them only while they matter (Momentum and the Speed Force in Speed Mode, the charge, Flash Time, the
 * portal, the Remnant, the drain); the big speed panel top-left only in the full Flash Suit.
 * <ul>
 *   <li>top-left: the velocity (blocks a second, big, with mph), the speed gauge up to the top speed (with the sound
 *       barrier marked and the run's top speed), the <b>Momentum</b> gauge, the <b>Speed Force</b> meter, the suit
 *       line and bars for what is running;</li>
 *   <li>bottom-right: the six keys (R G Z X C V -- H and N never get a box) in the mono style: the box fills with the tap
 *       move's cooldown, the strip under it with the Shift move's, the second strip with the hold move's. Hold Left-Alt
 *       for the move names.</li>
 * </ul>
 * v0.17.1 (user: "make the speed force speedster hud colours change depending on what lightning colour the player chose"):
 * every accent -- the titles, the bars, the active borders, the Flash Suit panel -- is drawn in the player's own
 * {@link SpeedForceColours} tones ({@link Palette}).
 */
public final class SpeedForceHud {
	private static final float MPH_PER_BPS = 2.23694f;

	private static final int BG = 0xC0100606;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int DIM = 0xFF9A8F8A;
	private static final int RED = 0xFFFF3A2E;
	private static final int FLASH_BLUE = 0xFF8FD8FF;

	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int STRIP_H = 2;
	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int COOLDOWN = 0xB0000000;
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 }; // v0.17.1 (user): H and N never get a box on an ability HUD

	private static float speed;
	private static float top;
	private static int idleTicks;

	private SpeedForceHud() {
	}

	/**
	 * The HUD's colours from one lightning colour: {@code accent} (titles, bars, borders: the brightest-reading of the
	 * colour's tones), {@code light} (the meter, the ready strips), {@code deep} (the trough the bars run in, the panel's
	 * title band and edge).
	 */
	public record Palette(int accent, int light, int deep, int edge) {
		public static Palette of(int colour) {
			int t = 1;
			while (t < 3 && luminance(SpeedForceColours.tone(colour, t)) < 0.45) {
				t++;
			}
			int accent = 0xFF000000 | SpeedForceColours.tone(colour, t);
			int light = 0xFF000000 | SpeedForceColours.tone(colour, Math.min(3, t + 1));
			int base = SpeedForceColours.tone(colour, t == 1 ? 0 : t - 1);
			int deep = 0xFF000000 | scale(base, 0.28f);
			int edge = 0xFF000000 | scale(SpeedForceColours.tone(colour, t), 0.7f);
			return new Palette(accent, light, deep, edge);
		}

		private static double luminance(int rgb) {
			return (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF)) / 255.0;
		}

		private static int scale(int rgb, float f) {
			int r = Math.round(((rgb >> 16) & 0xFF) * f);
			int g = Math.round(((rgb >> 8) & 0xFF) * f);
			int b = Math.round((rgb & 0xFF) * f);
			return (r << 16) | (g << 8) | b;
		}
	}

	static boolean shows(Player p) {
		if (p == null || !SpeedForceSpeedster.hasPower(p)) {
			return false;
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return exp == null || exp.activePower.isEmpty();
	}

	static void tick(Minecraft mc) {
		Player p = mc.player;
		if (!shows(p) || mc.isPaused()) {
			return;
		}
		double dx = p.getX() - p.xo;
		double dy = p.getY() - p.yo;
		double dz = p.getZ() - p.zo;
		float now = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 20f;
		if (now > 400f) {
			now = speed; // a teleport (Rewind)
		}
		speed = Mth.lerp(0.35f, speed, now);
		if (speed > top) {
			top = speed;
		}
		idleTicks = speed < 1f ? idleTicks + 1 : 0;
		if (idleTicks > 200) {
			top = 0f;
		}
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (mc.options.hideGui || mc.level == null || !shows(p)) {
			return;
		}
		if (com.projecthero.mod.client.flash.FlashSuitHud.replacesSpeedForceHud(p)) {
			return; // v0.17.1 (user): in the full Flash Suit the visor HUD is the one HUD (it draws these keys itself)
		}
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		Palette pal = Palette.of(SpeedForceColours.clamp(s.colour));
		if (SpeedForceSpeedster.suited(p)) {
			renderPanel(g, mc, p, s, now, pal);
		}
		renderKeys(g, mc, p, s, now, pal);
	}

	private static void renderPanel(GuiGraphics g, Minecraft mc, Player p, SpeedForceState s, long now, Palette pal) {
		boolean suit = SpeedForceSpeedster.suited(p);
		float topBps = (float) SpeedForceConfig.topBps(suit);
		boolean flash = s.flashLeft == SpeedForceState.Z_FLASH_TIME;
		boolean portal = s.flashLeft == SpeedForceState.Z_PORTAL; // v0.17.1 (user): Z at 100% -- the portal charge, never Flash Time
		boolean remnant = s.remnantUntil > now;
		boolean drained = s.drained > 0;

		Component mode;
		int modeColour;
		if (SpeedForceRealm.inside(p)) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.realm");
			modeColour = pal.light();
		} else if (portal) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.portal");
			modeColour = pal.light();
		} else if (flash) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.flash_time");
			modeColour = FLASH_BLUE;
		} else if (s.phasing) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.phasing");
			modeColour = 0xFFC8B8FF;
		} else if (s.tornadoLeft > 0) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.tornado");
			modeColour = pal.accent();
		} else if (SpeedForceAbilities.tornadoArms(p)) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.arms");
			modeColour = pal.accent();
		} else if (s.carryId != 0) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.carrying");
			modeColour = pal.accent();
		} else if (speed > 12f) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.run");
			modeColour = speed >= SpeedForceConfig.SONIC_BOOM_BPS ? RED : pal.accent();
		} else {
			mode = Component.translatable("hud.projecthero.speed_force.mode.idle");
			modeColour = DIM;
		}

		int x = 6;
		int y = 6;
		int w = 156;
		int rows = (flash ? 1 : 0) + (portal ? 1 : 0) + (remnant ? 1 : 0) + (drained ? 1 : 0);
		int h = 80 + rows * 9;
		g.fill(x, y, x + w, y + h, BG);
		g.renderOutline(x, y, w, h, pal.edge());
		g.fill(x + 1, y + 1, x + w - 1, y + 12, 0xC0000000 | (pal.deep() & 0xFFFFFF));
		g.drawString(mc.font, Component.literal("⚡ ").withColor(pal.accent() & 0xFFFFFF)
				.append(Component.translatable("hud.projecthero.speed_force.title").withColor(WHITE & 0xFFFFFF)), x + 4, y + 3, WHITE, true);
		g.drawString(mc.font, mode, x + w - 4 - mc.font.width(mode), y + 3, modeColour, true);

		// the velocity, big, with mph beside it
		String bps = String.format(Locale.ROOT, "%.0f", speed);
		g.pose().pushPose();
		g.pose().translate(x + 5, y + 16, 0);
		g.pose().scale(2f, 2f, 1f);
		g.drawString(mc.font, bps, 0, 0, speed >= SpeedForceConfig.SONIC_BOOM_BPS ? RED : pal.accent(), true);
		g.pose().popPose();
		int after = x + 7 + mc.font.width(bps) * 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.bps"), after, y + 17, WHITE, true);
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.mph", String.format(Locale.ROOT, "%.0f", speed * MPH_PER_BPS)),
				after, y + 26, DIM, false);
		Component suitLine = Component.translatable(suit ? "hud.projecthero.speed_force.suit" : "hud.projecthero.speed_force.no_suit");
		g.drawString(mc.font, suitLine, x + w - 5 - mc.font.width(suitLine), y + 17, suit ? pal.accent() : DIM, false);
		Component topLine = Component.translatable("hud.projecthero.speed_force.top", String.format(Locale.ROOT, "%.0f", topBps));
		g.drawString(mc.font, topLine, x + w - 5 - mc.font.width(topLine), y + 26, DIM, false);

		// the speed gauge (0 to the top speed), the sound barrier marked, the run's top speed in red
		int gx = x + 5;
		int gy = y + 37;
		int gw = w - 10;
		g.fill(gx, gy, gx + gw, gy + 5, pal.deep());
		int fill = Math.round(gw * Mth.clamp(speed / topBps, 0f, 1f));
		for (int i = 0; i < fill; i++) {
			g.fill(gx + i, gy, gx + i + 1, gy + 5, lerpColour(pal.light(), pal.accent(), i / (float) gw));
		}
		int sx = gx + Math.round(gw * (float) SpeedForceConfig.SONIC_BOOM_BPS / topBps) - 1;
		g.fill(sx, gy - 1, sx + 1, gy + 6, WHITE);
		if (top > 0.5f) {
			int tx = gx + Math.round(gw * Mth.clamp(top / topBps, 0f, 1f)) - 1;
			g.fill(tx, gy - 2, tx + 1, gy + 7, RED);
		}

		// Momentum: the gauge that sets the speed, with the 25 / 60% tiers marked
		boolean locked = s.surgeUntil > now;
		int ry = gy + 9;
		String pct = String.format(Locale.ROOT, "%.0f%%", s.momentum * 100f);
		g.drawString(mc.font, Component.translatable("hud.projecthero.speed_force.momentum"), gx, ry, pal.accent(), false);
		g.drawString(mc.font, pct, x + w - 5 - mc.font.width(pct), ry, locked ? WHITE : pal.accent(), false);
		int my = ry + 10;
		g.fill(gx, my, gx + gw, my + 4, pal.deep());
		int mfill = Math.round(gw * Mth.clamp(s.momentum, 0f, 1f));
		for (int i = 0; i < mfill; i++) {
			g.fill(gx + i, my, gx + i + 1, my + 4, locked ? WHITE : lerpColour(pal.accent(), pal.light(), i / (float) gw));
		}
		for (float mark : new float[] { 0.25f, 0.60f }) {
			int mx = gx + Math.round(gw * mark) - 1;
			g.fill(mx, my - 1, mx + 1, my + 5, WHITE);
		}

		// the Speed Force meter
		ry = my + 7;
		String meter = String.format(Locale.ROOT, "%.0f", s.meter);
		g.drawString(mc.font, Component.translatable("hud.projecthero.speed_force.meter"), gx, ry, pal.light(), false);
		g.drawString(mc.font, meter, x + w - 5 - mc.font.width(meter), ry, pal.light(), false);
		int ey = ry + 10;
		g.fill(gx, ey, gx + gw, ey + 3, pal.deep());
		g.fill(gx, ey, gx + Math.round(gw * Mth.clamp(s.meter / SpeedForceSpeedster.meterMax(p), 0f, 1f)), ey + 3, drained ? DIM : pal.light());

		// what is running
		ry = ey + 6;
		if (flash) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.flash_time"), s.meter / SpeedForceSpeedster.meterMax(p),
					Component.translatable("hud.projecthero.speed_force.per_second", String.format(Locale.ROOT, "%.0f", SpeedForceConfig.FLASH_COST_PER_SECOND)),
					FLASH_BLUE, pal);
			ry += 9;
		}
		if (portal) {
			float c = Math.min(1f, s.flashTotal / (float) SpeedForceConfig.PORTAL_TICKS);
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.portal"), c,
					Component.literal(String.format(Locale.ROOT, "%.0f%%", c * 100f)), pal.light(), pal);
			ry += 9;
		}
		if (remnant) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.remnant"), (s.remnantUntil - now) / (float) SpeedForceConfig.REMNANT_TICKS,
					seconds(s.remnantUntil - now), pal.accent(), pal);
			ry += 9;
		}
		if (drained) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.drained"), s.drained / (float) SpeedForceConfig.DRAINED_TICKS,
					seconds(s.drained), RED, pal);
		}
	}

	private static void renderKeys(GuiGraphics g, Minecraft mc, Player p, SpeedForceState s, long now, Palette pal) {
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - 3 - totalW;
		int y0 = g.guiHeight() - MARGIN - 3 - 2 * (STRIP_H + 1) - BOX;
		// the title, as the plain Speedster's: the power and the mode
		boolean realm = SpeedForceRealm.inside(p);
		Component mode = Component.translatable(realm ? "hud.projecthero.speed_force.mode.realm"
				: s.chargeStart != 0L ? "hud.projecthero.speed_force.mode.charging"
				: s.flashLeft == SpeedForceState.Z_PORTAL ? "hud.projecthero.speed_force.mode.portal"
				: s.flashLeft == SpeedForceState.Z_FLASH_TIME ? "hud.projecthero.speed_force.mode.flash_time"
				: s.phasing ? "hud.projecthero.speed_force.mode.phasing"
				: s.drained > 0 ? "hud.projecthero.speed_force.mode.drained"
				: s.speedMode ? "hud.projecthero.speed_force.mode.speed" : "hud.projecthero.speed_force.mode.walk");
		Component title = Component.translatable("hud.projecthero.speed_force.title").append(" - ").append(mode);
		int fx1 = x0 + totalW;
		int fy0 = y0 - 12;
		boolean lit = s.speedMode || s.chargeStart != 0L || realm;
		g.drawString(mc.font, title, x0, y0 - 10, s.drained > 0 ? RED : lit ? pal.light() : WHITE, true);
		// the bars, only while they matter, stacked up over the title
		int by = y0 - 20;
		if (s.chargeStart != 0L) {
			float c = Math.min(1f, (now - s.chargeStart) / (float) SpeedForceConfig.CHARGE_TICKS);
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.charge"), c,
					c >= 1f ? Component.translatable("hud.projecthero.speed_force.ready") : Component.literal(String.format(Locale.ROOT, "%.0f%%", c * 100f)),
					c >= 1f ? WHITE : pal.accent(), pal);
			by -= 9;
		}
		if (s.flashLeft == SpeedForceState.Z_PORTAL) {
			float c = Math.min(1f, s.flashTotal / (float) SpeedForceConfig.PORTAL_TICKS);
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.portal"), c,
					Component.literal(String.format(Locale.ROOT, "%.0f%%", c * 100f)), pal.light(), pal);
			by -= 9;
		}
		if (s.flashLeft == SpeedForceState.Z_FLASH_TIME) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.flash_time"), s.meter / SpeedForceSpeedster.meterMax(p),
					Component.translatable("hud.projecthero.speed_force.per_second", String.format(Locale.ROOT, "%.0f", SpeedForceConfig.FLASH_COST_PER_SECOND)),
					FLASH_BLUE, pal);
			by -= 9;
		}
		if (s.remnantUntil > now) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.remnant"), (s.remnantUntil - now) / (float) SpeedForceConfig.REMNANT_TICKS,
					seconds(s.remnantUntil - now), pal.accent(), pal);
			by -= 9;
		}
		if (s.drained > 0) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.drained"), s.drained / (float) SpeedForceConfig.DRAINED_TICKS,
					seconds(s.drained), RED, pal);
			by -= 9;
		}
		if (s.speedMode || s.meter < SpeedForceSpeedster.meterMax(p)) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.meter"), s.meter / SpeedForceSpeedster.meterMax(p),
					Component.literal(String.format(Locale.ROOT, "%.0f", s.meter)), s.drained > 0 ? DIM : pal.light(), pal);
			by -= 9;
		}
		if (s.speedMode) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.momentum"), s.momentum,
					Component.literal(String.format(Locale.ROOT, "%.0f%%", s.momentum * 100f)), s.surgeUntil > now ? WHITE : pal.accent(), pal);
			by -= 9;
		}
		fy0 = Math.min(fy0, by + 9);
		drawKeys(g, mc, p, s, now, pal, x0, y0, fx1, fy0 - 2, false);
	}

	/** v0.17.1: the six key boxes' row width. */
	public static int keysWidth() {
		return BOX * SLOTS.length + GAP * (SLOTS.length - 1);
	}

	/** v0.17.1: the six key boxes stacked as a column ({@link #drawKeys} with {@code column}), its height. */
	public static int keysColumnHeight() {
		return keysHeight() * SLOTS.length + GAP * (SLOTS.length - 1);
	}

	/** v0.17.1: the key boxes' height, the two cooldown strips included. */
	public static int keysHeight() {
		return BOX + 2 * (STRIP_H + 1);
	}

	/**
	 * v0.17.1: the six key boxes (R G Z X C V -- never H or N) at {@code x0, y0}: the box fills with the tap move's
	 * cooldown, the strips under it with the Shift move's and the hold move's; Left Alt lists the moves, right-aligned to
	 * {@code listRight} with its bottom at {@code listBottom}. {@code column}: stacked top to bottom instead of a row (the
	 * Flash Suit visor's right-edge column). Shared by this HUD and the Flash Suit visor.
	 */
	public static void drawKeys(GuiGraphics g, Minecraft mc, Player p, SpeedForceState s, long now, Palette pal, int x0, int y0,
			int listRight, int listBottom, boolean column) {
		int fx1 = listRight;
		int fy0 = listBottom + 2;
		if (com.projecthero.mod.client.gui.MoveListOverlay.held()) {
			List<com.projecthero.mod.client.gui.MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SpeedForceAbilityManager.idsOf(slot);
				String key = com.projecthero.mod.client.gui.MoveListOverlay.key(slot);
				// Z has no tap: Flash Time IS the hold (from the press), its portal the third
				Component plain = slot == AbilitySlot.SLOT_4
						? Component.translatable("hud.projecthero.speed_force.hold", name(ids[0]))
						: name(ids[0]);
				Component hold = Component.translatable("hud.projecthero.speed_force.hold", name(ids[2]));
				rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of(key, plain, name(ids[1]), hold));
			}
			rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of(com.projecthero.mod.client.gui.MoveListOverlay.key(AbilitySlot.SLOT_8),
					name(SpeedForceAbilities.RESCUE), Component.translatable("projecthero.speed_force_speedster.ability.speed_force_menu")));
			com.projecthero.mod.client.gui.MoveListOverlay.draw(g, fx1, fy0 - 2, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = column ? x0 : x0 + i * (BOX + GAP);
			int ky = column ? y0 + i * (keysHeight() + GAP) : y0;
			String[] ids = SpeedForceAbilityManager.idsOf(slot);
			int cd = SpeedForceSpeedster.cooldownRemaining(p, ids[0]);
			int max = Math.max(1, SpeedForceAbilityManager.maxCooldown(ids[0]));
			boolean active = (slot == AbilitySlot.SLOT_1 && s.holdAnim && s.animId == SpeedForceState.ANIM_MASS_WINDUP)
					|| (slot == AbilitySlot.SLOT_2 && (s.tornadoLeft > 0 || (s.holdAnim && s.animId == SpeedForceState.ANIM_THROW_CHARGE)))
					|| (slot == AbilitySlot.SLOT_3 && SpeedForceAbilities.tornadoArms(p))
					|| (slot == AbilitySlot.SLOT_4 && (s.flashLeft > 0 || s.remnantUntil > now))
					|| (slot == AbilitySlot.SLOT_6 && (s.speedMode || s.chargeStart != 0L || s.phasing));
			g.fill(x, ky, x + BOX, ky + BOX, BOX_BG);
			g.renderOutline(x, ky, BOX, BOX, active ? pal.accent() : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, ky + 2, KEY, false);
			if (cd > 0) {
				int hh = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, ky + BOX - 1 - hh, x + BOX - 1, ky + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, ky + BOX / 2 - 4, 0xFFFFFFFF);
			}
			// the Shift move's strip, then the hold move's (Z's: the portal -- its charge while it forms)
			for (int k = 1; k <= 2 && k < ids.length; k++) {
				int sy = ky + BOX + 1 + (k - 1) * (STRIP_H + 1);
				int scd = SpeedForceSpeedster.cooldownRemaining(p, ids[k]);
				int smax = Math.max(1, SpeedForceAbilityManager.maxCooldown(ids[k]));
				float ready = scd <= 0 ? 1f : 1f - Math.min(1f, scd / (float) smax);
				int on = k == 1 ? pal.accent() : pal.light();
				if (slot == AbilitySlot.SLOT_4 && k == 2 && s.flashLeft == SpeedForceState.Z_PORTAL) {
					ready = Math.min(1f, s.flashTotal / (float) SpeedForceConfig.PORTAL_TICKS);
					on = WHITE;
				}
				g.fill(x, sy, x + BOX, sy + STRIP_H, pal.deep());
				g.fill(x, sy, x + Math.round(BOX * ready), sy + STRIP_H, scd <= 0 || on == WHITE ? on : (on & 0x00FFFFFF) | 0x80000000);
			}
		}
	}

	private static Component name(String id) {
		return Component.translatable("projecthero.speed_force_speedster.ability." + id);
	}

	private static Component seconds(long ticks) {
		return Component.literal(String.format(Locale.ROOT, "%ds", (Math.max(0, ticks) + 19) / 20));
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** A labelled hairline bar the width of the key row, its value at the right. */
	private static void thin(GuiGraphics g, Minecraft mc, int x, int y, int w, Component label, float frac, Component value, int colour, Palette pal) {
		g.drawString(mc.font, label, x, y, colour, true);
		int bx = x + mc.font.width(label) + 4;
		int bw = x + w - mc.font.width(value) - 4 - bx;
		if (bw > 4) {
			g.fill(bx, y + 3, bx + bw, y + 5, 0xC0000000 | (pal.deep() & 0xFFFFFF));
			g.fill(bx, y + 3, bx + Math.round(bw * Mth.clamp(frac, 0f, 1f)), y + 5, colour);
		}
		g.drawString(mc.font, value, x + w - mc.font.width(value), y, WHITE, true);
	}

	private static void bar(GuiGraphics g, Minecraft mc, int x, int y, int w, Component label, float frac, Component value, int colour, Palette pal) {
		g.drawString(mc.font, label, x, y, colour, false);
		int bx = x + 62;
		int bw = w - 62 - 26;
		g.fill(bx, y + 3, bx + bw, y + 5, pal.deep());
		g.fill(bx, y + 3, bx + Math.round(bw * Mth.clamp(frac, 0f, 1f)), y + 5, colour);
		g.drawString(mc.font, value, x + w - mc.font.width(value), y, WHITE, false);
	}

	private static int lerpColour(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | (r << 16) | (gr << 8) | bl;
	}
}
