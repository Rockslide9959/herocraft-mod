package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/** v0.17.1: no first-person hands in the Hard-Light Mech's cockpit -- the mech's own arms and fists are the hands. */
@Mixin(ItemInHandRenderer.class)
public abstract class GreenLanternMechHandsMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void projecthero$noHandsInMech(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
			LocalPlayer player, int packedLight, CallbackInfo ci) {
		if (HardLightMechEntity.isPilot(player)) {
			ci.cancel();
		}
	}
}
