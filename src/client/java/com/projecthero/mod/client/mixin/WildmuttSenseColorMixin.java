package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttSense;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1: Wildmutt's Scent Sense outline colours (amber; red for the tracked creature). Only answered for client-side
 * entities ({@code IronManHighlight.mayDecide}, inside {@link WildmuttSense#colour}), so the integrated server never sees it.
 */
@Mixin(Entity.class)
public abstract class WildmuttSenseColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$wildmuttSenseColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer) {
			return;
		}
		int c = WildmuttSense.colour(self);
		if (c != 0) {
			cir.setReturnValue(c);
		}
	}
}
