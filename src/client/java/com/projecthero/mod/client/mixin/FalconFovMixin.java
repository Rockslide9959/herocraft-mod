package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.falcon.FalconClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** v0.17.1: the Falcon's Tactical Goggles zoom -- holding V with the goggles on narrows the local player's view. */
@Mixin(AbstractClientPlayer.class)
public abstract class FalconFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$falconGogglesZoom(CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		float scale = FalconClient.fovScale();
		if (scale < 1f) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}
}
