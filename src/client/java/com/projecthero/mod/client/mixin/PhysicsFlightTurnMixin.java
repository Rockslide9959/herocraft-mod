package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.flight.PhysicsFlight;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.2 Physics Flight: while the local player flies in Physics mode, mouse look turns them about their own axes
 * ({@link PhysicsFlight}: mouse X rolls or yaws, mouse Y pitches) instead of vanilla's world-locked yaw / pitch.
 */
@Mixin(Entity.class)
public abstract class PhysicsFlightTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void projecthero$physicsFlightTurn(double yRot, double xRot, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && PhysicsFlight.onMouseTurn(player, yRot, xRot)) {
			ci.cancel();
		}
	}
}
