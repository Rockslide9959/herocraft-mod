package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.andy.AndyMotion;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3: Andy's whole-body motion ({@link AndyMotion}): the flight's torpedo line, the rolling head, the hand walk lowered
 * onto its hands, the hops and the limp. Every viewer (it reads only the synced state and the player's movement).
 */
@Mixin(PlayerRenderer.class)
public abstract class AndyBodyRotationMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$andyMotion(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks,
			float scale, CallbackInfo ci) {
		AndyMotion.apply(player, poseStack, partialTicks, scale);
	}
}
