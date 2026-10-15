package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.ghostrider.GhostRiderMarkClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5: the Judgement Mark's outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this
 * client's renderer asks -- the outlines are the casting Ghost Rider's alone (the v0.15.4 privacy rule).
 */
@Mixin(Minecraft.class)
public abstract class GhostRiderMarkGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$ghostRiderMark(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (GhostRiderMarkClient.marked(entity)) {
			cir.setReturnValue(true);
		}
	}
}
