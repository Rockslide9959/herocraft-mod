package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.carapace.CarapaceRender;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** v0.16.6: worn armour is not drawn over Carapace's suit (as Rena Rouge's {@code RenaRougeArmorMixin}). */
@Mixin(HumanoidArmorLayer.class)
public abstract class CarapaceArmorMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$carapaceHideArmour(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player && CarapaceRender.cover(player, partialTick) > 0f) {
			ci.cancel();
		}
	}
}
