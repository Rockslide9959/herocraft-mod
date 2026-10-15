package com.projecthero.mod.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.16.5: Ghost Rider on every player who has the Spirit out (every viewer: the state is synced). See {@link GhostRiderRender}. */
public class GhostRiderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public GhostRiderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		GhostRiderRender.Anim anim = GhostRiderRender.anim(player, partialTick);
		if (!anim.anything()) {
			return;
		}
		GhostRiderRender.render(pose, buffers, light, getParentModel(), anim, ageInTicks);
		if (anim.cover() > 0f && anim.cover() < 0.999f) {
			// v0.16.5: the wearer's second layer goes texel by texel with the change, not all at once
			float cover = anim.cover();
			com.projecthero.mod.client.render.OverlayStrips.render(pose, buffers, light, player, getParentModel(),
					(u, v) -> GhostRiderRender.order(u, v) > cover);
		}
	}
}
