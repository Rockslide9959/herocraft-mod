package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hero.power.p15.InvisibilityLightHandlers;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.22 (Light / Invisibility, C): a cloaked player's held items vanish along with the rest of them. Vanilla keeps
 * drawing the items in an invisible player's hands, which gave the cloak away; this skips the held-item layer
 * whenever {@link InvisibilityLightHandlers#hideArmor} says the whole appearance is hidden (Cloak, Sparkling Flight,
 * Shadow Walk -- read from the all-viewer mutation flags, so every player sees it). Cosmetic only; first-person view of
 * your own hands is a different renderer and is untouched.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerCloakMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$hideHeldItemsWhileCloaked(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player && InvisibilityLightHandlers.hideArmor(player)) {
			ci.cancel();
		}
	}
}
