package com.projecthero.mod.client.bluebeetle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.16.5: Blue Beetle's armour on every player who has it out (every viewer), see {@link BlueBeetleRender}; the arm
 * blaster, twin cannon and hammer ({@link BlueBeetleTech}); and the Scarab itself, a voxel scarab fixed between the
 * shoulder blades of anyone bonded to Khaji-Da, armour on or off (the user asked for it to stay on the player).
 */
public class BlueBeetleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public BlueBeetleLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !BlueBeetle.hasPower(player)) {
			return;
		}
		BlueBeetleRender.Anim anim = BlueBeetleRender.anim(player, partialTick);
		if (anim.anything()) {
			BlueBeetleRender.render(pose, buffers, light, getParentModel(), player, anim, partialTick, ageInTicks);
			if (!anim.full()) {
				// v0.16.5: the wearer's second layer goes texel by texel in step with the armour's build
				float cover = anim.cover();
				OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> BlueBeetleRender.order(u, v) > cover);
			}
			if (anim.full()) {
				BlueBeetleTech.renderArmTech(pose, buffers, light, player, getParentModel(), partialTick);
			}
		}
		BlueBeetleTech.renderScarab(pose, buffers, light, getParentModel());
	}
}
