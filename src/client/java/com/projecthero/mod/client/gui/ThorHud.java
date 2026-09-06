package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.CooldownState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.StormEnergy;
import com.projecthero.mod.power.ThorAbility;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Thor HUD (v0.6.22 rework). Built to mirror {@link SpiderHud} -- the same six ability boxes in
 * the same bottom-right corner, the same cooldown shading, the same hold-{@code ALT}-for-names, and a
 * resource meter beneath -- just recoloured for a storm god (deep blue panels, gold trim) instead of
 * Spider-Man's reds. Only drawn while the player is actually holding Mjolnir.
 *
 * <p>The hammerless-flight countdown from the old HUD is kept: a single small line above the boxes
 * that turns urgent under five seconds.
 */
public final class ThorHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;

	private static final int COLOR_BOX_BG = 0xC00A1526;
	private static final int COLOR_BORDER = 0xFF2E4A6E;
	private static final int COLOR_BORDER_ACTIVE = 0xFFF2C24E;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFE8D9B0;
	private static final int COLOR_ENERGY = 0xFF6FA8FF;
	private static final int COLOR_ENERGY_LOW = 0xFF4C4C88;
	private static final int COLOR_NAME = 0xFFCFE0FF;

	/** Below this many ticks (5s) remaining, the hammerless-flight countdown text turns urgent. */
	private static final int WARNING_TICKS = 5 * 20;

	/** Slot order 1..6 -> {@code (ability id, cooldown key or null)}. Matches {@code ThorAbilityAdapter}. */
	private static final String[] SLOT_NAME_KEYS = {
			"call_mjolnir", "lightning_strike", "lightning_beam",
			"god_of_thunders_wrath", "thunderclap", "chain_lightning"
	};
	private static final ThorAbility[] SLOT_COOLDOWNS = {
			ThorAbility.CALL_HAMMER, ThorAbility.LIGHTNING_STRIKE, null,
			ThorAbility.GOD_OF_THUNDER, ThorAbility.THUNDERCLAP, ThorAbility.CHAIN_LIGHTNING
	};

	private ThorHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}

		boolean holdingMjolnir = player.getMainHandItem().is(ModItems.MJOLNIR)
				|| player.getOffhandItem().is(ModItems.MJOLNIR);
		int graceTicks = player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0);

		if (holdingMjolnir) {
			renderKit(graphics, client, player);
			renderWrathCharge(graphics, client, player);
		}
		if (graceTicks > 0) {
			renderHammerlessFlightCountdown(graphics, client, graceTicks);
		}
	}

	private static void renderKit(GuiGraphics graphics, Minecraft client, Player player) {
		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		CooldownState cooldowns = player.getAttachedOrElse(ModAttachments.COOLDOWNS, null);

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = screenW - MARGIN - totalW;
		int y0 = screenH - MARGIN - BOX - 20;

		graphics.drawString(client.font, Component.translatable("projecthero.guide.thor")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), x0, y0 - 10, 0xFF7FB0FF);

		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);

			ThorAbility cd = SLOT_COOLDOWNS[i];
			int cdRemain = (cd != null && cooldowns != null) ? cooldowns.remainingTicks(cd, gameTime) : 0;

			graphics.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			graphics.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			graphics.drawString(client.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, COLOR_KEY, false);

			if (cdRemain > 0) {
				graphics.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				graphics.drawCenteredString(client.font,
						String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cdRemain / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.thor.ability." + SLOT_NAME_KEYS[i]);
				int tw = client.font.width(name);
				graphics.drawString(client.font, name, x0 - 8 - tw, y0 + i * 10 - 52, COLOR_NAME);
			}
		}

		// Storm Energy bar.
		float ratio = Math.max(0.0f, Math.min(1.0f, StormEnergy.get(player) / StormEnergy.MAX));
		int barY = y0 + BOX + 4;
		graphics.fill(x0 - 1, barY - 1, x0 + totalW + 1, barY + 5, COLOR_BORDER);
		graphics.fill(x0, barY, x0 + totalW, barY + 4, 0xAA0A1526);
		graphics.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + 4,
				ratio < 0.2f ? COLOR_ENERGY_LOW : COLOR_ENERGY);
		graphics.drawString(client.font, Component.translatable("hud.projecthero.thor.storm_energy",
				(int) Math.ceil(StormEnergy.get(player)), (int) StormEnergy.MAX), x0, barY + 5, 0xFFA8C0E0, false);
	}

	/** Ticks the player must hold Z to charge God of Thunder's Wrath (mirrors {@code ThorPowers}). */
	private static final int WRATH_CHARGE_TICKS = 5 * 20;

	/**
	 * The 5-second buildup bar for God of Thunder's Wrath, drawn while the player is holding Z. Sits
	 * directly above the ability keybind row in the bottom-right corner -- clear of the Storm Energy
	 * bar (below the row) and the "Thor" label (just above it), so nothing in this HUD overlaps.
	 * Fills left-to-right; flashes gold as it completes.
	 */
	private static void renderWrathCharge(GuiGraphics graphics, Minecraft client, Player player) {
		int ticks = player.getAttachedOrElse(ModAttachments.THOR_WRATH_CHARGE, 0);
		if (ticks <= 0) {
			return;
		}
		float ratio = Math.min(1.0f, ticks / (float) WRATH_CHARGE_TICKS);
		int w = 6 * BOX + 5 * GAP;
		int h = 5;
		int x = graphics.guiWidth() - MARGIN - w;
		int boxesY = graphics.guiHeight() - MARGIN - BOX - 20;
		int y = boxesY - 10 - 10 - h;
		boolean full = ratio >= 1.0f;

		Component label = Component.translatable("hud.projecthero.thor.wrath_charge")
				.withStyle(full ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
		graphics.drawCenteredString(client.font, label, x + w / 2, y - 10, 0xFFFFFFFF);

		graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_BORDER);
		graphics.fill(x, y, x + w, y + h, 0xC00A1526);
		graphics.fill(x, y, x + Math.round(w * ratio), y + h, full ? COLOR_BORDER_ACTIVE : COLOR_ENERGY);
	}

	/** Subtle by design -- a single small line above the ability boxes. Turns gold and flickers under
	 * {@link #WARNING_TICKS} remaining. */
	private static void renderHammerlessFlightCountdown(GuiGraphics graphics, Minecraft client, int ticks) {
		float seconds = ticks / 20.0f;
		boolean urgent = ticks <= WARNING_TICKS;
		boolean flicker = urgent && (client.level.getGameTime() / 4L) % 2L == 0L;

		ChatFormatting color = urgent ? (flicker ? ChatFormatting.YELLOW : ChatFormatting.GOLD) : ChatFormatting.AQUA;
		Component text = Component.translatable("message.projecthero.hammerless_flight.countdown",
				String.format(java.util.Locale.ROOT, "%.1f", seconds)).withStyle(color);

		graphics.drawString(client.font, text, 10, 12, 0xFFFFFFFF);
	}
}
