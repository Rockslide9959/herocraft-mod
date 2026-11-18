package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.squad.SquadKnockdownClient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.5 squad knockdown: the knocked-squadmate outline is a soft red. Only answered for client-side entities
 * ({@code IronManHighlight.mayDecide}), so the integrated server never sees it.
 */
@Mixin(Entity.class)
public abstract class SquadKnockdownGlowColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$knockedSquadmateColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		if (SquadKnockdownClient.outlined(self)) {
			cir.setReturnValue(SquadKnockdownClient.OUTLINE_COLOR);
		}
	}
}
