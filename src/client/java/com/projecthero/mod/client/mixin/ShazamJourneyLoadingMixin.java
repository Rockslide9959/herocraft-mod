package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.shazam.journey.JourneyClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;

/**
 * v0.17.7 Shazam acquisition (user: "the abrupt change into the grey colour ... and then the darkness"): the journey crosses
 * worlds under a black (or white) fade, but the "Loading terrain" screen used to flash its grey background over it for a few
 * ticks. While a journey scene is playing it is drawn in the fade's colour instead, so the dark stays dark.
 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ShazamJourneyLoadingMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void projecthero$journeyCover(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		int cover = JourneyClient.loadingCover();
		if (cover == 0) {
			cover = com.projecthero.mod.client.blackpanther.plane.AncestralPlaneClient.loadingCover(); // v0.17.7: the Ancestral Plane
		}
		if (cover != 0) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), cover);
			ci.cancel();
		}
	}
}
