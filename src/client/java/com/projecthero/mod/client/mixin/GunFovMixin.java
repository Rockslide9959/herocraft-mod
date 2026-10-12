package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.firearm.FirearmClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aiming a firearm down its sights narrows the FOV. The zoom factor comes from the held weapon's
 * {@code zoomLevels} ({@link com.projecthero.mod.firearm.FirearmData}) -- a slight pull-in for the
 * pistol/rifle, and the sniper's 3x/6x/10x steps. Smoothly interpolated so the camera eases rather
 * than snapping.
 */
@Mixin(GameRenderer.class)
public abstract class GunFovMixin {
	private float projecthero$currentZoom = 1.0f;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void projecthero$firearmZoom(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Double> cir) {
		if (useFovSetting) {
			// v0.16.1: the Green Lantern Sniper Rifle scope -- the world view only (the hand pass would magnify the arm),
			// eased on its own so the two calls a frame (world + hand) never fight over one smoothing value
			float sniper = com.projecthero.mod.client.greenlantern.GreenLanternSniperClient.zoomFactor();
			projecthero$sniperZoom += (sniper - projecthero$sniperZoom) * 0.35f;
			if (Math.abs(projecthero$sniperZoom - 1.0f) < 0.002f) {
				projecthero$sniperZoom = 1.0f;
			} else {
				cir.setReturnValue(cir.getReturnValueD() * projecthero$sniperZoom);
			}
		}
		float target = FirearmClient.zoomFactor();
		projecthero$currentZoom += (target - projecthero$currentZoom) * 0.35f;
		if (Math.abs(projecthero$currentZoom - 1.0f) < 0.002f) {
			projecthero$currentZoom = 1.0f;
			return;
		}
		cir.setReturnValue(cir.getReturnValueD() * projecthero$currentZoom);
	}

	private float projecthero$sniperZoom = 1.0f;
}
