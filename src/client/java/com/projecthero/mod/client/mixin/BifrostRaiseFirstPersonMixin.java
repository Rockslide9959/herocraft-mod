package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.thor.BifrostTravelClient;
import com.projecthero.mod.item.ModItems;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7: Bifrost Travel in first person -- the held Stormbreaker lifts up into view and tilts its head back toward the sky
 * while {@link BifrostTravelClient#raise} is up (inside vanilla's push/pop round the item, main hand only).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class BifrostRaiseFirstPersonMixin {
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$bifrostRaiseFirstPerson(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swing, ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || !stack.is(ModItems.STORMBREAKER)) {
			return;
		}
		float w = BifrostTravelClient.raise(player, partialTick);
		if (w <= 0.001f) {
			return;
		}
		pose.translate(0.0f, 0.42f * w, -0.12f * w);
		pose.mulPose(Axis.XP.rotationDegrees(28.0f * w));
		pose.mulPose(Axis.ZP.rotationDegrees(8.0f * w));
	}
}
