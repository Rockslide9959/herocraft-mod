package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.spin.GyroCapeLayer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;

/** v0.17.4: Gyro's coat brings its own cape ({@link GyroCapeLayer}), so the wearer's Minecraft cape is hidden under it. */
@Mixin(CapeLayer.class)
public abstract class GyroCoatCapeMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$gyroCoatCape(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float a, float b, float c, float d,
			float e, float f, CallbackInfo ci) {
		if (GyroCapeLayer.shown(p)) {
			ci.cancel();
		}
	}
}
