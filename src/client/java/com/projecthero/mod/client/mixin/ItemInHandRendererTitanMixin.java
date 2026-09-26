package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/** No first-person hands (or held item) while riding a Titan: they would float at head height, 10 blocks up. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererTitanMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void projecthero$noHandsInTitan(float partialTicks, PoseStack poseStack,
			MultiBufferSource.BufferSource buffer, LocalPlayer player, int packedLight, CallbackInfo ci) {
		if (TitanFormEntity.isOwnerRider(player)) {
			ci.cancel();
		}
	}
}
