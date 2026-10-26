package com.projecthero.mod.client.flash;

import java.util.Locale;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user: "give the user a HUD when using the flash suit ... it should show important information and their
 * velocity"): the Flash Suit's speed readout, top-left, while a speedster wears the full suit.
 * <ul>
 *   <li>the velocity -- big, in blocks a second, with miles an hour beside it (1 block = 1 m) -- and a gauge up to
 *   Overdrive's top sprint with marks at the Speed Mode and Overdrive tiers;</li>
 *   <li>the mode (Walking / Speed Mode / Overdrive / Phasing / Time Slow / Exhausted) and the run's top speed;</li>
 *   <li>bars for what is running: Overdrive's time left, Time Slow's charge or time left, the exhaustion after it.</li>
 * </ul>
 * Everything is read from the synced Super Speed state; the velocity is the player's own movement, smoothed.
 */
public final class FlashSuitHud {
	private static final String KEY = SuperSpeedHandlers.KEY;
	/** The gauge's full scale: Overdrive's top sprint, blocks a second. */
	private static final float GAUGE_MAX = 120f;
	private static final float MPH_PER_BPS = 2.23694f;

	private static final int BG = 0xC0100606;
	private static final int EDGE = 0xFFB01E1E;
	private static final int GOLD = 0xFFFFD23A;
	private static final int RED = 0xFFFF3A2E;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int DIM = 0xFF9A8F8A;

	private static float speed;
	private static float top;
	private static int idleTicks;

	private FlashSuitHud() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(FlashSuitHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(FlashSuitHud::tick);
	}

	private static boolean shows(Player p) {
		if (p == null || !FlashSuit.wearsFull(p)) {
			return false;
		}
		ExperimentalState s = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return s != null && KEY.equals(s.activePower);
	}

	private static void tick(Minecraft mc) {
		Player p = mc.player;
		if (!shows(p) || mc.isPaused()) {
			return;
		}
		double dx = p.getX() - p.xo;
		double dy = p.getY() - p.yo;
		double dz = p.getZ() - p.zo;
		float now = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 20f;
		speed = Mth.lerp(0.35f, speed, now);
		if (speed > top) {
			top = speed;
		}
		// the run's top speed resets after 10 s standing still
		idleTicks = speed < 1f ? idleTicks + 1 : 0;
		if (idleTicks > 200) {
			top = 0f;
		}
	}

	private static float res(ExperimentalState s, String name) {
		return s.resources.getOrDefault(KEY + "/" + name, 0f);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (mc.options.hideGui || !shows(p)) {
			return;
		}
		ExperimentalState s = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		boolean overdrive = res(s, SuperSpeedHandlers.OVERDRIVE_LEFT) > 0f;
		boolean speedMode = s.activeToggles.contains(KEY + "/speed_mode");
		boolean phasing = SuperSpeedHandlers.phasing(p);
		float exhaust = res(s, SuperSpeedHandlers.EXHAUST);
		float tsLeft = res(s, com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.LEFT);
		boolean tsCharging = res(s, SuperSpeedHandlers.TS_CHARGING) > 0.5f;

		Component mode;
		int modeColour;
		if (exhaust > 0f) {
			mode = Component.translatable("hud.projecthero.flash.mode.exhausted");
			modeColour = DIM;
		} else if (tsLeft > 0f) {
			mode = Component.translatable("hud.projecthero.flash.mode.time_slow");
			modeColour = 0xFF8FD8FF;
		} else if (phasing) {
			mode = Component.translatable("hud.projecthero.flash.mode.phasing");
			modeColour = 0xFFC8B8FF;
		} else if (overdrive) {
			mode = Component.translatable("hud.projecthero.flash.mode.overdrive");
			modeColour = RED;
		} else if (speedMode) {
			mode = Component.translatable("hud.projecthero.flash.mode.speed");
			modeColour = GOLD;
		} else {
			mode = Component.translatable("hud.projecthero.flash.mode.walk");
			modeColour = WHITE;
		}

		int x = 6;
		int y = 6;
		int w = 150;
		int rows = 1 + (overdrive ? 1 : 0) + (tsLeft > 0f || tsCharging ? 1 : 0) + (exhaust > 0f ? 1 : 0);
		int h = 52 + rows * 9;
		g.fill(x, y, x + w, y + h, BG);
		g.renderOutline(x, y, w, h, EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + 12, 0xC0601010);
		// the header: the bolt, the name, the mode
		g.drawString(mc.font, Component.literal("⚡ ").withColor(GOLD & 0xFFFFFF)
				.append(Component.translatable("hud.projecthero.flash.title").withColor(WHITE & 0xFFFFFF)), x + 4, y + 3, WHITE, true);
		g.drawString(mc.font, mode, x + w - 4 - mc.font.width(mode), y + 3, modeColour, true);

		// the velocity, big, with mph beside it
		String bps = String.format(Locale.ROOT, "%.0f", speed);
		g.pose().pushPose();
		g.pose().translate(x + 5, y + 16, 0);
		g.pose().scale(2f, 2f, 1f);
		g.drawString(mc.font, bps, 0, 0, overdrive ? RED : GOLD, true);
		g.pose().popPose();
		int after = x + 7 + mc.font.width(bps) * 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.bps"), after, y + 17, WHITE, true);
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.mph", String.format(Locale.ROOT, "%.0f", speed * MPH_PER_BPS)),
				after, y + 26, DIM, false);

