package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.doctorfate.FateFx;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Doctor Fate, Sight of Nabu: Chaos outlines red, Order gold, the rest white. Only answered for client-side entities
 * ({@code IronManHighlight.mayDecide}), so the integrated server never sees it.
 */
@Mixin(Entity.class)
public abstract class FateSightColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$sightColour(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		if (FateFx.sighted(self)) {
			cir.setReturnValue(FateFx.colour(self));
		}
	}
}
