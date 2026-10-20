package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.ironman.IronManHulkbusterAnimatable;
import com.projecthero.mod.client.ironman.IronManHulkbusterFirstPerson;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/** v0.16.1: in the full Mark 8, first person shows the Hulkbuster's own huge forearms and fists instead of the hands. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererHulkbusterMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void projecthero$hulkbusterArms(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
			LocalPlayer player, int packedLight, CallbackInfo ci) {
		if (IronManHulkbusterAnimatable.active(player) && !player.isInvisible()) {
			ci.cancel();
			IronManHulkbusterFirstPerson.render(partialTicks, poseStack, buffer, player, packedLight);
		}
	}
}
