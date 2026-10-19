package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.viperion.ViperionFirstPerson;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7, first person: while a move has Viperion's Lyre in his hand the main hand is drawn as his bare arm instead of
 * the item it holds (vanilla's own swing) -- {@code ViperionFirstPersonArmMixin} then puts the Lyre in the fist.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ViperionItemInHandMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$viperionArm(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (!ViperionFirstPerson.drawsArm(player, hand, partialTick)) {
			return;
		}
		pose.pushPose();
		renderPlayerArm(pose, buffers, light, 0.0f, swingProgress, player.getMainArm());
		pose.popPose();
		ci.cancel();
	}
}
