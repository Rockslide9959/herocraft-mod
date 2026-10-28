package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeEmp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * v0.17.1 Upgrade's Overload / EMP Burst: inside a jammed zone ({@link UpgradeEmp}) no block gives off a redstone signal, so
 * lamps go dark, pistons pull back, dust loses its power and dispensers stop -- until the zone lapses and everything in it is
 * re-updated. One static "any zone at all" check first, so the cost is nil the rest of the time.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class OmnitrixUpgradeEmpMixin {
	@Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
	private void projecthero$empSignal(BlockGetter level, BlockPos pos, Direction dir, CallbackInfoReturnable<Integer> cir) {
		if (UpgradeEmp.jammed(level, pos)) {
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true)
	private void projecthero$empDirectSignal(BlockGetter level, BlockPos pos, Direction dir, CallbackInfoReturnable<Integer> cir) {
		if (UpgradeEmp.jammed(level, pos)) {
			cir.setReturnValue(0);
		}
	}
}
