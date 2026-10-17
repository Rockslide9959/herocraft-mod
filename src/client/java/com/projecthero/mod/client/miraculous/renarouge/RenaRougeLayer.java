package com.projecthero.mod.client.miraculous.renarouge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;

/**
 * v0.16.6: Rena Rouge on every player who is (or is turning into) her -- every viewer, the state is synced. The suit,
 * the fox tail and ears (they grow in over the last part of the transformation) and the Flute. See {@link RenaRougeRender}.
 */
public class RenaRougeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public RenaRougeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = RenaRougeRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		RenaRougeRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover, 1f, 0xFFFFFF);
		if (cover < 0.999f) {
			// the wearer's jacket / sleeves / trousers go texel by texel with the light (her hat layer stays: vanilla draws it)
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> RenaRougeRender.order(u, v) > cover, false);
		}
		float grow = Mth.clamp((cover - 0.7f) / 0.3f, 0f, 1f);
		RenaRougeRender.renderFoxParts(pose, buffers, light, getParentModel(), grow, ageInTicks, Mth.clamp(limbSwingAmount, 0f, 1f), 1f, 0xFFFFFF);
		if (cover > 0.6f && RenaRouge.is(player)) {
			RenaRougeRender.renderFlute(pose, buffers, light, getParentModel(), RenaRougeClient.fluteAt(player, partialTick), slim, 1f);
		}
	}
}
