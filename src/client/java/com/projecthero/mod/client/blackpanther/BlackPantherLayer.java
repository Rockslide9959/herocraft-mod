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
		if (player.isInvisible()) {
			return;
		}
		// v0.17.7 (user: "make the black panther necklace model visible when not transformed, should be visible when the necklace is
		// equipped"): drawn whenever the necklace is worn in the chest slot -- over the suit, on the bare skin, and all through the
		// build / retract. On the bare skin its inside faces (2.36 front / back, 4.36 sides) stand clear of the vanilla jacket
		// (2.25 / 4.25); the side runs sit inside the arms' cubes (inner faces at 4, wide and slim alike), never on a face.
		if (BlackPanther.showsNecklace(player)) {
			PlayerModel<AbstractClientPlayer> m = getParentModel();
			BlackPantherRender.renderNecklace(pose, buffers, light, player, m.body);
		}
		if (!BlackPanther.hasPower(player)) {
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
		// v0.17.7 (user: "remove the claw model it looks bad"): no claw blades
	}
}
