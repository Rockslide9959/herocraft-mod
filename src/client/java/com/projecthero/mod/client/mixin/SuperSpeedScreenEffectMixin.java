package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Super Speed Shift+C Phase (v0.14.5): no "inside a block" texture over the screen while phasing through a wall,
 * so you can see where you are going.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class SuperSpeedScreenEffectMixin {
	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
	private static void projecthero$speedPhaseClearView(Player player, CallbackInfoReturnable<BlockState> cir) {
		if (SuperSpeedHandlers.phasing(player)) {
			cir.setReturnValue(null);
		}
	}
}
