package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.projecthero.mod.firearm.FirearmManager;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * v0.15.18: aiming a gun slows the player 30% ({@link FirearmManager#ADS_SLOW_ID}), and vanilla derives the FOV from
 * movement speed -- so the slowdown would add its own Slowness-style zoom on top of the gun's ADS zoom. Undo just that
 * modifier's share of the speed the FOV formula reads (the gun's own zoom is applied by {@link GunFovMixin}).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class GunAdsFovMixin {
	@ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double projecthero$ignoreAdsSlow(double speed) {
		AttributeInstance attr = ((AbstractClientPlayer) (Object) this).getAttribute(Attributes.MOVEMENT_SPEED);
		if (attr != null && attr.hasModifier(FirearmManager.ADS_SLOW_ID)) {
			return speed / (1.0 + FirearmManager.ADS_SLOW);
		}
		return speed;
	}
}
