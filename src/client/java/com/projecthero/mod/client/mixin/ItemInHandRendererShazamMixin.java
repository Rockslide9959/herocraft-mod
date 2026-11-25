package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.shazam.ShazamPose;
import com.projecthero.mod.shazam.Shazam;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7, first person: Shazam's moves play on his empty hands ({@link ShazamPose#firstPerson}) -- the combo's jabs and hook, the
 * uppercut, the cocked Haymaker and its release, the hand raised to call the lightning, Zeus's Wrath held out, the creature held
 * overhead -- with vanilla's punch swing and equip dip held off while a move owns them. Both hands draw (the off hand too, which
 * vanilla leaves empty), only while the main hand is empty (the Super Soldier's pattern).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererShazamMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress, float swingProgress,
			HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$shazamHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!stack.isEmpty() || player.isInvisible() || player.isScoping() || !player.getMainHandItem().isEmpty() || !Shazam.transformed(player)) {
			return;
		}
		float w = ShazamPose.firstPersonWeight(player, partialTick);
		if (w <= 0.001f) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		pose.pushPose();
		ShazamPose.firstPerson(player, arm, partialTick, pose);
		float keep = 1f - Math.min(1f, w);
		renderPlayerArm(pose, buffers, light, main ? equipProgress * keep : 0f, main ? swingProgress * keep : 0f, arm);
		pose.popPose();
		ci.cancel();
	}
}
