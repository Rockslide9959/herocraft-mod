package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.miraculous.viperion.ViperionHeatSense;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.7: Viperion's Heat Sense outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this
 * client's renderer asks -- the glow is his alone.
 */
@Mixin(Minecraft.class)
public abstract class ViperionHeatGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$viperionHeatSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ViperionHeatSense.outlined(entity)) {
			cir.setReturnValue(true);
		}
	}
}
