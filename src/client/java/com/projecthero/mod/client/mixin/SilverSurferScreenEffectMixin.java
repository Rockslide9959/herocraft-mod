package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** v0.17.1 Silver Surfer Molecular Phase: no "inside a block" texture over the screen while phased through a wall. */
@Mixin(ScreenEffectRenderer.class)
public abstract class SilverSurferScreenEffectMixin {
	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
	private static void projecthero$surferPhaseClearView(Player player, CallbackInfoReturnable<BlockState> cir) {
		if (SilverSurfer.phasing(player)) {
			cir.setReturnValue(null);
		}
	}
}
