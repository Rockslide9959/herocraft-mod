package com.herocraft.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.herocraft.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Super Speed: you mine as fast as you move. {@code getDestroySpeed} is multiplied by the same factor
 * Super Speed's movement is (×3 in Speed Mode, ×6 in Overdrive), so block-breaking keeps pace with
 * the speed tier. Inert for every other player/state.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void herocraft$speedMining(BlockState state, CallbackInfoReturnable<Float> cir) {
		float factor = SuperSpeedHandlers.speedFactor((Player) (Object) this);
		if (factor > 1.01f) {
			cir.setReturnValue(cir.getReturnValue() * factor);
		}
	}
}
