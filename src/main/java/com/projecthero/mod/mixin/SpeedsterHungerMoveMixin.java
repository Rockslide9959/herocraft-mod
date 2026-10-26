package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1: the half of {@link SpeedsterHungerMixin} on {@code ServerPlayer} (where {@code checkMovementStatistics} lives):
 * notes the horizontal distance being charged for while vanilla hands out the movement exhaustion.
 */
@Mixin(ServerPlayer.class)
public abstract class SpeedsterHungerMoveMixin {
	@Inject(method = "checkMovementStatistics", at = @At("HEAD"))
	private void projecthero$movedStart(double dx, double dy, double dz, CallbackInfo ci) {
		com.projecthero.mod.speedforce.SpeedsterHunger.movedNow = Math.sqrt(dx * dx + dz * dz);
	}

	@Inject(method = "checkMovementStatistics", at = @At("RETURN"))
	private void projecthero$movedEnd(double dx, double dy, double dz, CallbackInfo ci) {
		com.projecthero.mod.speedforce.SpeedsterHunger.movedNow = -1.0;
	}
}
