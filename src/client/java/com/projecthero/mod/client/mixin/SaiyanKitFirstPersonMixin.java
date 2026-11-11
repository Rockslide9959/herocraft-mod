package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.saiyan.kit.SaiyanKitPose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3 Saiyan revamp: the move kit's first-person arm clips ({@link SaiyanKitPose#firstPerson}) on the bare main arm just
 * before vanilla draws it, and the posed off arm too for a two-handed clip (the QuirkFirstPersonPoseMixin pattern).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SaiyanKitFirstPersonMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equippedProgress, float swingProgress,
			HumanoidArm side);

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
	private void projecthero$saiyanKitArms(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || player != Minecraft.getInstance().player) {
			return;
		}
		QuirkFirstPersonPose.Sample s = SaiyanKitPose.firstPerson(player, partialTick);
		if (s == null || s.weight() <= 0.01f) {
			return;
		}
		HumanoidArm main = player.getMainArm();
		if (s.twoHanded() && player.getOffhandItem().isEmpty()) {
			HumanoidArm off = main.getOpposite();
			pose.pushPose();
			QuirkFirstPersonPose.apply(pose, s, false, off == HumanoidArm.RIGHT ? 1f : -1f);
			renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, off);
			pose.popPose();
		}
		QuirkFirstPersonPose.apply(pose, s, true, main == HumanoidArm.RIGHT ? 1f : -1f);
	}
}
