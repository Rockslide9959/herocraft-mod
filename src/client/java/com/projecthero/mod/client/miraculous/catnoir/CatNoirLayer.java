package com.projecthero.mod.client.miraculous.catnoir;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.16.6: Cat Noir on every player transformed as him (every viewer: the state is synced). See {@link CatNoirRender}. */
public class CatNoirLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public CatNoirLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = CatNoirRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		CatNoirRender.render(pose, buffers, light, player, getParentModel(), cover, limbSwing, limbSwingAmount, ageInTicks, partialTick);
		// the wearer's own second layer: the hat (their hair) always stays; the jacket, sleeves and trousers go texel by
		// texel with the change (the vanilla overlay parts are hidden while any of the suit shows)
		OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> v < 16 || CatNoirRender.order(u, v) > cover);
		CatNoirRender.renderBaton(pose, buffers, light, player, getParentModel(), ageInTicks, partialTick);
	}
}
