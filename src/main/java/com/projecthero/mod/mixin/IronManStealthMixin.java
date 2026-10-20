package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.1 comic rework: the Mark 5 STEALTH armour is silent -- its wearer's footsteps (and swim sounds) are never
 * played, on the server (what everyone else hears) or the wearer's own client. Sculk is handled by
 * {@link IronManStealthVibrationMixin}. The rule lives in {@code IronManArmor#stealthed}.
 */
@Mixin(Entity.class)
public abstract class IronManStealthMixin {
	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void projecthero$silentSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
		if ((Object) this instanceof Player player && com.projecthero.mod.ironman.IronManArmor.stealthed(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "playSwimSound", at = @At("HEAD"), cancellable = true)
	private void projecthero$silentSwim(float volume, CallbackInfo ci) {
		if ((Object) this instanceof Player player && com.projecthero.mod.ironman.IronManArmor.stealthed(player)) {
			ci.cancel();
		}
	}
}
