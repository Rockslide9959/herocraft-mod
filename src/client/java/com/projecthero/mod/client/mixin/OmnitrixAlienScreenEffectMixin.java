package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** v0.17.2 (Wildvine): no "inside a block" texture over the screen for an alien at home inside blocks ({@link AlienKit#clearInBlocks}). */
@Mixin(ScreenEffectRenderer.class)
public abstract class OmnitrixAlienScreenEffectMixin {
	@Inject(method = "getViewBlockingState", at = @At("RETURN"), cancellable = true)
	private static void projecthero$alienClearView(Player player, CallbackInfoReturnable<BlockState> cir) {
		BlockState blocking = cir.getReturnValue();
		AlienKit kit = blocking == null ? null : AlienKit.activeKit(player);
		if (kit != null && kit.clearInBlocks(player, blocking)) {
			cir.setReturnValue(null);
		}
	}
}
