package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.beamclash.BeamClashClient;

import net.minecraft.client.Minecraft;

/**
 * v0.17.3: during a beam clash a left click pushes the beam: it never swings, attacks or mines. v0.17.5: right click does nothing,
 * and Escape (or anything else that would open the pause menu) forfeits the clash instead of pausing.
 */
@Mixin(Minecraft.class)
public abstract class BeamClashInputMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashMash(CallbackInfoReturnable<Boolean> cir) {
		if (BeamClashClient.onClick()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashNoMining(boolean leftClick, CallbackInfo ci) {
		if (BeamClashClient.inClash()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashNoUse(CallbackInfo ci) {
		if (BeamClashClient.inClash()) {
			ci.cancel();
		}
	}

	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void projecthero$beamClashForfeit(boolean pauseOnly, CallbackInfo ci) {
		if (BeamClashClient.onPause()) {
			ci.cancel();
		}
	}
}
