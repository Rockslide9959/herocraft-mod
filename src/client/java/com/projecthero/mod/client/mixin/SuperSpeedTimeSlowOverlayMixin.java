package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.TimeSlowOverlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Super Speed Time Slow (v0.14.7): the screen effect is drawn with the camera overlays (vignette, pumpkin, portal) --
 * over the world, under the crosshair, hotbar, chat and every other HUD element.
 */
@Mixin(Gui.class)
public abstract class SuperSpeedTimeSlowOverlayMixin {
	@Inject(method = "renderCameraOverlays", at = @At("TAIL"))
	private void projecthero$timeSlowOverlay(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		TimeSlowOverlay.render(graphics, delta.getGameTimeDeltaPartialTick(false));
	}
}
