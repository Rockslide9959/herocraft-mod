package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.miraculous.ladybug.LadybugGlowClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: Ladybug's outlines (Yo-yo Scan, Yo-yo Phone, Ladybug Vision). Hooked on {@code Minecraft#shouldEntityAppearGlowing},
 * which only this client's renderer asks -- the outlines are the Ladybug's alone (the v0.15.4 privacy rule).
 */
@Mixin(Minecraft.class)
public abstract class LadybugGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$ladybugGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (LadybugGlowClient.marked(entity)) {
			cir.setReturnValue(true);
		}
	}
}
