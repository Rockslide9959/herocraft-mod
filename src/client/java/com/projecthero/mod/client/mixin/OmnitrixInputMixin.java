package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.OmnitrixClient;

import net.minecraft.client.Minecraft;

/**
 * v0.17.1: the Omnitrix's clicks. While the watch is up a left- or right-click slaps the core down (never attacking, using or
 * mining); while transformed a right-click on air with an empty hand reaches the alien kit's "use".
 */
@Mixin(Minecraft.class)
public abstract class OmnitrixInputMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixSlapAttack(CallbackInfoReturnable<Boolean> cir) {
		if (OmnitrixClient.onClick(false)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixNoMining(boolean leftClick, CallbackInfo ci) {
		if (OmnitrixClient.blocksHeldAttack()) {
			ci.cancel();
		}
	}

	/** v0.17.5: no pick-block while the watch is in function mode (the middle button types the code; a rebound pick key too). */
	@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixNoPick(CallbackInfo ci) {
		if (com.projecthero.mod.client.omnitrix.OmnitrixFunctionClient.blocksPick()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixSlapUse(CallbackInfo ci) {
		if (OmnitrixClient.onClick(true) || OmnitrixClient.onUseAir()) {
			ci.cancel();
		}
	}
}