		// the gauge, with marks at Speed Mode's sprint (40) and Overdrive's (120)
		int gx = x + 5;
		int gy = y + 37;
		int gw = w - 10;
		g.fill(gx, gy, gx + gw, gy + 5, 0xFF2A1010);
		float frac = Mth.clamp(speed / GAUGE_MAX, 0f, 1f);
		int fill = Math.round(gw * frac);
		for (int i = 0; i < fill; i++) {
			float t = i / (float) gw;
			int c = lerpColour(0xFFFFE07A, overdrive ? 0xFFFF2A1A : 0xFFFF9A1A, t);
			g.fill(gx + i, gy, gx + i + 1, gy + 5, c);
		}
		for (float mark : new float[] { 20f, 40f, 120f }) {
			int mx = gx + Math.round(gw * mark / GAUGE_MAX) - 1;
			g.fill(mx, gy - 1, mx + 1, gy + 6, 0xFFFFFFFF);
		}
		if (top > 0.5f) {
			int tx = gx + Math.round(gw * Mth.clamp(top / GAUGE_MAX, 0f, 1f)) - 1;
			g.fill(tx, gy - 2, tx + 1, gy + 7, RED);
		}

		// the rows: top speed, then whatever is running
		int ry = gy + 9;
		g.drawString(mc.font, Component.translatable("hud.projecthero.flash.top", String.format(Locale.ROOT, "%.0f", top)), gx, ry, DIM, false);
		ry += 9;
		if (overdrive) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.flash.overdrive"), res(s, SuperSpeedHandlers.OVERDRIVE_LEFT) / (30f * 20f),
					res(s, SuperSpeedHandlers.OVERDRIVE_LEFT), RED);
			ry += 9;
		}
		if (tsLeft > 0f) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.flash.time_slow"), tsLeft / (45f * 20f), tsLeft, 0xFF8FD8FF);
			ry += 9;
		} else if (tsCharging) {
			float c = res(s, SuperSpeedHandlers.TS_CHARGE);
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.flash.charging"), c / SuperSpeedHandlers.TS_CHARGE_TICKS,
					SuperSpeedHandlers.TS_CHARGE_TICKS - c, 0xFF8FD8FF);
			ry += 9;
		}
		if (exhaust > 0f) {
			bar(g, mc, gx, ry, gw, Component.translatable("hud.projecthero.flash.exhausted"), exhaust / (30f * 20f), exhaust, DIM);
		}
	}

	/** A labelled thin bar with the seconds left at its end. */
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
