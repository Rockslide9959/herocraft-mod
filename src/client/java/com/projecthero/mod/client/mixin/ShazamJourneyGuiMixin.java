package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.shazam.journey.JourneyClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/** v0.17.7 Shazam acquisition: no hotbar, crosshair, chat or effect icons over the journey's cutscenes. */
@Mixin(Gui.class)
public abstract class ShazamJourneyGuiMixin {
	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
	private void projecthero$journeyHotbar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (JourneyClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void projecthero$journeyCrosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (JourneyClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderChat", at = @At("HEAD"), cancellable = true, require = 0)
	private void projecthero$journeyChat(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (JourneyClient.active() && !JourneyClient.prompting()) { // at the prompt they may type the word in chat
			ci.cancel();
		}
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void projecthero$journeyEffects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (JourneyClient.active()) {
			ci.cancel();
		}
	}
}
