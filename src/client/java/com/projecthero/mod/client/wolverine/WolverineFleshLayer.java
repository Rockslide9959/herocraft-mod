package com.projecthero.mod.client.wolverine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * While a Wolverine's flesh fades back to normal, the base body is drawn with the flesh texture (see
 * {@code PlayerRendererFleshMixin}) and this layer draws the player's own skin over it at a rising alpha.
 */
public class WolverineFleshLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public WolverineFleshLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		float alpha = WolverineFlesh.skinAlpha(player, partialTick);
		if (alpha <= 0.01f || alpha >= 1.0f || player.isInvisible()) {
			return;
		}
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
		int argb = (Math.round(alpha * 255.0f) << 24) | 0xFFFFFF;
		getParentModel().renderToBuffer(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, argb);
	}
}
