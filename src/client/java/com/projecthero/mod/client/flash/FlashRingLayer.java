package com.projecthero.mod.client.flash;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.HandRing;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.14.11: the gold Flash Ring on the right hand, drawn for anyone with a ring on (every viewer: the attachment is
 * synced). v0.14.13: a real little ring (gold band, red bezel, yellow bolt-gem) on the shared {@link HandRing} finger --
 * the same spot as the Green Lantern's ring -- and drawn in first person too ({@link #render(PoseStack,
 * MultiBufferSource, AbstractClientPlayer, ModelPart, float)} from {@code GreenLanternRingHandMixin}). Only shown while
 * the suit is stored in it.
 */
public class FlashRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public FlashRingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/**
	 * Whether {@code player} has a Flash Ring to draw. v0.14.13: only while the suit is packed inside it -- the moment any
	 * Flash Suit piece is on (the suit is out), the ring is not shown.
	 */
	public static boolean wearing(AbstractClientPlayer player) {
		return !player.isInvisible() && FlashRing.holdsSuit(player) && !FlashSuit.wearsAny(player);
	}

	/** Draws the ring on {@code arm} (the right arm, posed as it was just drawn); {@code pose} is not yet in the arm. */
	public static void render(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, ModelPart arm, float partialTick) {
		if (!wearing(player)) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		HandRing.draw(pose, HandRing.buffer(buffers), HandRing.SKIN_GAP, HandRing.FLASH); // never over the suit (see wearing)
		pose.popPose();
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		render(poseStack, buffers, player, getParentModel().rightArm, partialTick);
	}
}
