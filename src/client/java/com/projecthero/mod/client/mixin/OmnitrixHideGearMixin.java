package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienClients;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** v0.17.1: an Omnitrix alien wears no visible armour (it still protects: the alien's body is what shows). */
@Mixin(HumanoidArmorLayer.class)
public abstract class OmnitrixHideGearMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$alienNoArmour(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b, float c, float d,
			float e, float f, CallbackInfo ci) {
		if (entity instanceof Player p && AlienClients.shown(p) != null) {
			ci.cancel();
		}
	}
}
