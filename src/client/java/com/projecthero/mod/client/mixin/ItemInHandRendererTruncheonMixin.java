package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.moonknight.MoonKnightTruncheonPose;
import com.projecthero.mod.moonknight.item.MoonKnightTruncheonItem;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.4, first person: while one of the Truncheon's moves plays (the combo's forehand / backhand / overhead smash, the
 * staff spin, the summon twirl), the held truncheon is moved about the hand just before it is drawn
 * ({@link MoonKnightTruncheonPose#firstPerson}). Inside vanilla's own push / pop, so nothing leaks.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererTruncheonMixin {
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$moonKnightTruncheonMove(AbstractClientPlayer player, float partialTick, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!(stack.getItem() instanceof MoonKnightTruncheonItem)) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		MoonKnightTruncheonPose.firstPerson(player, arm, partialTick, pose);
	}
}
