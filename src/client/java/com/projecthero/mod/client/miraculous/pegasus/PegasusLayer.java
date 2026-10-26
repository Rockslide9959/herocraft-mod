package com.projecthero.mod.client.miraculous.pegasus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.pegasus.Pegasus;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;

/**
 * v0.16.6: Pegasus on every player who is (or is turning into) him -- every viewer, the state is synced: the suit, the
 * wearer's own second layer going texel by texel during the change, and the Horseshoe (belt or hand). See
 * {@link PegasusRender}.
 */
public class PegasusLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public PegasusLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = PegasusRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PegasusRender.renderSuit(pose, buffers, light, getParentModel(), slim, cover);
		if (cover < 0.999f) {
			// the wearer's whole second layer -- hair too: the mane hood replaces it -- goes texel by texel with the light
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> PegasusRender.order(u, v) > cover, true);
		}
		if (cover > 0.6f && Pegasus.is(player) && !PegasusSequenceClient.renderShoe(pose, buffers, light, getParentModel(), player, partialTick, slim)) {
			// (v0.17.1: the long transformation shows it on its own beats)
			PegasusRender.renderShoe(pose, buffers, light, getParentModel(), PegasusClient.shoeAt(player, partialTick), slim);
		}
	}
}
