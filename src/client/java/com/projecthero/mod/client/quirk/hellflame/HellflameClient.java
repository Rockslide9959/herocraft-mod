package com.projecthero.mod.client.quirk.hellflame;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.client.quirk.QuirkHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7, v0.17.1 (Quirks revamp): Hellflame's client side -- the keyframed poses ({@link HellflamePose}) and first-person
 * clips ({@link HellflameFirstPerson}), the flames every viewer sees ({@link HellflameFx}: particles and the flame-beard tell;
 * {@link HellflameRender}: the Hell Spider strings, the Mesh, the Hellblast beam, the Fire Wall's sheets and Prominence
 * Burn's sun as glowing geometry), the Fire Jet Flight of the local player ({@link HellflameFlightClient}) and the HUD's
 * thermometer ({@link #drawStatus}).
 */
public final class HellflameClient implements QuirkClientKit {
	private static final int BAR_BG = 0xC0141414;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int RED = 0xFFFF5A4A;

	private HellflameClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.HELLFLAME, new HellflameClient());
		HellflameParticleClient.register();
		HellflameRender.register();
		ClientTickEvents.START_CLIENT_TICK.register(HellflameFlightClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(HellflameFx::tick);
		HellflameFirstPerson.register();
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		HellflamePose.apply(player, model);
	}

	@Override
	public float holdCharge(Player player, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_2 && Hellflame.charging(player) ? Hellflame.charge(player, 0f) : -1f;
	}

	@Override
	public boolean fullBodyFirstPerson(Player player) {
		return Hellflame.prominence(player);
	}

	/** The flame colour of a band: orange (Kindle), yellow-white (Blaze), white-hot (Inferno), flickering white past 100. */
	static int bandColour(QuirkBand band, long now) {
		return switch (band) {
			case LOW -> 0xFFFF7A1A;
			case SWEET -> 0xFFFFC040;
			case RED -> 0xFFFFF0C8;
			case BEYOND -> (now / 2) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFD080;
		};
	}

	/**
	 * The Body Heat thermometer: a bulb at the left glowing in the band's flame colour (orange, yellow-white, white-hot) and the
	 * tube running from it with the shared band ticks / shimmer / red-zone pulse ({@link QuirkHud#drawBands}), the value at the
	 * right, and a wrapped status line over it (OVERHEAT / BURNT OUT / PROMINENCE BURN / Hellblast / flying / Inferno).
	 */
	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		long now = player.level().getGameTime();
		float heat = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		boolean burnt = Hellflame.burntOut(player);
		boolean over = Quirks.overloaded(player);
		boolean beyond = QuirkMeters.beyond(player);
		QuirkBand band = QuirkMeters.clientBand(player);
		int barH = 4;
		int by = bottom - 2 - barH;
		Component label = Component.translatable("hud.projecthero.hellflame.heat");
		int labelW = mc.font.width(label);
		String value = beyond ? "100+" : String.valueOf(Math.round(heat));
		int valueW = mc.font.width("100+");
		int bulbX = x0 + labelW + 4;
		int barX = bulbX + 8;
		int barW = Math.max(20, x0 + width - barX - valueW - 3);
		int colour = over && !burnt ? ((now / 5) % 2 == 0 ? RED : 0xFFB02010) : burnt ? 0xFF8A4A30 : bandColour(band, now);
		g.drawString(mc.font, label, x0, by - 2, NAME, false);
		// the bulb (a 7x8 disc) and the tube's rounded far end
		int cy = by + barH / 2;
		// a 9x9 disc
		int[][] rows = { { 3, 6 }, { 1, 8 }, { 1, 8 }, { 0, 9 }, { 0, 9 }, { 0, 9 }, { 1, 8 }, { 1, 8 }, { 3, 6 } };
		for (int r = 0; r < rows.length; r++) {
			g.fill(bulbX + rows[r][0], cy - 4 + r, bulbX + rows[r][1], cy - 3 + r, BORDER);
			if (r > 0 && r < rows.length - 1) {
				g.fill(bulbX + rows[r][0] + 1, cy - 4 + r, bulbX + rows[r][1] - 1, cy - 3 + r, colour);
			}
		}
		g.fill(bulbX + 2, cy - 2, bulbX + 4, cy - 1, 0xC0FFFFFF);
		// the bulb's heat flickers up into the tube
		g.fill(bulbX + 8, by, barX, by + barH, colour);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + barH + 1, BORDER);
		g.fill(barX + barW + 1, by, barX + barW + 2, by + barH, BORDER);
		g.fill(barX, by, barX + barW, by + barH, BAR_BG);
		QuirkHud.drawBands(g, new QuirkHud.MeterView(barX, by, barW, barH, beyond ? 100f : heat, band, QuirkMeters.spec(Quirk.HELLFLAME), colour,
				now, over, false));
		g.drawString(mc.font, value, x0 + width - mc.font.width(value), by - 2, NAME, false);
		int top = by - 3;

		// the status line (wrapped)
		Component status = null;
		int sc = NAME;
		if (Hellflame.prominence(player)) {
			status = Component.translatable("hud.projecthero.hellflame.prominence");
			sc = 0xFFFFE0A0;
		} else if (burnt) {
			status = Component.translatable("hud.projecthero.hellflame.burnout", (Hellflame.val(player, Hellflame.V_BURNOUT) - now + 19) / 20);
			sc = RED;
		} else if (beyond) {
			status = Component.translatable("hud.projecthero.hellflame.overheat", (Quirks.peek(player).val(QuirkMeters.V_BEYOND) - now + 19) / 20);
			sc = 0xFFFFFFFF;
		} else if (Hellflame.charging(player)) {
			status = Component.translatable("hud.projecthero.hellflame.charge");
			sc = 0xFFFFB050;
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
			int y = top - 1 - lines.size() * 9;
			top = y;
			for (FormattedCharSequence line : lines) {
				g.drawString(mc.font, line, x0, y, sc, false);
				y += 9;
			}
		}
		return top;
	}
}
