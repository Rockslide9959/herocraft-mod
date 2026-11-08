package com.projecthero.mod.client.andy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.andy.Andy;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.3: Andy's regrowing parts, stumps, card, sword and Victor haze on every player with the power ({@link AndyRender}). */
public class AndyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public AndyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!Andy.hasPower(player)) {
			return;
		}
		if (player.isInvisible()) {
			AndyRender.capture(player, pose, getParentModel());
			return;
		}
		AndyRender.renderLayer(pose, buffers, light, player, getParentModel(), partialTick);
	}
}
