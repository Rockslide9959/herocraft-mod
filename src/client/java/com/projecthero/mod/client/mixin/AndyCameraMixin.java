package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.andy.AndyFirstPerson;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** v0.17.3: Andy's boost in first person -- a FOV kick and a slight shake while he tears along at 40 b/s. */
@Mixin(GameRenderer.class)
public abstract class AndyCameraMixin {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private double projecthero$andyBoostFov(double fov, Camera camera, float partial, boolean useSetting) {
		return useSetting ? fov * AndyFirstPerson.fovScale(partial) : fov;
	}

	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void projecthero$andyBoostShake(PoseStack pose, float partial, CallbackInfo ci) {
		AndyFirstPerson.shake(pose, partial);
	}
}
