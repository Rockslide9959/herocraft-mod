package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.speedforce.SpeedForceSenses;
import com.projecthero.mod.speedforce.SpeedForceConfig;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1: the danger sense's outline colour. Only answered for client-side entities ({@code IronManHighlight.mayDecide}),
 * so the integrated server never sees it (the v0.15.4 highlight-leak rule).
 */
@Mixin(Entity.class)
public abstract class SpeedForceDangerColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$dangerColour(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		if (SpeedForceSenses.marked(self)) {
			cir.setReturnValue(SpeedForceConfig.DANGER_COLOUR);
		}
	}
}
