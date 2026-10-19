package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.viperion.ViperionPose;
import com.projecthero.mod.miraculous.viperion.Viperion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v0.16.7, third person: the held item is hidden from Viperion's right hand while the Lyre is in it. */
@Mixin(ItemInHandLayer.class)
public abstract class ViperionItemInHandLayerMixin {
	@Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$viperionHidesItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (arm == HumanoidArm.RIGHT && entity instanceof AbstractClientPlayer player && Viperion.is(player)
				&& ViperionPose.inHand(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))) {
			ci.cancel();
		}
	}
}
