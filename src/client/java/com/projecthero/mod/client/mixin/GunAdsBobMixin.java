package com.projecthero.mod.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.client.punisher.GunAnim;
import com.projecthero.mod.client.punisher.GunFirstPerson;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * v0.15.18 (user: remove the left-right swing while moving while aiming): vanilla's view bobbing is applied to the
 * first-person hand as well as to the world, so a Punisher gun swung side to side on screen while walking even with
 * its sights up. Fade the hand's share of the bob out as the gun comes up to the eye (the world still bobs, the gun
 * stays steady on the crosshair); hip fire keeps the full bob.
 */
@Mixin(GameRenderer.class)
public abstract class GunAdsBobMixin {
	@WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
	private void projecthero$steadyAdsHand(GameRenderer self, PoseStack pose, float partialTick, Operation<Void> original) {
		Minecraft mc = Minecraft.getInstance();
		float ads = mc.player != null && GunFirstPerson.active(mc.player) ? GunAnim.aim(mc.player, partialTick) : 0f;
		if (ads <= 0f) {
			original.call(self, pose, partialTick);
			return;
		}
		if (ads >= 1f) {
			return;
		}
		// bob into a scratch stack, then apply only the hip-fire share of it
		PoseStack bob = new PoseStack();
		original.call(self, bob, partialTick);
		Matrix4f m = new Matrix4f().lerp(bob.last().pose(), 1f - ads);
		pose.mulPose(m);
	}
}
