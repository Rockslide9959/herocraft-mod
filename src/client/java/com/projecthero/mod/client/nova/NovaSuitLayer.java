package com.projecthero.mod.client.nova;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.15.13: the Nova Corps uniform on every player who has it on (every viewer: the state is synced). See
 * {@link NovaSuitRender}.
 */
public class NovaSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public NovaSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float progress = NovaSuitRender.progress(player, partialTick);
		if (progress <= 0f) {
			return;
		}
		NovaSuitRender.render(pose, buffers, light, getParentModel(), progress, 1f, NovaSuitRender.overloaded(player));
	}
}
