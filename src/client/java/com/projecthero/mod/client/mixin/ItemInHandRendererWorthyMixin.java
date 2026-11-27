package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.worthy.WorthyPose;
import com.projecthero.mod.worthy.WorthyLending;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 Thor x Super Soldier, first person: while a lent-weapon move plays ({@link WorthyPose#firstPerson}) the weapon (and the
 * shield in the other hand) is moved about the hand just before vanilla draws it -- inside vanilla's own push / pop, so nothing
 * leaks. Only for a Super Soldier wielding a lent Mjolnir / Stormbreaker.
 */
@Mixin(value = ItemInHandRenderer.class, priority = 1100)
public abstract class ItemInHandRendererWorthyMixin {
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$worthySwing(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (WorthyLending.lentHand(player) == null) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		WorthyPose.firstPerson(player, main ? player.getMainArm() : player.getMainArm().getOpposite(), partialTick, pose);
	}
}
