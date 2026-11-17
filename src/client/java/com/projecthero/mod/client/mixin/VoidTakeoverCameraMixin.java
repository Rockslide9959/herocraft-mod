package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.projecthero.mod.client.sentry.VoidTakeoverClient;

import net.minecraft.client.Camera;

/**
 * v0.17.5 Void takeover rework 2: the forced third-person camera pulls well out (eased between 7 and 14 blocks,
 * {@link VoidTakeoverClient#cameraDistance}) so the player sees the hovering body and the destruction spreading below it.
 * The walls still pull the camera in (vanilla's own clip check runs on the longer distance).
 */
@Mixin(Camera.class)
public abstract class VoidTakeoverCameraMixin {
	@ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
	private float projecthero$voidTakeoverDistance(float distance) {
		float d = VoidTakeoverClient.cameraDistance();
		return d > 0f ? Math.max(distance, d) : distance;
	}
}
