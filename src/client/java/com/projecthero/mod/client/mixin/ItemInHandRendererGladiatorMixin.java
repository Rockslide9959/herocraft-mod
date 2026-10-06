package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.hulk.GladiatorFirstPerson;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.5, first person: Gladiator Hulk shows both arms -- each hand is drawn as his bare arm (with its own swing, no
 * equip dip) in place of whatever item it holds, so {@code PlayerRendererGladiatorWeaponMixin} can put the hammer
 * in the right fist and the axe in the left. See {@link GladiatorFirstPerson}.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererGladiatorMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$gladiatorArms(AbstractClientPlayer player, float partialTick, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!GladiatorFirstPerson.drawsArm(player, hand)) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		pose.pushPose();
		renderPlayerArm(pose, buffers, light, 0.0f, swingProgress, arm); // no equip dip: the weapons never change
		pose.popPose();
		ci.cancel();
	}
}
