package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.quirk.QuirkCameraFx;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** v0.17.1 Quirk framework: the Quirk FOV punch ({@link QuirkCameraFx#fovMultiplier}) on the world view (not the hand). */
@Mixin(GameRenderer.class)
public abstract class QuirkFovPunchMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void projecthero$quirkFovPunch(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		if (!useFovSetting) {
			return;
		}
		float m = QuirkCameraFx.fovMultiplier(partialTick);
		if (m != 1f) {
			cir.setReturnValue(cir.getReturnValueD() * m);
		}
	}
}
