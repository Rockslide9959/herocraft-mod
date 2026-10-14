package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.spider.SpiderMaskAnim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.2: the Spider-Man mask coming off / going on, in first person -- the hand (both hands, putting it on) comes up
 * into view and sweeps across it to peel the mask up off the head or pull it down over it ({@link
 * SpiderMaskAnim#firstPersonArm}). While an arm is part of the animation it is drawn bare in place of whatever that hand
 * holds (vanilla never draws an empty off-hand arm, so this draws it); the held item is back the moment it ends.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SpiderMaskHandMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equippedProgress,
			float swingProgress, HumanoidArm side);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$maskHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float[] a = SpiderMaskAnim.firstPersonArm(player, side == HumanoidArm.RIGHT, partialTick);
		if (a == null || player.isInvisible()) {
			return;
		}
		float s = side == HumanoidArm.RIGHT ? 1f : -1f;
		pose.pushPose();
		// across / up the view, the forearm leaning in toward the face about a pivot well below the screen
		pose.translate(s * a[0], a[1], 0f);
		float px = s * 0.62f;
		float py = -0.75f;
		float pz = -0.7f;
		pose.translate(px, py, pz);
		pose.mulPose(Axis.ZP.rotation(s * 0.45f * a[2]));
		pose.mulPose(Axis.XP.rotation(-0.25f * a[2]));
		pose.translate(-px, -py, -pz);
		renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, side);
		pose.popPose();
		ci.cancel();
	}
}
