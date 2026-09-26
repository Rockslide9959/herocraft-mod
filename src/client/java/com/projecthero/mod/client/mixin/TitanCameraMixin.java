package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.titanshifter.TitanShakeClient;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/**
 * Two Titan camera touches: the third-person camera of a Titan's rider pulls back so the whole 11-block
 * body is in frame, and every nearby player's view trembles with the Titan's footfalls and impacts
 * ({@link TitanShakeClient}).
 */
@Mixin(Camera.class)
public abstract class TitanCameraMixin {
	@Shadow
	protected abstract void move(float distanceOffset, float verticalOffset, float horizontalOffset);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float getYRot();

	@Shadow
	public abstract float getXRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$titanCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
			float partialTick, CallbackInfo ci) {
		if (detached && entity != null && TitanFormEntity.isOwnerRider(entity) && entity.getVehicle() instanceof TitanFormEntity form) {
			// v0.12.32: further back and lower (the camera starts at the Titan's eyes, ~10 blocks up) so the whole
			// body -- feet included -- is in frame, not just the head and shoulders.
			move(-form.getBbHeight() * 1.1f, -form.getBbHeight() * 0.3f, 0.0f);
		}
		float dy = TitanShakeClient.offsetYaw();
		float dx = TitanShakeClient.offsetPitch();
		if (dy != 0.0f || dx != 0.0f) {
			setRotation(getYRot() + dy, getXRot() + dx);
		}
	}
}
