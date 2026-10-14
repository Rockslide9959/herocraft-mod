package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.hulk.HulkRampageClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * v0.16.4 (user): "when the hulk rampages make the players screen zoom out and get smaller and have a little black box around
 * the screen and put the minigame at the bottom in the black". Before the HUD is drawn, the finished world picture is shrunk
 * into its inset ({@link HulkRampageClient#shrinkWorld}); while the inset shows, the hotbar, crosshair, experience level and
 * effect icons are left out so the black strip at the bottom is the minigame's alone.
 */
@Mixin(Gui.class)
public abstract class GuiHulkRampageMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void projecthero$shrinkWorldForRampage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		HulkRampageClient.shrinkWorld();
	}

	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
	private void projecthero$noHotbarInRampage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (HulkRampageClient.insetShowing()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void projecthero$noCrosshairInRampage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (HulkRampageClient.insetShowing()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void projecthero$noLevelInRampage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (HulkRampageClient.insetShowing()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void projecthero$noEffectsInRampage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (HulkRampageClient.insetShowing()) {
			ci.cancel();
		}
	}
}
