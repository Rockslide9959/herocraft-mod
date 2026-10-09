package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.punisher.PunisherLockOnClient;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.15.18: while a Punisher Shift+V lock-on holds the camera ({@link PunisherLockOnClient}), mouse look is thrown away
 * (so nothing piles up to jerk the view once it lets go) and the view is steered onto the target instead.
 */
@Mixin(MouseHandler.class)
public abstract class PunisherLockOnMouseMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void projecthero$punisherLockOn(double movementTime, CallbackInfo ci) {
		if (PunisherLockOnClient.locked()) {
			accumulatedDX = 0.0;
			accumulatedDY = 0.0;
			PunisherLockOnClient.steer();
			ci.cancel();
		}
	}
}
