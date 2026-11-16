package com.projecthero.mod.client.sentry;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryAbilityManager;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.data.SentryState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Sentry HUD, bottom-right, on the shared Minecraft-style column ({@link VanillaHud.Stack}):
 * <pre>
 *   Flying  Calm  Shield 0:04  Suit  Trial    status tags (wrap)
 *   THE SENTRY / THE VOID
 *   Sun  [Gauge: notched gold boss bar]  82   fuels the Sentry moves
 *   Void [Hairline: a black 3-px line]   47   the danger (gold edge once past 50, pulsing past 90)
 *   +----+----+----+----+----+----+           R G Z X C V: cooldowns; greyed when the Sun can't pay; yellow while Shift shows Shift
 *                                             (v0.17.5 rework: X = Sonic Boom; flight is double-tap Space, listed in the move list)
 * </pre>
 * The Void's thresholds also draw here, over the whole view: black veins creeping in from the screen edges from 50 Void (thicker at
 * 75), and from 90 a heartbeat pulse of darkness. Hold Left-Alt for the move list. The takeover's own view is
 * {@link VoidTakeoverClient}'s (this HUD steps aside then).
 */
public final class SentryHud {
	private static final AbilitySlot[] SLOTS = VanillaHud.ORDER;
	private static final int GOLD_TEXT = 0xFFFFC83A;
	private static final int VOID_TEXT = 0xFFB088FF;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private SentryHud() {
	}

