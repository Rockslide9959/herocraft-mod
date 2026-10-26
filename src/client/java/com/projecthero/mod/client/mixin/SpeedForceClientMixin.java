package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.1 Speed Force Speedster Shift+X Phase: vanilla shoves a player who is inside blocks back out toward open space every
 * tick -- while phasing that is exactly where they mean to be, so the shove is skipped.
 */
@Mixin(LocalPlayer.class)
public abstract class SpeedForceClientMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void projecthero$speedForcePhaseNoShove(double x, double z, CallbackInfo ci) {
		if (SpeedForceSpeedster.phasing((LocalPlayer) (Object) this)) {
			ci.cancel();
		}
	}
}
