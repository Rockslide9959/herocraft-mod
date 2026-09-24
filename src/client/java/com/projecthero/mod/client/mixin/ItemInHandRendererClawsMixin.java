package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.wolverine.WolverineClawsModel;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * First person: while Wolverine's claws are out, the (otherwise hidden) empty off hand pops up too, drawn
 * exactly like the main arm -- so {@link PlayerRendererClawsMixin} then adds its mirrored claws.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererClawsMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$wolverineOffHand(AbstractClientPlayer player, float partialTick, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand != InteractionHand.OFF_HAND || !stack.isEmpty() || player.isInvisible()
				|| !WolverineClawsModel.visible(player, partialTick)) {
			return;
		}
		pose.pushPose();
		renderPlayerArm(pose, buffers, light, equipProgress, swingProgress, player.getMainArm().getOpposite());
		pose.popPose();
		ci.cancel();
	}
}
