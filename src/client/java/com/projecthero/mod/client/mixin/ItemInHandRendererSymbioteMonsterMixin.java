package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.symbiote.monster.SymbioteMonsterFirstPerson;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/** v0.17.5 Symbiote Monster form: first person shows the monster's huge clawed forearms instead of the hands. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererSymbioteMonsterMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void projecthero$symbioteMonsterClaws(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
			LocalPlayer player, int packedLight, CallbackInfo ci) {
		if (SymbioteMonsterFirstPerson.active(player)) {
			ci.cancel();
			SymbioteMonsterFirstPerson.render(partialTicks, poseStack, buffer, player, packedLight);
		}
	}
}
