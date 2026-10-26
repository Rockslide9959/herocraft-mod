package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: vanilla widens the view with the movement speed attribute -- at the Speed Force Speedster's 250 b/s it would
 * fold the world into a fisheye. The view still widens for the sense of speed, but never past {@link #FOV_CAP}.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class SpeedForceFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$capSpeedForceFov(CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValue() > 1.3f && SpeedForceSpeedster.speedActive((Player) (Object) this)) {
			cir.setReturnValue(1.3f); // FOV_CAP
		}
	}
}
