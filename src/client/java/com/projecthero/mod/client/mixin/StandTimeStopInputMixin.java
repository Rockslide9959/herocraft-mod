package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.stand.TimeStopClient;

import net.minecraft.client.MouseHandler;

/** v0.17.2 Star Platinum: The World -- a frozen player can't even turn their head. */
@Mixin(MouseHandler.class)
public abstract class StandTimeStopInputMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeStopTurn(double movementTime, CallbackInfo ci) {
		if (TimeStopClient.localFrozen()) {
			ci.cancel();
		}
	}
}
