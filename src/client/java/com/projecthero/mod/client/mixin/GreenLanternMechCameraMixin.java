package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.greenlantern.mech.GreenLanternMechClient;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/**
 * v0.17.1: the Hard-Light Mech's camera -- in third person the pilot's camera pulls well back (the camera starts at the
 * pilot's eyes in the chest cockpit) so the whole mech (7.5 blocks since the v0.17.1 Gurren rework) is in frame; and everyone near one feels its footfalls
 * and the Ground Slam ({@link GreenLanternMechClient}).
 */
@Mixin(Camera.class)
public abstract class GreenLanternMechCameraMixin {
	@Shadow
	protected abstract void move(float distanceOffset, float verticalOffset, float horizontalOffset);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float getYRot();

	@Shadow
	public abstract float getXRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$mechCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
			float partialTick, CallbackInfo ci) {
		if (detached && HardLightMechEntity.isPilot(entity)) {
			move(-7.4f, 1.0f, 0.0f); // v0.17.1 Gurren rework: back a little further for the taller mech
		}
		float dy = GreenLanternMechClient.shakeYaw();
		float dx = GreenLanternMechClient.shakePitch();
		if (dy != 0.0f || dx != 0.0f) {
			setRotation(getYRot() + dy, getXRot() + dx);
		}
	}
}
