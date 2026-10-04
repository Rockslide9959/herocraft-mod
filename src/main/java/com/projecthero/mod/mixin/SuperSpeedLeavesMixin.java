package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p04.SpeedLeaves;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.14.21: Super Speed's Speed Mode / Overdrive runs through leaves (never from above -- see {@link SpeedLeaves}).
 * The entity-context collision shape is what both the client's movement simulation and the server's movement check
 * use ({@code BlockCollisions}), so one hook covers both sides.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SuperSpeedLeavesMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$speedLeaves(BlockGetter level, BlockPos pos, CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		if (SpeedLeaves.passThrough((BlockState) (Object) this, pos, context)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
