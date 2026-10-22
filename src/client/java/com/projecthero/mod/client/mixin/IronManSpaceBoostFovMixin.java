package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.IronManFlightFxClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.16.7 (user): the Mk VIII Flight Boost's FOV kick -- the local player's view widens hard as the boost kicks in, then
 * settles a little wider while it runs ({@link IronManFlightFxClient#boostFovFactor}; vanilla eases the change).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class IronManSpaceBoostFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$spaceBoostFov(CallbackInfoReturnable<Float> cir) {
		if ((Object) this == Minecraft.getInstance().player) {
			float k = IronManFlightFxClient.boostFovFactor();
			if (k != 1.0f) {
				cir.setReturnValue(cir.getReturnValueF() * k);
			}
		}
	}
}
