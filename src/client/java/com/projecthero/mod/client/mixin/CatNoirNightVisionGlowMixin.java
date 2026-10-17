package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.miraculous.catnoir.CatNoirNightVision;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: Cat Noir's Night Vision outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this
 * client's renderer asks -- the outlines are his alone (the v0.15.4 privacy rule).
 */
@Mixin(Minecraft.class)
public abstract class CatNoirNightVisionGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$catNoirNightVision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (CatNoirNightVision.outlined(entity)) {
			cir.setReturnValue(true);
		}
	}
}
