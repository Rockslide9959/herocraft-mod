package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.1: the Omnitrix in first person -- the raised wrist (drawn with the off hand, which is empty), and the alien kits'
 * first-person arm clips (the main-hand arm, and the off-hand arm too for a two-handed clip).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class OmnitrixItemInHandMixin {
	@Unique
	private boolean projecthero$pushed;

	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipped, float swing, HumanoidArm side);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void projecthero$clipBegin(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equipped, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		projecthero$pushed = false;
		FirstPersonClip clip = hand == InteractionHand.MAIN_HAND && stack.isEmpty() ? OmnitrixFirstPerson.clip(player) : null;
		if (clip != null) {
			pose.pushPose();
			float side = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
			if (clip.apply(pose, OmnitrixFirstPerson.clipTicks(player, partial), side)) {
				projecthero$pushed = true;
			} else {
				pose.popPose();
			}
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void projecthero$clipEnd(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equipped, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (projecthero$pushed) {
			pose.popPose();
			projecthero$pushed = false;
		}
		if (hand != InteractionHand.OFF_HAND || !stack.isEmpty() || player.isInvisible()) {
			return;
		}
		// the raised Omnitrix
		OmnitrixFirstPerson.renderRaised(pose, buffers, light, player, partial);
		// a two-handed alien clip: the off-hand arm joins in
		FirstPersonClip clip = OmnitrixFirstPerson.clip(player);
		com.projecthero.mod.client.omnitrix.AlienClientKit kit = com.projecthero.mod.client.omnitrix.AlienClients.shownKit(player);
		boolean both = kit != null && com.projecthero.mod.omnitrix.Omnitrix.transformed(player) && kit.bothHandsInFirstPerson(player);
		if ((clip != null && clip.twoHanded()) || both) {
			HumanoidArm off = player.getMainArm().getOpposite();
			float side = off == HumanoidArm.RIGHT ? 1f : -1f;
			pose.pushPose();
			// v0.17.1 (Wildmutt, bothHandsInFirstPerson): a beast's off paw is always in view, clip or not
			boolean applied = clip != null && clip.twoHanded() && clip.apply(pose, OmnitrixFirstPerson.clipTicks(player, partial), side);
			if (applied || both) {
				renderPlayerArm(pose, buffers, light, 0f, 0f, off);
			}
			pose.popPose();
		}
	}
}
