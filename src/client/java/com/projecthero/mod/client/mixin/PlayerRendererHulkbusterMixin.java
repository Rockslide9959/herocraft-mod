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

	/**
	 * v0.16.7 (user) "you dont really see the player while the suits building itself": the Hulkbuster's inner frame closes
	 * round the pilot ({@code HulkbusterBuildClient#frameTextures}); as it locks shut the pilot is drawn tucked a little
	 * narrower so no elbow or faceplate pokes through it (the frame is the Hulkbuster's own torso, a touch shallower than a
	 * player's head). Never invisible: before the frame is up they are drawn as normal.
	 */
	@Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
	private void projecthero$tuckPilot(AbstractClientPlayer player, PoseStack poseStack, float partialTick, CallbackInfo ci) {
		float k = com.projecthero.mod.client.ironman.HulkbusterBuildClient.pilotTuck(player, partialTick);
		if (k > 0f) {
			poseStack.scale(1f - 0.3f * k, 1f - 0.05f * k, 1f - 0.34f * k);
		}
	}

	/**
	 * v0.16.7 (user) Veronica: the mid-air build -- the player (in whatever suit they wear) is drawn as normal and the
	 * Hulkbuster's arrived / flying pieces round them.
	 */
	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$renderHulkbusterBuild(AbstractClientPlayer player, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		IronManHulkbusterRenderer renderer = IronManHulkbusterRenderer.get();
		if (renderer == null || player.isInvisible() || IronManHulkbusterAnimatable.active(player)
				|| com.projecthero.mod.client.ironman.HulkbusterVeronicaClient.buildPose(player, partialTicks) == null) {
			return;
		}
		var pod = com.projecthero.mod.client.ironman.HulkbusterVeronicaClient.buildPod(player);
		renderer.renderBody(player, pod == null ? entityYaw : pod.buildYaw(), partialTicks, poseStack, buffer, packedLight);
	}
}
