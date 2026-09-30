package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.mutation.v0145.SpeedPhaseFx;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.14.8 Super Speed Phase: in first person the phaser's own hands buzz a little every frame. The game renderer
 * pushes / pops the pose stack around this call, so the shake never leaks out of the hand pass.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SpeedPhaseHandJitterMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void projecthero$phaseHandJitter(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
			LocalPlayer player, int light, CallbackInfo ci) {
		SpeedPhaseFx.handJitter(poseStack, player);
	}
}
