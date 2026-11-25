package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.thor.BifrostTravelClient;
import com.projecthero.mod.item.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7: Bifrost Travel -- while the caster holds Stormbreaker up to the sky ({@code BifrostRaisePoseMixin}) the axe is
 * turned a quarter turn about the hand's X axis, so its haft runs along the raised arm and the head points straight up
 * instead of jutting out behind the head. Applied inside vanilla's push/pop round the item, so nothing else is touched.
 */
@Mixin(ItemInHandLayer.class)
public abstract class BifrostRaiseGripMixin {
	@Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$bifrostSkyward(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
			PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!stack.is(ModItems.STORMBREAKER) || !(entity instanceof Player p) || arm != p.getMainArm()) {
			return;
		}
		float w = BifrostTravelClient.raise(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (w > 0.001f) {
			pose.mulPose(Axis.XP.rotationDegrees(-90.0f * w));
		}
	}
}
