package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.greenlantern.block.GreenLanternSurvivalBlocks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.1: the Green Lantern's Hard-Light Scaffold Tower climbs like vanilla scaffolding. Vanilla lets a sneaking player
 * cling to a ladder or vine instead of sliding -- except inside scaffolding, where sneaking sinks you down; this makes
 * the hard-light column count as scaffolding for that one check. Runs on both sides (the local player's movement is
 * simulated on the client).
 */
@Mixin(LivingEntity.class)
public abstract class HardLightScaffoldClimbMixin {
	@WrapOperation(method = "handleOnClimbable", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
	private boolean projecthero$hardLightScaffold(BlockState state, Block block, Operation<Boolean> original) {
		return original.call(state, block) || state.is(GreenLanternSurvivalBlocks.HARD_LIGHT_SCAFFOLD);
	}
}
