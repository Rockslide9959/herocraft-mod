package com.projecthero.mod.client.render;

import com.projecthero.mod.client.greenlantern.GreenLanternHandRenderer;
import com.projecthero.mod.greenlantern.GreenLantern;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.11.10, explicit user request: a tiny, always-on Power Ring on the hand of any bonded Green
 * Lantern, driven by the synced {@link GreenLantern#hasPower} state so it shows correctly for other players too,
 * rather than editing the skin texture itself (which is what keeps it from ever touching the player's own skin).
 *
 * <p>v0.13.21: the flat item-icon fleck is replaced by a real little ring model on the right hand (band, bezel and a
 * glowing gem), plus the Energy Blade / Mining Drill while they are switched on -- all drawn by
 * {@link GreenLanternHandRenderer}, which the first-person hand shares. Always the right hand now (the user asked for
 * the ring on the right hand specifically; it used to follow the main-arm setting).
 */
public class PowerRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public PowerRingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
			ItemRenderer itemRenderer) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!GreenLanternHandRenderer.hasAnything(player)) {
			return;
		}
		// v0.15.15: the Ring Flight glow, a thin skin of light over the posed model
		com.projecthero.mod.client.greenlantern.GreenLanternFlightGlow.render(pose, buffers, player, getParentModel(), ageInTicks);
		GreenLanternHandRenderer.render(pose, buffers, packedLight, player, getParentModel().rightArm, ageInTicks);
	}
}
