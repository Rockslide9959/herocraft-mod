package com.projecthero.mod.client.quirk.explosion;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): the Explosion Quirk's client side -- the poses ({@link ExplosionPose}; the Howitzer's whole-body
 * spin is {@code QuirkBodyTurnMixin}), Explosion Propulsion's flight ({@link ExplosionPropulsionClient}) and its own HUD
 * block:
 * <pre>
 *   Sweat [=========-----] 63       the meter (amber; the Howitzer / Gauntlet thresholds ticked on the bar)
 *   Arms  [===--------------]       Arm Strain (a hairline gauge: yellow, orange from 70, blinking red near full)
 * </pre>
 * The blasts' particles and sounds all come from the server (every viewer sees them); the costume is drawn by the core's
 * costume layer.
 */
public final class ExplosionClient implements QuirkClientKit {
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BAR_BG = 0xC0141414;
	private static final int AMBER = 0xFFFFB020;
	private static final int AMBER_LIGHT = 0xFFFFD87A;
	private static final int ORANGE = 0xFFFF7A2A;
	private static final int RED = 0xFFFF4A3A;

	private ExplosionClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.EXPLOSION, new ExplosionClient());
		ClientTickEvents.START_CLIENT_TICK.register(ExplosionPropulsionClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(ExplosionPropulsionClient::endTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ExplosionPropulsionClient.reset());
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ExplosionPose.apply(player, model);
	}

	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		var font = mc.font;
		long now = player.level().getGameTime();
		boolean strained = Quirks.overloaded(player);
		Component sweatLabel = Component.translatable("projecthero.quirk.meter.sweat");
		Component armsLabel = Component.translatable("hud.projecthero.quirk.explosion.arms");
		int labelW = Math.max(font.width(sweatLabel), font.width(armsLabel));
		int valueW = font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);

		// ---- Arm Strain (bottom row)
		float strain = strained ? 100f : Mth.clamp(Explosion.strain(player), 0f, 100f);
		int ay = bottom - 6;
		g.drawString(font, armsLabel, x0, ay - 3, strained ? RED : HINT, false);
		g.fill(barX - 1, ay - 1, barX + barW + 1, ay + 3, BORDER);
		g.fill(barX, ay, barX + barW, ay + 2, BAR_BG);
		int sf = Math.round(barW * strain / 100f);
		if (sf > 0) {
			int col = strain >= 90f ? ((now / 4) % 2 == 0 ? RED : 0xFFFFFFFF) : strain >= ExplosionConfig.STRAIN_WARN ? ORANGE : AMBER_LIGHT;
			g.fill(barX, ay, barX + sf, ay + 2, col);
		}

		// ---- Sweat
		float sweat = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		int by = ay - 12;
		g.drawString(font, sweatLabel, x0, by - 2, NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + 5, BORDER);
		g.fill(barX, by, barX + barW, by + 4, BAR_BG);
		com.projecthero.mod.client.quirk.QuirkHud.bandedFill(g, player, com.projecthero.mod.quirk.Quirk.EXPLOSION, barX, by, barW, 4, sweat,
				sweat >= ExplosionConfig.GAUNTLET_MIN_SWEAT ? AMBER_LIGHT : AMBER); // v0.17.1 bands
		// the thresholds: Howitzer Impact and the Gauntlet Grenade
		for (float mark : new float[] { ExplosionConfig.HOWITZER_MIN_SWEAT, ExplosionConfig.GAUNTLET_MIN_SWEAT }) {
			int mx = barX + Math.round(barW * mark / 100f);
			g.fill(mx, by - 2, mx + 1, by + 6, sweat >= mark ? 0xFFFFFFFF : 0xFF707070);
		}
		String value = String.valueOf(Math.round(sweat));
		g.drawString(font, value, x0 + width - font.width(value), by - 2, NAME, false);
		return by - 2;
	}
}
