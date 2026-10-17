package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.renarouge.RenaRougeClient;
import com.projecthero.mod.client.miraculous.renarouge.RenaRougeRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6: while Rena Rouge's Flute is in her right hand (a move) or at her lips, whatever item she holds in that hand
 * is not drawn over it (third person).
 */
@Mixin(ItemInHandLayer.class)
public abstract class RenaRougeHeldItemMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$renaRougeFluteInHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
			PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (arm != HumanoidArm.RIGHT || !(entity instanceof Player player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		RenaRougeRender.FluteAt at = RenaRougeClient.firstPersonFlute(player, partial);
		if (at != null) {
			ci.cancel();
		}
	}
}
