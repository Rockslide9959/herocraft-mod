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
			boolean slim = CyborgRender.slim(player);
			// v0.17.7: under the hologram both layers of the plates go at its line (CyborgRender.renderUnderHologram)
			CyborgRender.render(pose, buffers, light, getParentModel(), anim, ageInTicks, slim);
			if (anim.hologramShowing()) {
				// v0.16.5 rework: the hologram of his old skin building / draining over the plates
				CyborgRender.renderHologram(pose, buffers, light, player, getParentModel(), anim, ageInTicks);
				if (!flesh) {
					// v0.17.7: ahead of the hologram, the wearer's own second layer where the plates have gaps
					float line = anim.holo();
					boolean building = anim.building();
					com.projecthero.mod.client.render.OverlayStrips.render(pose, buffers, light, player, getParentModel(),
							(u, v) -> CyborgRender.holoTint(u, v, line, building) == 0 && CyborgRender.gap(slim, u, v), false);
				}
			} else if (!flesh) {
				// v0.16.5: the wearer's second layer goes texel row by texel row as the plates spread, not all at once;
				// v0.17.7: and stays where both of the plates' layers are clear (the wearer's skin shows between the parts)
				float spread = anim.spread() / CyborgRender.STEPS;
				com.projecthero.mod.client.render.OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> {
					float o = CyborgRender.convertOrder(u, v);
					return o < 1.5f && (o > spread || CyborgRender.gap(slim, u, v));
				}, false);
			}
		}
	}
}
