package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.doctorfate.DoctorFateClient;

import net.minecraft.client.MouseHandler;

/** v0.17.3 Doctor Fate: a possessed player looks where Nabu looks -- their own mouse does not turn them. */
@Mixin(MouseHandler.class)
public abstract class FateMouseMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void projecthero$nabuHoldsTheView(double movementTime, CallbackInfo ci) {
		if (DoctorFateClient.localPossessed()) {
			ci.cancel();
		}
	}
}
