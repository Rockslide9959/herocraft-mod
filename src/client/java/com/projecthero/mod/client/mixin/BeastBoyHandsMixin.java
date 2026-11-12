package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastBoy;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/** v0.17.3 Beast Boy: an animal has no hands and holds nothing -- the first-person hands and held items are hidden. */
@Mixin(ItemInHandRenderer.class)
public abstract class BeastBoyHandsMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastNoHands(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light,
			CallbackInfo ci) {
		if (BeastBoy.morphed(player)) {
			ci.cancel();
		}
	}
}
