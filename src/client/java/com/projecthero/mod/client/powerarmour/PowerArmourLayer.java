package com.projecthero.mod.client.powerarmour;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.5 Power Armour: the suit on its wearer, riding the posed player model, for every viewer. */
public class PowerArmourLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public PowerArmourLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		SuitRenderer.Look look = SuitLooks.wearer(player, player.level().getGameTime() + partialTick);
		if (look == null) {
			return;
		}
		SuitRenderer.render(pose, buffers, light, LivingEntityRenderer.getOverlayCoords(player, 0f), SuitLooks.parts(getParentModel()), look);
	}
}
