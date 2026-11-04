package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.galactus.GalactusCinematicClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/** v0.17.2 Galactus raid v2: no hotbar, crosshair or effect icons over a cutscene's letterbox. */
@Mixin(Gui.class)
public abstract class GalactusCinematicGuiMixin {
	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
	private void projecthero$cinematicHotbar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (GalactusCinematicClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void projecthero$cinematicCrosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (GalactusCinematicClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void projecthero$cinematicEffects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (GalactusCinematicClient.active()) {
			ci.cancel();
		}
	}
}
