package com.projecthero.mod.client.miraculous.ryuko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.ryuko.Ryuko;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.3: Ryuko's suit and sword on every player who has them out, for every viewer ({@link RyukoRender}). */
public class RyukoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public RyukoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = RyukoRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		RyukoRender.renderSuit(pose, buffers, light, getParentModel(), RyukoRender.slim(player), cover);
		if (cover < 0.999f) {
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> RyukoRender.order(u, v) > cover, false);
		}
		if (cover > 0.99f && !Ryuko.swordAway(player) && RyukoSequenceClient.swordOut(player, partialTick)) {
			pose.pushPose();
			getParentModel().rightArm.translateAndRotate(pose);
			placeInHand(pose);
			RyukoRender.renderSword(pose, buffers, light, Ryuko.color(Ryuko.element(player)));
			pose.popPose();
		}
	}

	/** From the arm's shoulder pivot to her fist, the blade angled forward and a little down. */
	public static void placeInHand(PoseStack pose) {
		pose.translate(-1f / 16f, 11.5f / 16f, -0.6f / 16f);
		pose.mulPose(Axis.XP.rotationDegrees(-42f));
	}
}
