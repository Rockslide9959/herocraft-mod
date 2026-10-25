package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.1 Silver Surfer Shift+Z Molecular Phase: vanilla shoves a player who is inside blocks back out toward open space
 * every tick -- while phased that is exactly where he means to be, so the shove is skipped.
 */
@Mixin(LocalPlayer.class)
public abstract class SilverSurferPhaseClientMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void projecthero$surferPhaseNoShove(double x, double z, CallbackInfo ci) {
		if (SilverSurfer.phasing((LocalPlayer) (Object) this)) {
			ci.cancel();
		}
	}
}
