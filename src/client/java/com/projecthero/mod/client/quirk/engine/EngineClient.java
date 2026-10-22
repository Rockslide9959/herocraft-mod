package com.projecthero.mod.client.quirk.engine;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.engine.Engine;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): the Engine Quirk's client side -- the poses ({@link EnginePose}), the exhaust trails ({@link EngineFx}),
 * the engines' sound ({@link EngineSound}), the sliding turns ({@link EngineRunClient}) and its own HUD block:
 * <pre>
 *   RECIPRO TURBO 27s                (while Recipro Turbo runs; blue, with a time-left hairline)
 *   STALLED 48s                      (while the engines are stalled; red)
 *   Gear [1][2][3]      13.4 b/s     the gear lit cell by cell, momentum as a hairline under the cells, his speed
 *   Heat [=======-----]  58          the engine heat (blue, turning orange then red as it climbs)
 * </pre>
 * The costume is drawn by the Quirk core's costume layer; the FOV cap at speed is {@code QuirkEngineFovMixin}.
 */
public final class EngineClient implements QuirkClientKit {
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BAR_BG = 0xC0141414;
	private static final int BLUE = 0xFF5AA9FF;
	private static final int BLUE_LIGHT = 0xFFA8D8FF;
	private static final int ORANGE = 0xFFFF9A3A;
	private static final int RED = 0xFFFF4A3A;

	private EngineClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.ENGINE, new EngineClient());
		// v0.16.7 (user): the calf engines on every Engine holder (after the Quirk suit layer, so over it), and in first person
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new EngineCalvesLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(EngineFirstPersonLegs::render);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			EngineRunClient.endTick(mc);
			EngineFx.tick(mc);
			if (mc.level != null) {
				EngineCalves.prune(mc.level.getGameTime());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			EngineSound.reset();
			EngineRunClient.reset();
			EngineCalves.reset();
		});
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		EnginePose.apply(player, model);
	}

	/** Heat colour: blue to 60, orange to 85, red above (blinking at 95+). */
	static int heatColour(float heat, long now) {
		if (heat >= 95f && (now / 4) % 2 == 0) {
			return 0xFFFFFFFF;
		}
		return heat >= 85f ? RED : heat >= 60f ? ORANGE : BLUE;
	}

	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		var font = mc.font;
		long now = player.level().getGameTime();
		boolean stalled = Engine.stalled(player);
		int y = bottom;

		// ---- heat
		float heat = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		Component heatLabel = Component.translatable("projecthero.quirk.meter.engine");
		String heatValue = String.valueOf(Math.round(heat));
		int labelW = Math.max(font.width(heatLabel), font.width(Component.translatable("hud.projecthero.quirk.engine.gear")));
		int valueW = font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);
		int by = y - 4 - 4;
		g.drawString(font, heatLabel, x0, by - 2, NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + 5, BORDER);
		g.fill(barX, by, barX + barW, by + 4, BAR_BG);
		int fill = Math.round(barW * heat / 100f);
		if (fill > 0) {
			g.fill(barX, by, barX + fill, by + 4, stalled && (now / 5) % 2 == 0 ? RED : heatColour(heat, now));
			g.fill(barX, by, barX + fill, by + 1, 0x60FFFFFF);
		}
		g.drawString(font, heatValue, x0 + width - font.width(heatValue), by - 2, NAME, false);
		y = by - 13;

		// ---- gear cells, momentum hairline, speed
		int gear = Engine.gear(player);
		float mom = Engine.momentum(player);
		boolean recipro = Engine.turbo(player);
		g.drawString(font, Component.translatable("hud.projecthero.quirk.engine.gear"), x0, y + 1, stalled ? HINT : NAME, false);
		int cellW = 11, gap = 2;
		int cx = barX;
		for (int i = 1; i <= 3; i++) {
			boolean lit = gear >= i;
			int col = !lit ? BAR_BG : recipro ? BLUE_LIGHT : BLUE;
			g.fill(cx - 1, y - 1, cx + cellW + 1, y + 10, lit ? col : BORDER);
			g.fill(cx, y, cx + cellW, y + 9, lit ? col : BAR_BG);
			String n = String.valueOf(i);
			g.drawString(font, n, cx + (cellW - font.width(n)) / 2 + 1, y + 1, lit ? 0xFF101820 : HINT, false);
			cx += cellW + gap;
		}
		int cellsW = 3 * cellW + 2 * gap;
		if (gear > 0) {
			// momentum: a hairline under the cells
			g.fill(barX, y + 11, barX + Math.round(cellsW * mom), y + 12, recipro ? BLUE_LIGHT : BLUE);
		}
		Component speed = Component.translatable("hud.projecthero.quirk.engine.speed", String.format(java.util.Locale.ROOT, "%.1f", EngineRunClient.speedBps()));
		int sw = font.width(speed);
		if (barX + cellsW + 6 + sw <= x0 + width) {
			g.drawString(font, speed, x0 + width - sw, y + 1, HINT, false);
		}
		y -= 12;

		// ---- Recipro Turbo / the stall
		int turbo = Engine.turboLeft(player);
		if (turbo > 0 && !stalled) {
			Component line = Component.translatable("hud.projecthero.quirk.engine.turbo", (turbo + 19) / 20);
			int col = turbo < 60 && (now / 4) % 2 == 0 ? 0xFFFFFFFF : BLUE_LIGHT;
			drawFit(g, mc, line, x0, y, width, col);
			// the time left as a hairline under it
			int w = Math.round(width * Math.min(1f, turbo / (float) com.projecthero.mod.quirk.engine.EngineConfig.TURBO_TICKS));
			g.fill(x0, y + 9, x0 + w, y + 10, BLUE);
			y -= 12;
		} else if (stalled) {
			int left = Quirks.overloadLeft(player);
			Component line = Component.translatable("hud.projecthero.quirk.engine.stalled", (left + 19) / 20);
			drawFit(g, mc, line, x0, y, width, (now / 10) % 2 == 0 ? RED : 0xFFB0B0B0);
			y -= 11;
		}
		return y + 11;
	}

	private static void drawFit(GuiGraphics g, Minecraft mc, Component text, int x, int y, int width, int colour) {
		String s = text.getString();
		if (mc.font.width(s) > width) {
			s = mc.font.plainSubstrByWidth(s, width - mc.font.width("..")) + "..";
		}
		g.drawString(mc.font, s, x, y, colour, false);
	}
}
