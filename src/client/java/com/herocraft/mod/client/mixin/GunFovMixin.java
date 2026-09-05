package com.herocraft.mod.client.mixin;

import com.herocraft.mod.client.firearm.FirearmClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aiming a firearm down its sights narrows the FOV. The zoom factor comes from the held weapon's
 * {@code zoomLevels} ({@link com.herocraft.mod.firearm.FirearmData}) -- a slight pull-in for the
 * pistol/rifle, and the sniper's 3x/6x/10x steps. Smoothly interpolated so the camera eases rather
 * than snapping.
 */
@Mixin(GameRenderer.class)
public abstract class GunFovMixin {
	private float herocraft$currentZoom = 1.0f;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void herocraft$firearmZoom(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Double> cir) {
		float target = FirearmClient.zoomFactor();
		herocraft$currentZoom += (target - herocraft$currentZoom) * 0.35f;
		if (Math.abs(herocraft$currentZoom - 1.0f) < 0.002f) {
			herocraft$currentZoom = 1.0f;
			return;
		}
		cir.setReturnValue(cir.getReturnValueD() * herocraft$currentZoom);
	}
}
