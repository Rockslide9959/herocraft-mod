package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.thor.ThorKitPose;
import com.projecthero.mod.item.ModItems;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 Thor Cosmic revamp: while Twin Throw / Bifrost Judgement / Ragnarok fly a spectral copy of a weapon
 * ({@code ThorFxRenderer}), the real one in his hand is not drawn, so the weapon reads as having left it.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerThorHideMixin {
	@Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$hideThrownThorWeapon(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
			PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (entity instanceof Player p && (stack.is(ModItems.MJOLNIR) || stack.is(ModItems.STORMBREAKER))
				&& ThorKitPose.hidesHeld(p, stack.is(ModItems.MJOLNIR))) {
			ci.cancel();
		}
	}
}
