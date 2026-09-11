package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Laser Vision targeting reticle: for anyone who owns Laser Vision, a faint set of corner brackets is
 * drawn around the crosshair whenever they are looking directly at a living entity. Purely cosmetic and
 * purely local -- nothing is sent to the server.
 */
public final class LaserReticleHud {
	private LaserReticleHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui || client.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) {
			return;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains("power_02_laser_vision")) {
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity)) {
			return;
		}
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		int r = 7;
		int len = 3;
		int color = 0x88FF5533;
		// four corner brackets
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int x = cx + sx * r;
				int y = cy + sy * r;
				graphics.fill(Math.min(x, x - sx * len), y, Math.max(x, x - sx * len) + 1, y + 1, color);
				graphics.fill(x, Math.min(y, y - sy * len), x + 1, Math.max(y, y - sy * len) + 1, color);
			}
		}
	}
}
