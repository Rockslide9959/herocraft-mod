package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.supersoldier.CapShieldModel;
import com.projecthero.mod.client.supersoldier.CapShieldPose;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5, first person: a Captain America shield is placed by {@link CapShieldPose#renderFirstPerson} (carried low at the
 * side, eased up to block, shoved out to bash, dropping in on a catch); the empty throwing hand sweeps through the throw and
 * rises to meet the shield coming home.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class CapShieldFirstPersonMixin {
	@Unique
	private boolean projecthero$pushed;

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$capShieldFirstPerson(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		projecthero$pushed = false;
		if (player.isScoping()) {
			return;
		}
		if (CapShieldModel.isCapShield(stack)) {
			ci.cancel();
			CapShieldPose.renderFirstPerson(player, hand, stack, partialTick, swingProgress, equipProgress, pose, buffers, light);
			return;
		}
		// v0.17.5: the Battle Suit's slotted shield raised with an empty off hand -- drawn as if that hand held it, while up / lowering
		if (stack.isEmpty() && hand == InteractionHand.OFF_HAND) {
			ItemStack slotted = com.projecthero.mod.supersoldier.CapShieldSlot.usable(player);
			if (!slotted.isEmpty() && CapShieldPose.block(player, partialTick) > 0.001f) {
				ci.cancel();
				CapShieldPose.renderFirstPerson(player, hand, slotted, partialTick, 0f, 0f, pose, buffers, light);
				return;
			}
		}
		if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
			pose.pushPose();
			if (CapShieldPose.firstPersonEmptyArm(player, partialTick, pose)) {
				projecthero$pushed = true;
			} else {
				pose.popPose();
			}
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void projecthero$capShieldFirstPersonPop(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (projecthero$pushed) {
			projecthero$pushed = false;
			pose.popPose();
		}
	}
}
