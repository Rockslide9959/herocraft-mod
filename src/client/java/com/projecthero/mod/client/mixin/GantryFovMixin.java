package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.ironman.GantryClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.15.5, user request ("don't zoom the player's view in"): a Stark Gantry sequence pins its wearer by zeroing their
 * movement speed, and vanilla derives the FOV from movement speed -- the view zoomed right in for the whole suit-up.
 * Hold the FOV neutral while the gantry holds the local player (the same fix as the Hulk's kneeling change).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class GantryFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
	private void projecthero$noGantryZoom(CallbackInfoReturnable<Float> cir) {
		if ((Object) this == Minecraft.getInstance().player && GantryClient.holdFov()) {
			cir.setReturnValue(1.0f);
		}
	}
}
