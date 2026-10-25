package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.silversurfer.SilverSurferAwareness;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1: the Silver Surfer's Cosmic Awareness outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only
 * this client's renderer asks -- the outlines are the Surfer's alone.
 */
@Mixin(Minecraft.class)
public abstract class SilverSurferGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$cosmicAwareness(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (SilverSurferAwareness.glows(entity)) {
			cir.setReturnValue(true);
		}
	}
}
