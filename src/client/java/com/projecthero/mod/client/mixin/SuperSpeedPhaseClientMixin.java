package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.client.player.LocalPlayer;

/**
 * Super Speed Shift+C Phase (v0.14.5): vanilla shoves a player who is inside blocks back out toward open space
 * every tick. While phasing that is exactly where you want to be, so the shove is skipped.
 */
@Mixin(LocalPlayer.class)
public abstract class SuperSpeedPhaseClientMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void projecthero$speedPhaseNoShove(double x, double z, CallbackInfo ci) {
		if (SuperSpeedHandlers.phasing((LocalPlayer) (Object) this)) {
			ci.cancel();
		}
	}
}
