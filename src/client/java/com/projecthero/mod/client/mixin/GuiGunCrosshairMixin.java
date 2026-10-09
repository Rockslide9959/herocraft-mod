package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.firearm.FirearmClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/** v0.15.16: the crosshair is hidden while a Punisher gun is aimed down its sights (see {@link FirearmClient#hidesCrosshair}). */
@Mixin(Gui.class)
public abstract class GuiGunCrosshairMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void projecthero$hideWhenAiming(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (FirearmClient.hidesCrosshair()) {
			ci.cancel();
		}
	}
}
