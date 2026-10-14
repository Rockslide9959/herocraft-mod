package com.projecthero.mod.client.mixin;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.flight.PhysicsFlight;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/**
 * v0.16.2 Physics Flight: (1) runs the attitude update once a frame, just before the camera reads the player's rotation;
 * (2) rolls the camera about its own forward axis by the flier's roll. The roll goes into the camera's rotation
 * quaternion itself, so the view matrix, the frustum culling, the sky and the particle billboards all roll together,
 * while the HUD (drawn separately) stays level. Applied at the end of every {@code setRotation}, so any later re-aim of
 * the camera (shakes, kicks) keeps the roll.
 */
@Mixin(Camera.class)
public abstract class PhysicsFlightCameraMixin {
	@Shadow
	@Final
	private static Vector3f FORWARDS;

	@Shadow
	@Final
	private static Vector3f UP;

	@Shadow
	@Final
	private static Vector3f LEFT;

	@Shadow
	@Final
	private Quaternionf rotation;

	@Shadow
	@Final
	private Vector3f forwards;

	@Shadow
	@Final
	private Vector3f up;

	@Shadow
	@Final
	private Vector3f left;

	@Inject(method = "setup", at = @At("HEAD"))
	private void projecthero$physicsFlightFrame(BlockGetter level, Entity entity, boolean detached, boolean reverse,
			float partialTick, CallbackInfo ci) {
		PhysicsFlight.frame();
	}

	@Inject(method = "setRotation", at = @At("TAIL"))
	private void projecthero$physicsFlightRoll(float yRot, float xRot, CallbackInfo ci) {
		float roll = PhysicsFlight.cameraRoll();
		if (roll == 0f) {
			return;
		}
		// the camera looks down its local -Z: a right-hand turn about -Z by +roll drops the right side (right wing down)
		this.rotation.rotateZ(-roll * ((float) Math.PI / 180f));
		FORWARDS.rotate(this.rotation, this.forwards);
		UP.rotate(this.rotation, this.up);
		LEFT.rotate(this.rotation, this.left);
	}
}
