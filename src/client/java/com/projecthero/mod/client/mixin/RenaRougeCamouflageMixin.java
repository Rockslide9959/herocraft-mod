package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.6: Fox Camouflage -- nothing of a camouflaged Rena Rouge is drawn for anyone: not the body, not the suit, not
 * her armour, her held items, the Flute or her name (vanilla invisibility still draws worn armour and held items).
 */
@Mixin(PlayerRenderer.class)
public abstract class RenaRougeCamouflageMixin {
	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$renaRougeCamouflage(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (RenaRouge.camouflaged(player)) {
			ci.cancel();
		}
	}
}
