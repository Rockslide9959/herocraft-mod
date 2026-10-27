package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.quirk.QuirkCameraFx;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/** v0.17.1 Quirk framework: the Quirk camera shake ({@link QuirkCameraFx}) on the view, after vanilla set it up. */
@Mixin(Camera.class)
public abstract class QuirkCameraShakeMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float getYRot();

	@Shadow
	public abstract float getXRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$quirkShake(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		float dy = QuirkCameraFx.yaw(partialTick);
		float dx = QuirkCameraFx.pitch(partialTick);
		if (dy != 0f || dx != 0f) {
			setRotation(getYRot() + dy, getXRot() + dx);
		}
	}
}
