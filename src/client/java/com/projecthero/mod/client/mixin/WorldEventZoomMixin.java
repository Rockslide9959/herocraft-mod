package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.oathbreaker.WorldEventZoomClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A brief, cosmetic FOV narrow for a world event building up to something (currently: the
 * Oathbreaker's summon) -- same injection point as {@link GunFovMixin}, composed multiplicatively with
 * whatever that one already returned so the two never fight over the FOV.
 */
@Mixin(GameRenderer.class)
public abstract class WorldEventZoomMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void projecthero$worldEventZoom(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Double> cir) {
		float factor = WorldEventZoomClient.factor();
		if (factor >= 0.999f) {
			return;
		}
		cir.setReturnValue(cir.getReturnValueD() * factor);
	}
}
