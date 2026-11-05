package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienBodyTurn;
import com.projecthero.mod.client.omnitrix.AlienClients;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.17.2: an alien's whole-body move turns ({@link AlienBodyTurn}). */
@Mixin(PlayerRenderer.class)
public abstract class OmnitrixBodyTurnMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$alienBodyTurn(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks,
			float scale, CallbackInfo ci) {
		if (AlienClients.shownKit(player) instanceof AlienBodyTurn turn) {
			turn.bodyTurn(player, poseStack, partialTicks);
		}
	}
}
