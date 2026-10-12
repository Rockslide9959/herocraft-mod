package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.greenlantern.GreenLanternSniperClient;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.1: while the Green Lantern Sniper Rifle construct is out, attack charges / fires it and use scopes it (sent by
 * {@link GreenLanternSniperClient}) -- so vanilla's swing / mine / hit and use / place must not also happen. Gated
 * strictly on {@link GreenLanternSniperClient#active()}.
 */
@Mixin(Minecraft.class)
public abstract class GreenLanternSniperInputMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$sniperStartAttack(CallbackInfoReturnable<Boolean> cir) {
		if (GreenLanternSniperClient.active()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$sniperContinueAttack(boolean leftClick, CallbackInfo ci) {
		if (GreenLanternSniperClient.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$sniperStartUse(CallbackInfo ci) {
		if (GreenLanternSniperClient.active()) {
			ci.cancel();
		}
	}
}
