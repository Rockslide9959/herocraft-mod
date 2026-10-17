package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.catnoir.CatNoirFirstPerson;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6, first person: while Cat Noir's baton is out (or his hand is charged with CATACLYSM) the main hand is drawn as
 * his bare arm instead of the item it holds (vanilla's own swing, no equip dip) -- {@code CatNoirFirstPersonArmMixin}
 * then puts the baton in the fist. See {@link CatNoirFirstPerson}.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class CatNoirItemInHandMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$catNoirArm(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (!CatNoirFirstPerson.drawsArm(player, hand)) {
			return;
		}
		pose.pushPose();
		renderPlayerArm(pose, buffers, light, 0.0f, swingProgress, player.getMainArm());
		pose.popPose();
		ci.cancel();
	}
}
