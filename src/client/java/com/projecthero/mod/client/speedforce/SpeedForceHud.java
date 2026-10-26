package com.projecthero.mod.client.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.speedforce.SpeedForceAbilityManager;
import com.projecthero.mod.speedforce.SpeedForceConfig;
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
 * bars stacked over them only while they matter (Momentum and the Speed Force in Speed Mode, the charge, a launch, Flash
 * Time, the Remnant); the big speed panel top-left only in the full Flash Suit (as the plain Speedster's Flash Suit HUD).
 * <ul>
 *   <li>top-left: the velocity (blocks a second, big, with mph), the speed gauge up to the top speed (with the sound
 *       barrier marked and the run's top speed), the <b>Momentum</b> gauge, the <b>Speed Force</b> meter, the suit
 *       line (the +50% and the top speed it gives) and bars for what is running (Surge, Flash Time, the Remnant);</li>
 *   <li>bottom-right: the six keys (R G Z X C V) in the mono style: the box fills with the plain move's cooldown, the strip
 *       under it with the Shift move's. Hold Left-Alt for the move names.</li>
 * </ul>
 */
public final class SpeedForceHud {
	private static final float MPH_PER_BPS = 2.23694f;

	private static final int BG = 0xC0100606;
	private static final int EDGE = 0xFFB01E1E;
	private static final int GOLD = 0xFFFFD23A;
	private static final int RED = 0xFFFF3A2E;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int DIM = 0xFF9A8F8A;
	private static final int METER = 0xFFFFF0A0;

	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int SHIFT_H = 2;
	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFFFD23A;
	private static final int KEY = 0xFFB4B4B4;
	private static final int COOLDOWN = 0xB0000000;
	private static final int SHIFT_READY = 0xFFC89A2A;
	private static final int SHIFT_CD = 0xFF38301A;
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 }; // v0.17.1 (user): H and N never get a box on an ability HUD

	private static float speed;
	private static float top;
	private static int idleTicks;

	private SpeedForceHud() {
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
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (SpeedForceSpeedster.suited(p)) {
			renderPanel(g, mc, p, s, now);
		}
		renderKeys(g, mc, p, s, now);
	}

	private static void renderPanel(GuiGraphics g, Minecraft mc, Player p, SpeedForceState s, long now) {
		boolean suit = SpeedForceSpeedster.suited(p);
		float topBps = (float) SpeedForceConfig.topBps(suit);
		boolean surge = s.surgeUntil > now;
		boolean flash = s.flashLeft > 0;
		boolean remnant = s.remnantUntil > now;

		Component mode;
		int modeColour;
		if (surge) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.surge");
			modeColour = WHITE;
		} else if (flash) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.flash_time");
			modeColour = 0xFF8FD8FF;
		} else if (s.phasing) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.phasing");
			modeColour = 0xFFC8B8FF;
		} else if (s.tornadoLeft > 0) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.tornado");
			modeColour = GOLD;
		} else if (s.carryId != 0) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.carrying");
			modeColour = GOLD;
		} else if (speed > 12f) {
			mode = Component.translatable("hud.projecthero.speed_force.mode.run");
			modeColour = speed >= SpeedForceConfig.SONIC_BOOM_BPS ? RED : GOLD;
		} else {
			mode = Component.translatable("hud.projecthero.speed_force.mode.idle");
			modeColour = DIM;
		}

		int x = 6;
		int y = 6;
		int w = 156;
		int rows = (surge ? 1 : 0) + (flash ? 1 : 0) + (remnant ? 1 : 0);
		int h = 80 + rows * 9;
		g.fill(x, y, x + w, y + h, BG);
		g.renderOutline(x, y, w, h, EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + 12, 0xC0601010);
		g.drawString(mc.font, Component.literal("⚡ ").withColor(GOLD & 0xFFFFFF)
				.append(Component.translatable("hud.projecthero.speed_force.title").withColor(WHITE & 0xFFFFFF)), x + 4, y + 3, WHITE, true);
		g.drawString(mc.font, mode, x + w - 4 - mc.font.width(mode), y + 3, modeColour, true);

		// the velocity, big, with mph beside it
		String bps = String.format(Locale.ROOT, "%.0f", speed);
		g.pose().pushPose();
		g.pose().translate(x + 5, y + 16, 0);
		g.pose().scale(2f, 2f, 1f);
		g.drawString(mc.font, bps, 0, 0, speed >= SpeedForceConfig.SONIC_BOOM_BPS ? RED : GOLD, true);
		g.pose().popPose();
		int after = x + 7 + mc.font.width(bps) * 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.bps"), after, y + 17, WHITE, true);
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.mph", String.format(Locale.ROOT, "%.0f", speed * MPH_PER_BPS)),
				after, y + 26, DIM, false);
		// the suit line, right-aligned beside the speed
		Component suitLine = Component.translatable(suit ? "hud.projecthero.speed_force.suit" : "hud.projecthero.speed_force.no_suit");
		g.drawString(mc.font, suitLine, x + w - 5 - mc.font.width(suitLine), y + 17, suit ? GOLD : DIM, false);
		Component topLine = Component.translatable("hud.projecthero.speed_force.top", String.format(Locale.ROOT, "%.0f", topBps));
		g.drawString(mc.font, topLine, x + w - 5 - mc.font.width(topLine), y + 26, DIM, false);

		// the speed gauge (0 to the top speed), the sound barrier marked, the run's top speed in red
		int gx = x + 5;
		int gy = y + 37;
		int gw = w - 10;
		g.fill(gx, gy, gx + gw, gy + 5, 0xFF2A1010);
		int fill = Math.round(gw * Mth.clamp(speed / topBps, 0f, 1f));
		for (int i = 0; i < fill; i++) {
			g.fill(gx + i, gy, gx + i + 1, gy + 5, lerpColour(0xFFFFE07A, 0xFFFF2A1A, i / (float) gw));
		}
		int sx = gx + Math.round(gw * (float) SpeedForceConfig.SONIC_BOOM_BPS / topBps) - 1;
		g.fill(sx, gy - 1, sx + 1, gy + 6, WHITE);
		if (top > 0.5f) {
			int tx = gx + Math.round(gw * Mth.clamp(top / topBps, 0f, 1f)) - 1;
			g.fill(tx, gy - 2, tx + 1, gy + 7, RED);
		}

		// Momentum: the gauge that sets the speed, with the 25 / 60% tiers marked
		int ry = gy + 9;
		String pct = String.format(Locale.ROOT, "%.0f%%", s.momentum * 100f);
		g.drawString(mc.font, Component.translatable("hud.projecthero.speed_force.momentum"), gx, ry, GOLD, false);
		g.drawString(mc.font, pct, x + w - 5 - mc.font.width(pct), ry, surge ? WHITE : GOLD, false);
		int my = ry + 10;
		g.fill(gx, my, gx + gw, my + 4, 0xFF2A1010);
		int mfill = Math.round(gw * Mth.clamp(s.momentum, 0f, 1f));
		for (int i = 0; i < mfill; i++) {
			g.fill(gx + i, my, gx + i + 1, my + 4, surge ? 0xFFFFFFFF : lerpColour(0xFFFFB02A, 0xFFE0281A, i / (float) gw));
		}
		for (float mark : new float[] { 0.25f, 0.60f }) {
			int mx = gx + Math.round(gw * mark) - 1;
			g.fill(mx, my - 1, mx + 1, my + 5, 0xFFFFFFFF);
		}

		// the Speed Force meter
		ry = my + 7;
		String meter = String.format(Locale.ROOT, "%.0f", s.meter);
		g.drawString(mc.font, Component.translatable("hud.projecthero.speed_force.meter"), gx, ry, METER, false);
		g.drawString(mc.font, meter, x + w - 5 - mc.font.width(meter), ry, METER, false);
		int ey = ry + 10;
		g.fill(gx, ey, gx + gw, ey + 3, 0xFF2A1010);
		g.fill(gx, ey, gx + Math.round(gw * Mth.clamp(s.meter / SpeedForceConfig.METER_MAX, 0f, 1f)), ey + 3, METER);

		// what is running
		ry = ey + 6;
		if (surge) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.surge"), (s.surgeUntil - now) / (float) SpeedForceConfig.SURGE_TICKS,
					s.surgeUntil - now, WHITE);
			ry += 9;
		}
		if (flash) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.flash_time"), s.flashLeft / (float) Math.max(1, s.flashTotal),
					s.flashLeft, 0xFF8FD8FF);
			ry += 9;
		}
		if (remnant) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.speed_force.remnant"), (s.remnantUntil - now) / (float) SpeedForceConfig.REMNANT_TICKS,
					s.remnantUntil - now, GOLD);
		}
	}

	private static void renderKeys(GuiGraphics g, Minecraft mc, Player p, SpeedForceState s, long now) {
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - 3 - totalW;
		int y0 = g.guiHeight() - MARGIN - 3 - SHIFT_H - 1 - BOX;
		// the title, as the plain Speedster's: the power and the mode
		Component mode = Component.translatable(s.chargeStart != 0L ? "hud.projecthero.speed_force.mode.charging"
				: s.flashLeft > 0 ? "hud.projecthero.speed_force.mode.flash_time"
				: s.phasing ? "hud.projecthero.speed_force.mode.phasing"
				: s.speedMode ? "hud.projecthero.speed_force.mode.speed" : "hud.projecthero.speed_force.mode.walk");
		Component title = Component.translatable("hud.projecthero.speed_force.title").append(" - ").append(mode);
		int fx1 = x0 + totalW;
		int fy0 = y0 - 12;
		g.drawString(mc.font, title, x0, y0 - 10, s.speedMode || s.chargeStart != 0L ? 0xFFFFE6A0 : 0xFFFFFFFF, true);
		// the bars, only while they matter, stacked up over the title
		int by = y0 - 20;
		if (s.chargeStart != 0L) {
			float c = Math.min(1f, (now - s.chargeStart) / (float) SpeedForceConfig.CHARGE_TICKS);
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.charge"), c,
					c >= 1f ? Component.translatable("hud.projecthero.speed_force.ready") : Component.literal(String.format(Locale.ROOT, "%.0f%%", c * 100f)),
					c >= 1f ? WHITE : GOLD);
			by -= 9;
		}
		if (s.flashLeft > 0) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.flash_time"), s.flashLeft / (float) Math.max(1, s.flashTotal),
					Component.literal((s.flashLeft + 19) / 20 + "s"), 0xFF8FD8FF);
			by -= 9;
		}
		if (s.remnantUntil > now) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.remnant"), (s.remnantUntil - now) / (float) SpeedForceConfig.REMNANT_TICKS,
					Component.literal((s.remnantUntil - now + 19) / 20 + "s"), GOLD);
			by -= 9;
		}
		if (s.speedMode || s.meter < SpeedForceConfig.METER_MAX) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.meter"), s.meter / SpeedForceConfig.METER_MAX,
					Component.literal(String.format(Locale.ROOT, "%.0f", s.meter)), METER);
			by -= 9;
		}
		if (s.speedMode) {
			thin(g, mc, x0, by, totalW, Component.translatable("hud.projecthero.speed_force.momentum"), s.momentum,
					Component.literal(String.format(Locale.ROOT, "%.0f%%", s.momentum * 100f)), s.surgeUntil > now ? WHITE : 0xFFFFB02A);
			by -= 9;
		}
		fy0 = Math.min(fy0, by + 9);

		if (com.projecthero.mod.client.gui.MoveListOverlay.held()) {
			List<com.projecthero.mod.client.gui.MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SpeedForceAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of(com.projecthero.mod.client.gui.MoveListOverlay.key(slot),
							Component.translatable("projecthero.speed_force_speedster.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.speed_force_speedster.ability." + ids[1]) : null));
				}
			}
			rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of(com.projecthero.mod.client.gui.MoveListOverlay.key(AbilitySlot.SLOT_8),
					Component.translatable("projecthero.speed_force_speedster.ability." + com.projecthero.mod.speedforce.SpeedForceAbilities.RESCUE), null));
			rows.add(com.projecthero.mod.client.gui.MoveListOverlay.Row.of("Hold " + com.projecthero.mod.client.gui.MoveListOverlay.key(AbilitySlot.SLOT_5),
					Component.translatable("projecthero.speed_force_speedster.ability." + com.projecthero.mod.speedforce.SpeedForceAbilities.HEAL), null));
			com.projecthero.mod.client.gui.MoveListOverlay.draw(g, fx1, fy0 - 2, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = SpeedForceAbilityManager.idsOf(slot);
			int cd = SpeedForceSpeedster.cooldownRemaining(p, ids[0]);
			int max = Math.max(1, SpeedForceAbilityManager.maxCooldown(ids[0]));
			boolean hasShift = ids.length > 1; // N (Rescue Run) has no Shift move: Shift+N is Ground-Stick
			int scd = hasShift ? SpeedForceSpeedster.cooldownRemaining(p, ids[1]) : 0;
			int smax = hasShift ? Math.max(1, SpeedForceAbilityManager.maxCooldown(ids[1])) : 1;
			boolean active = (slot == AbilitySlot.SLOT_2 && (s.throwLeft > 0 || s.tornadoLeft > 0))
					|| (slot == AbilitySlot.SLOT_3 && s.phasing)
					|| (slot == AbilitySlot.SLOT_4 && (s.flashLeft > 0 || s.remnantUntil > now))
					|| (slot == AbilitySlot.SLOT_8 && s.carryId != 0)
					|| (slot == AbilitySlot.SLOT_6 && (s.speedMode || s.chargeStart != 0L));
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			// Flash Time's cooldown only starts when it ends; Surge's runs from the start
			if (cd > 0) {
				int hh = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - hh, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (!hasShift) {
				continue;
			}
			int sy = y0 + BOX + 1;
			g.fill(x, sy, x + BOX, sy + SHIFT_H, SHIFT_CD);
			float ready = scd <= 0 ? 1f : 1f - Math.min(1f, scd / (float) smax);
			g.fill(x, sy, x + Math.round(BOX * ready), sy + SHIFT_H, scd <= 0 ? SHIFT_READY : 0xFF7A5E22);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_8) {
			String n = ModKeyBindings.MAX_STEEL_TRANSFORM.getTranslatedKeyMessage().getString();
			return n.length() > 2 ? n.substring(0, 2) : n;
		}
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** A labelled hairline bar the width of the key row, its value at the right. */
	private static void thin(GuiGraphics g, Minecraft mc, int x, int y, int w, Component label, float frac, Component value, int colour) {
		g.drawString(mc.font, label, x, y, colour, true);
		int bx = x + mc.font.width(label) + 4;
		int bw = x + w - mc.font.width(value) - 4 - bx;
		if (bw > 4) {
			g.fill(bx, y + 3, bx + bw, y + 5, 0xC0201008);
			g.fill(bx, y + 3, bx + Math.round(bw * Mth.clamp(frac, 0f, 1f)), y + 5, colour);
		}
		g.drawString(mc.font, value, x + w - mc.font.width(value), y, WHITE, true);
	}

	private static void bar(GuiGraphics g, Minecraft mc, int x, int y, int w, Component label, float frac, float ticksLeft, int colour) {
		g.drawString(mc.font, label, x, y, colour, false);
		int bx = x + 62;
		int bw = w - 62 - 22;
		g.fill(bx, y + 3, bx + bw, y + 5, 0xFF2A1010);
		g.fill(bx, y + 3, bx + Math.round(bw * Mth.clamp(frac, 0f, 1f)), y + 5, colour);
		String secs = String.format(Locale.ROOT, "%.0fs", Math.ceil(ticksLeft / 20f));
		g.drawString(mc.font, secs, x + w - mc.font.width(secs), y, WHITE, false);
	}

	private static int lerpColour(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | (r << 16) | (gr << 8) | bl;
	}
}
