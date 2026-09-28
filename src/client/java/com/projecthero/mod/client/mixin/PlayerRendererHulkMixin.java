package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.hulk.HulkRenderer;
import com.projecthero.mod.hulk.Hulk;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.13.12 (Hulk Phase 3): a player who is the Hulk is drawn as the Hulk model ({@link HulkRenderer}) instead of the
 * vanilla player -- armour, cape and held items included, which is how "hide armour while transformed" works.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHulkMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void projecthero$buildHulkRenderer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
		HulkRenderer.rebuild(context);
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$renderHulk(AbstractClientPlayer player, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		HulkRenderer renderer = HulkRenderer.get();
		if (renderer == null || !Hulk.isHulk(player)) {
			return;
		}
		ci.cancel();
		if (player.isInvisible()) {
			return;
		}
		renderer.render(player, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}
}
