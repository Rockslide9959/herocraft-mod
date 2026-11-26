package com.projecthero.mod.client.cyborg;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.16.5: Cyborg's extras on every player with the power (every viewer: the state is synced). v0.17.7 round 2: Cyborg himself
 * (and the flesh, and the hologram of his old skin) is his skin now, drawn by vanilla at his normal size ({@link CyborgSkins});
 * this layer adds the jetpack / arm tech, the eye's glow and the hologram projector. See {@link CyborgRender}.
 */
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
		// v0.16.5 (user): the jetpack he builds on his back for Jet Flight, and the blaster for the sonic moves
		CyborgTech.renderJetpack(pose, buffers, light, player, getParentModel(), partialTick, ageInTicks);
		CyborgTech.renderArmTech(pose, buffers, light, player, getParentModel(), partialTick, ageInTicks,
				player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM);
		if (anim.anything() && CyborgSkins.texture(player) != null) {
			// v0.17.7 round 2: the eye's glow on his own head / hat parts, at their normal size
			CyborgRender.renderGlow(pose, buffers, getParentModel(), anim, ageInTicks, CyborgRender.slim(player));
		}
		if (anim.hologramShowing()) {
			// v0.16.5 rework: the forehead projector's light while the hologram of his old skin builds
			CyborgRender.renderHologram(pose, buffers, light, player, getParentModel(), anim, ageInTicks);
		}
	}
}
