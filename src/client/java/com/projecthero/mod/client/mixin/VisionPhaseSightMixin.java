package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.vision.VisionHud;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.3 Vision's Intangibility: no "inside a block" texture over the screen while his head is in a wall he is phasing through
 * (as {@code SilverSurferScreenEffectMixin}). The culling half is {@code VisionPhaseCullMixin}.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class VisionPhaseSightMixin {
	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
	private static void projecthero$visionPhaseClearView(Player player, CallbackInfoReturnable<BlockState> cir) {
		if (VisionHud.phaseSight(player)) {
			cir.setReturnValue(null);
		}
	}
}
