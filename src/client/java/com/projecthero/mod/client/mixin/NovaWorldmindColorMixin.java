package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.nova.NovaWorldmindClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.15.13: the Worldmind's outline colours -- gold for the passive 32-block sense, cyan for a scanned creature, red for
 * the marked one. Only answered for client-side entities ({@code IronManHighlight.mayDecide}), so the integrated server
 * never sees it, and only for entities the local Nova's own Worldmind outlines.
 */
@Mixin(Entity.class)
public abstract class NovaWorldmindColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$novaWorldmindColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self || !NovaWorldmindClient.outlines(viewer, self)) {
			return;
		}
		cir.setReturnValue(NovaWorldmindClient.color(viewer, self));
	}
}
