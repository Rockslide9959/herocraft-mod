package com.projecthero.mod.client.blackpanther;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.3: Black Panther's suit and necklace on every player who has the power, for every viewer ({@link BlackPantherRender}). */
public class BlackPantherLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public BlackPantherLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !BlackPanther.hasPower(player)) {
			return;
		}
		BlackPantherRender.Anim anim = BlackPantherRender.anim(player, partialTick);
		if (anim.anything()) {
			BlackPantherRender.render(pose, buffers, light, getParentModel(), player, anim, ageInTicks);
			if (!anim.full()) {
				// the wearer's second layer goes texel by texel in step with the suit
				float cover = anim.cover();
				OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> BlackPantherRender.order(u, v) > cover);
			}
		}
		// v0.17.3 tester round: the necklace stays on show over the full suit (drawn after it, proud of it) -- unless it is off
		if (!BlackPanther.necklaceOff(player) || anim.anything()) { // (taken off while suited: it goes once the suit has drained into it)
			BlackPantherRender.renderNecklace(pose, buffers, light, player, getParentModel().body);
		}
		if (anim.full() && BlackPantherRender.clawsShown(player)) {
			BlackPantherRender.renderClaws(pose, buffers, light, player, getParentModel());
		}
	}
}
