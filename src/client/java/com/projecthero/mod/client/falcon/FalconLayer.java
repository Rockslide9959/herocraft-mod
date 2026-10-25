package com.projecthero.mod.client.falcon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.falcon.Falcon;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * v0.17.1: the Falcon on every player wearing the full set (every viewer: the worn armour is synced) -- the suit
 * ({@link FalconRender}) over the wearer's own skin with their hat layer (hair, hats) kept and the goggles over it, and the
 * EXO-7 Wing Pack on the back ({@link FalconGeometry}): wings folded down the back on the ground, spread in flight, wrapped
 * round in front for the Wing Shield, the jets lit while boosting.
 */
public class FalconLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public FalconLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !FalconRender.suited(player)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> parent = getParentModel();
		// the wearer's hat layer (hair, hats) stays: the core hides every overlay part while a suit shows, so draw it again;
		// the suit's own hat layer (the raised goggles and strap) sits just outside it
		if (player.isModelPartShown(PlayerModelPart.HAT)) {
			boolean was = parent.hat.visible;
			parent.hat.visible = true;
			parent.hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light,
					LivingEntityRenderer.getOverlayCoords(player, 0f));
			parent.hat.visible = was;
		}
		FalconRender.render(pose, buffers, light, player, parent);
		// the Wing Pack
		float[] w = FalconRender.wings(player);
		float jets = Falcon.boosting(player) ? 1f : 0f;
		float flap = FalconFlightClient.flap(player, partialTick);
		pose.pushPose();
		parent.body.translateAndRotate(pose);
		FalconGeometry.wingPack(pose, buffers, light, w[0], w[1], flap, jets, ageInTicks);
		pose.popPose();
	}
}
