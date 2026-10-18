package com.projecthero.mod.client.miraculous.carapace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;

/** v0.16.6: Carapace on every player who is (or is turning into) him -- the suit and the Shield. See {@link CarapaceRender}. */
public class CarapaceLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public CarapaceLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = CarapaceRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		CarapaceRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover);
		if (cover < 0.999f) {
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> CarapaceRender.order(u, v) > cover, true);
		}
		if (cover > 0.6f) {
			CarapaceRender.renderShield(pose, buffers, light, getParentModel(), CarapaceClient.shieldAt(player, partialTick), slim);
		}
	}
}
