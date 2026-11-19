package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;
import com.projecthero.mod.client.backpack.BackpackLayer;

import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5: elytra over a worn backpack spread from the bag's outer face instead of through it. Vanilla pushes the wings 2 px out of
 * the back ({@code translate(0, 0, 0.125)}); this adds {@link BackpackLayer#backClearance} for wings whose inner edge is at z 2.
 */
@Mixin(ElytraLayer.class)
public abstract class BackpackElytraMixin {
	@ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 2)
	private float projecthero$elytraOverBackpack(float z, @Local(argsOnly = true) LivingEntity entity) {
		if (entity instanceof Player p) {
			return z + BackpackLayer.backClearance(p, 2.0f) / 16f;
		}
		return z;
	}
}
