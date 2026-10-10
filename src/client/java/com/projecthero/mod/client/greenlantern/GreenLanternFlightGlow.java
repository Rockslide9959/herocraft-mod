package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.flight.BodyGlow;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.15.15 playtest (user: "the glow is supposed to warp around the players body like an outer layer of the players
 * body, make it really transparent"): while in Ring Flight, a Green Lantern wears a thin skin of green light -- the shared
 * {@link BodyGlow} in {@link BodyGlow#GREEN_LANTERN}, faded in / out with flight ({@link GreenLanternFlightFx}), a touch
 * brighter sprint-flying. Drawn from {@code PowerRingLayer}. Replaces the old ellipsoid aura.
 */
public final class GreenLanternFlightGlow {
	private GreenLanternFlightGlow() {
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
			float ageInTicks) {
		// v0.15.20: switched off on the Shift+N suit screen
		var gl = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (gl != null && !gl.bodyGlow) {
			return;
		}
		float k = GreenLanternFlightFx.auraStrength(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		float boost = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BOOSTING, false) || player.isSprinting() ? 1f : 0f;
		BodyGlow.render(pose, buffers, player, model, ageInTicks, k, boost, BodyGlow.GREEN_LANTERN, false);
	}
}
