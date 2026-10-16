package com.projecthero.mod.client.cyborg;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.16.5: Cyborg's plates (and his hologram) on every player with the power (every viewer: the state is synced). See {@link CyborgRender}. */
public class CyborgLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public CyborgLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		CyborgRender.Anim anim = CyborgRender.anim(player, partialTick);
		// v0.16.5 (user): raw flesh after the Mother Box blast, and under the plates while they build
		boolean flesh = com.projecthero.mod.cyborg.Cyborg.fleshShows(player);
		if (flesh) {
			CyborgRender.renderFlesh(pose, buffers, light, getParentModel());
		}
		// v0.16.5 (user): the jetpack he builds on his back for Jet Flight, and the blaster for the sonic moves
		CyborgTech.renderJetpack(pose, buffers, light, player, getParentModel(), partialTick, ageInTicks);
		CyborgTech.renderArmTech(pose, buffers, light, player, getParentModel(), partialTick, ageInTicks,
				player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM);
		if (anim.anything()) {
			CyborgRender.render(pose, buffers, light, getParentModel(), anim, ageInTicks);
			if (anim.hologramShowing()) {
				// v0.16.5 rework: the hologram of his old skin building / draining over the plates
				CyborgRender.renderHologram(pose, buffers, light, player, getParentModel(), anim, ageInTicks);
			} else if (!flesh && anim.spread() < CyborgRender.STEPS - 0.01f) {
				// v0.16.5: the wearer's second layer goes texel row by texel row as the plates spread, not all at once
				float spread = anim.spread();
				com.projecthero.mod.client.render.OverlayStrips.render(pose, buffers, light, player, getParentModel(),
						(u, v) -> CyborgRender.convertOrder(u, v) > spread / CyborgRender.STEPS && CyborgRender.convertOrder(u, v) < 1.5f, false);
			}
		}
	}
}
