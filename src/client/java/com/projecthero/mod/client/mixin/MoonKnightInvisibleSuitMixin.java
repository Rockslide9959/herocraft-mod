package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight Phase 5 -- Jake's Vanish (and any other Invisibility on a suited Moon Knight): vanilla still draws armour
 * on an invisible player, which would leave the whole GeckoLib suit floating in the air. While a transformed Moon Knight
 * is invisible the armour layer is skipped (the cape layer already hides itself). Cosmetic only.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class MoonKnightInvisibleSuitMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$hideVanishedMoonKnight(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player && player.isInvisible()
				&& com.projecthero.mod.moonknight.MoonKnight.isTransformed(player)) {
			ci.cancel();
		}
	}
}
