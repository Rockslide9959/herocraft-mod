package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.client.blackpanther.BlackPantherPose;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.4, first person: Black Panther's moves play on both empty hands ({@link BlackPantherPose#firstPerson}) -- the strikes,
 * grabs, throws and stances of both kits -- with vanilla's own punch swing and equip dip held off while a move owns them. Only when the main hand is empty: holding an item, vanilla's hands are left alone.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererBlackPantherMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress, float swingProgress,
			HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$blackPantherHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!stack.isEmpty() || player.isInvisible() || !player.getMainHandItem().isEmpty() || !BlackPanther.hasPower(player)) {
			return;
		}
		float w = BlackPantherPose.firstPersonWeight(player, partialTick);
		if (w <= 0.001f) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		pose.pushPose();
		BlackPantherPose.firstPerson(player, arm, partialTick, pose);
		// a move owns the hands outright: no punch swing and no post-hit dip under it
		float keep = 1f - Math.min(1f, w);
		renderPlayerArm(pose, buffers, light, main ? equipProgress * keep : 0f, main ? swingProgress * keep : 0f, arm);
		pose.popPose();
		ci.cancel();
	}
}
