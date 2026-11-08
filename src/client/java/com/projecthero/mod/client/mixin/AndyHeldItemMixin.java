package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v0.17.3: nothing is held in a hand Andy doesn't have (third person). */
@Mixin(ItemInHandLayer.class)
public abstract class AndyHeldItemMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyNoHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (entity instanceof Player p && Andy.missing(p, arm == HumanoidArm.RIGHT ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM)) {
			ci.cancel();
		}
	}
}
