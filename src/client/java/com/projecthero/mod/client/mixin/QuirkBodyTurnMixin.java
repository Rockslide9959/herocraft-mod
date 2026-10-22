package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.7 (Quirk revamp): whole-body turns for the Engine and Explosion moves -- costume and all -- about the body's middle:
 * Engine's spin / sweep / finisher turns and the Rising Kick's back flip, and Bakugo's Howitzer Impact (a spin about the
 * vertical while he rises, then head-first along his dive, corkscrewing about his long axis). Each kit's pose class says
 * {@code [yaw, pitch, roll]} in degrees (or null). Driven by the synced anim / timers, so every viewer sees it; the
 * first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class QuirkBodyTurnMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$quirkBodyTurn(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		float[] t = com.projecthero.mod.client.quirk.engine.EnginePose.bodyTurn(player, partialTicks);
		if (t == null) {
			t = com.projecthero.mod.client.quirk.explosion.ExplosionPose.bodyTurn(player, partialTicks);
		}
		if (t == null) {
			return;
		}
		poseStack.translate(0.0f, 0.9f, 0.0f);
		if (t[0] != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(t[0]));
		}
		if (t[1] != 0f) {
			poseStack.mulPose(Axis.XP.rotationDegrees(t[1]));
		}
		if (t.length > 2 && t[2] != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(t[2]));
		}
		poseStack.translate(0.0f, -0.9f, 0.0f);
	}
}
