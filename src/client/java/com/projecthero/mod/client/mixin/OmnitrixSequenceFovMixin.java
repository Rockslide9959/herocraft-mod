package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** v0.17.2: the long Omnitrix transformation's shots have their own field of view (the root's slowness would zoom it). */
@Mixin(GameRenderer.class)
public abstract class OmnitrixSequenceFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void projecthero$omnitrixSequenceFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		if (useFovSetting) {
			double fov = OmnitrixSequenceClient.fov(partialTick);
			if (fov > 0) {
				cir.setReturnValue(fov);
			}
		}
	}
}
