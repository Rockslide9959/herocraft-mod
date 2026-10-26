package com.projecthero.mod.client.miraculous.multimouse;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Multimouse on every player who is (or is turning into) her -- the suit (the wearer's second layer going texel
 * by texel during the change) and the 3D extras: the ears, the rope belt and the tail. See {@link MultimouseRender}.
 */
public class MultimouseLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public MultimouseLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = MultimouseRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		boolean slim = MultimouseRender.slim(player);
		MultimouseRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover, OverlayTexture.NO_OVERLAY);
		if (cover < 0.999f) {
			// the wearer's own second layer (hair included: the hood replaces it) goes texel by texel with the light
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> MultimouseRender.order(u, v) > cover, true);
		}
		MultimouseRender.renderExtras(pose, buffers, light, getParentModel(), cover, ageInTicks, Mth.clamp(limbSwingAmount, 0f, 1f), limbSwing);
	}
}
