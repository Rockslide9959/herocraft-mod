package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.undead.UndeadFirstPerson;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** v0.17.3: Undead's boost in first person -- a FOV kick and a slight shake while he tears along at 40 b/s. */
@Mixin(GameRenderer.class)
public abstract class UndeadCameraMixin {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private double projecthero$undeadBoostFov(double fov, Camera camera, float partial, boolean useSetting) {
		return useSetting ? fov * UndeadFirstPerson.fovScale(partial) : fov;
	}

	/** v0.17.3 hold X: the view wrenched with the head, the jolt as it tears off (at HEAD: vanilla returns early when not hurt). */
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void projecthero$undeadHeadShake(PoseStack pose, float partial, CallbackInfo ci) {
		com.projecthero.mod.client.undead.UndeadHeadThrowRender.shake(pose, partial);
	}

	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void projecthero$undeadBoostShake(PoseStack pose, float partial, CallbackInfo ci) {
		UndeadFirstPerson.shake(pose, partial);
	}
}
