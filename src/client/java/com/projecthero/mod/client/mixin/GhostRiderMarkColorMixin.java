package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.ghostrider.GhostRiderMarkClient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5: Judgement-marked creatures outline hellfire red. Only answered for client-side entities
 * ({@code IronManHighlight.mayDecide}), so the integrated server never sees it.
 */
@Mixin(Entity.class)
public abstract class GhostRiderMarkColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$ghostRiderMarkColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		if (GhostRiderMarkClient.marked(self)) {
			cir.setReturnValue(GhostRiderMarkClient.RED);
		}
	}
}
