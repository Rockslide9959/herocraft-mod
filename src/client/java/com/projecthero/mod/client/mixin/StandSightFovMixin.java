package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.stand.StandClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** v0.17.2 Stands: Precision Sight's telescopic zoom on the world view (not the hand). */
@Mixin(GameRenderer.class)
public abstract class StandSightFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void projecthero$precisionSightZoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		if (useFovSetting && StandClient.sightActive()) {
			cir.setReturnValue(cir.getReturnValueD() * 0.3);
		}
	}
}
