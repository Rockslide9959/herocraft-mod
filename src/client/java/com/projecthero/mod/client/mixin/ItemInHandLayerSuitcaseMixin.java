package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.21: while a Mark V suit-up / fold is running, {@code MarkVSuitcaseLayer} draws the (unfolding) suitcase in the
 * right hand, so the ordinary right-hand held-item draw is skipped for that window -- otherwise the closed case the
 * player right-clicked (or whatever else they hold) would be drawn through the open one.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerSuitcaseMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$suitcaseOwnsRightHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		// v0.14.29: the Mark 5 suitcase build / fold holds the case in BOTH hands, so neither held item is drawn
		if (entity instanceof Player player && com.projecthero.mod.client.ironman.MarkVSuitcaseLayer.active(player)
				&& (arm == HumanoidArm.RIGHT || com.projecthero.mod.client.ironman.MarkVSuitcaseLayer.bothHands(player))) {
			ci.cancel();
		}
		// v0.15.11: while a suit is put on by hand the hands hold the hammer / wrench / the piece being fitted
		// (IronManManualSuitUpLayer), never whatever was in them
		if (entity instanceof Player player && com.projecthero.mod.client.ironman.IronManManualSuitUpLayer.active(player)) {
			ci.cancel();
		}
	}
}
