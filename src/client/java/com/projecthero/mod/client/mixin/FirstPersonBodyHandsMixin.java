package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.fpbody.FirstPersonBody;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/** v0.15.15 full-body first person ({@link FirstPersonBody}): the real arms are in view, so no floating first-person hands. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonBodyHandsMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void projecthero$fpBodyNoHands(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
			LocalPlayer player, int packedLight, CallbackInfo ci) {
		if (FirstPersonBody.active()) {
			ci.cancel();
		}
	}
}
