package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttSense;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1: Wildmutt's Scent Sense outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this client's
 * renderer asks -- the outlines are his alone (the v0.15.4 privacy rule).
 */
@Mixin(Minecraft.class)
public abstract class WildmuttSenseGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$wildmuttSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (WildmuttSense.outlined(entity)) {
			cir.setReturnValue(true);
		}
	}
}
