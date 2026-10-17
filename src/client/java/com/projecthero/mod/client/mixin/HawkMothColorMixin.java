package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.miraculous.hawkmoth.HawkMothFxClient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: the colour of Hawk Moth's outlines (see {@link HawkMothGlowMixin}). Only answered for client-side entities
 * ({@code IronManHighlight.mayDecide}), so the integrated server never sees it.
 */
@Mixin(Entity.class)
public abstract class HawkMothColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$hawkMothColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		int c = HawkMothFxClient.outline(self);
		if (c != -1) {
			cir.setReturnValue(c);
		}
	}
}
