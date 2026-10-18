package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.miraculous.catnoir.CatNoirPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.7 (user): Cat Noir's Rooftop Run sprint, on all fours like in the show -- the body half (the limbs are set in
 * {@link CatNoirPose#allFours} / {@code CatNoirPose.apply}). The whole model tips forward flat, face down, pivoting at
 * the height of his legs' length so the arms (front legs) and legs (back legs), pointed straight "down" in the tipped
 * frame, reach the ground. Eased by the same blend, so it folds in and out smoothly; every viewer sees it (it reads
 * only the synced Rooftop Run flag, the sprint flag and the position).
 */
@Mixin(PlayerRenderer.class)
public abstract class CatNoirAllFoursMixin {
	/** The limbs' length (12 px), in blocks: the flat body rides this high. */
	private static final float LEG = 0.78f;

	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$catNoirAllFours(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float f = CatNoirPose.allFours(player, partialTicks);
		if (f < 0.01f) {
			return;
		}
		float pivot = LEG * scale;
		poseStack.translate(0.0f, pivot, 0.0f);
		// negative X tips the head forward (see MoonKnightGlidePoseMixin)
		poseStack.mulPose(Axis.XP.rotationDegrees(-90f * f));
		poseStack.translate(0.0f, -pivot, 0.0f);
	}
}
