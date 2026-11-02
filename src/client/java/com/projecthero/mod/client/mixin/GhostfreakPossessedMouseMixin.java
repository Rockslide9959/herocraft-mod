package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.omnitrix.alien.GhostfreakClient;

import net.minecraft.client.MouseHandler;

/** v0.17.2: a possessed player looks where Ghostfreak looks -- their own mouse does not turn them. */
@Mixin(MouseHandler.class)
public abstract class GhostfreakPossessedMouseMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void projecthero$ghostfreakHoldsTheView(double movementTime, CallbackInfo ci) {
		if (GhostfreakClient.localPossessed()) {
			ci.cancel();
		}
	}
}