	static String clock(long ticks) {
		long s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		SentryState s = Sentry.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		if (!s.inTakeover()) {
			thresholds(g, mc, s, partial);
		}
		if (mc.options.hideGui || s.inTakeover()) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean dark = s.voidForm;
		VanillaHud.Stack hud = VanillaHud.Stack.bottomRight(g, SLOTS.length);
		hud.keys(SLOTS.length);
		keys(hud, p, s, dark);
		voidLine(hud, s, now, partial);
		float sunFrac = Mth.clamp(s.sun / SentryConfig.SUN_MAX, 0f, 1f);
		hud.meter(Component.translatable("hud.projecthero.sentry.sun"), sunFrac, 0xFFFFC83A, String.valueOf(Math.round(s.sun)), VanillaHud.WHITE,
				MutationMeters.Style.GAUGE);
		hud.title(Component.translatable(dark ? "hud.projecthero.sentry.title_void" : "hud.projecthero.sentry.title"), dark ? VOID_TEXT : GOLD_TEXT);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (s.flying) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.flying").getString(), VanillaHud.WHITE));
		}
		if (s.calming) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.calm").getString(), 0xFF9AD0FF));
		}
		if (s.shieldUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.shield").getString() + " " + clock(s.shieldUntil - now), GOLD_TEXT));
		}
		if (s.nightfallUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.nightfall").getString() + " " + clock(s.nightfallUntil - now), VOID_TEXT));
		}
		if (s.sunsStart != 0L) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.suns").getString(), GOLD_TEXT));
		}
		if (Sentry.suitWorn(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.suit").getString(), 0xFF7FA8FF));
		}
		if (s.letOutTrialStart != 0L) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.trial").getString(), VOID_TEXT));
		}
		if (s.letOutLockedUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.sentry.locked").getString() + " " + clock(s.letOutLockedUntil - now), 0xFFAAAAAA));
		}
		hud.tags(tags);
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		if (MoveListOverlay.held()) {
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SentryAbilityManager.idsOf(slot, dark);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.sentry.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.sentry.ability." + ids[1]) : null));
			}
			// v0.17.5 rework: flight is double-tap Space (both forms)
			rows.add(MoveListOverlay.inline(Component.translatable("hud.projecthero.sentry.key_space").getString(),
					Component.translatable("projecthero.sentry.ability.flight")));
		}
		hud.moveList(rows);
	}

	private static void keys(VanillaHud.Stack hud, LocalPlayer p, SentryState s, boolean dark) {
		boolean shift = forceShift || p.isShiftKeyDown();
		long now = p.level().getGameTime();
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = SentryAbilityManager.idsOf(slot, dark);
			if (ids.length == 0) {
				hud.slot(i, VanillaHud.keyLabel(slot), false, false, 0f, 0, 0f, false);
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SentryAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, SentryAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, SentryAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, SentryAbilityManager.maxCooldown(other)));
			boolean usable = SentryAbilityManager.allowed(p, shown);
			boolean active = (SentryAbilityManager.SUNBEAM.equals(shown) && s.beamStart != 0L)
					|| (SentryAbilityManager.CALM.equals(shown) && s.calming)
					|| (SentryAbilityManager.SHIELD.equals(shown) && s.shieldUntil > now)
					|| (SentryAbilityManager.MILLION_SUNS.equals(shown) && s.sunsStart != 0L)
					|| (SentryAbilityManager.GRIP.equals(shown) && s.gripTarget >= 0)
					|| (SentryAbilityManager.NIGHTFALL.equals(shown) && s.nightfallUntil > now);
			hud.slot(i, VanillaHud.keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
		if (s.sunsStart != 0L) {
			hud.hold(2, Mth.clamp((now - s.sunsStart) / (float) SentryConfig.SUNS_CHARGE_TICKS, 0f, 1f)); // Z: the charge rising
		}
		if (s.gripTarget >= 0) {
			hud.hold(1, Mth.clamp((now - s.gripStart) / (float) SentryConfig.GRIP_TEAR_TICKS, 0f, 1f)); // G: the tear
		}
	}

	/** The Void meter: a Hairline row drawn by hand -- a black 3-px line on grey, a gold edge past 50, pulsing past 90. */
	private static void voidLine(VanillaHud.Stack hud, SentryState s, long now, float partial) {
		GuiGraphics g = hud.g;
		hud.reserve(11);
		int y = hud.top() + 2;
		Component label = Component.translatable("hud.projecthero.sentry.void");
		String value = String.valueOf(Math.round(s.voidLevel));
		int labelW = hud.font.width(label);
		int valueW = Math.max(hud.font.width("0:00"), hud.font.width(value));
		int barX = hud.x0 + labelW + 4;
		int barW = Math.max(20, hud.width - labelW - 4 - valueW - 3);
		float frac = Mth.clamp(s.voidLevel / SentryConfig.VOID_MAX, 0f, 1f);
		int valueColour = s.voidLevel >= SentryConfig.VOID_DIMMING ? 0xFFFF5555 : s.voidLevel >= SentryConfig.VOID_WHISPERS ? VOID_TEXT : VanillaHud.WHITE;
		g.drawString(hud.font, label, hud.x0, y, VanillaHud.WHITE, true);
		g.drawString(hud.font, value, hud.x0 + hud.width - hud.font.width(value), y, valueColour, true);
		int by = y + 3;
		g.fill(barX - 1, by - 1, barX + barW + 1, by + 4, 0xFF5A5A5A);
		g.fill(barX, by, barX + barW, by + 3, 0xFFB8B8B8);
		int fill = Math.round(barW * frac);
		if (fill > 0) {
			g.fill(barX, by, barX + fill, by + 3, 0xFF050308);
			if (s.voidLevel >= SentryConfig.VOID_WHISPERS) {
				float pulse = s.voidLevel >= SentryConfig.VOID_DIMMING ? 0.5f + 0.5f * Mth.sin((now + partial) * 0.5f) : 1f;
				int a = (int) (0xC0 * pulse) << 24;
				g.fill(barX, by, barX + fill, by + 1, a | 0xFFC83A); // the gold vein along its top
				g.fill(barX + fill - 1, by, barX + fill, by + 3, a | 0xFFE89A);
			}
		}
		// the threshold ticks at 50 / 75 / 90
		for (float t : new float[] { SentryConfig.VOID_WHISPERS, SentryConfig.VOID_MISFIRE, SentryConfig.VOID_DIMMING }) {
			int tx = barX + Math.round(barW * t / SentryConfig.VOID_MAX);
			g.fill(tx, by - 1, tx + 1, by + 4, 0xFF7A2A2A);
		}
	}

	/** The Void's thresholds over the view: veins from the edges (50+), a heartbeat of darkness (90+). */
	private static void thresholds(GuiGraphics g, Minecraft mc, SentryState s, float partial) {
		float v = s.voidLevel;
		if (v < SentryConfig.VOID_WHISPERS) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = mc.level.getGameTime();
		float t = now + partial;
		float k = Mth.clamp((v - SentryConfig.VOID_WHISPERS) / (SentryConfig.VOID_MAX - SentryConfig.VOID_WHISPERS), 0f, 1f);
		// black veins creeping in from the edges: branching jagged lines, fixed per vein, growing with the Void
		int veins = 10 + Math.round(k * 14);
		int reach = Math.round(Math.min(w, h) * (0.10f + 0.30f * k));
		for (int i = 0; i < veins; i++) {
			long seed = i * 977L + 13L;
			int side = i % 4;
			float along = ((seed * 31L) % 1000L) / 1000f;
			int x = side == 0 ? 0 : side == 1 ? w - 1 : Math.round(along * w);
			int y = side == 2 ? 0 : side == 3 ? h - 1 : Math.round(along * h);
			int dx = side == 0 ? 1 : side == 1 ? -1 : 0;
			int dy = side == 2 ? 1 : side == 3 ? -1 : 0;
			int len = Math.round(reach * (0.5f + 0.5f * (((seed * 17L) % 100L) / 100f)) * (0.85f + 0.15f * Mth.sin(t * 0.05f + i)));
			int thick = v >= SentryConfig.VOID_MISFIRE ? 3 : 2;
			for (int j = 0; j < len; j += 2) {
				int wob = Math.round(Mth.sin(j * 0.18f + i * 1.7f) * 4f);
				int px = x + dx * j + (dx == 0 ? wob : 0);
				int py = y + dy * j + (dy == 0 ? wob : 0);
				int a = (int) (200 * (1f - j / (float) Math.max(1, len)));
				int tw = Math.max(1, Math.round(thick * (1f - j / (float) Math.max(1, len))));
				g.fill(px, py, px + tw, py + tw, (a << 24) | 0x080410);
				if (j == len / 2 && v >= SentryConfig.VOID_MISFIRE) {
					// a branch
					for (int b = 0; b < len / 3; b += 2) {
						int bx = px + (dx == 0 ? b / 2 : dx * b);
						int bys = py + (dy == 0 ? b / 2 : dy * b);
						g.fill(bx, bys, bx + 1, bys + 1, ((a / 2) << 24) | 0x080410);
					}
				}
			}
		}
		if (v >= SentryConfig.VOID_DIMMING) {
			// the heartbeat: two quick pulses, a pause
			float beat = (t % 24f) / 24f;
			float pulse = beat < 0.12f ? beat / 0.12f : beat < 0.24f ? 1f - (beat - 0.12f) / 0.12f
					: beat < 0.34f ? (beat - 0.24f) / 0.10f * 0.7f : beat < 0.46f ? 0.7f - (beat - 0.34f) / 0.12f * 0.7f : 0f;
			int a = (int) (90 * pulse);
			if (a > 0) {
				int band = Math.min(w, h) / 5;
				g.fillGradient(0, 0, w, band, (a << 24) | 0x05000A, 0x0005000A);
				g.fillGradient(0, h - band, w, h, 0x0005000A, (a << 24) | 0x05000A);
			}
		}
	}
}
