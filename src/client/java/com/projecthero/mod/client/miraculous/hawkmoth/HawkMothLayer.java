package com.projecthero.mod.client.miraculous.hawkmoth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.16.6: Hawk Moth on every player who holds Nooroo (every viewer: the state is synced) -- the suit
 * ({@link HawkMothSuitRender}), the wearer's own hat layer (kept on) plus, mid-change, the rest of his second layer texel
 * by texel, and the cane in his hand ({@link CaneRender}).
 */
public class HawkMothLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public HawkMothLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = HawkMothSuitRender.progress(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		// the wearer's hat layer always (vanilla's is hidden while a suit shows); jacket / sleeves / trousers until the wave reaches them
		OverlayStrips.render(pose, buffers, light, player, getParentModel(),
				(u, v) -> (v < 16 && u < 32) || HawkMothSuitRender.order(u, v) > cover);
		HawkMothSuitRender.render(pose, buffers, light, player, getParentModel(), cover);
		if (HawkMothPose.caneShown(player, partialTick)) {
			CaneRender.render(pose, buffers, light, getParentModel(), player, partialTick);
		}
	}
}
