package com.projecthero.mod.client.saiyan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.saiyan.aura.SaiyanAuraRenderer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.3: a Saiyan's aura on every player, for every viewer (a render layer, so never round the camera in first person). */
public class SaiyanLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public SaiyanLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || player.isSpectator()) {
			return;
		}
		SaiyanAuraRenderer.render(pose, buffers, player, partialTick, ageInTicks);
		// v0.17.3 PREVIEW: the SSJ hair model (off unless SsjHairRenderer.PREVIEW is set)
		com.projecthero.mod.client.saiyan.hair.SsjHairRenderer.render(pose, buffers, light, player, getParentModel());
	}
}
