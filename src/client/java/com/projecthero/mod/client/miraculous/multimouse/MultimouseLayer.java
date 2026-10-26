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
		// v0.17.1: the long transformation raises the hood on its own beat (1 = on, outside it)
		float hood = MultimouseSequenceClient.hood(player, partialTick);
		MultimouseRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover, OverlayTexture.NO_OVERLAY, hood);
		if (cover < 0.999f) {
			// the wearer's own second layer (hair included: the hood replaces it) goes texel by texel with the light --
			// in the long one her hair stays until the hood comes up over it
			OverlayStrips.render(pose, buffers, light, player, getParentModel(),
					(u, v) -> MultimouseRender.order(u, v) > cover || (hood < 0.5f && v < 16), true);
		}
		float swing = Mth.clamp(limbSwingAmount, 0f, 1f);
		float[] seq = MultimouseSequenceClient.extras(player, partialTick);
		if (seq != null) {
			// the ears pop on their beat, the rope is off her belt while she skips it, the tail flicks out as it winds back
			MultimouseRender.renderExtras(pose, buffers, light, getParentModel(), ageInTicks, swing, limbSwing, 1f, seq[0], seq[1] > 0f, seq[2], seq[3]);
		} else {
			MultimouseRender.renderExtras(pose, buffers, light, getParentModel(), cover, ageInTicks, swing, limbSwing);
		}
	}
}
