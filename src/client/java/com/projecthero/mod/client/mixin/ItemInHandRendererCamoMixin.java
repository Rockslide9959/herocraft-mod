package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.ironman.IronManFirstPersonGauntlets;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Mk V Active Camo: vanilla draws no first-person hand at all for an invisible player. An invisible Iron
 * Man (the camo, the blink) gets a FAINT ghost of his gauntlet instead -- drawn through the normal arm path with
 * {@link IronManFirstPersonGauntlets#faint} set, which {@code PlayerRendererHandMixin} turns into a translucent gauntlet
 * with no skin arm under it.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererCamoMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$camoFaintArm(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || !stack.isEmpty() || player != Minecraft.getInstance().player
				|| !IronManFirstPersonGauntlets.faintArm(player)) {
			return;
		}
		IronManFirstPersonGauntlets.faint = true;
		try {
			renderPlayerArm(pose, buffers, light, equipProgress, swingProgress, player.getMainArm());
		} finally {
			IronManFirstPersonGauntlets.faint = false;
		}
		ci.cancel();
	}
}
