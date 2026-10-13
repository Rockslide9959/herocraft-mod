package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** v0.16.1: {@code Player} overrides {@code playStepSound}; the Mark 5 STEALTH wearer's steps are silent there too. */
@Mixin(Player.class)
public abstract class IronManStealthStepMixin {
	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void projecthero$silentPlayerSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (com.projecthero.mod.ironman.IronManArmor.stealthed((Player) (Object) this)) {
			ci.cancel();
		}
	}
}
