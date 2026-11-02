package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.alien.GreyMatterClient;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.2 Grey Matter revamp, Inside Job: the 4-tick shrink into the creature ({@link GreyMatterClient#insideJitter}). The player
 * renderer overrides {@code scale} without calling up, so {@code GreyMatterInsideRenderMixin}'s hook on the living renderer
 * never sees a player.
 */
@Mixin(PlayerRenderer.class)
public abstract class GreyMatterInsideShrinkMixin {
	@Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
	private void projecthero$greyMatterShrink(AbstractClientPlayer player, PoseStack pose, float partial, CallbackInfo ci) {
		GreyMatterClient.insideJitter(player, pose, partial);
	}
}
