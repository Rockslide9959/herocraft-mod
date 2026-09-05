package com.herocraft.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.herocraft.mod.hero.power.p15.InvisibilityLightHandlers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Light Manipulation's cloaking hides the whole player, armour included. Vanilla keeps rendering
 * worn armour on an invisible player, so this cancels the armour layer entirely while the wearer is
 * cloaked or dissolved into light by Sparkling Flight (see
 * {@link InvisibilityLightHandlers#hideArmor}). Purely cosmetic; server-side gameplay is untouched.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void herocraft$hideArmorWhileCloaked(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player && InvisibilityLightHandlers.hideArmor(player)) {
			ci.cancel();
		}
	}
}
