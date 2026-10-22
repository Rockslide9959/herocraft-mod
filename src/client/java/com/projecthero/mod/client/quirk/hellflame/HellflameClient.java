package com.projecthero.mod.client.quirk.hellflame;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Hellflame's client side -- the keyframed poses ({@link HellflamePose}), the flames every viewer sees
 * ({@link HellflameFx}: particles; {@link HellflameRender}: the Hell Spider strings, the Mesh and the Prominence beam as
 * glowing geometry), the Fire Jet Flight of the local player ({@link HellflameFlightClient}) and the HUD's Body Heat block
 * ({@link #drawStatus}).
 */
public final class HellflameClient implements QuirkClientKit {
	private static final int BAR_BG = 0xC0141414;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int GOLD = 0xFFFFD25A;
	private static final int RED = 0xFFFF5A4A;

	private HellflameClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.HELLFLAME, new HellflameClient());
		HellflameParticleClient.register();
		HellflameRender.register();
		ClientTickEvents.START_CLIENT_TICK.register(HellflameFlightClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(HellflameFx::tick);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		HellflamePose.apply(player, model);
	}

	/**
	 * The Body Heat block: a status line (PLUS ULTRA / BURNT OUT / OVERHEATED / the running move, wrapped to the HUD's width)
	 * over the Heat bar, which runs orange, turns red-hot past {@link HellflameConfig#HEAT_WARN}, pulses while overheated and
	 * burns gold during Plus Ultra. A tick on the bar marks the warning line.
	 */
	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		long now = player.level().getGameTime();
		float heat = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		boolean plus = Hellflame.plusUltra(player);
		boolean burnt = Hellflame.burntOut(player);
		boolean over = Quirks.overloaded(player);
		int barH = 4;
		int by = bottom - 1 - barH;
		Component label = Component.translatable("hud.projecthero.hellflame.heat");
		int labelW = mc.font.width(label);
		String value = String.valueOf(Math.round(heat));
		int valueW = mc.font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);
		int colour;
		if (plus) {
			colour = (now / 3) % 2 == 0 ? 0xFFFFF0B0 : GOLD;
		} else if (over) {
			colour = (now / 5) % 2 == 0 ? RED : 0xFFB02010;
		} else if (heat >= HellflameConfig.HEAT_WARN) {
			colour = 0xFFFF3A1A;
		} else if (heat >= 50f) {
			colour = 0xFFFF5A12;
		} else {
			colour = 0xFFFF8A1A;
		}
		g.drawString(mc.font, label, x0, by - 2, NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + barH + 1, BORDER);
		g.fill(barX, by, barX + barW, by + barH, BAR_BG);
		int fill = Math.round(barW * heat / 100f);
		if (fill > 0) {
			g.fill(barX, by, barX + fill, by + barH, colour | 0xFF000000);
			g.fill(barX, by, barX + fill, by + 1, 0x60FFFFFF);
		}
		int warnX = barX + Math.round(barW * HellflameConfig.HEAT_WARN / 100f);
		g.fill(warnX, by - 1, warnX + 1, by + barH + 1, 0xA0FFFFFF);
		g.drawString(mc.font, value, x0 + width - mc.font.width(value), by - 2, NAME, false);
		int top = by - 2;

		// the status line (wrapped)
		Component status = null;
		int sc = NAME;
		if (plus) {
			status = Component.translatable("hud.projecthero.hellflame.plus_ultra", (Hellflame.val(player, Hellflame.V_PLUS) - now + 19) / 20);
			sc = GOLD;
		} else if (burnt) {
			status = Component.translatable("hud.projecthero.hellflame.burnout", (Hellflame.val(player, Hellflame.V_BURNOUT) - now + 19) / 20);
			sc = RED;
		} else if (Hellflame.beaming(player)) {
			status = Component.translatable("hud.projecthero.hellflame.beam");
			sc = 0xFFFFB050;
		} else if (Hellflame.flying(player)) {
			status = Component.translatable("hud.projecthero.hellflame.flying");
			sc = 0xFFFFB050;
		} else if (!over && heat >= HellflameConfig.HEAT_WARN) {
			status = Component.translatable("hud.projecthero.hellflame.hot");
			sc = 0xFFFF7A50;
		}
		if (status != null) {
			List<FormattedCharSequence> lines = mc.font.split(status, width);
			int y = top - 2 - lines.size() * 9;
			top = y;
			for (FormattedCharSequence line : lines) {
				g.drawString(mc.font, line, x0, y, sc, false);
				y += 9;
			}
		}
		return top;
	}
}
