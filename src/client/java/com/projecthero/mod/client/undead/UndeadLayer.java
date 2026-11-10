package com.projecthero.mod.client.undead;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.undead.Undead;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.3: Undead's regrowing parts, stumps, card, sword and Victor haze on every player with the power ({@link UndeadRender}). */
public class UndeadLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public UndeadLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!Undead.hasPower(player)) {
			return;
		}
		if (player.isInvisible()) {
			UndeadRender.capture(player, pose, getParentModel());
			return;
		}
		UndeadRender.renderLayer(pose, buffers, light, player, getParentModel(), partialTick);
	}
}
