package com.projecthero.mod.client.mixin;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.greenlantern.PowerBatteryHeldRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.15, first person: the Power Battery is shown hanging from your fist by its handle (your arm drawn as when
 * empty-handed), swaying; and while charging the ring, both arms come in -- the battery held out in front, the ring fist
 * pressed against it -- with the battery glowing. See {@link PowerBatteryHeldRenderer}.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererPowerBatteryMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$powerBattery(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (player.isInvisible()) {
			return;
		}
		HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float charge = PowerBatteryHeldRenderer.charge(player, partialTick);
		if (charge >= 0f && PowerBatteryHeldRenderer.isBattery(player.getOffhandItem())) {
			// charging: the off hand holds the battery out, the other (ring) hand is pressed against it
			boolean batteryHand = hand == InteractionHand.OFF_HAND;
			pose.pushPose();
			PowerBatteryHeldRenderer.chargeArmPose(pose, side, batteryHand, player, partialTick);
			renderPlayerArm(pose, buffers, light, 0f, 0f, side);
			pose.popPose();
			if (batteryHand) {
				pose.pushPose();
				PowerBatteryHeldRenderer.chargeArmPose(pose, side, true, player, partialTick);
				org.joml.Matrix4f start = new org.joml.Matrix4f(pose.last().pose());
				pose.popPose();
				Vector3f fist = PowerBatteryHeldRenderer.firstPersonFist(pose, start, 0f, 0f, side);
				PowerBatteryHeldRenderer.drawFirstPerson(player, player.getOffhandItem(), fist, pitch, partialTick, pose, buffers, light,
						true);
			}
			ci.cancel();
			return;
		}
		if (!PowerBatteryHeldRenderer.isBattery(stack)) {
			return;
		}
		pose.pushPose();
		renderPlayerArm(pose, buffers, light, equipProgress, swingProgress, side);
		pose.popPose();
		Vector3f fist = PowerBatteryHeldRenderer.firstPersonFist(pose, new org.joml.Matrix4f(pose.last().pose()), equipProgress,
				swingProgress, side);
		PowerBatteryHeldRenderer.drawFirstPerson(player, stack, fist, pitch, partialTick, pose, buffers, light, false);
		ci.cancel();
	}
}
