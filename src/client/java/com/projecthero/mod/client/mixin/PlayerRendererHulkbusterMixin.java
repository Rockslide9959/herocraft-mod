package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.ironman.IronManHulkbusterAnimatable;
import com.projecthero.mod.client.ironman.IronManHulkbusterRenderer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.1: a player wearing the full Mark 8 HULKBUSTER is drawn as the segmented Hulkbuster body
 * ({@link IronManHulkbusterRenderer}) instead of the vanilla player + armour -- the same player-replacement trick as the
 * Hulk. Invisible players stay invisible.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHulkbusterMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void projecthero$buildHulkbusterRenderer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
		IronManHulkbusterRenderer.rebuild(context);
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$renderHulkbuster(AbstractClientPlayer player, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		IronManHulkbusterRenderer renderer = IronManHulkbusterRenderer.get();
		if (renderer == null || !IronManHulkbusterAnimatable.active(player)) {
			return;
		}
		ci.cancel();
		if (!player.isInvisible()) {
			renderer.renderBody(player, entityYaw, partialTicks, poseStack, buffer, packedLight);
		}
	}
}
