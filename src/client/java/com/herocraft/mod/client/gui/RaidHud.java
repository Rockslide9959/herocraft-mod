package com.herocraft.mod.client.gui;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.grave.GraveboundState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Gravebound Curse countdown block.
 *
 * <p>Since v0.7.2 the raid itself is shown with a vanilla-style boss bar at the top of the screen
 * (see {@code EventBossBar}) rather than a bespoke readout, so all that is left here is the curse
 * timer: a small block on the right edge near the vertical middle, clear of the hotbar, the raid bar
 * and the vanilla effect icons alike. It reads the player's own synced attachment, so it needs no
 * packets and does nothing at all while no curse is running.
 */
public final class RaidHud {
	private static final int MARGIN_X = 6;
	private static final int LINE = 10;

	private static final int COLOR_PANEL = 0xB0101018;
	private static final int COLOR_BORDER = 0xFF3A2438;
	private static final int COLOR_TEXT = 0xFFD8D8E8;
	private static final int COLOR_CURSE = 0xFFB07CE0;
	private static final int COLOR_CURSE_URGENT = 0xFFFF5555;

	private RaidHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}
		renderCurse(graphics, client, player);
	}

	private static void renderCurse(GuiGraphics graphics, Minecraft client, Player player) {
		GraveboundState grave = player.getAttachedOrElse(ModAttachments.GRAVEBOUND_STATE, null);
		if (grave == null || grave.curseTicksLeft <= 0) {
			return;
		}
		int seconds = grave.curseTicksLeft / 20;
		boolean urgent = seconds <= 120;
		Component title = Component.translatable("hud.herocraft.curse")
				.withStyle(urgent ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE);
		Component timer = Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d",
				seconds / 60, seconds % 60));

		int width = Math.max(client.font.width(title), client.font.width(timer)) + 10;
		int height = LINE * 2 + 6;
		int x = graphics.guiWidth() - MARGIN_X - width;
		int y = graphics.guiHeight() / 2 - height - 24;
		graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
		graphics.renderOutline(x, y, width, height, COLOR_BORDER);
		graphics.drawString(client.font, title, x + 5, y + 4, urgent ? COLOR_CURSE_URGENT : COLOR_CURSE, false);
		graphics.drawString(client.font, timer, x + 5, y + 4 + LINE, COLOR_TEXT, false);
	}
}
